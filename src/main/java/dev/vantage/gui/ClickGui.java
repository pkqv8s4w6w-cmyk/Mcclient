package dev.vantage.gui;

import dev.vantage.Vantage;
import dev.vantage.gui.component.ModuleRow;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The client's main interface.
 *
 * <p>A single centred window: categories down the left, modules in the middle, each module's
 * settings expanding in place beneath it. Everything is laid out in one top-down flow inside a
 * scissor rectangle, so scrolling and clipping stay correct without any component needing to know
 * it is in a scroll view.
 */
public class ClickGui extends GuiScreen {

    public enum Background { DIM, GRADIENT, BLUR }

    private static final float WINDOW_WIDTH = 540.0f;
    private static final float WINDOW_HEIGHT = 348.0f;
    private static final float RAIL_WIDTH = 66.0f;
    private static final float HEADER_HEIGHT = 46.0f;
    private static final float CONTENT_PADDING = 10.0f;
    private static final float SCROLLBAR_WIDTH = 3.0f;
    private static final float SEARCH_HEIGHT = 18.0f;

    private final Map<Category, List<ModuleRow>> rowsByCategory = new EnumMap<Category, List<ModuleRow>>(Category.class);
    private final List<ModuleRow> allRows = new ArrayList<ModuleRow>();

    private final Animated openProgress = new Animated(0.0, 0.10);
    private final Animated scroll = new Animated(0.0, 0.07);
    private final Animated railIndicator = new Animated(0.0, 0.09);

    private Category selected = Category.ANALYSIS;
    private String search = "";
    private boolean searchFocused;
    private float scrollTarget;
    private float contentOverflow;

    private ShaderGroup blur;
    private boolean blurUnavailable;

    private Background background = Background.GRADIENT;

    public ClickGui() {
        for (Module module : Vantage.instance().modules().getModules()) {
            ModuleRow row = new ModuleRow(module);
            allRows.add(row);
            List<ModuleRow> bucket = rowsByCategory.get(module.getCategory());
            if (bucket == null) {
                bucket = new ArrayList<ModuleRow>();
                rowsByCategory.put(module.getCategory(), bucket);
            }
            bucket.add(row);
        }
    }

    public void setBackground(Background background) {
        this.background = background;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        openProgress.snapTo(0.0);
        openProgress.setTarget(1.0);
    }

    @Override
    public void onGuiClosed() {
        // Persist as soon as the screen closes rather than only at shutdown, so a crash later in
        // the session does not lose the changes just made.
        Vantage.instance().saveConfig();
        if (blur != null) {
            blur.deleteShaderGroup();
            blur = null;
        }
    }

    // -- layout -----------------------------------------------------------------------------

    private float windowX() {
        return (width - WINDOW_WIDTH) / 2.0f;
    }

    private float windowY() {
        return (height - WINDOW_HEIGHT) / 2.0f;
    }

    private float contentX() {
        return windowX() + RAIL_WIDTH + CONTENT_PADDING;
    }

    private float contentY() {
        return windowY() + HEADER_HEIGHT;
    }

    private float contentWidth() {
        return WINDOW_WIDTH - RAIL_WIDTH - CONTENT_PADDING * 2.0f - SCROLLBAR_WIDTH - 3.0f;
    }

    private float contentHeight() {
        return WINDOW_HEIGHT - HEADER_HEIGHT - CONTENT_PADDING;
    }

    /** Rows to show: search results across every category, or the selected category's own. */
    private List<ModuleRow> visibleRows() {
        if (!search.trim().isEmpty()) {
            String needle = search.trim().toLowerCase(java.util.Locale.ROOT);
            List<ModuleRow> matches = new ArrayList<ModuleRow>();
            for (ModuleRow row : allRows) {
                Module module = row.getModule();
                if (module.getName().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || module.getDescription().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    matches.add(row);
                }
            }
            return matches;
        }
        List<ModuleRow> bucket = rowsByCategory.get(selected);
        return bucket == null ? new ArrayList<ModuleRow>() : bucket;
    }

    // -- rendering --------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackground(partialTicks);

        double opened = openProgress.get();
        // Scale up very slightly as it opens; a pure fade reads as sluggish.
        float eased = (float) Easing.outQuint(opened);
        int fade = (int) (255 * eased);

        float scale = 0.97f + 0.03f * eased;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(width / 2.0f, height / 2.0f, 0.0f);
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1.0f);
        net.minecraft.client.renderer.GlStateManager.translate(-width / 2.0f, -height / 2.0f, 0.0f);

        RenderUtil.shadow(windowX(), windowY(), WINDOW_WIDTH, WINDOW_HEIGHT, Theme.PANEL_RADIUS, 8,
                RenderUtil.withAlpha(0xFF000000, (int) (150 * eased)));
        RenderUtil.roundedRect(windowX(), windowY(), WINDOW_WIDTH, WINDOW_HEIGHT, Theme.PANEL_RADIUS,
                RenderUtil.withAlpha(Theme.PANEL, fade));

        drawRail(mouseX, mouseY, fade);
        drawHeader(mouseX, mouseY, fade);
        drawModules(mouseX, mouseY);

        RenderUtil.roundedOutline(windowX(), windowY(), WINDOW_WIDTH, WINDOW_HEIGHT, Theme.PANEL_RADIUS,
                1.0f, RenderUtil.withAlpha(Theme.BORDER, fade));

        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    private void drawBackground(float partialTicks) {
        if (background == Background.BLUR && !blurUnavailable) {
            if (applyBlur(partialTicks)) {
                return;
            }
        }
        if (background == Background.GRADIENT) {
            drawGradientRect(0, 0, width, height, 0x70101014, 0xC0050506);
        } else {
            RenderUtil.rect(0, 0, width, height, Theme.BACKDROP);
        }
    }

    /**
     * Runs the vanilla blur post-process over the frame behind the window.
     *
     * @return true if the blur ran; false means the caller should draw a plain backdrop instead
     */
    private boolean applyBlur(float partialTicks) {
        if (!OpenGlHelper.isFramebufferEnabled()) {
            blurUnavailable = true;
            return false;
        }
        try {
            if (blur == null) {
                blur = new ShaderGroup(mc.getTextureManager(), mc.getResourceManager(),
                        mc.getFramebuffer(), new ResourceLocation("shaders/post/blur.json"));
                blur.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
            }
            blur.loadShaderGroup(partialTicks);
            // Framebuffer work leaves the GL state set up for the pass, not for our 2D drawing.
            net.minecraft.client.renderer.GlStateManager.enableAlpha();
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            net.minecraft.client.renderer.GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            return true;
        } catch (Throwable failure) {
            // Shader support varies wildly across drivers. Stop trying and use the plain backdrop.
            blurUnavailable = true;
            blur = null;
            Vantage.LOGGER.warn("Background blur unavailable on this driver; using a plain backdrop", failure);
            return false;
        }
    }

    private void drawRail(int mouseX, int mouseY, int fade) {
        float railX = windowX();
        float railY = windowY();
        RenderUtil.roundedRect(railX, railY, RAIL_WIDTH, WINDOW_HEIGHT, Theme.PANEL_RADIUS,
                RenderUtil.withAlpha(Theme.RAIL, fade));
        // Square off the rail's right edge so it meets the content area cleanly.
        RenderUtil.rect(railX + RAIL_WIDTH - Theme.PANEL_RADIUS, railY, Theme.PANEL_RADIUS, WINDOW_HEIGHT,
                RenderUtil.withAlpha(Theme.RAIL, fade));

        Fonts.TITLE.drawString("Vantage", railX + 11.0f, railY + 13.0f, RenderUtil.withAlpha(Theme.TEXT, fade));
        Fonts.TINY.drawString("v" + Vantage.VERSION, railX + 12.0f, railY + 30.0f,
                RenderUtil.withAlpha(Theme.TEXT_FAINT, fade));

        Category[] categories = Category.values();
        float itemHeight = 27.0f;
        float firstY = railY + HEADER_HEIGHT + 8.0f;

        int selectedIndex = 0;
        for (int i = 0; i < categories.length; i++) {
            if (categories[i] == selected) {
                selectedIndex = i;
            }
        }
        railIndicator.setTarget(selectedIndex);
        float indicatorY = firstY + (float) (railIndicator.get() * itemHeight);
        RenderUtil.roundedRect(railX + 6.0f, indicatorY, RAIL_WIDTH - 12.0f, itemHeight - 3.0f, 4.0,
                RenderUtil.withAlpha(Theme.accentDim(), fade));
        RenderUtil.roundedRect(railX + 6.0f, indicatorY + 5.0f, 2.0f, itemHeight - 13.0f, 1.0,
                RenderUtil.withAlpha(Theme.accent(), fade));

        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            float itemY = firstY + i * itemHeight;
            boolean hovered = mouseX >= railX + 6 && mouseX <= railX + RAIL_WIDTH - 6
                    && mouseY >= itemY && mouseY <= itemY + itemHeight - 3;
            int colour = category == selected ? Theme.accent() : (hovered ? Theme.TEXT : Theme.TEXT_MUTED);
            Fonts.SMALL.drawString(category.getDisplayName(), railX + 14.0f, itemY + 7.0f,
                    RenderUtil.withAlpha(colour, fade));
        }
    }

    private void drawHeader(int mouseX, int mouseY, int fade) {
        float headerX = contentX();
        float headerY = windowY() + 11.0f;
        float fieldWidth = contentWidth();

        float textY = headerY + (SEARCH_HEIGHT - Fonts.SMALL.getHeight()) / 2.0f;
        RenderUtil.roundedRect(headerX, headerY, fieldWidth, SEARCH_HEIGHT, 5.0,
                RenderUtil.withAlpha(0xFF101216, fade));
        RenderUtil.roundedOutline(headerX, headerY, fieldWidth, SEARCH_HEIGHT, 5.0, 1.0f,
                RenderUtil.withAlpha(searchFocused ? Theme.accent() : Theme.BORDER, fade));

        if (search.isEmpty() && !searchFocused) {
            Fonts.SMALL.drawString("Search modules", headerX + 8.0f, textY,
                    RenderUtil.withAlpha(Theme.TEXT_FAINT, fade));
        } else {
            float endX = Fonts.SMALL.drawString(search, headerX + 8.0f, textY,
                    RenderUtil.withAlpha(Theme.TEXT, fade));
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                RenderUtil.rect(endX + 1.0f, textY, 0.8f, Fonts.SMALL.getHeight() - 2.0f,
                        RenderUtil.withAlpha(Theme.accent(), fade));
            }
        }

        String hint = search.trim().isEmpty() ? selected.getDescription() : visibleRows().size() + " matching";
        Fonts.TINY.drawRightAligned(hint, headerX + fieldWidth - 2.0f, headerY + SEARCH_HEIGHT + 3.0f,
                RenderUtil.withAlpha(Theme.TEXT_FAINT, fade));
    }

    private void drawModules(int mouseX, int mouseY) {
        List<ModuleRow> rows = visibleRows();

        RenderUtil.beginScissor(contentX(), contentY(), contentWidth() + SCROLLBAR_WIDTH + 3.0f, contentHeight());

        float offset = (float) scroll.get();
        float cursor = contentY() - offset;
        float gap = 4.0f;

        for (ModuleRow row : rows) {
            float rowHeight = row.getHeight();
            // Bounds are set even for rows that are not drawn, so one scrolled out of view cannot
            // keep stale coordinates from an earlier frame and swallow a click meant for another.
            row.setBounds(contentX(), cursor, contentWidth());
            if (cursor + rowHeight >= contentY() - 4.0f && cursor <= contentY() + contentHeight() + 4.0f) {
                row.render(mouseX, mouseY);
            }
            cursor += rowHeight + gap;
        }

        float totalHeight = cursor + offset - contentY();
        contentOverflow = Math.max(0.0f, totalHeight - contentHeight());

        if (rows.isEmpty()) {
            Fonts.SMALL.drawCentred("Nothing here yet", contentX() + contentWidth() / 2.0f,
                    contentY() + contentHeight() / 2.0f - 8.0f, Theme.TEXT_FAINT);
        }

        RenderUtil.endScissor();

        if (contentOverflow > 1.0f) {
            float trackX = contentX() + contentWidth() + 4.0f;
            float visibleFraction = contentHeight() / (contentHeight() + contentOverflow);
            float thumbHeight = Math.max(18.0f, contentHeight() * visibleFraction);
            float travel = contentHeight() - thumbHeight;
            float thumbY = contentY() + travel * (offset / contentOverflow);
            RenderUtil.roundedRect(trackX, contentY(), SCROLLBAR_WIDTH, contentHeight(),
                    SCROLLBAR_WIDTH / 2.0, 0x18FFFFFF);
            RenderUtil.roundedRect(trackX, thumbY, SCROLLBAR_WIDTH, thumbHeight,
                    SCROLLBAR_WIDTH / 2.0, RenderUtil.withAlpha(Theme.accent(), 0.75f));
        }
    }

    // -- input ------------------------------------------------------------------------------

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        scrollTarget -= Math.signum(wheel) * 32.0f;
        clampScroll();
    }

    private void clampScroll() {
        if (scrollTarget < 0.0f) {
            scrollTarget = 0.0f;
        }
        if (scrollTarget > contentOverflow) {
            scrollTarget = contentOverflow;
        }
        scroll.setTarget(scrollTarget);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        float headerY = windowY() + 11.0f;
        boolean onSearch = mouseX >= contentX() && mouseX <= contentX() + contentWidth()
                && mouseY >= headerY && mouseY <= headerY + SEARCH_HEIGHT;
        if (mouseButton == 0) {
            searchFocused = onSearch;
        }
        if (onSearch) {
            if (mouseButton == 1) {
                search = "";
            }
            return;
        }

        // Rail.
        Category[] categories = Category.values();
        float firstY = windowY() + HEADER_HEIGHT + 8.0f;
        for (int i = 0; i < categories.length; i++) {
            float itemY = firstY + i * 27.0f;
            if (mouseX >= windowX() + 6 && mouseX <= windowX() + RAIL_WIDTH - 6
                    && mouseY >= itemY && mouseY <= itemY + 24.0f) {
                if (selected != categories[i]) {
                    selected = categories[i];
                    scrollTarget = 0.0f;
                    scroll.setTarget(0.0);
                }
                return;
            }
        }

        // Modules, but only clicks that land inside the scroll viewport.
        if (mouseY < contentY() || mouseY > contentY() + contentHeight()) {
            return;
        }
        for (ModuleRow row : visibleRows()) {
            if (row.mouseClicked(mouseX, mouseY, mouseButton)) {
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        for (ModuleRow row : allRows) {
            row.mouseReleased(state);
        }
    }

    @Override
    protected void keyTyped(char typedCharacter, int keyCode) throws java.io.IOException {
        // A component that is capturing input gets first refusal, so typing an API key or binding
        // a key cannot be swallowed by the screen's own shortcuts.
        for (ModuleRow row : visibleRows()) {
            if (row.isCapturingInput() && row.keyTyped(typedCharacter, keyCode)) {
                return;
            }
        }

        if (searchFocused) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                searchFocused = false;
                search = "";
                return;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (!search.isEmpty()) {
                    search = search.substring(0, search.length() - 1);
                }
                return;
            }
            if (typedCharacter >= 32 && typedCharacter != 127) {
                search += typedCharacter;
                return;
            }
            return;
        }

        for (ModuleRow row : visibleRows()) {
            if (row.keyTyped(typedCharacter, keyCode)) {
                return;
            }
        }
        super.keyTyped(typedCharacter, keyCode);
    }

    /** Collapses every open module, used when reopening so the list starts tidy. */
    public void collapseAll() {
        for (ModuleRow row : allRows) {
            row.collapse();
        }
    }
}
