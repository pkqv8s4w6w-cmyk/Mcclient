package dev.vantage.gui.render;

import dev.vantage.mixin.accessor.RenderManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Drawing in world space: boxes, lines and the projection from world to screen.
 *
 * <p>Call {@link #begin()} and {@link #end()} around a batch. Everything between draws with depth
 * testing off, so what is drawn shows through walls - which is the point of every caller.
 */
public final class Render3D {

    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer SCREEN = BufferUtils.createFloatBuffer(3);

    private Render3D() {
    }

    public static double cameraX() {
        return ((RenderManagerAccessor) Minecraft.getMinecraft().getRenderManager()).vantageRenderX();
    }

    public static double cameraY() {
        return ((RenderManagerAccessor) Minecraft.getMinecraft().getRenderManager()).vantageRenderY();
    }

    public static double cameraZ() {
        return ((RenderManagerAccessor) Minecraft.getMinecraft().getRenderManager()).vantageRenderZ();
    }

    public static void begin() {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.5f);
    }

    public static void end() {
        GL11.glLineWidth(1.0f);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }

    /** A world box moved into camera space. */
    public static AxisAlignedBB relative(AxisAlignedBB box) {
        return box.offset(-cameraX(), -cameraY(), -cameraZ());
    }

    /** An entity's box where it is drawn this frame, between its last two positions. */
    public static AxisAlignedBB interpolatedBox(Entity entity, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        AxisAlignedBB box = entity.getEntityBoundingBox();
        return box.offset(x - entity.posX, y - entity.posY, z - entity.posZ);
    }

    public static void filledBox(AxisAlignedBB world, int argb) {
        AxisAlignedBB b = relative(world);
        RenderUtil.applyColour(argb);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        // Bottom, top, then the four sides.
        buffer.pos(b.minX, b.minY, b.minZ).endVertex();
        buffer.pos(b.maxX, b.minY, b.minZ).endVertex();
        buffer.pos(b.maxX, b.minY, b.maxZ).endVertex();
        buffer.pos(b.minX, b.minY, b.maxZ).endVertex();

        buffer.pos(b.minX, b.maxY, b.minZ).endVertex();
        buffer.pos(b.minX, b.maxY, b.maxZ).endVertex();
        buffer.pos(b.maxX, b.maxY, b.maxZ).endVertex();
        buffer.pos(b.maxX, b.maxY, b.minZ).endVertex();

        buffer.pos(b.minX, b.minY, b.minZ).endVertex();
        buffer.pos(b.minX, b.maxY, b.minZ).endVertex();
        buffer.pos(b.maxX, b.maxY, b.minZ).endVertex();
        buffer.pos(b.maxX, b.minY, b.minZ).endVertex();

        buffer.pos(b.maxX, b.minY, b.maxZ).endVertex();
        buffer.pos(b.maxX, b.maxY, b.maxZ).endVertex();
        buffer.pos(b.minX, b.maxY, b.maxZ).endVertex();
        buffer.pos(b.minX, b.minY, b.maxZ).endVertex();

        buffer.pos(b.minX, b.minY, b.maxZ).endVertex();
        buffer.pos(b.minX, b.maxY, b.maxZ).endVertex();
        buffer.pos(b.minX, b.maxY, b.minZ).endVertex();
        buffer.pos(b.minX, b.minY, b.minZ).endVertex();

        buffer.pos(b.maxX, b.minY, b.minZ).endVertex();
        buffer.pos(b.maxX, b.maxY, b.minZ).endVertex();
        buffer.pos(b.maxX, b.maxY, b.maxZ).endVertex();
        buffer.pos(b.maxX, b.minY, b.maxZ).endVertex();
        tessellator.draw();
    }

    public static void outlinedBox(AxisAlignedBB world, int argb) {
        AxisAlignedBB b = relative(world);
        RenderUtil.applyColour(argb);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        double[][] corners = {
                {b.minX, b.minY, b.minZ}, {b.maxX, b.minY, b.minZ}, {b.maxX, b.minY, b.maxZ}, {b.minX, b.minY, b.maxZ},
                {b.minX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.maxZ}, {b.minX, b.maxY, b.maxZ}
        };
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : edges) {
            double[] a = corners[edge[0]];
            double[] c = corners[edge[1]];
            buffer.pos(a[0], a[1], a[2]).endVertex();
            buffer.pos(c[0], c[1], c[2]).endVertex();
        }
        tessellator.draw();
    }

    /** A box with a translucent fill and a solid edge, the usual way to mark something. */
    public static void box(AxisAlignedBB world, int argb, float fillAlpha) {
        filledBox(world, RenderUtil.withAlpha(argb, fillAlpha));
        outlinedBox(world, argb);
    }

    /** A line through world points, in order. */
    public static void polyline(java.util.List<double[]> points, int argb) {
        if (points.size() < 2) {
            return;
        }
        RenderUtil.applyColour(argb);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
        double cx = cameraX();
        double cy = cameraY();
        double cz = cameraZ();
        for (double[] point : points) {
            buffer.pos(point[0] - cx, point[1] - cy, point[2] - cz).endVertex();
        }
        tessellator.draw();
    }

    public static void line(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        RenderUtil.applyColour(argb);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        buffer.pos(x0 - cameraX(), y0 - cameraY(), z0 - cameraZ()).endVertex();
        buffer.pos(x1 - cameraX(), y1 - cameraY(), z1 - cameraZ()).endVertex();
        tessellator.draw();
    }

    /** A flat ring on the ground, for blast radii and landing spots. */
    public static void circle(double x, double y, double z, double radius, int argb) {
        RenderUtil.applyColour(argb);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION);
        int segments = Math.max(24, (int) (radius * 12));
        for (int i = 0; i < segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            buffer.pos(x + Math.cos(angle) * radius - cameraX(), y - cameraY(),
                    z + Math.sin(angle) * radius - cameraZ()).endVertex();
        }
        tessellator.draw();
    }

    /**
     * Records the matrices the world was drawn with. Call during world rendering; {@link #project}
     * then maps world points to the screen for the 2D overlay drawn afterwards.
     */
    public static void captureMatrices() {
        MODELVIEW.clear();
        PROJECTION.clear();
        VIEWPORT.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
    }

    /**
     * Where a world point lands on screen, in GUI-scaled coordinates.
     *
     * @return {x, y} or null if the point is behind the camera
     */
    public static float[] project(double x, double y, double z, int scaleFactor) {
        SCREEN.clear();
        boolean ok = GLU.gluProject((float) (x - cameraX()), (float) (y - cameraY()), (float) (z - cameraZ()),
                MODELVIEW, PROJECTION, VIEWPORT, SCREEN);
        if (!ok || SCREEN.get(2) < 0.0f || SCREEN.get(2) > 1.0f) {
            return null;
        }
        float screenX = SCREEN.get(0) / scaleFactor;
        float screenY = (Minecraft.getMinecraft().displayHeight - SCREEN.get(1)) / scaleFactor;
        return new float[]{screenX, screenY};
    }
}
