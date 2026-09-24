package dev.vantage.setting;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingTest {

    enum Mode { OFF, SMOOTH, INSTANT }

    @Test
    void numberClampsToRange() {
        NumberSetting setting = new NumberSetting("Range", "", 3.0, 1.0, 6.0, 0.5);
        setting.set(99.0);
        assertEquals(6.0, setting.asDouble(), 1e-9);
        setting.set(-99.0);
        assertEquals(1.0, setting.asDouble(), 1e-9);
    }

    @Test
    void numberSnapsToStepWithoutFloatingPointNoise() {
        NumberSetting setting = new NumberSetting("Step", "", 0.0, 0.0, 1.0, 0.1);
        setting.set(0.34);
        // A naive min + round(x/step)*step lands on 0.30000000000000004 here.
        assertEquals(0.3, setting.asDouble(), 1e-9);
        assertEquals("0.3", String.valueOf(setting.asDouble()));
    }

    @Test
    void numberFractionRoundTrips() {
        NumberSetting setting = new NumberSetting("Frac", "", 0.0, 0.0, 10.0, 1.0);
        setting.setFraction(0.5);
        assertEquals(5.0, setting.asDouble(), 1e-9);
        assertEquals(0.5, setting.getFraction(), 1e-9);
    }

    @Test
    void numberRejectsImpossibleBounds() {
        assertThrows(IllegalArgumentException.class, () -> new NumberSetting("Bad", "", 1, 5, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new NumberSetting("Bad", "", 1, 0, 5, 0));
    }

    @Test
    void listenerFiresOnlyOnRealChange() {
        AtomicInteger calls = new AtomicInteger();
        BooleanSetting setting = new BooleanSetting("Toggle", "", false);
        setting.onChange(value -> calls.incrementAndGet());

        setting.set(false);
        assertEquals(0, calls.get(), "setting the same value should not notify");
        setting.set(true);
        assertEquals(1, calls.get());
    }

    @Test
    void visibilityPredicateGatesSubSettings() {
        BooleanSetting parent = new BooleanSetting("Parent", "", false);
        NumberSetting child = new NumberSetting("Child", "", 1, 0, 10, 1);
        child.visibleWhen(parent::value);

        assertFalse(child.isVisible());
        parent.set(true);
        assertTrue(child.isVisible());
    }

    @Test
    void enumCyclesAndIgnoresUnknownConstants() {
        EnumSetting<Mode> setting = new EnumSetting<>("Mode", "", Mode.OFF);
        setting.cycle();
        assertEquals(Mode.SMOOTH, setting.get());
        setting.cycle();
        setting.cycle();
        assertEquals(Mode.OFF, setting.get(), "cycling should wrap");

        setting.set(Mode.INSTANT);
        setting.read(new com.google.gson.JsonPrimitive("REMOVED_IN_A_LATER_VERSION"));
        assertEquals(Mode.INSTANT, setting.get(), "an unknown constant must not clear the value");
    }

    @Test
    void enumLabelsAreReadable() {
        assertEquals("Instant", EnumSetting.label(Mode.INSTANT));
    }

    @Test
    void colorSplitsAndRebuildsChannels() {
        ColorSetting setting = new ColorSetting("Accent", "", 0xFF3B82F6);
        assertEquals(0xFF, setting.getAlpha());
        assertEquals(0x3B, setting.getRed());
        assertEquals(0x82, setting.getGreen());
        assertEquals(0xF6, setting.getBlue());

        setting.setAlpha(0x80);
        assertEquals(0x80, setting.getAlpha());
        assertEquals(0x3B, setting.getRed(), "changing alpha must not disturb rgb");
    }

    @Test
    void rainbowOverridesTheFixedColourButKeepsAlpha() {
        ColorSetting setting = new ColorSetting("Accent", "", 0x80FF0000);
        assertEquals(0x80FF0000, setting.display());

        setting.setRainbow(true);
        assertEquals(0x80, (setting.display() >> 24) & 0xFF, "rainbow must preserve alpha");
    }

    @Test
    void stringTruncatesToMaxLength() {
        StringSetting setting = new StringSetting("Key", "", "", 4, true);
        setting.set("abcdefgh");
        assertEquals("abcd", setting.get());
        assertTrue(setting.isMasked());
    }

    @Test
    void keybindReportsBoundState() {
        KeybindSetting setting = new KeybindSetting("Bind", "", KeybindSetting.UNBOUND);
        assertFalse(setting.isBound());
        setting.set(54);
        assertTrue(setting.isBound());
        assertTrue(setting.matches(54));
        assertFalse(setting.matches(55));
        setting.clear();
        assertFalse(setting.matches(0), "an unbound key must not match key code 0");
    }

    @Test
    void configKeyIsStableAcrossLabelCasing() {
        assertEquals("max_players", new BooleanSetting("Max Players", "", false).getConfigKey());
        assertNotEquals("Max Players", new BooleanSetting("Max Players", "", false).getConfigKey());
    }

    @Test
    void settingsRejectNullDefaults() {
        assertThrows(IllegalArgumentException.class, () -> new StringSetting("X", "", null));
    }
}
