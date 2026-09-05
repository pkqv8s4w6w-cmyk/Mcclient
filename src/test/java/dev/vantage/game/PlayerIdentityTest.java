package dev.vantage.game;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerIdentityTest {

    /** How Mojang issues account UUIDs: random, and therefore version 4. */
    private static UUID account() {
        return UUID.randomUUID();
    }

    /** How a server manufactures a profile for an NPC: derived from a name, and version 3. */
    private static UUID npc(String label) {
        return UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void arealAccountPasses() {
        assertTrue(PlayerIdentity.isRealAccount(account(), "Notch"));
        assertTrue(PlayerIdentity.isRealAccount(account(), "Steve_1"));
        assertTrue(PlayerIdentity.isRealAccount(account(), "aaaaaaaaaaaaaaaa"));
    }

    @Test
    void anNpcProfileIsRejected() {
        // The bug this fixes: shopkeepers and lobby NPCs arrive through the same player-info
        // packet as players, so the threat list was ranking them.
        assertFalse(PlayerIdentity.isRealAccount(npc("Bed Wars"), "Shopkeeper"));
        assertFalse(PlayerIdentity.isRealAccount(npc("OfflinePlayer:Bot"), "Bot"));
    }

    @Test
    void anOfflineModeUuidIsRejected() {
        // Offline UUIDs use the same MD5 scheme, so they read as version 3 as well.
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Notch".getBytes(StandardCharsets.UTF_8));
        assertFalse(PlayerIdentity.isRealAccount(offline, "Notch"));
    }

    @Test
    void decorativeRowsAreRejectedOnTheirName() {
        assertFalse(PlayerIdentity.isRealAccount(account(), "§eBed Wars"));
        assertFalse(PlayerIdentity.isRealAccount(account(), "Kills: 3"));
        assertFalse(PlayerIdentity.isRealAccount(account(), ""));
        assertFalse(PlayerIdentity.isRealAccount(account(), "a name far too long to be real"));
    }

    @Test
    void missingDataIsRejectedRatherThanGuessed() {
        assertFalse(PlayerIdentity.isRealAccount(null, "Notch"));
        assertFalse(PlayerIdentity.isRealAccount(account(), null));
        assertFalse(PlayerIdentity.isRealAccount(null, null));
    }

    @Test
    void everyVersionButFourIsRejected() {
        // Only version 4 is Mojang's. Anything else was manufactured by whoever sent it.
        for (int version = 0; version <= 5; version++) {
            UUID stamped = withVersion(version);
            assertTrue(PlayerIdentity.isRealAccount(stamped, "Player") == (version == 4),
                    "version " + version);
        }
    }

    private static UUID withVersion(int version) {
        long most = (0x0123456789ABCDEFL & ~0xF000L) | ((long) version << 12);
        return new UUID(most, 0x89ABCDEF01234567L);
    }
}
