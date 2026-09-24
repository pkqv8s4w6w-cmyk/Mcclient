package dev.vantage.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendManagerTest {

    @TempDir
    Path directory;

    @Test
    void namesMatchWhateverTheCase() {
        FriendManager friends = new FriendManager(directory.resolve("friends.json"));
        friends.add("Notch");
        assertTrue(friends.isFriend("notch"));
        assertTrue(friends.isFriend("NOTCH"));
        assertFalse(friends.add("nOtCh"));
    }

    @Test
    void toggleSaysWhichWayItWent() {
        FriendManager friends = new FriendManager(directory.resolve("friends.json"));
        assertTrue(friends.toggle("Steve"));
        assertFalse(friends.toggle("steve"));
        assertFalse(friends.isFriend("Steve"));
    }

    @Test
    void survivesASaveAndLoadKeepingTheOriginalCapitals() throws Exception {
        Path file = directory.resolve("friends.json");
        FriendManager first = new FriendManager(file);
        first.add("Alex_01");
        first.add("jeb_");
        first.save();

        FriendManager second = new FriendManager(file);
        second.load();
        assertEquals(2, second.list().size());
        assertEquals("Alex_01", second.list().get(0));
    }

    @Test
    void aBrokenFileCostsTheListNotTheGame() throws Exception {
        Path file = directory.resolve("friends.json");
        Files.write(file, "{ not json".getBytes(StandardCharsets.UTF_8));
        FriendManager friends = new FriendManager(file);
        friends.load();
        assertTrue(friends.list().isEmpty());
    }
}
