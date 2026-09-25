package dev.vantage.combat;

import dev.vantage.Vantage;
import dev.vantage.game.TeamResolver;
import dev.vantage.module.impl.combat.AntiBotModule;
import dev.vantage.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Picks who a combat module should act on. */
public final class TargetFinder {

    public enum Sort { DISTANCE, HEALTH, ANGLE, HURT_TIME }

    private TargetFinder() {
    }

    /**
     * Everything within range and field of view that the settings allow, best first.
     *
     * @param fov total cone in degrees; 360 means all around
     */
    public static List<EntityLivingBase> find(TargetSettings settings, double range, float fov, Sort sort) {
        Minecraft mc = Minecraft.getMinecraft();
        List<EntityLivingBase> found = new ArrayList<EntityLivingBase>();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return found;
        }
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && isValid((EntityLivingBase) entity, settings, range, fov)) {
                found.add((EntityLivingBase) entity);
            }
        }
        sort(found, sort);
        return found;
    }

    public static EntityLivingBase best(TargetSettings settings, double range, float fov, Sort sort) {
        List<EntityLivingBase> found = find(settings, range, fov, sort);
        return found.isEmpty() ? null : found.get(0);
    }

    public static boolean isValid(EntityLivingBase entity, TargetSettings settings, double range, float fov) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP self = mc.thePlayer;
        if (entity == self || entity.isDead || entity.getHealth() <= 0.0f || entity instanceof EntityArmorStand) {
            return false;
        }
        if (!isAllowedKind(entity, settings)) {
            return false;
        }
        if (entity.isInvisible() && !settings.invisibles.value()) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (player.isSpectator() || player.capabilities.isCreativeMode && player.capabilities.disableDamage) {
                return false;
            }
            if (Vantage.instance().friends().isFriend(player.getName())) {
                return false;
            }
            if (settings.teams.value() && TeamResolver.isTeammate(player)) {
                return false;
            }
            AntiBotModule antiBot = Vantage.instance().modules().get(AntiBotModule.class);
            if (antiBot != null && antiBot.isEnabled() && antiBot.isBot(player)) {
                return false;
            }
        }
        if (distanceToBox(self, entity) > range) {
            return false;
        }
        if (fov < 360.0f && angleTo(self, entity) > fov / 2.0f) {
            return false;
        }
        return settings.walls.value() || self.canEntityBeSeen(entity);
    }

    private static boolean isAllowedKind(EntityLivingBase entity, TargetSettings settings) {
        if (entity instanceof EntityPlayer) {
            return settings.players.value();
        }
        if (entity instanceof EntityMob || entity instanceof EntitySlime || entity instanceof EntityGolem
                || entity instanceof EntityDragon || entity instanceof EntityWither) {
            return settings.mobs.value();
        }
        if (entity instanceof EntityAnimal || entity instanceof EntityVillager) {
            return settings.animals.value();
        }
        return false;
    }

    /** Eye to nearest point of the target's box: the distance the server's reach check uses. */
    public static double distanceToBox(EntityPlayerSP self, Entity target) {
        Vec3 eyes = self.getPositionEyes(1.0f);
        AxisAlignedBB box = target.getEntityBoundingBox();
        double x = clamp(eyes.xCoord, box.minX, box.maxX);
        double y = clamp(eyes.yCoord, box.minY, box.maxY);
        double z = clamp(eyes.zCoord, box.minZ, box.maxZ);
        double dx = eyes.xCoord - x;
        double dy = eyes.yCoord - y;
        double dz = eyes.zCoord - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Whether the server will take a hit on this target from where you stand. It measures feet to
     * feet rather than eyes to box, and allows six blocks when you can see the target but only three
     * through a wall; anything else is dropped without a word.
     */
    public static boolean serverAccepts(EntityPlayerSP self, Entity target) {
        double limit = self.canEntityBeSeen(target) ? 6.0 : 3.0;
        return self.getDistanceSqToEntity(target) < limit * limit;
    }

    /** How far off the crosshair a target is, in degrees. */
    public static float angleTo(EntityPlayerSP self, Entity target) {
        float[] wanted = aimAt(self, target, 0.5);
        return RotationUtil.angleBetween(self.rotationYaw, self.rotationPitch, wanted[0], wanted[1]);
    }

    /**
     * The rotation that points at a target.
     *
     * @param height where on the body to aim, 0 feet to 1 head
     */
    public static float[] aimAt(EntityPlayerSP self, Entity target, double height) {
        return aimAt(self, target, height, 1.0f);
    }

    /**
     * Like {@link #aimAt(EntityPlayerSP, Entity, double)}, but at where both players are drawn
     * this frame rather than where they were at the last tick. For anything that turns the camera
     * between ticks, so it aims at the model on screen instead of slightly ahead of it.
     */
    public static float[] aimAt(EntityPlayerSP self, Entity target, double height, float partialTicks) {
        Vec3 eyes = self.getPositionEyes(partialTicks);
        AxisAlignedBB box = target.getEntityBoundingBox().offset(
                (target.lastTickPosX - target.posX) * (1.0 - partialTicks),
                (target.lastTickPosY - target.posY) * (1.0 - partialTicks),
                (target.lastTickPosZ - target.posZ) * (1.0 - partialTicks));
        double x = (box.minX + box.maxX) / 2.0;
        double z = (box.minZ + box.maxZ) / 2.0;
        // Aim at the part of the body level with the eyes when it is in range, which is what a
        // person does, and only pick the requested height when the eyes are above or below the box.
        double y = clamp(eyes.yCoord, box.minY + (box.maxY - box.minY) * 0.1, box.maxY - 0.1);
        if (height >= 0.0 && (eyes.yCoord > box.maxY || eyes.yCoord < box.minY)) {
            y = box.minY + (box.maxY - box.minY) * height;
        }
        return RotationUtil.rotationsFor(x - eyes.xCoord, y - eyes.yCoord, z - eyes.zCoord);
    }

    public static void sort(List<EntityLivingBase> entities, Sort sort) {
        final EntityPlayerSP self = Minecraft.getMinecraft().thePlayer;
        Comparator<EntityLivingBase> order;
        switch (sort) {
            case HEALTH:
                order = new Comparator<EntityLivingBase>() {
                    @Override
                    public int compare(EntityLivingBase a, EntityLivingBase b) {
                        return Float.compare(a.getHealth() + a.getAbsorptionAmount(), b.getHealth() + b.getAbsorptionAmount());
                    }
                };
                break;
            case ANGLE:
                order = new Comparator<EntityLivingBase>() {
                    @Override
                    public int compare(EntityLivingBase a, EntityLivingBase b) {
                        return Float.compare(angleTo(self, a), angleTo(self, b));
                    }
                };
                break;
            case HURT_TIME:
                order = new Comparator<EntityLivingBase>() {
                    @Override
                    public int compare(EntityLivingBase a, EntityLivingBase b) {
                        return Integer.compare(a.hurtTime, b.hurtTime);
                    }
                };
                break;
            default:
                order = new Comparator<EntityLivingBase>() {
                    @Override
                    public int compare(EntityLivingBase a, EntityLivingBase b) {
                        return Double.compare(distanceToBox(self, a), distanceToBox(self, b));
                    }
                };
        }
        Collections.sort(entities, order);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
