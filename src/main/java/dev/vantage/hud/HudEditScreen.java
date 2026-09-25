package dev.vantage.hud;

import dev.vantage.Vantage;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/** Drag HUD elements around, with alignment guides and scroll-to-resize. */
public class HudEditScreen extends GuiScreen {

    private static final float SNAP_THRESHOLD = 4.0f;
    private static final float EDGE_MARGIN = 3.0f;

    private HudModule dragged;
    private float grabOffsetX;
    private float grabOffsetY;
    private boolean snappedX;
    private boolean snappedY;

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        Vantage.instance().saveConfig();
    }

    private List<HudModule> elements() {
        List<HudModule> result = new ArrayList<HudModule>();
        for (Module module : Vantage.instance().modules().getModules()) {
            if (module instanceof HudModule && module.isEnabled()) {
                result.add((HudModule) module);
            }
        }
        return result;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, width, height, 0x60000000, 0x90000000);

        ScaledResolution resolution = new ScaledResolution(mc);
        List<HudModule> elements = elements();

        if (dragged != null) {
            moveDragged(resolution, elements, mouseX, mouseY);
        }

        for (HudModule element : elements) {
            float x = element.getX(resolution);
            float y = element.getY(resolution);
            element.drawAt(x, y);

            boolean hovered = RenderUtil.isInside(mouseX, mouseY, x, y, element.getWidth(), element.getHeight());
            boolean active = element == dragged;
            if (hovered || active) {
                RenderUtil.roundedOutline(x - 1.0f, y - 1.0f,
                        element.getWidth() + 2.0f, element.getHeight() + 2.0f,
                        4.5, 1.0f, active ? Theme.accent() : RenderUtil.withAlpha(Theme.accent(), 0.5f));
                Fonts.TINY.drawString(element.getName(), x, y - 10.0f, Theme.text());
            }
        }

        drawGuides(resolution);
        drawHint();
    }

    private void moveDragged(ScaledResolution resolution, List<HudModule> elements, int mouseX, int mouseY) {
        List<HudModule> others = new ArrayList<HudModule>(elements);
        others.remove(dragged);

        float[] startsX = new float[others.size()];
        float[] sizesX = new float[others.size()];
        float[] startsY = new float[others.size()];
        float[] sizesY = new float[others.size()];
        for (int i = 0; i < others.size(); i++) {
            HudModule other = others.get(i);
            startsX[i] = other.getX(resolution);
            sizesX[i] = other.getWidth();
            startsY[i] = other.getY(resolution);
            sizesY[i] = other.getHeight();
        }

        Snapping.Result resultX = Snapping.snap(mouseX - grabOffsetX, dragged.getWidth(),
                resolution.getScaledWidth(), startsX, sizesX, SNAP_THRESHOLD, EDGE_MARGIN);
        Snapping.Result resultY = Snapping.snap(mouseY - grabOffsetY, dragged.getHeight(),
                resolution.getScaledHeight(), startsY, sizesY, SNAP_THRESHOLD, EDGE_MARGIN);

        snappedX = resultX.snapped;
        snappedY = resultY.snapped;
        dragged.setPosition(resolution, resultX.value, resultY.value);
    }

    private void drawGuides(ScaledResolution resolution) {
        if (dragged == null) {
            return;
        }
        int guideColour = RenderUtil.withAlpha(Theme.accent(), 0.55f);
        float x = dragged.getX(resolution);
        float y = dragged.getY(resolution);
        if (snappedX) {
            RenderUtil.rect(x, 0.0f, 0.6f, resolution.getScaledHeight(), guideColour);
            RenderUtil.rect(x + dragged.getWidth(), 0.0f, 0.6f, resolution.getScaledHeight(), guideColour);
        }
        if (snappedY) {
            RenderUtil.rect(0.0f, y, resolution.getScaledWidth(), 0.6f, guideColour);
            RenderUtil.rect(0.0f, y + dragged.getHeight(), resolution.getScaledWidth(), 0.6f, guideColour);
        }
    }

    private void drawHint() {
        String hint = "Drag to move  •  Scroll over an element to resize  •  Esc to finish";
        float textWidth = Fonts.SMALL.getWidth(hint);
        float boxWidth = textWidth + 20.0f;
        float boxX = (width - boxWidth) / 2.0f;
        float boxY = height - 30.0f;
        RenderUtil.roundedRect(boxX, boxY, boxWidth, 20.0f, 5.0, RenderUtil.withAlpha(Theme.panel(), 220));
        RenderUtil.roundedOutline(boxX, boxY, boxWidth, 20.0f, 5.0, 1.0f, Theme.border());
        Fonts.SMALL.drawCentred(hint, width / 2.0f, boxY + 4.5f, Theme.textMuted());
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        ScaledResolution resolution = new ScaledResolution(mc);
        List<HudModule> elements = elements();

        // Walk backwards so the element drawn last, and therefore on top, is picked first.
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudModule element = elements.get(i);
            float x = element.getX(resolution);
            float y = element.getY(resolution);
            if (!RenderUtil.isInside(mouseX, mouseY, x, y, element.getWidth(), element.getHeight())) {
                continue;
            }
            if (mouseButton == 0) {
                dragged = element;
                grabOffsetX = mouseX - x;
                grabOffsetY = mouseY - y;
            }
            return;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragged = null;
        snappedX = false;
        snappedY = false;
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        int mouseX = Mouse.getEventX() * resolution.getScaledWidth() / mc.displayWidth;
        int mouseY = resolution.getScaledHeight() - Mouse.getEventY() * resolution.getScaledHeight() / mc.displayHeight - 1;

        for (HudModule element : elements()) {
            float x = element.getX(resolution);
            float y = element.getY(resolution);
            if (RenderUtil.isInside(mouseX, mouseY, x, y, element.getWidth(), element.getHeight())) {
                element.adjustScale(wheel > 0 ? 0.05 : -0.05);
                return;
            }
        }
    }
}
