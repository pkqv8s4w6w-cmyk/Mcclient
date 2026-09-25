package dev.vantage.module.impl.visual;

import dev.vantage.event.Render3DEvent;
import dev.vantage.gui.render.Render3D;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

/** Outlines chests and ender chests through walls. */
public class StorageEspModule extends Module {

    private final NumberSetting range = register(new NumberSetting(
            "Range", "Furthest to draw", 64.0, 8.0, 128.0, 8.0, "m"));

    public StorageEspModule() {
        super("StorageESP", Category.VISUAL, "Outlines chests through walls");
        on(Render3DEvent.class, event -> {
            Minecraft mc = Minecraft.getMinecraft();
            Render3D.begin();
            for (TileEntity tile : mc.theWorld.loadedTileEntityList) {
                int colour;
                if (tile instanceof TileEntityChest) {
                    colour = 0xFFF5A623;
                } else if (tile instanceof TileEntityEnderChest) {
                    colour = 0xFFB072FF;
                } else {
                    continue;
                }
                BlockPos pos = tile.getPos();
                if (mc.thePlayer.getDistanceSq(pos) > range.asDouble() * range.asDouble()) {
                    continue;
                }
                Render3D.box(new AxisAlignedBB(pos.getX() + 0.06, pos.getY(), pos.getZ() + 0.06,
                        pos.getX() + 0.94, pos.getY() + 0.88, pos.getZ() + 0.94), colour, 0.15f);
            }
            Render3D.end();
        });
    }
}
