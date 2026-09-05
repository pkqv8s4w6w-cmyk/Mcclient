package dev.vantage.module.impl.combat;

import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.net.PacketDelayer;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pins nearby players where they were, so they can be hit after they have moved.
 *
 * <p>The gap this uses is one vanilla leaves open. A server accepts a melee hit whenever the
 * attacker is within six blocks of the target - {@code NetHandlerPlayServer.processUseEntity}
 * checks {@code getDistanceSqToEntity < 36.0} - but the client only traces for a target out to
 * three. Blocking a player's position packets leaves their entity, and so their bounding box, where
 * it was; the crosshair trace hits that stale box, and the attack packet that follows carries
 * nothing but an entity id, so the server checks its own current positions and counts the hit.
 * Nothing here spoofs a rotation, redirects an attack or fakes a position: the module withholds
 * packets and vanilla does the rest.
 *
 * <p>Packets are <em>blocked</em> for the length of a window rather than delayed by a fixed amount.
 * A running delay leaves a target trailing by a constant time, worth about a block at sprint speed;
 * pinning them keeps them inside reach for as long as the window lasts, which is the difference
 * between an occasional extra hit and a useful one. {@code HoldWindow} owns that lifecycle.
 *
 * <p>Only inbound packets for other players are touched. Nothing this client sends is delayed, so
 * the server always knows exactly where the local player is and other players always see them live
 * - the module is not fakelag and cannot make its user easier to hit. Nothing addressed to the
 * local player is held either, so knockback arrives on time.
 *
 * <p>This is bannable on any public server. It is here for a private one.
 */
public class BacktrackModule extends Module {

    /** 1.8.9 counts a player's hurt animation down from ten ticks. */
    private static final double HURT_TICK_MILLIS = 50.0;
    private static final double MAX_HURT_MILLIS = 500.0;

    private final NumberSetting maxDelay = register(new NumberSetting(
            "Maximum Delay", "Longest a player may be held in place before they are let go",
            250.0, 0.0, 1000.0, 10.0, "ms"));
    private final NumberSetting minDistance = register(new NumberSetting(
            "Min Distance", "Players closer than this are left alone; you can already hit them",
            1.0, 0.0, 6.0, 0.25, "m"));
    private final NumberSetting maxDistance = register(new NumberSetting(
            "Max Distance", "Players further than this are left alone; past six the server refuses the hit anyway",
            5.0, 0.0, 6.0, 0.25, "m"));
    private final NumberSetting maxHurtTime = register(new NumberSetting(
            "Maximum Hurt Time", "Only hold a player once they are this close to being damageable again; "
                    + "below the maximum this makes the effect come in spikes rather than one smooth stall",
            500.0, 0.0, MAX_HURT_MILLIS, 50.0, "ms"));
    private final NumberSetting cooldown = register(new NumberSetting(
            "Cooldown", "How long after letting a player go before they may be held again",
            0.0, 0.0, 5000.0, 100.0, "ms"));
    private final BooleanSetting releaseOnHurt = register(new BooleanSetting(
            "Release On Hurt", "Let everyone go the moment you take a hit", true));

    private int lastHurtTime;
    private boolean reportedUnavailable;

    public BacktrackModule() {
        super("Backtrack", Category.COMBAT, "Hit players where they were, not where they are");
    }

    @Override
    protected void onDisable() {
        PacketDelayer.instance().setActive(false);
        // Detaching hands back everything still held, so nobody is left stuck in the wrong place.
        PacketDelayer.instance().detach();
        lastHurtTime = 0;
    }

    @Override
    public void onWorldChanged() {
        // The entities these belonged to are gone, so there is nothing to deliver them to.
        PacketDelayer.instance().clear();
        lastHurtTime = 0;
    }

    @Override
    public void onTick() {
        PacketDelayer delayer = PacketDelayer.instance();

        if (!PacketDelayer.isUsable()) {
            // Without the entity id, movement packets pass straight through and the module would
            // look like it was running while doing nothing. Better to say so and switch off.
            reportUnavailableOnce();
            delayer.setActive(false);
            setEnabledSilently(false);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            delayer.setActive(false);
            return;
        }

        // Every tick, so a reconnect is recovered from without tracking connection state.
        delayer.attachIfNeeded();
        delayer.setLocalEntityId(mc.thePlayer.getEntityId());
        delayer.setMaxDelayMillis((long) maxDelay.asInt());
        delayer.setCooldownMillis((long) cooldown.asInt());
        delayer.setEligible(worthHolding(mc));
        delayer.setActive(true);

        // Asked every tick regardless of the setting: it is what advances the remembered hurt time,
        // and skipping it while the toggle is off would leave a stale one to misfire against when
        // the toggle comes back on mid-fight.
        boolean tookAHit = tookAHit(mc);
        if (tookAHit && releaseOnHurt.value()) {
            delayer.flushAll();
        }

        // Windows close on a clock, and a pinned player sends nothing to notice that on.
        delayer.tick();
    }

    /**
     * Entity ids of the players worth holding right now.
     *
     * <p>Distance is measured centre to centre, the same way the server measures its six-block
     * limit, so the sliders mean the same thing that check does.
     *
     * <p>Note this reads a held player's <em>pinned</em> position, which is the point: a player
     * already being held reads as standing still, so their window is ended by its own clock rather
     * than by a distance that has stopped changing.
     */
    private Set<Integer> worthHolding(Minecraft mc) {
        double min = minDistance.asDouble();
        double max = maxDistance.asDouble();
        double hurtLimit = maxHurtTime.asDouble();

        Set<Integer> targets = new HashSet<Integer>();
        for (EntityPlayer player : otherPlayers(mc)) {
            double distance = mc.thePlayer.getDistanceToEntity(player);
            if (distance < min || distance > max) {
                continue;
            }
            // How long until they can take damage again, near enough: the hurt animation and the
            // damage immunity both run about ten ticks, and the animation is the half a client
            // actually receives.
            if (hurtLimit < MAX_HURT_MILLIS && player.hurtTime * HURT_TICK_MILLIS > hurtLimit) {
                continue;
            }
            targets.add(Integer.valueOf(player.getEntityId()));
        }
        return targets;
    }

    private List<EntityPlayer> otherPlayers(Minecraft mc) {
        // Copied before iterating: the entity list is mutated as chunks load.
        List<EntityPlayer> result = new ArrayList<EntityPlayer>();
        for (Object entity : new ArrayList<Object>(mc.theWorld.playerEntities)) {
            if (entity instanceof EntityPlayer && entity != mc.thePlayer) {
                result.add((EntityPlayer) entity);
            }
        }
        return result;
    }

    /** True on the tick the local player's hurt animation starts, not for its whole duration. */
    private boolean tookAHit(Minecraft mc) {
        int hurtTime = mc.thePlayer.hurtTime;
        boolean started = hurtTime > 0 && hurtTime > lastHurtTime;
        lastHurtTime = hurtTime;
        return started;
    }

    private void reportUnavailableOnce() {
        if (reportedUnavailable) {
            return;
        }
        reportedUnavailable = true;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "§8[§bVantage§8] §fBacktrack cannot read entity ids on this build, so it has been "
                            + "switched off rather than run without doing anything."));
        }
    }
}
