package play.xponer.astronima.client.render;

import play.xponer.astronima.sim.pipe.Valve;

/**
 * Where a moving part is, this tick.
 *
 * <p>Deliberately free of any Minecraft or rendering types, in the same spirit as
 * {@code client/hud/HudScale}: the <em>behaviour</em> of a moving part is separated from
 * the drawing of it so the behaviour can be unit-tested. Nothing in the build renders a
 * frame, so a renderer's arithmetic is otherwise verified by looking at it — and this
 * project has already shipped indicators that registered, compiled, and were never once
 * seen to draw.
 *
 * <p>A <strong>handwheel</strong> is a <em>position</em>: it travels to where the player
 * set it and stops there. (A pump impeller was tried too, but it lived inside the housing
 * where nothing could see it turn — an animation with no viewer is not an animation, so it
 * was removed rather than kept as decoration.)
 */
public final class MovingParts {

    // ------------------------------------------------------------------ handwheel

    /**
     * How far the wheel turns per setting.
     *
     * <p>Total travel is {@code (SETTINGS-1) * 45 = 180°}, and the ceiling matters more
     * than the value: <strong>at 360° a shut valve and a fully open one are the same
     * picture.</strong> That is precisely the rule-9 defect this mod already shipped once,
     * when five settings shared one model. Asserted in the tests rather than left to
     * whoever next decides a bigger sweep would read better.
     */
    public static final double DEGREES_PER_SETTING = 45.0;

    /** Travel of a handwheel per tick — a step takes about 0.4 s, the pace of a hand. */
    public static final double WHEEL_DEGREES_PER_TICK = 6.0;

    /** Where the wheel belongs for a setting. Shut is 0°. */
    public static double wheelTarget(int setting) {
        int clamped = Math.clamp(setting, 0, Valve.SETTINGS - 1);
        return clamped * DEGREES_PER_SETTING;
    }

    /** Total sweep from shut to full open. Must stay below 360 — see above. */
    public static double wheelTotalTravel() {
        return wheelTarget(Valve.SETTINGS - 1) - wheelTarget(0);
    }

    /**
     * Moves a wheel one tick toward where it belongs.
     *
     * <p>A fixed number of degrees per tick rather than a fraction of the distance
     * remaining. The fractional form is the obvious one to write and it never
     * arrives — it approaches the target asymptotically and the wheel creeps forever,
     * which is invisible in a screenshot and obvious after ten seconds of watching.
     *
     * <p>Rate is per <em>tick</em>, not per frame, so the wheel turns at the same speed
     * on every machine. A frame-rate-dependent easing is the kind of fault never noticed
     * by whoever wrote it on one computer.
     */
    public static double stepWheel(double current, double target, double degreesPerTick) {
        double remaining = target - current;
        if (Math.abs(remaining) <= degreesPerTick) {
            return target; // arrive exactly, so it stops rather than hunting around it
        }
        return current + Math.copySign(degreesPerTick, remaining);
    }

    public static double stepWheel(double current, double target) {
        return stepWheel(current, target, WHEEL_DEGREES_PER_TICK);
    }

    private MovingParts() {}
}
