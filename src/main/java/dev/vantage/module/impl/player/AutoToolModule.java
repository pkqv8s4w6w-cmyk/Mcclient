package dev.vantage.module.impl.player;

import dev.vantage.event.TickStartEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.util.InventoryUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Mouse;

/** Holds the best tool for the block you are mining, and goes back to what you had after. */
public class AutoToolModule extends Module {

    private final BooleanSetting switchBack = register(new BooleanSetting(
            "Switch Back", "Return to the previous item when you stop mining", true));
    private final BooleanSetting sword = register(new BooleanSetting(
            "Sword", "Also switch to your best sword when you hit a player", false));

    private int previousSlot = -1;

    public AutoToolModule() {
        super("AutoTool", Category.PLAYER, "Switch to the best tool as you mine");
        on(TickStartEvent.class, event -> tick());
    }

    private void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) {
            return;
        }
        MovingObjectPosition over = mc.objectMouseOver;
        boolean mining = Mouse.isButtonDown(0) && over != null
                && over.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
        boolean fighting = sword.value() && Mouse.isButtonDown(0) && over != null
                && over.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY;
        if (mining) {
            Block block = mc.theWorld.getBlockState(over.getBlockPos()).getBlock();
            choose(InventoryUtil.toolSlotFor(block));
        } else if (fighting) {
            choose(InventoryUtil.swordSlot());
        } else if (previousSlot >= 0) {
            if (switchBack.value()) {
                InventoryUtil.select(previousSlot);
            }
            previousSlot = -1;
        }
    }

    private void choose(int slot) {
        Minecraft mc = Minecraft.getMinecraft();
        if (slot < 0 || slot == mc.thePlayer.inventory.currentItem) {
            return;
        }
        if (previousSlot < 0) {
            previousSlot = mc.thePlayer.inventory.currentItem;
        }
        InventoryUtil.select(slot);
    }
}
