package dev.vantage.module.impl.bedwars;

import dev.vantage.event.Render2DEvent;
import dev.vantage.event.Render3DEvent;
import dev.vantage.game.BedTracker;
import dev.vantage.game.TeamColour;
import dev.vantage.gui.Theme;
import dev.vantage.gui.font.Fonts;
import dev.vantage.gui.render.Render3D;
import dev.vantage.gui.render.RenderUtil;
import dev.vantage.module.Category;
import dev.vantage.module.Module;
import dev.vantage.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

/** Marks every bed through walls, in its team's colour, with whose it is and how far. */
public class BedEspModule extends Module {

    private final BooleanSetting labels = register(new BooleanSetting(
            "Labels", "Show the team and distance above each bed", true));
    private final BooleanSetting ownBed = register(new BooleanSetting(
            "Own Bed", "Mark your own bed too", true));

    public BedEspModule() {
        super("BedESP", Category.BEDWARS, "See every bed through walls");
        on(Render3DEvent.class, event -> drawBoxes());
        on(Render2DEvent.class, event -> drawLabels());
    }

    private static AxisAlignedBB boxOf(BedTracker.Bed bed) {
        BlockPos a = bed.getHead();
        BlockPos b = bed.getFoot();
        return new AxisAlignedBB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, a.getY() + 0.5625, Math.max(a.getZ(), b.getZ()) + 1);
    }

    private static int colourOf(BedTracker.Bed bed) {
        if (bed.isOwn()) {
            return Theme.safe();
        }
        TeamColour owner = bed.getOwner();
        return owner == TeamColour.UNKNOWN ? Theme.danger() : owner.getArgb();
    }

    private void drawBoxes() {
        Render3D.begin();
        for (BedTracker.Bed bed : BedTracker.get().getBeds()) {
            if (bed.isOwn() && !ownBed.value()) {
                continue;
            }
            Render3D.box(boxOf(bed), colourOf(bed), 0.25f);
        }
        Render3D.end();
    }

    private void drawLabels() {
        if (!labels.value()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        for (BedTracker.Bed bed : BedTracker.get().getBeds()) {
            if (bed.isOwn() && !ownBed.value()) {
                continue;
            }
            float[] screen = Render3D.project(bed.centreX(), bed.y() + 1.2, bed.centreZ(), scale);
            if (screen == null) {
                continue;
            }
            String owner = bed.isOwn() ? "Your bed" : (bed.getOwner() == TeamColour.UNKNOWN
                    ? "Bed" : bed.getOwner().getDisplayName() + " bed");
            String text = owner + "  " + Math.round(bed.horizontalDistanceTo(mc.thePlayer.posX, mc.thePlayer.posZ)) + "m";
            float width = Fonts.SMALL.getWidth(text) + 8.0f;
            RenderUtil.roundedRect(screen[0] - width / 2.0f, screen[1] - 6.0f, width, 12.0f, 3.0,
                    RenderUtil.withAlpha(Theme.panel(), 200));
            Fonts.SMALL.drawCentred(text, screen[0], screen[1] - 4.5f, colourOf(bed));
        }
    }
}
