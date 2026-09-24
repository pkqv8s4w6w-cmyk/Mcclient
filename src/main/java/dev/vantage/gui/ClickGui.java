package dev.vantage.gui;

import dev.vantage.Vantage;
import dev.vantage.gui.component.ModuleCard;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.page.ConfigsPage;
import dev.vantage.gui.page.FriendsPage;
import dev.vantage.gui.page.ModulesPage;
import dev.vantage.gui.page.Page;
import dev.vantage.gui.page.SettingsPage;
import dev.vantage.gui.page.ThemesPage;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.gui.widget.TextField;
import dev.vantage.gui.widget.Widgets;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.module.impl.client.ClickGuiModule;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's menu.
 *
 * <p>A sidebar of categories and pages on the left - each with an icon, a title and a line saying
 * what it holds - and the selected page on the right, headed by its title with its own controls
 * beside it. Modules are cards that open in place to show their settings.
 *
 * <p>Everything is laid out on a fixed 660 by 440 canvas and scaled to fit the screen, so the menu
 * looks the same at every GUI scale and never runs off a small window. Mouse positions are mapped
 * back onto the canvas, so no page ever has to think about the scale.
 */
public class ClickGui extends GuiScreen {

    public enum Background { DIM, GRADIENT, BLUR, NONE }

    private static final float W = 660.0f;
    private static final float H = 440.0f;
    private static final float SIDEBAR = 172.0f;
    private static final float RADIUS = 10.0f;
    private static final float CONTENT_PAD = 20.0f;
    private static final float BODY_TOP = 66.0f;
    private static final float NAV_HEIGHT = 23.0f;
    private static final float NAV_TOP = 88.0f;

    /** One entry in the sidebar. */
    private static final class NavItem {
        final String title;
        final String subtitle;
        final char icon;
        final Page page;

        NavItem(String title, String subtitle, char icon, Page page) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.page = page;
        }
    }

    private final List<ModuleCard> cards = new ArrayList<ModuleCard>();
    private final List<NavItem> moduleNav = new ArrayList<NavItem>();
    private final List<NavItem> clientNav = new ArrayList<NavItem>();
    private final TextField search = new TextField("Search features...", 32, Icons.SEARCH);
    private final Page searchPage;

    private NavItem selected;
    private final Animated openProgress = new Animated(0.0, 0.05);
    private final Animated indicatorY = new Animated(NAV_TOP, 0.045);
    private final Animated pageFade = new Animated(1.0, 0.06);
    private final Animated scroll = new Animated(0.0, 0.045);
    private float scrollTarget;
    private float bodyHeight;

    private float scale = 1.0f;
    private float originX;
    private float originY;

    private ShaderGroup blur;
    private boolean blurUnavailable;

    public ClickGui() {
        for (Module module : Vantage.instance().modules().getModules()) {
            cards.add(new ModuleCard(module));
        }
        for (Category category : Category.values()) {
            if (category.isListed()) {
                moduleNav.add(new NavItem(category.getDisplayName(), category.getDescription(), category.getIcon(),
                        ModulesPage.forCategory(category, cards)));
            }
        }
        clientNav.add(new NavItem("Configs", "Save your loadouts", Icons.FOLDER_OPEN, new ConfigsPage()));
        clientNav.add(new NavItem("Friends", "Players you never hit", Icons.USERS, new FriendsPage()));
        clientNav.add(new NavItem("Themes", "Choose your colours", Icons.PALETTE, new ThemesPage()));
        clientNav.add(new NavItem("Settings", "Customise your client", Icons.SETTINGS, new SettingsPage()));
        searchPage = ModulesPage.forSearch(search::getText, cards);
        search.onChange(() -> resetScroll());
        selected = moduleNav.get(0);
    }

    private static ClickGuiModule menu() {
        return Vantage.instance().modules().get(ClickGuiModule.class);
    }

    private Page currentPage() {
        return search.getText().trim().isEmpty() ? selected.page : searchPage;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        openProgress.snapTo(0.0);
        openProgress.setTarget(1.0);
        Keyboard.enableRepeatEvents(true);
        currentPage().onOpen();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        search.setFocused(false);
        // Persist as soon as the menu closes, so a crash later cannot lose what was just changed.
        Vantage.instance().saveConfig();
        if (blur != null) {
            blur.deleteShaderGroup();
            blur = null;
        }
    }

    // -- layout -----------------------------------------------------------------------------

    private void layout() {
        float fit = Math.min((width - 24.0f) / W, (height - 24.0f) / H);
        float eased = (float) Easing.outQuint(openProgress.get());
        scale = Math.max(0.4f, Math.min(1.6f, fit * menu().getUiScale())) * (0.97f + 0.03f * eased);
        originX = width / 2.0f - W * scale / 2.0f;
        originY = height / 2.0f - H * scale / 2.0f;
    }

    private float toCanvasX(float screenX) {
        return (screenX - originX) / scale;
    }

    private float toCanvasY(float screenY) {
        return (screenY - originY) / scale;
    }

    private float bodyViewportHeight() {
        return H - BODY_TOP - 12.0f;
    }

    private boolean inBody(float x, float y) {
        return x >= SIDEBAR && x <= W && y >= BODY_TOP && y <= BODY_TOP + bodyViewportHeight();
    }

    private void resetScroll() {
        scrollTarget = 0.0f;
        scroll.setTarget(0.0);
    }

    // -- drawing ----------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackdrop(partialTicks);
        layout();
        float mx = toCanvasX(mouseX);
        float my = toCanvasY(mouseY);
        int alpha = (int) (255 * Easing.outQuint(openProgress.get()));

        GlStateManager.pushMatrix();
        GlStateManager.translate(originX, originY, 0.0f);
        GlStateManager.scale(scale, scale, 1.0f);

        RenderUtil.shadow(0, 0, W, H, RADIUS, 10, RenderUtil.withAlpha(0xFF000000, (int) (150 * alpha / 255.0f)));
        RenderUtil.roundedRect(0, 0, W, H, RADIUS, RenderUtil.withAlpha(Theme.panel(), (int) (245 * alpha / 255.0f)));
        drawSidebar(mx, my);
        drawContent(mx, my);
        RenderUtil.roundedOutline(0, 0, W, H, RADIUS, 1.0f, Theme.border());

        GlStateManager.popMatrix();
    }

    private void drawBackdrop(float partialTicks) {
        Background background = menu().getBackground();
        if (background == Background.BLUR && !blurUnavailable && applyBlur(partialTicks)) {
            return;
        }
        if (background == Background.GRADIENT) {
            drawGradientRect(0, 0, width, height, 0x50000000, 0xB0000000);
        } else if (background == Background.DIM || background == Background.BLUR) {
            RenderUtil.rect(0, 0, width, height, 0x88000000);
        }
    }

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
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            RenderUtil.rect(0, 0, width, height, 0x40000000);
            return true;
        } catch (Throwable failure) {
            blurUnavailable = true;
            blur = null;
            Vantage.LOGGER.warn("Background blur unavailable on this driver; using a plain backdrop", failure);
            return false;
        }
    }

    private void drawSidebar(float mx, float my) {
        // The sidebar is its own plane, squared off where it meets the content.
        RenderUtil.roundedRect(0, 0, SIDEBAR, H, RADIUS, Theme.rail());
        RenderUtil.rect(SIDEBAR - RADIUS, 0, RADIUS, H, Theme.rail());
        RenderUtil.rect(SIDEBAR - 1.0f, 0, 1.0f, H, Theme.divider());

        Fonts.ICONS_LARGE.drawString(String.valueOf(Icons.CROWN), 16.0f, 16.0f, Theme.accent());
        Fonts.TITLE.drawString(Vantage.MOD_NAME, 36.0f, 13.0f, Theme.text());
        Fonts.TINY.drawString("Private Edition " + Vantage.VERSION, 36.0f, 27.0f, Theme.textFaint());

        search.render(12.0f, 44.0f, SIDEBAR - 24.0f, 20.0f);

        boolean searching = !search.getText().trim().isEmpty();
        Widgets.sectionLabel("MODULES", 16.0f, NAV_TOP - 11.0f);
        float clientTop = NAV_TOP + moduleNav.size() * NAV_HEIGHT + 20.0f;
        Widgets.sectionLabel("CLIENT", 16.0f, clientTop - 11.0f);

        indicatorY.setTarget(navY(selected));
        if (!searching) {
            float y = (float) indicatorY.get();
            RenderUtil.roundedRect(8.0f, y, SIDEBAR - 16.0f, NAV_HEIGHT - 2.0f, 6.0, Theme.row());
            RenderUtil.roundedRect(8.0f, y + 5.0f, 2.0f, NAV_HEIGHT - 12.0f, 1.0, Theme.accent());
        }
        for (NavItem item : moduleNav) {
            drawNavItem(item, navY(item), mx, my, searching);
        }
        for (NavItem item : clientNav) {
            drawNavItem(item, navY(item), mx, my, searching);
        }
        drawStatusCard();
    }

    private float navY(NavItem item) {
        int index = moduleNav.indexOf(item);
        if (index >= 0) {
            return NAV_TOP + index * NAV_HEIGHT;
        }
        return NAV_TOP + moduleNav.size() * NAV_HEIGHT + 20.0f + clientNav.indexOf(item) * NAV_HEIGHT;
    }

    private void drawNavItem(NavItem item, float y, float mx, float my, boolean searching) {
        boolean active = item == selected && !searching;
        boolean hovered = mx >= 8.0f && mx <= SIDEBAR - 8.0f && my >= y && my <= y + NAV_HEIGHT - 2.0f;
        if (hovered && !active) {
            RenderUtil.roundedRect(8.0f, y, SIDEBAR - 16.0f, NAV_HEIGHT - 2.0f, 6.0, RenderUtil.withAlpha(Theme.row(), 140));
        }
        int iconColour = active ? Theme.accent() : (hovered ? Theme.text() : Theme.textMuted());
        Fonts.ICONS.drawString(String.valueOf(item.icon), 18.0f, y + 6.0f, iconColour);
        Fonts.SMALL_BOLD.drawString(item.title, 34.0f, y + 2.5f, active || hovered ? Theme.text() : Theme.textMuted());
        Fonts.TINY.drawString(Fonts.TINY.trimToWidth(item.subtitle, SIDEBAR - 50.0f), 34.0f, y + 12.0f, Theme.textFaint());
    }

    private void drawStatusCard() {
        float y = H - 46.0f;
        RenderUtil.roundedRect(10.0f, y, SIDEBAR - 20.0f, 36.0f, 7.0, Theme.row());
        RenderUtil.circle(22.0f, y + 12.0f, 3.0f, Theme.safe());
        Fonts.SMALL_BOLD.drawString(Vantage.MOD_NAME, 30.0f, y + 6.5f, Theme.text());
        int on = 0;
        for (Module module : Vantage.instance().modules().getModules()) {
            if (module.isEnabled() && module.getCategory() != Category.CLIENT) {
                on++;
            }
        }
        String line = "Profile " + Vantage.instance().getActiveProfile() + "  •  " + on + " on";
        Fonts.TINY.drawString(Fonts.TINY.trimToWidth(line, SIDEBAR - 46.0f), 18.0f, y + 21.0f, Theme.textFaint());
    }

    private void drawContent(float mx, float my) {
        Page page = currentPage();
        float left = SIDEBAR + CONTENT_PAD;
        float right = W - CONTENT_PAD;
        float fade = (float) pageFade.get();
        pageFade.setTarget(1.0);

        Fonts.TITLE_LARGE.drawString(page.title(), left, 16.0f, RenderUtil.withAlpha(Theme.text(), fade));
        String subtitle = page == searchPage ? "Results for \"" + search.getText().trim() + "\"" : page.subtitle();
        Fonts.SMALL.drawString(subtitle, left, 36.0f, RenderUtil.withAlpha(Theme.textFaint(), fade));
        page.renderHeaderControls(right, 20.0f, mx, my);
        RenderUtil.rect(SIDEBAR, BODY_TOP - 8.0f, W - SIDEBAR, 1.0f, Theme.divider());

        float viewport = bodyViewportHeight();
        scrollTarget = Math.max(0.0f, Math.min(scrollTarget, Math.max(0.0f, bodyHeight - viewport + 8.0f)));
        scroll.setTarget(scrollTarget);
        float offset = (float) scroll.get();

        RenderUtil.beginScissor(originX + SIDEBAR * scale, originY + (BODY_TOP - 4.0f) * scale,
                (W - SIDEBAR) * scale, (viewport + 4.0f) * scale);
        boolean mouseInBody = inBody(mx, my);
        float bodyMouseX = mouseInBody ? mx : -1000.0f;
        float bodyMouseY = mouseInBody ? my : -1000.0f;
        // Sliders being dragged keep tracking outside the viewport; hover does not.
        if (Mouse.isButtonDown(0)) {
            bodyMouseX = mx;
            bodyMouseY = my;
        }
        bodyHeight = page.renderBody(left, BODY_TOP - offset + (1.0f - fade) * 6.0f, right - left, bodyMouseX, bodyMouseY);
        RenderUtil.endScissor();

        float overflow = bodyHeight - viewport;
        if (overflow > 1.0f) {
            float track = viewport - 4.0f;
            float thumb = Math.max(18.0f, track * viewport / bodyHeight);
            float thumbY = BODY_TOP + (track - thumb) * Math.min(1.0f, offset / overflow);
            RenderUtil.roundedRect(W - 7.0f, thumbY, 3.0f, thumb, 1.5, RenderUtil.withAlpha(Theme.accent(), 0.5f));
        }
    }

    // -- input ------------------------------------------------------------------------------

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollTarget -= Math.signum(wheel) * 40.0f;
            scrollTarget = Math.max(0.0f, scrollTarget);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, button);
        layout();
        float mx = toCanvasX(mouseX);
        float my = toCanvasY(mouseY);

        if (search.mouseClicked(mx, my, button)) {
            return;
        }
        for (NavItem item : concat(moduleNav, clientNav)) {
            float y = navY(item);
            if (mx >= 8.0f && mx <= SIDEBAR - 8.0f && my >= y && my <= y + NAV_HEIGHT - 2.0f) {
                select(item);
                return;
            }
        }
        Page page = currentPage();
        if (my < BODY_TOP - 8.0f && mx > SIDEBAR) {
            page.headerClicked(mx, my, button);
            return;
        }
        if (inBody(mx, my)) {
            page.bodyClicked(mx, my, button);
        }
    }

    private void select(NavItem item) {
        if (item != selected || !search.getText().isEmpty()) {
            search.setText("");
            search.setFocused(false);
            selected = item;
            pageFade.snapTo(0.0);
            pageFade.setTarget(1.0);
            resetScroll();
            item.page.onOpen();
        }
    }

    private static List<NavItem> concat(List<NavItem> first, List<NavItem> second) {
        List<NavItem> all = new ArrayList<NavItem>(first);
        all.addAll(second);
        return all;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        currentPage().mouseReleased(state);
    }

    @Override
    protected void keyTyped(char character, int keyCode) throws java.io.IOException {
        if (search.isFocused()) {
            search.keyTyped(character, keyCode);
            return;
        }
        Page page = currentPage();
        if (page.isCapturingInput()) {
            page.keyTyped(character, keyCode);
            return;
        }
        if (page.keyTyped(character, keyCode)) {
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == menu().getKeybind().get()) {
            mc.displayGuiScreen(null);
            return;
        }
        // Typing anywhere starts a search, the quickest way to a module you know the name of.
        if (Character.isLetterOrDigit(character)) {
            search.setFocused(true);
            search.keyTyped(character, keyCode);
        }
    }
}
