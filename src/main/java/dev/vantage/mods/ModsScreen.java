package dev.vantage.mods;

import dev.vantage.Vantage;
import dev.vantage.gui.Theme;
import dev.vantage.gui.component.SettingComponent;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.setting.Setting;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Lists installed mods and edits the settings of those using Forge's standard config format. */
public class ModsScreen extends GuiScreen {

    private static final float WIDTH = 540.0f;
    private static final float HEIGHT = 320.0f;
    private static final float LIST_WIDTH = 190.0f;
    private static final float ROW_HEIGHT = 24.0f;
    private static final float PADDING = 10.0f;

    private final List<InstalledMod> mods = new ArrayList<InstalledMod>();
    private final List<File> unattributed = new ArrayList<File>();
    private final List<SettingComponent> components = new ArrayList<SettingComponent>();

    private ForgeConfigFile open;
    private String openLabel = "";
    private String notice = "";
    private float listScroll;
    private float settingsScroll;

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        mods.clear();
        unattributed.clear();
        try {
            mods.addAll(ModsManager.listMods());
            unattributed.addAll(ModsManager.findUnattributedConfigs(mods));
        } catch (Throwable failure) {
            Vantage.LOGGER.error("Could not enumerate mods", failure);
        }
    }

    private float left() {
        return (width - WIDTH) / 2.0f;
    }

    private float top() {
        return (height - HEIGHT) / 2.0f;
    }

    private int totalRows() {
        return mods.size() + (unattributed.isEmpty() ? 0 : unattributed.size() + 1);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, width, height, 0x70101014, 0xC0050506);

        RenderUtil.shadow(left(), top(), WIDTH, HEIGHT, Theme.PANEL_RADIUS, 8, 0x96000000);
        RenderUtil.roundedRect(left(), top(), WIDTH, HEIGHT, Theme.PANEL_RADIUS, Theme.panel());
        RenderUtil.roundedOutline(left(), top(), WIDTH, HEIGHT, Theme.PANEL_RADIUS, 1.0f, Theme.border());

        Fonts.TITLE.drawString("Mods", left() + PADDING, top() + 10.0f, Theme.text());
        Fonts.TINY.drawString(mods.size() + " installed", left() + PADDING, top() + 28.0f, Theme.textFaint());

        drawList(mouseX, mouseY);
        drawSettings(mouseX, mouseY);
    }

    private void drawList(int mouseX, int mouseY) {
        float x = left() + PADDING;
        float y = top() + 42.0f;
        float viewportHeight = HEIGHT - 52.0f;

        RenderUtil.beginScissor(x, y, LIST_WIDTH, viewportHeight);
        float cursor = y - listScroll;

        for (InstalledMod mod : mods) {
            drawRow(mod.getName(), mod.getVersion(),
                    mod.isEditable() ? "editable" : "no config", mod.isEditable(),
                    x, cursor, mouseX, mouseY, openLabel.equals(mod.getName()));
            cursor += ROW_HEIGHT;
        }
        if (!unattributed.isEmpty()) {
            Fonts.TINY.drawString("UNMATCHED CONFIGS", x + 4.0f, cursor + 6.0f, Theme.textFaint());
            cursor += ROW_HEIGHT;
            for (File file : unattributed) {
                drawRow(file.getName(), "", "editable", true,
                        x, cursor, mouseX, mouseY, openLabel.equals(file.getName()));
                cursor += ROW_HEIGHT;
            }
        }
        RenderUtil.endScissor();
    }

    private void drawRow(String name, String version, String status, boolean editable,
                         float x, float y, int mouseX, int mouseY, boolean selected) {
        if (y + ROW_HEIGHT < top() || y > top() + HEIGHT) {
            return;
        }
        boolean hovered = RenderUtil.isInside(mouseX, mouseY, x, y, LIST_WIDTH, ROW_HEIGHT - 2.0f);
        int background = selected ? Theme.accentDim() : (hovered ? Theme.rowHover() : Theme.row());
        RenderUtil.roundedRect(x, y, LIST_WIDTH, ROW_HEIGHT - 2.0f, 4.0, background);

        Fonts.SMALL.drawString(Fonts.SMALL.trimToWidth(name, LIST_WIDTH - 60.0f), x + 7.0f, y + 3.0f,
                editable ? Theme.text() : Theme.textMuted());
        if (!version.isEmpty()) {
            Fonts.TINY.drawString(version, x + 7.0f, y + 13.0f, Theme.textFaint());
        }
        Fonts.TINY.drawRightAligned(status, x + LIST_WIDTH - 7.0f, y + 8.0f,
                editable ? Theme.safe() : Theme.textFaint());
    }

    private void drawSettings(int mouseX, int mouseY) {
        float x = left() + PADDING * 2.0f + LIST_WIDTH;
        float y = top() + 42.0f;
        float paneWidth = WIDTH - LIST_WIDTH - PADDING * 3.0f;
        float viewportHeight = HEIGHT - 52.0f;

        if (open == null) {
            Fonts.SMALL.drawString("Select a mod to edit its settings.", x, y, Theme.textFaint());
            Fonts.TINY.drawString("Mods using their own config format are listed but cannot be",
                    x, y + 18.0f, Theme.textFaint());
            Fonts.TINY.drawString("edited here - there is no shared format to read.",
                    x, y + 28.0f, Theme.textFaint());
            return;
        }

        Fonts.SMALL_BOLD.drawString(openLabel, x, y, Theme.text());
        Fonts.TINY.drawString(notice.isEmpty() ? "Changes apply after a restart for most mods" : notice,
                x, y + 12.0f, notice.isEmpty() ? Theme.textFaint() : Theme.safe());

        float saveWidth = 52.0f;
        float saveX = x + paneWidth - saveWidth;
        boolean saveHovered = RenderUtil.isInside(mouseX, mouseY, saveX, y - 2.0f, saveWidth, 16.0f);
        RenderUtil.roundedRect(saveX, y - 2.0f, saveWidth, 16.0f, 4.0,
                saveHovered ? Theme.accentHover() : Theme.accent());
        Fonts.SMALL.drawCentred("Save", saveX + saveWidth / 2.0f, y + 1.0f, 0xFFFFFFFF);

        float contentTop = y + 26.0f;
        RenderUtil.beginScissor(x, contentTop, paneWidth, viewportHeight - 26.0f);
        float cursor = contentTop - settingsScroll;
        for (SettingComponent component : components) {
            if (!component.getSetting().isVisible()) {
                continue;
            }
            component.setBounds(x - 10.0f, cursor, paneWidth + 10.0f);
            if (cursor + component.getHeight() >= contentTop && cursor <= contentTop + viewportHeight) {
                component.render(mouseX, mouseY);
            }
            cursor += component.getHeight();
        }
        RenderUtil.endScissor();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        float listX = left() + PADDING;
        float listY = top() + 42.0f;
        if (mouseX >= listX && mouseX <= listX + LIST_WIDTH && mouseY >= listY && mouseY <= top() + HEIGHT - 10.0f) {
            int index = (int) ((mouseY - listY + listScroll) / ROW_HEIGHT);
            selectRow(index);
            return;
        }

        float paneX = left() + PADDING * 2.0f + LIST_WIDTH;
        float paneWidth = WIDTH - LIST_WIDTH - PADDING * 3.0f;
        float saveX = paneX + paneWidth - 52.0f;
        if (open != null && RenderUtil.isInside(mouseX, mouseY, saveX, top() + 40.0f, 52.0f, 16.0f)) {
            saveOpenConfig();
            return;
        }

        for (SettingComponent component : components) {
            if (component.mouseClicked(mouseX, mouseY, mouseButton)) {
                return;
            }
        }
    }

    private void selectRow(int index) {
        if (index < 0) {
            return;
        }
        if (index < mods.size()) {
            InstalledMod mod = mods.get(index);
            if (mod.isEditable()) {
                openConfig(mod.getConfigFile(), mod.getName());
            }
            return;
        }
        // Past the mod list comes the unmatched-config heading, then the files themselves.
        int offset = index - mods.size() - 1;
        if (offset >= 0 && offset < unattributed.size()) {
            File file = unattributed.get(offset);
            openConfig(file, file.getName());
        }
    }

    private void openConfig(File file, String label) {
        components.clear();
        notice = "";
        open = ForgeConfigFile.open(file);
        openLabel = label;
        settingsScroll = 0.0f;
        if (open == null) {
            notice = "Could not read this config";
            return;
        }
        for (Setting<?> setting : open.getSettings()) {
            SettingComponent component = SettingComponent.create(setting);
            if (component != null) {
                components.add(component);
            }
        }
    }

    private void saveOpenConfig() {
        try {
            open.save();
            notice = "Saved - restart to apply";
        } catch (Throwable failure) {
            notice = "Could not write the file";
            Vantage.LOGGER.error("Could not save {}", openLabel, failure);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        for (SettingComponent component : components) {
            component.mouseReleased(state);
        }
    }

    @Override
    protected void keyTyped(char typedCharacter, int keyCode) throws java.io.IOException {
        for (SettingComponent component : components) {
            if (component.isCapturingInput() && component.keyTyped(typedCharacter, keyCode)) {
                return;
            }
        }
        super.keyTyped(typedCharacter, keyCode);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        boolean overList = mouseX < left() + PADDING + LIST_WIDTH;
        float delta = Math.signum(wheel) * 24.0f;
        if (overList) {
            listScroll = Math.max(0.0f, Math.min(listScroll - delta,
                    Math.max(0.0f, totalRows() * ROW_HEIGHT - (HEIGHT - 52.0f))));
        } else {
            settingsScroll = Math.max(0.0f, settingsScroll - delta);
        }
    }
}
