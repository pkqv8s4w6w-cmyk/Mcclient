package dev.vantage.gui.page;

/**
 * One screen of the menu: a module category, or Configs, Friends, Themes or Settings.
 *
 * <p>The menu draws the frame, the title and the scrolling viewport; a page draws the controls in
 * the header's right-hand side and the body inside the viewport. Coordinates are the menu's own
 * layout space, already translated for scale and scroll, so a page never deals with either.
 */
public abstract class Page {

    public abstract String title();

    public abstract String subtitle();

    /** Draws controls right-aligned to {@code right} on the title row. */
    public void renderHeaderControls(float right, float y, float mouseX, float mouseY) {
    }

    public boolean headerClicked(float mouseX, float mouseY, int button) {
        return false;
    }

    /**
     * Draws the scrolling body with its top-left at {@code (x, top)}.
     *
     * @return the height of everything drawn, so the menu knows how far it scrolls
     */
    public abstract float renderBody(float x, float top, float width, float mouseX, float mouseY);

    public boolean bodyClicked(float mouseX, float mouseY, int button) {
        return false;
    }

    public void mouseReleased(int button) {
    }

    /** @return true if the key was used */
    public boolean keyTyped(char character, int keyCode) {
        return false;
    }

    /** True while something on the page wants every key, so Escape does not close the menu. */
    public boolean isCapturingInput() {
        return false;
    }

    public void onOpen() {
    }
}
