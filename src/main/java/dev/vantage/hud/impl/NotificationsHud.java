package dev.vantage.hud.impl;

import dev.vantage.gui.Easing;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.hud.HudModule;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.notify.Notifications;
import dev.vantage.setting.BooleanSetting;

import java.util.List;

/** Toasts for module toggles and Bedwars alerts, stacked, each with a bar that runs down. */
public class NotificationsHud extends HudModule {

    private static final float WIDTH = 150.0f;
    private static final float HEIGHT = 26.0f;
    private static final float GAP = 3.0f;

    private final BooleanSetting toggles = register(new BooleanSetting(
            "Toggles", "Show a toast when a module is switched on or off", true));

    public NotificationsHud() {
        super("Notifications", "Toasts for toggles and Bedwars alerts");
        Module.setToggleListener(module -> {
            if (!isEnabled() || !toggles.value() || module.getCategory() == Category.CLIENT) {
                return;
            }
            Notifications.post(module.getName(), module.isEnabled() ? "Enabled" : "Disabled",
                    module.isEnabled() ? Notifications.Kind.SUCCESS : Notifications.Kind.INFO, 1500L);
        });
    }

    @Override
    protected boolean drawsOwnPanel() {
        return true;
    }

    @Override
    public float getContentWidth() {
        return WIDTH;
    }

    @Override
    public float getContentHeight() {
        return Math.max(HEIGHT, Notifications.active(System.currentTimeMillis()).size() * (HEIGHT + GAP));
    }

    private static int colourFor(Notifications.Kind kind) {
        switch (kind) {
            case SUCCESS:
                return Theme.safe();
            case WARNING:
                return Theme.warning();
            case DANGER:
                return Theme.danger();
            default:
                return Theme.accent();
        }
    }

    @Override
    protected void renderContent() {
        long now = System.currentTimeMillis();
        List<Notifications.Notification> active = Notifications.active(now);
        float y = 0.0f;
        for (Notifications.Notification notification : active) {
            double progress = notification.progress(now);
            // Slide in over the first tenth, out over the last.
            double entry = Easing.outCubic(Math.min(1.0, progress * 10.0));
            double exit = Easing.outCubic(Math.min(1.0, (1.0 - progress) * 10.0));
            float offset = (float) ((1.0 - Math.min(entry, exit)) * (WIDTH + 10.0));
            int accent = colourFor(notification.kind);
            float x = offset;
            RenderUtil.shadow(x, y, WIDTH, HEIGHT, 4.0, 3, 0x60000000);
            RenderUtil.roundedRect(x, y, WIDTH, HEIGHT, 4.0, RenderUtil.withAlpha(Theme.panel(), 235));
            RenderUtil.roundedRect(x, y + 5.0f, 2.0f, HEIGHT - 10.0f, 1.0, accent);
            Fonts.SMALL_BOLD.drawString(notification.title, x + 8.0f, y + 4.0f, Theme.text());
            Fonts.TINY.drawString(Fonts.TINY.trimToWidth(notification.message, WIDTH - 14.0f),
                    x + 8.0f, y + 14.0f, Theme.textMuted());
            RenderUtil.rect(x + 4.0f, y + HEIGHT - 1.5f, (float) ((WIDTH - 8.0f) * (1.0 - progress)), 1.0f,
                    RenderUtil.withAlpha(accent, 0.8f));
            y += HEIGHT + GAP;
        }
    }
}
