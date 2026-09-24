package dev.vantage.module.impl.bedwars;

import dev.vantage.combat.CombatUtil;
import dev.vantage.combat.RotationManager;
import dev.vantage.event.MotionEvent;
import dev.vantage.event.Render2DEvent;
import dev.vantage.event.Render3DEvent;
import dev.vantage.game.TeamColour;
import dev.vantage.game.TeamResolver;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.Render3D;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.RotationUtil;
import dev.vantage.util.Simulation;
import dev.vantage.util.WorldCollider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.AxisAlignedBB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shows where every pearl, fireball and TNT is going to end up, and warns you if it is you.
 *
 * <p>Enemy pearls get their full flight drawn with the landing spot marked, so you can be standing
 * there when they arrive. Fireballs get their impact point and blast radius, TNT its fuse and
 * radius. When a blast is about to land on you, a warning with the time left appears over the
 * crosshair; Auto Deflect hits incoming fireballs back.
 */
public class ProjectileForecastModule extends Module {

    private static final int MAX_TICKS = 200;

    private final BooleanSetting pearls = register(new BooleanSetting(
            "Pearls", "Show where thrown pearls will land", true));
    private final BooleanSetting fireballs = register(new BooleanSetting(
            "Fireballs", "Show where fireballs will hit", true));
    private final BooleanSetting tnt = register(new BooleanSetting(
            "TNT", "Show lit TNT with its fuse", true));
    private final NumberSetting fireballRadius = register(new NumberSetting(
            "Fireball Radius", "Blast radius to assume for fireballs", 3.0, 1.0, 6.0, 0.5, "m"));
    private final NumberSetting tntRadius = register(new NumberSetting(
            "TNT Radius", "Blast radius to assume for TNT", 4.0, 1.0, 8.0, 0.5, "m"));
    private final NumberSetting tntFuse = register(new NumberSetting(
            "TNT Fuse", "How long lit TNT takes to go off on your server; vanilla is 4 seconds. "
                    + "The server never tells the client, so this is counted from when it appears",
            4.0, 0.5, 8.0, 0.1, "s"));
    private final BooleanSetting deflect = register(new BooleanSetting(
            "Auto Deflect", "Hit incoming fireballs back", false));

    /** Who threw each pearl, guessed from who was standing where it appeared. */
    private final Map<Integer, String> throwers = new HashMap<Integer, String>();
    private final List<Forecast> forecasts = new ArrayList<Forecast>();
    private EntityFireball deflectTarget;
    private long lastDeflect;

    private static final class Forecast {
        final Entity entity;
        final Simulation.Path path;
        final double radius;
        final int colour;
        final String label;
        final double seconds;

        Forecast(Entity entity, Simulation.Path path, double radius, int colour, String label, double seconds) {
            this.entity = entity;
            this.path = path;
            this.radius = radius;
            this.colour = colour;
            this.label = label;
            this.seconds = seconds;
        }

        double[] end() {
            return path == null ? null : (path.impact != null ? path.impact : path.points.get(path.points.size() - 1));
        }
    }

    public ProjectileForecastModule() {
        super("Projectile Forecast", Category.BEDWARS, "Shows where pearls, fireballs and TNT will land");
        on(Render3DEvent.class, event -> drawWorld());
        on(Render2DEvent.class, event -> drawScreen());
        on(MotionEvent.class, this::onMotion);
    }

    @Override
    public void onWorldChanged() {
        throwers.clear();
        forecasts.clear();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getMinecraft();
        WorldCollider collider = new WorldCollider(mc.theWorld);
        forecasts.clear();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (pearls.value() && entity instanceof EntityEnderPearl) {
                String thrower = throwerOf(mc, entity);
                if (mc.thePlayer.getName().equals(thrower)) {
                    continue;
                }
                Simulation.Path path = Simulation.projectile(Simulation.Kind.THROWABLE, entity.posX, entity.posY, entity.posZ,
                        entity.motionX, entity.motionY, entity.motionZ, 0, 0, 0, MAX_TICKS, -64.0, collider);
                EntityPlayer player = thrower == null ? null : mc.theWorld.getPlayerEntityByName(thrower);
                TeamColour team = player == null ? TeamColour.UNKNOWN : TeamResolver.teamOf(player);
                int colour = team == TeamColour.UNKNOWN ? Theme.accent() : team.getArgb();
                String label = (thrower == null ? "Pearl" : thrower + "'s pearl");
                forecasts.add(new Forecast(entity, path, 0.0, colour, label, path.ticks / 20.0));
            } else if (fireballs.value() && entity instanceof EntityFireball) {
                EntityFireball fireball = (EntityFireball) entity;
                Simulation.Path path = Simulation.projectile(Simulation.Kind.FIREBALL, fireball.posX, fireball.posY, fireball.posZ,
                        fireball.motionX, fireball.motionY, fireball.motionZ,
                        fireball.accelerationX, fireball.accelerationY, fireball.accelerationZ, MAX_TICKS, -64.0, collider);
                forecasts.add(new Forecast(entity, path, fireballRadius.asDouble(), Theme.danger(), "Fireball", path.ticks / 20.0));
            } else if (tnt.value() && entity instanceof EntityTNTPrimed) {
                double remaining = Math.max(0.0, tntFuse.asDouble() - entity.ticksExisted / 20.0);
                forecasts.add(new Forecast(entity, null, tntRadius.asDouble(), Theme.warning(), "TNT", remaining));
            }
        }
        throwers.keySet().removeIf(id -> mc.theWorld.getEntityByID(id) == null);
    }

    private String throwerOf(Minecraft mc, Entity pearl) {
        Integer id = pearl.getEntityId();
        if (!throwers.containsKey(id)) {
            // Pearls spawn at the thrower's eyes, so whoever is closest on first sight threw it.
            EntityPlayer closest = null;
            double closestDistance = 16.0;
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                double distance = player.getDistanceSq(pearl.posX, pearl.posY - player.getEyeHeight(), pearl.posZ);
                if (distance < closestDistance) {
                    closest = player;
                    closestDistance = distance;
                }
            }
            throwers.put(id, closest == null ? null : closest.getName());
        }
        return throwers.get(id);
    }

    private void drawWorld() {
        if (forecasts.isEmpty()) {
            return;
        }
        Render3D.begin();
        for (Forecast forecast : forecasts) {
            if (forecast.path != null) {
                Render3D.polyline(forecast.path.points, RenderUtil.withAlpha(forecast.colour, 0.85f));
            }
            double[] end = forecast.entity instanceof EntityTNTPrimed
                    ? new double[]{forecast.entity.posX, forecast.entity.posY, forecast.entity.posZ} : forecast.end();
            if (end == null) {
                continue;
            }
            if (forecast.radius > 0.0) {
                Render3D.circle(end[0], end[1] + 0.05, end[2], forecast.radius, forecast.colour);
            } else {
                Render3D.box(new AxisAlignedBB(end[0] - 0.3, end[1], end[2] - 0.3, end[0] + 0.3, end[1] + 1.8, end[2] + 0.3),
                        forecast.colour, 0.2f);
            }
        }
        Render3D.end();
    }

    private void drawScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int scale = resolution.getScaleFactor();
        double soonest = Double.MAX_VALUE;
        String threat = null;
        for (Forecast forecast : forecasts) {
            double[] end = forecast.entity instanceof EntityTNTPrimed
                    ? new double[]{forecast.entity.posX, forecast.entity.posY, forecast.entity.posZ} : forecast.end();
            if (end == null) {
                continue;
            }
            float[] screen = Render3D.project(end[0], end[1] + (forecast.radius > 0 ? 1.0 : 2.1), end[2], scale);
            if (screen != null) {
                String text = forecast.label + "  " + String.format(Locale.ROOT, "%.1fs", forecast.seconds);
                float width = Fonts.TINY.getWidth(text) + 6.0f;
                RenderUtil.roundedRect(screen[0] - width / 2.0f, screen[1] - 5.0f, width, 10.0f, 2.5,
                        RenderUtil.withAlpha(Theme.panel(), 210));
                Fonts.TINY.drawCentred(text, screen[0], screen[1] - 3.5f, forecast.colour);
            }
            if (forecast.radius > 0.0) {
                double distance = mc.thePlayer.getDistance(end[0], end[1], end[2]);
                if (distance <= forecast.radius + 0.5 && forecast.seconds < soonest) {
                    soonest = forecast.seconds;
                    threat = forecast.label;
                }
            }
        }
        if (threat != null && soonest < 3.0) {
            String warning = threat + " landing on you  •  " + String.format(Locale.ROOT, "%.1fs", soonest);
            float width = Fonts.BODY_BOLD.getWidth(warning) + 14.0f;
            float x = resolution.getScaledWidth() / 2.0f - width / 2.0f;
            float y = resolution.getScaledHeight() / 2.0f - 34.0f;
            RenderUtil.roundedRect(x, y, width, 15.0f, 4.0, RenderUtil.withAlpha(Theme.danger(), 220));
            Fonts.BODY_BOLD.drawString(warning, x + 7.0f, y + 3.0f, 0xFFFFFFFF);
        }
    }

    private void onMotion(MotionEvent event) {
        if (!deflect.value()) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (event.isPre()) {
            deflectTarget = incomingFireball(player);
            if (deflectTarget != null) {
                float[] facing = RotationUtil.rotationsFor(deflectTarget.posX - player.posX,
                        deflectTarget.posY - (player.posY + player.getEyeHeight()), deflectTarget.posZ - player.posZ);
                RotationManager.get().request(facing[0], facing[1], 60, 180.0f);
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (deflectTarget != null && now - lastDeflect > 250L) {
            CombatUtil.attack(deflectTarget, true);
            lastDeflect = now;
        }
    }

    /** A fireball within hitting distance and closing on you. */
    private static EntityFireball incomingFireball(EntityPlayerSP player) {
        for (Entity entity : Minecraft.getMinecraft().theWorld.loadedEntityList) {
            if (!(entity instanceof EntityFireball) || player.getDistanceToEntity(entity) > 4.5f) {
                continue;
            }
            EntityFireball fireball = (EntityFireball) entity;
            double towardX = player.posX - fireball.posX;
            double towardZ = player.posZ - fireball.posZ;
            double headingX = fireball.motionX + fireball.accelerationX;
            double headingZ = fireball.motionZ + fireball.accelerationZ;
            if (towardX * headingX + towardZ * headingZ > 0.0) {
                return fireball;
            }
        }
        return null;
    }
}
