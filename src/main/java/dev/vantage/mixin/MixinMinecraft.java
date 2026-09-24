package dev.vantage.mixin;

import dev.vantage.event.ClickEvent;
import dev.vantage.event.EventBus;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every click the game actually handles, whether it came from the mouse or from a module pressing
 * the key. Counting here rather than sampling the button once a tick catches clicks shorter than a
 * tick, which is most of them at any real clicking speed.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "clickMouse", at = @At("HEAD"))
    private void vantage$leftClick(CallbackInfo callback) {
        EventBus.global().post(ClickEvent.LEFT);
    }

    @Inject(method = "rightClickMouse", at = @At("HEAD"))
    private void vantage$rightClick(CallbackInfo callback) {
        EventBus.global().post(ClickEvent.RIGHT);
    }
}
