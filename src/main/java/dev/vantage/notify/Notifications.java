package dev.vantage.notify;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Short messages shown as toasts: module toggles and Bedwars alerts.
 *
 * <p>Anything can post from any thread; the HUD element drains and draws them. Minecraft-free, so
 * the expiry rules are testable.
 */
public final class Notifications {

    public enum Kind { INFO, SUCCESS, WARNING, DANGER }

    public static final class Notification {
        public final String title;
        public final String message;
        public final Kind kind;
        public final long createdAt;
        public final long durationMillis;

        Notification(String title, String message, Kind kind, long createdAt, long durationMillis) {
            this.title = title;
            this.message = message;
            this.kind = kind;
            this.createdAt = createdAt;
            this.durationMillis = durationMillis;
        }

        /** 0 when just posted, 1 when it is due to go. */
        public double progress(long now) {
            return Math.min(1.0, Math.max(0.0, (now - createdAt) / (double) durationMillis));
        }
    }

    private static final int LIMIT = 6;
    private static final List<Notification> ACTIVE = new ArrayList<Notification>();

    private Notifications() {
    }

    public static void post(String title, String message, Kind kind) {
        post(title, message, kind, 2500L);
    }

    public static synchronized void post(String title, String message, Kind kind, long durationMillis) {
        post(title, message, kind, durationMillis, System.currentTimeMillis());
    }

    static synchronized void post(String title, String message, Kind kind, long durationMillis, long now) {
        // The same alert repeated replaces the old one rather than stacking copies of it.
        Iterator<Notification> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Notification existing = iterator.next();
            if (existing.title.equals(title) && existing.message.equals(message)) {
                iterator.remove();
            }
        }
        ACTIVE.add(new Notification(title, message, kind, now, durationMillis));
        while (ACTIVE.size() > LIMIT) {
            ACTIVE.remove(0);
        }
    }

    /** The notifications still showing, oldest first. Expired ones are dropped here. */
    public static synchronized List<Notification> active(long now) {
        Iterator<Notification> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().progress(now) >= 1.0) {
                iterator.remove();
            }
        }
        return new ArrayList<Notification>(ACTIVE);
    }

    static synchronized void clear() {
        ACTIVE.clear();
    }
}
