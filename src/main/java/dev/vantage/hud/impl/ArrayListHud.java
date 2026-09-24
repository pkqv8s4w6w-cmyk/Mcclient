package dev.vantage.hud.impl;

import dev.vantage.Vantage;
import dev.vantage.gui.Animated;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.hud.HudModule;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The list of what is switched on, longest first, sliding in and out as modules toggle. Each line
 * takes its colour from a sweep across the accent, so the list reads as one piece.
 */
public class ArrayListHud extends HudModule {

    private final BooleanSetting suffixes = register(new BooleanSetting(
            "Suffixes", "Show each module's mode after its name", true));
    private final BooleanSetting rightAlign = register(new BooleanSetting(
            "Right Align", "Line up on the right edge, for the top-right corner", true));

    private final Map<Module, Animated> slide = new HashMap<Module, Animated>();

    public ArrayListHud() {
        super("ArrayList", "The list of modules you have switched on");
    }

    private static boolean listed(Module module) {
        Category category = module.getCategory();
        return category != Category.HUD && category != Category.CLIENT;
    }

    private String label(Module module) {
        String suffix = suffixes.value() ? module.getSuffix() : null;
        return suffix == null ? module.getName() : module.getName() + " §7" + suffix;
    }

    /** Enabled modules plus those still sliding out, longest label first. */
    private List<Module> shown() {
        List<Module> list = new ArrayList<Module>();
        for (Module module : Vantage.instance().modules().getModules()) {
            if (!listed(module)) {
                continue;
            }
            Animated animation = slide.get(module);
            if (animation == null) {
                animation = new Animated(0.0, 0.07);
                slide.put(module, animation);
            }
            animation.setTarget(module.isEnabled() ? 1.0 : 0.0);
            if (module.isEnabled() || animation.get() > 0.01) {
                list.add(module);
            }
        }
        list.sort((a, b) -> Float.compare(Fonts.BODY.getWidth(label(b)), Fonts.BODY.getWidth(label(a))));
        return list;
    }

    private float lineHeight() {
        return Fonts.BODY.getHeight() + 2.0f;
    }

    @Override
    public float getContentWidth() {
        float widest = Fonts.BODY.getWidth("ArrayList");
        for (Module module : shown()) {
            widest = Math.max(widest, Fonts.BODY.getWidth(label(module)) + 6.0f);
        }
        return widest;
    }

    @Override
    public float getContentHeight() {
        float total = 0.0f;
        for (Module module : shown()) {
            total += lineHeight() * (float) slide.get(module).get();
        }
        return Math.max(total, lineHeight());
    }

    @Override
    protected void renderContent() {
        List<Module> modules = shown();
        float width = getContentWidth();
        float y = 0.0f;
        long now = System.currentTimeMillis();
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            float progress = (float) slide.get(module).get();
            String text = label(module);
            float textWidth = Fonts.BODY.getWidth(text);
            // A slow sweep of brightness down the list, offset per line.
            double phase = (now / 1400.0 + i * 0.12) % 1.0;
            int colour = RenderUtil.blend(Theme.accent(), RenderUtil.shift(Theme.accent(), 0.3),
                    0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0));
            float slideOffset = (1.0f - progress) * (textWidth + 8.0f);
            float x = rightAlign.value() ? width - textWidth - 3.0f + slideOffset : 3.0f - slideOffset;
            int alpha = (int) (255 * progress);
            // A dark strip behind each line keeps it legible over sky and snow alike.
            float stripX = rightAlign.value() ? x - 3.0f : 0.0f;
            float stripWidth = textWidth + 6.0f;
            RenderUtil.rect(stripX, y, stripWidth, lineHeight() * progress,
                    RenderUtil.withAlpha(Theme.panel(), (int) (150 * progress)));
            float barX = rightAlign.value() ? width - 1.0f : 0.0f;
            RenderUtil.rect(barX, y, 1.0f, lineHeight() * progress, RenderUtil.withAlpha(colour, alpha));
            Fonts.BODY.drawString(text, x, y + 1.0f, RenderUtil.withAlpha(colour, alpha));
            y += lineHeight() * progress;
        }
    }
}
