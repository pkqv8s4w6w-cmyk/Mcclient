package dev.vantage.hud.impl;

import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.hud.HudModule;
import dev.vantage.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Frame rate, latency, position and facing. */
public class InfoHud extends HudModule {

    private final BooleanSetting showFps = register(new BooleanSetting("FPS", "", true));
    private final BooleanSetting showPing = register(new BooleanSetting("Ping", "", true));
    private final BooleanSetting showCoordinates = register(new BooleanSetting("Coordinates", "", false));
    private final BooleanSetting showFacing = register(new BooleanSetting("Facing", "", false));

    public InfoHud() {
        super("Info", "Frame rate, latency, position and facing");
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<String>();
        Minecraft mc = Minecraft.getMinecraft();
        if (showFps.value()) {
            lines.add(Minecraft.getDebugFPS() + " fps");
        }
        if (showPing.value()) {
            lines.add(ping(mc) + " ms");
        }
        if (mc.thePlayer != null && showCoordinates.value()) {
            lines.add(String.format(Locale.ROOT, "%.0f, %.0f, %.0f",
                    mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
        }
        if (mc.thePlayer != null && showFacing.value()) {
            lines.add(facing(mc.thePlayer.rotationYaw));
        }
        if (lines.isEmpty()) {
            lines.add("Info");
        }
        return lines;
    }

    private static int ping(Minecraft mc) {
        if (mc.getNetHandler() == null || mc.thePlayer == null) {
            return 0;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? 0 : info.getResponseTime();
    }

    /** Compass direction from a yaw, normalised into the eight-point range. */
    static String facing(float yaw) {
        String[] points = {"South", "South West", "West", "North West",
                "North", "North East", "East", "South East"};
        int index = (int) Math.floor(((yaw % 360.0f) + 360.0f) % 360.0f / 45.0f + 0.5f) & 7;
        return points[index];
    }

    @Override
    public float getContentWidth() {
        float widest = 0.0f;
        for (String line : lines()) {
            widest = Math.max(widest, Fonts.SMALL.getWidth(line));
        }
        return widest;
    }

    @Override
    public float getContentHeight() {
        return lines().size() * (Fonts.SMALL.getHeight() - 1.0f);
    }

    @Override
    protected void renderContent() {
        float y = 0.0f;
        for (String line : lines()) {
            Fonts.SMALL.drawString(line, 0.0f, y, Theme.TEXT);
            y += Fonts.SMALL.getHeight() - 1.0f;
        }
    }
}
