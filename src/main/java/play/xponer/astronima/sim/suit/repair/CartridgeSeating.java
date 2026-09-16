package play.xponer.astronima.sim.suit.repair;

import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Scrubber bay — <strong>one committed push</strong>.
 *
 * <p>Sliding a cartridge into its bay is a single motion against rising resistance:
 * the seal lips bite harder the further in it goes, and there is a detent where it
 * seats. Push past the detent and you crush the gasket; stop short and it backs out
 * on you the moment the suit pressurises.
 *
 * <p>The verb is therefore <em>commitment</em>, and it is the only job in the suit
 * that is over in one movement. There is no rhythm to learn and no sequence to
 * follow — you either feel the detent and release, or you do not. It is also the most
 * forgiving, deliberately: this is the repair a player will be doing in a hurry, in
 * the dark, with something else going wrong.
 */
public final class CartridgeSeating implements RepairTask {
    /** Depth at which the cartridge seats. */
    public static final double SEAT_DEPTH = 0.8;

    /** How far past the detent the gasket survives. */
    public static final double CRUSH_DEPTH = 0.95;

    /** Depth below which releasing simply lets it slide back out. */
    public static final double GRIP_DEPTH = 0.6;

    /** Push speed at zero depth, depth-fraction per second. */
    private static final double BASE_SPEED = 0.5;

    private double depth;
    private boolean pushing;
    private boolean seated;
    private int failedAttempts;
    private double seatError = 1.0;

    @Override
    public SuitSubsystem subsystem() {
        return SuitSubsystem.SCRUBBER_BAY;
    }

    public void beginPush() {
        if (!seated) {
            pushing = true;
        }
    }

    @Override
    public void tick(double dtSeconds) {
        if (!pushing || seated) {
            return;
        }
        depth += pushSpeed() * dtSeconds;
        if (depth >= 1.0) {
            // Bottomed out: the gasket is crushed and has to be re-seated.
            depth = 0;
            pushing = false;
            failedAttempts++;
        }
    }

    /**
     * Resistance rises with depth, so the cartridge slows as it approaches the seat.
     * That is the tactile cue the whole interaction is built on — without it, the
     * detent would be pure reaction time rather than something you can feel coming.
     */
    public double pushSpeed() {
        return BASE_SPEED * (1.0 - 0.7 * Math.clamp(depth, 0.0, 1.0));
    }

    /** Releasing commits wherever the cartridge has reached. */
    public void release() {
        if (!pushing || seated) {
            return;
        }
        pushing = false;
        if (depth >= SEAT_DEPTH && depth <= CRUSH_DEPTH) {
            seated = true;
            seatError = Math.abs(depth - SEAT_DEPTH) / (CRUSH_DEPTH - SEAT_DEPTH);
        } else if (depth > CRUSH_DEPTH) {
            depth = 0;
            failedAttempts++;
        } else if (depth >= GRIP_DEPTH) {
            // Far enough in that the lips hold it: you can pick the push back up
            // from here rather than starting the motion again.
            failedAttempts++;
        } else {
            // Short of the grip: it slides back out under its own spring.
            depth = 0;
            failedAttempts++;
        }
    }

    public double depth() {
        return depth;
    }

    public boolean isPushing() {
        return pushing;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    @Override
    public boolean isComplete() {
        return seated;
    }

    @Override
    public float progress() {
        return seated ? 1f : (float) Math.clamp(depth / SEAT_DEPTH, 0.0, 0.99);
    }

    /**
     * Seating right on the detent gives a full-life gasket; scraping in at the edge
     * of the window works but has already taken a set.
     */
    @Override
    public float quality() {
        if (!seated) {
            return 0.4f;
        }
        float placement = (float) (1.0 - seatError * 0.3);
        return Math.max(0.4f, placement - failedAttempts * 0.1f);
    }

    @Override
    public String hintKey() {
        return seated ? "done" : pushing ? "release_at_detent" : "push_home";
    }
}
