package play.xponer.astronima.sim.chem;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * A chemical formula, parsed — so the code can ask what something is made of.
 *
 * <p>{@code sim/ore/Mineral} has carried real formulas as strings since the ores were written,
 * and beside each one a hand-typed {@code metalMassFraction}. Those two are the same fact stated
 * twice: magnetite is {@code Fe3O4}, and 72 % of it is iron <em>because</em> it is Fe₃O₄. Typed
 * separately, they can disagree, and nothing in the build would ever notice — which is exactly
 * the shape of failure rule 13 exists for.
 *
 * <p>Parsing the formula makes the second number derivable from the first, so
 * {@code FormulaTest} can hold every mineral to its own chemistry.
 *
 * <h2>What it understands</h2>
 * <table>
 *   <tr><td>{@code Fe3O4}</td><td>counts</td></tr>
 *   <tr><td>{@code Mg3Si2O5(OH)4}</td><td>groups, nested</td></tr>
 *   <tr><td>{@code (Fe,Ni)9S8}</td><td><strong>solid solutions</strong> — a crystal site shared
 *       between elements, which is ordinary mineral notation and not decoration</td></tr>
 *   <tr><td>{@code Fe0.95O}</td><td>non-stoichiometric compounds, which are real</td></tr>
 * </table>
 *
 * <p><strong>The one assumption, named:</strong> a solid-solution site is split
 * <em>equally</em> between its members, because the notation {@code (Mg,Fe)2SiO4} genuinely does
 * not say what the ratio is — real olivine runs anywhere from pure forsterite to pure fayalite.
 * Where a mineral's actual ratio matters it must state it rather than let this guess (see
 * {@link #isSolidSolution()}, which is how a caller finds out it is being guessed at).
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Formula {

    private final String text;
    private final Map<Element, Double> atoms;
    private final boolean solidSolution;

    private Formula(String text, Map<Element, Double> atoms, boolean solidSolution) {
        this.text = text;
        this.atoms = Collections.unmodifiableMap(atoms);
        this.solidSolution = solidSolution;
    }

    /**
     * Parses a formula, or throws if it is not one.
     *
     * <p>Throws rather than returning empty, because a formula that fails to parse in the middle
     * of the ore chain is a typo in a constant, not a runtime condition to handle. Use
     * {@link #tryParse} where "this might not be a formula at all" is a legitimate answer — the
     * mod has minerals whose composition is genuinely a mixture with no formula, such as kerogen.
     */
    public static Formula parse(String text) {
        Parser parser = new Parser(text);
        Map<Element, Double> atoms = parser.sequence(false);
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("empty formula: '" + text + "'");
        }
        return new Formula(text, atoms, parser.sawSolidSolution);
    }

    /** The formula, or empty when the text is not one — an organic mixture, a trade name. */
    public static Optional<Formula> tryParse(String text) {
        try {
            return Optional.of(parse(text));
        } catch (IllegalArgumentException notAFormula) {
            return Optional.empty();
        }
    }

    /** Moles of this element per formula unit; fractional where a site is shared. */
    public double atoms(Element element) {
        return atoms.getOrDefault(element, 0.0);
    }

    /** Everything in it, and how much. */
    public Map<Element, Double> composition() {
        return atoms;
    }

    /** Grams per mole of formula unit. */
    public double molarMass() {
        double total = 0;
        for (Map.Entry<Element, Double> entry : atoms.entrySet()) {
            total += entry.getKey().atomicMass() * entry.getValue();
        }
        return total;
    }

    /**
     * What share of this substance's <em>mass</em> is that element, 0..1.
     *
     * <p>The number an ore chain actually cares about: a tonne of magnetite is 0.72 tonnes of
     * iron, whatever the mole counts say.
     */
    public double massFraction(Element element) {
        double mass = molarMass();
        return mass <= 0 ? 0 : element.atomicMass() * atoms(element) / mass;
    }

    /** True when a site is shared between elements, so the split above is an assumption. */
    public boolean isSolidSolution() {
        return solidSolution;
    }

    /** The formula as written. */
    @Override
    public String toString() {
        return text;
    }

    // ---- the parser -----------------------------------------------------------

    /**
     * Recursive descent, because the grammar is genuinely recursive: {@code (OH)4} nests, and
     * pretending otherwise with a regular expression is how {@code Mg3Si2O5(OH)4} silently comes
     * out with four hydrogens and no oxygens.
     */
    private static final class Parser {
        private final String text;
        private int at;
        private boolean sawSolidSolution;

        Parser(String text) {
            this.text = text == null ? "" : text.trim();
        }

        Map<Element, Double> sequence(boolean insideBrackets) {
            Map<Element, Double> found = new EnumMap<>(Element.class);
            while (at < text.length()) {
                char c = text.charAt(at);
                if (c == ')') {
                    if (!insideBrackets) {
                        throw fail("unmatched ')'");
                    }
                    break;
                }
                if (c == '(') {
                    at++;
                    Map<Element, Double> inner = solutionAhead() ? solutionSite() : sequence(true);
                    expect(')');
                    mergeScaled(found, inner, count());
                } else if (Character.isUpperCase(c)) {
                    Element element = element();
                    found.merge(element, count(), Double::sum);
                } else {
                    throw fail("unexpected '" + c + "'");
                }
            }
            return found;
        }

        /**
         * Distinguishes {@code (Fe,Ni)} from {@code (OH)} by looking for a comma at this
         * bracket's own depth — the only thing that tells a shared site from a group.
         */
        private boolean solutionAhead() {
            int depth = 0;
            for (int i = at; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    if (depth == 0) {
                        return false;
                    }
                    depth--;
                } else if (c == ',' && depth == 0) {
                    return true;
                }
            }
            throw fail("unclosed '('");
        }

        /** {@code (Mg,Fe)} — one site, split equally, which is the assumption named above. */
        private Map<Element, Double> solutionSite() {
            sawSolidSolution = true;
            Map<Element, Double> members = new EnumMap<>(Element.class);
            int count = 0;
            while (true) {
                members.merge(element(), 1.0, Double::sum);
                count++;
                if (at < text.length() && text.charAt(at) == ',') {
                    at++;
                    continue;
                }
                break;
            }
            Map<Element, Double> site = new EnumMap<>(Element.class);
            for (Map.Entry<Element, Double> entry : members.entrySet()) {
                site.put(entry.getKey(), entry.getValue() / count);
            }
            return site;
        }

        /**
         * One symbol. Tries two characters before one, so {@code Co} reads as cobalt while
         * {@code CO} reads as carbon and oxygen.
         */
        private Element element() {
            if (at >= text.length() || !Character.isUpperCase(text.charAt(at))) {
                throw fail("expected an element symbol");
            }
            if (at + 1 < text.length() && Character.isLowerCase(text.charAt(at + 1))) {
                Optional<Element> twoLetter = Element.bySymbol(text.substring(at, at + 2));
                if (twoLetter.isPresent()) {
                    at += 2;
                    return twoLetter.get();
                }
            }
            String symbol = text.substring(at, at + 1);
            return Element.bySymbol(symbol).map(element -> {
                at += 1;
                return element;
            }).orElseThrow(() -> fail("unknown element '" + symbol + "'"));
        }

        /** A subscript, defaulting to one. Decimal because non-stoichiometry is real. */
        private double count() {
            int start = at;
            while (at < text.length()
                    && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.')) {
                at++;
            }
            if (start == at) {
                return 1.0;
            }
            try {
                return Double.parseDouble(text.substring(start, at));
            } catch (NumberFormatException malformed) {
                throw fail("bad subscript '" + text.substring(start, at) + "'");
            }
        }

        private void expect(char c) {
            if (at >= text.length() || text.charAt(at) != c) {
                throw fail("expected '" + c + "'");
            }
            at++;
        }

        private static void mergeScaled(Map<Element, Double> into, Map<Element, Double> from,
                                        double scale) {
            for (Map.Entry<Element, Double> entry : from.entrySet()) {
                into.merge(entry.getKey(), entry.getValue() * scale, Double::sum);
            }
        }

        private IllegalArgumentException fail(String why) {
            return new IllegalArgumentException(why + " in formula '" + text + "' at " + at);
        }
    }
}
