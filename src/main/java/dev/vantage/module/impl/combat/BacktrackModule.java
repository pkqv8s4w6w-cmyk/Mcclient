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
 * Holds nearby players' movement packets back, so they can be hit where they were rather than
 * where they are.
 *
 * <p>The gap this uses is one vanilla leaves open. A server accepts a melee hit whenever the
 * attacker is within six blocks of the target - {@code NetHandlerPlayServer.processUseEntity}
 * checks {@code getDistanceSqToEntity < 36.0} - but the client only traces for a target out to
 * three. Delaying a player's position packets leaves their entity, and so their bounding box,
 * sitting where it was a moment ago; the crosshair trace hits that stale box, and the attack packet
 * that follows carries nothing but an entity id, so the server checks its own current positions and
 * counts the hit. Nothing here spoofs a rotation, redirects an attack or fakes a position: the
 * module holds packets and vanilla does the rest.
 *
 * <p>The distance band is measured the same way the server measures it, centre to centre, so the
 * numbers on the sliders mean the same thing as the six-block limit they sit under.
 *
 * <p>Note that a held player is not frozen. Once the queue fills, packets come out at the rate they
 * go in, leaving that player a constant distance behind rather than stopped - so someone running
 * away still reads as leaving the band, and holding stops on its own.
 *
 * <p>This is bannable on any public server. It is here for a private one.
 */
public class BacktrackModule extends Module {

    private final NumberSetting delay = register(new NumberSetting(
            "Delay", "How far behind their real position nearby players are held",
            200.0, 0.0, 1000.0, 10.0, "ms"));
    private final NumberSetting minDistance = register(new NumberSetting(
            "Min Distance", "Players closer than this are left alone; you can already hit them",
            0.0, 0.0, 6.0, 0.25, "m"));
    private final NumberSetting maxDistance = register(new NumberSetting(
            "Max Distance", "Players further than this are left alone; past six the server refuses the hit anyway",
            5.0, 0.0, 6.0, 0.25, "m"));
    private final BooleanSetting releaseOnHurt = register(new BooleanSetting(
            "Release On Hurt", "Hand back everything held the moment you take a hit", true));

    private int lastHurtTime;
    private boolean reportedUnavailable;

    public BacktrackModule() {
        super("Backtrack", Category.COMBAT, "Hit players where they were, not where they are");
    }

    @Override
    protected void onDisable() {
        PacketDelayer.instance().setActive(false);
        // Detaching hands back everything still queued, so nobody is left sitting behind.
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
        delayer.setDelayMillis((long) delay.asInt());
        delayer.setTargets(withinBand(mc));
        delayer.setActive(true);

        // Asked every tick regardless of the setting: it is what advances the remembered hurt time,
        // and skipping it while the toggle is off would leave a stale one to misfire against when
        // the toggle comes back on mid-fight.
        boolean tookAHit = tookAHit(mc);
        if (tookAHit && releaseOnHurt.value()) {
            delayer.flush();
        }
    }

    /** Entity ids of every other player currently inside the distance band. */
    private Set<Integer> withinBand(Minecraft mc) {
        double min = minDistance.asDouble();
        double max = maxDistance.asDouble();

        Set<Integer> targets = new HashSet<Integer>();
        for (EntityPlayer player : otherPlayers(mc)) {
            double distance = mc.thePlayer.getDistanceToEntity(player);
            if (distance >= min && distance <= max) {
                targets.add(Integer.valueOf(player.getEntityId()));
            }
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
