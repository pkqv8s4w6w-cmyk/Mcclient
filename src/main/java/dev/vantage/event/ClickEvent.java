package dev.vantage.event;

/** The game is handling a mouse click: an attack or break (left) or a use or place (right). */
public final class ClickEvent {

    public static final ClickEvent LEFT = new ClickEvent(true);
    public static final ClickEvent RIGHT = new ClickEvent(false);

    private final boolean left;

    private ClickEvent(boolean left) {
        this.left = left;
    }

    public boolean isLeft() {
        return left;
    }
}
