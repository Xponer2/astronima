package play.xponer.astronima.compat.jei;

import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.block.entity.MagneticSeparatorBlockEntity;
import play.xponer.astronima.block.entity.OreCrusherBlockEntity;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.block.entity.ElectrolysisCellBlockEntity;
import play.xponer.astronima.block.entity.SlsPrinterBlockEntity;
import play.xponer.astronima.sim.metal.Carbonyl;
import play.xponer.astronima.sim.metal.ColdWorking;
import play.xponer.astronima.sim.metal.LaserSintering;
import play.xponer.astronima.sim.ore.ElectrolysisSpecies;
import play.xponer.astronima.sim.ore.MoltenElectrolysis;
import play.xponer.astronima.sim.ore.OreBody;
import play.xponer.astronima.block.entity.SolarRetortBlockEntity;
import play.xponer.astronima.sim.ore.Calcination;
import play.xponer.astronima.sim.ore.ChlorateDecomposition;
import play.xponer.astronima.sim.ore.Dehydroxylation;
import play.xponer.astronima.sim.ore.Elutriation;
import play.xponer.astronima.sim.ore.RetortProcess;
import play.xponer.astronima.sim.ore.SolarConcentrator;
import play.xponer.astronima.sim.ore.Comminution;
import play.xponer.astronima.sim.ore.OreGrade;
import play.xponer.astronima.block.entity.PolymerizerBlockEntity;
import play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity;
import play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.organic.Cracking;
import play.xponer.astronima.sim.ore.TroiliteRoasting;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of a machine's JEI entry, as a plotted curve rather than a table.
 *
 * <p>Two earlier versions of these pages failed for the same underlying reason. The
 * first showed a single setting, which taught nothing because the mechanic <em>is</em>
 * the relationship between settings. The second showed a table of five settings, which
 * contained the whole relationship and still made the reader do the plotting in their
 * head.
 *
 * <p>Every one of these machines has one control with two consequences that move in
 * opposite directions. That is a fact about a <em>shape</em>: two lines crossing, or
 * one peaking while the other keeps climbing. Drawn, it is obvious in a second; written
 * out as percentages, it is arithmetic.
 *
 * <p>Everything is computed from the same model the machines run, so the pages cannot
 * drift from the game.
 */
public record ProcessingRecipe(ItemStack input, List<ItemStack> outputs,
                               String title, String axisLabel,
                               List<Chart.Series> series, List<String> notes,
                               List<ItemStack> extraInputs) {

    /**
     * Every existing page has exactly one real input — {@code extraInputs} exists for the one
     * that does not (design/iron-smelter.md), and this constructor is what keeps every one of
     * those existing call sites untouched rather than threading an empty list through all of
     * them by hand.
     */
    public ProcessingRecipe(ItemStack input, List<ItemStack> outputs, String title,
                            String axisLabel, List<Chart.Series> series, List<String> notes) {
        this(input, outputs, title, axisLabel, series, notes, List.of());
    }

    /** Samples across a control. Enough to show a curve's shape without noise. */
    private static final int SAMPLES = 13;

    // ------------------------------------------------------------------- crushing

    public static List<ProcessingRecipe> crushing() {
        List<ProcessingRecipe> pages = new ArrayList<>();
        for (OreGrade grade : OreGrade.values()) {
            pages.add(crushPage(grade));
        }
        pages.add(ilmenitePage());
        return pages;
    }

    /**
     * Crushing ilmenite, whose page answers a different question than the ore grades' do: not
     * "how much metal does the grind free" but "what grain size does the grind make", because that
     * grain size is what the fluidized bed's spin window slides on. Without this page a player who
     * looks up ilmenite ore finds only prose and no picture of where it goes.
     */
    private static ProcessingRecipe ilmenitePage() {
        List<Float> grain = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            double setting = i / (double) (SAMPLES - 1);
            // Grain size normalised to the coarsest the crusher makes, so the curve reads 1..0.
            grain.add((float) (Comminution.particleSizeMicrons(setting) / Comminution.COARSEST_MICRONS));
        }
        ItemStack out = new ItemStack(ModItems.CRUSHED_ILMENITE.get());
        out.set(ModDataComponents.GRIND_FINENESS.get(),
                play.xponer.astronima.menu.ProcessingMenu.SETTING_SCALE / 2);

        return new ProcessingRecipe(new ItemStack(ModBlocks.ILMENITE_ORE.get()), List.of(out),
                "Ilmenite",
                "wide jaw gap  ->  narrow",
                List.of(new Chart.Series("grain size", MachineFrame.ACCENT, grain)),
                List.of("No metal to free - ilmenite is oxide-locked. The jaw gap sets grain SIZE.",
                        "That size sets the fluidized bed's spin window: match the spin to it."));
    }

    /**
     * What closing the jaws buys and what it costs, on one pair of axes.
     *
     * <p>Liberation climbing and work climbing with it is the entire crusher: the
     * lines never cross, so there is no "correct" gap — only how much cranking a given
     * amount of freed mineral is worth to you.
     */
    private static ProcessingRecipe crushPage(OreGrade grade) {
        List<Float> liberation = new ArrayList<>();
        List<Float> work = new ArrayList<>();
        int maxWork = workAt(1.0);

        for (int i = 0; i < SAMPLES; i++) {
            double setting = i / (double) (SAMPLES - 1);
            liberation.add((float) Comminution.liberation(
                    Comminution.particleSizeMicrons(setting)));
            work.add(workAt(setting) / (float) maxWork);
        }

        return new ProcessingRecipe(oreItem(grade), List.of(crushed(grade, 0.5)),
                grade.displayName(),
                "wide jaw gap  ->  narrow",
                List.of(new Chart.Series("frees", MachineFrame.GOOD, liberation),
                        new Chart.Series("work", MachineFrame.BAD, work)),
                List.of("Narrowing the jaws frees more mineral and costs more "
                                + "cranking per batch.",
                        "Feeds the separator."));
    }

    private static int workAt(double setting) {
        return OreCrusherBlockEntity.BASE_WORK + (int) Math.round(
                Comminution.workPerKilogram(Comminution.particleSizeMicrons(setting)) * 45);
    }

    // ----------------------------------------------------------------- separation

    public static List<ProcessingRecipe> separation() {
        List<ProcessingRecipe> pages = new ArrayList<>();
        for (OreGrade grade : OreGrade.values()) {
            pages.add(separatePage(grade));
        }
        return pages;
    }

    /**
     * The recovery-versus-grade curve, which is the whole tier in one picture.
     *
     * <p>Caught climbing all the way while clean <em>peaks and falls</em> is the thing
     * worth understanding, and it is the reason grinding to dust is a mistake. No
     * amount of prose does that as quickly as seeing one line turn over while the
     * other does not.
     */
    private static ProcessingRecipe separatePage(OreGrade grade) {
        List<Float> caught = new ArrayList<>();
        List<Float> clean = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();

        for (int i = 0; i < SAMPLES; i++) {
            double setting = i / (double) (SAMPLES - 1);
            MagneticSeparatorBlockEntity.Split split =
                    MagneticSeparatorBlockEntity.run(crushed(grade, setting), 0.75);
            caught.add((float) split.recovery());
            clean.add((float) split.grade());

            if (i == SAMPLES / 2) {
                if (!split.grains().isEmpty()) {
                    outputs.add(split.grains());
                }
                if (!split.tailings().isEmpty()) {
                    outputs.add(split.tailings());
                }
            }
        }

        return new ProcessingRecipe(crushed(grade, 0.5), outputs,
                grade.displayName(),
                "coarse feed  ->  fine",
                List.of(new Chart.Series("caught", MachineFrame.GOOD, caught),
                        new Chart.Series("clean", MachineFrame.ACCENT, clean)),
                List.of("Finer feed catches more metal, but the cleanest product "
                                + "comes from a mid-range grind.",
                        "Nickel locked in sulfide is never caught."));
    }


    // --------------------------------------------------------------- solar retort

    /**
     * The retort's two charges, each against the dial that runs it.
     *
     * <p>This machine is the only one whose control is a <em>window</em> rather than a
     * direction, so its page has to show a band and not a slope: what a player needs from it is
     * "where do I stop", which is a shape no table conveys. And it needs two pages, because the
     * same dial has two windows — one for water and one for oxygen — with two entirely
     * different ways of being overcooked.
     */
    public static List<ProcessingRecipe> retort() {
        return List.of(retortPage(RetortProcess.DEHYDROXYLATION), retortPage(RetortProcess.CHLORATE),
                retortPage(RetortProcess.CALCINATION));
    }

    private static ProcessingRecipe retortPage(RetortProcess process) {
        List<Float> yield = new ArrayList<>();
        List<Float> spoilt = new ArrayList<>();

        // Across the whole dial, so the window's position on it is the thing you see.
        for (int i = 0; i < SAMPLES; i++) {
            double focus = i / (double) (SAMPLES - 1);
            double temperature = SolarConcentrator.temperatureK(1.0, focus);
            switch (process) {
                case CHLORATE -> {
                    ChlorateDecomposition.Bake bake = ChlorateDecomposition.bake(
                            SolarRetortBlockEntity.CHLORATE_CHARGE_GRAMS, temperature);
                    yield.add((float) Math.clamp(bake.oxygenMoles()
                            / (SolarRetortBlockEntity.CHLORATE_CHARGE_GRAMS
                                    * ChlorateDecomposition.O2_MOL_PER_GRAM), 0, 1));
                    spoilt.add((float) ChlorateDecomposition.chlorineFraction(temperature));
                }
                case CALCINATION -> {
                    double carbonateGrams = SolarRetortBlockEntity.CHARGE_GRAMS
                            * SolarRetortBlockEntity.BAKED_SILICATE_CARBONATE;
                    Calcination.Bake bake = Calcination.bake(carbonateGrams, temperature);
                    yield.add((float) Calcination.conversion(temperature));
                    spoilt.add(bake.decrepitated() ? 1f : 0f);
                }
                case DEHYDROXYLATION -> {
                    Dehydroxylation.Bake bake = Dehydroxylation.bake(
                            SolarRetortBlockEntity.CHARGE_GRAMS,
                            SolarRetortBlockEntity.TAILINGS_HYDRATION, temperature);
                    yield.add((float) Math.clamp(bake.waterGrams()
                            / (SolarRetortBlockEntity.CHARGE_GRAMS
                                    * SolarRetortBlockEntity.TAILINGS_HYDRATION
                                    * Dehydroxylation.WATER_FRACTION_OF_SERPENTINE), 0, 1));
                    spoilt.add(bake.sintered() ? 1f : 0f);
                }
            }
        }

        ItemStack input = new ItemStack(switch (process) {
            case CHLORATE -> ModItems.CHLORATE_POWDER.get();
            case CALCINATION -> ModItems.BAKED_SILICATE.get();
            case DEHYDROXYLATION -> ModItems.TAILINGS.get();
        });
        ItemStack residue = new ItemStack(switch (process) {
            case CHLORATE -> ModItems.MINERAL_SALTS.get();
            case CALCINATION -> ModItems.MAGNESIUM_OXIDE.get();
            case DEHYDROXYLATION -> ModItems.BAKED_SILICATE.get();
        });
        String title = switch (process) {
            case CHLORATE -> "Chlorate -> oxygen";
            case CALCINATION -> "Baked silicate -> magnesium oxide";
            case DEHYDROXYLATION -> "Hydrated rock -> water";
        };
        String yieldLabel = switch (process) {
            case CHLORATE -> "oxygen";
            case CALCINATION -> "MgO";
            case DEHYDROXYLATION -> "water";
        };
        String spoilLabel = switch (process) {
            case CHLORATE -> "chlorine";
            case CALCINATION -> "decrepitated";
            case DEHYDROXYLATION -> "fused";
        };
        List<String> notes = switch (process) {
            case CHLORATE -> List.of(
                    "Oxygen comes off around 500 C and the bed is spent by 500.",
                    "Past 600 C the halogen comes off as chlorine instead - into the"
                            + " room you are standing in.",
                    "The window sits inside the water one, so a dial left where rock"
                            + " bakes dry will gas you.");
            case CALCINATION -> List.of(
                    "MgO comes off around 977 C - right at the edge of what this mirror can"
                            + " reach at all.",
                    "Past 1007 C the charge decrepitates, fracturing and scattering as fines.",
                    "Feed it baked silicate, not raw tailings - dehydroxylation has to run"
                            + " first, the same rock, cooler.");
            case DEHYDROXYLATION -> List.of(
                    "Water is fully driven off before the mirror is half tight.",
                    "Tighter than that fuses the charge and seals the rest in.",
                    "The steam goes into the room next door, not into the machine.",
                    // Reported as "where is the recipe for X" for a different item entirely
                    // (metal_billet) turned up this same shape here on inspection: raw asteroid
                    // rock is a second real feed for this exact process (SolarRetortBlockEntity
                    // .hydrationOf), and this page shows only tailings. Said in a note rather than
                    // charted on its own curve, since the curve above is scaled to tailings' own
                    // hydration fraction and rock's real number is a different, smaller charge.
                    String.format(java.util.Locale.ROOT,
                            "Raw asteroid rock works too, no crushing needed first - its water"
                                    + " fraction is %.0f%% of what tailings carries, so a batch"
                                    + " yields less, not zero.",
                            SolarRetortBlockEntity.ROCK_HYDRATION
                                    / SolarRetortBlockEntity.TAILINGS_HYDRATION * 100));
        };

        return new ProcessingRecipe(input, List.of(residue), title, "spread mirror  ->  tight focus",
                List.of(new Chart.Series(yieldLabel, MachineFrame.GOOD, yield),
                        new Chart.Series(spoilLabel, MachineFrame.BAD, spoilt)),
                notes);
    }

    // ------------------------------------------------------------- winnowing table

    /**
     * The table with no control, whose page has to answer <em>what decides this, then?</em>
     *
     * <p>Two things upstream do: how finely the feed was ground, and whether there is air in
     * the room to carry it. Neither is on the machine, which is exactly why it needs a page —
     * a player looking at the block can see no reason it should work better or worse.
     */
    public static List<ProcessingRecipe> winnowing() {
        List<Float> inAir = new ArrayList<>();
        List<Float> thin = new ArrayList<>();

        // Two lines, because the two upstream decisions are the whole machine: the grind
        // along the axis, and the room's pressure as the pair of curves.
        for (int i = 0; i < SAMPLES; i++) {
            double sized = i / (double) (SAMPLES - 1);
            inAir.add((float) Elutriation.sharpness(sized, 101.0));
            thin.add((float) Elutriation.sharpness(sized, Elutriation.MINIMUM_PRESSURE_KPA * 1.5));
        }

        return List.of(new ProcessingRecipe(new ItemStack(ModItems.CRUSHED_ORE.get()),
                List.of(new ItemStack(ModItems.IRON_NICKEL_GRAINS.get()),
                        new ItemStack(ModItems.TAILINGS.get())),
                "Air classification",
                "coarse feed  ->  evenly sized",
                List.of(new Chart.Series("in cabin air", MachineFrame.GOOD, inAir),
                        new Chart.Series("thin air", MachineFrame.WARN, thin)),
                List.of("No dial: what decides this is the grind, upstream.",
                        "Evenly sized feed separates cleanly; a mixture of sizes does not,"
                                + " because a big light grain falls like a small heavy one.",
                        "Needs air to work at all - useless in vacuum, and weak in a room"
                                + " that is barely pressurised.")));
    }

    // ------------------------------------------------------------ carbonyl refining

    /**
     * The only page in this book whose <em>middle</em> is the dangerous setting.
     *
     * <p>Every other machine here is wrong at one end, or at both. This one is wrong in the
     * gap: warm enough to walk the nickel into the gas and too cool to walk it back out, so it
     * makes the most poisonous substance in the game and never destroys it. Drawn as two curves
     * that do not overlap, the hole between them is the whole lesson and no sentence conveys it
     * as fast.
     */
    public static List<ProcessingRecipe> refining() {
        List<Float> forming = new ArrayList<>();
        List<Float> breaking = new ArrayList<>();

        // Across the dial's whole travel, so the gap sits where the player will see it.
        double low = 293.15;
        double high = Carbonyl.DECOMPOSING_COMPLETE_K + 40;
        for (int i = 0; i < SAMPLES; i++) {
            double t = low + (high - low) * i / (double) (SAMPLES - 1);
            forming.add((float) Carbonyl.formingFraction(t));
            breaking.add((float) Carbonyl.decomposingFraction(t));
        }

        return List.of(new ProcessingRecipe(
                new ItemStack(ModItems.IRON_NICKEL_GRAINS.get()),
                List.of(new ItemStack(ModItems.PURE_NICKEL.get()),
                        new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),
                "Mond process",
                "cold  ->  50 C  ->  the gap  ->  230 C",
                List.of(new Chart.Series("into the gas", MachineFrame.ACCENT, forming),
                        new Chart.Series("back out, pure", MachineFrame.GOOD, breaking)),
                List.of("Carbon monoxide carries the nickel and is handed BACK - it is a tool"
                                + " you keep, not a fuel you burn.",
                        "Iron does not follow, so the two come out apart.",
                        "Between the two curves it forms nickel carbonyl and never destroys"
                                + " it. That gas is lethal at parts per million and its harm"
                                + " is delayed - purge the room, do not wait to feel it.")));
    }

    // --------------------------------------------------------- fluidized-bed reduction

    /**
     * The reactor whose control is a spin and whose lesson is a <em>match</em>.
     *
     * <p>Two curves, one per grind, of how much iron a batch recovers across the drum-speed dial.
     * Each peaks in the middle — too slow and the bed blows out the exhaust, too fast and it packs
     * to the wall — but the peaks sit at <em>different</em> speeds: the fine grind must be spun
     * fast to be pinned, the coarse grind gently. Seeing the two humps offset is the whole
     * machine: there is no best spin, only the one that matches the grind in the slot.
     */
    public static List<ProcessingRecipe> reduction() {
        List<Float> fine = recoveryCurve(30);
        List<Float> coarse = recoveryCurve(500);

        ItemStack feed = new ItemStack(ModItems.CRUSHED_ILMENITE.get());
        feed.set(ModDataComponents.GRIND_FINENESS.get(),
                play.xponer.astronima.menu.ProcessingMenu.SETTING_SCALE / 2);

        return List.of(new ProcessingRecipe(feed,
                List.of(new ItemStack(ModItems.IRON_POWDER.get()),
                        new ItemStack(ModItems.TITANIA.get())),
                "Ilmenite reduction",
                "slow drum  ->  fast",
                List.of(new Chart.Series("fine grind", MachineFrame.GOOD, fine),
                        new Chart.Series("coarse grind", MachineFrame.ACCENT, coarse)),
                List.of("Spin makes the gravity: too slow blows the bed out, too fast packs it.",
                        "The good spin SLIDES with the grind - fine fast, coarse gentle.",
                        "H2 is spent, not returned; it leaves as water.")));
    }

    /** Iron recovered as a fraction of the charge, across the drum-speed dial, at one grind. */
    private static List<Float> recoveryCurve(double grindMicrons) {
        List<Float> recovery = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            double setting = i / (double) (SAMPLES - 1);
            double rpm = play.xponer.astronima.block.entity.FluidizedBedBlockEntity.rpmFor(setting);
            recovery.add((float) recoveredFraction(grindMicrons, rpm));
        }
        return recovery;
    }

    /** Runs the real two-call machine path to a settled charge, exactly as the block drives it. */
    private static double recoveredFraction(double grindMicrons, double rpm) {
        double contact = play.xponer.astronima.sim.metal.CentrifugalBed
                .contactQuality(grindMicrons, rpm);
        double entrainment = play.xponer.astronima.sim.metal.CentrifugalBed
                .entrainmentSeverity(grindMicrons, rpm);
        double charge = play.xponer.astronima.block.entity.FluidizedBedBlockEntity
                .CHARGE_ILMENITE_MOL;
        play.xponer.astronima.sim.metal.IlmeniteReduction.Charge bed =
                play.xponer.astronima.sim.metal.IlmeniteReduction.Charge.ofIlmenite(charge);
        for (int i = 0; i < 2000 && !bed.isReduced(); i++) {
            bed = play.xponer.astronima.sim.metal.IlmeniteReduction
                    .step(bed, 1e9, contact, entrainment, 0.1).charge();
        }
        return charge <= 0 ? 0 : bed.ironMol() / charge;
    }

    // --------------------------------------------------------------- cold forging

    /**
     * The forge does two real jobs, not one, and this used to show only the second — grains
     * going straight to a tool head, the billet in between skipped entirely. Reported as
     * <em>"where is the recipe for a metal billet?"</em>: nowhere, because no page anywhere in
     * JEI named {@code metal_billet} as an input, an output, or anything else — the info note on
     * the item itself was the only trace of it. Four pages now, one per real step per alloy,
     * because that is what {@code ColdForgeBlockEntity} actually does: {@link #pressPage} is
     * {@code consolidate()}, {@link #forgePage} is {@code strikeBillet()}.
     */
    public static List<ProcessingRecipe> forging() {
        return List.of(
                pressPage(ColdWorking.BASE_NICKEL, "Press: chondritic metal",
                        ModItems.IRON_NICKEL_GRAINS.get()),
                pressPage(ColdWorking.SEAM_NICKEL, "Press: seam metal (high nickel)",
                        ModItems.PURE_NICKEL.get()),
                forgePage(ColdWorking.BASE_NICKEL, "Forge: chondritic metal"),
                forgePage(ColdWorking.SEAM_NICKEL, "Forge: seam metal (high nickel)"));
    }

    /**
     * The forge's first, easy-to-miss job: no heat, no dial, just a vacuum and enough grains.
     * What is pressed decides what the billet is, which is the whole payoff of the Mond process
     * one bench over — not "purer is better", but a player finally choosing the alloy.
     */
    private static ProcessingRecipe pressPage(double nickel, String title,
                                              net.minecraft.world.item.Item feed) {
        ItemStack billet = new ItemStack(ModItems.METAL_BILLET.get());
        billet.set(ModDataComponents.METAL_NICKEL.get(), (float) nickel);

        return new ProcessingRecipe(
                new ItemStack(feed,
                        play.xponer.astronima.block.entity.ColdForgeBlockEntity.GRAINS_PER_BILLET),
                List.of(billet), title,
                "no dial - needs a vacuum, not heat", List.of(),
                List.of("Clean metal surfaces with no oxide film simply bond - the same hazard"
                                + " that welds spacecraft parts together by accident, used on"
                                + " purpose here.",
                        "The chamber is pumped, not the room: the forge evacuates itself, so this"
                                + " runs in a breathable habitat.",
                        "What is pressed decides what the billet is - refined nickel presses into"
                                + " a seam-grade billet, which hardens faster and spends its give"
                                + " faster for it."));
    }

    /**
     * Work hardening across a run of blows: hardness up, ductility down, and the point
     * where they meet is where the piece cracks.
     *
     * <p>This is the clearest case for plotting rather than tabulating. The lesson is
     * that the two lines converge and that you must stop before they do — which is a
     * picture, not a number.
     */
    private static ProcessingRecipe forgePage(double nickel, String title) {
        List<Float> hardness = new ArrayList<>();
        List<Float> give = new ArrayList<>();

        ColdWorking.Piece piece = ColdWorking.Piece.fresh();
        for (int blow = 0; blow < 24 && !piece.cracked(); blow++) {
            hardness.add((float) piece.hardness());
            give.add((float) piece.ductility());
            piece = ColdWorking.strike(piece, 0.5, nickel);
        }

        ItemStack billet = new ItemStack(ModItems.METAL_BILLET.get());
        billet.set(ModDataComponents.METAL_NICKEL.get(), (float) nickel);

        ItemStack head = new ItemStack(ModItems.TOOL_HEAD.get());
        head.set(ModDataComponents.METAL_NICKEL.get(), (float) nickel);
        head.set(ModDataComponents.METAL_QUALITY.get(), 0.8f);

        return new ProcessingRecipe(billet, List.of(head),
                title,
                "blows  ->",
                List.of(new Chart.Series("hard", MachineFrame.GOOD, hardness),
                        new Chart.Series("give", MachineFrame.WARN, give)),
                List.of("Every blow hardens the metal and spends its give.",
                        "There is no annealing out here, so when the give runs out the "
                                + "piece cracks. Finish before the lines meet."));
    }

    // ------------------------------------------------------------------- electrolysis

    /**
     * One page per electrode, because the cell has no dial for a curve to sweep — what it
     * reaches is a discrete choice made at the workbench, not a continuous one made on the
     * panel. See {@code design/electrolysis.md} §2: an earlier draft of this design gated
     * species behind power and read as a curve; this one does not, on purpose.
     */
    public static List<ProcessingRecipe> electrolysis() {
        List<ProcessingRecipe> pages = new ArrayList<>();
        for (ElectrolysisSpecies species : ElectrolysisSpecies.values()) {
            pages.add(electrolysisPage(species));
        }
        return pages;
    }

    private static ProcessingRecipe electrolysisPage(ElectrolysisSpecies species) {
        OreBody body = OreBody.chondrite(ElectrolysisCellBlockEntity.CHARGE_GRAMS);
        MoltenElectrolysis.Charge charge = MoltenElectrolysis.Charge.of(body, species);
        MoltenElectrolysis.Step whole = MoltenElectrolysis.step(charge, 1.0);
        int items = (int) Math.floor(whole.metalMol() / ElectrolysisCellBlockEntity.MOL_PER_ITEM);

        ItemStack input = new ItemStack(ModItems.TAILINGS.get());
        ItemStack metal = new ItemStack(electrolysisMetal(species), Math.max(items, 1));

        String electrode = switch (species) {
            case IRON -> "none — the cell's own default";
            case SILICON -> new ItemStack(ModItems.SILICON_ELECTRODE.get())
                    .getHoverName().getString() + ", installed";
            case ALUMINUM -> new ItemStack(ModItems.ALUMINUM_ELECTRODE.get())
                    .getHoverName().getString() + ", installed";
        };
        String title = switch (species) {
            case IRON -> "Iron (default)";
            case SILICON -> "Silicon";
            case ALUMINUM -> "Aluminium";
        };

        // A flat line rather than a swept curve — there is nothing to sweep - but the chart
        // still carries the one number worth comparing across pages at a glance: how large a
        // share of the melt this target actually recovers.
        float share = (float) species.yieldFraction();
        return new ProcessingRecipe(input, List.of(metal), title,
                "no dial — set by the electrode",
                List.of(new Chart.Series("share of the melt", MachineFrame.ACCENT,
                        List.of(share, share))),
                List.of("Electrode: " + electrode + ".",
                        "One batch (" + Math.round(ElectrolysisCellBlockEntity.CHARGE_GRAMS)
                                + " g of tailings) yields up to " + items + " and "
                                + Math.round(whole.o2Mol()) + " mol of oxygen, straight into"
                                + " the sealed room.",
                        "Power changes only how fast a batch runs, never which metal comes"
                                + " out — wire in more panels, not a different electrode."));
    }

    private static net.minecraft.world.item.Item electrolysisMetal(ElectrolysisSpecies species) {
        return switch (species) {
            case IRON -> net.minecraft.world.item.Items.IRON_INGOT;
            case SILICON -> ModItems.SILICON.get();
            case ALUMINUM -> ModItems.ALUMINUM.get();
        };
    }

    // ------------------------------------------------------------------- sls printer

    /**
     * Three named regions of the process plane, each a real curve — soundness against scan
     * speed at one fixed power — because unlike the electrolysis cell's discrete choice, this
     * machine's output genuinely varies continuously. Three fixed powers rather than the whole
     * plane, because JEI shows what a player would find exploring it, not the model's own grid.
     */
    public static List<ProcessingRecipe> sls() {
        return List.of(
                slsPage("In the sound pocket", SlsPrinterBlockEntity.DEFAULT_POWER_W,
                        "Matched power and speed: dense, sound parts across most of the speed"
                                + " range - until scanning too fast balls the track anyway."),
                slsPage("Underpowered", LaserSintering.MIN_POWER_W + 10,
                        "Not enough energy at any speed on this line: the powder never fully"
                                + " melts, so every part here is porous with unmelted voids."),
                slsPage("Overpowered", LaserSintering.MAX_POWER_W - 50,
                        "Too much energy: the melt pool vaporises into keyhole porosity before"
                                + " speed even enters into it."));
    }

    private static ProcessingRecipe slsPage(String title, double powerW, String note) {
        ItemStack input = new ItemStack(ModItems.IRON_POWDER.get(), SlsPrinterBlockEntity.FEED_PER_BATCH);
        double sampleSpeed = 400.0;
        ItemStack part = new ItemStack(ModItems.SINTERED_FRAME.get());
        part.set(ModDataComponents.SINTER_SOUNDNESS.get(),
                (float) LaserSintering.soundness(powerW, sampleSpeed));

        List<Float> soundness = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            double speed = LaserSintering.MIN_SPEED_MMS + i * (LaserSintering.MAX_SPEED_MMS
                    - LaserSintering.MIN_SPEED_MMS) / (SAMPLES - 1);
            soundness.add((float) LaserSintering.soundness(powerW, speed));
        }

        return new ProcessingRecipe(input, List.of(part), title,
                "scan speed, " + Math.round(powerW) + " W laser",
                List.of(new Chart.Series("part soundness", MachineFrame.ACCENT, soundness)),
                List.of(note,
                        "Both dials are dragged together on the printer's own plane - this page"
                                + " fixes the power to show what one line across it looks like.",
                        "Grid power only changes how many batches a minute this printer gets"
                                + " through, never which part a given (power, speed) prints."));
    }

    // ------------------------------------------------------------------- cracking tower

    public static List<ProcessingRecipe> crackingTower() {
        return List.of(
                crackingPage(ModItems.THOLIN_CLUMP.get(), "Tholins"),
                crackingPage(ModItems.SLUDGE.get(), "Sludge"));
    }

    private static ProcessingRecipe crackingPage(net.minecraft.world.item.Item feed, String title) {
        Cracking.Yield yield = Cracking.crack(Cracking.CHARGE_GRAMS);
        double ethyleneG = yield.ethyleneMol() * Gas.ETHYLENE.molarMassKgPerMol() * 1000.0;
        double methaneG = yield.methaneMol() * Gas.METHANE.molarMassKgPerMol() * 1000.0;
        // No item outputs at all — the whole charge leaves as gas, vented into the room this
        // sits in. An empty chart, honestly: there is no dial here to sweep.
        return new ProcessingRecipe(new ItemStack(feed), List.of(), title,
                "no dial — cracks whatever is fed", List.of(),
                List.of("Vents " + Math.round(ethyleneG) + " g ethylene and "
                                + Math.round(methaneG) + " g methane into the sealed room this"
                                + " sits in, per charge.",
                        "No solid residue is tracked — the whole charge leaves as gas.",
                        "Needs a sealed room to vent into, or the batch is held rather than"
                                + " lost."));
    }

    // ------------------------------------------------------------------- polymerizer

    public static List<ProcessingRecipe> polymerizer() {
        ItemStack input = new ItemStack(ModItems.TITANIA.get(),
                PolymerizerBlockEntity.TITANIA_PER_BATCH);
        ItemStack output = new ItemStack(ModItems.POLYETHYLENE.get());
        return List.of(new ProcessingRecipe(input, List.of(output), "Addition polymerization",
                "no dial — strung from the room's ethylene", List.of(),
                List.of("Titania is the catalyst here (real Ziegler-Natta chemistry) — a small"
                                + " amount spent per batch, not a bulk reagent.",
                        "Needs " + PolymerizerBlockEntity.ETHYLENE_PER_BATCH_MOL
                                + " mol of ethylene in the room per batch — pipe it in from a"
                                + " cracking tower.")));
    }

    // ------------------------------------------------------------------- water electrolyzer

    public static List<ProcessingRecipe> waterElectrolyzer() {
        ItemStack input = net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER);
        return List.of(new ProcessingRecipe(input, List.of(), "Electrolysis",
                "no dial — splits whatever is fed", List.of(),
                List.of("Vents "
                                + Math.round(play.xponer.astronima.block.entity
                                        .WaterElectrolyzerBlockEntity.H2_PER_BATCH_MOL)
                                + " mol hydrogen and "
                                + Math.round(play.xponer.astronima.block.entity
                                        .WaterElectrolyzerBlockEntity.O2_PER_BATCH_MOL)
                                + " mol oxygen into the sealed room this sits in, per bottle — a"
                                + " real 2:1 ratio.",
                        "The hydrogen is the Sabatier reactor's own reagent: build the two"
                                + " machines in one sealed room and the loop closes.",
                        "Needs a sealed room to vent into, or the batch is held rather than"
                                + " lost.")));
    }

    // ------------------------------------------------------------------- Sabatier reactor

    public static List<ProcessingRecipe> sabatierReactor() {
        ItemStack input = new ItemStack(ModItems.PURE_NICKEL.get(),
                play.xponer.astronima.block.entity.SabatierReactorBlockEntity.NICKEL_PER_BATCH);
        return List.of(new ProcessingRecipe(input, List.of(), "The Sabatier Reaction",
                "no dial — reacts the room's own CO2 and H2", List.of(),
                List.of("Nickel is the catalyst here (a token amount per batch, the same"
                                + " simplification the polymerizer's titania already carries), not"
                                + " a bulk reagent.",
                        "Needs "
                                + play.xponer.astronima.block.entity.SabatierReactorBlockEntity
                                        .CO2_PER_BATCH_MOL
                                + " mol CO2 and "
                                + play.xponer.astronima.block.entity.SabatierReactorBlockEntity
                                        .H2_PER_BATCH_MOL
                                + " mol H2 in the room together — real Sabatier stoichiometry is"
                                + " 1:4.",
                        "Makes methane (feed a combustion generator) and water vapor (feed a"
                                + " dehumidifier) — nothing in the loop is wasted.")));
    }

    // ------------------------------------------------------------------- Bosch reactor

    public static List<ProcessingRecipe> boschReactor() {
        ItemStack input = new ItemStack(ModItems.IRON_POWDER.get(),
                play.xponer.astronima.block.entity.BoschReactorBlockEntity.IRON_PER_BATCH);
        ItemStack output = new ItemStack(ModItems.CARBON_POWDER.get(),
                (int) play.xponer.astronima.block.entity.BoschReactorBlockEntity.CARBON_PER_BATCH_MOL);
        return List.of(new ProcessingRecipe(input, List.of(output), "The Bosch Reaction",
                "no dial — reacts the room's own CO2 and H2", List.of(),
                List.of("The real alternative to the Sabatier reactor one bench over: same room"
                                + " CO2 and H2, an iron catalyst instead of nickel, and the carbon"
                                + " stays solid instead of becoming methane.",
                        "Needs only "
                                + play.xponer.astronima.block.entity.BoschReactorBlockEntity
                                        .H2_PER_BATCH_MOL
                                + " mol H2 per "
                                + play.xponer.astronima.block.entity.BoschReactorBlockEntity
                                        .CO2_PER_BATCH_MOL
                                + " mol CO2 - half what Sabatier needs for the same CO2.",
                        "Carbon has no recipe that consumes it yet - real steelmaking feedstock,"
                                + " kept for whichever tier finally alloys it into iron.")));
    }

    // ------------------------------------------------------------------- Troilite roaster

    public static List<ProcessingRecipe> troiliteRoaster() {
        ItemStack input = crushed(OreGrade.CHONDRITE, 0.5);
        TroiliteRoasting.Step step = TroiliteRoasting.roast(
                OreGrade.CHONDRITE.body(TroiliteRoasterBlockEntity.CHARGE_GRAMS));
        ItemStack output = new ItemStack(ModBlocks.HEMATITE_ORE.get(),
                Math.max(1, (int) Math.floor(step.fe2O3Mol())));
        return List.of(new ProcessingRecipe(input, List.of(output), "Troilite Roasting",
                "no dial — roasts whatever troilite the fed ore carries", List.of(),
                List.of("Real and balanced: 4 FeS + 7 O2 -> 2 Fe2O3 + 4 SO2 — the room's own"
                                + " oxygen is genuinely spent, not a token cost.",
                        "The hematite smelts to iron like any other ore — same oxide, no new"
                                + " downstream item needed.",
                        "SO2 vents into the room this sits in, real and dangerous — don't roast"
                                + " in a sealed room without a scrubber.")));
    }

    // ------------------------------------------------------------------- Sulfuric acid plant

    public static List<ProcessingRecipe> sulfuricAcidPlant() {
        ItemStack input = new ItemStack(ModBlocks.HEMATITE_ORE.get(),
                SulfuricAcidPlantBlockEntity.HEMATITE_PER_BATCH);
        ItemStack output = new ItemStack(ModItems.SULFURIC_ACID.get(),
                (int) SulfuricAcidPlantBlockEntity.ACID_PER_BATCH_MOL);
        return List.of(new ProcessingRecipe(input, List.of(output), "The Contact Process",
                "no dial — reacts the room's own SO2, O2 and water vapor", List.of(),
                List.of("The real industrial route to sulfuric acid, folded into one net line: "
                                + "2 SO2 + O2 + 2 H2O -> 2 H2SO4.",
                        "Real platinum or vanadium(V) oxide is the industrial catalyst; neither"
                                + " exists as a mineral in this mod yet, so hematite — the"
                                + " roaster's own product — stands in honestly instead.",
                        "Build a troilite roaster, a Sabatier or Bosch reactor, and this plant in"
                                + " the same sealed room and every reagent it needs is already"
                                + " being produced there.")));
    }

    // ------------------------------------------------------------------- heavy water cell

    public static List<ProcessingRecipe> heavyWaterCell() {
        ItemStack bottle = net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER);
        double afterOne = play.xponer.astronima.sim.chem.HeavyWaterCascade.fractionAfterStages(
                play.xponer.astronima.sim.chem.HeavyWaterCascade.NATURAL_D2O_FRACTION, 1);
        return List.of(new ProcessingRecipe(bottle, List.of(bottle), "Heavy Water Cell",
                "no dial — one electrolytic cascade stage per batch", List.of(),
                List.of("Electrolysis breaks O-H bonds faster than O-D bonds, so the water left"
                                + " behind after a batch is measurably richer in D2O than what went"
                                + " in — the real mechanism, not a metaphor.",
                        "One water bottle in, the same bottle out, its own D2O fraction advanced"
                                + " by a real separation factor of "
                                + play.xponer.astronima.sim.chem.HeavyWaterCascade
                                        .ELECTROLYTIC_SEPARATION_FACTOR
                                + "x. Natural ice-melt starts at 0.0156% D2O; one stage reaches"
                                + String.format(java.util.Locale.ROOT, " %.4f%%.", afterOne * 100),
                        "Feed a cell's own output back into itself to run the next stage — eight"
                                + " real passes cross 99.5% D2O, the fuel v0.9's own reactor will"
                                + " want.",
                        "A real cascade also loses about four-fifths of its water volume every"
                                + " stage; this machine keeps the isotope maths honest but not that"
                                + " part — see design/heavy-water.md §3.")));
    }

    // ------------------------------------------------------------------- freeze dryer

    public static List<ProcessingRecipe> freezeDryer() {
        ItemStack silicate = new ItemStack(ModItems.BAKED_SILICATE.get());
        ItemStack aerogel = new ItemStack(ModItems.SILICA_AEROGEL.get());
        return List.of(new ProcessingRecipe(silicate, List.of(aerogel), "Freeze Dryer",
                "no dial — needs a dewar of LN2 and real vacuum", List.of(),
                List.of("Rehydrated baked silicate, frozen against an adjacent dewar's liquid"
                                + " nitrogen, then held in real vacuum: its pore water sublimes"
                                + " straight from solid to vapor, skipping the liquid phase that"
                                + " would otherwise collapse the gel's pores under surface"
                                + " tension.",
                        "The real alternative to supercritical CO2 drying (the classic Kistler"
                                + " process) — freeze-drying is a published, industrially-used"
                                + " aerogel route, chosen because it needs nothing this mod does"
                                + " not already have.",
                        "Must sit outside a sealed room, bolted to a cryo tank holding LN2, or it"
                                + " will not progress at all — the panel names both when it is not"
                                + " ready.")));
    }

    // ------------------------------------------------------------------- titanium cell

    public static List<ProcessingRecipe> titaniumCell() {
        ItemStack input = new ItemStack(ModItems.TITANIA.get());
        ItemStack output = new ItemStack(ModItems.TITANIUM.get(),
                play.xponer.astronima.sim.ore.TitaniumReduction.titaniumItemsPerCharge());
        return List.of(new ProcessingRecipe(input, List.of(output), "Titanium Cell",
                "no dial — reduces whatever titania it is fed", List.of(),
                List.of("The FFC-Cambridge process: titania stays solid, as a cathode in a bath"
                                + " of molten CaCl2 at ~900C. Current pulls the oxide straight out"
                                + " of the lattice; it leaves at a carbon anode as CO/CO2.",
                        "Real decomposition potential: "
                                + String.format(java.util.Locale.ROOT, "%.2f",
                                        play.xponer.astronima.sim.ore.TitaniumReduction
                                                .DECOMPOSITION_VOLTS)
                                + " V - notably lower than aluminium's own ~2.73 V. Titanium's"
                                + " real difficulty is slow solid-state diffusion, not raw"
                                + " voltage, which is why this machine is slow rather than merely"
                                + " power-hungry.",
                        "No separate sponge-to-ingot step: the same simplification the carbonyl"
                                + " refiner already makes for the Mond process. This closes the"
                                + " vacuum-arc-furnace deferral outright rather than leaving a"
                                + " second machine pending.")));
    }

    // ------------------------------------------------------------------- induction furnace

    public static List<ProcessingRecipe> inductionFurnace() {
        ItemStack input = new ItemStack(ModItems.IRON_POWDER.get());
        ItemStack output = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
        double kWhPerKg = play.xponer.astronima.sim.metal.InductionMelting.ironEnergyJoules(1.0)
                / 3_600_000.0;
        return List.of(new ProcessingRecipe(input, List.of(output), "Induction Furnace",
                "no dial — melts whatever iron powder it is fed", List.of(),
                List.of("A vanilla furnace already smelts iron powder here, and"
                                + " AbstractFurnaceBlockEntityMixin already makes it draw real"
                                + " room oxygen and exhale CO2 while lit. This machine is the"
                                + " same conversion with none of that: nothing here ever touches"
                                + " the room's own air.",
                        "Real thermodynamics: sensible heat to reach iron's 1811 K melting point,"
                                + " plus its real latent heat of fusion — about "
                                + String.format(java.util.Locale.ROOT, "%.2f", kWhPerKg)
                                + " kWh per kg, an order of magnitude below the titanium cell's"
                                + " own real ~30 kWh/kg, since melting already-reduced metal is a"
                                + " far smaller job than reducing an oxide.",
                        "No new item: the ingot it produces is exactly as reachable as it always"
                                + " was through the vanilla recipe - this is a second, air-free"
                                + " path to it, not a new destination.")));
    }

    // ------------------------------------------------------------------- iron smelter

    /**
     * The one page with a real second input slot: {@code design/iron-smelter.md}'s whole point
     * is that flux is a real item in a real slot, not a room reagent described only in prose the
     * way every other two-reagent machine's second ingredient already is.
     */
    public static List<ProcessingRecipe> ironSmelter() {
        ItemStack ore = new ItemStack(ModBlocks.HEMATITE_ORE.get());
        ItemStack flux = new ItemStack(ModItems.MAGNESIUM_OXIDE.get());
        double fullOre = play.xponer.astronima.block.entity.IronSmelterBlockEntity.ORE_GRAMS_PER_ITEM;
        double fullFlux = play.xponer.astronima.block.entity.IronSmelterBlockEntity.FLUX_GRAMS_PER_ITEM;
        play.xponer.astronima.sim.metal.Fluxing.Batch fullBatch =
                play.xponer.astronima.sim.metal.Fluxing.smelt(fullOre, fullFlux);
        ItemStack iron = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT,
                Math.max(1, (int) Math.floor(fullBatch.ironGrams()
                        / play.xponer.astronima.block.entity.IronSmelterBlockEntity.IRON_GRAMS_PER_INGOT)));
        ItemStack slag = new ItemStack(ModItems.SLAG.get(),
                Math.max(1, (int) Math.floor(fullBatch.slagGrams()
                        / play.xponer.astronima.block.entity.IronSmelterBlockEntity.SLAG_GRAMS_PER_ITEM)));

        List<Float> ironYield = new ArrayList<>();
        List<Float> slagYield = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            double suppliedFlux = fullFlux * i / (double) (SAMPLES - 1);
            play.xponer.astronima.sim.metal.Fluxing.Batch batch =
                    play.xponer.astronima.sim.metal.Fluxing.smelt(fullOre, suppliedFlux);
            ironYield.add((float) (batch.ironGrams() / (fullOre
                    * play.xponer.astronima.sim.metal.Fluxing.IRON_FRACTION_OF_HEMATITE)));
            slagYield.add((float) batch.fluxRatio());
        }

        return List.of(new ProcessingRecipe(ore, List.of(iron, slag), "Iron Smelter",
                "no flux  ->  full stoichiometric flux",
                List.of(new Chart.Series("iron yield", MachineFrame.GOOD, ironYield),
                        new Chart.Series("flux used", MachineFrame.WARN, slagYield)),
                List.of("Real fluxing and slagging at last: MgO reacts with hematite's own real"
                                + " silicate gangue (MgO + SiO2 -> MgSiO3), freeing the iron it was"
                                + " locked to and leaving real slag behind instead of contaminating"
                                + " the batch.",
                        "No flux still yields real, crude iron - about "
                                + String.format(java.util.Locale.ROOT, "%.0f",
                                        play.xponer.astronima.sim.metal.Fluxing.UNFLUXED_IRON_FRACTION * 100)
                                + "% of a fully-fluxed batch, the same way a real bloomery smelted"
                                + " iron for millennia before anyone added flux on purpose.",
                        "One magnesium oxide item already covers one hematite ore item's own real"
                                + " stoichiometric need with room to spare - more than that per ore"
                                + " item does not react into more slag."),
                List.of(flux)));
    }

    // ------------------------------------------------------------------- Downs cell

    /**
     * The Downs process, real and unglamorous: rock salt melted and electrolyzed at the highest
     * voltage anything in this mod needs (design/halogens.md §1.2 — sodium's own
     * electropositivity is exactly what makes it both hard to reduce and violently reactive once
     * freed). No dial, no room reagent — one ore item is one fixed charge, split whole.
     */
    public static List<ProcessingRecipe> downsCell() {
        double haliteGrams = play.xponer.astronima.block.entity.DownsCellBlockEntity
                .HALITE_GRAMS_PER_ITEM;
        play.xponer.astronima.sim.chem.HaliteElectrolysis.Charge charge =
                play.xponer.astronima.sim.chem.HaliteElectrolysis.Charge.of(haliteGrams);
        play.xponer.astronima.sim.chem.HaliteElectrolysis.Step whole =
                play.xponer.astronima.sim.chem.HaliteElectrolysis.step(charge, 1.0);

        ItemStack ore = new ItemStack(ModBlocks.HALITE_ORE.get());
        ItemStack sodium = new ItemStack(ModItems.SODIUM.get(),
                Math.max(1, (int) Math.floor(whole.sodiumMol()
                        / play.xponer.astronima.item.SodiumItem.MOL_PER_ITEM)));

        return List.of(new ProcessingRecipe(ore, List.of(sodium), "The Downs Process",
                "no dial - melts and electrolyzes whatever rock salt it is fed", List.of(),
                List.of("Real and industrial: 2 NaCl (l) -> 2 Na (l) + Cl2 (g) - still how sodium"
                                + " metal is made today.",
                        "The highest cell voltage anything here needs (4.07 V theoretical) - the"
                                + " same electropositivity that makes sodium hard to reduce also"
                                + " makes it violently reactive once it is.",
                        "One batch (" + Math.round(haliteGrams) + " g of rock salt) yields "
                                + sodium.getCount() + " sodium and "
                                + String.format(java.util.Locale.ROOT, "%.1f", whole.cl2Mol())
                                + " mol of chlorine, straight into the sealed room this sits in -"
                                + " real and dangerous, the same gas the solar retort's own"
                                + " chlorate bed already warns about.",
                        "Sodium reacts with water for real: right-click it against water and"
                                + " 2 Na + 2 H2O -> 2 NaOH + H2 actually runs, hydrogen and all -"
                                + " it is not a decoration.")));
    }

    // ------------------------------------------------------------------- zone refiner

    /**
     * Zone refining, real and un-glamorous the same way the Downs cell is: a molten zone dragged
     * along electrolytic silicon rejects real metallic contamination into the melt it leaves
     * behind (design/halogens.md §9 - Trumbore 1960's own real segregation coefficient for iron
     * in silicon, k = 8x10^-6). No dial, no room reagent - ten silicon items are one fixed batch.
     */
    public static List<ProcessingRecipe> zoneRefiner() {
        int feed = play.xponer.astronima.block.entity.ZoneRefinerBlockEntity.FEED_PER_BATCH;
        int yield = play.xponer.astronima.sim.ore.ZoneRefining.wafersPerBatch(feed);

        ItemStack silicon = new ItemStack(ModItems.SILICON.get(), feed);
        ItemStack wafers = new ItemStack(ModItems.WAFER_SILICON.get(), yield);

        return List.of(new ProcessingRecipe(silicon, List.of(wafers), "Zone Refiner",
                "no dial - purifies whatever silicon it is fed", List.of(),
                List.of("Real segregation, not a reaction: impurities prefer the melt over the"
                                + " freshly-solidified crystal behind it. Iron's own real"
                                + " segregation coefficient in silicon is about "
                                + String.format(java.util.Locale.ROOT, "%.0e",
                                        play.xponer.astronima.sim.ore.ZoneRefining
                                                .SEGREGATION_COEFFICIENT_IRON)
                                + " - so favourable that a single pass purifies essentially the"
                                + " whole rod, which is exactly why this machine has no dial.",
                        "One batch (" + feed + " silicon) yields " + yield + " wafer-grade silicon"
                                + " - the last zone-length's worth of rod is where every pass's"
                                + " rejected impurity ends up, cropped and discarded rather than"
                                + " sold.",
                        "Real dopants like boron segregate far worse (k ~ 0.8) and are not"
                                + " modelled here - nothing in this mod's own silicon ever picks"
                                + " one up in the first place, only the trace metallic"
                                + " contamination it shares a melt with iron reduction.")));
    }

    // ------------------------------------------------------------------- HF digester

    /**
     * Real fluorite digestion (design/halogens.md §15-17, Part C1): {@code CaF2 + H2SO4 ->
     * CaSO4 + 2 HF}, real 1:1 stoichiometry, both reagents mandatory - no crude-without-flux
     * shortcut the way the iron smelter has one.
     */
    public static List<ProcessingRecipe> hfDigester() {
        ItemStack fluorite = new ItemStack(ModBlocks.FLUORITE_ORE.get());
        ItemStack acid = new ItemStack(ModItems.SULFURIC_ACID.get(),
                play.xponer.astronima.block.entity.HfDigesterBlockEntity.SULFURIC_ACID_PER_CHARGE);
        int hfItems = (int) Math.floor(play.xponer.astronima.sim.chem.FluoriteDigestion.hfMolFrom(
                play.xponer.astronima.block.entity.HfDigesterBlockEntity.FLUORITE_MOL_PER_CHARGE));
        int gypsumItems = (int) Math.floor(
                play.xponer.astronima.sim.chem.FluoriteDigestion.gypsumMolFrom(
                play.xponer.astronima.block.entity.HfDigesterBlockEntity.FLUORITE_MOL_PER_CHARGE));
        ItemStack hf = new ItemStack(ModItems.HYDROFLUORIC_ACID.get(), hfItems);
        ItemStack gypsum = new ItemStack(ModItems.GYPSUM.get(), gypsumItems);

        return List.of(new ProcessingRecipe(fluorite, List.of(hf, gypsum), "HF Digester",
                "no dial - both reagents mandatory, real fixed 1:1 stoichiometry", List.of(),
                List.of("The real \"salt cake\" process, still how most of the world's HF is"
                                + " made: fluorite digested by sulfuric acid, this mod's own acid"
                                + " plant giving that item its first real consumer.",
                        "Real net reaction enthalpy: "
                                + String.format(java.util.Locale.ROOT, "%.1f",
                                        play.xponer.astronima.sim.chem.FluoriteDigestion
                                                .ENTHALPY_KJ_PER_MOL_CAF2)
                                + " kJ/mol CaF2 - endothermic, which is why this draws real power"
                                + " rather than reacting for free the moment both reagents are"
                                + " present.",
                        "One batch (1 fluorite, " + acid.getCount() + " sulfuric acid) yields "
                                + hf.getCount() + " hydrofluoric acid and " + gypsum.getCount()
                                + " gypsum - real 1:2:1 CaF2:HF:CaSO4 stoichiometry.",
                        "Real HF is uniquely dangerous - it penetrates skin painlessly and attacks"
                                + " bone beneath it. Handling the bottle by hand (right-click it)"
                                + " starts a real, armor-ignoring chemical-burn dose; this machine's"
                                + " own automatic feed is the safe way to move it (design/"
                                + "halogens.md Part C2)."),
                List.of(acid)));
    }

    /**
     * Real wet oxide etching (design/halogens.md §40-45, Part C4): {@code SiO2 + 6 HF -> H2SiF6 +
     * 2 H2O}, real 1:6:1 stoichiometry, HF hand-loaded only.
     */
    public static List<ProcessingRecipe> etchStation() {
        ItemStack wafer = new ItemStack(ModItems.WAFER_SILICON.get());
        int hfItems = play.xponer.astronima.block.entity.EtchStationBlockEntity.HF_PER_CHARGE;
        ItemStack hf = new ItemStack(ModItems.HYDROFLUORIC_ACID.get(), hfItems);
        int dieItems = (int) Math.floor(play.xponer.astronima.sim.chem.WaferEtching.dieMolFrom(
                play.xponer.astronima.block.entity.EtchStationBlockEntity.WAFER_MOL_PER_CHARGE));
        int acidItems = (int) Math.floor(
                play.xponer.astronima.sim.chem.WaferEtching.fluorosilicicAcidMolFrom(
                play.xponer.astronima.block.entity.EtchStationBlockEntity.WAFER_MOL_PER_CHARGE));
        ItemStack die = new ItemStack(ModItems.ETCHED_DIE.get(), dieItems);
        ItemStack fluorosilicicAcid = new ItemStack(ModItems.FLUOROSILICIC_ACID.get(), acidItems);

        return List.of(new ProcessingRecipe(wafer, List.of(die, fluorosilicicAcid), "Wafer Etching",
                "no dial - HF hand-loaded only, real fixed 1:6 stoichiometry", List.of(),
                List.of("Real wet oxide etching, still the basis of wafer fabrication today: a"
                                + " wafer's own native oxide skin (SiO2), stripped in a"
                                + " photolithographically-patterned window by hydrofluoric acid.",
                        "One batch (1 wafer, " + hf.getCount() + " hydrofluoric acid) yields "
                                + die.getCount() + " etched die and " + fluorosilicicAcid.getCount()
                                + " fluorosilicic acid - real 1:6:1 SiO2:HF:H2SiF6 stoichiometry.",
                        "Gated on a real cleanroom: an adjacent cleanroom controller must certify"
                                + " this room at least "
                                + String.format(java.util.Locale.ROOT, "%.0f%%",
                                        play.xponer.astronima.sim.chem.WaferEtching
                                                .ETCH_MIN_CLEANLINESS * 100)
                                + " clean before this machine will run at all.",
                        "The HF slot cannot be hopper-fed or dragged in - right-click the bottle"
                                + " onto this block to hand-load it. That handling is the same real,"
                                + " armor-ignoring contact event right-clicking the bottle itself"
                                + " triggers (design/halogens.md Part C2) - useful or not, handling"
                                + " concentrated HF is never free.",
                        "This mod's real electronics gate: not yet consumed by anything, honestly -"
                                + " the smart visor and SCADA automation are its real, not-yet-built"
                                + " uses."),
                List.of(hf)));
    }

    // ------------------------------------------------------------------- helpers

    private static ItemStack crushed(OreGrade grade, double setting) {
        ItemStack stack = new ItemStack(ModItems.CRUSHED_ORE.get());
        stack.set(ModDataComponents.ORE_BATCH.get(), OreGrade.pack(grade, setting));
        return stack;
    }

    private static ItemStack oreItem(OreGrade grade) {
        return new ItemStack(grade == OreGrade.METAL_RICH
                ? ModBlocks.METAL_RICH_ORE.get() : ModBlocks.ASTEROID_ROCK.get());
    }
}
