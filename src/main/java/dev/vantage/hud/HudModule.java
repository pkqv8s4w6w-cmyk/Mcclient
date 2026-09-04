package dev.vantage.hud;

import dev.vantage.gui.Theme;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/**
 * A draggable on-screen element.
 *
 * <p>Position is stored as a fraction of the screen rather than in pixels, so an element keeps its
 * place when the window is resized or the GUI scale changes. It is clamped back into view on every
 * draw, which covers the case of moving from a wide screen to a narrow one.
 *
 * <p>Subclasses draw in their own coordinate space starting at the origin and report their size;
 * this class handles anchoring, scaling and the optional backing panel.
 */
public abstract class HudModule extends Module {

    private static final float PADDING = 3.5f;

    private final NumberSetting positionX = registerHidden(
            new NumberSetting("Position X", "", 0.01, 0.0, 1.0, 0.0001));
    private final NumberSetting positionY = registerHidden(
            new NumberSetting("Position Y", "", 0.05, 0.0, 1.0, 0.0001));

    private final NumberSetting scale = register(new NumberSetting(
            "Scale", "Size of this element", 1.0, 0.5, 2.5, 0.05, "x"));
    private final BooleanSetting backdrop = register(new BooleanSetting(
            "Background", "Draw a panel behind this element", true));
    private final NumberSetting opacity = register(new NumberSetting(
            "Opacity", "How solid the background panel is", 70.0, 0.0, 100.0, 5.0, "%"));

    protected HudModule(String name, String description) {
        this(name, Category.HUD, description);
    }

    /** For elements that belong somewhere other than the HUD list, such as the threat ranking. */
    protected HudModule(String name, Category category, String description) {
        super(name, category, description);
        // The opacity slider is meaningless with no panel to fade, so hide it when off.
        opacity.visibleWhen(backdrop::value);
    }

    /** Size of the drawn content, before scaling and before any backing panel. */
    public abstract float getContentWidth();

    public abstract float getContentHeight();

    /** Draws the element with its top-left at the origin. */
    protected abstract void renderContent();

    public float getScale() {
        return scale.asFloat();
    }

    /** Nudges the size, used by the editor's scroll wheel. Clamped by the setting's own range. */
    public void adjustScale(double delta) {
        scale.set(scale.asDouble() + delta);
    }

    public boolean hasBackdrop() {
        return backdrop.value();
    }

    // -- placement --------------------------------------------------------------------------

    public float getWidth() {
        return (getContentWidth() + PADDING * 2.0f) * getScale();
    }

    public float getHeight() {
        return (getContentHeight() + PADDING * 2.0f) * getScale();
    }

    public float getX(ScaledResolution resolution) {
        float maximum = Math.max(0.0f, resolution.getScaledWidth() - getWidth());
        return Math.min(maximum, (float) (positionX.asDouble() * resolution.getScaledWidth()));
    }

    public float getY(ScaledResolution resolution) {
        float maximum = Math.max(0.0f, resolution.getScaledHeight() - getHeight());
        return Math.min(maximum, (float) (positionY.asDouble() * resolution.getScaledHeight()));
    }

    public void setPosition(ScaledResolution resolution, float x, float y) {
        positionX.set(x / (double) resolution.getScaledWidth());
        positionY.set(y / (double) resolution.getScaledHeight());
    }

    // -- drawing ----------------------------------------------------------------------------

    @Override
    public void onRenderOverlay(float partialTicks) {
        // The editor draws elements itself so it can show them being dragged; skip here to avoid
        // painting each element twice.
        if (Minecraft.getMinecraft().currentScreen instanceof HudEditScreen) {
            return;
        }
        draw();
    }

    /** Draws the element at its configured place. Also used by the editor. */
    public void draw() {
        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
        drawAt(getX(resolution), getY(resolution));
    }

    public void drawAt(float x, float y) {
        float elementScale = getScale();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0f);
        GlStateManager.scale(elementScale, elementScale, 1.0f);

        if (backdrop.value()) {
            int alpha = (int) (255 * (opacityFraction()));
            RenderUtil.roundedRect(0.0f, 0.0f,
                    getContentWidth() + PADDING * 2.0f,
                    getContentHeight() + PADDING * 2.0f,
                    3.5, RenderUtil.withAlpha(Theme.PANEL, alpha));
        }

        GlStateManager.translate(PADDING, PADDING, 0.0f);
        renderContent();

        GlStateManager.popMatrix();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    protected float opacityFraction() {
        return (float) (opacity.asDouble() / 100.0);
    }
}
