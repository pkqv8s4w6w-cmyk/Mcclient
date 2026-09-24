package dev.vantage.hud.impl;

import dev.vantage.Vantage;
import dev.vantage.event.AttackEvent;
import dev.vantage.gui.Animated;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.EntityColours;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.hud.HudModule;
import dev.vantage.module.impl.combat.KillAuraModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;

import java.util.Locale;

/**
 * A card for whoever you are fighting: face, name, health bar, distance, and whether you are
 * winning the trade on health right now.
 *
 * <p>The target is KillAura's if it has one, otherwise whoever you last hit, for a few seconds.
 */
public class TargetHud extends HudModule {

    private static final float WIDTH = 132.0f;
    private static final float HEIGHT = 38.0f;
    private static final long LINGER_MILLIS = 4000L;

    private final Animated healthBar = new Animated(1.0, 0.12);
    private final Animated presence = new Animated(0.0, 0.06);
    private EntityLivingBase lastHit;
    private long lastHitAt;
    private EntityLivingBase shown;

    public TargetHud() {
        super("TargetHUD", "A card for whoever you are fighting");
        on(AttackEvent.class, event -> {
            if (event.getTarget() instanceof EntityLivingBase) {
                lastHit = (EntityLivingBase) event.getTarget();
                lastHitAt = System.currentTimeMillis();
            }
        });
    }

    @Override
    protected boolean drawsOwnPanel() {
        return true;
    }

    private EntityLivingBase current() {
        KillAuraModule aura = Vantage.instance().modules().get(KillAuraModule.class);
        EntityLivingBase target = aura == null ? null : aura.getTarget();
        if (target != null) {
            return target;
        }
        if (lastHit != null && !lastHit.isDead && lastHit.getHealth() > 0.0f
                && System.currentTimeMillis() - lastHitAt < LINGER_MILLIS) {
            return lastHit;
        }
        // In the editor, show the player themself so the card can be placed.
        return Minecraft.getMinecraft().currentScreen instanceof dev.vantage.hud.HudEditScreen
                ? Minecraft.getMinecraft().thePlayer : null;
    }

    @Override
    public float getContentWidth() {
        return WIDTH;
    }

    @Override
    public float getContentHeight() {
        return HEIGHT;
    }

    @Override
    protected void renderContent() {
        EntityLivingBase target = current();
        presence.setTarget(target == null ? 0.0 : 1.0);
        if (target != null) {
            shown = target;
        }
        float alpha = (float) presence.get();
        if (alpha < 0.02f || shown == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int panel = RenderUtil.withAlpha(Theme.panel(), (int) (235 * alpha));
        RenderUtil.shadow(0, 0, WIDTH, HEIGHT, 5.0, 4, RenderUtil.withAlpha(0xFF000000, (int) (90 * alpha)));
        RenderUtil.roundedRect(0, 0, WIDTH, HEIGHT, 5.0, panel);

        if (shown instanceof AbstractClientPlayer) {
            drawFace((AbstractClientPlayer) shown, 5.0f, 5.0f, 28.0f, alpha);
        }
        float textX = 38.0f;
        Fonts.BODY_BOLD.drawString(Fonts.BODY_BOLD.trimToWidth(shown.getName(), WIDTH - textX - 6.0f),
                textX, 5.0f, RenderUtil.withAlpha(EntityColours.of(shown), alpha));

        float health = shown.getHealth() + shown.getAbsorptionAmount();
        float fraction = Math.min(1.0f, shown.getHealth() / shown.getMaxHealth());
        healthBar.setTarget(fraction);
        float distance = mc.thePlayer.getDistanceToEntity(shown);
        String detail = String.format(Locale.ROOT, "%.1f hp  •  %.1fm", health, distance);
        Fonts.TINY.drawString(detail, textX, 16.0f, RenderUtil.withAlpha(Theme.textMuted(), alpha));

        if (shown != mc.thePlayer) {
            float ours = mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount();
            boolean winning = ours >= health;
            String verdict = winning ? "Winning" : "Losing";
            Fonts.TINY.drawRightAligned(verdict, WIDTH - 6.0f, 16.0f,
                    RenderUtil.withAlpha(winning ? Theme.safe() : Theme.danger(), alpha));
        }

        float barX = textX;
        float barY = 27.0f;
        float barWidth = WIDTH - textX - 6.0f;
        RenderUtil.roundedRect(barX, barY, barWidth, 5.0f, 2.5, RenderUtil.withAlpha(Theme.row(), alpha));
        float filled = (float) (barWidth * healthBar.get());
        if (filled > 1.0f) {
            RenderUtil.roundedRect(barX, barY, filled, 5.0f, 2.5,
                    RenderUtil.withAlpha(EntityColours.health(fraction), alpha));
        }
    }

    /** The front of the skin's head, plus the hat layer, from the player's skin texture. */
    private static void drawFace(AbstractClientPlayer player, float x, float y, float size, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(player.getLocationSkin());
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 8.0f, 8.0f, 8, 8, (int) size, (int) size, 64.0f, 64.0f);
        Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 40.0f, 8.0f, 8, 8, (int) size, (int) size, 64.0f, 64.0f);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
