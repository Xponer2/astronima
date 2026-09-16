package play.xponer.astronima.crafting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which rung of the ladder every thing in the mod sits on.
 *
 * <h2>Why this is data and not prose</h2>
 * The tiers were a shape in {@code PLAN.md} and nowhere else, which meant the only way to answer
 * <em>"what does a player have when they reach the laboratory?"</em> was to read a roadmap written
 * in terms of versions and hope it still described the game. A roster that lives beside the
 * recipes cannot drift from them, and two things fall straight out of it:
 *
 * <ul>
 *   <li><strong>Nothing is gated behind something that comes later.</strong> A recipe on rung two
 *       that quietly needs a rung-four ingredient is a progression bug that no amount of playing
 *       finds quickly — you just cannot build the thing, and the reason is three crafts away.
 *       {@code TierTest} walks every recipe and refuses it.</li>
 *   <li><strong>The roster is complete.</strong> Anything the crafting tree can produce and this
 *       file does not place is unplaced, and an unplaced thing has no position in the progression
 *       at all.</li>
 * </ul>
 *
 * <p>Deliberately <em>declared</em> rather than derived from crafting depth. Depth would say the
 * pry bar and the asteroid rock are the same rung as the wreck's suit, which is arithmetic rather
 * than design: a tier is a statement about what the player is <em>doing</em>, and the only place
 * that can come from is a decision.
 */
public final class Tiers {

    /**
     * The rungs, in order. The ordinal is the gate: nothing may need something above it.
     *
     * <p><strong>These are not the rungs the first draft had.</strong> That one put survival
     * before ore, because that is the order the design document talks in — air first, then rock.
     * The guard below rejected it immediately and it was right: <em>a habitat is made of metal.</em>
     * The hull plate, the bulkhead door, the gas pipe and the airlock are all downstream of the
     * crusher, so a player does not survive and then mine, they mine in a suit and then build
     * somewhere to take it off. Six inversions, and every one of them was the roster being wrong
     * about the game rather than the game being wrong.
     */
    public enum Tier {
        /** The wreck, the rock, and what breaks off in your hands. Nothing here is manufactured. */
        SALVAGE("Salvage", "What you woke up with, and what the rock gives up to bare hands."),
        /** Breaking rock up and sorting it. The two machines you can build out of raw stuff. */
        ORE("Ore", "Crushing and sorting: freeing the mineral, then separating it from the waste."),
        /** The first things worth making out of that metal: tools and a hull. */
        METAL("Metal", "Forging, and the plate everything after this is built out of."),
        /**
         * Chemistry that gets metal out of rock cold — and every vessel it needs is built out of
         * the rung below, which is why it sits here rather than beside the crusher.
         */
        CHEMISTRY("Chemistry", "Retort, Mond process, fluidized bed: metal without a furnace."),
        /** Air, water, pressure, and a room to hold them. All of it built from the rung below. */
        HABITAT("Habitat", "Sealing a volume and keeping what is in it breathable."),
        /** Wire, logic and the machines that need feeding. */
        POWER("Power", "Making electricity, moving it, and not setting fire to the run."),
        /** Knowing what is wrong with you, and doing something about it. */
        MEDICAL("Medical", "Microbiology: culture it, identify it, treat the thing you found.");

        private final String title;
        private final String blurb;

        Tier(String title, String blurb) {
            this.title = title;
            this.blurb = blurb;
        }

        public String title() {
            return title;
        }

        public String blurb() {
            return blurb;
        }
    }

    private static final Map<String, Tier> ROSTER = new LinkedHashMap<>();

    private static void place(Tier tier, String... ids) {
        for (String id : ids) {
            ROSTER.put(id, tier);
        }
    }

    static {
        place(Tier.SALVAGE,
                "astronima:asteroid_rock", "astronima:regolith", "astronima:tholin_clump",
                "astronima:water_ice", "astronima:chlorate_ore", "astronima:chlorate_powder",
                "astronima:hematite_ore", "astronima:ilmenite_ore", "astronima:metal_rich_ore",
                "astronima:halite_ore", "astronima:fluorite_ore",
                "astronima:pry_bar", "astronima:eva_suit", "astronima:hammer_stone",
                "astronima:mineral_salts", "minecraft:furnace",
                // Found the same way metal_rich_ore is - a rare find, not a recipe (design/
                // radiation.md §6) - even though nothing uses it until the power tier.
                "astronima:rtg_core");

        place(Tier.ORE,
                "astronima:ore_crusher", "astronima:crushed_ore", "astronima:crushed_ilmenite",
                "astronima:magnetic_separator", "astronima:iron_nickel_grains",
                "astronima:tailings", "minecraft:iron_ingot");

        place(Tier.CHEMISTRY,
                "astronima:solar_retort", "astronima:baked_silicate", "astronima:magnesium_oxide",
                "astronima:winnowing_table",
                "astronima:carbonyl_refiner", "astronima:pure_nickel", "astronima:fluidized_bed",
                "astronima:refractory_lining", "astronima:precision_bearing",
                "astronima:control_board", "astronima:gas_seal", "astronima:reinforced_frame",
                "astronima:iron_powder", "astronima:titania",
                "astronima:electrolysis_cell", "astronima:silicon_electrode",
                "astronima:aluminum_electrode", "astronima:silicon", "astronima:aluminum",
                "astronima:sls_printer", "astronima:sintered_frame",
                "astronima:cracking_tower", "astronima:polymerizer", "astronima:polyethylene",
                "astronima:mylar", "astronima:kapton_tape", "astronima:acoustic_foam",
                "astronima:sleeping_bag", "astronima:water_electrolyzer",
                "astronima:sabatier_reactor", "astronima:bosch_reactor",
                "astronima:carbon_powder", "astronima:troilite_roaster",
                "astronima:sulfuric_acid_plant", "astronima:sulfuric_acid",
                "astronima:ammonia_canister_empty", "astronima:ammonia_canister",
                "astronima:ammonia_heat_pipe", "astronima:painted_hull_plate",
                "astronima:heavy_water_cell", "astronima:titanium_cell", "astronima:titanium",
                "astronima:induction_furnace", "astronima:iron_smelter", "astronima:slag",
                "astronima:vr_simulation_pod", "astronima:downs_cell", "astronima:sodium",
                "astronima:zone_refiner", "astronima:wafer_silicon",
                "astronima:hf_digester", "astronima:hydrofluoric_acid", "astronima:gypsum",
                "astronima:hepa_filter");

        place(Tier.METAL,
                "astronima:cold_forge", "astronima:metal_billet", "astronima:tool_head",
                "astronima:iron_rod", "astronima:hull_plate", "astronima:packed_tailings",
                "astronima:improvised_pickaxe", "astronima:meteoric_pickaxe",
                "astronima:meteoric_axe", "astronima:meteoric_shovel", "astronima:wrench",
                "astronima:grab_rail", "astronima:magnetic_boots", "astronima:seismic_probe",
                "astronima:striker", "astronima:glow_stick", "minecraft:crafting_table",
                "minecraft:torch", "astronima:unlit_torch",
                // Suit repair parts: made of the first metal, and the opening loop of the game.
                "astronima:sealant_patch", "astronima:latch_set", "astronima:calibrated_valve",
                "astronima:insulation_weave", "astronima:salvaged_circuit");

        place(Tier.HABITAT,
                "astronima:insulated_hull_plate", "astronima:bulkhead_door",
                "astronima:cargo_crate", "astronima:oxygen_candle", "astronima:oxygen_tank",
                "astronima:oxygen_tank_empty", "astronima:gas_analyzer", "astronima:gas_pipe",
                "astronima:gas_port", "astronima:gas_pump", "astronima:gas_tank",
                "astronima:gas_valve", "astronima:purge_valve", "astronima:scrubber",
                "astronima:lithium_hydroxide_cartridge", "astronima:dehumidifier",
                "astronima:pre_breathe_station", "astronima:alarm",
                "astronima:airlock_controller", "astronima:painted_insulated_hull_plate");

        place(Tier.POWER,
                "astronima:solar_array", "astronima:power_cell", "astronima:combustion_generator",
                "astronima:fuel_cell", "astronima:sludge", "astronima:wire_coil",
                "astronima:wire_ribbon",
                "astronima:wire_cutters", "astronima:wire_snips",
                "astronima:power_fuse", "astronima:power_breaker",
                "astronima:diagnostic_goggles", "astronima:circuit_plate", "astronima:macro_plate",
                "astronima:basic_biomonitor_chip", "astronima:pathogen_analyzer_chip",
                "astronima:signal_switch", "astronima:signal_button", "astronima:signal_clock",
                "astronima:memory_ram", "astronima:video_framebuffer", "astronima:processor",
                "astronima:data_cell", "astronima:storage_frame", "astronima:storage_drive",
                "astronima:storage_terminal", "astronima:cell_compressor",
                "astronima:presence_sensor",
                "astronima:vacuum_sensor", "astronima:oxygen_sensor", "astronima:frost_sensor",
                "astronima:gate_and", "astronima:gate_or", "astronima:gate_xor",
                "astronima:gate_nand", "astronima:gate_nor", "astronima:gate_not",
                // Rendered off sludge, itself on this rung - same reasoning as ammonia_heat_pipe
                // sitting beside its own ammonia_canister up in Chemistry.
                "astronima:paraffin_wax", "astronima:paraffin_thermal_mass",
                // Needs insulated_hull_plate (Habitat), and the cooler needs real electricity
                // to run at all (design/cryogenics.md §1.1) - the first rung both are true of.
                "astronima:cryo_tank", "astronima:cryo_cooler", "astronima:freeze_dryer",
                "astronima:silica_aerogel",
                // Needs precision_bearing/control_board/insulated_hull_plate - the same rung
                // the cryo family above already sits at (design/radiation.md).
                "astronima:rtg", "astronima:geiger_counter",
                // Needs circuit_plate (design/halogens.md §33) - a rung later than the rest of
                // the halogens/HF family up in Chemistry, which is why it sits down here instead.
                "astronima:cleanroom_controller",
                // Needs precision_bearing/circuit_plate too (design/halogens.md §42) - the same
                // rung as the cleanroom controller it is built to sit beside.
                "astronima:etch_station", "astronima:etched_die", "astronima:fluorosilicic_acid");

        place(Tier.MEDICAL,
                "astronima:petri_dish", "astronima:incubator", "astronima:microscope",
                "astronima:synthesiser", "astronima:dose", "astronima:decon_station",
                // Astra Incognita's first two items. Placed on the last rung rather than a rung
                // of their own: their materials (baked silicate, pure nickel, hull plate) are all
                // Chemistry or earlier, so anywhere at or after Chemistry is dependency-safe, and
                // this branch does not yet have enough content to earn a dedicated tier (that is
                // an A2+ decision, design/astra-incognita.md §11).
                "astronima:spectrograph", "astronima:spectral_plate", "astronima:coherence_meter",
                // The astra field meter and collector (design/astra-extraction-loop.md §2/§3):
                // the identical optics palette, so the identical dependency-depth reasoning above
                // places them on this same rung rather than a dedicated one.
                "astronima:astra_field_meter", "astronima:astra_collector", "astronima:astra_sounder",
                // Raw asterium (design/astra-precipitation.md): reachable exactly as soon as the
                // collector that precipitates it is, so it sits on the same rung.
                "astronima:asterium_grains",
                // The ritual figure's own block forms (design/astra-ritual-grammar.md): reachable
                // the moment asterium_grains is, so the same rung.
                "astronima:asterium_block", "astronima:astra_altar",
                // The decode verb's own tokens (design/astra-research-m4b.md §1): baked silicate
                // alone, same dependency depth as the instruments right above.
                "astronima:filter_h_alpha", "astronima:filter_helium_i", "astronima:filter_iron_i",
                "astronima:filter_forbidden_oiii", "astronima:filter_sodium_d1",
                "astronima:filter_sodium_d2", "astronima:filter_calcium_h", "astronima:filter_calcium_k",
                // The tier-2 lens (design/astra-atlas-s3d-unlocks.md §4.1): the same baked
                // silicate + pure nickel dependency depth as the instruments on this rung — its
                // own gate is a research claim, not a materials tier, so it sits exactly where
                // its ingredients place it.
                "astronima:wide_aperture_lens");
    }

    /** Where a thing sits, or null if nobody has placed it. */
    public static Tier of(String id) {
        return ROSTER.get(id);
    }

    public static Map<String, Tier> roster() {
        return Map.copyOf(ROSTER);
    }

    /** Everything on one rung, in the order it was declared. */
    public static List<String> on(Tier tier) {
        return ROSTER.entrySet().stream()
                .filter(entry -> entry.getValue() == tier)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Tiers() {}
}
