package dev.vantage.module.impl.visual;

import dev.vantage.event.CameraEvent;
import dev.vantage.module.Category;
import dev.vantage.module.Module;

/** Stops the screen shaking when you take damage. */
public class NoHurtCamModule extends Module {

    public NoHurtCamModule() {
        super("NoHurtCam", Category.VISUAL, "No screen shake when you take damage");
        on(CameraEvent.class, event -> event.setHurtShake(false));
    }
}
