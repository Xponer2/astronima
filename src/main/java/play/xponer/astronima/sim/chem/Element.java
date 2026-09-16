package play.xponer.astronima.sim.chem;

import java.util.Locale;
import java.util.Optional;

/**
 * The periodic table, as far as this mod needs it.
 *
 * <p>Every mineral in {@code sim/ore/Mineral} already carries its real formula as a
 * <em>string</em> — {@code "Fe3O4"}, {@code "Mg3Si2O5(OH)4"} — which is honest documentation and
 * completely opaque to the code. Nothing can ask what a mineral is made of, so nothing can
 * derive what comes out of it; the yields sit beside the formula as separate constants, free to
 * drift from it, and a typo in either is invisible.
 *
 * <p>This is the other half: the same formulas, parsed. Once an element is a value rather than
 * two characters of a string, a mineral's metal content is <em>computed</em> from its
 * composition instead of asserted next to it, and {@link Formula} can prove the two agree.
 *
 * <p><strong>Atomic masses are the IUPAC conventional values</strong>, in g/mol. They are
 * physical constants, not balance figures — nothing here may be tuned, and if a number in this
 * file is wrong it is simply wrong.
 *
 * <p>Minecraft-free (rule 1).
 */
public enum Element {
    // ---- the light ones: the volatiles, the organics, the atmosphere ----------
    HYDROGEN("H", "Hydrogen", 1, 1.008),
    HELIUM("He", "Helium", 2, 4.0026),
    LITHIUM("Li", "Lithium", 3, 6.94),
    BERYLLIUM("Be", "Beryllium", 4, 9.0122),
    BORON("B", "Boron", 5, 10.81),
    CARBON("C", "Carbon", 6, 12.011),
    NITROGEN("N", "Nitrogen", 7, 14.007),
    OXYGEN("O", "Oxygen", 8, 15.999),
    FLUORINE("F", "Fluorine", 9, 18.998),
    NEON("Ne", "Neon", 10, 20.180),

    // ---- the rock-formers: what an asteroid mostly *is* -----------------------
    SODIUM("Na", "Sodium", 11, 22.990),
    MAGNESIUM("Mg", "Magnesium", 12, 24.305),
    ALUMINIUM("Al", "Aluminium", 13, 26.982),
    SILICON("Si", "Silicon", 14, 28.085),
    PHOSPHORUS("P", "Phosphorus", 15, 30.974),
    SULFUR("S", "Sulfur", 16, 32.06),
    CHLORINE("Cl", "Chlorine", 17, 35.45),
    ARGON("Ar", "Argon", 18, 39.95),
    POTASSIUM("K", "Potassium", 19, 39.098),
    CALCIUM("Ca", "Calcium", 20, 40.078),

    // ---- the transition metals: the ore chain --------------------------------
    TITANIUM("Ti", "Titanium", 22, 47.867),
    VANADIUM("V", "Vanadium", 23, 50.942),
    CHROMIUM("Cr", "Chromium", 24, 51.996),
    MANGANESE("Mn", "Manganese", 25, 54.938),
    IRON("Fe", "Iron", 26, 55.845),
    COBALT("Co", "Cobalt", 27, 58.933),
    NICKEL("Ni", "Nickel", 28, 58.693),
    COPPER("Cu", "Copper", 29, 63.546),
    ZINC("Zn", "Zinc", 30, 65.38),
    GALLIUM("Ga", "Gallium", 31, 69.723),
    GERMANIUM("Ge", "Germanium", 32, 72.630),
    ARSENIC("As", "Arsenic", 33, 74.922),
    SELENIUM("Se", "Selenium", 34, 78.971),

    // ---- yttrium and barium exist here for one reason: YBa2Cu3O7 -------------
    YTTRIUM("Y", "Yttrium", 39, 88.906),
    ZIRCONIUM("Zr", "Zirconium", 40, 91.224),
    MOLYBDENUM("Mo", "Molybdenum", 42, 95.95),

    /**
     * The platinum group — genuinely enriched in metallic asteroids, and the real-world
     * economic argument for asteroid mining in the first place.
     */
    RUTHENIUM("Ru", "Ruthenium", 44, 101.07),
    RHODIUM("Rh", "Rhodium", 45, 102.91),
    PALLADIUM("Pd", "Palladium", 46, 106.42),
    SILVER("Ag", "Silver", 47, 107.87),
    CADMIUM("Cd", "Cadmium", 48, 112.41),
    INDIUM("In", "Indium", 49, 114.82),
    TIN("Sn", "Tin", 50, 118.71),
    ANTIMONY("Sb", "Antimony", 51, 121.76),
    TELLURIUM("Te", "Tellurium", 52, 127.60),
    BARIUM("Ba", "Barium", 56, 137.33),
    TUNGSTEN("W", "Tungsten", 74, 183.84),
    OSMIUM("Os", "Osmium", 76, 190.23),
    IRIDIUM("Ir", "Iridium", 77, 192.22),
    PLATINUM("Pt", "Platinum", 78, 195.08),
    GOLD("Au", "Gold", 79, 196.97),
    MERCURY("Hg", "Mercury", 80, 200.59),
    LEAD("Pb", "Lead", 82, 207.2),
    BISMUTH("Bi", "Bismuth", 83, 208.98),
    THORIUM("Th", "Thorium", 90, 232.04),
    URANIUM("U", "Uranium", 92, 238.03);

    private final String symbol;
    private final String displayName;
    private final int atomicNumber;
    private final double atomicMass;

    Element(String symbol, String displayName, int atomicNumber, double atomicMass) {
        this.symbol = symbol;
        this.displayName = displayName;
        this.atomicNumber = atomicNumber;
        this.atomicMass = atomicMass;
    }

    /** The chemical symbol, exactly as a formula spells it: {@code Fe}, {@code Cl}. */
    public String symbol() {
        return symbol;
    }

    public String displayName() {
        return displayName;
    }

    /** Protons. The one number that actually says which element this is. */
    public int atomicNumber() {
        return atomicNumber;
    }

    /** Standard atomic weight, g/mol. */
    public double atomicMass() {
        return atomicMass;
    }

    /**
     * The element with this symbol, if there is one.
     *
     * <p>Case-sensitive on purpose: {@code CO} is carbon monoxide and {@code Co} is cobalt, and
     * a lookup that shrugged at the difference would silently turn one into the other.
     */
    public static Optional<Element> bySymbol(String symbol) {
        for (Element element : values()) {
            if (element.symbol.equals(symbol)) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    /** A stable, lower-case id — for tags, JEI pages and anything else that needs a key. */
    public String id() {
        return displayName.toLowerCase(Locale.ROOT);
    }
}
