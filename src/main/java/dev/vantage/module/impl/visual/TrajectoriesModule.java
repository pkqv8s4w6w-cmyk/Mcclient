package dev.vantage.module.impl.visual;

import dev.vantage.event.Render3DEvent;
import dev.vantage.gui.Theme;
import dev.vantage.gui.render.Render3D;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.util.Simulation;
import dev.vantage.util.WorldCollider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;

/** Shows where the bow, pearl, snowball or egg in your hand will land. */
public class TrajectoriesModule extends Module {

    public TrajectoriesModule() {
        super("Trajectories", Category.VISUAL, "Shows where your projectiles will land");
        on(Render3DEvent.class, this::onWorld);
    }

    private void onWorld(Render3DEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        ItemStack held = player.getHeldItem();
        if (held == null) {
            return;
        }
        Item item = held.getItem();
        Simulation.Kind kind;
        double velocity;
        float pitchOffset = 0.0f;
        if (item instanceof ItemBow) {
            kind = Simulation.Kind.ARROW;
            int charge = player.isUsingItem() ? held.getMaxItemUseDuration() - player.getItemInUseCount() : 20;
            velocity = Simulation.bowVelocity(charge);
            if (velocity < 0.1) {
                return;
            }
        } else if (item instanceof ItemEnderPearl || item instanceof ItemSnowball || item instanceof ItemEgg) {
            kind = Simulation.Kind.THROWABLE;
            velocity = 1.5;
        } else {
            return;
        }
        float partial = event.getPartialTicks();
        float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partial;
        float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partial + pitchOffset;
        double[] direction = Simulation.direction(yaw, pitch);
        double x = Render3D.cameraX() - Math.cos(Math.toRadians(yaw)) * 0.16;
        double y = Render3D.cameraY() + player.getEyeHeight() - 0.1;
        double z = Render3D.cameraZ() - Math.sin(Math.toRadians(yaw)) * 0.16;

        Simulation.Path path = Simulation.projectile(kind, x, y, z,
                direction[0] * velocity, direction[1] * velocity, direction[2] * velocity,
                0, 0, 0, 300, -64.0, new WorldCollider(mc.theWorld));

        Render3D.begin();
        Render3D.polyline(path.points, Theme.accent());
        if (path.impact != null) {
            double[] hit = path.impact;
            Render3D.box(new AxisAlignedBB(hit[0] - 0.25, hit[1] - 0.25, hit[2] - 0.25,
                    hit[0] + 0.25, hit[1] + 0.25, hit[2] + 0.25), Theme.accent(), 0.25f);
        }
        Render3D.end();
    }
}
