package play.xponer.astronima.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Colour and geometry maths for the instrument panels.
 *
 * <p>Separated from the drawing so it can be unit-tested, and because the reason the
 * old panels looked flat was never a missing library — it was that everything was a
 * solid rectangle. Depth in a pixel UI comes from a handful of cheap tricks done
 * consistently: a body that is darker at the bottom than the top, a one-pixel light
 * edge where a surface catches light, a one-pixel dark edge where it falls away, and
 * chamfered corners so nothing reads as a raw box. All of that is arithmetic, and it
 * lives here.
 */
public final class HudPaint {
    /**
     * Pixels to inset a corner row, giving a chamfer that reads as a rounded corner at
     * HUD scale. Circles do not survive being drawn three pixels wide; a triangular
     * chamfer does, which is what pixel art has always used.
     *
     * @param rowFromEdge 0 for the outermost row of the corner
     */
    public static int cornerInset(int rowFromEdge, int radius) {
        return Math.max(0, radius - 1 - rowFromEdge);
    }

    /** Multiplies a packed ARGB colour's brightness, leaving alpha alone. */
    public static int shade(int argb, float factor) {
        int a = argb >>> 24;
        int r = clampByte(Math.round(((argb >> 16) & 0xFF) * factor));
        int g = clampByte(Math.round(((argb >> 8) & 0xFF) * factor));
        int b = clampByte(Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Replaces a colour's alpha, 0..255. */
    public static int withAlpha(int argb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }

    /** Blends two packed colours; {@code t} of 0 gives the first. */
    public static int mix(int from, int to, float t) {
        float alpha = Math.clamp(t, 0f, 1f);
        int a = Math.round(((from >>> 24) * (1 - alpha)) + ((to >>> 24) * alpha));
        int r = Math.round((((from >> 16) & 0xFF) * (1 - alpha)) + (((to >> 16) & 0xFF) * alpha));
        int g = Math.round((((from >> 8) & 0xFF) * (1 - alpha)) + (((to >> 8) & 0xFF) * alpha));
        int b = Math.round(((from & 0xFF) * (1 - alpha)) + ((to & 0xFF) * alpha));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Moves a displayed value toward its target, framerate-independently.
     *
     * <p>Instruments that snap look cheap and, worse, are hard to read — a needle that
     * jumps between two values every tick tells you less than one that glides. This is
     * exponential smoothing with the frame time folded in, so the result does not
     * depend on how fast the machine is drawing.
     *
     * @param responseSeconds roughly how long it takes to cover most of the distance
     */
    public static float approach(float current, float target, float dtSeconds,
                                 float responseSeconds) {
        if (responseSeconds <= 0 || dtSeconds <= 0) {
            return target;
        }
        float alpha = (float) (1.0 - Math.exp(-dtSeconds / responseSeconds));
        float next = current + (target - current) * Math.clamp(alpha, 0f, 1f);
        // Snap when the remaining distance is invisible, so a bar actually reaches
        // full rather than approaching it forever.
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    /**
     * How many segments of a segmented bar are lit.
     *
     * <p>Segmented rather than continuous because a real panel reads in discrete
     * steps: it is far easier to see "seven of ten" at a glance than to judge the
     * length of a smooth bar. Anything above zero lights at least one segment, so a
     * trace amount never looks like nothing at all.
     */
    public static int litSegments(float fraction, int segments) {
        if (fraction <= 0) {
            return 0;
        }
        return Math.clamp(Math.round(fraction * segments), 1, segments);
    }

    /** Where a threshold marker sits along a bar, in pixels from its left edge. */
    public static int markerOffset(double threshold, double fullScale, int barWidth) {
        if (fullScale <= 0) {
            return 0;
        }
        return Math.clamp((int) Math.round(barWidth * threshold / fullScale), 0, barWidth);
    }

    /** How fast a needle would oscillate around its target with no damping at all. */
    public static final float NEEDLE_NATURAL_FREQUENCY_HZ = 2.0f;
    /**
     * Below 1 the needle overshoots before it settles. design/presentation.md §2: "The needle has
     * inertia and overshoots. Not a flourish: the swing *is* a rate-of-change readout... Damping
     * constant is a declared, tested value, not an art choice" — this is that value.
     */
    public static final float NEEDLE_DAMPING_RATIO = 0.35f;

    /** A needle's motion state: where it is drawn, and how fast it is currently swinging. */
    public record NeedleState(float position, float velocity) {}

    /** One physics step of a damped-spring needle toward {@code target}, using the mod's own
     *  declared frequency and damping ratio. */
    public static NeedleState springStep(float position, float velocity, float target, float dtSeconds) {
        return springStep(position, velocity, target, dtSeconds, NEEDLE_NATURAL_FREQUENCY_HZ, NEEDLE_DAMPING_RATIO);
    }

    /**
     * A damped harmonic oscillator driven toward {@code target}: a real needle's mass resists
     * acceleration, and a damping ratio below 1 lets it swing past the target before settling —
     * the same reason a real analog needle overshoots a sudden change on the process it reads.
     * Integrated with semi-implicit ("symplectic") Euler, which stays stable for a springy system
     * where plain Euler would blow up.
     */
    public static NeedleState springStep(float position, float velocity, float target, float dtSeconds,
                                         float naturalFrequencyHz, float dampingRatio) {
        if (dtSeconds <= 0) {
            return new NeedleState(position, velocity);
        }
        float omega = (float) (2.0 * Math.PI * naturalFrequencyHz);
        float acceleration = omega * omega * (target - position) - 2f * dampingRatio * omega * velocity;
        float nextVelocity = velocity + acceleration * dtSeconds;
        float nextPosition = position + nextVelocity * dtSeconds;
        return new NeedleState(nextPosition, nextVelocity);
    }

    private static int clampByte(int value) {
        return Math.clamp(value, 0, 255);
    }

    /** How dark a scanline row is drawn over the glass beneath it. */
    private static final int SCANLINE = 0x50000000;

    /**
     * Darkens every other row of a region, the one texture a real CRT tube has that a flat panel
     * never did — {@code design/presentation.md}, written after a flat gradient body was reported
     * as reading like generic software rather than a screen built into a suit or a machine.
     */
    public static void scanlines(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        for (int row = 1; row < height; row += 2) {
            graphics.fill(x, y + row, x + width, y + row + 1, SCANLINE);
        }
    }

    private HudPaint() {}
}
