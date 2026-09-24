package dev.vantage.gui.page;

import dev.vantage.Vantage;
import dev.vantage.config.FriendManager;
import dev.vantage.gui.Icons;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.widget.TextField;
import dev.vantage.gui.widget.Widgets;
import dev.vantage.module.Module;
import dev.vantage.module.impl.utility.MiddleClickFriendModule;
import dev.vantage.notify.Notifications;

import java.util.ArrayList;
import java.util.List;

/** Players every combat module leaves alone, and the middle-click shortcut for adding them. */
public class FriendsPage extends Page {

    private static final float CARD_HEIGHT = 30.0f;
    private static final float GAP = 6.0f;

    private final TextField input = new TextField("Add a friend by name and press Enter", 16, Icons.USER_PLUS);
    private final List<Object[]> removeButtons = new ArrayList<Object[]>();
    private Widgets.Rect middleClickSwitch = new Widgets.Rect(0, 0, 0, 0);
    private Widgets.Rect addButton = new Widgets.Rect(0, 0, 0, 0);

    public FriendsPage() {
        input.onSubmit(this::add);
    }

    @Override
    public String title() {
        return "Friends";
    }

    @Override
    public String subtitle() {
        return "Players your combat modules leave alone";
    }

    private static FriendManager friends() {
        return Vantage.instance().friends();
    }

    private void add(String name) {
        if (name.isEmpty()) {
            return;
        }
        if (friends().add(name)) {
            Notifications.post("Friend added", name, Notifications.Kind.SUCCESS);
            Vantage.instance().saveConfig();
        }
        input.setText("");
    }

    @Override
    public void renderHeaderControls(float right, float y, float mouseX, float mouseY) {
        int count = friends().list().size();
        String text = count + (count == 1 ? " friend" : " friends");
        Fonts.TINY.drawRightAligned(text, right, y + 4.0f, Theme.textFaint());
    }

    @Override
    public float renderBody(float x, float top, float width, float mouseX, float mouseY) {
        float cursor = top;
        float addWidth = Widgets.buttonWidth("Add", Icons.PLUS);
        input.render(x, cursor, width - addWidth - 6.0f, 22.0f);
        addButton = Widgets.button("Add", Icons.PLUS, x + width - addWidth, cursor + 3.0f, Widgets.Style.PRIMARY, mouseX, mouseY);
        cursor += 22.0f + GAP;

        Module middleClick = Vantage.instance().modules().get(MiddleClickFriendModule.class);
        boolean hovered = mouseY >= cursor && mouseY <= cursor + CARD_HEIGHT && mouseX >= x && mouseX <= x + width;
        Widgets.card(x, cursor, width, CARD_HEIGHT, hovered);
        Fonts.ICONS.drawString(String.valueOf(Icons.MOUSE_POINTER_CLICK), x + 12.0f, cursor + 10.5f, Theme.accent());
        Fonts.SMALL.drawString("Middle-click a player to add or remove them", x + 28.0f, cursor + 10.0f, Theme.text());
        middleClickSwitch = Widgets.toggle(x + width - 12.0f - Widgets.SWITCH_WIDTH,
                cursor + (CARD_HEIGHT - Widgets.SWITCH_HEIGHT) / 2.0f,
                middleClick != null && middleClick.isEnabled() ? 1.0 : 0.0);
        cursor += CARD_HEIGHT + GAP * 2.0f;

        removeButtons.clear();
        List<String> names = friends().list();
        if (names.isEmpty()) {
            Fonts.SMALL.drawCentred("No friends yet", x + width / 2.0f, cursor + 12.0f, Theme.textFaint());
            return cursor + 30.0f - top;
        }
        for (String name : names) {
            boolean over = mouseY >= cursor && mouseY <= cursor + CARD_HEIGHT && mouseX >= x && mouseX <= x + width;
            Widgets.card(x, cursor, width, CARD_HEIGHT, over);
            Fonts.ICONS.drawString(String.valueOf(Icons.USERS), x + 12.0f, cursor + 10.5f, Theme.safe());
            Fonts.BODY.drawString(name, x + 28.0f, cursor + 9.0f, Theme.text());
            Widgets.Rect remove = Widgets.buttonRightAligned("Remove", Icons.USER_MINUS, x + width - 10.0f,
                    cursor + (CARD_HEIGHT - Widgets.BUTTON_HEIGHT) / 2.0f, Widgets.Style.GHOST, mouseX, mouseY);
            removeButtons.add(new Object[]{name, remove});
            cursor += CARD_HEIGHT + GAP;
        }
        return cursor - top;
    }

    @Override
    public boolean bodyClicked(float mouseX, float mouseY, int button) {
        if (input.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (addButton.contains(mouseX, mouseY)) {
            add(input.getText().trim());
            return true;
        }
        if (middleClickSwitch.contains(mouseX, mouseY)) {
            Module middleClick = Vantage.instance().modules().get(MiddleClickFriendModule.class);
            if (middleClick != null) {
                middleClick.toggle();
            }
            return true;
        }
        for (Object[] entry : removeButtons) {
            if (((Widgets.Rect) entry[1]).contains(mouseX, mouseY)) {
                friends().remove((String) entry[0]);
                Vantage.instance().saveConfig();
                Notifications.post("Friend removed", (String) entry[0], Notifications.Kind.INFO);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyTyped(char character, int keyCode) {
        return input.keyTyped(character, keyCode);
    }

    @Override
    public boolean isCapturingInput() {
        return input.isFocused();
    }
}
