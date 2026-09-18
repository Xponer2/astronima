package play.xponer.astronima.crafting;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for how every obtainable thing in Astronima is obtained.
 *
 * <p>Two consumers, one model: {@code ModRecipeProvider} emits the real recipe JSON
 * from these entries, and {@code BootstrapReachabilityTest} proves the whole graph is
 * reachable from bare hands. A recipe cannot exist without being provably craftable,
 * and nothing can quietly become kit-only. Ids are plain strings so this class stays
 * free of Minecraft imports and unit-testable.
 */
public final class CraftingTree {
    /** One way of obtaining an item. */
    public sealed interface Source permits WorldSource, Shaped, Shapeless, Cooking, Transformation {}

    /** Obtainable directly from the world with bare hands (mining drop, worldgen). */
    public record WorldSource(String id, String how) implements Source {}

    /** Obtained by using another item in the world (no crafting grid involved). */
    public record Transformation(String result, String from) implements Source {}

    public record Shaped(String result, int count, List<String> pattern,
                         Map<Character, String> keys) implements Source {
        /** Key symbols in a fixed order; see {@link Shapeless#orderedInputs()}. */
        public List<Character> orderedKeys() {
            return keys.keySet().stream().sorted().toList();
        }

        /** @see Shapeless#principalInput() */
        public String principalInput() {
            return keys.get(orderedKeys().getFirst());
        }
    }

    public record Shapeless(String result, int count, Map<String, Integer> inputs) implements Source {
        /**
         * Input ids in a fixed order.
         *
         * <p>The entries below are written with {@link Map#of}, whose iteration order is
         * deliberately randomised per JVM run. Emitting straight from the map therefore
         * reshuffled the ingredient list on every datagen run, so every run produced a
         * diff and real recipe changes were lost in the noise. Sorting by id gives datagen
         * one order to emit while keeping the concise literals below.
         *
         * <p>Shapeless recipes match in any arrangement, so this is presentation only.
         */
        public List<String> orderedInputs() {
            return inputs.keySet().stream().sorted().toList();
        }

        /**
         * The input a recipe is named and unlocked by.
         *
         * <p>Taken from {@link #orderedInputs()} rather than from the map directly, so the
         * criterion name — and the {@code _from_<input>} id of a second route to the same
         * result — stay put across runs. Before this they rode on whichever entry the map
         * happened to iterate last, which could rename a generated file rather than merely
         * reorder one's contents.
         */
        public String principalInput() {
            return orderedInputs().getFirst();
        }
    }

    /** Furnace smelting (the provider also emits a half-time blasting variant). */
    public record Cooking(String result, String input, float xp, int timeTicks) implements Source {}

    /**
     * Items the game is unwinnable without. The reachability test requires these even
     * if some future refactor drops their recipes from the list below.
     */
    public static final List<String> LIFE_CRITICAL = List.of(
            "minecraft:crafting_table",
            "minecraft:furnace",
            "minecraft:torch",
            "astronima:hull_plate",
            "astronima:insulated_hull_plate",
            "astronima:grab_rail",
            "astronima:purge_valve",
            "astronima:solar_array",
            "astronima:power_cell",
            "astronima:combustion_generator",
            "astronima:fuel_cell",
            "astronima:sludge",
            "astronima:pure_nickel",
            "astronima:carbonyl_refiner",
            "astronima:magnetic_boots",
            "astronima:bulkhead_door",
            "astronima:oxygen_candle",
            "astronima:scrubber",
            "astronima:iron_nickel_grains",
            "astronima:meteoric_pickaxe",
            "astronima:improvised_pickaxe",
            "astronima:meteoric_shovel",
            "astronima:lithium_hydroxide_cartridge",
            "astronima:oxygen_tank_empty",
            "astronima:gas_analyzer");

    /**
     * In-game documentation for things whose *use* isn't a recipe — shown by the JEI
     * plugin on the item's info page and emitted as lang keys
     * ({@code astronima.jei.usage.<path>}) by the language datagen.
     */
    public static Map<String, String> usageNotes() {
        Map<String, String> notes = new java.util.LinkedHashMap<>(literalUsageNotes());
        for (play.xponer.astronima.sim.magic.SpectralLine line
                : play.xponer.astronima.sim.magic.SpectralLine.values()) {
            notes.put("astronima:" + line.id(),
                    "Ground for " + line.displayName() + " (" + line.wavelengthNm() + " nm). TO USE: "
                            + "hold it, open the atlas, select a captured object's spectrum strip, "
                            + "and click a tall narrow peak on the curve. Locks and reveals the line "
                            + "if it is really " + line.displayName() + " shifted to that position; "
                            + "does nothing otherwise, and nothing is consumed either way. MORE IN "
                            + "THE CODEX (press K): Spectroscopy / Every light has a fingerprint.");
        }
        return java.util.Map.copyOf(notes);
    }

    private static Map<String, String> literalUsageNotes() {
        return Map.ofEntries(
                Map.entry("astronima:spectrograph",
                "Exposes a spectral plate. TO USE: hold a blank spectral plate in the OFF hand, "
                        + "stand under open sky in daylight, and right-click. Refuses with no clear "
                        + "sky or an occupied/exposed off hand. Only the Sun is reachable this way "
                        + "today; other targets exist only through the /astronima magic spectrum "
                        + "debug command until the objects to point this at exist in the world. "
                        + "MORE IN THE CODEX (press K): Spectroscopy / Every light has a fingerprint."),
                Map.entry("astronima:spectral_plate",
                "Blank until exposed by a spectrograph. An exposed plate's tooltip lists every "
                        + "spectral line actually captured, with wavelengths — real lines, real "
                        + "numbers, the same ones /astronima magic spectrum reports. A plate that "
                        + "captured nothing was pointed at something with no light or no lines of "
                        + "its own, and says so rather than looking broken. "
                        + "MORE IN THE CODEX (press K): Spectroscopy / Every light has a fingerprint."),
                Map.entry("astronima:coherence_meter",
                "Reads coherence time (tau) at a point, decomposed into its six causes: thermal, "
                        + "vibration, radiation, field, gas and your own presence. TO USE: hold it and "
                        + "walk — a live six-bar readout with a needle for total tau appears while it "
                        + "is in either hand. Right-click for the same reading in chat. The shortest bar "
                        + "is the next thing worth fixing at a site. "
                        + "MORE IN THE CODEX (press K): Coherence / The quiet places."),
                Map.entry("astronima:astra_field_meter",
                "Reads real astra density at your own position, including whatever your own "
                        + "sweeps have already taken from it. TO USE: hold it and right-click for the "
                        + "reading in chat. A number that reads lower than the ground around it is "
                        + "ground somebody has already worked."),
                Map.entry("astronima:astra_collector",
                "Sweeps astra from the ground at your position, depleting it for real, and holds "
                        + "what it collected. TO USE: right-click to sweep; nothing is collected "
                        + "from ground already burnt. SNEAK + right-click to precipitate what you "
                        + "are holding into raw asterium, once you hold enough — the held amount "
                        + "leaks over real time, so do not sit on a full charge too long."),
                Map.entry("astronima:astra_sounder",
                "Reads the real minerals beneath your feet as an absorption signature — different "
                        + "minerals really take different bites out of astra's own spectrum on the "
                        + "way up. TO USE: hold it and right-click for the reading in chat. "
                        + "MORE IN THE CODEX (press K): Instruments / Reading the crust."),
                Map.entry("astronima:asterium_grains",
                "The core's own four-billion-year process, run by a player at a scale they can "
                        + "afford: astra concentrated far enough condenses into real matter. Made, "
                        + "never mined — sneak + right-click a sufficiently charged astra collector."),
                Map.entry("astronima:asterium_block",
                "Nine asterium_grains, compressed solid. A ritual figure's own anchor material — "
                        + "design/astra-ritual-grammar.md §1 — set at the end of each arm."),
                Map.entry("astronima:astra_altar",
                "A ritual's focus. TO USE: build 2-4 straight arms of any solid blocks out from it "
                        + "(horizontal, up to 6 blocks), each ending in an asterium_block anchor, "
                        + "then a closed ring of solid blocks just past your longest arm. Right-click "
                        + "to activate — the anchors are spent whether it completes or stalls."),
                Map.entry("astronima:wide_aperture_lens",
                "A bigger objective — real aperture, not magnification. A wider lens gathers more "
                        + "of a faint object's own light, which is the only thing that actually "
                        + "resolves a dim target rather than showing the same too-faint smudge "
                        + "larger. Unlocked by holding the same_elements claim in the atlas. "
                        + "MORE IN THE CODEX (press K): Instruments / A wider lens."),
                Map.entry("astronima:gas_pipe",
                "Carries gas between rooms. Flow goes as the fourth power of the bore, so a wider line is dramatically faster and a longer one is proportionally slower — two thin pipes carry an eighth of one thick one. A pipe only ever equalises: it moves gas down a pressure difference and stops when both ends match. It cannot fill a tank above room pressure."),
                Map.entry("astronima:gas_pump",
                "Moves gas from its BIG face to its SMALL face. TO USE: run pipe from a room's gas port "
                        + "to the big recessed mouth, and from the small raised spout to a tank. WHEN IT WILL NOT "
                        + "RUN: 'no route' means nothing is plumbed to one face; 'two outlets' means two rooms or "
                        + "tanks are on one face and a pump has one of each; 'stalled' means the tank is full, "
                        + "which is success. MORE IN THE CODEX (press K): Life support / Plumbing."),
                Map.entry("astronima:gas_tank",
                "A pressure vessel. Holds far more gas than a room of the same size because it is built to take the pressure, and it survives the habitat depressurising — which is what makes it a buffer rather than storage. Rated to 3000 kPa; it bursts at 2.5x that. A pump stalls at the rating, so filling normally can never burst one."),
                Map.entry("astronima:gas_valve",
                "Narrows the bore of a run. Because flow goes as the fourth power of the bore, a valve at half open passes a SIXTEENTH of the flow, not half — nearly all of its useful range is in the last part of the travel. Shut is completely shut, so a valve is how you isolate a section. The tightest valve on a run sets the whole run's flow. Right-click to step the setting."),
                Map.entry("astronima:gas_port",
                "Opens a pipe run into a room. TO USE: 1. Place it in a wall so its dark BORE faces into the air you want to reach - the other five sides are plain plate. 2. Run pipe from the plated back. 3. A run needs TWO openings; one opening connects nothing to nothing. WHEN NOTHING HAPPENS: a bore facing rock reaches no air, and a run with a single port has nowhere to move gas to. Break a port to cut a room off the network."
                        + "MORE IN THE CODEX (press K): Life support / Plumbing."),
                Map.entry("astronima:meteoric_shovel",
                "Wears like the pickaxe: blunts instead of breaking, and regrinds on packed tailings."),
                Map.entry("astronima:meteoric_axe",
                "Wears like the pickaxe: blunts instead of breaking, and regrinds on packed tailings."),
                Map.entry("astronima:improvised_pickaxe",
                "Grains pressed into a head with no forging — a green compact, held together where the grains touch. Soft, short-lived, and only one regrind, but you can have one minutes after your first magnet pull. Build a cold forge to do better."),
                Map.entry("astronima:pry_bar",
                "Salvage off the wreck. Slow on anything but the softest rock and never wears out, so it is what you still have when everything else is gone."),
                Map.entry("astronima:oxygen_candle",
                "Place and use to strike. Releases hot oxygen for about 4 minutes and cannot be put out — size the room first. Spent candles drop nothing."),
                Map.entry("astronima:scrubber",
                "Chemically removes CO2 from the room it touches. Load LiOH cartridges by right-clicking with one; right-click empty-handed for status."),
                Map.entry("astronima:gas_analyzer",
                "Use: atmosphere readout in chat, plus a live HUD while held. Sneak-use: room diagnostics with a particle view — leaks glow orange."),
                Map.entry("astronima:oxygen_tank_empty",
                "Use inside a pressurized room to compress 12 mol of its oxygen into the tank. The room really loses that oxygen."),
                Map.entry("astronima:oxygen_tank",
                "Carry it and breathing switches to the tank automatically in vacuum or unbreathable air. About 11 minutes per tank; leaves an empty shell."),
                Map.entry("astronima:ammonia_canister_empty",
                "Use inside a room holding ammonia (real, only from a mined NH3 pocket vented into the air) to compress 20 mol of it into the canister. The room really loses that ammonia."),
                Map.entry("astronima:ammonia_canister",
                "A real, finite charge - the crafting ingredient that seals an ammonia heat pipe. No recipe consumes it any other way."),
                Map.entry("astronima:ammonia_heat_pipe",
                "A sealed, passive conductor: real ammonia heat-pipe conductivity moves heat from whichever end is hotter to whichever is colder, far faster than the bare wall it replaces. Build it between two rooms and their temperatures move toward each other over real time. No dial, no fuel - charged once by the canister it was built with."),
                Map.entry("astronima:paraffin_wax",
                "Rendered out of sludge at ordinary furnace heat, well short of what the cracking tower needs to crack it. The crafting ingredient that seals a paraffin thermal mass. MORE IN THE CODEX (press K): Life support / A block that remembers how hot it got."),
                Map.entry("astronima:paraffin_thermal_mass",
                "A real thermal mass: melts at 58 degC, banking a room's own heat spike as latent heat instead of temperature, then gives it back as it re-freezes. No dial, no fuel - place it and it works. More of them in one room share the load. MORE IN THE CODEX (press K): Life support / A block that remembers how hot it got."),
                Map.entry("astronima:bulkhead_door",
                "The only airtight door — sealed when closed. Every ordinary door leaks around the frame."),
                Map.entry("astronima:hull_plate",
                "Airtight fabricated plating: the standard wall, floor, and ceiling of a built habitat."),
                Map.entry("astronima:painted_hull_plate",
                "Hull plate finished with white TiO2 pigment. Absorbs far less of the sun than bare plate, so an exposed wall built from this runs cooler in daylight. Same seal, same strength - the paint changes what happens to sunlight, not what the wall holds back. MORE IN THE CODEX (press K): Life support / White is a colour you calculate."),
                Map.entry("astronima:painted_insulated_hull_plate",
                "Insulated hull plate, painted. Both real properties of the same wall at once: the powder jacket slows conduction into rock, the paint reflects sunlight the exposed side would otherwise soak up. MORE IN THE CODEX (press K): Life support / White is a colour you calculate."),
                Map.entry("astronima:solar_array",
                "One square metre of photovoltaic. Makes 37 W in full sun and nothing at night - it takes seven of them to match one person on a handle, because sunlight in the belt is a seventh of Earth's. Needs open sky, exactly as the retort does. TERMINALS: Power terminals on all four sides, brass. Wire one to a cell or straight to a machine; what arrives depends on the metal you chose and how far you ran it."
                        + "MORE IN THE CODEX (press K): Power / Making it."),
                Map.entry("astronima:carbonyl_refiner",
                "The Mond process. Carbon monoxide carries nickel out of a charge at 50 C and puts it "
                        + "back down pure at 230 C - and hands the CO back, so the gas is a tool you keep rather "
                        + "than a fuel you burn. TERMINALS: a strip on every side — blue in, brass power, amber "
                        + "out, left to right. MORE IN THE CODEX (press K): Ore / Metal without heat."),
                Map.entry("astronima:pure_nickel",
                "Nickel with nothing else in it. Worth having because nickel content decides how fast a billet work-hardens and how quickly it spends its give - until now you took whatever the rock contained, and now you choose."),
                Map.entry("astronima:fluidized_bed",
                "Hydrogen-reduces crushed ilmenite to iron and titania - the lunar/ISRU reaction. You "
                        + "cannot fluidize a bed in free fall, so this one SPINS to make its own gravity, and the "
                        + "spin is the only dial. Out comes iron powder (smelt it) and titania. TERMINALS: a strip "
                        + "on every side — blue in, brass power, amber out, left to right. MORE IN THE CODEX (press "
                        + "K): Ore / Metal without heat."),
                Map.entry("astronima:ilmenite_ore",
                "Iron-titanium oxide, FeTiO3, in deep rock below the hematite. A magnet does nothing to it and a furnace cannot free the metal - both the iron and the titanium are bonded to oxygen. Crush it, then reduce it in a fluidized bed."),
                Map.entry("astronima:crushed_ilmenite",
                "Ilmenite ground for the fluidized bed. It remembers the setting it was ground at, because the drum speed that fluidizes it slides with the grain size - a fine dust must be spun fast to be pinned, a coarse grind packs solid if spun fast. There is no best grind here, only a MATCH between how fine you ground and how fast you spin."),
                Map.entry("astronima:iron_powder",
                "Reduced iron off the fluidized bed - metal already, no oxygen needed to free it. Smelt or blast it into an iron ingot. Later tiers sinter it directly into machine bodies."),
                Map.entry("astronima:titania",
                "Titanium dioxide, the ilmenite's titanium half left behind by the reduction. Real Ziegler-Natta catalysts are titanium compounds, which is the polymerizer's own use for a little of it per batch. The rest keeps for the albedo paint and titanium metal of the fine-materials tier."),
                Map.entry("astronima:electrolysis_cell",
                "Molten-oxide electrolysis: splits the oxide its installed electrode targets into metal and free oxygen, straight into the sealed room it sits in. No electrode installed reaches iron for free. Power only changes how fast it runs, never which metal comes out - that is set by which electrode is in, a swap, not a dial."),
                Map.entry("astronima:silicon_electrode",
                "Retunes the electrolysis cell to silicon dioxide's own decomposition voltage. Installed equipment, not a reagent - swap it out again any time to run iron instead."),
                Map.entry("astronima:aluminum_electrode",
                "Retunes the electrolysis cell to aluminium oxide - the hardest oxide this cell reaches, and it costs the refiner's own seam-grade nickel to prove it."),
                Map.entry("astronima:silicon",
                "Off the electrolysis cell's cathode with a silicon electrode installed. Pure, straight from the melt. Zone-refine it further for real wafer-grade purity."),
                Map.entry("astronima:aluminum",
                "Off the electrolysis cell's cathode with an aluminum electrode installed - the rarest metal this tier makes."),
                Map.entry("astronima:sls_printer",
                "Selective laser sintering: fuses iron powder into a printed part. The only machine here with two real dials - drag the marker anywhere on the plane to set laser power and scan speed together. The same energy reached fast or slow prints a different part, so there is a pocket worth finding, not a point. Land in the green for a dense, sound part; land outside it and the part still prints, just porous or beaded, and it fails in use rather than failing to appear. MORE IN THE CODEX (press K): Ore / Printing a part."),
                Map.entry("astronima:sintered_frame",
                "A printed part, only as sound as the plane was set to when its batch finished. Its own soundness travels with it."),
                Map.entry("astronima:sludge",
                "Soot and sulfur scraped out of a burner with a shovel. How fast it builds up is your doing: an engine starved of air sooots, and sour gas from a sulfide pocket is worse still. Cracking-tower feedstock, same as tholins - the petrochemical tier's own use for it."),
                Map.entry("astronima:cracking_tower",
                "Thermal cracking: tholins or sludge in, ethylene and methane out, vented into the sealed room this sits in - no solid residue. Ethylene is the polymerizer's own monomer; pipe it to one with a gas pump."),
                Map.entry("astronima:polymerizer",
                "Addition polymerization: strings the room's own ethylene into polyethylene over a titania catalyst. Needs a cracking tower's gas piped in and a little titania per batch - the mod's one general engineering-plastic stock, shaped into everything else on this page."),
                Map.entry("astronima:polyethylene",
                "The polymerizer's product. Shape it at a bench into mylar, kapton tape, acoustic foam or a sleeping bag - one polymer stock, several real uses."),
                Map.entry("astronima:mylar",
                "A second path to the suit's thermal layer, alongside insulation weave - whichever you have on hand fixes the fault."),
                Map.entry("astronima:kapton_tape",
                "A second path to the suit's helmet seal, alongside a sealant patch. Cold plastic goes brittle: applied below -20 C the repair starts life shorter, same as a rushed field fix."),
                Map.entry("astronima:acoustic_foam",
                "Polyethylene panelling for an interior wall. A real building material today; the sound-dampening physics it is named for is still unbuilt, named in BACKLOG.md rather than faked."),
                Map.entry("astronima:sleeping_bag",
                "The mod's first respawn point. Use it like a bed: it sets where you wake up, and skips the night if it is one."),
                Map.entry("astronima:water_electrolyzer",
                "Real electrolysis: a water bottle in, hydrogen and oxygen out, vented into the sealed room this sits in. The hydrogen is the Sabatier reactor's own reagent - build the two in the same room and the loop closes."),
                Map.entry("astronima:sabatier_reactor",
                "The real ISS technology: your own exhaled CO2 plus the electrolyzer's hydrogen, over a nickel catalyst, into methane and water vapor. Feed the methane to a combustion generator and the water vapor to a dehumidifier, and nothing in the loop is wasted."),
                Map.entry("astronima:bosch_reactor",
                "The real alternative to the Sabatier reactor: the same room CO2 and hydrogen, over an iron catalyst instead of nickel, into solid carbon and water vapor. Needs only half the hydrogen Sabatier does for the same CO2 - the carbon has to be dealt with instead of simply burned."),
                Map.entry("astronima:carbon_powder",
                "Solid carbon off the Bosch reactor - real amorphous soot. Anneal it in a Graphitizer with the room's oxygen purged out and it reorders into real crystalline graphite; the same vessel in an ordinary breathable room just burns it away instead."),
                Map.entry("astronima:graphite_powder",
                "Real crystalline graphite, annealed straight from carbon powder in a Graphitizer with the room's oxygen purged out - the same carbon, reordered by heat, no reagent. Combine with carbon fiber at a bench for a real carbon-carbon composite plate."),
                Map.entry("astronima:graphitizer",
                "Real high-temperature carbon chemistry - no dial, it self-heats to whatever it is fed. Carbon powder or a stabilized fiber, purged room: graphite or real carbon fiber. Either one, room still breathable: hot carbon burns in the air instead, and the charge is gone as smoke."),
                Map.entry("astronima:pitch_fiber",
                "Sludge, melt-spun at a bench into green fiber - shaping, not a reaction. Thermoplastic: heat it before stabilizing and it will fuse rather than carbonize."),
                Map.entry("astronima:stabilized_fiber",
                "Pitch fiber, oxidatively cross-linked in a Graphitizer with real oxygen in the room - infusible now, and ready for the same vessel's high setpoint to carbonize it into real carbon fiber."),
                Map.entry("astronima:carbon_fiber",
                "Real carbon fiber - a stabilized pitch fiber carbonized in a Graphitizer with the room's oxygen purged out. Combine with graphite powder at a bench for a real carbon-carbon composite plate."),
                Map.entry("astronima:algae_bioreactor",
                "Real photosynthesis: 6 CO2 + 6 H2O + light -> C6H12O6 + 6 O2. A real water bottle in, real oxygen and this mod's first real food out - needs real electrical power to run at all (nothing about photosynthesis has a manual-labour equivalent) and real CO2 in the room, spent 1:1 with the oxygen it makes."),
                Map.entry("astronima:lettuce_seedling",
                "A real seed bank that survived the crash. Plant it on a hull plate under real open sky and it grows into real red romaine lettuce - the same 'Outredgeous' cultivar NASA's own Veg-01/03/05 ISS experiments grow."),
                Map.entry("astronima:lettuce",
                "Real red romaine lettuce, grown hydroponically - a real second food, larger and less nutrient-dense than algae biomass. Pick it by hand at maturity and the plant keeps growing, the real 'several mature leaves... at weekly intervals' harvest NASA's own Veg-05 uses, not a destroy-and-replant cycle."),
                Map.entry("astronima:algae_biomass",
                "This mod's first real, edible food - real Chlorella/Spirulina off the algae bioreactor, over 60% protein by mass in the real organism and cited as up to 30% of a real astronaut's daily intake in real MELiSSA/ISS photobioreactor research. A real nutrient-dense supplement, not a full meal."),
                Map.entry("astronima:crop_waste",
                "Real inedible plant matter - the outer leaves, roots and stem every real leafy-green harvest leaves behind. Feed it to an anaerobic digester for real biogas and real fertilizer."),
                Map.entry("astronima:anaerobic_digester",
                "Real anaerobic digestion: bacteria break down organic waste with no oxygen into real biogas (roughly 60% methane, 40% CO2) and real fertilizer. Runs with no power at all - real digestion feeds on the waste's own chemical potential, not electricity or light."),
                Map.entry("astronima:fertilizer",
                "Real digestate - nitrogen/phosphorus/potassium-bearing fertilizer, the digester's own real second product. No consumer for it yet in this mod."),
                Map.entry("astronima:carbon_composite_plate",
                "Real carbon-carbon composite: carbon fiber reinforcement in a graphite matrix, the same real material aerospace heat shields and rocket nozzles use. No recipe consumes it yet; kept for the structural reinforcement the Hostile Rock II phase will want."),
                Map.entry("astronima:troilite_roaster",
                "Roasts crushed ore's own troilite (FeS) in the room's real oxygen: 4 FeS + 7 O2 -> 2 Fe2O3 + 4 SO2. The hematite smelts to iron like any other ore, but the SO2 genuinely vents into the room - don't stand in a sealed one without a scrubber."),
                Map.entry("astronima:sulfuric_acid_plant",
                "The Contact Process, folded into one net line: 2 SO2 + O2 + 2 H2O -> 2 H2SO4, over a hematite catalyst - real platinum or vanadium(V) oxide is the industrial choice, but neither exists as a mineral here. Reads the exact SO2 and water vapor the roaster and the Sabatier or Bosch reactor already vent into the same room."),
                Map.entry("astronima:heavy_water_cell",
                "One electrolytic D2O enrichment stage - a water bottle in, the same bottle out one stage further up a real isotope-separation cascade. Natural ice-melt is 0.0156% D2O; feed a cell's own output back into itself and eight real passes cross 99.5%, the fuel v0.9's own reactor will want."),
                Map.entry("astronima:titanium_cell",
                "FFC-Cambridge reduction: titania stays solid, as a cathode in molten CaCl2 at ~900C, and current pulls the oxide straight out of the lattice. Real and chosen over the (also real) Kroll process specifically because it needs no chlorine and no magnesium reagent - titania in, real metal out, no separate consolidation step."),
                Map.entry("astronima:titanium",
                "Real metal, reduced straight from titania. Its real decomposition potential is lower than aluminium's own - titanium's difficulty is slow solid-state diffusion, not raw voltage. No recipe consumes it yet; kept for whichever tier finally puts it to use, the same honest 'not yet' sintered_frame already carries."),
                Map.entry("astronima:induction_furnace",
                "Melts iron powder to an ingot without touching the room's own air - a vanilla furnace here already draws real oxygen and exhales CO2 while lit, competing directly with your lungs. Real sensible plus latent heat of fusion, an order of magnitude cheaper than the titanium cell's own reduction."),
                Map.entry("astronima:iron_smelter",
                "Real fluxing and slagging: hematite ore plus magnesium oxide flux. MgO reacts with the ore's own real silicate gangue into slag, freeing the iron. Flux is optional - crude iron without it, real bloomery-style, cleaner iron with it - not a hard requirement to memorize but a real ratio to learn."),
                Map.entry("astronima:slag",
                "Real magnesium silicate (MgSiO3) off the iron smelter's own flux reaction - the gangue a real blast furnace pulls out of its ore, freed by the flux instead of contaminating the metal. Packs into shielding exactly like tailings or baked silicate."),
                Map.entry("astronima:halite_ore",
                "Rock salt - near-pure NaCl, scattered near the ice lenses it evaporated out of. Melt it in a Downs cell for real sodium metal and chlorine gas."),
                Map.entry("astronima:downs_cell",
                "The Downs process: melts rock salt and electrolyzes it, real and industrial - 2 NaCl -> 2 Na + Cl2. The highest voltage any machine here needs - sodium's own electropositivity is what makes it hard to reduce and dangerous once freed. Chlorine vents into the room, real and toxic, the same gas the solar retort's own chlorate bed already warns about."),
                Map.entry("astronima:sodium",
                "Real, reactive metal off the Downs cell. Right-click it against water for the actual reaction: 2 Na + 2 H2O -> 2 NaOH + H2, hydrogen and all - not a decoration. No recipe consumes it yet; kept for whichever tier finally puts it to work."),
                Map.entry("astronima:zone_refiner",
                "Zone refining: a molten zone dragged along a rod of electrolytic silicon rejects real metallic contamination into the melt it leaves behind rather than the crystal it forms, the same segregation any real float-zone rig uses. No dial - the real segregation coefficient for iron in silicon is so favourable that a single pass purifies essentially the whole rod."),
                Map.entry("astronima:wafer_silicon",
                "Zone-refined electrolytic silicon, real wafer-grade purity - the last zone-length of every batch is where the swept impurity ends up, cropped and discarded rather than sold. Same element as ordinary silicon, just purer; the semiconductor tier's own feedstock once cleanrooms and circuit fabrication exist."),
                Map.entry("astronima:vr_simulation_pod",
                "A gateway to a safe void. Right-click to enter; right-click any pod again, from either side, to return exactly where you left. Free placement of anything you already carry, and nothing that would kill you in there actually does. Rehearse a dangerous build before risking it for real - nothing you gain or lose inside follows you back out."),
                Map.entry("astronima:sulfuric_acid",
                "Real sulfuric acid off the plant. Digest it against fluorite in an HF digester for real hydrofluoric acid and gypsum - three items are one full charge. Other real uses (ore leaching, lead-acid battery electrolyte) still have no consumer here yet."),
                Map.entry("astronima:fluorite_ore",
                "A real hydrothermal vein mineral (CaF2), deep in the rock alongside ilmenite's own band. Digest it against sulfuric acid in an HF digester - real 1:1 stoichiometry - for real hydrofluoric acid and gypsum."),
                Map.entry("astronima:hf_digester",
                "Real fluorite digestion: CaF2 + H2SO4 -> CaSO4 + 2 HF, the \"salt cake\" process, still how most of the world's HF is made. Both reagents mandatory - no crude-without-flux shortcut. Endothermic, so it draws real power rather than reacting for free."),
                Map.entry("astronima:hydrofluoric_acid",
                "Real hydrofluoric acid off the digester - correctly composed, correctly reactive chemistry, and genuinely dangerous now: right-clicking it crosses skin without pain and starts a real, slowly-clearing chemical-burn dose that ignores worn armor - a single item's own real mass is already enough to be critical. No antidote exists yet; avoidance is the only defence."),
                Map.entry("astronima:gypsum",
                "Real gypsum (CaSO4), the digester's own byproduct. Real industrial uses are plaster and cement; no recipe consumes it here yet, the same honest 'not yet' sintered_frame already carries."),
                Map.entry("astronima:cleanroom_controller",
                "A real HEPA blower/positive-pressure unit. Load a fresh filter by right-clicking with one; right-click empty-handed for status. Holds the sealed room it touches above standard atmosphere, raising its real, tracked cleanliness toward certified over about 20 real minutes - a leaky room never gets there and burns filters trying."),
                Map.entry("astronima:hepa_filter",
                "Fed whole into a cleanroom controller and consumed on the spot - about 500 mol of real air filtered before it needs replacing."),
                Map.entry("astronima:etch_station",
                "Real wet oxide etching: SiO2 + 6 HF -> H2SiF6 + 2 H2O, real 1:6 stoichiometry. Needs a cleanroom controller touching one of its own six faces, certified at least 90% clean. The HF slot cannot be hopper-fed or dragged in - right-click the bottle onto this block to hand-load it, the same real, armor-ignoring contact event handling the bottle directly triggers."),
                Map.entry("astronima:etched_die",
                "This mod's real electronics gate, off a real wafer etch - correctly composed, not yet consumed by anything: the smart visor and SCADA automation are its real, not-yet-built uses."),
                Map.entry("astronima:fluorosilicic_acid",
                "Real H2SiF6, the etch station's own real byproduct. Real industrial use is water fluoridation; here no recipe consumes it, the same honest 'not yet' astronima:gypsum's own entry already carries."),
                Map.entry("astronima:fuel_cell",
                "Hydrogen and oxygen straight to electricity - 60 % efficient, three times cheaper in "
                        + "oxygen per megajoule than the burner, and a fifth as hot. Its exhaust is water, which "
                        + "the dehumidifier bottles. It cannot breathe the room: pure gases only, so bolt gas tanks "
                        + "to it. TERMINALS: brass power and an amber status stud on every side. MORE IN THE CODEX "
                        + "(press K): Power / Making it."),
                Map.entry("astronima:combustion_generator",
                "Burns methane out of the room it stands in - 250 W of power and 750 W of heat, because "
                        + "three quarters of any fuel's energy comes out warm. It takes two moles of your oxygen "
                        + "per mole of fuel and hands back carbon dioxide for the scrubber. Put it where you want "
                        + "the warmth. TERMINALS: brass power and an amber status stud on every side. MORE IN THE "
                        + "CODEX (press K): Power / Making it."),
                Map.entry("astronima:power_cell",
                "Holds a quarter of an hour of one machine. Its job is the night, not capacity: the array "
                        + "makes nothing for half the day. Charge it by bolting it to an array or wiring one to it "
                        + "with cable; power a machine by bolting the cell to the machine or putting both on the "
                        + "same cable run. TERMINALS: Power terminals on all four sides — brass studs. MORE IN THE "
                        + "CODEX (press K): Power / Making it."),
                Map.entry("astronima:purge_valve",
                "Emergency vent. Built into an outside wall and opened, it dumps that room's whole atmosphere to space - which is the only way to get a gas out of the air when nothing filters it. Costs you everything in the room, so isolate it first, suit up, purge, shut the valve, and refill from a stored tank."),
                Map.entry("astronima:grab_rail",
                "Something to catch hold of. Touch one while travelling and you stop dead, unhurt - which is how every real spacewalk is done. Line a shaft with them before you jump down it, not after."),
                Map.entry("astronima:magnetic_boots",
                "Ferromagnetic soles. On a floor of hull plate you walk normally; on rock they are dead weight. Gripping costs you about half your speed, so they are a trade rather than an upgrade."),
                Map.entry("astronima:insulated_hull_plate",
                "Hull plate with a tailings-packed jacket, evacuated. Seals exactly like plain plate and conducts about a twentieth as much heat - the wall to build when a habitat is losing more warmth than it makes."),
                Map.entry("astronima:cold_forge",
                "Presses metal grains into a billet, then forges it. Consolidation only works in vacuum - "
                        + "with no air there is no oxide film, so clean metal welds to itself. Take it outside. "
                        + "Blow strength is set with a WRENCH and softens as the ram's spring takes a set. "
                        + "TERMINALS: a strip on every side. MORE IN THE CODEX (press K): Ore / Calibration, and why there's no slider."),
                Map.entry("astronima:metal_billet",
                "Grains cold welded solid. Work it on the forge: each blow hardens it and spends its give, and there is no way to anneal out here."),
                Map.entry("astronima:tool_head",
                "A forged head. How well it was worked decides how long the tool lasts."),
                Map.entry("astronima:hammer_stone",
                "The bootstrap tool. Crude and quick to wear out, but it needs no metal - which is the point, since you need it to get metal."),
                Map.entry("astronima:meteoric_pickaxe",
                "Made from native meteoric iron, cold worked — no smelting at any stage, because none is possible here. Goes blunt rather than breaking and never disappears: a spent one still cuts, slowly. How long it lasts is set by how well its heads were forged. Grind the edge back on packed tailings, three times per head."),
                Map.entry("astronima:ore_crusher",
                "Hold ore and use it to work the handle. The jaw gap is set on the machine with a WRENCH, one "
                        + "notch a click, sneak to go the other way - and it opens as the liners wear, so come "
                        + "back to it. Coarse loses metal to the tailings, fine drags waste into the concentrate: "
                        + "there is no setting that avoids both. TERMINALS: a strip on every side. MORE IN THE "
                        + "CODEX (press K): Ore / Calibration, and why there's no slider."),
                Map.entry("astronima:magnetic_separator",
                "Feed it crushed ore. Reports recovery (how much of the metal it caught) and grade (how "
                        + "clean the product is) after every batch. Field strength is set with a WRENCH and falls "
                        + "away as the magnet warms. A magnet cannot touch nickel locked in sulfide, however finely "
                        + "you grind it. TERMINALS: a strip on every side — blue in, brass power, amber out. MORE "
                        + "IN THE CODEX (press K): Ore / Three separations."),
                Map.entry("astronima:cargo_crate",
                "Somewhere to put things. This world has no wood, so there are no chests and no barrels - a crate is the storage. Unlike a chest it is a full block, which means the room scan treats it as airtight: a wall of crates holds pressure, so a store room can be built out of its own contents rather than needing a hull around them."),
                Map.entry("astronima:winnowing_table",
                "Feed it crushed ore, indoors. A gas stream lifts the light silicate over the lip and "
                        + "leaves the dense grains behind, which is how it concentrates the nickel sulfide a magnet "
                        + "cannot touch at all. In vacuum it does nothing at all; there is no gas to carry "
                        + "anything. WHAT IT WILL NOT DO: give you nickel metal. MORE IN THE CODEX (press K): Ore / "
                        + "Three separations."),
                Map.entry("astronima:crushed_ore",
                "Remembers the grade it came from and the setting it was ground at. Both decide what a separator can get out of it - and they decide it differently for each one: the magnet cares how far the grains are freed, the winnowing table cares how evenly they are sized."),
                Map.entry("astronima:iron_nickel_grains",
                "Native meteoric metal - already metal, no smelting involved. This is what makes tools possible without oxygen."),
                Map.entry("astronima:tailings",
                "Spoil from separation - crushed silicate with the magnetics taken out. Pack it into blocks for shielding; later tiers can still extract from it."),
                Map.entry("astronima:solar_retort",
                "Bakes rock with a concentrating mirror - what it bakes for depends on what you feed it. Set it into a roof: the mirror needs open sky and daylight, and the gas it gives up needs a sealed room on the other side or it stalls against its own back-pressure. The focus dial has a marked window per feed - below it nothing comes out, above it the charge is ruined its own way. Tailings gives water for a dehumidifier; baked silicate fed back in, hotter, gives magnesium oxide - a real flux."
                        + "MORE IN THE CODEX (press K): Ore / Metal without heat."),
                Map.entry("astronima:baked_silicate",
                "Tailings with the water driven off. Still packs into shielding exactly as wet spoil does. Feed it back into the retort at a hotter setting and its own carbonate bakes off as CO2, leaving magnesium oxide - a real gangue-removal flux."),
                Map.entry("astronima:magnesium_oxide",
                "MgO, off a second, much hotter bake of baked silicate - real magnesite calcination, right at the edge of what this mirror can reach. Push too hard and the charge decrepitates, scattering as fines. The real flux this tier's ore processing has been missing: reacts with silicate gangue to form a fluid slag, freeing the metal."),
                Map.entry("astronima:packed_tailings",
                "Rammed spoil. Dense crushed rock is what real habitat designs use against radiation and micrometeorites - you do not ship shielding, you pile up what you already dug."),
                Map.entry("astronima:water_ice",
                "Buried ice, preserved because rock shielded it from sublimating. Melting it needs heat this tier does not have yet."),
                Map.entry("astronima:chlorate_ore",
                "Chlorate-bearing rock. Break it down for the powder that drives oxygen candles."),
                Map.entry("astronima:chlorate_powder",
                "Sodium chlorate. Heated it decomposes and releases oxygen - the chemistry behind every emergency oxygen candle."),
                Map.entry("astronima:tholin_clump",
                "Organic residue from the asteroid matrix. The only carbon on this rock, and the reason the chemical tier will be possible at all."),
                Map.entry("astronima:metal_rich_ore",
                "A seam of native iron-nickel. Ordinary chondrite carries a few percent metal; a seam carries enough to be worth the walk."),
                Map.entry("astronima:lithium_hydroxide_cartridge",
                "CO2 absorbent, consumed in place. Fitted to the suit it scrubs your helmet for about 20 minutes — roughly two tanks — and a partly used one can be loaded into a room scrubber for whatever it has left."),
                Map.entry("astronima:seismic_probe",
                "Use on a rock face: an acoustic echo reports the distance to the first cavity behind it, and whether a trapped-gas signature is present. Probe before you dig."),
                Map.entry("astronima:gate_and",
                "An AND gate: output is live when both inputs are. Parallel contacts on one line are "
                        + "already an OR, which is what wire does by itself; everything else a circuit needs is a "
                        + "component, exactly as it is in a real one. PADS: it lies flat on the surface, in the "
                        + "wire layer itself, five pixels by five. MORE IN THE CODEX (press K): Electrical / Logic "
                        + "in the wire layer."),
                Map.entry("astronima:gate_or",
                "An OR gate: output is live when either input is. Parallel contacts on one line are "
                        + "already an OR, which is what wire does by itself; everything else a circuit needs is a "
                        + "component, exactly as it is in a real one. PADS: it lies flat on the surface, in the "
                        + "wire layer itself, five pixels by five. MORE IN THE CODEX (press K): Electrical / Logic "
                        + "in the wire layer."),
                Map.entry("astronima:gate_xor",
                "An XOR gate: output is live when exactly one input is. Parallel contacts on one line are "
                        + "already an OR, which is what wire does by itself; everything else a circuit needs is a "
                        + "component, exactly as it is in a real one. PADS: it lies flat on the surface, in the "
                        + "wire layer itself, five pixels by five. MORE IN THE CODEX (press K): Electrical / Logic "
                        + "in the wire layer."),
                Map.entry("astronima:gate_nand",
                "A NAND gate: output is live when the inputs are not both. It carries the inversion "
                        + "bubble at its output pad, which is the real schematic notation and the only thing that "
                        + "tells it apart from an AND at five pixels across. PADS: it lies flat on the surface, in "
                        + "the wire layer itself, five pixels by five. MORE IN THE CODEX (press K): Electrical / "
                        + "Logic in the wire layer."),
                Map.entry("astronima:gate_nor",
                "A NOR gate: output is live when neither input is. It carries the inversion bubble at its "
                        + "output pad - the same mark a NAND has, and the same meaning. PADS: it lies flat on the "
                        + "surface, in the wire layer itself, five pixels by five. Place it and the output points "
                        + "the way you were looking; the wrench turns it afterwards. MORE IN THE CODEX (press K): "
                        + "Electrical / Logic in the wire layer."),
                Map.entry("astronima:gate_not",
                "A NOT gate: output is live when its input is not. One input pad, in the middle of one "
                        + "edge, and the inversion bubble at the output: an inverter reads as a signal passing "
                        + "straight through and coming out the other way round. PADS: it lies flat on the surface, "
                        + "in the wire layer itself, five pixels by five. MORE IN THE CODEX (press K): Electrical / "
                        + "Logic in the wire layer."),
                Map.entry("astronima:power_fuse",
                "The cheap thing that fails instead of your wall. It sits in series with a run - one pad "
                        + "on the line, one on the load - and gives up at four fifths of what that run can carry, "
                        + "so it is 11 A on standard iron and 2.3 A on signal wire. YOU CAN SEE ITS STATE: a bright "
                        + "element while it holds, a dark sooty gap once it has gone. MORE IN THE CODEX (press K): "
                        + "Electrical / Fuses and breakers."),
                Map.entry("astronima:synthesiser",
                "Fills a blank vial with whichever drug you tell it to - including the wrong one. "
                        + "IT DOES NOT READ THE PLATE: nothing here checks your antibiogram, "
                        + "because choosing the drug is the decision the whole medical tier is "
                        + "built around. The plate tells you; this does as it is told. "
                        + "MORE IN THE CODEX (press K): Medical / The bench."),
                Map.entry("astronima:dose",
                "One dose of one antimicrobial. Blank until a synthesiser fills it. TAKE IT AND "
                        + "KEEP TAKING IT: a dose covers part of a course, and letting the course "
                        + "lapse - or switching drugs halfway - breeds a strain that drug will "
                        + "never touch again. MORE IN THE CODEX (press K): Medical / The bench."),
                Map.entry("astronima:microscope",
                "A thousand times, with oil, and a fine focus you have to work. TO USE: put a "
                        + "grown dish on the stage, then drag the knob or scroll until the field "
                        + "sharpens - the sweet spot is small, because at this magnification the "
                        + "depth of field is under a micron. Click the field to write down what "
                        + "you see. IT WILL NOT RECORD A BLUR: an out-of-focus field still shows "
                        + "shapes, and shapes read at the wrong plane are how chains get written "
                        + "down as clusters. STAIN IT FIRST - unstained cells are barely visible. "
                        + "MORE IN THE CODEX (press K): Medical / The bench."),
                Map.entry("astronima:decon_station",
                "Ultraviolet for what it can see, then water for what it cannot. STAND ON IT "
                        + "AND USE IT to run a cycle; right-click with a water bottle or bucket "
                        + "to fill the tank. THE READOUT IS THE BIOMONITOR (H) - the contamination "
                        + "bars fall while you stand here. THE LAMP STALLS ON PURPOSE: light only "
                        + "reaches what it can see, so the booth switches to the rinse itself and "
                        + "needs water to finish. MORE IN THE CODEX (press K): Medical / Getting "
                        + "clean."),
                Map.entry("astronima:incubator",
                "A warm box with a thermostat, and the one machine the laboratory cannot do "
                        + "without. TO USE: put a streaked dish in the slot and set the dial to "
                        + "37 C; the plate is visible through the door and the colonies come up on "
                        + "it as it grows. TOO COLD IS A WASTED NIGHT, TOO HOT IS A DEAD PLATE: "
                        + "above 47 C it cooks, and a cooked plate looks exactly like one that was "
                        + "sterile. MORE IN THE CODEX (press K): Medical / The bench."),
                Map.entry("astronima:petri_dish",
                "A shallow dish of nutrient agar with a lid, and the notebook the whole laboratory "
                        + "writes in. TO USE: streak a sample across it in four quadrants, incubate "
                        + "it at 37 C for 18 h, then work on the colonies. IT REMEMBERS: every test "
                        + "run on it is written on the dish, so what one bench finds out the next "
                        + "bench receives. Hover it to read the label. MORE IN THE CODEX (press K): "
                        + "Medical / The bench."),
                Map.entry("astronima:power_breaker",
                "Protection you throw back on, and the switch you work behind. YOU CANNOT RESET IT INTO A "
                        + "FAULT: throw it back on with the third machine still drawing and it trips again "
                        + "immediately - fix the circuit first, then the lever holds. PADS: two pads, line and "
                        + "load, three pixels apart, exactly like a fuse - while it holds the run passes straight "
                        + "through it, and while it is off the run is two runs. MORE IN THE CODEX (press K): "
                        + "Electrical / Fuses and breakers."),
                Map.entry("astronima:circuit_plate",
                "A blank board you design a circuit into, and then bolt to a wall like any other part. "
                        + "Right-click in the air (or sneak and right-click a surface) to open the editor: it is a "
                        + "BOARD, not a list - pick a gate from the palette, click a square to drop it there, then "
                        + "drag from one pin to another and the wire appears between them. PADS: eight pixels "
                        + "square once mounted, four blue input pads A-D down one edge and four amber output pads "
                        + "W-Z down the other. MORE IN THE CODEX (press K): Electrical / Logic in the wire "
                        + "layer."),
                Map.entry("astronima:macro_plate",
                "A high-density macro assembly board for building hierarchical circuits. "
                        + "Right-click in the air (or sneak and right-click a surface) to open the editor: "
                        + "place primitive gates or drag pre-built circuit plates from your inventory as sub-components. "
                        + "PADS: ten pixels square once mounted, four blue input pads A-D down one edge and four amber "
                        + "output pads W-Z down the other. MORE IN THE CODEX (press K): Electrical / Logic in the wire layer."),
                Map.entry("astronima:diagnostic_goggles",
                "Wear these and a wire tells you about itself. Look at any trace and a panel names the "
                        + "metal it is drawn from, how long the circuit it belongs to is, its end-to-end "
                        + "resistance, the current it would carry at the machine bus, and how much of that would be "
                        + "lost heating the run instead of arriving. MORE IN THE CODEX (press K): Electrical / "
                        + "Sizing a conductor."),
                Map.entry("astronima:basic_biomonitor_chip",
                "Fit in your implant slot and the biomonitor stops guessing. Without any chip it can only "
                        + "tell you what your own body feels - a vague symptom, no cause. With this one fitted it can "
                        + "read blood gas and pressure directly, so every real hazard gets named outright: which body "
                        + "system, where, and what to actually do about it."),
                Map.entry("astronima:pathogen_analyzer_chip",
                "One tier up from the basic chip: an unidentified infection stops being a total blank and "
                        + "gets a working designator, so you can tell one organism from another before you know either "
                        + "one's real name. That name is still earned in the laboratory, not handed over by a chip."),
                Map.entry("astronima:presence_sensor",
                "A mat that closes a contact while somebody is standing on the block above it. Wire it up "
                        + "and the circuit goes live while a crew member is there - which is the first thing in the "
                        + "mod that gives a wire something to say rather than something to carry. TERMINALS: Signal "
                        + "outputs on all four sides — amber studs. MORE IN THE CODEX (press K): Electrical / Logic "
                        + "in the wire layer."),
                Map.entry("astronima:vacuum_sensor",
                "Drives its circuit while the room around it is below 30 kPa - that is, while it is not "
                        + "somewhere a person can work. Wire one inside an airlock chamber and the chamber tells "
                        + "your logic for itself that it is at vacuum, instead of the controller having to be "
                        + "asked. TERMINALS: amber signal outputs on all four sides. MORE IN THE CODEX (press K): "
                        + "Electrical / Logic in the wire layer."),
                Map.entry("astronima:oxygen_sensor",
                "Drives while the oxygen partial pressure around it is below 16 kPa - the breathing "
                        + "floor. This is the one that kills quietly: a room can be at full pressure and still be "
                        + "unbreathable, which is exactly the failure nobody notices in time. TERMINALS: amber "
                        + "signal outputs on all four sides. MORE IN THE CODEX (press K): Electrical / Logic in the "
                        + "wire layer."),
                Map.entry("astronima:frost_sensor",
                "Drives while the room around it is below -10 C. Wire it to whatever should happen when a compartment is losing the heat fight. All three alarms drive on the HAZARD rather than on the good state, so a row of them into one OR gate is a master alarm with no inversions anywhere - and the good state is one NOT gate away. TERMINALS: amber signal outputs on all four sides. It reads the room it is standing in, so put it in the room you care about."
                        + "MORE IN THE CODEX (press K): Electrical / Logic in the wire layer."),
                Map.entry("astronima:signal_button",
                "Press it and it drives its circuit for three quarters of a second, then springs back. A "
                        + "switch is a state you set; a button is an event, and an event is what you need to try a "
                        + "circuit out - watch the charge leave, follow it through your gates, see where it stops. "
                        + "PADS: three pixels square, with one amber output pad on its own face - it lies in the "
                        + "wire layer, so run the trace onto the pad itself. MORE IN THE CODEX (press K): "
                        + "Electrical / Logic in the wire layer."),
                Map.entry("astronima:signal_switch",
                "A hand switch, mounted in the wire layer: three pixels square, with one amber pad. "
                        + "Right-click it to open and close it; while it is closed, every wire joined to that pad "
                        + "is live. This is how relay logic was really built - the structure lives in the wiring, "
                        + "not in a bag of gate blocks. MORE IN THE CODEX (press K): Electrical / Logic in the wire "
                        + "layer."),
                Map.entry("astronima:signal_clock",
                "A free-running oscillator: no hand needed, it toggles on its own, twice a second, for as "
                        + "long as it is wired in. Wire it into a plate's own clock line and that plate stops "
                        + "waiting on the wall clock entirely - it settles many pulses of its own internally every "
                        + "time it refreshes, so a counter wired to a real clock chip counts far faster than one "
                        + "driven by a button. PADS: three pixels square, with one amber output pad on its own "
                        + "face. MORE IN THE CODEX (press K): Electrical / Logic in the wire layer."),
                Map.entry("astronima:memory_ram",
                "Sixteen 4-bit cells, a real chip instead of fourteen thousand hand-placed gates. Address "
                        + "with a0-a3, put the value on d0-d3, pulse clk with we live to write - only on the "
                        + "low-to-high edge, so holding clk high never repeats it. q0-q3 always show the addressed "
                        + "cell, no clock needed to read. PADS: twelve pixels square, address left, data in top, "
                        + "data out right, we and clk along the bottom. MORE IN THE CODEX (press K): Electrical / "
                        + "Logic in the wire layer."),
                Map.entry("astronima:video_framebuffer",
                "A RAM chip with a window on it: same a0-a3, d0-d3, we and clk pads as memory_ram, "
                        + "same sixteen 4-bit cells - except all 64 bits also draw as an 8x8 picture on its own "
                        + "face. Address 2y is the left half of row y, 2y+1 the right half. A full redraw is "
                        + "sixteen writes (a few seconds); one nibble is one write, instant. PADS: twelve pixels "
                        + "square, laid out exactly like memory_ram. MORE IN THE CODEX (press K): Electrical / "
                        + "Logic in the wire layer."),
                Map.entry("astronima:processor",
                "A whole microcontroller: 256 bytes of unified memory, an accumulator, a program "
                        + "counter, zero/carry flags - programmed by right-clicking it and writing assembly in "
                        + "the editor that opens. i0-i3 read a 4-bit input; p0-p3 and q0-q3 are two latched 4-bit "
                        + "output ports; stb pulses once per STROBE; run held closed executes the program, opened "
                        + "and closed again restarts it from the top. PADS: twelve pixels square, laid out like "
                        + "memory_ram's own. MORE IN THE CODEX (press K): Electrical / Logic in the wire layer."),
                Map.entry("astronima:data_cell",
                "A real digital manifest, not a bag of holding: sneak-right-click a container to log everything "
                        + "it holds into the cell - item and real count, the same honest simplification a sealed "
                        + "room's own gas mols already make. Right-click open air to drop it all back into the "
                        + "world. Capacity is 128 real slots: 64 of one item fills a slot, so one kind can run "
                        + "into the thousands before it is full, and a later upgrade doubles that. MORE IN THE "
                        + "CODEX (press K): Electrical / Logic in the wire layer."),
                Map.entry("astronima:storage_frame",
                "The buildable shell of a storage structure: any connected shape works, not just a cube - a "
                        + "wall, an L, a room's worth. Does nothing standing alone; connect it to a storage_drive "
                        + "and each frame block touching the connected group unlocks one more of the drive's own "
                        + "slots (design/data-cells.md)."),
                Map.entry("astronima:storage_drive",
                "Where real data_cell items actually go: 54 real slots, a double chest's own worth, but only 1 "
                        + "plus however much connected storage_frame surrounds it are unlocked - the rest stay "
                        + "locked until more frame is built around it. A cell aimed at a locked slot is simply "
                        + "refused, never lost. MORE IN THE CODEX (press K): Electrical / Logic in the wire "
                        + "layer."),
                Map.entry("astronima:storage_terminal",
                "The real browsing screen for whatever storage_drive(s) it directly touches: one row per "
                        + "distinct item logged across every cell in those drives, real name and real total, "
                        + "sortable by name/count/type. Drop a stack into the one real input slot and it "
                        + "auto-distributes across whichever cells have room; click a row to take a real stack "
                        + "back out. MORE IN THE CODEX (press K): Electrical / Logic in the wire layer."),
                Map.entry("astronima:cell_compressor",
                "Hold a loaded data_cell in one hand and use this in the other to raise its own compression "
                        + "one real step (64 -> 128 -> 256 -> 512 -> 1024 items per slot) - every logged entry "
                        + "survives, the cell just needs fewer of its 128 slots to hold the same real count. "
                        + "Refuses, unconsumed, on a cell already at the top tier."),
                Map.entry("astronima:wire_cutters",
                "Takes wire back out. Right-click any trace and the whole circuit joined to it comes out "
                        + "at once, with most of the metal returned; sneak and right-click to cut only the surface "
                        + "you are pointing at, for trimming a corner or freeing a face you want to build on. MORE "
                        + "IN THE CODEX (press K): Electrical / Sizing a conductor."),
                Map.entry("astronima:wire_snips",
                "Takes one length out of a run instead of the whole circuit. Right-click a wire pixel to "
                        + "mark the cut's start, right-click another pixel of the same run to cut everything "
                        + "between them and get the metal back; sneak+right-click to let go of the marked start "
                        + "without cutting. Refuses if the two pixels are not on one run, or if a loop between "
                        + "them would leave the two sides still joined the long way round. MORE IN THE CODEX "
                        + "(press K): Electrical / Sizing a conductor."),
                Map.entry("astronima:wire_coil",
                "Draws wire one pixel at a time, and the panel on it picks the metal, the colour, the "
                        + "route and the GAUGE. The gauge is the real decision: signal carries control lines, "
                        + "standard runs one or two machines, busbar feeds a workshop. Right-click a surface to "
                        + "start a run and again to finish it; the ghost shows the route and what it will cost "
                        + "before you commit. MORE IN THE CODEX (press K): Electrical / Sizing a conductor."),
                Map.entry("astronima:wire_ribbon",
                "Lays four or eight wires at once instead of one: right-click a surface to hold lane 0, "
                        + "right-click again to lay every lane in step, each its own colour. Land it right on "
                        + "a chip's row of pads and all N lanes seat at once — refuses rather than land off by "
                        + "one pin. Use in air swaps routing mode, sneak+use in air swaps four/eight lanes. "
                        + "MORE IN THE CODEX (press K): Electrical / Logic in the wire layer."),
                Map.entry("astronima:wrench",
                "Rotates a directional block in place — a pump, a port, a valve — instead of breaking and "
                        + "re-placing it. Sneak to turn it the other way. On a processing machine it CALIBRATES "
                        + "instead: one notch of the setting per click, and the machine stops for three seconds "
                        + "while the shims settle. On plumbing with nothing to turn it surveys the line and says "
                        + "why it is doing nothing. MORE IN THE CODEX (press K): Ore / Calibration, and why there's no slider."),
                Map.entry("astronima:airlock_controller",
                "Cycles a two-door chamber without losing the habitat's air. Build a sealed chamber with "
                        + "two bulkhead doors — they may be in the same wall — run port, pipe, pump and tank from "
                        + "it, and mount this on the chamber wall. SCAN fills in whatever it can guess, and you "
                        + "correct it. TERMINALS: Control inputs on the three sides that are not the panel — blue "
                        + "studs. MORE IN THE CODEX (press K): Life support / The airlock."),
                Map.entry("astronima:alarm",
                "Watches the room it touches: latches on (klaxon + full redstone signal) when oxygen runs low, toxic gases appear, CO builds up, or the mixture turns ignitable."),
                Map.entry("astronima:eva_suit",
                "Your pressure suit, salvaged damaged from the wreck. Each subsystem is repaired separately: hold the suit in one hand and use the matching part in the other. A second one can be built from scratch if the original is ever truly lost - it starts exactly as broken as the first did."),
                Map.entry("astronima:sealant_patch",
                "Seals the cracked helmet. Until it is fixed the suit cannot hold pressure at all."),
                Map.entry("astronima:latch_set",
                "Restores the tank mount, so an oxygen tank can finally be fitted to the suit."),
                Map.entry("astronima:calibrated_valve",
                "Repairs the regulator. A faulty one wastes half your tank."),
                Map.entry("astronima:insulation_weave",
                "Rebuilds the thermal layer of the suit."),
                Map.entry("astronima:salvaged_circuit",
                "Brings the suit status display back to life."),
                Map.entry("astronima:pre_breathe_station",
                "Use before an EVA to breathe pure oxygen and wash dissolved nitrogen out of your tissue, preventing the bends. Each use consumes oxygen from the room."),
                Map.entry("astronima:striker",
                "Scrape it to throw sparks: lights oxygen candles and relights torches that have gone out. No flint needed — there is none on an asteroid."),
                Map.entry("astronima:unlit_torch",
                "A torch that ran out of oxygen. It keeps its place on the wall; relight it with a striker once the air can carry a flame again."),
                Map.entry("astronima:glow_stick",
                "Cold chemical light that needs no oxygen, so it works where a flame simply goes out. Fades over time as the reaction is used up."),
                Map.entry("astronima:dehumidifier",
                "Condenses exhaled water vapor out of the room and banks it. Right-click to collect bottled water. Without one, a sealed habitat drifts to saturation: fog, frost, and mold."),
                Map.entry("astronima:cryo_tank",
                "A dewar: plumbs into the pipe network exactly like a gas tank, plus a real liquid phase. Bolt a cryocooler to a face to liquefy network gas into it, or right-click with silica aerogel to wrap it for slower boil-off. Left unvented, boil-off pressure climbs toward a real BLEVE - plumb a valve to its network the same way you would any other vessel."),
                Map.entry("astronima:cryo_cooler",
                "A Stirling cryocooler: draws real electricity (it cannot be hand-cranked - sustained refrigeration has no honest manual equivalent) and the room's own gas, and liquefies it into a bound cryo tank bolted to one of its faces. The one machine in this mod for which power is not optional."),
                Map.entry("astronima:freeze_dryer",
                "Silica aerogel by freeze-drying, not supercritical CO2 extraction: rehydrated baked silicate, frozen against an adjacent dewar's LN2, then held in real exterior vacuum while its pore water sublimes away. Only progresses outside a sealed room, next to a dewar holding liquid nitrogen."),
                Map.entry("astronima:silica_aerogel",
                "The freeze dryer's product, and a cryo tank's own top insulation tier - right-click a placed dewar with it to wrap the tank, cutting its boil-off heat leak by six times."),
                Map.entry("astronima:rtg",
                "A radioisotope thermoelectric generator: real, steady power with no fuel, no sun and no reagent at all - and unconditionally, unavoidably, a real gamma source. Nothing shields it but the room you build around it: any solid block on the line of sight stops gamma almost completely."),
                Map.entry("astronima:rtg_core",
                "A sealed radioisotope capsule. This mod has no isotope-refining chemistry, so this is salvaged whole rather than made - a rare (3%) find while mining metal_rich_ore, framed as wreck debris fused into the seam on impact."),
                Map.entry("astronima:geiger_counter",
                "Right-click to read the real gamma dose rate at your position and your own body's accumulated dose, in sievert - the same real inverse-square and line-of-sight physics the game itself uses to decide what is actually hurting you. Gamma only: nothing in this mod produces a neutron yet."));
    }

    /**
     * Which claim, if any, a recipe result needs held before it is reachable
     * (design/astra-atlas-s3d-unlocks.md §3). A separate registry rather than a new field on
     * {@link Source} itself: every existing exhaustive switch over {@code Source}'s five kinds —
     * the recipe emitter, every reachability-family test, {@code CraftingDump} — stays exactly
     * as it was, because the gate is a fact <em>about</em> a result id, read alongside the tree,
     * not a new shape inside it. This is what design/astra-atlas-s3d-unlocks.md §3 means by
     * "the proof stays one proof": {@link #reachableFromBareHands()} is unchanged and still
     * answers "is every ingredient reachable"; a gated result additionally needs its own claim
     * reachable, which {@code sim/magic/ResearchReachability} — the one place that is allowed to
     * know about both {@code CraftingTree} and {@code Claims} — checks by calling this.
     *
     * <p>Plain strings, not {@code Research.Claim} — {@code crafting} does not depend on
     * {@code sim.magic}, and does not need to: a claim id is data here, resolved by whoever
     * already knows what a claim is.
     *
     * <p><strong>Named honestly: this is the reachability *proof*'s gate, not yet the game's
     * own.</strong> {@code ModRecipeProvider} emits every {@code Shaped}/{@code Shapeless}
     * result as an ordinary vanilla recipe — Minecraft's own recipe system has no concept of
     * "claim held," so today a crafting table lets anyone make {@code wide_aperture_lens} from
     * the first tick of a new world. What exists is the correctness proof: the reachability walk
     * ({@code sim/magic/ResearchReachability}) is honest about what a gate *would* require once
     * enforced. Real enforcement — refusing or voiding the craft server-side for a player who
     * has not held the gate claim — is a separate, deliberately deferred change: this codebase
     * has no existing precedent for cancelling a vanilla craft (confirmed by grep before writing
     * this), and building that mechanism carries its own real risk (item loss, recipe-book
     * confusion, interaction with auto-crafting) that deserves its own careful pass rather than
     * being folded into this one. Tracked, not hidden — see design/astra-atlas-s3d-unlocks.md.
     */
    private static final Map<String, String> RESEARCH_GATES = Map.of(
            "astronima:wide_aperture_lens", "same_elements");

    /** The claim id gating {@code resultId}, or {@code null} for an ungated result — every other
     *  recipe in this tree. */
    public static String researchGateOf(String resultId) {
        return RESEARCH_GATES.get(resultId);
    }

    /**
     * The fixed-point walk from bare hands (BootstrapReachabilityTest's loop, extracted so the
     * research reachability guard asks the same walker instead of copying it — one proof, one
     * implementation, rule 46). Returns every item id reachable with no claims held.
     *
     * <p>Deliberately blind to {@link #researchGateOf}: this answers "is the crafting graph
     * itself connected," the question {@code BootstrapReachabilityTest} et al. already ask and
     * keep asking unchanged. A gated result's ingredients being reachable is necessary but not
     * sufficient for a player to actually craft it — the claim question is a second, independent
     * axis, and conflating the two here would make this function lie about which one it answers.
     */
    public static Set<String> reachableFromBareHands() {
        List<Source> all = sources();
        Set<String> reachable = new java.util.HashSet<>();
        for (Source source : all) {
            if (source instanceof WorldSource world) {
                reachable.add(world.id());
            }
        }
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Source source : all) {
                String result = switch (source) {
                    case WorldSource ignored -> null;
                    case Shaped shaped ->
                            reachable.containsAll(shaped.keys().values()) ? shaped.result() : null;
                    case Shapeless shapeless ->
                            reachable.containsAll(shapeless.inputs().keySet()) ? shapeless.result() : null;
                    case Cooking cooking ->
                            reachable.contains(cooking.input()) ? cooking.result() : null;
                    case Transformation transformation ->
                            reachable.contains(transformation.from()) ? transformation.result() : null;
                };
                if (result != null && reachable.add(result)) {
                    progress = true;
                }
            }
        }
        return reachable;
    }

    /** Every result id the tree can produce, whether or not anything reaches it. */
    public static Set<String> recipeIds() {
        Set<String> ids = new java.util.HashSet<>();
        for (Source source : sources()) {
            switch (source) {
                case WorldSource ignored -> { }
                case Shaped shaped -> ids.add(shaped.result());
                case Shapeless shapeless -> ids.add(shapeless.result());
                case Cooking cooking -> ids.add(cooking.result());
                case Transformation transformation -> ids.add(transformation.result());
            }
        }
        return ids;
    }

    public static List<Source> sources() {
        List<Source> found = new java.util.ArrayList<>(literalSources());
        // Filter tokens (design/astra-research-m4b.md §1/§3): one per SpectralLine, from the
        // same optics glass alone. The design's first instinct was silicate dyed to match the
        // line's own colour, dropped the moment it met this tree's own dye chain - there isn't
        // one (an asteroid has no flowers) - so each token is instead a distinct, unambiguous
        // silicate count, 1 through N. Generated from the enum for real this time - the first
        // cut of this claimed to be generated in its own comment while actually being eight
        // hand-typed Shapeless calls, found live by an audit rather than by any of the three
        // reachability-family tests, all of which walk this exact list as their own ground
        // truth and so had nothing to check a missing entry against (PLAN.md rule 86's own
        // lesson, repeated one leaf later).
        int count = 1;
        for (play.xponer.astronima.sim.magic.SpectralLine line
                : play.xponer.astronima.sim.magic.SpectralLine.values()) {
            found.add(new Shapeless("astronima:" + line.id(), 1,
                    Map.of("astronima:baked_silicate", count)));
            count++;
        }
        return List.copyOf(found);
    }

    private static List<Source> literalSources() {
        return List.of(
                // ---- bare-hands world sources (mining tags only affect speed, never drops)
                new WorldSource("astronima:asteroid_rock",
                        "The bulk rock of the asteroid — mine it anywhere, with any tool or bare hands. Occasionally yields a tholin clump."),
                new WorldSource("astronima:regolith",
                        "Loose impact rubble blanketing the surface. Dig it up with anything; sift four into mineral salts."),
                new WorldSource("astronima:pry_bar",
                        "Salvaged from the wreck at the crash site — you start with it. A lever, not an edge: it opens panels and works the softest rock, and it never wears out."),
                new WorldSource("astronima:metal_rich_ore",
                        "Rare seams in deep rock - probe for them"),
                new WorldSource("astronima:rtg_core",
                        "A sealed radioisotope capsule, salvaged rather than mined — wreck debris"
                                + " fused into a metal-rich seam when the ship that put you here broke"
                                + " apart on impact. A rare (3%) find while mining metal_rich_ore."),
                // Both are produced by machines rather than a crafting grid, so they
                // are declared here to keep the reachability test honest about them.
                new Transformation("astronima:crushed_ore", "astronima:asteroid_rock"),
                // A seam is crusher feed too. Only the plain rock was declared, so
                // nothing downstream knew a seam could be processed at all.
                new Transformation("astronima:crushed_ore", "astronima:metal_rich_ore"),
                new Transformation("astronima:iron_nickel_grains", "astronima:crushed_ore"),
                // Ilmenite: mined deep, crushed to a grind the fluidized bed reads, then reduced
                // by hydrogen to iron powder and titania. All three steps are machine work, so
                // they are declared here to keep the reachability test honest about them.
                new Transformation("astronima:crushed_ilmenite", "astronima:ilmenite_ore"),
                new Transformation("astronima:iron_powder", "astronima:crushed_ilmenite"),
                new Transformation("astronima:titania", "astronima:crushed_ilmenite"),
                // Made by the titanium cell, not a hand recipe - declared from its own feed the
                // same way pure_nickel is declared from iron-nickel grains above.
                new Transformation("astronima:titanium", "astronima:titania"),
                // Made by the freeze dryer, not a hand recipe - real baked silicate rehydrated,
                // frozen against a dewar's LN2, and sublimed dry in vacuum (design/cryogenics.md §5).
                new Transformation("astronima:silica_aerogel", "astronima:baked_silicate"),
                new Transformation("astronima:metal_billet", "astronima:iron_nickel_grains"),
                new Transformation("astronima:tool_head", "astronima:metal_billet"),
                new Transformation("astronima:tailings", "astronima:crushed_ore"),
                // The solar retort: hydrated rock in, dried rock out, water into the air.
                new Transformation("astronima:baked_silicate", "astronima:tailings"),
                new Transformation("astronima:baked_silicate", "astronima:asteroid_rock"),
                // The same retort, a second and hotter bake: baked silicate's own carbonate
                // fraction gives up its CO2, leaving real MgO flux behind (design/
                // carbonate-calcination.md).
                new Transformation("astronima:magnesium_oxide", "astronima:baked_silicate"),
                // The electrolysis cell: tailings in, whichever metal the installed electrode
                // targets out, oxygen into the room. Iron needs no electrode at all (the
                // cell's own default), so it is reachable from tailings the moment the
                // machine exists; silicon and aluminium are also declared from tailings
                // because the electrode is equipment installed at the machine, not a second
                // ingredient the crafting grid consumes.
                new Transformation("astronima:silicon", "astronima:tailings"),
                new Transformation("astronima:aluminum", "astronima:tailings"),
                // Made by the zone refiner, not a hand recipe - real segregation, not a reaction
                // (design/halogens.md §9), declared from its own feed the same way sodium is
                // declared from halite ore above.
                new Transformation("astronima:wafer_silicon", "astronima:silicon"),
                // Made by the HF digester, not a hand recipe - a real reaction, declared from its
                // own primary feed the same way slag is declared from hematite_ore. Sulfuric
                // acid's own real consumption here is not separately expressible (Transformation
                // takes one parent) - it keeps its own TERMINAL entry instead.
                new Transformation("astronima:hydrofluoric_acid", "astronima:fluorite_ore"),
                new Transformation("astronima:gypsum", "astronima:fluorite_ore"),
                // Made by the etch station, not a hand recipe - a real reaction, declared from its
                // own primary feed the same way HF/gypsum are declared from fluorite_ore above.
                // HF's own real consumption here is not separately expressible (Transformation
                // takes one parent) - it keeps its own TERMINAL entry instead (design/halogens.md
                // §45).
                new Transformation("astronima:etched_die", "astronima:wafer_silicon"),
                new Transformation("astronima:fluorosilicic_acid", "astronima:wafer_silicon"),
                // Made by the printer, not a hand recipe - declared from its own feed the same
                // way silicon and aluminium are declared from tailings above.
                new Transformation("astronima:sintered_frame", "astronima:iron_powder"),
                new WorldSource("astronima:chlorate_ore",
                        "Scattered through deep rock - the oxygen candle feedstock"),
                new WorldSource("astronima:hematite_ore",
                        "Iron oxide veins in the deeper rock (below y≈60). Smelt or blast into iron ingots."),
                new WorldSource("astronima:ilmenite_ore",
                        "Iron-titanium oxide, a deeper find than hematite (below y≈40). No magnet"
                                + " and no furnace touches it — crush it and reduce it in a"
                                + " fluidized bed."),
                new WorldSource("astronima:chlorate_powder",
                        "Shatter the white chlorate crystal veins in the upper rock. Silk touch keeps the intact crystal block instead."),
                new WorldSource("astronima:water_ice",
                        "Subsurface ice lenses at middle depths — the asteroid's water, and future oxygen and hydrogen."),
                new WorldSource("astronima:tholin_clump",
                        "Combustible organics found while mining asteroid rock (about 1 in 12 blocks). The asteroid's furnace fuel."),
                // A real seed bank surviving a real crash - the same "just have it, salvaged from
                // the wreck" standing every other bare-hands find on this page already carries
                // (design/hydroponics.md §4.3).
                new WorldSource("astronima:lettuce_seedling",
                        "Salvaged from the crew module's own emergency seed bank."),
                new WorldSource("astronima:halite_ore",
                        "Rock salt, scattered near the ice lenses it evaporated out of — melt it"
                                + " in a Downs cell for real sodium and chlorine."),
                new WorldSource("astronima:fluorite_ore",
                        "A real hydrothermal vein mineral, deep in the rock alongside ilmenite's"
                                + " own band — digest it against sulfuric acid in an HF digester"
                                + " for real hydrofluoric acid and gypsum."),
                // Made by the Downs cell, not a hand recipe - real electrolysis
                // (design/halogens.md), declared from its own feed the same way pure_nickel is
                // declared from iron-nickel grains above.
                new Transformation("astronima:sodium", "astronima:halite_ore"),

                // Scraped out of a generator that has been running. Modelled as a
                // transformation from the machine rather than from an ingredient, because
                // that is what it is: the fuel is a gas and the residue is what the gas
                // leaves behind. Declaring it reachable from the generator is the honest
                // statement of "you get this by owning one and running it".
                new Transformation("astronima:sludge", "astronima:combustion_generator"),
                // The polymerizer: titania spent as a catalyst, one item consumed per batch,
                // the same convention every other machine's own feed uses. The ethylene reagent
                // is a gas read off the room, untracked here the same way hydrogen and carbon
                // monoxide already are for the fluidized bed and the refiner.
                new Transformation("astronima:polyethylene", "astronima:titania"),
                new Transformation("astronima:pure_nickel", "astronima:iron_nickel_grains"),
                // Pressed at the forge like ordinary grains, and the billet comes out at seam
                // grade rather than chondritic - the alloy choice the refiner exists for.
                new Transformation("astronima:metal_billet", "astronima:pure_nickel"),

                // Well below cracking temperature: the same raw feed the tower converts entirely
                // to gas (design/petrochemicals.md §2) instead renders out a waxy condensate at
                // ordinary furnace heat, the same way real oil-shale retorting yields a heavy wax
                // fraction long before the temperature that would crack it (design/
                // phase-change-blocks.md §3). Not a claim about the tower's own reaction.
                new Cooking("astronima:paraffin_wax", "astronima:sludge", 0.2f, 200),

                // ---- metallurgy
                new Cooking("minecraft:iron_ingot", "astronima:hematite_ore", 0.7f, 200),
                // Reduced iron powder sinters/melts to an ingot — the fluidized bed's iron
                // half earning its keep now, before the SLS printer that will also sinter it.
                new Cooking("minecraft:iron_ingot", "astronima:iron_powder", 0.7f, 200),
                new Shapeless("astronima:iron_rod", 4, Map.of("minecraft:iron_ingot", 1)),
                new Shaped("astronima:hull_plate", 2,
                        List.of("II", "II"), Map.of('I', "minecraft:iron_ingot")),
                // ---- side-crafted materials: each machine family below draws on a different
                // one of these instead of every build reaching for the same three generic parts.
                new Shapeless("astronima:refractory_lining", 2,
                        Map.of("astronima:baked_silicate", 2, "astronima:metal_billet", 1)),
                new Shapeless("astronima:precision_bearing", 2,
                        Map.of("astronima:metal_billet", 2, "astronima:pure_nickel", 1)),
                new Shapeless("astronima:control_board", 1,
                        Map.of("astronima:salvaged_circuit", 1, "astronima:baked_silicate", 1)),
                new Shapeless("astronima:gas_seal", 2,
                        Map.of("astronima:polyethylene", 2, "astronima:metal_billet", 1)),
                new Shapeless("astronima:reinforced_frame", 1,
                        Map.of("astronima:hull_plate", 2, "astronima:iron_rod", 2)),
                // Fine rock powder packed around a plate and pumped down. The tailings are
                // the ore chain's waste stream, so the wall that keeps you warm is built
                // out of the thing you were already standing in heaps of.
                // A bent rod on a wall. Cheap in metal on purpose: what a rail really costs
                // is having placed it before you needed it.
                // A hull fitting with a gate in it: the plate is the wall it sits in, the
                // valve body is the same worked metal the rest of the plumbing is.
                // Silica for the cell, metal for the frame. The retort's baked residue is
                // the silicate this tier finally has a use for beyond rammed shielding.
                new Shaped("astronima:solar_array", 1,
                        List.of("BBB", "RRR"),
                        Map.of('B', "astronima:baked_silicate", 'R', "astronima:iron_rod")),
                // A can of worked metal with salts for the electrolyte - which is what the
                // chlorate route leaves behind, so breathing and storing share a by-product.
                // Drawn metal in a sleeve. Cheap, because what a run costs you is the loss
                // along it rather than the metal in it.
                // A worked-metal case round a burner. The mold is what shapes the cylinder,
                // which is the forge tier finally building something that is not a tool.
                // Stacked plates round a membrane. Salts stand in for the electrolyte, as
                // they do in the power cell - the chlorate route's residue earning its third use.
                // A pressure vessel with a heater round it - real refractory lining for walls
                // that actually run hot, not the same billet a cold hull already uses.
                new Shaped("astronima:carbonyl_refiner", 1,
                        List.of("BPB", "PRP", "BPB"),
                        Map.of('B', "astronima:refractory_lining", 'P', "astronima:hull_plate",
                                'R', "astronima:iron_rod")),
                // A drum on a motor: refractory lining for a wall running the tier's own
                // thermochemical heat, plate for the housing, and a salvaged circuit to drive the
                // spin — the same family as the refiner it sits beside, distinct from it by shape.
                new Shaped("astronima:fluidized_bed", 1,
                        List.of("PBP", "BCB", "PBP"),
                        Map.of('P', "astronima:hull_plate", 'B', "astronima:refractory_lining",
                                'C', "astronima:salvaged_circuit")),
                // A vessel for the melt, a control board rather than a raw circuit to drive the
                // cell — the same weight class as the refiner and the bed one bench over, and a
                // real answer to needing precise current for two very different electrode
                // chemistries, not just corners swapped on the same three parts.
                new Shaped("astronima:electrolysis_cell", 1,
                        List.of("BPB", "PCP", "BPB"),
                        Map.of('B', "astronima:metal_billet", 'P', "astronima:hull_plate",
                                'C', "astronima:control_board")),
                // The default electrode needs no crafting at all — see the cell's own doc. These
                // two are what a player builds to reach past it: a modest step up from the
                // refiner's own materials for silicon, and seam-grade nickel (the refiner's own
                // product) for aluminium, so the harder target genuinely costs having built the
                // easier tier first.
                new Shapeless("astronima:silicon_electrode", 1,
                        Map.of("astronima:metal_billet", 1, "astronima:salvaged_circuit", 1)),
                new Shapeless("astronima:aluminum_electrode", 1,
                        Map.of("astronima:pure_nickel", 1, "astronima:metal_billet", 1,
                                "astronima:salvaged_circuit", 1)),
                // Control boards rather than raw circuits, riding on precision bearings rather
                // than a raw billet: a laser and its motion gantry are precision electronics and
                // precision motion in a way a heated vessel or a spinning drum is not, so this
                // machine draws on the tier's two precision parts instead of its two bulk ones.
                new Shaped("astronima:sls_printer", 1,
                        List.of("CPC", "BPB", "CPC"),
                        Map.of('C', "astronima:control_board", 'P', "astronima:hull_plate",
                                'B', "astronima:precision_bearing")),
                // Two more machines in the same weight class. A real gas seal for the tower's own
                // vapour column, and a reinforced frame for the reactor vessel actually taking the
                // polymerisation's heat and pressure - two more of the tier's own real parts,
                // never the same three things rearranged.
                new Shaped("astronima:cracking_tower", 1,
                        List.of("PPP", "BCB", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'B', "astronima:gas_seal",
                                'C', "astronima:salvaged_circuit")),
                new Shaped("astronima:polymerizer", 1,
                        List.of("BCB", "PPP", "BCB"),
                        Map.of('B', "astronima:metal_billet", 'C', "astronima:salvaged_circuit",
                                'P', "astronima:reinforced_frame")),
                // One polymer stock, shaped at a bench into four real uses - no second machine
                // step for each (design/petrochemicals.md §2/§6). Counts differ so the four
                // recipes never collide on the same ingredient signature.
                new Shapeless("astronima:mylar", 2, Map.of("astronima:polyethylene", 1)),
                new Shapeless("astronima:kapton_tape", 3, Map.of("astronima:polyethylene", 2)),
                new Shapeless("astronima:acoustic_foam", 1, Map.of("astronima:polyethylene", 3)),
                new Shapeless("astronima:sleeping_bag", 1,
                        Map.of("astronima:polyethylene", 2, "astronima:mylar", 1)),
                // The regenerative loop's own three machines - distinct hollow-cornered patterns
                // so the family reads as three different builds next to the petrochemical bench,
                // and real gas seals rather than raw billet in every one of them: every machine
                // here holds a real gas nobody wants loose, which is not true of the tier's other
                // machine families.
                new Shaped("astronima:water_electrolyzer", 1,
                        List.of("C C", "BPB", "C C"),
                        Map.of('C', "astronima:salvaged_circuit", 'B', "astronima:gas_seal",
                                'P', "astronima:hull_plate")),
                new Shaped("astronima:sabatier_reactor", 1,
                        List.of("BPB", "C C", "BPB"),
                        Map.of('B', "astronima:gas_seal", 'P', "astronima:hull_plate",
                                'C', "astronima:salvaged_circuit")),
                // The electrolyzer's own cousin, one tier up: the same three materials the whole
                // electrolysis/reactor family already uses, arranged distinctly from all of them.
                new Shaped("astronima:heavy_water_cell", 1,
                        List.of("CBC", "BPB", "CBC"),
                        Map.of('C', "astronima:salvaged_circuit", 'B', "astronima:gas_seal",
                                'P', "astronima:hull_plate")),
                // The electrolytic tier's own hardest cell: billets rather than a gas seal, the
                // real material the crafting cost curve should read as "the latest, dearest yet."
                new Shaped("astronima:titanium_cell", 1,
                        List.of("BCB", "CPC", "BCB"),
                        Map.of('B', "astronima:metal_billet", 'C', "astronima:salvaged_circuit",
                                'P', "astronima:hull_plate")),
                // A real current driver, like the electrolysis cell it stands beside — a control
                // board, not an improvised circuit — but corners and edges swapped from that
                // cell's own B-P-B/P-C-P/B-P-B grid, so the two read as siblings, not the same
                // block twice.
                new Shaped("astronima:downs_cell", 1,
                        List.of("CBC", "BPB", "CBC"),
                        Map.of('C', "astronima:control_board", 'B', "astronima:metal_billet",
                                'P', "astronima:hull_plate")),
                // Zone refining's own machine: a control board drives the induction coil that
                // drags the molten zone, billet at the centre where the rod sits.
                new Shaped("astronima:zone_refiner", 1,
                        List.of("CPC", "PBP", "CPC"),
                        Map.of('C', "astronima:salvaged_circuit", 'P', "astronima:hull_plate",
                                'B', "astronima:metal_billet")),
                // The HF digester: the last unused corner/edge/centre permutation of this tier's
                // own billet/circuit/plate family (design/halogens.md §17 Part C1) - every other
                // arrangement of those three parts across three roles is already a sibling cell.
                new Shaped("astronima:hf_digester", 1,
                        List.of("PCP", "CBC", "PCP"),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'B', "astronima:metal_billet")),
                // A plainer cousin of the cells beside it: no circuit at the corners, since
                // there is no reaction to arm or tune - just a coil and a crucible.
                new Shaped("astronima:induction_furnace", 1,
                        List.of("BPB", "PCP", "BPB"),
                        Map.of('B', "astronima:metal_billet", 'P', "astronima:hull_plate",
                                'C', "astronima:salvaged_circuit")),
                // Real fluxing and slagging at last (design/iron-smelter.md) - plate and billet
                // swap the corners/edges induction_furnace's own grid uses, the same
                // distinct-material-grid discipline every machine in this family follows.
                new Shaped("astronima:iron_smelter", 1,
                        List.of("PBP", "BCB", "PBP"),
                        Map.of('P', "astronima:hull_plate", 'B', "astronima:metal_billet",
                                'C', "astronima:salvaged_circuit")),
                // Made by the smelter, not a hand recipe - declared from its own ore feed the
                // same way sulfuric_acid and carbon_powder are declared from their own catalyst.
                new Transformation("astronima:slag", "astronima:hematite_ore"),
                // The gateway to the void (design/vr-simulation-pod.md): a control board for the
                // simulation computer (the same precision-electronics choice sls_printer already
                // makes), a reinforced frame for the sealed capsule chamber (the same real vessel
                // the polymerizer and bosch_reactor already reuse), one circuit at the core - a
                // fresh grid, not this family's usual plate/billet/circuit arrangement, since
                // there is no plate or billet in a device that holds no reaction of its own.
                new Shaped("astronima:vr_simulation_pod", 1,
                        List.of("CFC", "FBF", "CFC"),
                        Map.of('C', "astronima:control_board", 'F', "astronima:reinforced_frame",
                                'B', "astronima:salvaged_circuit")),
                new Shaped("astronima:bosch_reactor", 1,
                        List.of("PBP", "C C", "PBP"),
                        Map.of('P', "astronima:hull_plate", 'B', "astronima:gas_seal",
                                'C', "astronima:salvaged_circuit")),
                // Made by the reactor, not a hand recipe - declared from its own catalyst feed
                // the same way sintered_frame is declared from iron powder.
                new Transformation("astronima:carbon_powder", "astronima:iron_powder"),
                // A true ring - hollow centre only - unlike any of this family's other grids
                // (design/carbon-fiber.md): the hollow-rows shapes belong to the troilite roaster
                // and the sulfuric acid plant, and every symmetric corners/edges/centre
                // permutation of plate-billet-circuit is already spoken for elsewhere.
                new Shaped("astronima:graphitizer", 1,
                        List.of("CPC", "B B", "CPC"),
                        Map.of('C', "astronima:salvaged_circuit", 'P', "astronima:hull_plate",
                                'B', "astronima:metal_billet")),
                // Made by the vessel, not a hand recipe - annealed straight from carbon powder,
                // no reagent (design/carbon-fiber.md §1's own real Acheson process).
                new Transformation("astronima:graphite_powder", "astronima:carbon_powder"),
                // Real melt-spinning: pure shaping, no reaction, the same "ordinary content, not
                // a new mechanic" cut petrochemicals.md already gives mylar/kapton off polyethylene.
                new Shapeless("astronima:pitch_fiber", 1, Map.of("astronima:sludge", 1)),
                // Made by the vessel's low setpoint - real oxidative stabilization, needs the
                // room's own oxygen (design/carbon-fiber.md §2).
                new Transformation("astronima:stabilized_fiber", "astronima:pitch_fiber"),
                // Made by the vessel's high setpoint - real carbonization/graphitization, the
                // same anneal graphite_powder gets, off a genuinely different real precursor.
                new Transformation("astronima:carbon_fiber", "astronima:stabilized_fiber"),
                // Real aerospace practice, one crafting step standing in for real multi-cycle
                // densification (design/carbon-fiber.md §3's own named simplification).
                new Shapeless("astronima:carbon_composite_plate", 1,
                        Map.of("astronima:graphite_powder", 1, "astronima:carbon_fiber", 1)),
                // A true hollow-sides ring, circuit on the outer edges rather than the corners -
                // unlike any grid in this family so far (design/hydroponics.md).
                new Shaped("astronima:algae_bioreactor", 1,
                        List.of("C C", "PBP", "C C"),
                        Map.of('C', "astronima:salvaged_circuit", 'P', "astronima:hull_plate",
                                'B', "astronima:metal_billet")),
                // Made by the reactor, not a hand recipe - real photosynthesis off a real water
                // bottle and the room's own CO2 (design/hydroponics.md §1).
                new Transformation("astronima:algae_biomass", "minecraft:potion"),
                // Not a Transformation: the seedling is planted (becomes a block), not consumed
                // into lettuce in one step - real growth happens over many random ticks in
                // between, and harvest leaves the plant alive at HARVESTED_AGE, not gone. No JEI
                // page can show that shape, and jei_machine_transformations_are_visible correctly
                // caught the fabricated claim that one should exist (the same lesson
                // minecraft:potion's own fix already taught this design, design/hydroponics.md §4).
                new WorldSource("astronima:lettuce",
                        "picked by hand from a mature lettuce plant, grown from a planted seedling"),
                // A cross, not a ring or hollow sides - a genuinely different topology from every
                // grid in this family above (checked against each one, not assumed).
                new Shaped("astronima:anaerobic_digester", 1,
                        List.of(" C ", "BPB", " C "),
                        Map.of('C', "astronima:salvaged_circuit", 'B', "astronima:metal_billet",
                                'P', "astronima:hull_plate")),
                // Trimmed by hand at the same real harvest that yields lettuce - not a
                // Transformation for the identical reason lettuce itself is not one (above):
                // harvesting is a real, drawn-out in-world interaction, not a machine "using" one
                // item to make another (design/anaerobic-digestion.md §4).
                new WorldSource("astronima:crop_waste",
                        "trimmed by hand from a mature lettuce plant at harvest, the same real"
                                + " right-click that yields the lettuce itself"),
                // Made by the digester, not a hand recipe - real anaerobic digestion off real crop
                // waste, no power required (design/anaerobic-digestion.md §1).
                new Transformation("astronima:fertilizer", "astronima:crop_waste"),
                // Same three materials as the loop's own trio above, billet and circuit swapped
                // across the hollow corners so the actual material grid differs from all three
                // (not just the letters) - the sulfur-chain machine reads as a cousin, not a clone.
                new Shaped("astronima:troilite_roaster", 1,
                        List.of("B B", "CPC", "B B"),
                        Map.of('B', "astronima:metal_billet", 'C', "astronima:salvaged_circuit",
                                'P', "astronima:hull_plate")),
                // The sulfur chain's second machine: plate and circuit swap the roles billet and
                // circuit held in the roaster's own pattern, so all five machines in this family
                // have genuinely distinct material grids, not just relabelled letters.
                new Shaped("astronima:sulfuric_acid_plant", 1,
                        List.of("PCP", "B B", "PCP"),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'B', "astronima:metal_billet")),
                // Made by the plant, not a hand recipe - declared from its own catalyst feed the
                // same way carbon_powder is declared from the Bosch reactor's own iron powder.
                new Transformation("astronima:sulfuric_acid", "astronima:hematite_ore"),
                new Shaped("astronima:fuel_cell", 1,
                        List.of("PSP", "PSP", "BBB"),
                        Map.of('P', "astronima:hull_plate", 'S', "astronima:mineral_salts",
                                'B', "astronima:metal_billet")),
                new Shaped("astronima:combustion_generator", 1,
                        List.of("PPP", "BMB", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'B', "astronima:metal_billet",
                                'M', "astronima:iron_rod")),
                new Shaped("astronima:power_cell", 1,
                        List.of("PSP", "PSP", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'S', "astronima:mineral_salts")),
                new Shaped("astronima:purge_valve", 1,
                        List.of("PRP", "R R", "PRP"),
                        Map.of('P', "astronima:hull_plate", 'R', "astronima:iron_rod")),
                new Shaped("astronima:grab_rail", 4,
                        List.of("R R", "RRR"), Map.of('R', "astronima:iron_rod")),
                // Kamacite grains are the ferromagnetic material this whole tier is built on
                // - they are what the magnetic separator catches. Soles that clamp to a deck
                // are the same magnetism, worn on a foot instead of swung over a belt.
                new Shaped("astronima:magnetic_boots", 1,
                        List.of("G G", "P P"),
                        Map.of('G', "astronima:iron_nickel_grains",
                                'P', "astronima:hull_plate")),
                new Shaped("astronima:insulated_hull_plate", 1,
                        List.of("TTT", "THT", "TTT"),
                        Map.of('T', "astronima:tailings", 'H', "astronima:hull_plate")),
                // A coating, not a second material - ordinary plate finished with the pigment,
                // the same "no new mechanic" cut mylar/kapton/foam already make off polyethylene
                // (design/albedo-paint.md §3). Independent of insulation, so both plates get
                // their own painted recipe rather than one recipe gated behind the other.
                new Shapeless("astronima:painted_hull_plate", 1,
                        Map.of("astronima:hull_plate", 1, "astronima:titania", 1)),
                new Shapeless("astronima:painted_insulated_hull_plate", 1,
                        Map.of("astronima:insulated_hull_plate", 1, "astronima:titania", 1)),

                // ---- vanilla stations, asteroid-native
                new Shaped("minecraft:crafting_table", 1,
                        List.of("PP", "PP"), Map.of('P', "astronima:hull_plate")),
                new Shaped("minecraft:furnace", 1,
                        List.of("RRR", "R R", "RRR"), Map.of('R', "astronima:asteroid_rock")),
                new Shapeless("minecraft:torch", 4,
                        Map.of("astronima:tholin_clump", 1, "astronima:iron_rod", 1)),

                // ---- tools (vanilla outputs, iron rods in the stick slots)
                //
                // No iron pickaxe. It was strictly better than the meteoric one and
                // cost only a smelted ingot, which made the entire cold-forging tier
                // — the magnet, the crusher, the work hardening — optional busywork.
                // The meteoric pickaxe is the pickaxe; see design/tool-wear.md.
                //
                // The shovel and axe stay for now because nothing replaces them yet.
                // Removing them without a meteoric equivalent would take away digging
                // rather than redirect it.
                // The vanilla shovel and axe are gone too, now that meteoric ones
                // exist. Nothing is taken away — the same jobs are done by tools that
                // wear instead of shattering and that carry how well they were made.
                new Shaped("astronima:gas_pipe", 6,
                        List.of("RRR", "RRR"),
                        Map.of('R', "astronima:iron_rod")),
                new Shaped("astronima:gas_valve", 1,
                        List.of(" R ", "RPR", " R "),
                        Map.of('P', "astronima:hull_plate", 'R', "astronima:iron_rod")),
                new Shaped("astronima:gas_pump", 1,
                        List.of("PRP", "RCR", "PRP"),
                        Map.of('P', "astronima:hull_plate", 'R', "astronima:iron_rod",
                                'C', "astronima:salvaged_circuit")),
                new Shaped("astronima:gas_tank", 1,
                        List.of("PPP", "P P", "PPP"),
                        Map.of('P', "astronima:hull_plate")),
                new Shaped("astronima:gas_port", 1,
                        List.of("PRP"),
                        Map.of('P', "astronima:hull_plate", 'R', "astronima:iron_rod")),
                new Shaped("astronima:cryo_tank", 1,
                        List.of("PIP", "I I", "PIP"),
                        Map.of('P', "astronima:hull_plate", 'I', "astronima:insulated_hull_plate")),
                new Shaped("astronima:cryo_cooler", 1,
                        List.of("PBP", "IEI", "PBP"),
                        Map.of('P', "astronima:hull_plate", 'B', "astronima:precision_bearing",
                                'I', "astronima:insulated_hull_plate", 'E', "astronima:control_board")),
                new Shaped("astronima:freeze_dryer", 1,
                        List.of("III", "I I", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'I', "astronima:insulated_hull_plate")),
                new Shaped("astronima:rtg", 1,
                        List.of("PBP", "ICI", "PBP"),
                        Map.of('P', "astronima:hull_plate", 'B', "astronima:precision_bearing",
                                'I', "astronima:insulated_hull_plate",
                                'C', "astronima:rtg_core")),
                new Shaped("astronima:geiger_counter", 1,
                        List.of("R", "E", "P"),
                        Map.of('R', "astronima:iron_rod", 'E', "astronima:control_board",
                                'P', "astronima:hull_plate")),
                new Shaped("astronima:improvised_pickaxe", 1,
                        List.of("GGG", " R ", " R "),
                        Map.of('G', "astronima:iron_nickel_grains", 'R', "astronima:iron_rod")),
                new Shaped("astronima:meteoric_shovel", 1,
                        List.of("H", "R", "R"),
                        Map.of('H', "astronima:tool_head", 'R', "astronima:iron_rod")),
                new Shaped("astronima:meteoric_axe", 1,
                        List.of("HH", "HR", " R"),
                        Map.of('H', "astronima:tool_head", 'R', "astronima:iron_rod")),

                // ---- life support
                new Shaped("astronima:bulkhead_door", 1,
                        List.of("PP", "PP", "PP"), Map.of('P', "astronima:hull_plate")),
                new Shaped("astronima:scrubber", 1,
                        List.of("PPP", "ISI", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'I', "minecraft:iron_ingot",
                                'S', "astronima:mineral_salts")),
                new Shaped("astronima:cleanroom_controller", 1,
                        List.of("PPP", "ICI", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'I', "minecraft:iron_ingot",
                                'C', "astronima:circuit_plate")),
                new Shapeless("astronima:hepa_filter", 1,
                        Map.of("astronima:mineral_salts", 3, "minecraft:iron_ingot", 1)),
                new Shaped("astronima:etch_station", 1,
                        List.of("PPP", "PBP", "PCP"),
                        Map.of('P', "astronima:hull_plate", 'B', "astronima:precision_bearing",
                                'C', "astronima:circuit_plate")),
                new Shapeless("astronima:oxygen_candle", 1,
                        Map.of("astronima:chlorate_powder", 4, "minecraft:iron_ingot", 1)),
                new Shapeless("astronima:mineral_salts", 1, Map.of("astronima:regolith", 4)),
                new Shapeless("astronima:lithium_hydroxide_cartridge", 1,
                        Map.of("astronima:mineral_salts", 2, "minecraft:iron_ingot", 1)),
                // The mechanical tier. Both machines are deliberately buildable from
                // salvage and rod stock alone: they are what you make *before* you
                // have any metal, so they cannot require any.
                // Everything in the bootstrap is rock, and that is not a stylistic
                // choice. Furnaces refuse to run without oxygen (v0.29), iron ingots
                // come only from smelting, and every tier-1 machine used to need a rod
                // made from one — so the entire no-fire route to metal was unreachable
                // on the one rock where fire is impossible. The chain now starts from
                // the ground under your feet and needs no metal until it makes some.
                new Shaped("astronima:hammer_stone", 1,
                        List.of("SS ", " S ", " S "),
                        Map.of('S', "astronima:asteroid_rock")),
                // The anvil is a block of dressed rock. Building it needs the hammer,
                // which is the right order: you shape the anvil before you use it.
                new Shaped("astronima:cold_forge", 1,
                        List.of("SSS", "SHS", "SSS"),
                        Map.of('S', "astronima:asteroid_rock",
                               'H', "astronima:hammer_stone")),
                // Cold-worked metal drawn into rod stock. This is what breaks the
                // circular dependency: rods now come *from* the tier rather than
                // being needed to build it.
                // Spoil packed into a block. Crushed silicate is the standard answer
                // to radiation and micrometeorites in every serious ISRU plan - you do
                // not ship shielding, you pile up the rock you already broke. Gives the
                // separator's waste stream a real destination without inventing a tier.
                // Silk-touching a vein keeps the crystal whole, which is how you carry
                // it home without spilling powder down a shaft. Breaking it up later
                // is the point of having kept it intact.
                new Shapeless("astronima:chlorate_powder", 3,
                        Map.of("astronima:chlorate_ore", 1)),
                new Shaped("astronima:packed_tailings", 1,
                        List.of("TT", "TT"),
                        Map.of('T', "astronima:tailings")),
                // Dried spoil rams just as well as wet: drying rock for its water must
                // not cost you the shielding you would otherwise have piled up with it.
                new Shaped("astronima:packed_tailings", 1,
                        List.of("BB", "BB"),
                        Map.of('B', "astronima:baked_silicate")),
                // Real furnace slag packs the same as any other crushed rock - the smelter's own
                // byproduct given a real use rather than sitting as a hazard-free dead end.
                new Shaped("astronima:packed_tailings", 1,
                        List.of("SS", "SS"),
                        Map.of('S', "astronima:slag")),
                new Shapeless("astronima:iron_rod", 3,
                        Map.of("astronima:metal_billet", 1)),
                new Shaped("astronima:meteoric_pickaxe", 1,
                        List.of("HHH", " R ", " R "),
                        Map.of('H', "astronima:tool_head",
                               'R', "astronima:iron_rod")),
                // Nothing here is imported. The jaw is asteroid rock — harder than
                // the chondrite it crushes — hung on salvaged rod stock. An earlier
                // draft of this recipe called for cobblestone, which does not exist
                // anywhere in this game.
                new Shaped("astronima:ore_crusher", 1,
                        List.of("S S", "SHS", "S S"),
                        Map.of('S', "astronima:asteroid_rock",
                               'H', "astronima:hammer_stone")),
                // The drum is wound around lodestone: the magnetite fraction of iron
                // ore is naturally magnetised, which is how the first compasses were
                // made and the only magnet available out here.
                new Shaped("astronima:magnetic_separator", 1,
                        List.of("SMS", "M M", "SMS"),
                        Map.of('S', "astronima:asteroid_rock",
                               'M', "astronima:hematite_ore")),
                // Plate folded into a box. Nothing clever: the point is that it exists at
                // all in a world where a chest cannot be made.
                new Shaped("astronima:cargo_crate", 1,
                        List.of("PPP", "P P", "PPP"),
                        Map.of('P', "astronima:hull_plate")),
                // A shallow perforated deck with a plenum under it. No magnet, no heat,
                // no reagent: the working fluid is the habitat's own air, which is why
                // this is the one separation step that stops at the airlock door.
                new Shaped("astronima:winnowing_table", 1,
                        List.of("PPP", "R R", "SSS"),
                        Map.of('P', "astronima:hull_plate",
                               'R', "astronima:iron_rod",
                               'S', "astronima:asteroid_rock")),
                // A concentrating mirror over a sealed vessel. Polished hull plate is
                // the only reflective surface this tier can make, and the vessel has to
                // hold pressure, so it is plate too.
                new Shaped("astronima:solar_retort", 1,
                        List.of("PPP", "PGP", "SIS"),
                        Map.of('P', "astronima:hull_plate",
                               'G', "astronima:iron_nickel_grains",
                               'S', "astronima:asteroid_rock",
                               'I', "minecraft:iron_ingot")),
                new Shaped("astronima:oxygen_tank_empty", 1,
                        List.of("I", "P", "P"),
                        Map.of('I', "minecraft:iron_ingot", 'P', "astronima:hull_plate")),
                // Same slim vertical shape as the oxygen tank one bench over - a real
                // pressure canister is a real pressure canister - but the actual material
                // grid differs (a circuit reads the seal, not raw iron), so the two do not
                // collide as recipes.
                new Shaped("astronima:ammonia_canister_empty", 1,
                        List.of("C", "P", "P"),
                        Map.of('C', "astronima:salvaged_circuit", 'P', "astronima:hull_plate")),
                // The filled canister becomes the pipe's own sealed core - a real crafting
                // ingredient, not a token, matching "charged once" (design/ammonia-heat-pipes.md
                // §3): billet on either side of the canister, one straight row.
                new Shaped("astronima:ammonia_heat_pipe", 1,
                        List.of("BCB"),
                        Map.of('B', "astronima:metal_billet", 'C', "astronima:ammonia_canister")),
                // A sealed cartridge of wax boxed in hull plate - furniture, not a machine, so
                // no third grid pattern to learn beyond "wax in a box" (design/
                // phase-change-blocks.md §4).
                new Shaped("astronima:paraffin_thermal_mass", 1,
                        List.of("PPP", "PWP", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'W', "astronima:paraffin_wax")),
                new Shapeless("astronima:gas_analyzer", 1,
                        Map.of("minecraft:iron_ingot", 2, "astronima:mineral_salts", 1,
                                "astronima:hull_plate", 1)),

                // A full replacement suit — real materials, once the original wreck suit is
                // genuinely gone rather than merely worn (design/suit.md, PLAN.md rule 118). The
                // same weight class as incubator (6 hull_plate, 1 salvaged_circuit, 2 iron_rod),
                // not the cheapest thing this tier has, and not a shortcut around the repair
                // loop the whole tier is built on: nothing here sets SuitCondition working, so a
                // freshly built one starts exactly as broken as the one you woke up in
                // (EvaSuitItem.repairedMask's own default is SuitCondition.WRECKED, and this
                // recipe does not touch the stack's data components at all) — every subsystem
                // still needs the same real repair verb before the suit holds pressure.
                new Shaped("astronima:eva_suit", 1,
                        List.of("PPP", "PCP", "RPR"),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'R', "astronima:iron_rod")),

                // ---- suit repair parts: simple machined pieces, one per fault
                new Shapeless("astronima:sealant_patch", 2,
                        Map.of("astronima:mineral_salts", 1, "astronima:hull_plate", 1)),
                new Shapeless("astronima:latch_set", 1,
                        Map.of("minecraft:iron_ingot", 1, "astronima:iron_rod", 1)),
                new Shapeless("astronima:calibrated_valve", 1,
                        Map.of("minecraft:iron_ingot", 2, "astronima:mineral_salts", 1)),
                new Shapeless("astronima:insulation_weave", 1,
                        Map.of("astronima:tholin_clump", 2, "astronima:mineral_salts", 1)),
                new Shapeless("astronima:salvaged_circuit", 1,
                        Map.of("minecraft:iron_ingot", 1, "astronima:chlorate_powder", 2)),

                // ---- instruments & safety (v0.2)
                new Shapeless("astronima:seismic_probe", 1,
                        Map.of("minecraft:iron_ingot", 2, "astronima:iron_rod", 1,
                                "astronima:mineral_salts", 1)),
                // Rod stock into a spanner: available at the iron tier, which is exactly
                // when the directional plumbing it aims becomes buildable.
                // Wire is drawn from rod stock over a former. Deliberately cheap per coil: the
                // cost that matters in this tier is the metal a *run* eats, which is charged by
                // the pixel, not the tool that lays it.
                // Cutters: two jaws and a pivot. The reclaim half of the wire loop, so it is
                // costed to be affordable at the same time as the coil rather than later.
                // A mat with a contact under it, and a hand switch. Both are plate, a circuit
                // and a little rod stock: control gear is cheap, because the interesting cost of
                // an automated airlock is the wiring you have to think about, not the parts.
                // Logic. One shape for all six: a plate housing round a circuit, on a stem.
                // Cheap on purpose - the cost of a control system is the thinking, not the parts.
                // A fuse is a deliberately undersized conductor in a housing: a pinch of metal
                // and a plate, which is why it is the cheap thing that fails instead of the wall.
                // A breaker is a fuse plus a mechanism: the same pinch of conductor, plus the
                // linkage that lets it be thrown back. One each, and it never needs replacing —
                // which is the trade, because the fuse comes four to a craft.
                new Shaped("astronima:power_breaker", 1,
                        List.of("PRP", " C "),
                        Map.of('P', "astronima:hull_plate", 'R', "astronima:iron_rod",
                                'C', "astronima:salvaged_circuit")),
                // A dish is glass and a little nutrient. Cheap on purpose: you will use dozens,
                // and a laboratory that made you ration plates would make you guess instead of
                // testing, which is the opposite of the point.
                // A box, a heater and a pane to look through: an incubator is not clever, it is
                // just something that holds a temperature honestly.
                // Glass, a frame and something to hold the slide still. The lens is the
                // expensive part, and the salvaged circuit stands in for it.
                // Glassware and a pump. The clever part is the chemistry, and the chemistry is
                // in what you tell it to make.
                new Shaped("astronima:synthesiser", 1,
                        List.of("SCS", "PRP", "PPP"),
                        Map.of('S', "astronima:baked_silicate", 'C', "astronima:salvaged_circuit",
                                'P', "astronima:hull_plate", 'R', "astronima:iron_rod")),
                new Shaped("astronima:dose", 4,
                        List.of("SS"),
                        Map.of('S', "astronima:baked_silicate")),
                new Shaped("astronima:microscope", 1,
                        List.of(" S ", "PCP", "PPP"),
                        Map.of('S', "astronima:baked_silicate", 'P', "astronima:hull_plate",
                                'C', "astronima:salvaged_circuit")),
                // A cabinet, a lamp and a nozzle plate. The salvaged circuit is the ballast the
                // tube needs; the glow stick is the light source, which is the only cold-cathode
                // thing on this rock that emits anything a germicidal lamp could be built from.
                new Shaped("astronima:decon_station", 1,
                        List.of("PGP", "PCP", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'G', "astronima:glow_stick",
                                'C', "astronima:salvaged_circuit")),
                new Shaped("astronima:incubator", 1,
                        List.of("PPP", "PCP", "PRP"),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'R', "astronima:iron_rod")),
                new Shaped("astronima:petri_dish", 4,
                        List.of("S S", "SSS"),
                        Map.of('S', "astronima:baked_silicate")),
                new Shaped("astronima:power_fuse", 4,
                        List.of("PRP"),
                        Map.of('P', "astronima:hull_plate", 'R', "astronima:iron_rod")),
                new Shaped("astronima:gate_and", 1,
                        List.of("PCP", " R "),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'R', "astronima:iron_rod")),
                new Shaped("astronima:gate_or", 1,
                        List.of("PCP", " R "),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'R', "astronima:iron_rod")),
                new Shaped("astronima:gate_xor", 1,
                        List.of("PCP", " R "),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'R', "astronima:iron_rod")),
                new Shaped("astronima:gate_nand", 1,
                        List.of("PCP", " R "),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'R', "astronima:iron_rod")),
                new Shaped("astronima:gate_nor", 1,
                        List.of("PCP", " R "),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'R', "astronima:iron_rod")),
                new Shaped("astronima:gate_not", 1,
                        List.of("PCP", " R "),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'R', "astronima:iron_rod")),
                // A blank board: plate, a circuit, and rod stock for its legs.
                new Shaped("astronima:circuit_plate", 1,
                        List.of("RCR", "PPP"),
                        Map.of('R', "astronima:iron_rod", 'C', "astronima:salvaged_circuit",
                                'P', "astronima:hull_plate")),
                new Shaped("astronima:macro_plate", 1,
                        List.of("CCC", "PPP"),
                        Map.of('C', "astronima:circuit_plate", 'P', "astronima:hull_plate")),
                // Goggles: a salvaged circuit behind glass, strapped on. The tier's instrument.
                new Shaped("astronima:diagnostic_goggles", 1,
                        List.of("RPR", " C "),
                        Map.of('R', "astronima:iron_rod", 'P', "astronima:hull_plate",
                                'C', "astronima:salvaged_circuit")),
                // The chip ladder's own two rungs (design/biomonitor-chips.md). Small, implanted
                // electronics rather than a machine, so a plain combination rather than a grid -
                // the same shape control_board itself already uses.
                new Shapeless("astronima:basic_biomonitor_chip", 1,
                        Map.of("astronima:control_board", 1, "astronima:salvaged_circuit", 1)),
                // A sample stage to hold something steady under - the same precision-bearing part
                // sls_printer already reaches for, one tier up from the basic chip's plain circuit.
                new Shapeless("astronima:pathogen_analyzer_chip", 1,
                        Map.of("astronima:control_board", 1, "astronima:precision_bearing", 1)),
                new Shaped("astronima:presence_sensor", 1,
                        List.of("PPP", "RCR"),
                        Map.of('P', "astronima:hull_plate", 'R', "astronima:iron_rod",
                                'C', "astronima:salvaged_circuit")),
                new Shaped("astronima:vacuum_sensor", 1,
                        List.of("PCP", " S "),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'S', "astronima:mineral_salts")),
                new Shaped("astronima:oxygen_sensor", 1,
                        List.of("PCP", " S "),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'S', "astronima:mineral_salts")),
                new Shaped("astronima:frost_sensor", 1,
                        List.of("PCP", " S "),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit",
                                'S', "astronima:mineral_salts")),
                new Shaped("astronima:signal_button", 1,
                        List.of(" R ", "PCP"),
                        Map.of('P', "astronima:hull_plate", 'R', "astronima:iron_rod",
                                'C', "astronima:salvaged_circuit")),
                new Shaped("astronima:signal_switch", 1,
                        List.of(" R ", "PCP"),
                        Map.of('P', "astronima:hull_plate", 'R', "astronima:iron_rod",
                                'C', "astronima:salvaged_circuit")),
                // Two circuits rather than one - it is timing itself, not just carrying a contact.
                new Shaped("astronima:signal_clock", 1,
                        List.of("CRC", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'R', "astronima:iron_rod",
                                'C', "astronima:salvaged_circuit")),
                // Built from a macro plate rather than a hull plate - sixteen real cells is a step
                // up from the chip that nests other chips, not a sibling to a switch.
                new Shaped("astronima:memory_ram", 1,
                        List.of("CRC", "RMR", "CRC"),
                        Map.of('C', "astronima:salvaged_circuit", 'R', "astronima:iron_rod",
                                'M', "astronima:macro_plate")),
                // The same core as memory_ram (a macro plate between two rods) with a full ring
                // of circuits rather than just the corners - the extra electronics driving the
                // matrix, not a different chip underneath.
                new Shaped("astronima:video_framebuffer", 1,
                        List.of("CCC", "RMR", "CCC"),
                        Map.of('C', "astronima:salvaged_circuit", 'R', "astronima:iron_rod",
                                'M', "astronima:macro_plate")),
                // A real chip wrapped in driver electronics and a housing - no exotic new
                // material, the same chip-plus-electronics-plus-frame family memory_ram itself
                // already is (design/data-cells.md §5).
                new Shapeless("astronima:data_cell", 1,
                        Map.of("astronima:memory_ram", 1, "astronima:salvaged_circuit", 1,
                                "astronima:metal_billet", 1)),
                // The buildable shell around a drive - plate and billet only, no circuit,
                // since the frame itself does nothing but exist and connect (design/data-cells.md
                // §9). A lattice grid rather than a solid plate reads as structure to build with,
                // not another wall panel.
                new Shaped("astronima:storage_frame", 4,
                        List.of("PBP", "B B", "PBP"),
                        Map.of('P', "astronima:hull_plate", 'B', "astronima:metal_billet")),
                // Cargo crate's own open-box shape (design/data-cells.md §9) with a circuit set
                // into the lid - the same box, wired to gate what actually goes in.
                new Shaped("astronima:storage_drive", 1,
                        List.of("PCP", "P P", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'C', "astronima:salvaged_circuit")),
                // The drive's own screen, in a different real material: a display panel where
                // the drive had a lid, and a billet core where the drive had none - the same
                // storage-family box shape, wired differently, so the two read as siblings.
                new Shaped("astronima:storage_terminal", 1,
                        List.of("CDC", "PBP", "PPP"),
                        Map.of('C', "astronima:salvaged_circuit", 'D', "astronima:video_framebuffer",
                                'P', "astronima:hull_plate", 'B', "astronima:metal_billet")),
                // Real compression, not a new chip: a circuit to drive the denser addressing and
                // a precision bearing for the same fine-tolerance fabrication data_cell's own
                // memory_ram already needed - no exotic material invented for a four-step ladder.
                new Shapeless("astronima:cell_compressor", 1,
                        Map.of("astronima:salvaged_circuit", 1, "astronima:precision_bearing", 1)),
                // The same three ingredients as memory_ram and video_framebuffer, in a third
                // arrangement - a whole microcontroller earns no exotic new material, only a
                // denser one of the same chip family.
                new Shaped("astronima:processor", 1,
                        List.of("RCR", "CMC", "RCR"),
                        Map.of('C', "astronima:salvaged_circuit", 'R', "astronima:iron_rod",
                                'M', "astronima:macro_plate")),
                new Shaped("astronima:wire_cutters", 1,
                        List.of("I I", " R ", "R R"),
                        Map.of('I', "minecraft:iron_ingot", 'R', "astronima:iron_rod")),
                // A lighter, cheaper build than the cutters: one blade pair pivoting on a rod,
                // not the full two-handed jaw that takes a whole circuit out at once.
                new Shapeless("astronima:wire_snips", 1,
                        Map.of("minecraft:iron_ingot", 1, "astronima:iron_rod", 1)),
                new Shapeless("astronima:wire_coil", 1,
                        Map.of("astronima:iron_rod", 2, "minecraft:iron_ingot", 1)),
                new Shaped("astronima:wire_ribbon", 1,
                        List.of("RRR", " I ", "RRR"),
                        Map.of('R', "astronima:iron_rod", 'I', "minecraft:iron_ingot")),
                new Shaped("astronima:wrench", 1,
                        List.of("R R", " I ", " R "),
                        Map.of('R', "astronima:iron_rod", 'I', "minecraft:iron_ingot")),
                // The airlock panel: plate for the housing, a circuit for the sequencer.
                // Costed at the same tier as the pump it drives — an airlock is the
                // *reason* to build the pump-and-tank chain, so it must not out-tier it.
                new Shaped("astronima:airlock_controller", 1,
                        List.of("PPP", "PCP", "PRP"),
                        Map.of('P', "astronima:hull_plate",
                                'C', "astronima:salvaged_circuit",
                                'R', "astronima:iron_rod")),
                new Shapeless("astronima:alarm", 1,
                        Map.of("minecraft:iron_ingot", 3, "astronima:hull_plate", 1,
                                "astronima:mineral_salts", 1)),
                // Ferrocerium on steel: sparks without flint, of which there is none here.
                new Shapeless("astronima:striker", 1,
                        Map.of("astronima:iron_rod", 1, "astronima:chlorate_powder", 1)),

                // Chemiluminescence: two reagents, no oxygen, no heat.
                new Shapeless("astronima:glow_stick", 3,
                        Map.of("astronima:tholin_clump", 1, "astronima:mineral_salts", 1)),
                new Shaped("astronima:dehumidifier", 1,
                        List.of("PPP", "IRI", "PPP"),
                        Map.of('P', "astronima:hull_plate", 'I', "minecraft:iron_ingot",
                                'R', "astronima:iron_rod")),
                // A real, pre-existing gap this tree never had to name until now: the
                // dehumidifier is this mod's own real source of a bottled water item
                // (SolarRetortBlockEntity's own doc - "the same vapour the dehumidifier
                // bottles"), and nothing needed that traced until algae_biomass's own real
                // photosynthesis chain became the first thing to consume a water bottle as its
                // defining feedstock rather than an already-reachable convenience.
                // WorldSource, not Transformation: the dehumidifier is a passive
                // ReadableBlockEntity with no feed slot and no JEI page of its own - it condenses
                // room water vapour (a gas), it does not "use" any item to make the bottle, so a
                // Transformation's own "used another item" premise would be false, not merely
                // undemonstrated (`jei_machine_transformations_are_visible` caught exactly this
                // when this was first written as a Transformation from hull_plate).
                new WorldSource("minecraft:potion",
                        "condensed from a sealed room's own humid air by a dehumidifier"),
                new Shaped("astronima:pre_breathe_station", 1,
                        List.of("PIP", "PRP"),
                        Map.of('P', "astronima:hull_plate", 'I', "minecraft:iron_ingot",
                                'R', "astronima:iron_rod")),

                // Astra Incognita: optics from the retort, a mirror from the carbonyl refiner,
                // no new ore (design/astra-incognita.md §7). A materials chain built from what
                // v0.55 already produces, one step simpler than the design's own — see the
                // usage notes for exactly what is simplified and why.
                new Shaped("astronima:spectrograph", 1,
                        List.of("SPS", "SHS", " R "),
                        Map.of('S', "astronima:baked_silicate", 'P', "astronima:pure_nickel",
                                'H', "astronima:hull_plate", 'R', "astronima:iron_rod")),
                new Shaped("astronima:spectral_plate", 4,
                        List.of("SSS"),
                        Map.of('S', "astronima:baked_silicate")),
                // The survey meter (§8.2): the spectrograph's own optics palette, so it reads as
                // the same instrument family, in a compact handheld shape rather than the
                // spectrograph's tripod-like one.
                new Shaped("astronima:coherence_meter", 1,
                        List.of("SPS", " H ", " R "),
                        Map.of('S', "astronima:baked_silicate", 'P', "astronima:pure_nickel",
                                'H', "astronima:hull_plate", 'R', "astronima:iron_rod")),
                // design/astra-extraction-loop.md §2: the tier's own first, cheapest instrument
                // (astra-systems.md's own layer I) — the same optics palette, simpler than the
                // coherence meter's own shape, since it reads one number rather than decomposing
                // six.
                new Shaped("astronima:astra_field_meter", 1,
                        List.of("S S", " P ", " R "),
                        Map.of('S', "astronima:baked_silicate", 'P', "astronima:pure_nickel",
                                'R', "astronima:iron_rod")),
                // design/astra-extraction-loop.md §3: layer II's first machine, boxier and built
                // from more hull plate than the handheld instruments above it — the same optics
                // sensing element, housed rather than merely held.
                new Shaped("astronima:astra_collector", 1,
                        List.of("HHH", "SPS", "HRH"),
                        Map.of('H', "astronima:hull_plate", 'S', "astronima:baked_silicate",
                                'P', "astronima:pure_nickel", 'R', "astronima:iron_rod")),
                // design/astra-crust-sounding.md §3: the tier's third real instrument, a single
                // shaft driven downward rather than the meter's own held-flat shape - the same
                // optics palette again, one column instead of a T, so the bench reads it as a
                // sibling of the meter rather than a re-skin of it.
                new Shaped("astronima:astra_sounder", 1,
                        List.of(" S ", " P ", " R "),
                        Map.of('S', "astronima:baked_silicate", 'P', "astronima:pure_nickel",
                                'R', "astronima:iron_rod")),
                // design/astra-ritual-grammar.md §1's anchor material: the standard 3x3
                // compression every metal in this mod already gives its own raw material, so
                // "grains become a block" reads as the same familiar step, not a new one.
                new Shaped("astronima:asterium_block", 1,
                        List.of("GGG", "GGG", "GGG"),
                        Map.of('G', "astronima:asterium_grains")),
                // design/astra-ritual-grammar.md's own focus block: a hull-plate housing around a
                // small real cost of asterium_grains itself - the altar is attuned with a pinch of
                // the very material its own rituals will go on to make more of.
                new Shaped("astronima:astra_altar", 1,
                        List.of("HHH", "GRG", "HHH"),
                        Map.of('H', "astronima:hull_plate", 'G', "astronima:asterium_grains",
                                'R', "astronima:iron_rod")),
                // design/astra-atlas-s3d-unlocks.md §4.1: a real bigger objective, mostly the
                // same optics glass the spectrograph itself is ground from, ringed in a nickel
                // mount for rigidity. Gated on same_elements — see RESEARCH_GATES's own comment
                // for why not metal_assay.
                new Shaped("astronima:wide_aperture_lens", 1,
                        List.of("SSS", "SPS", "SSS"),
                        Map.of('S', "astronima:baked_silicate", 'P', "astronima:pure_nickel")),

                // ---- in-world transformations (documented via usage notes)
                new Transformation("astronima:oxygen_tank", "astronima:oxygen_tank_empty"),
                new Transformation("astronima:ammonia_canister", "astronima:ammonia_canister_empty"),
                new Transformation("astronima:unlit_torch", "minecraft:torch"),
                // design/astra-precipitation.md: precipitated by a sufficiently-charged collector,
                // not crafted from a static ingredient — "from" names the collector itself, so
                // reachability walks through it exactly like every other Transformation here.
                new Transformation("astronima:asterium_grains", "astronima:astra_collector"),
                new WorldSource("astronima:eva_suit",
                        "Salvaged from the crash — you are already wearing it. Every subsystem starts broken."));
    }

    private CraftingTree() {}
}
