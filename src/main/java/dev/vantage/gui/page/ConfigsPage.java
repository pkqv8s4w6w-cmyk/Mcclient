package dev.vantage.gui.page;

import dev.vantage.Vantage;
import dev.vantage.config.ConfigManager;
import dev.vantage.gui.Icons;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.widget.TextField;
import dev.vantage.gui.widget.Widgets;
import dev.vantage.notify.Notifications;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Saved setups. Apply switches to one, Share copies it to the clipboard for a friend, Import takes
 * one off the clipboard, and New saves what you have now under a new name.
 */
public class ConfigsPage extends Page {

    private static final float CARD_HEIGHT = 40.0f;
    private static final float GAP = 6.0f;

    private final TextField newName = new TextField("Name the new profile and press Enter", 24, Icons.PLUS);
    private boolean creating;
    private Widgets.Rect importBounds = new Widgets.Rect(0, 0, 0, 0);
    private Widgets.Rect newBounds = new Widgets.Rect(0, 0, 0, 0);
    private final List<Object[]> cardButtons = new ArrayList<Object[]>();
    private List<ConfigManager.ProfileSummary> profiles = new ArrayList<ConfigManager.ProfileSummary>();

    public ConfigsPage() {
        newName.onSubmit(this::create);
    }

    @Override
    public String title() {
        return "Configs";
    }

    @Override
    public String subtitle() {
        return "Save and share your loadouts";
    }

    @Override
    public void onOpen() {
        refresh();
    }

    private void refresh() {
        ConfigManager config = Vantage.instance().config();
        profiles = new ArrayList<ConfigManager.ProfileSummary>();
        for (String name : config.listProfiles()) {
            profiles.add(config.summarise(name));
        }
    }

    /** The active profile's count comes from the modules themselves, since it may not be saved yet. */
    private static int liveEnabledCount() {
        int count = 0;
        for (dev.vantage.module.Module module : Vantage.instance().modules().getModules()) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void renderHeaderControls(float right, float y, float mouseX, float mouseY) {
        newBounds = Widgets.buttonRightAligned("New", Icons.PLUS, right, y - 1.0f, Widgets.Style.PRIMARY, mouseX, mouseY);
        importBounds = Widgets.buttonRightAligned("Import", Icons.DOWNLOAD, newBounds.x - 6.0f, y - 1.0f,
                Widgets.Style.GHOST, mouseX, mouseY);
    }

    @Override
    public boolean headerClicked(float mouseX, float mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (newBounds.contains(mouseX, mouseY)) {
            creating = true;
            newName.setText("");
            newName.setFocused(true);
            return true;
        }
        if (importBounds.contains(mouseX, mouseY)) {
            importFromClipboard();
            return true;
        }
        return false;
    }

    @Override
    public float renderBody(float x, float top, float width, float mouseX, float mouseY) {
        float cursor = top;
        if (creating) {
            newName.render(x, cursor, width, 22.0f);
            cursor += 22.0f + GAP;
        }
        cardButtons.clear();
        String active = Vantage.instance().getActiveProfile();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm, d MMM", Locale.ROOT);
        for (ConfigManager.ProfileSummary profile : profiles) {
            boolean isActive = profile.name.equals(active);
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= cursor && mouseY <= cursor + CARD_HEIGHT;
            Widgets.card(x, cursor, width, CARD_HEIGHT, hovered);
            float nameEnd = Fonts.BODY_BOLD.drawString(profile.name, x + 12.0f, cursor + 8.0f, Theme.text());
            if (isActive) {
                Widgets.chip("Active", nameEnd + 6.0f, cursor + 8.0f, Theme.safe());
            }
            int enabled = isActive ? liveEnabledCount() : profile.enabledModules;
            String meta = enabled + (enabled == 1 ? " module on" : " modules on")
                    + (profile.lastModified > 0 ? "  •  saved " + format.format(new Date(profile.lastModified)) : "");
            Fonts.TINY.drawString(meta, x + 12.0f, cursor + 23.0f, Theme.textFaint());

            float buttonY = cursor + (CARD_HEIGHT - Widgets.BUTTON_HEIGHT) / 2.0f;
            float right = x + width - 12.0f;
            if (!"default".equals(profile.name)) {
                Widgets.Rect delete = Widgets.iconButton(Icons.TRASH_2, right - Widgets.BUTTON_HEIGHT, buttonY,
                        Theme.danger(), mouseX, mouseY);
                cardButtons.add(new Object[]{"delete", profile.name, delete});
                right = delete.x - 6.0f;
            }
            Widgets.Rect share = Widgets.buttonRightAligned("Share", Icons.SHARE_2, right, buttonY,
                    Widgets.Style.GHOST, mouseX, mouseY);
            cardButtons.add(new Object[]{"share", profile.name, share});
            if (!isActive) {
                Widgets.Rect apply = Widgets.buttonRightAligned("Apply", (char) 0, share.x - 6.0f, buttonY,
                        Widgets.Style.PRIMARY, mouseX, mouseY);
                cardButtons.add(new Object[]{"apply", profile.name, apply});
            }
            cursor += CARD_HEIGHT + GAP;
        }
        return cursor - top;
    }

    @Override
    public boolean bodyClicked(float mouseX, float mouseY, int button) {
        if (creating && newName.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (Object[] entry : cardButtons) {
            Widgets.Rect bounds = (Widgets.Rect) entry[2];
            if (!bounds.contains(mouseX, mouseY)) {
                continue;
            }
            String action = (String) entry[0];
            String name = (String) entry[1];
            if ("apply".equals(action)) {
                Vantage.instance().switchProfile(name);
                Notifications.post("Profile applied", name, Notifications.Kind.SUCCESS);
            } else if ("share".equals(action)) {
                share(name);
            } else if ("delete".equals(action)) {
                delete(name);
            }
            refresh();
            return true;
        }
        return false;
    }

    private void create(String typed) {
        if (typed.isEmpty()) {
            creating = false;
            return;
        }
        String name = ConfigManager.sanitiseProfileName(typed);
        // Saving under the new name first keeps the current setup as its starting point.
        try {
            Vantage.instance().config().save(name, Vantage.instance().modules().getModules());
            Vantage.instance().switchProfile(name);
            Notifications.post("Profile created", name, Notifications.Kind.SUCCESS);
        } catch (IOException failure) {
            Notifications.post("Could not create profile", failure.getMessage(), Notifications.Kind.DANGER);
        }
        creating = false;
        newName.setFocused(false);
        refresh();
    }

    private void share(String name) {
        try {
            GuiScreen.setClipboardString(Vantage.instance().config().exportProfile(name));
            Notifications.post("Copied to clipboard", "Send it to a friend and they can Import it", Notifications.Kind.SUCCESS);
        } catch (IOException failure) {
            Notifications.post("Could not share", failure.getMessage(), Notifications.Kind.DANGER);
        }
    }

    private void importFromClipboard() {
        try {
            String saved = Vantage.instance().config().importProfile("imported", GuiScreen.getClipboardString());
            Notifications.post("Profile imported", "Saved as " + saved, Notifications.Kind.SUCCESS);
        } catch (IOException failure) {
            Notifications.post("Nothing to import", "The clipboard does not hold a profile", Notifications.Kind.WARNING);
        }
        refresh();
    }

    private void delete(String name) {
        try {
            if (name.equals(Vantage.instance().getActiveProfile())) {
                Vantage.instance().switchProfile("default");
            }
            Vantage.instance().config().deleteProfile(name);
            Notifications.post("Profile deleted", name, Notifications.Kind.INFO);
        } catch (IOException failure) {
            Notifications.post("Could not delete", failure.getMessage(), Notifications.Kind.DANGER);
        }
    }

    @Override
    public boolean keyTyped(char character, int keyCode) {
        return creating && newName.keyTyped(character, keyCode);
    }

    @Override
    public boolean isCapturingInput() {
        return creating && newName.isFocused();
    }
}
