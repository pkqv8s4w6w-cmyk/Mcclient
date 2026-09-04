package dev.vantage.gui.font;

import dev.vantage.Vantage;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws text from a real TrueType font instead of Minecraft's bitmap font.
 *
 * <p>Glyphs are rasterised once by AWT into a single texture atlas, then drawn as textured quads.
 * Baking happens at {@link #SUPERSAMPLE}x the requested size and is drawn back down, which is what
 * keeps the result sharp at any GUI scale - the bitmap font's blockiness is the main reason
 * hand-rolled interfaces look cheap.
 *
 * <p>Characters the bundled font has no glyph for are baked from the platform's logical sans
 * instead, so a missing symbol degrades to a different typeface rather than a blank box.
 */
public final class TTFFontRenderer {

    /** Bake at twice the display size so downscaling hides the rasteriser's pixel grid. */
    private static final int SUPERSAMPLE = 2;

    private static final int PADDING = 2;
    private static final int MAX_ATLAS_WIDTH = 2048;

    /**
     * Characters worth baking beyond ASCII and Latin-1: the marks the threat list and detector
     * use. Verified present in the bundled font on Java 8, apart from those that fall back.
     */
    private static final char[] EXTRA_CHARACTERS = {
            '✓', // check, for an intact bed
            '✗', // ballot x, for a broken bed
            '★', // filled star, for Bedwars level
            '●', // filled circle
            '▲', // up triangle
            '▼', // down triangle
            '•', // bullet
            '×', // multiplication sign, for violation counts
            '→', // right arrow
            '∞', // infinity, for an undefined ratio
    };

    private final float size;
    private final boolean bold;

    private final Map<Character, Glyph> glyphs = new HashMap<Character, Glyph>();
    private int textureId = -1;
    private int atlasWidth;
    private int atlasHeight;
    private float ascent;
    private float lineHeight;
    private boolean bakeFailed;

    public TTFFontRenderer(float size, boolean bold) {
        this.size = size;
        this.bold = bold;
    }

    // -- baking -----------------------------------------------------------------------------

    private Font loadBaseFont(float pointSize) {
        try (InputStream stream = Vantage.class.getResourceAsStream("/assets/vantage/fonts/inter.ttf")) {
            if (stream != null) {
                Font loaded = Font.createFont(Font.TRUETYPE_FONT, stream);
                return loaded.deriveFont(bold ? Font.BOLD : Font.PLAIN, pointSize);
            }
        } catch (Exception failure) {
            Vantage.LOGGER.warn("Bundled font could not be read; falling back to the system sans", failure);
        }
        return new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, (int) pointSize);
    }

    private synchronized void ensureBaked() {
        if (textureId != -1 || bakeFailed) {
            return;
        }
        try {
            bake();
        } catch (Throwable failure) {
            // Text is not worth crashing the game over. Callers fall back to Minecraft's font.
            bakeFailed = true;
            Vantage.LOGGER.error("Font atlas could not be built at size {}", size, failure);
        }
    }

    private void bake() {
        float pointSize = size * SUPERSAMPLE;
        Font primary = loadBaseFont(pointSize);
        Font fallback = new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, (int) pointSize);

        char[] characters = collectCharacters();

        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D scratchGraphics = scratch.createGraphics();
        FontMetrics primaryMetrics = scratchGraphics.getFontMetrics(primary);
        FontMetrics fallbackMetrics = scratchGraphics.getFontMetrics(fallback);

        ascent = primaryMetrics.getAscent() / (float) SUPERSAMPLE;
        lineHeight = primaryMetrics.getHeight() / (float) SUPERSAMPLE;
        int cellHeight = Math.max(primaryMetrics.getHeight(), fallbackMetrics.getHeight()) + PADDING * 2;

        // Shelf-pack: walk the characters, wrapping to a new row when the width runs out.
        int penX = 0;
        int penY = 0;
        int widest = 0;
        Map<Character, int[]> placements = new HashMap<Character, int[]>();
        for (char character : characters) {
            boolean usesFallback = !primary.canDisplay(character);
            FontMetrics metrics = usesFallback ? fallbackMetrics : primaryMetrics;
            int advance = metrics.charWidth(character);
            if (advance <= 0) {
                continue;
            }
            int cellWidth = advance + PADDING * 2;
            if (penX + cellWidth > MAX_ATLAS_WIDTH) {
                penX = 0;
                penY += cellHeight;
            }
            placements.put(character, new int[]{penX, penY, cellWidth, advance, usesFallback ? 1 : 0});
            penX += cellWidth;
            widest = Math.max(widest, penX);
        }
        scratchGraphics.dispose();

        atlasWidth = nextPowerOfTwo(Math.max(widest, 1));
        atlasHeight = nextPowerOfTwo(penY + cellHeight);

        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        // White glyphs, tinted per draw call by the vertex colour.
        graphics.setColor(Color.WHITE);

        for (Map.Entry<Character, int[]> entry : placements.entrySet()) {
            char character = entry.getKey();
            int[] placement = entry.getValue();
            boolean usesFallback = placement[4] == 1;
            graphics.setFont(usesFallback ? fallback : primary);
            FontMetrics metrics = usesFallback ? fallbackMetrics : primaryMetrics;
            graphics.drawString(String.valueOf(character),
                    placement[0] + PADDING,
                    placement[1] + PADDING + metrics.getAscent());
            glyphs.put(character, new Glyph(
                    placement[0] + PADDING,
                    placement[1] + PADDING,
                    placement[3],
                    metrics.getHeight(),
                    placement[3] / (float) SUPERSAMPLE));
        }
        graphics.dispose();

        textureId = uploadTexture(atlas);
    }

    private char[] collectCharacters() {
        StringBuilder builder = new StringBuilder();
        for (char c = 0x20; c <= 0x7E; c++) {
            builder.append(c);
        }
        for (char c = 0xA0; c <= 0xFF; c++) {
            builder.append(c);
        }
        for (char c : EXTRA_CHARACTERS) {
            builder.append(c);
        }
        char[] result = new char[builder.length()];
        builder.getChars(0, builder.length(), result, 0);
        return result;
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result <<= 1;
        }
        return result;
    }

    private static int uploadTexture(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int pixel : pixels) {
            buffer.put((byte) (pixel >> 16 & 0xFF)); // r
            buffer.put((byte) (pixel >> 8 & 0xFF));  // g
            buffer.put((byte) (pixel & 0xFF));       // b
            buffer.put((byte) (pixel >> 24 & 0xFF)); // a
        }
        buffer.flip();

        int id = GL11.glGenTextures();
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GlStateManager.bindTexture(0);
        return id;
    }

    // -- measuring --------------------------------------------------------------------------

    public boolean isUsable() {
        ensureBaked();
        return !bakeFailed;
    }

    public float getHeight() {
        ensureBaked();
        return lineHeight;
    }

    public float getAscent() {
        ensureBaked();
        return ascent;
    }

    public float getWidth(String text) {
        ensureBaked();
        if (bakeFailed || text == null) {
            return 0.0f;
        }
        float width = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            // Skip Minecraft colour codes; they position nothing.
            if (character == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            Glyph glyph = glyphs.get(character);
            if (glyph != null) {
                width += glyph.advance;
            }
        }
        return width;
    }

    /** Trims text to fit a pixel budget, appending an ellipsis when it had to cut. */
    public String trimToWidth(String text, float maxWidth) {
        if (getWidth(text) <= maxWidth) {
            return text;
        }
        float ellipsis = getWidth("...");
        StringBuilder builder = new StringBuilder();
        float width = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            float advance = getWidth(String.valueOf(text.charAt(i)));
            if (width + advance + ellipsis > maxWidth) {
                break;
            }
            builder.append(text.charAt(i));
            width += advance;
        }
        return builder.append("...").toString();
    }

    // -- drawing ----------------------------------------------------------------------------

    /**
     * Draws {@code text} with its left edge at {@code x} and its baseline positioned so the text
     * block's top sits at {@code y}.
     *
     * @return the x coordinate just past the last glyph
     */
    public float drawString(String text, float x, float y, int colour) {
        ensureBaked();
        if (bakeFailed || text == null || text.isEmpty()) {
            return x;
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.bindTexture(textureId);

        int alpha = colour >> 24 & 0xFF;
        if (alpha == 0) {
            alpha = 255; // A colour given without alpha means opaque, not invisible.
        }
        float red = (colour >> 16 & 0xFF) / 255.0f;
        float green = (colour >> 8 & 0xFF) / 255.0f;
        float blue = (colour & 0xFF) / 255.0f;
        float alphaFraction = alpha / 255.0f;
        GlStateManager.color(red, green, blue, alphaFraction);

        GL11.glBegin(GL11.GL_QUADS);
        float penX = x;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '§' && i + 1 < text.length()) {
                int[] coded = MinecraftColours.resolve(text.charAt(i + 1));
                if (coded != null) {
                    GL11.glEnd();
                    GlStateManager.color(coded[0] / 255.0f, coded[1] / 255.0f, coded[2] / 255.0f, alphaFraction);
                    GL11.glBegin(GL11.GL_QUADS);
                    i++;
                    continue;
                }
                if (text.charAt(i + 1) == 'r') {
                    GL11.glEnd();
                    GlStateManager.color(red, green, blue, alphaFraction);
                    GL11.glBegin(GL11.GL_QUADS);
                    i++;
                    continue;
                }
                i++;
                continue;
            }
            Glyph glyph = glyphs.get(character);
            if (glyph == null) {
                continue;
            }
            emitGlyph(glyph, penX, y);
            penX += glyph.advance;
        }
        GL11.glEnd();

        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
        return penX;
    }

    private void emitGlyph(Glyph glyph, float x, float y) {
        float drawWidth = glyph.width / (float) SUPERSAMPLE;
        float drawHeight = glyph.height / (float) SUPERSAMPLE;
        float u0 = glyph.atlasX / (float) atlasWidth;
        float v0 = glyph.atlasY / (float) atlasHeight;
        float u1 = (glyph.atlasX + glyph.width) / (float) atlasWidth;
        float v1 = (glyph.atlasY + glyph.height) / (float) atlasHeight;

        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x, y + drawHeight);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + drawWidth, y + drawHeight);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x + drawWidth, y);
    }

    public float drawCentred(String text, float centreX, float y, int colour) {
        return drawString(text, centreX - getWidth(text) / 2.0f, y, colour);
    }

    public float drawRightAligned(String text, float rightX, float y, int colour) {
        return drawString(text, rightX - getWidth(text), y, colour);
    }

    /** Draws a soft dark copy behind the text so it stays readable over bright terrain. */
    public float drawWithShadow(String text, float x, float y, int colour) {
        int shadowAlpha = (int) ((colour >> 24 & 0xFF) * 0.6f);
        drawString(text, x + 0.6f, y + 0.6f, (shadowAlpha == 0 ? 140 : shadowAlpha) << 24);
        return drawString(text, x, y, colour);
    }
}
