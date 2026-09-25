package dev.vantage.mixin;

import dev.vantage.event.EventBus;
import dev.vantage.event.SaveOptionsEvent;
import dev.vantage.event.Stage;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brackets options.txt being written, so modules can keep their temporary changes out of it. */
@Mixin(GameSettings.class)
public abstract class MixinGameSettings {

    @Inject(method = "saveOptions", at = @At("HEAD"))
    private void vantage$beforeSave(CallbackInfo callback) {
        EventBus.global().post(new SaveOptionsEvent(Stage.PRE));
    }

    @Inject(method = "saveOptions", at = @At("RETURN"))
    private void vantage$afterSave(CallbackInfo callback) {
        EventBus.global().post(new SaveOptionsEvent(Stage.POST));
    }
}
