package dev.vantage.module.impl.bedwars;

import dev.vantage.combat.RotationManager;
import dev.vantage.event.MotionEvent;
import dev.vantage.game.BedTracker;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.PacketDigger;
import dev.vantage.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

/**
 * Breaks enemy beds in range, straight through whatever is around them. A 1.8 server checks that
 * you are within six blocks of a block you dig, and nothing about whether you can see it.
 */
public class BedNukerModule extends Module {

    private final NumberSetting range = register(new NumberSetting(
            "Range", "How far away a bed can be broken; the server refuses past six", 4.5, 2.0, 6.0, 0.25, "m"));
    private final BooleanSetting rotate = register(new BooleanSetting(
            "Rotate", "Face the bed while breaking it", true));

    private final PacketDigger digger = new PacketDigger();

    public BedNukerModule() {
        super("BedNuker", Category.BEDWARS, "Breaks enemy beds in range through walls");
        markBlatant();
        on(MotionEvent.class, this::onMotion);
    }

    @Override
    protected void onDisable() {
        digger.abort();
    }

    @Override
    public void onWorldChanged() {
        digger.abort();
    }

    private void onMotion(MotionEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        BlockPos target = targetBlock(player);
        if (target == null) {
            digger.abort();
            return;
        }
        Vec3 centre = new Vec3(target.getX() + 0.5, target.getY() + 0.3, target.getZ() + 0.5);
        if (event.isPre()) {
            if (rotate.value()) {
                Vec3 eyes = player.getPositionEyes(1.0f);
                float[] facing = RotationUtil.rotationsFor(centre.xCoord - eyes.xCoord,
                        centre.yCoord - eyes.yCoord, centre.zCoord - eyes.zCoord);
                RotationManager.get().request(facing[0], facing[1], 25, 60.0f);
            }
            return;
        }
        digger.start(target, EnumFacing.UP);
        digger.tick();
    }

    /** The nearest half of the nearest enemy bed within range. */
    private BlockPos targetBlock(EntityPlayerSP player) {
        Vec3 eyes = player.getPositionEyes(1.0f);
        BlockPos best = null;
        double bestDistance = range.asDouble();
        for (BedTracker.Bed bed : BedTracker.get().getEnemyBeds()) {
            for (BlockPos half : new BlockPos[]{bed.getHead(), bed.getFoot()}) {
                double distance = eyes.distanceTo(new Vec3(half.getX() + 0.5, half.getY() + 0.5, half.getZ() + 0.5));
                if (distance <= bestDistance) {
                    best = half;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }
}
