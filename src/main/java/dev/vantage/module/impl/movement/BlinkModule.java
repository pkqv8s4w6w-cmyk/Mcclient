package dev.vantage.module.impl.movement;

import dev.vantage.event.PacketEvent;
import dev.vantage.event.Render3DEvent;
import dev.vantage.gui.Theme;
import dev.vantage.gui.render.Render3D;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds back everything you send, so to everyone else you stand frozen where Blink started, then
 * releases it all at once. Keep-alives and chat still go through, so the server does not time you
 * out and you can still talk.
 */
public class BlinkModule extends Module {

    private final BooleanSetting pulse = register(new BooleanSetting(
            "Pulse", "Release everything held every so often instead of only when switched off", false));
    private final NumberSetting pulseDelay = register(new NumberSetting(
            "Pulse Delay", "How often to release", 1000.0, 100.0, 5000.0, 100.0, "ms"));

    private final List<Packet<?>> held = new ArrayList<Packet<?>>();
    private double startX;
    private double startY;
    private double startZ;
    private long lastRelease;

    public BlinkModule() {
        super("Blink", Category.MOVEMENT, "Freeze where you are for everyone else, then catch up");
        markBlatant();
        pulseDelay.visibleWhen(pulse::value);
        on(PacketEvent.Send.class, this::onSend);
        on(Render3DEvent.class, event -> drawStart());
    }

    @Override
    public String getSuffix() {
        synchronized (held) {
            return String.valueOf(held.size());
        }
    }

    @Override
    protected void onEnable() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            markStart(player);
        }
        lastRelease = System.currentTimeMillis();
    }

    @Override
    protected void onDisable() {
        release();
    }

    @Override
    public void onWorldChanged() {
        synchronized (held) {
            held.clear();
        }
    }

    @Override
    public void onTick() {
        if (pulse.value() && System.currentTimeMillis() - lastRelease >= pulseDelay.asInt()) {
            release();
            markStart(Minecraft.getMinecraft().thePlayer);
            lastRelease = System.currentTimeMillis();
        }
    }

    private void markStart(EntityPlayerSP player) {
        startX = player.posX;
        startY = player.posY;
        startZ = player.posZ;
    }

    private void onSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
            return;
        }
        synchronized (held) {
            held.add(packet);
        }
        event.cancel();
    }

    private void release() {
        List<Packet<?>> toSend;
        synchronized (held) {
            toSend = new ArrayList<Packet<?>>(held);
            held.clear();
        }
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            return;
        }
        for (Packet<?> packet : toSend) {
            PacketUtil.sendSilent(packet);
        }
    }

    /** Marks where everyone else still sees you. */
    private void drawStart() {
        boolean anyMovement;
        synchronized (held) {
            anyMovement = !held.isEmpty() && held.stream().anyMatch(p -> p instanceof C03PacketPlayer);
        }
        if (!anyMovement) {
            return;
        }
        Render3D.begin();
        Render3D.box(new AxisAlignedBB(startX - 0.3, startY, startZ - 0.3, startX + 0.3, startY + 1.8, startZ + 0.3),
                Theme.accent(), 0.2f);
        Render3D.end();
    }
}
