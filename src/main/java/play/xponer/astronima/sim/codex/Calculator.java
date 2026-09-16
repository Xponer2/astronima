package play.xponer.astronima.sim.codex;

import java.util.List;
import java.util.Locale;

/**
 * A calculator that lives in a codex page: a few inputs the player can drag, one or more
 * outputs that move with them.
 *
 * <p>Rule 25 in full: this interface — and everything that implements it — is Minecraft-free
 * and decides everything; the screen ({@code CodexUi}) only draws the rows it is handed. Rule
 * 46 is the other half: {@link #compute} calls the real model in {@code sim/}, never a copy of
 * it, so this cannot drift from the machine it is about.
 *
 * <p>See {@code design/codex-calculator-c4a.md} for the leaf plan this implements.
 */
public interface Calculator {
    /** {@code {calc:<id>}}'s id — {@code "ore/comminution"}, not a bare name. */
    String id();

    /** In the order a player drags them, and the order {@link #compute} reads {@code values}. */
    List<Input> inputs();

    /** One value per {@link #inputs()} entry, same order. Clamped by the calculator itself —
     *  a caller does not have to trust its own clamping. */
    List<Output> compute(List<Double> values);

    /**
     * @param id       stable across a page's whole lifetime — what the panel remembers per input
     * @param labelKey translation key, not English text
     * @param unit     shown text, already resolved — not a translation key
     * @param min      inclusive
     * @param max      inclusive
     * @param step     one mouse-wheel notch or drag-track step; UI, not physics (§2.1)
     * @param preset   where the row starts before a player or a remembered session touches it
     */
    record Input(String id, String labelKey, String unit,
                 double min, double max, double step, double preset) { }

    /**
     * @param shown    the value formatted for reading — a decision, so it lives here and not
     *                 in the painter (rule 25); {@code Locale.ROOT}, not the player's locale,
     *                 because this is a precision/unit choice, not a translation
     * @param noteKey  translation key explaining an {@link Verdict#IMPOSSIBLE} output, or
     *                 {@code ""} when there is nothing to say
     */
    record Output(String id, String labelKey, String unit,
                  double value, String shown, Verdict verdict, String noteKey) { }

    /**
     * What the colour channel says. {@code IMPOSSIBLE} is kept apart from {@code BAD} on
     * purpose (parent design §3.2): "this machine cannot reach that at all" is a different
     * claim from "it can, badly", and collapsing them reads as the machine being broken.
     * {@code INFO} carries no judgement — a reading with nothing to grade, like a raw size in
     * microns — and is not colour-coded the way the other three are.
     */
    enum Verdict { GOOD, MARGINAL, BAD, IMPOSSIBLE, INFO }

    /**
     * Reads a player-typed number, comma or point as the decimal separator.
     *
     * <p>{@code ru_ru}'s own keyboard types a comma, and {@code Double.parseDouble("0,5")}
     * throws — the whole reason this exists rather than being inlined at the one call site
     * {@code CodexUi} has for it: a locale bug is worth its own unit test, not a lambda nobody
     * can reach without booting a screen.
     *
     * @return the parsed value, or {@code null} when {@code raw} is not a number at all
     */
    static Double parseTyped(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalised = raw.strip().replace(',', '.');
        try {
            double value = Double.parseDouble(normalised);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** {@code String.format(Locale.ROOT, ...)}, spelled once so every calculator's {@code shown}
     *  strings are built the same way rather than each picking its own format call. */
    static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }

    /**
     * A raw input value, clamped into {@code input}'s own range - and, first, made finite at
     * all. {@code Math.clamp} propagates {@code NaN} rather than resolving it, so an untrusted
     * caller handing {@code compute} a non-finite value (there is no such caller today - the
     * panel's slider and {@link #parseTyped} both already refuse one - but §5's own test plan
     * asks for the variant directly against the calculator) would otherwise carry {@code NaN}
     * through every output rather than being clamped like every other out-of-range value.
     */
    static double clampFinite(double raw, Input input) {
        double value = Double.isFinite(raw) ? raw : input.preset();
        return Math.clamp(value, input.min(), input.max());
    }
}
