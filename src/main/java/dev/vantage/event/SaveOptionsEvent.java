package dev.vantage.event;

/**
 * The game is writing options.txt ({@link Stage#PRE}), or has just written it ({@link Stage#POST}).
 * Modules that change a game option put the player's own value back for the length of the save,
 * so what is on disk is always what the player chose.
 */
public final class SaveOptionsEvent {

    private final Stage stage;

    public SaveOptionsEvent(Stage stage) {
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }
}
