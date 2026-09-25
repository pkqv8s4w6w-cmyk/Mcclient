package dev.vantage.module.impl.movement;

import dev.vantage.event.MoveEvent;
import dev.vantage.event.UpdateEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import dev.vantage.setting.EnumSetting;
import dev.vantage.setting.NumberSetting;
import dev.vantage.util.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Flight.
 *
 * <p>Motion flies at a set speed with jump and sneak for up and down. Vanilla hands the game's own
 * creative flight to the player. Glide only slows the fall.
 *
 * <p>A server with flight disabled counts every movement packet that does not descend by at least
 * 1/32 of a block while nowhere near the ground, and kicks at 80. The count never goes down, only
 * back to zero on landing, so occasional dips do not help. Anti-Kick sinks steadily whenever no
 * vertical key is held, which keeps those packets out of the count; climbing still adds to it.
 * Servers with allow-flight on never kick at all.
 */
public class FlyModule extends Module {

    public enum Mode { MOTION, VANILLA, GLIDE }

    private final EnumSetting<Mode> mode = register(new EnumSetting<Mode>(
            "Mode", "How to fly", Mode.MOTION));
    private final NumberSetting speed = register(new NumberSetting(
            "Speed", "Horizontal speed in blocks per tick", 1.0, 0.1, 5.0, 0.05));
    private final NumberSetting verticalSpeed = register(new NumberSetting(
            "Vertical Speed", "Up and down speed in blocks per tick", 0.6, 0.1, 3.0, 0.05));
    private final NumberSetting glideSpeed = register(new NumberSetting(
            "Glide Speed", "How fast to sink when gliding", 0.05, 0.0, 0.5, 0.01));
    private final BooleanSetting antiKick = register(new BooleanSetting(
            "Anti-Kick", "Sink slowly when not climbing, so a server with flight off does not kick you", true));

    /** Just faster than the 1/32 block a tick the server treats as not falling. */
    private static final double SINK = -0.035;

    public FlyModule() {
        super("Fly", Category.MOVEMENT, "Fly around");
        markBlatant();
        verticalSpeed.visibleWhen(() -> mode.get() == Mode.MOTION);
        glideSpeed.visibleWhen(() -> mode.get() == Mode.GLIDE);
        speed.visibleWhen(() -> mode.get() != Mode.GLIDE);
        on(MoveEvent.class, this::onMove);
        on(UpdateEvent.class, event -> onUpdate());
    }

    @Override
    public String getSuffix() {
        return mode.currentLabel();
    }

    @Override
    protected void onDisable() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        if (mode.get() == Mode.VANILLA && !player.capabilities.isCreativeMode) {
            player.capabilities.isFlying = false;
            player.capabilities.allowFlying = false;
            player.capabilities.setFlySpeed(0.05f);
        }
        player.motionX = 0.0;
        player.motionZ = 0.0;
    }

    private void onUpdate() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (mode.get() == Mode.VANILLA) {
            player.capabilities.allowFlying = true;
            player.capabilities.isFlying = true;
            player.capabilities.setFlySpeed((float) (speed.asDouble() / 10.0));
        }
    }

    private void onMove(MoveEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        switch (mode.get()) {
            case MOTION: {
                double vertical = 0.0;
                if (player.movementInput.jump) {
                    vertical = verticalSpeed.asDouble();
                } else if (player.movementInput.sneak) {
                    vertical = -verticalSpeed.asDouble();
                }
                if (antiKick.value() && vertical == 0.0) {
                    vertical = SINK;
                }
                player.motionY = vertical;
                event.setY(vertical);
                MovementUtil.setSpeed(event, speed.asDouble());
                break;
            }
            case GLIDE:
                if (!player.onGround && player.motionY < 0.0) {
                    player.motionY = -glideSpeed.asDouble();
                    event.setY(player.motionY);
                }
                break;
            default:
                if (antiKick.value() && !player.onGround && !player.movementInput.jump && event.getY() > SINK) {
                    event.setY(SINK);
                }
        }
    }
}
