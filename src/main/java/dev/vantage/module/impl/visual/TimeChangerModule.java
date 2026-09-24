package dev.vantage.module.impl.visual;

import dev.vantage.event.PacketEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S03PacketTimeUpdate;

/** Sets the time of day on your screen. Only you see it. */
public class TimeChangerModule extends Module {

    private final NumberSetting time = register(new NumberSetting(
            "Time", "0 is sunrise, 6000 noon, 13000 night, 18000 midnight", 6000.0, 0.0, 24000.0, 500.0));

    public TimeChangerModule() {
        super("TimeChanger", Category.VISUAL, "Choose the time of day you see");
        on(PacketEvent.Receive.class, event -> {
            if (event.getPacket() instanceof S03PacketTimeUpdate) {
                event.cancel();
            }
        });
    }

    @Override
    public void onTick() {
        Minecraft.getMinecraft().theWorld.setWorldTime(time.asInt());
    }
}
