package dev.vantage.event;

/**
 * Asked once a frame about camera effects the player might want gone. Cancel the hurt shake by
 * setting it off; report free camera so terrain culling treats the view like a spectator's.
 */
public final class CameraEvent {

    private boolean hurtShake = true;
    private boolean freeCamera;

    public boolean isHurtShake() {
        return hurtShake;
    }

    public void setHurtShake(boolean hurtShake) {
        this.hurtShake = hurtShake;
    }

    public boolean isFreeCamera() {
        return freeCamera;
    }

    public void setFreeCamera(boolean freeCamera) {
        this.freeCamera = freeCamera;
    }
}
