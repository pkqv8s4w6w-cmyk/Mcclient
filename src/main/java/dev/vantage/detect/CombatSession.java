package dev.vantage.detect;

/**
 * Whether a fight is actually happening with a given player.
 *
 * <p>This is the single biggest thing separating a detector that works from one that flags the
 * whole lobby. Every signal these checks rely on — swing timing, where someone is looking, how
 * blocks appear under them — is produced constantly by ordinary Bedwars play. A player mining
 * wool sends a steady stream of swing packets that is indistinguishable from an autoclicker. A
 * player running past you turns to look at things. A player bridging to mid places blocks under
 * themselves. None of that is evidence of anything, and collecting it was why everyone came out
 * suspicious.
 *
 * <p>So nothing is collected unless a fight is under way: someone has damaged someone in the last
 * few seconds. Outside that window the checks are blind on purpose.
 *
 * <p>No Minecraft references, so the window behaviour is pinned by tests.
 */
public final class CombatSession {

    /** How long after the last blow the fight is still considered live. */
    public static final long TIMEOUT_MILLIS = 4_000L;

    private long lastExchangeMillis;
    private long openedAtMillis;
    private int exchanges;
    private boolean everOpened;

    /** Records a blow landing in either direction, which is what keeps a fight alive. */
    public void exchange(long nowMillis) {
        if (!isOpen(nowMillis)) {
            openedAtMillis = nowMillis;
            exchanges = 0;
        }
        lastExchangeMillis = nowMillis;
        everOpened = true;
        exchanges++;
    }

    public boolean isOpen(long nowMillis) {
        return everOpened && nowMillis - lastExchangeMillis < TIMEOUT_MILLIS;
    }

    /** How many blows have landed in the current fight. Zero once it has gone quiet. */
    public int getExchanges(long nowMillis) {
        return isOpen(nowMillis) ? exchanges : 0;
    }

    /** How long the current fight has been running, in milliseconds. Zero once it has gone quiet. */
    public long getDurationMillis(long nowMillis) {
        return isOpen(nowMillis) ? nowMillis - openedAtMillis : 0L;
    }

    public long getLastExchangeMillis() {
        return lastExchangeMillis;
    }

    public void reset() {
        lastExchangeMillis = 0L;
        openedAtMillis = 0L;
        exchanges = 0;
        everOpened = false;
    }
}
