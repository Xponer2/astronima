package play.xponer.astronima.sim.chem;

import play.xponer.astronima.crafting.CraftingTree;
import play.xponer.astronima.sim.metal.ColdWorking;
import play.xponer.astronima.sim.ore.Mineral;
import play.xponer.astronima.sim.ore.OreBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a stack actually is, chemically — the roster {@code ChemistryTooltip} reads to put real
 * composition on the item rather than leaving it in the simulation.
 *
 * <p>Four honest shapes, matching what real matter actually is (see {@code
 * design/chemistry-tooltip.md}): a pure substance with one formula ({@link #substance}), a
 * mineral assemblage with several formulas at once ({@link #mixture}, derived from
 * {@link OreBody} the same way the ore chain itself already is — never a second hand-typed
 * number), a real alloy at its representative ratio ({@link #alloy}, off the same
 * {@code sim/metal/ColdWorking} constants the forge itself runs on), or matter with no fixed
 * formula at all ({@link #note}), which gets a translated label instead of an invented ratio.
 *
 * <p><strong>Every tool, machine and fixture derives from what it is actually made of</strong>
 * ({@link #deriveAssemblies()}) — item-count-weighted real ingredient chemistry, read straight
 * off {@code CraftingTree}'s own real recipes, rather than left with no entry. A wrench built
 * from an iron rod and an iron ingot shows real iron; a machine built half from a real alloy and
 * half from something with no fixed formula (tholins, a sealed isotope capsule) honestly reports
 * that it has no fixed formula either, rather than silently pretending the other half of it does
 * not exist. This is a named simplification (rule 8): every real component is weighted equally
 * by item count, not by its own real mass, because this project has no per-component gram figure
 * for most crafting ingredients (a control board and an iron rod are not the same real mass) —
 * an even average is the honest default rather than an invented one.
 *
 * <p>Reaction products — a Downs cell's sodium, a retort's magnesium oxide, a smelter's slag —
 * are hand-written rather than derived from their feedstock: {@code CraftingTree.Transformation}
 * names a real machine <em>process</em> for reachability, not "made of the same substance as,"
 * and deriving a genuine chemical reaction's product from its feedstock's own formula would
 * simply be wrong (magnesium oxide is not baked silicate). Refills and state changes — a topped-up
 * tank, a snuffed torch — are the one Transformation shape safely copied whole, since nothing
 * about the physical object actually changed.
 *
 * <p>Minecraft-free (rule 1): {@code CraftingTree}'s own ids are plain strings by the same
 * design, so deriving from it costs nothing.
 */
public final class Chemistry {

    /** One item's chemistry: a composition (possibly empty), an organic residue share, or a note. */
    public record Entry(String formulaText, Map<Element, Double> elementMassFractions,
                        double organicResidueFraction, String noteKey) {

        static Entry substance(String formulaText) {
            Formula formula = Formula.parse(formulaText);
            Map<Element, Double> fractions = new EnumMap<>(Element.class);
            for (Element element : formula.composition().keySet()) {
                fractions.put(element, formula.massFraction(element));
            }
            return new Entry(formulaText, fractions, 0.0, null);
        }

        /**
         * Public because {@code CrushedOreItem} needs this same derivation for a batch's own
         * {@code OreGrade} — computed per stack rather than looked up statically, since which
         * grade a batch is is a data component, not a fixed fact of the item.
         */
        public static Entry mixture(OreBody body) {
            Map<Element, Double> fractions = new EnumMap<>(Element.class);
            double organic = 0.0;
            for (Mineral mineral : body.masses().keySet()) {
                double share = body.fractionOf(mineral);
                Optional<Formula> formula = Formula.tryParse(mineral.formula());
                if (formula.isEmpty()) {
                    organic += share;
                    continue;
                }
                for (Element element : formula.get().composition().keySet()) {
                    fractions.merge(element, share * formula.get().massFraction(element), Double::sum);
                }
            }
            return new Entry(null, fractions, organic, null);
        }

        /** A binary Fe/Ni alloy at a given nickel mass fraction — {@link #alloy}'s own shape. */
        static Entry alloy(double nickelFraction) {
            Map<Element, Double> fractions = new EnumMap<>(Element.class);
            fractions.put(Element.NICKEL, nickelFraction);
            fractions.put(Element.IRON, 1.0 - nickelFraction);
            return new Entry(null, fractions, 0.0, null);
        }

        static Entry note(String key) {
            return new Entry(null, Map.of(), 0.0, key);
        }

        /** True for an entry with no real numeric composition of any kind — the state an
         *  assembly built partly or wholly from note-only ingredients has to fall back to,
         *  rather than silently under-counting the ingredient it cannot express. */
        boolean hasNoRealData() {
            return elementMassFractions.isEmpty() && organicResidueFraction <= 0.0;
        }

        /** Grams per mole — only meaningful for a single-formula substance. */
        public double molarMass() {
            return formulaText == null ? 0.0 : Formula.parse(formulaText).molarMass();
        }

        public boolean isNoteOnly() {
            return noteKey != null;
        }
    }

    /** An organic or evaporite mixture: real matter, several species, no single formula. */
    public static final String NOTE_MIXTURE = "astronima.chem.mixture";

    /**
     * Not real chemistry at all — the field's own energy, not a substance (see
     * {@code feedback-magic-can-invent-physics}). Naming this honestly on the item itself is
     * more informative than either inventing a formula for it or leaving it with no entry.
     */
    public static final String NOTE_MAGIC = "astronima.chem.magic";

    private static final Map<String, Entry> ROSTER = new LinkedHashMap<>();

    /** Bare ids stay short to type; a full {@code namespace:path} (a vanilla item reachable from
     *  this mod's own tree) passes through unchanged. */
    private static String qualify(String id) {
        return id.contains(":") ? id : "astronima:" + id;
    }

    private static void substance(String id, String formulaText) {
        ROSTER.put(qualify(id), Entry.substance(formulaText));
    }

    private static void mixture(String id, OreBody body) {
        ROSTER.put(qualify(id), Entry.mixture(body));
    }

    /** A real alloy at its representative (chondritic-feedstock) nickel fraction — real per-stack
     *  values run higher off a seam or refined feed (design/forge lore, {@code ColdWorking}'s own
     *  {@code SEAM_NICKEL}), which a single static roster entry cannot reflect; this is the common
     *  case, named as such rather than invented. */
    private static void alloy(String id, double nickelFraction) {
        ROSTER.put(qualify(id), Entry.alloy(nickelFraction));
    }

    private static void note(String id, String key) {
        ROSTER.put(qualify(id), Entry.note(key));
    }

    static {
        // Pure substances — one formula, straight off the electrolysis/carbonyl/thermal/halogen
        // chains.
        substance("silicon", "Si");
        substance("silicon_electrode", "Si");
        // Zone refining changes purity, not elemental identity (design/halogens.md §9-10 Part B)
        // - this model has no purity axis to move, the same honest limit alloys' single
        // representative ratio already carries.
        substance("wafer_silicon", "Si");
        substance("aluminum", "Al");
        substance("aluminum_electrode", "Al");
        substance("pure_nickel", "Ni");
        substance("iron_powder", "Fe");
        substance("carbon_powder", "C");
        substance("sintered_frame", "Fe");
        substance("titania", "TiO2");
        substance("chlorate_powder", "NaClO3");
        substance("chlorate_ore", "NaClO3");
        substance("hematite_ore", "Fe2O3");
        substance("baked_silicate", "Mg3Si2O7");
        substance("ilmenite_ore", Mineral.ILMENITE.formula());
        substance("crushed_ilmenite", Mineral.ILMENITE.formula());
        // Sodium chlorate's own real halogen residue once it gives its oxygen up (rule: see the
        // retort's own ChlorateDecomposition doc) — common salt, exactly.
        substance("mineral_salts", "NaCl");
        // Subsurface ice — real, ordinary water ice; the asteroid's water, oxygen and hydrogen.
        substance("water_ice", "H2O");
        // Rock salt: near-pure NaCl (design/halogens.md §2) — the same real compound
        // mineral_salts is, by real coincidence of two different processes both ending in salt.
        substance("halite_ore", "NaCl");
        // The Downs process's own product (design/halogens.md §1) — real, elemental sodium.
        substance("sodium", "Na");
        // Real fluorite, the HF digester's own feedstock (design/halogens.md §15-16 Part C1).
        substance("fluorite_ore", "CaF2");
        // The digester's own real products — a genuine chemical reaction, hand-written the same
        // way every other reaction product on this roster already is (class doc).
        substance("hydrofluoric_acid", "HF");
        substance("gypsum", "CaSO4");
        // The etch station's own real reaction product (design/halogens.md §40/§45 Part C4) —
        // real hexafluorosilicic acid, a genuine new compound.
        substance("fluorosilicic_acid", "H2SiF6");
        // Etching changes shape/pattern, not elemental identity (design/halogens.md §40) - the
        // same honest "no purity/pattern axis to move" limit wafer_silicon's own entry above
        // already carries for zone refining.
        substance("etched_die", "Si");
        // FFC-Cambridge's own product (design/titanium-reduction.md) — real, elemental titanium.
        substance("titanium", "Ti");
        // The retort's second, hotter calcination arm (design/carbonate-calcination.md) — real
        // periclase, MgO, not baked silicate: a genuine reaction product, not the same substance.
        substance("magnesium_oxide", "MgO");
        // The iron smelter's own real flux reaction product (design/iron-smelter.md) —
        // MgO + SiO2 -> MgSiO3, real magnesium silicate, not the hematite it was freed from.
        substance("slag", "MgSiO3");
        // The Contact Process's own real product (design/chemistry-loop.md §2.7) —
        // 2 SO2 + O2 + 2 H2O -> 2 H2SO4.
        substance("sulfuric_acid", "H2SO4");
        // Real, ordinary silica — freeze-drying only removes the pore water, it does not change
        // the compound (design/cryogenics.md §5).
        substance("silica_aerogel", "SiO2");
        // Addition polymerization's own real repeat unit (design/petrochemicals.md) — the polymer
        // itself is (C2H4)n with no fixed molecule count, so the monomer is the honest formula to
        // give a mass fraction from; titania is the catalyst that makes this, not this item's own
        // material (Transformation's "from" names the machine, not the substance — see class doc).
        substance("polyethylene", "C2H4");
        // Salvaged steel, simplified to its dominant element the same way iron_powder/
        // sintered_frame already are (real steel is >95% iron by mass either way).
        substance("pry_bar", "Fe");
        // A real smelting reaction, not "made of the same substance as" — CraftingTree declares
        // this Cooking from *both* hematite_ore (Fe2O3, 69.9% Fe) and iron_powder (already pure
        // Fe); deriving from whichever one the loop reaches first would make an ingot smelted
        // from ore read as one-third oxygen. Real smelted iron is the metal, oxygen driven off,
        // whichever way it got there - the same reasoning every other reaction product on this
        // roster (magnesium_oxide, slag, sulfuric_acid...) is hand-written rather than derived.
        substance("minecraft:iron_ingot", "Fe");

        // Real alloys, at their representative nickel fraction (sim/metal/ColdWorking) — the
        // whole reason the cold forge exists as a tier: what is pressed decides the ratio.
        alloy("iron_nickel_grains", ColdWorking.BASE_NICKEL);
        alloy("metal_billet", ColdWorking.BASE_NICKEL);
        alloy("tool_head", ColdWorking.BASE_NICKEL);

        // Mineral assemblages — several formulas at once, aggregated from the same OreBody the
        // ore chain itself computes yields from.
        mixture("asteroid_rock", OreBody.chondrite(1.0));
        mixture("regolith", OreBody.chondrite(1.0));
        mixture("metal_rich_ore", OreBody.metalRich(1.0));

        // Real matter, no fixed formula — genuinely variable (crushed ore/tailings carry
        // whichever OreGrade was fed in, a data component no static roster entry can reflect;
        // CrushedOreItem's own per-stack Entry.mixture(OreGrade...) is the real answer for a
        // held stack) or genuinely undefined even in the real world (tholins have no fixed
        // formula; nor does an arbitrary paraffin-range alkane blend).
        note("crushed_ore", NOTE_MIXTURE);
        note("tailings", NOTE_MIXTURE);
        note("sludge", NOTE_MIXTURE);
        note("tholin_clump", NOTE_MIXTURE);
        // A mixture of medium-length alkanes, not a polymer - no fixed formula, and rendered
        // from sludge (itself already NOTE_MIXTURE) rather than something new entering the game.
        note("paraffin_wax", NOTE_MIXTURE);
        // A sealed capsule around a real but deliberately unspecified isotope
        // (design/radiation.md §2 — "a single reference isotope's dose behaviour, not a specific
        // real one"); naming an element here would claim a precision the design itself refuses.
        note("rtg_core", NOTE_MIXTURE);

        // The magic tier invents its own physics on purpose (feedback-magic-can-invent-physics)
        // — named as such rather than given a fabricated formula or silently left with no entry.
        note("asterium_grains", NOTE_MAGIC);
        note("asterium_block", NOTE_MAGIC);

        deriveAssemblies();

        // Refills and a state change - the one Transformation shape safe to copy whole, because
        // the physical object genuinely has not changed (class doc). Run after deriveAssemblies
        // so each "empty"/base form is already resolved.
        alias("ammonia_canister", "ammonia_canister_empty");
        alias("oxygen_tank", "oxygen_tank_empty");
        alias("unlit_torch", "minecraft:torch");

        // A second pass: anything waiting on one of the three aliases just above (a heat pipe
        // needs a full canister) can only resolve now that they exist.
        deriveAssemblies();
    }

    /** Copies an already-resolved entry under a second id — the refill/state-change exception
     *  {@link #deriveAssemblies()}'s own doc names. */
    private static void alias(String id, String sameAs) {
        Entry entry = ROSTER.get(qualify(sameAs));
        if (entry != null) {
            ROSTER.put(qualify(id), entry);
        }
    }

    /**
     * Everything {@code CraftingTree} can actually build, derived from what its own recipe says
     * it is made of — see the class doc for why {@code Transformation} is excluded and why a
     * note-only ingredient makes the whole assembly note-only rather than silently incomplete.
     *
     * <p>A fixed-point pass rather than plain recursion: an item can need another item that is
     * itself still undetermined (a wrench needs a rod, a rod needs a billet), and the order
     * {@code CraftingTree.sources()} lists them in is not a dependency order. Repeating the pass
     * until nothing new resolves reaches every level the real recipe graph actually has, however
     * deep, without this class needing to know that depth in advance.
     */
    private static void deriveAssemblies() {
        List<CraftingTree.Source> sources = CraftingTree.sources();
        boolean progress = true;
        while (progress) {
            progress = false;
            for (CraftingTree.Source source : sources) {
                String result;
                Map<String, Integer> ingredientCounts;
                switch (source) {
                    case CraftingTree.Shaped shaped -> {
                        result = shaped.result();
                        ingredientCounts = countsOf(shaped);
                    }
                    case CraftingTree.Shapeless shapeless -> {
                        result = shapeless.result();
                        ingredientCounts = shapeless.inputs();
                    }
                    case CraftingTree.Cooking cooking -> {
                        result = cooking.result();
                        ingredientCounts = Map.of(cooking.input(), 1);
                    }
                    // A machine process, not "made of" its feedstock (class doc) — never derived.
                    case CraftingTree.Transformation ignored -> {
                        continue;
                    }
                    case CraftingTree.WorldSource ignored -> {
                        continue;
                    }
                }
                String resultId = qualify(result);
                if (ROSTER.containsKey(resultId)) {
                    continue;
                }
                Entry assembled = assemble(ingredientCounts);
                if (assembled == null) {
                    continue; // an ingredient is not resolved yet - try again next pass
                }
                ROSTER.put(resultId, assembled);
                progress = true;
            }
        }
    }

    /** How many of each ingredient a shaped pattern actually calls for — every occurrence of its
     *  key character across every row, blanks excluded. */
    private static Map<String, Integer> countsOf(CraftingTree.Shaped shaped) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String row : shaped.pattern()) {
            for (char symbol : row.toCharArray()) {
                String id = shaped.keys().get(symbol);
                if (id != null) {
                    counts.merge(id, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    /**
     * Item-count-weighted average of whatever the ingredients actually are — {@code null} if any
     * of them has no roster entry yet (this pass has to wait for a later one), and note-only the
     * moment any of them is (the class doc's own "honest, not silently incomplete" rule).
     */
    private static Entry assemble(Map<String, Integer> ingredientCounts) {
        Map<Element, Double> fractions = new EnumMap<>(Element.class);
        double organic = 0.0;
        int total = 0;
        boolean anyMagic = false;
        boolean anyNoteOnly = false;

        for (Map.Entry<String, Integer> ingredient : ingredientCounts.entrySet()) {
            Entry entry = ROSTER.get(qualify(ingredient.getKey()));
            if (entry == null) {
                return null;
            }
            int count = ingredient.getValue();
            total += count;
            if (entry.hasNoRealData()) {
                anyNoteOnly = true;
                anyMagic |= NOTE_MAGIC.equals(entry.noteKey());
                continue;
            }
            for (Map.Entry<Element, Double> fraction : entry.elementMassFractions().entrySet()) {
                fractions.merge(fraction.getKey(), fraction.getValue() * count, Double::sum);
            }
            organic += entry.organicResidueFraction() * count;
        }
        if (total == 0) {
            return Entry.note(NOTE_MIXTURE);
        }
        if (anyNoteOnly) {
            return Entry.note(anyMagic ? NOTE_MAGIC : NOTE_MIXTURE);
        }
        for (Element element : new ArrayList<>(fractions.keySet())) {
            fractions.put(element, fractions.get(element) / total);
        }
        return new Entry(null, fractions, organic / total, null);
    }

    /** This item's chemistry, if it has one. {@code id} is the full {@code "namespace:path"}. */
    public static Optional<Entry> of(String id) {
        return Optional.ofNullable(ROSTER.get(id));
    }

    /** Everything on the roster, for tests to walk. */
    public static Map<String, Entry> roster() {
        return Collections.unmodifiableMap(ROSTER);
    }

    private Chemistry() {}
}
