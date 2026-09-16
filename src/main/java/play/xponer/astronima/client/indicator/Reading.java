package play.xponer.astronima.client.indicator;

/**
 * One field indicator's worth of state: what to draw, in what colour, and what the
 * number is if the player is close enough to want it.
 *
 * <p>MC-free on purpose. The decision of <em>what a gauge says</em> is the part that can
 * be wrong in a way that matters — a gauge disagreeing with the model is worse than no
 * gauge, because it is trusted — so it lives where a unit test can reach it, and the
 * renderer only draws what it is handed.
 *
 * @param fraction 0..1, how full the bar is
 * @param band     what the colour says about it
 * @param label    the exact figure, shown only at close range
 */
public record Reading(double fraction, Band band, String label) {

    /**
     * What the colour channel says. Deliberately about <em>severity</em> rather than
     * about magnitude: the bar already carries magnitude, and having the two channels
     * say different things is what makes an indicator readable at a glance.
     */
    public enum Band {
        /** Working normally. */
        NOMINAL,
        /** Doing nothing, but not broken — a stalled pump, a shut valve. */
        IDLE,
        /** Past a limit, or about to be. */
        WARNING,
        /** Failed. */
        CRITICAL
    }

    public Reading {
        fraction = Math.clamp(fraction, 0.0, 1.0);
    }

    public int colour() {
        return switch (band) {
            case NOMINAL -> 0xFF4CD964;
            case IDLE -> 0xFF8A93A0;
            case WARNING -> 0xFFD9A24C;
            case CRITICAL -> 0xFFD9534C;
        };
    }
}
