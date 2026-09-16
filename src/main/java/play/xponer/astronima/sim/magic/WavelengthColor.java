package play.xponer.astronima.sim.magic;

/**
 * Approximate visible-light colour for a wavelength — the classic 1996 Dan Bruton
 * approximation, the same one most amateur spectroscopy tools use. Not a rigorous colorimetric
 * model (a proper one needs the CIE colour-matching functions and a real working colour space),
 * but close enough that H-α reads red and [O III] reads the teal every real nebula photograph
 * shows it as (design/astra-incognita.md §9.1) — which is the entire point of building this
 * instead of picking colours by eye: the particle a player sees carries the same wavelength the
 * line itself does, not an art-directed guess at it.
 */
public final class WavelengthColor {

    private static final double GAMMA = 0.8;

    /**
     * Packed {@code 0xRRGGBB} for a wavelength in the visible range, roughly 380–780 nm.
     * Outside that range there is nothing visible to show, and this returns black rather than
     * guessing — the same "no signal, said plainly" shape as {@link Spectrum#capture} returning
     * an empty set for a cold target.
     */
    public static int rgb(double wavelengthNm) {
        double r;
        double g;
        double b;
        if (wavelengthNm >= 380 && wavelengthNm < 440) {
            r = -(wavelengthNm - 440) / (440 - 380);
            g = 0.0;
            b = 1.0;
        } else if (wavelengthNm < 490) {
            r = 0.0;
            g = (wavelengthNm - 440) / (490 - 440);
            b = 1.0;
        } else if (wavelengthNm < 510) {
            r = 0.0;
            g = 1.0;
            b = -(wavelengthNm - 510) / (510 - 490);
        } else if (wavelengthNm < 580) {
            r = (wavelengthNm - 510) / (580 - 510);
            g = 1.0;
            b = 0.0;
        } else if (wavelengthNm < 645) {
            r = 1.0;
            g = -(wavelengthNm - 645) / (645 - 580);
            b = 0.0;
        } else if (wavelengthNm <= 780) {
            r = 1.0;
            g = 0.0;
            b = 0.0;
        } else {
            return 0x000000;
        }

        double factor;
        if (wavelengthNm < 380) {
            return 0x000000;
        } else if (wavelengthNm < 420) {
            factor = 0.3 + 0.7 * (wavelengthNm - 380) / (420 - 380);
        } else if (wavelengthNm < 701) {
            factor = 1.0;
        } else {
            factor = 0.3 + 0.7 * (780 - wavelengthNm) / (780 - 700);
        }

        return (channel(r, factor) << 16) | (channel(g, factor) << 8) | channel(b, factor);
    }

    private static int channel(double value, double factor) {
        if (value <= 0.0) {
            return 0;
        }
        return (int) Math.round(255.0 * Math.pow(value * factor, GAMMA));
    }

    private WavelengthColor() {}
}
