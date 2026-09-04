package dev.vantage.gui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Immediate-mode drawing helpers for the client's interface.
 *
 * <p>1.8.9 runs a compatibility profile, so the straightforward {@code glBegin}/{@code glEnd} path
 * is available and is what the vanilla GUI itself uses. Every method here leaves the GL state as
 * it found it, because Minecraft's own rendering is unforgiving about leaked state - a stray
 * disabled texture unit shows up as an invisible hotbar three frames later.
 */
public final class RenderUtil {

    private RenderUtil() {
    }

    // -- state ------------------------------------------------------------------------------

    private static void beginShapes() {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
    }

    private static void endShapes() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }

    public static void applyColour(int argb) {
        GlStateManager.color(
                (argb >> 16 & 0xFF) / 255.0f,
                (argb >> 8 & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f,
                (argb >> 24 & 0xFF) / 255.0f);
    }

    // -- colour maths -----------------------------------------------------------------------

    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    public static int withAlpha(int argb, float multiplier) {
        int alpha = (int) ((argb >> 24 & 0xFF) * Math.max(0.0f, Math.min(1.0f, multiplier)));
        return withAlpha(argb, alpha);
    }

    /** Linear blend between two ARGB colours. {@code progress} of 0 returns {@code from}. */
    public static int blend(int from, int to, double progress) {
        double t = Math.max(0.0, Math.min(1.0, progress));
        int a = (int) ((from >> 24 & 0xFF) + ((to >> 24 & 0xFF) - (from >> 24 & 0xFF)) * t);
        int r = (int) ((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int g = (int) ((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Lightens for positive amounts, darkens for negative. Used for hover and press states. */
    public static int shift(int argb, double amount) {
        return blend(argb, amount >= 0 ? 0xFFFFFFFF : 0xFF000000, Math.abs(amount));
    }

    // -- rectangles -------------------------------------------------------------------------

    public static void rect(double x, double y, double width, double height, int colour) {
        beginShapes();
        applyColour(colour);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x, y + height);
        GL11.glVertex2d(x + width, y + height);
        GL11.glVertex2d(x + width, y);
        GL11.glVertex2d(x, y);
        GL11.glEnd();
        endShapes();
    }

    public static void gradientRect(double x, double y, double width, double height, int top, int bottom) {
        beginShapes();
        GL11.glBegin(GL11.GL_QUADS);
        applyColour(top);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x + width, y);
        applyColour(bottom);
        GL11.glVertex2d(x + width, y + height);
        GL11.glVertex2d(x, y + height);
        GL11.glEnd();
        endShapes();
    }

    public static void horizontalGradientRect(double x, double y, double width, double height, int left, int right) {
        beginShapes();
        GL11.glBegin(GL11.GL_QUADS);
        applyColour(left);
        GL11.glVertex2d(x, y + height);
        GL11.glVertex2d(x, y);
        applyColour(right);
        GL11.glVertex2d(x + width, y);
        GL11.glVertex2d(x + width, y + height);
        GL11.glEnd();
        endShapes();
    }

    // -- rounded shapes ---------------------------------------------------------------------

    /**
     * Segment count for a corner arc. Scaled by radius so small chips stay cheap and large panels
     * still look round rather than faceted.
     */
    private static int arcSegments(double radius) {
        return Math.max(4, (int) Math.ceil(radius * 1.8));
    }

    /** Emits the perimeter of a rounded rectangle. Caller supplies the primitive and colour. */
    private static void traceRoundedRect(double x, double y, double width, double height, double radius) {
        double r = Math.max(0.0, Math.min(radius, Math.min(width, height) / 2.0));
        int segments = arcSegments(r);
        // Corner centres, walking clockwise from the top-left.
        double[][] corners = {
                {x + r, y + r, 180.0},
                {x + width - r, y + r, 270.0},
                {x + width - r, y + height - r, 0.0},
                {x + r, y + height - r, 90.0},
        };
        for (double[] corner : corners) {
            for (int step = 0; step <= segments; step++) {
                double angle = Math.toRadians(corner[2] + (90.0 * step / segments));
                GL11.glVertex2d(corner[0] + Math.cos(angle) * r, corner[1] + Math.sin(angle) * r);
            }
        }
    }

    public static void roundedRect(double x, double y, double width, double height, double radius, int colour) {
        beginShapes();
        applyColour(colour);
        // A rounded rectangle is convex, so GL_POLYGON fills it correctly in one pass.
        GL11.glBegin(GL11.GL_POLYGON);
        traceRoundedRect(x, y, width, height, radius);
        GL11.glEnd();
        endShapes();
    }

    public static void roundedGradientRect(double x, double y, double width, double height,
                                           double radius, int top, int bottom) {
        beginShapes();
        GL11.glBegin(GL11.GL_POLYGON);
        double r = Math.max(0.0, Math.min(radius, Math.min(width, height) / 2.0));
        int segments = arcSegments(r);
        double[][] corners = {
                {x + r, y + r, 180.0},
                {x + width - r, y + r, 270.0},
                {x + width - r, y + height - r, 0.0},
                {x + r, y + height - r, 90.0},
        };
        for (double[] corner : corners) {
            for (int step = 0; step <= segments; step++) {
                double angle = Math.toRadians(corner[2] + (90.0 * step / segments));
                double vx = corner[0] + Math.cos(angle) * r;
                double vy = corner[1] + Math.sin(angle) * r;
                applyColour(blend(top, bottom, (vy - y) / height));
                GL11.glVertex2d(vx, vy);
            }
        }
        GL11.glEnd();
        endShapes();
    }

    public static void roundedOutline(double x, double y, double width, double height,
                                      double radius, float thickness, int colour) {
        beginShapes();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(thickness);
        applyColour(colour);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        // Inset by half the stroke so the line sits on the edge instead of straddling it.
        double inset = thickness / 2.0;
        traceRoundedRect(x + inset, y + inset, width - thickness, height - thickness, radius - inset);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0f);
        endShapes();
    }

    public static void circle(double centreX, double centreY, double radius, int colour) {
        beginShapes();
        applyColour(colour);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(centreX, centreY);
        int segments = Math.max(12, (int) Math.ceil(radius * 4));
        for (int step = 0; step <= segments; step++) {
            double angle = 2.0 * Math.PI * step / segments;
            GL11.glVertex2d(centreX + Math.cos(angle) * radius, centreY + Math.sin(angle) * radius);
        }
        GL11.glEnd();
        endShapes();
    }

    /**
     * A soft drop shadow, built from concentric rounded rectangles with falling alpha.
     *
     * <p>Cheaper and far more predictable across drivers than a real blur pass, and at the sizes
     * the GUI uses the difference is not visible.
     */
    public static void shadow(double x, double y, double width, double height,
                              double radius, int spread, int colour) {
        int baseAlpha = colour >> 24 & 0xFF;
        for (int layer = spread; layer >= 1; layer--) {
            // Quadratic falloff reads as a soft edge; linear looks like a stack of outlines.
            double falloff = (double) (spread - layer + 1) / spread;
            int alpha = (int) (baseAlpha * falloff * falloff / spread * 2.0);
            if (alpha <= 0) {
                continue;
            }
            roundedRect(x - layer, y - layer, width + layer * 2, height + layer * 2,
                    radius + layer, withAlpha(colour, alpha));
        }
    }

    // -- clipping ---------------------------------------------------------------------------

    /**
     * Clips drawing to a rectangle given in GUI coordinates.
     *
     * <p>{@code glScissor} works in physical pixels measured from the bottom-left of the window,
     * while the GUI works in scaled units from the top-left, so both axes need converting.
     */
    public static void beginScissor(double x, double y, double width, double height) {
        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
        int factor = resolution.getScaleFactor();
        int scissorX = (int) Math.floor(x * factor);
        int scissorY = (int) Math.floor((resolution.getScaledHeight() - (y + height)) * factor);
        int scissorWidth = (int) Math.ceil(width * factor);
        int scissorHeight = (int) Math.ceil(height * factor);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, Math.max(0, scissorWidth), Math.max(0, scissorHeight));
    }

    public static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // -- hit testing ------------------------------------------------------------------------

    public static boolean isInside(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
