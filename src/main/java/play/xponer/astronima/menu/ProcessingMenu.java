package play.xponer.astronima.menu;

import play.xponer.astronima.client.hud.MachinePanel;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.block.entity.ColdForgeBlockEntity;
import play.xponer.astronima.block.entity.MagneticSeparatorBlockEntity;
import play.xponer.astronima.block.entity.OreCrusherBlockEntity;
import play.xponer.astronima.block.entity.ProcessingBlockEntity;
import play.xponer.astronima.block.entity.SolarRetortBlockEntity;
import play.xponer.astronima.sim.ore.ElectrolysisSpecies;

/**
 * The domain both tier-1-and-beyond processing machines share: what a machine <em>is</em>,
 * independent of the screen it is shown on.
 *
 * <p>Not a Minecraft menu any more — {@code design/ui-ldlib2-machines.md}. The instance side
 * this class used to carry (the synced {@link ContainerData} view, {@code progress()},
 * {@code setting()}, and the rest) moved to {@link ProcessingUiHolder}, which is both the real
 * {@code MenuProvider} every machine block opens and the real
 * {@code IContainerUIHolder} LDLib2 builds a screen from. What is left here is what has no
 * per-open-screen state at all: which {@link Kind} of machine this is, what each one's feed
 * slot accepts, and the synced-data array's own shape — {@code dataFor} and {@code settingOf}
 * both take a {@link ProcessingBlockEntity} as a plain parameter and always have, so neither
 * needed to move.
 *
 * <p>One class for every processing machine because they are the same shape of thing — an
 * input, some outputs, one operating parameter, and live numbers — and a player should not have
 * to relearn the panel for each device. What differs is only which slots exist and what the
 * parameter is called.
 */
public final class ProcessingMenu {
    /** Indices into the synced data array. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_SETTING = 1;
    public static final int DATA_WORK_REQUIRED = 2;
    public static final int DATA_RECOVERY = 3;
    public static final int DATA_GRADE = 4;
    /** How far a forge has pumped its chamber down, 0..1 scaled. */
    public static final int DATA_VACUUM = 5;
    /** Ordinal of the machine's {@link play.xponer.astronima.sim.machine.WorkState}: why the
     *  bar is or is not moving. */
    public static final int DATA_WORK_STATE = 6;
    /** Retort vessel temperature in kelvin — a whole number is fine for a bar. */
    public static final int DATA_TEMPERATURE_K = 7;
    /** Sunlight on the mirror, 0..1 scaled. */
    public static final int DATA_SUNLIGHT = 8;
    /**
     * Pressure of the room a winnowing table stands in, whole kPa.
     *
     * <p>Its own slot rather than borrowed from the retort's temperature. The two machines
     * that share {@code DATA_RECOVERY} share it because they report the same <em>kind</em>
     * of thing — two numbers in tension — and that is the whole justification; pressure and
     * temperature are not the same kind of thing, and a channel that carried either
     * depending on who was looking is how a readout starts lying.
     */
    public static final int DATA_ROOM_KPA = 9;

    /**
     * Which reaction the retort's charge is for, as a {@link play.xponer.astronima.sim.ore.RetortProcess}
     * ordinal.
     *
     * <p>Synced rather than derived on the client from the feed slot, because the slot is
     * empty for the whole of the batch it is describing: the charge is consumed the instant
     * it finishes, and a panel that forgot which window it had just been hunting would blank
     * its marks at exactly the moment the player looks up to see how they did.
     */
    public static final int DATA_PROCESS = 10;

    /**
     * Feed grain size, µm — the fluidized bed's, because its whole usable spin window slides with
     * how finely the ore was ground. Its own slot rather than a borrowed one: a grain size is not
     * a recovery and not a grade, and the two machines that share {@code DATA_RECOVERY} share it
     * because they report the same <em>kind</em> of number (rule 16).
     */
    public static final int DATA_FEED_MICRONS = 11;

    /** How far a machine's setting has drifted from where it was calibrated, 0..1. */
    public static final int DATA_CALIBRATION_ERROR = 12;

    /** How far through a calibration somebody is, 0..1, or zero when nobody is doing one. */
    public static final int DATA_CALIBRATING = 13;

    /**
     * Which {@link play.xponer.astronima.sim.machine.Calibration.Drift} this machine has, as an
     * ordinal.
     *
     * <p>Synced rather than looked up on the client from the machine's {@code Kind}, because a
     * client-side {@code Kind -> Drift} table would be a second copy of a fact the block entity
     * already states — and the copy that gets forgotten is always the one nothing forces you to
     * touch. With this the panel learns from the machine itself whether it has a dial, which way
     * its setting slides and what has gone wrong, and a new machine's panel is right the moment
     * its block entity names a drift.
     */
    public static final int DATA_DRIFT = 14;

    /**
     * Which metal the electrolysis cell is currently tuned for, as an
     * {@link play.xponer.astronima.sim.ore.ElectrolysisSpecies} ordinal.
     *
     * <p>Synced rather than read from the electrode slot on the client, for the same reason
     * {@link #DATA_PROCESS} is: nothing stops a player from pulling the electrode back out
     * to look at it, and the panel should keep reporting the cell's real target while they do.
     */
    public static final int DATA_ELECTROLYSIS_TARGET = 15;

    /** The printer's laser power, in watts — the plane's own x-axis. */
    public static final int DATA_SLS_POWER_W = 16;

    /** The printer's scan speed, in mm/s — the plane's own y-axis. */
    public static final int DATA_SLS_SPEED_MMS = 17;

    /**
     * What share of a full electrical draw the machine last actually got, 0..1 scaled — every
     * machine here can run either hand-cranked or wired, and until this channel existed the panel
     * had no way to tell "wired but starved by a bad run" apart from "not wired at all": both just
     * read as the same silent {@code CREEPING} state.
     */
    public static final int DATA_POWERED = 18;

    /** The heavy-water cell's own reading: the D2O fraction of whatever is in its feed slot right
     *  now, 0..1 scaled — the live number the panel cascades toward, not a machine setting. */
    public static final int DATA_D2O_FRACTION = 19;

    /** The iron smelter's own reading: how much of the last batch's real stoichiometric flux
     *  need actually got supplied, 0..1 scaled — "how clean was that" (design/iron-smelter.md). */
    public static final int DATA_FLUX_RATIO = 20;

    /**
     * Three generic "how much of a real room reagent does this machine actually have" channels,
     * 0..1 scaled — reused across every kind that reads room gas the same way {@code DATA_RECOVERY}/
     * {@code DATA_GRADE} already reuse one channel across several machines, rather than growing
     * this array once per machine (design/machines.md's own Update section: the room-reagent
     * machines had nothing at all to show). Not every kind uses all three; SABATIER_REACTOR and
     * BOSCH_REACTOR use A/B, TROILITE_ROASTER and POLYMERIZER use only A, SULFURIC_ACID_PLANT uses
     * all three.
     */
    public static final int DATA_REAGENT_A = 21;
    public static final int DATA_REAGENT_B = 22;
    public static final int DATA_REAGENT_C = 23;

    public static final int DATA_SIZE = 24;

    /** Settings travel as whole permille so they fit an int without losing feel. */
    public static final int SETTING_SCALE = 1000;

    public enum Kind { CRUSHER, SEPARATOR, FORGE, RETORT, WINNOWER, REFINER, FLUIDBED, ELECTROLYSIS, SLS,
        CRACKING_TOWER, POLYMERIZER, WATER_ELECTROLYZER, SABATIER_REACTOR, BOSCH_REACTOR,
        TROILITE_ROASTER, SULFURIC_ACID_PLANT, HEAVY_WATER_CELL, TITANIUM_CELL, INDUCTION_FURNACE,
        IRON_SMELTER, FREEZE_DRYER, DOWNS_CELL, ZONE_REFINER, HF_DIGESTER, ETCH_STATION, GRAPHITIZER,
        ALGAE_BIOREACTOR, ANAEROBIC_DIGESTER }

    /** Kinds that split their feed into two products and so need three item slots.
     *
     * <p>Public so {@code MachinePanel.plan(Kind)} can reserve the <em>real</em> stacked-slot
     * geometry {@link play.xponer.astronima.client.screen.ProcessingUi#addMachineSlots} actually
     * draws for these kinds, rather than a second, independently-maintained guess at which kinds
     * need it (rule 46/96 — one classification, not two that can drift apart). */
    public static boolean threeSlots(Kind kind) {
        return kind == Kind.SEPARATOR || kind == Kind.FLUIDBED || kind == Kind.ELECTROLYSIS
                || kind == Kind.WINNOWER || kind == Kind.REFINER;
    }

    /**
     * Kinds whose whole product is gas, vented straight into the room, so there is no output
     * slot at all — see {@code CrackingTowerBlockEntity}'s own doc, the first of these.
     *
     * <p>Public for the same reason {@link #threeSlots} is.
     */
    public static boolean oneSlot(Kind kind) {
        return kind == Kind.CRACKING_TOWER || kind == Kind.WATER_ELECTROLYZER
                || kind == Kind.SABATIER_REACTOR;
    }

    /**
     * The one kind with two consumed feed slots at once (ore, flux) alongside two real products
     * (iron, slag) — {@code IronSmelterBlockEntity}'s own doc names why this could not reuse
     * {@link #threeSlots}'s own one-feed-two-product shape.
     *
     * <p>Public for the same reason {@link #threeSlots} is.
     */
    public static boolean twoFeedsTwoProducts(Kind kind) {
        return kind == Kind.IRON_SMELTER || kind == Kind.HF_DIGESTER || kind == Kind.ETCH_STATION;
    }

    /**
     * How many item slots a machine's container actually has.
     *
     * <p>Named as its own function rather than a silent ternary, because the two call sites
     * ({@link ProcessingUiHolder}'s constructor and its own {@code quickMoveStack} support) must
     * agree, and a literal repeated in both is exactly how they stop agreeing (rule 20). A third
     * place has to agree with both of them too — the block entity's own container size, passed
     * to its superclass constructor — which is exactly the fact that drifted for the winnowing
     * table (PLAN.md): this returned 2 while the real container was 3, so the GUI never exposed
     * the table's second product slot at all. {@code ProcessingMenuTest} checks this against
     * every machine's real container size, scraped from source.
     */
    public static int slotCount(Kind kind) {
        if (oneSlot(kind)) {
            return 1;
        }
        if (twoFeedsTwoProducts(kind)) {
            return 4;
        }
        return threeSlots(kind) ? 3 : 2;
    }

    /**
     * Panel geometry, shared with the screen.
     *
     * <p>The first version kept these numbers in two places and they disagreed: the
     * readouts were drawn straight through the "Inventory" label. Slot positions and
     * the drawing that surrounds them have to come from one source or they will drift
     * again the next time either is touched.
     */
    // Read from MachinePanel rather than owned here. The numbers used to live in this class,
    // which extended a Minecraft one - so nothing could load them in a unit test and the only
    // checkable part of a machine screen was the picture in the middle of it. They are the same
    // numbers; what is new is that MachinePanel.plan(kind) reserves each of them as a rectangle,
    // per machine, and a scan refuses the overlap (rule 27).
    public static final int PANEL_WIDTH = MachinePanel.WIDTH;
    public static final int SLOT_IN_X = MachinePanel.SLOT_IN_X;
    public static final int SLOT_OUT_X = MachinePanel.SLOT_OUT_X;
    public static final int SLOT_ROW_Y = MachinePanel.SLOT_ROW_Y;
    public static final int DIAL_Y = MachinePanel.DIAL_Y;
    public static final int READOUT_Y = MachinePanel.READOUT_Y;
    public static final int INVENTORY_LABEL_Y = MachinePanel.INVENTORY_LABEL_Y;
    public static final int INVENTORY_Y = MachinePanel.INVENTORY_Y;
    public static final int HOTBAR_Y = MachinePanel.HOTBAR_Y;
    public static final int PANEL_HEIGHT = MachinePanel.HEIGHT;

    /** Output slots take nothing: product leaves, it does not go back in. */
    public static final class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /**
     * The feed slot refuses what its machine cannot use.
     *
     * <p>Before this, a wrong item went in and simply sat there while the machine did
     * nothing — a silent refusal indistinguishable from a broken machine, which is the
     * exact failure machine-io.md M3 names. Refusing at the slot makes the boundary
     * physical, and the screen names what the slot wants when hovered.
     */
    public static final class FeedSlot extends Slot {
        private final Kind kind;

        public FeedSlot(Kind kind, Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.kind = kind;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return feedAccepts(kind, getContainerSlot(), stack);
        }
    }

    /**
     * Holds the electrolysis cell's own installed electrode — equipment the player swaps,
     * not a product the machine makes or a feed it consumes. Refuses anything that is not a
     * recognised electrode, the same physical-boundary reasoning {@link FeedSlot} already
     * established, but stays player-extractable at any time, unlike an {@link OutputSlot}.
     */
    public static final class ElectrodeSlot extends Slot {
        public ElectrodeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(play.xponer.astronima.registry.ModItems.SILICON_ELECTRODE.get())
                    || stack.is(play.xponer.astronima.registry.ModItems.ALUMINUM_ELECTRODE.get());
        }
    }

    /** What each machine's feed slot takes — one authority for the slot and its tooltip. */
    public static boolean feedAccepts(Kind kind, ItemStack stack) {
        return switch (kind) {
            case CRUSHER -> stack.is(play.xponer.astronima.registry.ModItems.METAL_RICH_ORE.get())
                    || stack.is(play.xponer.astronima.registry.ModItems.ASTEROID_ROCK.get())
                    || stack.is(play.xponer.astronima.registry.ModItems.ILMENITE_ORE.get());
            case SEPARATOR -> stack.is(play.xponer.astronima.registry.ModItems.CRUSHED_ORE.get());
            // Refined nickel too, and that is the Mond process's whole point: what you press
            // decides the alloy, so the player chooses instead of accepting what the rock had.
            case FORGE -> stack.is(play.xponer.astronima.registry.ModItems.IRON_NICKEL_GRAINS.get())
                    || stack.is(play.xponer.astronima.registry.ModItems.PURE_NICKEL.get())
                    || stack.is(play.xponer.astronima.registry.ModItems.METAL_BILLET.get());
            // Anything with water still bonded into it. Baked silicate is deliberately
            // absent: it has already given up everything it had, and a feed slot that
            // accepted it would invite a player to wait on a batch that cannot produce.
            case RETORT -> play.xponer.astronima.block.entity.SolarRetortBlockEntity
                    .processOf(stack) != null;
            case WINNOWER -> stack.is(play.xponer.astronima.registry.ModItems.CRUSHED_ORE.get());
            // Only the mixed metal. Pure nickel is deliberately refused: putting the output
            // back in would be a machine that does nothing, and a slot that accepts it invites
            // exactly that.
            case REFINER -> stack.is(play.xponer.astronima.registry.ModItems.IRON_NICKEL_GRAINS.get());
            // Crushed ilmenite only — the reactor reads its grind to know where the spin window
            // sits. Ilmenite ore goes to the crusher first, not here.
            case FLUIDBED -> stack.is(play.xponer.astronima.registry.ModItems.CRUSHED_ILMENITE.get());
            // Tailings only, deliberately — this is the gangue an earlier tier already gave up
            // on, not raw rock a magnet or a bed still owes the player metal from.
            case ELECTROLYSIS -> stack.is(play.xponer.astronima.registry.ModItems.TAILINGS.get());
            // Iron powder off the fluidized bed - already smeltable into an ingot; this is
            // the powder's other route, into a shaped part rather than a lump.
            case SLS -> stack.is(play.xponer.astronima.registry.ModItems.IRON_POWDER.get());
            // Either feedstock — the tower cracks either into the same ethylene/methane split.
            case CRACKING_TOWER -> stack.is(play.xponer.astronima.registry.ModItems.THOLIN_CLUMP.get())
                    || stack.is(play.xponer.astronima.registry.ModItems.SLUDGE.get());
            // Titania: the catalyst, not a reagent the machine burns through in bulk. The
            // ethylene the batch actually needs is read off the room, not this slot.
            case POLYMERIZER -> stack.is(play.xponer.astronima.registry.ModItems.TITANIA.get());
            case WATER_ELECTROLYZER -> play.xponer.astronima.block.entity
                    .WaterElectrolyzerBlockEntity.isWaterBottle(stack);
            case SABATIER_REACTOR -> stack.is(play.xponer.astronima.registry.ModItems.PURE_NICKEL.get());
            case BOSCH_REACTOR -> stack.is(play.xponer.astronima.registry.ModItems.IRON_POWDER.get());
            case TROILITE_ROASTER -> stack.is(play.xponer.astronima.registry.ModItems.CRUSHED_ORE.get());
            case SULFURIC_ACID_PLANT -> stack.is(
                    play.xponer.astronima.registry.ModBlocks.HEMATITE_ORE.get().asItem());
            // Any water bottle, natural or already partway through a cascade — the fraction
            // component (or its absence) is what the block entity actually reads.
            case HEAVY_WATER_CELL -> play.xponer.astronima.block.entity
                    .WaterElectrolyzerBlockEntity.isWaterBottle(stack);
            case TITANIUM_CELL -> stack.is(play.xponer.astronima.registry.ModItems.TITANIA.get());
            case INDUCTION_FURNACE -> stack.is(play.xponer.astronima.registry.ModItems.IRON_POWDER.get());
            // The ore slot's own predicate — IRON_SMELTER's second feed (flux) needs its own,
            // different predicate, which is exactly why {@link #feedAccepts(Kind, int,
            // ItemStack)} exists rather than stretching this one, kind-wide method to cover it.
            case IRON_SMELTER -> stack.is(
                    play.xponer.astronima.registry.ModBlocks.HEMATITE_ORE.get().asItem());
            // Baked silicate only — the anhydrous tailings byproduct that gets rehydrated into
            // a gel and frozen against an adjacent dewar's LN2 (design/cryogenics.md §5).
            case FREEZE_DRYER -> stack.is(play.xponer.astronima.registry.ModItems.BAKED_SILICATE.get());
            // Rock salt only — melted and electrolyzed whole, the Downs process's own real feed.
            case DOWNS_CELL -> stack.is(
                    play.xponer.astronima.registry.ModBlocks.HALITE_ORE.get().asItem());
            // Electrolytic silicon only — zone-refined into wafer-grade stock, ten items a batch.
            case ZONE_REFINER -> stack.is(play.xponer.astronima.registry.ModItems.SILICON.get());
            // The fluorite slot's own predicate — the acid feed needs its own, different
            // predicate, exactly why {@link #feedAccepts(Kind, int, ItemStack)} exists.
            case HF_DIGESTER -> stack.is(
                    play.xponer.astronima.registry.ModBlocks.FLUORITE_ORE.get().asItem());
            // The wafer slot's own predicate — the HF slot refuses everything here on purpose
            // (design/halogens.md §42): the only door into it is the block's own hand-load
            // interaction, never this generic check.
            case ETCH_STATION -> stack.is(play.xponer.astronima.registry.ModItems.WAFER_SILICON.get());
            // Any of the three real feeds — which one decides the setpoint the vessel heats
            // itself to (design/carbon-fiber.md §2): no dial, so the slot has to take all three.
            case GRAPHITIZER -> stack.is(play.xponer.astronima.registry.ModItems.CARBON_POWDER.get())
                    || stack.is(play.xponer.astronima.registry.ModItems.PITCH_FIBER.get())
                    || stack.is(play.xponer.astronima.registry.ModItems.STABILIZED_FIBER.get());
            // A real water bottle, the same predicate the water electrolyzer already uses -
            // the room's own CO2, not this slot, is the reaction's other real reagent.
            case ALGAE_BIOREACTOR -> play.xponer.astronima.block.entity
                    .WaterElectrolyzerBlockEntity.isWaterBottle(stack);
            case ANAEROBIC_DIGESTER -> stack.is(play.xponer.astronima.registry.ModItems.CROP_WASTE.get());
        };
    }

    /**
     * Which item a specific feed slot takes, for machines with more than one — every kind but
     * {@link Kind#IRON_SMELTER} has exactly one feed slot, so this just defers to {@link
     * #feedAccepts(Kind, ItemStack)} and ignores which slot asked.
     */
    public static boolean feedAccepts(Kind kind, int slotIndex, ItemStack stack) {
        if (kind == Kind.IRON_SMELTER) {
            return switch (slotIndex) {
                case play.xponer.astronima.block.entity.IronSmelterBlockEntity.SLOT_ORE ->
                        stack.is(play.xponer.astronima.registry.ModBlocks.HEMATITE_ORE.get().asItem());
                case play.xponer.astronima.block.entity.IronSmelterBlockEntity.SLOT_FLUX ->
                        stack.is(play.xponer.astronima.registry.ModItems.MAGNESIUM_OXIDE.get());
                default -> false;
            };
        }
        if (kind == Kind.HF_DIGESTER) {
            return switch (slotIndex) {
                case play.xponer.astronima.block.entity.HfDigesterBlockEntity.SLOT_FLUORITE ->
                        stack.is(play.xponer.astronima.registry.ModBlocks.FLUORITE_ORE.get().asItem());
                case play.xponer.astronima.block.entity.HfDigesterBlockEntity.SLOT_ACID ->
                        stack.is(play.xponer.astronima.registry.ModItems.SULFURIC_ACID.get());
                default -> false;
            };
        }
        if (kind == Kind.ETCH_STATION) {
            // The HF slot refuses everything, always — real slot-driven placement (GUI drag,
            // hopper) is never how it fills. The only door in is the block's own hand-load
            // interaction, which writes the slot directly and never asks this question
            // (design/halogens.md §42).
            return slotIndex == play.xponer.astronima.block.entity.EtchStationBlockEntity.SLOT_WAFER
                    && stack.is(play.xponer.astronima.registry.ModItems.WAFER_SILICON.get());
        }
        return feedAccepts(kind, stack);
    }

    /** The feed's name, for the hover that explains a refusal. */
    public static String feedDescription(Kind kind) {
        return switch (kind) {
            case CRUSHER -> "asteroid rock, a metal seam, or ilmenite ore";
            case SEPARATOR -> "crushed ore";
            case FORGE -> "iron-nickel grains, refined nickel, or a billet to work";
            case RETORT -> "anything still holding water — tailings, rock — or chlorate powder,"
                    + " for its oxygen";
            case WINNOWER -> "crushed ore — and the finer it was ground, the better";
            case REFINER -> "iron-nickel grains — and carbon monoxide in the room to carry"
                    + " the nickel out of them";
            case FLUIDBED -> "crushed ilmenite — and hydrogen in the room to reduce it";
            case ELECTROLYSIS -> "tailings — and, optionally, an electrode to retune what"
                    + " it reaches";
            case SLS -> "iron powder — fused by the laser at whatever power and speed the"
                    + " plane is set to";
            case CRACKING_TOWER -> "tholins or sludge — cracked into ethylene, vented into"
                    + " the sealed room this sits in";
            case POLYMERIZER -> "titania, as a catalyst — and ethylene in the room to string"
                    + " into polyethylene";
            case WATER_ELECTROLYZER -> "a water bottle — split into hydrogen and oxygen,"
                    + " vented into the sealed room this sits in";
            case SABATIER_REACTOR -> "pure nickel, as a catalyst — and carbon dioxide plus"
                    + " hydrogen in the room to react into methane and water";
            case BOSCH_REACTOR -> "iron powder, as a catalyst — and carbon dioxide plus"
                    + " hydrogen in the room to react into carbon and water";
            case TROILITE_ROASTER -> "crushed ore — and oxygen in the room, genuinely spent,"
                    + " to roast its troilite into hematite and SO2";
            case SULFURIC_ACID_PLANT -> "hematite, as a catalyst — and sulfur dioxide, oxygen"
                    + " and water vapor in the room to react into sulfuric acid";
            case HEAVY_WATER_CELL -> "a water bottle — electrolysed one cascade stage further"
                    + " toward pure D2O, five bottles for every one that advances";
            case TITANIUM_CELL -> "titania — reduced straight to metal by the FFC-Cambridge"
                    + " process, no chlorine and no separate reagent needed";
            case INDUCTION_FURNACE -> "iron powder — melted to an ingot without touching the"
                    + " room's own air, unlike a vanilla furnace";
            case IRON_SMELTER -> "hematite ore";
            case FREEZE_DRYER -> "baked silicate — frozen against an adjacent dewar's LN2 and"
                    + " freeze-dried in real vacuum into silica aerogel";
            case DOWNS_CELL -> "rock salt — melted and electrolyzed into real sodium metal and"
                    + " chlorine gas, vented into the sealed room this sits in";
            case ZONE_REFINER -> "electrolytic silicon — a molten zone dragged along it rejects"
                    + " real metallic contamination into the melt, ten items a full batch";
            case HF_DIGESTER -> "fluorite ore and sulfuric acid — real 1:1 stoichiometry, both"
                    + " reagents mandatory, reacted into real hydrofluoric acid and gypsum";
            case ETCH_STATION -> "wafer-grade silicon and hydrofluoric acid — real 1:6"
                    + " stoichiometry, HF hand-loaded only, etched into a real die and"
                    + " fluorosilicic acid";
            case GRAPHITIZER -> "carbon powder, pitch fiber, or stabilized fiber — whichever one"
                    + " decides the real temperature it self-heats to";
            case ALGAE_BIOREACTOR -> "a water bottle — reacted against real CO2 in the room, real"
                    + " 1:1, into food and oxygen";
            case ANAEROBIC_DIGESTER -> "real crop waste — digested with no power at all into real"
                    + " biogas (60% methane, 40% CO2) vented into the room, and real fertilizer";
        };
    }

    /** A feed slot's own name, for the two-feed machines whose slots need different hovers. */
    public static String feedDescription(Kind kind, int slotIndex) {
        if (kind == Kind.IRON_SMELTER) {
            return slotIndex == play.xponer.astronima.block.entity.IronSmelterBlockEntity.SLOT_FLUX
                    ? "magnesium oxide, as a real flux — optional, but real fluxing and slagging"
                            + " turns crude iron into clean iron"
                    : "hematite ore";
        }
        if (kind == Kind.HF_DIGESTER) {
            return slotIndex == play.xponer.astronima.block.entity.HfDigesterBlockEntity.SLOT_ACID
                    ? "sulfuric acid — three items, the real stoichiometric need for one fluorite"
                            + " charge, mandatory, not an optional flux"
                    : "fluorite ore";
        }
        if (kind == Kind.ETCH_STATION) {
            return slotIndex == play.xponer.astronima.block.entity.EtchStationBlockEntity.SLOT_HF
                    ? "hydrofluoric acid — six items, hand-loaded only (right-click the bottle"
                            + " onto this block); this slot refuses a hopper or a drag"
                    : "wafer-grade silicon";
        }
        return feedDescription(kind);
    }

    /**
     * The same machine, named where a layout can hear it.
     *
     * <p>Two enums is a thing that can drift, so a test refuses it: they must have the same
     * constants in the same order. The alternative was leaving the panel unable to say which
     * machine it was sizing itself for, which is why every one of them was the height of the
     * tallest.
     */
    public static play.xponer.astronima.client.hud.MachineKind panelKind(Kind kind) {
        return play.xponer.astronima.client.hud.MachineKind.values()[kind.ordinal()];
    }

    /**
     * Builds the synced view of a machine.
     *
     * <p>Deliberately derived rather than stored: every value here is computed from
     * the block entity when asked, so there is no second copy to fall out of date.
     */
    public static ContainerData dataFor(ProcessingBlockEntity machine) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS -> Math.round(machine.progress() * SETTING_SCALE);
                    case DATA_SETTING -> (int) Math.round(settingOf(machine) * SETTING_SCALE);
                    case DATA_WORK_REQUIRED -> machine.workRequired();
                    // The forge reuses these two slots for hardness and ductility:
                    // both machines report "two numbers in tension", so one channel
                    // serves both rather than growing the array per machine.
                    case DATA_RECOVERY -> switch (machine) {
                        case MagneticSeparatorBlockEntity separator ->
                                Math.round(separator.lastRecovery() * SETTING_SCALE);
                        case ColdForgeBlockEntity forge ->
                                (int) Math.round(forge.piece().hardness() * SETTING_SCALE);
                        case play.xponer.astronima.block.entity.WinnowingTableBlockEntity table ->
                                Math.round(table.lastRecovery() * SETTING_SCALE);
                        default -> 0;
                    };
                    case DATA_GRADE -> switch (machine) {
                        case MagneticSeparatorBlockEntity separator ->
                                Math.round(separator.lastGrade() * SETTING_SCALE);
                        case ColdForgeBlockEntity forge ->
                                (int) Math.round(forge.piece().ductility() * SETTING_SCALE);
                        case play.xponer.astronima.block.entity.WinnowingTableBlockEntity table ->
                                Math.round(table.lastGrade() * SETTING_SCALE);
                        default -> 0;
                    };
                    case DATA_VACUUM -> machine instanceof ColdForgeBlockEntity forge
                            ? Math.round(forge.evacuation() * SETTING_SCALE) : 0;
                    case DATA_WORK_STATE -> machine.workState().ordinal();
                    // Two machines that genuinely report the same kind of number: a vessel
                    // temperature in kelvin. Neither is a player's setting any more - the
                    // retort's mirror aims itself and the refiner runs its own temperature
                    // journey. Both are "how hot is it", read straight off the machine.
                    case DATA_TEMPERATURE_K -> switch (machine) {
                        case SolarRetortBlockEntity retort ->
                                (int) Math.round(retort.temperatureK());
                        case play.xponer.astronima.block.entity.CarbonylRefinerBlockEntity refiner ->
                                (int) Math.round(refiner.setpointK());
                        default -> 0;
                    };
                    case DATA_SUNLIGHT -> machine instanceof SolarRetortBlockEntity retort
                            ? (int) Math.round(retort.sunlight() * SETTING_SCALE) : 0;
                    case DATA_PROCESS -> machine instanceof SolarRetortBlockEntity retort
                            ? retort.process().ordinal() : 0;
                    // The gap between what was set and where it has drifted to, and how far
                    // through a calibration somebody is. Two things the panel must show or the
                    // whole mechanic is a hidden number that quietly costs the player yield.
                    case DATA_CALIBRATION_ERROR ->
                            (int) Math.round(machine.calibrationError() * SETTING_SCALE);
                    case DATA_CALIBRATING ->
                            (int) Math.round(machine.calibrationProgress() * SETTING_SCALE);
                    case DATA_DRIFT -> machine.drift().ordinal();
                    case DATA_FEED_MICRONS -> machine
                            instanceof play.xponer.astronima.block.entity
                                    .FluidizedBedBlockEntity bed
                            ? (int) Math.round(bed.feedMicrons()) : 0;
                    case DATA_ROOM_KPA -> machine
                            instanceof play.xponer.astronima.block.entity
                                    .WinnowingTableBlockEntity table
                            ? (int) Math.round(table.roomPressureKPa()) : 0;
                    case DATA_ELECTROLYSIS_TARGET -> machine
                            instanceof play.xponer.astronima.block.entity
                                    .ElectrolysisCellBlockEntity cell
                            ? cell.target().ordinal() : 0;
                    case DATA_SLS_POWER_W -> machine
                            instanceof play.xponer.astronima.block.entity.SlsPrinterBlockEntity printer
                            ? (int) Math.round(printer.powerW()) : 0;
                    case DATA_SLS_SPEED_MMS -> machine
                            instanceof play.xponer.astronima.block.entity.SlsPrinterBlockEntity printer
                            ? (int) Math.round(printer.speedMmS()) : 0;
                    case DATA_POWERED ->
                            (int) Math.round(machine.poweredFraction() * SETTING_SCALE);
                    case DATA_D2O_FRACTION -> machine
                            instanceof play.xponer.astronima.block.entity.HeavyWaterCellBlockEntity cell
                            ? (int) Math.round(cell.feedD2OFraction() * SETTING_SCALE) : 0;
                    case DATA_FLUX_RATIO -> machine
                            instanceof play.xponer.astronima.block.entity.IronSmelterBlockEntity smelter
                            ? (int) Math.round(smelter.lastFluxRatio() * SETTING_SCALE) : 0;
                    case DATA_REAGENT_A -> (int) Math.round(SETTING_SCALE * switch (machine) {
                        case play.xponer.astronima.block.entity.SabatierReactorBlockEntity reactor ->
                                reactor.co2Fraction();
                        case play.xponer.astronima.block.entity.BoschReactorBlockEntity reactor ->
                                reactor.co2Fraction();
                        case play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity roaster ->
                                roaster.oxygenFraction();
                        case play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity plant ->
                                plant.so2Fraction();
                        case play.xponer.astronima.block.entity.PolymerizerBlockEntity polymerizer ->
                                polymerizer.ethyleneFraction();
                        case play.xponer.astronima.block.entity.EtchStationBlockEntity station ->
                                station.nearbyCleanlinessFraction();
                        default -> 0.0;
                    });
                    case DATA_REAGENT_B -> (int) Math.round(SETTING_SCALE * switch (machine) {
                        case play.xponer.astronima.block.entity.SabatierReactorBlockEntity reactor ->
                                reactor.h2Fraction();
                        case play.xponer.astronima.block.entity.BoschReactorBlockEntity reactor ->
                                reactor.h2Fraction();
                        case play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity plant ->
                                plant.oxygenFraction();
                        default -> 0.0;
                    });
                    case DATA_REAGENT_C -> machine
                            instanceof play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity plant
                            ? (int) Math.round(plant.waterVaporFraction() * SETTING_SCALE) : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // The server owns every one of these; the client never writes back
                // through this channel. Setting changes go through their own packet
                // so they can be validated.
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    /**
     * Where this machine's setting actually is, 0..1 — the drifted value, not the dialled one.
     *
     * <p>Every machine here reports the position of the <em>thing</em>, so a panel drawing a handle
     * at {@code calibratedTo()} and a mark at {@code setting()} is drawing the gap the player has
     * to close. A machine that returned what was asked for instead would make drift invisible in
     * exactly the window built to show it.
     */
    private static double settingOf(ProcessingBlockEntity machine) {
        return switch (machine) {
            case OreCrusherBlockEntity crusher -> crusher.setting();
            // Both report a physical quantity rather than a dial position, so they come back
            // through the range their own class defines rather than being read raw.
            case MagneticSeparatorBlockEntity separator -> separator.calibratedSetting();
            case ColdForgeBlockEntity forge -> forge.calibratedSetting();
            case SolarRetortBlockEntity retort -> retort.focus();
            // No control of its own: what it reports is how well the feed it has been given
            // can be classified at all, which is the number the crusher's dial decided.
            case play.xponer.astronima.block.entity.WinnowingTableBlockEntity table ->
                    table.classifiability();
            // The drum speed, 0..1 — the one thing the player trims on a fluidized bed.
            case play.xponer.astronima.block.entity.FluidizedBedBlockEntity bed -> bed.setting();
            // The vessel temperature, as a dial position. Missing here as well as from the packet
            // handler, so the refiner's handle sat pinned at the left end whatever the vessel was
            // doing - the other half of the same bug.
            case play.xponer.astronima.block.entity.CarbonylRefinerBlockEntity refiner ->
                    play.xponer.astronima.block.entity.CarbonylRefinerBlockEntity
                            .dialFor(refiner.setpointK());
            // No control of its own either: how far up the species ladder the installed
            // electrode reaches, 0 (iron, the default) to 1 (aluminium, the hardest) — not
            // drawn as a bar today, but a real quantity rather than a placeholder.
            case play.xponer.astronima.block.entity.ElectrolysisCellBlockEntity cell ->
                    cell.target().ordinal() / (double) (ElectrolysisSpecies.values().length - 1);
            // Laser power alone, as a fraction of its own range - one axis of two, reported
            // because ControlWiringTest wants a real number and not a placeholder, but the
            // panel itself draws both axes together (see MachineBody's own plane).
            case play.xponer.astronima.block.entity.SlsPrinterBlockEntity printer ->
                    (printer.powerW() - play.xponer.astronima.sim.metal.LaserSintering.MIN_POWER_W)
                            / (play.xponer.astronima.sim.metal.LaserSintering.MAX_POWER_W
                                    - play.xponer.astronima.sim.metal.LaserSintering.MIN_POWER_W);
            // Neither has a control at all — the tower cracks whatever is fed it, the
            // polymerizer strings whatever ethylene the room supplies. No partial-progress
            // quantity worth reporting the way the electrolysis target or the printer's power
            // fraction are, so this is genuinely zero rather than a placeholder.
            case play.xponer.astronima.block.entity.CrackingTowerBlockEntity tower -> 0;
            case play.xponer.astronima.block.entity.PolymerizerBlockEntity polymerizer -> 0;
            case play.xponer.astronima.block.entity.WaterElectrolyzerBlockEntity electrolyzer -> 0;
            case play.xponer.astronima.block.entity.SabatierReactorBlockEntity sabatier -> 0;
            case play.xponer.astronima.block.entity.BoschReactorBlockEntity bosch -> 0;
            case play.xponer.astronima.block.entity.TroiliteRoasterBlockEntity roaster -> 0;
            case play.xponer.astronima.block.entity.SulfuricAcidPlantBlockEntity plant -> 0;
            // No control either: one cascade stage per batch, always — nothing here for a
            // player to set.
            case play.xponer.astronima.block.entity.HeavyWaterCellBlockEntity cell -> 0;
            // No control either: reacts whatever titania it is fed, always.
            case play.xponer.astronima.block.entity.TitaniumCellBlockEntity cell -> 0;
            // No control either: melts whatever iron powder it is fed, always.
            case play.xponer.astronima.block.entity.InductionFurnaceBlockEntity furnace -> 0;
            // No control either: how much flux is in the second feed slot is a feed decision,
            // not a dial position.
            case play.xponer.astronima.block.entity.IronSmelterBlockEntity smelter -> 0;
            // No control at all: it runs on whether it is in vacuum next to a dewar of LN2, not
            // on anything drawn here.
            case play.xponer.astronima.block.entity.FreezeDryerBlockEntity dryer -> 0;
            // No control either: melts and electrolyzes whatever rock salt it is fed, always.
            case play.xponer.astronima.block.entity.DownsCellBlockEntity cell -> 0;
            // No control either: the real segregation coefficient already gives one un-tunable
            // answer (design/halogens.md §9.3) — nothing here for a player to set.
            case play.xponer.astronima.block.entity.ZoneRefinerBlockEntity refiner -> 0;
            // No control either: reacts whatever fluorite and sulfuric acid it is fed, at the
            // real fixed 1:1 stoichiometry — nothing here for a player to set.
            case play.xponer.astronima.block.entity.HfDigesterBlockEntity digester -> 0;
            // No control either: etches whatever wafer and hand-loaded HF it is fed, at the real
            // fixed 1:6 stoichiometry — nothing here for a player to set.
            case play.xponer.astronima.block.entity.EtchStationBlockEntity station -> 0;
            // No control either: self-heats to whatever real setpoint its own feed needs
            // (design/carbon-fiber.md §2) — nothing here for a player to set.
            case play.xponer.astronima.block.entity.GraphitizerBlockEntity graphitizer -> 0;
            // No control either: reacts whatever water bottle is fed against the room's own CO2,
            // always - nothing here for a player to set.
            case play.xponer.astronima.block.entity.AlgaeBioreactorBlockEntity reactor -> 0;
            // No control either: digests whatever real crop waste is fed, at the real fixed
            // 60/40 methane/CO2 split, with no power at all - nothing here for a player to set.
            case play.xponer.astronima.block.entity.AnaerobicDigesterBlockEntity digester -> 0;
            default -> 0;
        };
    }

    private ProcessingMenu() {}
}
