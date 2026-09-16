package play.xponer.astronima.compat.jei;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import play.xponer.astronima.client.hud.MachineFrame;

import java.util.List;

/**
 * A plotted curve for a JEI page.
 *
 * <p>These pages used to be a table of numbers, and a table is the wrong shape for
 * what they have to teach. Every one of these machines has a control whose two
 * consequences move in <em>opposite directions</em>, and that is a fact about a shape:
 * you can see two lines cross, or one peak while the other keeps climbing, in less
 * time than it takes to read one row. Reading it out of a column of percentages means
 * doing the plotting in your head.
 *
 * <p>Drawn with rectangles because that is what a GUI gives you, so lines are stepped
 * rather than smooth — which suits a pixel interface and matches the segmented gauges
 * on the machines themselves.
 */
public final class Chart {
    /** Room a chart label has. */
    private static final int TEXT_WIDTH = 160;

    /** One plotted line: a label, a colour, and values sampled left to right. */
    public record Series(String label, int colour, List<Float> values) {}

    private static final int AXIS = 0xFF5A5E60;
    private static final int GRID = 0xFF7E8284;

    /**
     * Draws a framed chart with its series, axis labels and legend.
     *
     * @param axisLabel what the horizontal axis varies, e.g. "jaw gap"
     */
    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y,
                            int width, int height, String axisLabel, List<Series> series) {
        MachineFrame.well(graphics, x, y, width, height);

        int plotX = x + 2;
        int plotY = y + 2;
        int plotW = width - 4;
        int plotH = height - 4;

        // Quarter gridlines: enough to read a value off, few enough to stay quiet.
        for (int i = 1; i < 4; i++) {
            int gridY = plotY + plotH * i / 4;
            for (int gx = plotX; gx < plotX + plotW; gx += 3) {
                graphics.fill(gx, gridY, gx + 1, gridY + 1, GRID);
            }
        }
        graphics.fill(plotX, plotY + plotH - 1, plotX + plotW, plotY + plotH, AXIS);

        for (Series line : series) {
            plot(graphics, plotX, plotY, plotW, plotH, line);
        }
        drawLegend(graphics, font, plotX, plotY, plotW, series);

        // The axis caption gets the strip below the plot to itself.
        MachineFrame.label(graphics, font, axisLabel, x, y + height + 3, TEXT_WIDTH);
    }

    /**
     * Legend keys stacked inside the plot's top-left.
     *
     * <p>Inside rather than below, because below is where the axis caption goes and
     * the two used to be drawn at the same height — overlapping into unreadable
     * nonsense. Inside the plot they also sit near the lines they name, which is where
     * a legend is most useful.
     */
    private static void drawLegend(GuiGraphicsExtractor graphics, Font font,
                                   int plotX, int plotY, int plotW, List<Series> series) {
        int row = plotY + 1;
        for (Series line : series) {
            int labelWidth = font.width(Component.literal(line.label()));
            int x = plotX + plotW - labelWidth - 6;
            graphics.fill(x - 5, row + 2, x - 2, row + 5, line.colour());
            MachineFrame.value(graphics, font, line.label(), x, row, TEXT_WIDTH, line.colour());
            row += 9;
        }
    }

    /**
     * Plots one series as connected vertical steps.
     *
     * <p>Each sample is joined to the last by filling the span between their heights,
     * so a rising line reads as continuous rather than as a row of dots. Doing it with
     * spans instead of a line algorithm keeps every pixel on the grid, which is what
     * stops it looking blurred at GUI scale.
     */
    private static void plot(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             Series series) {
        List<Float> values = series.values();
        if (values.size() < 2) {
            return;
        }
        int previousY = valueToY(values.get(0), y, height);
        for (int i = 1; i < values.size(); i++) {
            int px = x + (width - 2) * (i - 1) / (values.size() - 1);
            int nx = x + (width - 2) * i / (values.size() - 1);
            int currentY = valueToY(values.get(i), y, height);

            // The vertical joint between this sample and the last.
            int top = Math.min(previousY, currentY);
            int bottom = Math.max(previousY, currentY);
            graphics.fill(px, top, px + 2, bottom + 2, series.colour());
            // The horizontal run to the next sample.
            graphics.fill(px, currentY, nx + 2, currentY + 2, series.colour());
            previousY = currentY;
        }
    }

    private static int valueToY(float value, int y, int height) {
        float clamped = Math.clamp(value, 0f, 1f);
        return y + Math.round((height - 3) * (1 - clamped));
    }

    private Chart() {}
}
