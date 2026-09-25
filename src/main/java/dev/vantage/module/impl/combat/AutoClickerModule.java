package dev.vantage.module.impl.combat;

import dev.vantage.Vantage;
import dev.vantage.combat.ClickTimer;
import dev.vantage.combat.SwingRhythm;
import dev.vantage.event.PacketEvent;
import dev.vantage.event.TickStartEvent;
import dev.vantage.game.TeamResolver;
import dev.vantage.mixin.accessor.MinecraftAccessor;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.InventoryUtil;
import dev.vantage.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clicks for you while you hold a mouse button.
 *
 * <p>Clicks go in as key presses at the start of the tick, so the game handles them exactly as it
 * handles real ones - the same swing, the same attack cooldown, the same block interaction. Rates
 * come from {@link ClickTimer}, which is shaped like a real hand rather than a metronome.
 *
 * <p>Auto Block blocks with your sword for the moment an enemy's hit is going to land, and not
 * otherwise. Each enemy's swings are timed as they arrive, and from the rhythm of their clicking
 * comes when their next swing will be. That swing only matters if it lands after the half second
 * of immunity your last hit gave you, and only if they will be in reach and aiming at you when it
 * comes - so their position and yours are carried forward to that moment and checked there. The
 * block is started early enough to reach the server before their hit does, allowing for your ping,
 * and let go as soon as the moment has passed so you can hit back. Someone who is not clicking in a
 * rhythm is not predicted at all.
 */
public class AutoClickerModule extends Module {

    private final BooleanSetting left = register(new BooleanSetting(
            "Left Click", "Click while the left button is held", true));
    private final NumberSetting leftMin = register(new NumberSetting(
            "Left Min CPS", "Slowest left click rate", 9.0, 1.0, 20.0, 1.0));
    private final NumberSetting leftMax = register(new NumberSetting(
            "Left Max CPS", "Fastest left click rate", 13.0, 1.0, 20.0, 1.0));
    private final BooleanSetting breakBlocks = register(new BooleanSetting(
            "Break Blocks", "Hold still instead of clicking while looking at a block, so it breaks", true));
    private final BooleanSetting weaponOnly = register(new BooleanSetting(
            "Weapon Only", "Left click only while holding a sword or tool", false));
    private final BooleanSetting autoBlock = register(new BooleanSetting(
            "Auto Block", "Block with your sword when an enemy is about to hit you", false));
    private final NumberSetting blockRange = register(new NumberSetting(
            "Block Range", "How close an enemy has to be to hit you", 3.4, 2.5, 5.0, 0.1, "m"));
    private final BooleanSetting right = register(new BooleanSetting(
            "Right Click", "Click while the right button is held", false));
    private final NumberSetting rightMin = register(new NumberSetting(
            "Right Min CPS", "Slowest right click rate", 12.0, 1.0, 20.0, 1.0));
    private final NumberSetting rightMax = register(new NumberSetting(
            "Right Max CPS", "Fastest right click rate", 16.0, 1.0, 20.0, 1.0));
    private final BooleanSetting blocksOnly = register(new BooleanSetting(
            "Blocks Only", "Right click only while holding blocks", true));

    /** Degrees an attacker's aim may be off your body and still be tracking it. */
    private static final float AIM_SLACK = 12.0f;
    /** Half your body's width plus the margin the game adds around it for hits. */
    private static final double BODY_RADIUS = 0.4;
    /** The furthest ahead positions are carried when checking a predicted hit. */
    private static final double MAX_LOOKAHEAD_TICKS = 10.0;

    private final ClickTimer leftTimer = new ClickTimer();
    private final ClickTimer rightTimer = new ClickTimer();
    private final Map<Integer, SwingRhythm> rhythms = new ConcurrentHashMap<Integer, SwingRhythm>();
    private boolean autoBlocking;
    private boolean attackNextTick;

    public AutoClickerModule() {
        super("AutoClicker", Category.COMBAT, "Clicks for you while you hold a mouse button");
        leftMin.visibleWhen(left::value);
        leftMax.visibleWhen(left::value);
        breakBlocks.visibleWhen(left::value);
        weaponOnly.visibleWhen(left::value);
        autoBlock.visibleWhen(left::value);
        blockRange.visibleWhen(() -> left.value() && autoBlock.value());
        rightMin.visibleWhen(right::value);
        rightMax.visibleWhen(right::value);
        blocksOnly.visibleWhen(right::value);
        on(TickStartEvent.class, event -> tick());
        on(PacketEvent.Receive.class, this::onPacket);
    }

    /** Network thread: time every arm swing as it arrives, which is as close to the click as we see. */
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof S0BPacketAnimation)) {
            return;
        }
        S0BPacketAnimation animation = (S0BPacketAnimation) event.getPacket();
        if (animation.getAnimationType() != 0) {
            return;
        }
        SwingRhythm rhythm = rhythms.get(animation.getEntityID());
        if (rhythm == null) {
            rhythm = new SwingRhythm();
            rhythms.put(animation.getEntityID(), rhythm);
        }
        rhythm.swing(System.currentTimeMillis());
    }

    @Override
    public String getSuffix() {
        return left.value() ? leftMin.asInt() + "-" + leftMax.asInt() : null;
    }

    @Override
    protected void onDisable() {
        attackNextTick = false;
        if (autoBlocking) {
            stopBlocking(Minecraft.getMinecraft());
        }
    }

    @Override
    public void onWorldChanged() {
        // The block ended with the old world's player; there is nothing left to let go of.
        autoBlocking = false;
        attackNextTick = false;
        rhythms.clear();
    }

    private void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            leftTimer.reset();
            rightTimer.reset();
            attackNextTick = false;
            if (autoBlocking) {
                stopBlocking(mc);
            }
            return;
        }
        long now = System.currentTimeMillis();

        boolean fighting = left.value() && Mouse.isButtonDown(0) && leftAllowed(mc);
        boolean block = autoBlock.value() && fighting && !Mouse.isButtonDown(1) && holdingSword(mc)
                && hitPredicted(mc, now);
        if (fighting) {
            // A click does nothing if the game is still in its post-miss cooldown.
            ((MinecraftAccessor) mc).vantageSetLeftClickCounter(0);
            boolean due = leftTimer.shouldClick(now, leftMin.asDouble(), leftMax.asDouble());
            if (block) {
                // A hit is about to land; a click now would only cost the block that stops it.
                attackNextTick = false;
            } else if (autoBlocking) {
                // Clicks are thrown away on a tick that ends a block, so a click due now goes out on
                // the next one.
                attackNextTick = attackNextTick || due;
            } else if (attackNextTick || due) {
                attackNextTick = false;
                KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
            }
        } else {
            leftTimer.reset();
            attackNextTick = false;
        }

        if (block && !autoBlocking) {
            startBlocking(mc);
        } else if (!block && autoBlocking) {
            stopBlocking(mc);
        }

        if (right.value() && Mouse.isButtonDown(1) && rightAllowed(mc)) {
            if (rightTimer.shouldClick(now, rightMin.asDouble(), rightMax.asDouble())) {
                KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            }
        } else {
            rightTimer.reset();
        }
    }

    private boolean leftAllowed(Minecraft mc) {
        if (weaponOnly.value() && !InventoryUtil.holdingWeapon()) {
            return false;
        }
        if (breakBlocks.value() && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }
        // Using an item stops clicks, unless the item in use is the block this module is holding.
        return autoBlocking || !mc.thePlayer.isUsingItem();
    }

    // -- auto block -------------------------------------------------------------------------

    private static boolean holdingSword(Minecraft mc) {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemSword;
    }

    /**
     * Whether a hit on you is due right about now: some enemy's next swing, by their rhythm, falls
     * after your immunity from the last hit runs out, the block has to be up now to meet it at the
     * server, and at that moment they will be in reach of you and aiming at you.
     */
    private boolean hitPredicted(Minecraft mc, long now) {
        EntityPlayerSP self = mc.thePlayer;
        long ping = ping(mc);
        // By this clock the server's immunity ends when yours does here; both are a half trip late.
        long vulnerableFrom = now + Math.max(0, self.hurtResistantTime - self.maxHurtResistantTime / 2) * 50L;
        AntiBotModule antiBot = Vantage.instance().modules().get(AntiBotModule.class);

        Iterator<Map.Entry<Integer, SwingRhythm>> entries = rhythms.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, SwingRhythm> entry = entries.next();
            SwingRhythm rhythm = entry.getValue();
            if (now - rhythm.lastSwing() > 5000L) {
                entries.remove();
                continue;
            }
            if (!(mc.theWorld.getEntityByID(entry.getKey()) instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer other = (EntityPlayer) mc.theWorld.getEntityByID(entry.getKey());
            if (other == self || other.isDead || other.getHealth() <= 0.0f || other.isSpectator()
                    || TeamResolver.isTeammate(other) || Vantage.instance().friends().isFriend(other.getName())
                    || (antiBot != null && antiBot.isEnabled() && antiBot.isBot(other))) {
                continue;
            }
            long interval = rhythm.interval();
            if (interval <= 0L) {
                continue;
            }
            // The first swing that can still hurt you and whose block is not already too late.
            long seenAt = rhythm.nextSwing(Math.max(vulnerableFrom, now + ping - SwingRhythm.slack(interval)));
            if (seenAt < 0L || !SwingRhythm.inBlockWindow(now, seenAt, ping, interval)) {
                continue;
            }
            double ticksAhead = Math.max(0.0, Math.min(MAX_LOOKAHEAD_TICKS, (seenAt - ping / 2 - now) / 50.0));
            if (connects(self, other, ticksAhead)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an attacker, carried forward {@code ticks} along with you at your current speeds, is
     * in reach of your body and aiming at it.
     */
    private boolean connects(EntityPlayerSP self, EntityPlayer attacker, double ticks) {
        Vec3 eyes = attacker.getPositionEyes(1.0f).addVector(
                (attacker.posX - attacker.lastTickPosX) * ticks,
                (attacker.posY - attacker.lastTickPosY) * ticks,
                (attacker.posZ - attacker.lastTickPosZ) * ticks);
        AxisAlignedBB body = self.getEntityBoundingBox().offset(
                (self.posX - self.lastTickPosX) * ticks,
                (self.posY - self.lastTickPosY) * ticks,
                (self.posZ - self.lastTickPosZ) * ticks);
        double reach = distanceToBox(eyes, body);
        if (reach > blockRange.asDouble()) {
            return false;
        }
        double centreX = (body.minX + body.maxX) / 2.0;
        double centreY = (body.minY + body.maxY) / 2.0;
        double centreZ = (body.minZ + body.maxZ) / 2.0;
        float[] atBody = RotationUtil.rotationsFor(centreX - eyes.xCoord, centreY - eyes.yCoord, centreZ - eyes.zCoord);
        double across = Math.sqrt((centreX - eyes.xCoord) * (centreX - eyes.xCoord)
                + (centreZ - eyes.zCoord) * (centreZ - eyes.zCoord));
        // Near enough, your body fills more of their view, so their aim can be further off it.
        float bodyAngle = (float) Math.toDegrees(Math.atan2(BODY_RADIUS, Math.max(0.1, across)));
        float aimError = RotationUtil.angleBetween(attacker.rotationYawHead, attacker.rotationPitch, atBody[0], atBody[1]);
        return aimError <= bodyAngle + AIM_SLACK;
    }

    private static double distanceToBox(Vec3 point, AxisAlignedBB box) {
        double dx = Math.max(box.minX - point.xCoord, Math.max(0.0, point.xCoord - box.maxX));
        double dy = Math.max(box.minY - point.yCoord, Math.max(0.0, point.yCoord - box.maxY));
        double dz = Math.max(box.minZ - point.zCoord, Math.max(0.0, point.zCoord - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Your round trip to the server, as the tab list reports it. */
    private static long ping(Minecraft mc) {
        NetworkPlayerInfo info = mc.getNetHandler() == null ? null
                : mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? 0L : Math.max(0, Math.min(1000, info.getResponseTime()));
    }

    private void startBlocking(Minecraft mc) {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        KeyBinding.onTick(key);
        autoBlocking = true;
    }

    /** Lets go of the block, unless the player is holding right click themselves. */
    private void stopBlocking(Minecraft mc) {
        KeyBinding use = mc.gameSettings.keyBindUseItem;
        boolean held = GameSettings.isKeyDown(use);
        KeyBinding.setKeyBindState(use.getKeyCode(), held);
        if (!held && mc.thePlayer != null && mc.thePlayer.isUsingItem()) {
            mc.playerController.onStoppedUsingItem(mc.thePlayer);
        }
        autoBlocking = false;
    }

    private boolean rightAllowed(Minecraft mc) {
        if (!blocksOnly.value()) {
            return true;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemBlock;
    }
}
