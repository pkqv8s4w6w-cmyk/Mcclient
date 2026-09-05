package dev.vantage.gui;

import dev.vantage.Vantage;
import dev.vantage.gui.component.ModuleRow;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The client's main interface.
 *
 * <p>Categories down the left, one dense column of modules in the middle, each module's settings
 * expanding in place beneath it. Everything is laid out in a single top-down flow inside one
 * scissor rectangle, so scrolling and clipping stay correct without any component knowing it sits
 * in a scroll view.
 *
 * <p>Every measurement is a multiple of {@link Theme#UNIT}. That consistency is most of what
 * separates a layout that looks designed from one that looks assembled.
 */
public class ClickGui extends GuiScreen {

    public enum Background { DIM, GRADIENT, BLUR }

    private static final float WINDOW_WIDTH = 360.0f;
    private static final float WINDOW_HEIGHT = 286.0f;
    private static final float RAIL_WIDTH = 74.0f;
    private static final float PAD = Theme.UNIT * 2.0f;          // 8
    private static final float HEADER_HEIGHT = Theme.UNIT * 8.5f; // 34
    private static final float CATEGORY_HEIGHT = Theme.UNIT * 5.5f; // 22
    private static final float SEARCH_HEIGHT = Theme.UNIT * 4.0f;  // 16
    private static final float ROW_GAP = Theme.UNIT;
    private static final float SCROLLBAR_WIDTH = 3.0f;

    private final Map<Category, List<ModuleRow>> rowsByCategory = new EnumMap<Category, List<ModuleRow>>(Category.class);
    private final List<ModuleRow> allRows = new ArrayList<ModuleRow>();

    private final Animated openProgress = new Animated(0.0, 0.055);
    private final Animated scroll = new Animated(0.0, 0.045);
    private final Animated railIndicator = new Animated(0.0, 0.05);

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
        // the session cannot lose the changes just made.
        Vantage.instance().saveConfig();
        if (blur != null) {
            blur.deleteShaderGroup();
            blur = null;
        }
    }

    // -- layout -----------------------------------------------------------------------------

    private float windowX() {
        return Math.round((width - WINDOW_WIDTH) / 2.0f);
    }

    private float windowY() {
        return Math.round((height - WINDOW_HEIGHT) / 2.0f);
    }

    private float contentX() {
        return windowX() + RAIL_WIDTH + PAD;
    }

    private float contentY() {
        return windowY() + HEADER_HEIGHT;
    }

    private float contentWidth() {
        return WINDOW_WIDTH - RAIL_WIDTH - PAD * 2.0f - SCROLLBAR_WIDTH - Theme.UNIT;
    }

    private float contentHeight() {
        return WINDOW_HEIGHT - HEADER_HEIGHT - PAD;
    }

    private float categoryTop() {
        return windowY() + HEADER_HEIGHT + Theme.UNIT;
    }

    private List<ModuleRow> visibleRows() {
        if (!search.trim().isEmpty()) {
            String needle = search.trim().toLowerCase(Locale.ROOT);
            List<ModuleRow> matches = new ArrayList<ModuleRow>();
            for (ModuleRow row : allRows) {
                Module module = row.getModule();
                if (module.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || module.getDescription().toLowerCase(Locale.ROOT).contains(needle)) {
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

        float eased = (float) Easing.outQuint(openProgress.get());
        int fade = (int) (255 * eased);

        // A slight scale-up on open; a pure fade reads as sluggish.
        float scale = 0.98f + 0.02f * eased;
        GlStateManager.pushMatrix();
        GlStateManager.translate(width / 2.0f, height / 2.0f, 0.0f);
        GlStateManager.scale(scale, scale, 1.0f);
        GlStateManager.translate(-width / 2.0f, -height / 2.0f, 0.0f);

        RenderUtil.shadow(windowX(), windowY(), WINDOW_WIDTH, WINDOW_HEIGHT, Theme.PANEL_RADIUS, 6,
                RenderUtil.withAlpha(0xFF000000, (int) (120 * eased)));
        RenderUtil.roundedRect(windowX(), windowY(), WINDOW_WIDTH, WINDOW_HEIGHT, Theme.PANEL_RADIUS,
                RenderUtil.withAlpha(Theme.panel(), fade));

        drawRail(mouseX, mouseY, fade);
        drawSearch(mouseX, mouseY, fade);
        drawModules(mouseX, mouseY);

        RenderUtil.roundedOutline(windowX(), windowY(), WINDOW_WIDTH, WINDOW_HEIGHT, Theme.PANEL_RADIUS,
                1.0f, RenderUtil.withAlpha(Theme.border(), fade));

        drawTooltip(mouseX, mouseY);
        GlStateManager.popMatrix();
    }

    private void drawBackground(float partialTicks) {
        if (background == Background.BLUR && !blurUnavailable && applyBlur(partialTicks)) {
            return;
        }
        if (background == Background.GRADIENT) {
            drawGradientRect(0, 0, width, height, 0x60000000, 0xB0000000);
        } else {
            RenderUtil.rect(0, 0, width, height, Theme.backdrop());
        }
    }

    /**
     * Runs the vanilla blur post-process over the frame behind the window.
     *
     * @return true if the blur ran; false means draw a plain backdrop instead
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
            // Framebuffer work leaves GL set up for the pass, not for our 2D drawing.
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            return true;
        } catch (Throwable failure) {
            // Driver support for this varies too much to assume. Stop trying.
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
                RenderUtil.withAlpha(Theme.rail(), fade));
        // Square off the inner edge so the rail meets the content area cleanly.
        RenderUtil.rect(railX + RAIL_WIDTH - Theme.PANEL_RADIUS, railY, (float) Theme.PANEL_RADIUS,
                WINDOW_HEIGHT, RenderUtil.withAlpha(Theme.rail(), fade));

        Fonts.TITLE.drawString("Vantage", railX + PAD, railY + PAD + 1.0f,
                RenderUtil.withAlpha(Theme.text(), fade));
        Fonts.TINY.drawString("v" + Vantage.VERSION, railX + PAD + 1.0f, railY + PAD + 14.0f,
                RenderUtil.withAlpha(Theme.textFaint(), fade));

        Category[] categories = Category.values();
        int selectedIndex = 0;
        for (int i = 0; i < categories.length; i++) {
            if (categories[i] == selected) {
                selectedIndex = i;
            }
        }

        railIndicator.setTarget(selectedIndex);
        float indicatorY = categoryTop() + (float) (railIndicator.get() * CATEGORY_HEIGHT);
        RenderUtil.roundedRect(railX + Theme.UNIT, indicatorY, RAIL_WIDTH - Theme.UNIT * 2.0f,
                CATEGORY_HEIGHT - 2.0f, Theme.ROW_RADIUS, RenderUtil.withAlpha(Theme.accentDim(), fade));
        RenderUtil.roundedRect(railX + Theme.UNIT, indicatorY + 5.0f, 2.0f, CATEGORY_HEIGHT - 12.0f,
                1.0, RenderUtil.withAlpha(Theme.accent(), fade));

        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            float itemY = categoryTop() + i * CATEGORY_HEIGHT;
            boolean hovered = mouseX >= railX + Theme.UNIT && mouseX <= railX + RAIL_WIDTH - Theme.UNIT
                    && mouseY >= itemY && mouseY <= itemY + CATEGORY_HEIGHT - 2.0f;
            int colour = category == selected ? Theme.accent() : (hovered ? Theme.text() : Theme.textMuted());
            Fonts.SMALL.drawString(category.getDisplayName(), railX + Theme.UNIT * 3.0f,
                    itemY + (CATEGORY_HEIGHT - 2.0f - Fonts.SMALL.getHeight()) / 2.0f,
                    RenderUtil.withAlpha(colour, fade));
        }
    }

    private void drawSearch(int mouseX, int mouseY, int fade) {
        float searchX = contentX();
        float searchY = windowY() + PAD;
        float fieldWidth = contentWidth() + SCROLLBAR_WIDTH + Theme.UNIT;

        RenderUtil.roundedRect(searchX, searchY, fieldWidth, SEARCH_HEIGHT, Theme.ROW_RADIUS,
                RenderUtil.withAlpha(Theme.row(), fade));
        if (searchFocused) {
            RenderUtil.roundedOutline(searchX, searchY, fieldWidth, SEARCH_HEIGHT, Theme.ROW_RADIUS,
                    1.0f, RenderUtil.withAlpha(Theme.accent(), fade));
        }

        float textY = searchY + (SEARCH_HEIGHT - Fonts.SMALL.getHeight()) / 2.0f;
        if (search.isEmpty() && !searchFocused) {
            Fonts.SMALL.drawString("Search", searchX + Theme.UNIT * 1.5f, textY,
                    RenderUtil.withAlpha(Theme.textFaint(), fade));
        } else {
            float endX = Fonts.SMALL.drawString(search, searchX + Theme.UNIT * 1.5f, textY,
                    RenderUtil.withAlpha(Theme.text(), fade));
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                RenderUtil.rect(endX + 1.0f, textY, 0.8f, Fonts.SMALL.getHeight() - 2.0f,
                        RenderUtil.withAlpha(Theme.accent(), fade));
            }
        }
    }

    private void drawModules(int mouseX, int mouseY) {
        List<ModuleRow> rows = visibleRows();

        RenderUtil.beginScissor(contentX(), contentY(),
                contentWidth() + SCROLLBAR_WIDTH + Theme.UNIT, contentHeight());

        float offset = (float) scroll.get();
        float cursor = contentY() - offset;

        for (ModuleRow row : rows) {
            float rowHeight = row.getHeight();
            // Bounds are set even for rows that are not drawn, so one scrolled out of view cannot
            // keep stale coordinates and swallow a click meant for another row.
            row.setBounds(contentX(), cursor, contentWidth());
            if (cursor + rowHeight >= contentY() - 4.0f && cursor <= contentY() + contentHeight() + 4.0f) {
                row.render(mouseX, mouseY);
            }
            cursor += rowHeight + ROW_GAP;
        }

        contentOverflow = Math.max(0.0f, (cursor + offset - contentY()) - contentHeight());

        if (rows.isEmpty()) {
            Fonts.SMALL.drawCentred("Nothing here", contentX() + contentWidth() / 2.0f,
                    contentY() + contentHeight() / 2.0f - 6.0f, Theme.textFaint());
        }
        RenderUtil.endScissor();

        if (contentOverflow > 1.0f) {
            float trackX = contentX() + contentWidth() + Theme.UNIT;
            float visibleFraction = contentHeight() / (contentHeight() + contentOverflow);
            float thumbHeight = Math.max(16.0f, contentHeight() * visibleFraction);
            float travel = contentHeight() - thumbHeight;
            float thumbY = contentY() + travel * (offset / contentOverflow);
            RenderUtil.roundedRect(trackX, thumbY, SCROLLBAR_WIDTH, thumbHeight,
                    SCROLLBAR_WIDTH / 2.0, RenderUtil.withAlpha(Theme.accent(), 0.6f));
        }
    }

    /** Descriptions live here rather than on every row, so the list stays a list. */
    private void drawTooltip(int mouseX, int mouseY) {
        ModuleRow target = null;
        for (ModuleRow row : visibleRows()) {
            if (row.wantsTooltip()) {
                target = row;
                break;
            }
        }
        if (target == null) {
            return;
        }
        String text = target.getTooltip();
        float textWidth = Fonts.TINY.getWidth(text);
        float boxWidth = textWidth + Theme.UNIT * 3.0f;
        float boxHeight = Fonts.TINY.getHeight() + Theme.UNIT * 1.5f;

        // Keep it on screen rather than letting it run off the right edge.
        float boxX = Math.min(mouseX + 9.0f, width - boxWidth - 2.0f);
        float boxY = Math.min(mouseY + 9.0f, height - boxHeight - 2.0f);

        RenderUtil.shadow(boxX, boxY, boxWidth, boxHeight, Theme.CHIP_RADIUS, 4, 0x80000000);
        RenderUtil.roundedRect(boxX, boxY, boxWidth, boxHeight, Theme.CHIP_RADIUS, Theme.rail());
        RenderUtil.roundedOutline(boxX, boxY, boxWidth, boxHeight, Theme.CHIP_RADIUS, 1.0f, Theme.border());
        Fonts.TINY.drawString(text, boxX + Theme.UNIT * 1.5f, boxY + Theme.UNIT * 0.75f, Theme.textMuted());
    }

    // -- input ------------------------------------------------------------------------------

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        scrollTarget -= Math.signum(wheel) * (ModuleRow.HEADER_HEIGHT + ROW_GAP) * 2.0f;
        scrollTarget = Math.max(0.0f, Math.min(scrollTarget, contentOverflow));
        scroll.setTarget(scrollTarget);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        float searchY = windowY() + PAD;
        boolean onSearch = mouseX >= contentX() && mouseX <= contentX() + contentWidth()
                && mouseY >= searchY && mouseY <= searchY + SEARCH_HEIGHT;
        if (mouseButton == 0) {
            searchFocused = onSearch;
        }
        if (onSearch) {
            if (mouseButton == 1) {
                search = "";
            }
            return;
        }

        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            float itemY = categoryTop() + i * CATEGORY_HEIGHT;
            if (mouseX >= windowX() + Theme.UNIT && mouseX <= windowX() + RAIL_WIDTH - Theme.UNIT
                    && mouseY >= itemY && mouseY <= itemY + CATEGORY_HEIGHT - 2.0f) {
                if (selected != categories[i]) {
                    selected = categories[i];
                    scrollTarget = 0.0f;
                    scroll.setTarget(0.0);
                }
                return;
            }
        }

        // Only clicks inside the scroll viewport reach the module list.
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
        // A component capturing input gets first refusal, so typing an API key or binding a key
        // cannot be swallowed by the screen's own shortcuts.
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

    public void collapseAll() {
        for (ModuleRow row : allRows) {
            row.collapse();
        }
    }
}
