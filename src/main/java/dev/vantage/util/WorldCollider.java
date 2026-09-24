package dev.vantage.util;

import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Collision for {@link Simulation} against the real world's blocks. */
public final class WorldCollider implements Simulation.Collider {

    private final World world;

    public WorldCollider(World world) {
        this.world = world;
    }

    @Override
    public double hit(double x0, double y0, double z0, double x1, double y1, double z1) {
        Vec3 from = new Vec3(x0, y0, z0);
        Vec3 to = new Vec3(x1, y1, z1);
        MovingObjectPosition hit = world.rayTraceBlocks(from, to, false, true, false);
        if (hit == null || hit.hitVec == null) {
            return -1.0;
        }
        double length = from.distanceTo(to);
        return length <= 0.0 ? 0.0 : Math.min(1.0, from.distanceTo(hit.hitVec) / length);
    }
}
