package dev.vantage.module.impl.combat;

import dev.vantage.combat.ClickTimer;
import dev.vantage.event.TickStartEvent;
import dev.vantage.mixin.accessor.MinecraftAccessor;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Mouse;

/**
 * Clicks for you while you hold a mouse button.
 *
 * <p>Clicks go in as key presses at the start of the tick, so the game handles them exactly as it
 * handles real ones - the same swing, the same attack cooldown, the same block interaction. Rates
 * come from {@link ClickTimer}, which is shaped like a real hand rather than a metronome.
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
    private final BooleanSetting blockHit = register(new BooleanSetting(
            "Block Hit", "Tap block between hits with a sword", false));
    private final NumberSetting blockHitChance = register(new NumberSetting(
            "Block Hit Chance", "How often a hit is followed by a block", 30.0, 1.0, 100.0, 1.0, "%"));
    private final BooleanSetting right = register(new BooleanSetting(
            "Right Click", "Click while the right button is held", false));
    private final NumberSetting rightMin = register(new NumberSetting(
            "Right Min CPS", "Slowest right click rate", 12.0, 1.0, 20.0, 1.0));
    private final NumberSetting rightMax = register(new NumberSetting(
            "Right Max CPS", "Fastest right click rate", 16.0, 1.0, 20.0, 1.0));
    private final BooleanSetting blocksOnly = register(new BooleanSetting(
            "Blocks Only", "Right click only while holding blocks", true));

    private final ClickTimer leftTimer = new ClickTimer();
    private final ClickTimer rightTimer = new ClickTimer();
    private int releaseBlockIn = -1;

    public AutoClickerModule() {
        super("AutoClicker", Category.COMBAT, "Clicks for you while you hold a mouse button");
        leftMin.visibleWhen(left::value);
        leftMax.visibleWhen(left::value);
        breakBlocks.visibleWhen(left::value);
        weaponOnly.visibleWhen(left::value);
        blockHit.visibleWhen(left::value);
        blockHitChance.visibleWhen(() -> left.value() && blockHit.value());
        rightMin.visibleWhen(right::value);
        rightMax.visibleWhen(right::value);
        blocksOnly.visibleWhen(right::value);
        on(TickStartEvent.class, event -> tick());
    }

    @Override
    public String getSuffix() {
        return left.value() ? leftMin.asInt() + "-" + leftMax.asInt() : null;
    }

    @Override
    protected void onDisable() {
        if (releaseBlockIn >= 0) {
            releaseBlock();
        }
    }

    private void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            leftTimer.reset();
            rightTimer.reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (releaseBlockIn >= 0 && releaseBlockIn-- == 0) {
            releaseBlock();
        }

        if (left.value() && Mouse.isButtonDown(0) && leftAllowed(mc)) {
            // A click does nothing if the game is still in its post-miss cooldown.
            ((MinecraftAccessor) mc).vantageSetLeftClickCounter(0);
            if (leftTimer.shouldClick(now, leftMin.asDouble(), leftMax.asDouble())) {
                KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                maybeBlockHit(mc);
            }
        } else {
            leftTimer.reset();
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
        return !mc.thePlayer.isUsingItem();
    }

    private boolean rightAllowed(Minecraft mc) {
        if (!blocksOnly.value()) {
            return true;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemBlock;
    }

    private void maybeBlockHit(Minecraft mc) {
        if (!blockHit.value() || mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSword)) {
            return;
        }
        if (Math.random() * 100.0 < blockHitChance.asDouble()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            releaseBlockIn = 1;
        }
    }

    /** Lets go of a block-hit's block, unless the player is holding right click themselves. */
    private void releaseBlock() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Mouse.isButtonDown(1)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
        releaseBlockIn = -1;
    }
}
