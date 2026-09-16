package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.metal.ColdWorking;
import play.xponer.astronima.sim.metal.VacuumChamber;

/**
 * An anvil with a vacuum chamber over the work.
 *
 * <p>It does two jobs. The first is pressing loose grains into a billet, which works
 * because clean metal surfaces with no oxide film on them simply bond — a real
 * spacecraft engineering hazard, and here the mechanism that makes a billet with no
 * heat at all.
 *
 * <p><strong>The vacuum is in the chamber, not the room.</strong> An earlier version
 * required the surrounding air to be oxygen-free, which meant standing in an
 * unbreathable space in a suit to do any metalwork — a suit that needs parts, which
 * need metal. That was a knot, and it was also not how any real vacuum process is
 * done: bell jars and vacuum presses evacuate a box while the operator stands in
 * shirtsleeves. So the forge pumps its own chamber down, and you can run it in your
 * habitat.
 *
 * <p>Where you build still matters, it is just no longer a gate. Pumping down from
 * habitat pressure takes real cranking; a forge standing in a vacuum tunnel starts
 * evacuated and never leaks, because there is nothing outside to leak in.
 *
 * <p>The second job is forging, which is where the skill is. Each blow hardens the
 * billet and spends its ductility, with no way to anneal — so there is an optimum with
 * failure on both sides of it.
 */
public class ColdForgeBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    /** Grains needed to press one billet. */
    public static final int GRAINS_PER_BILLET = 4;

    /** Work to press a billet, and to land one forging blow. */
    public static final int CONSOLIDATE_WORK = 120;
    public static final int BLOW_WORK = 26;

    /** Work spent on one stroke of the hand pump. */
    public static final int PUMP_WORK = 8;

    private double chamberKPa = VacuumChamber.HABITAT_PRESSURE_KPA;
    private ColdWorking.Piece piece = ColdWorking.Piece.fresh();
    private boolean forging;

    public ColdForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLD_FORGE.get(), pos, state, 2);
    }

    /** How hard the hammer is falling now, which sags from where it was set as the spring tires. */
    public double blowStrength() {
        return blowFor(calibratedSetting());
    }

    /** Below this the hammer is not landing at all. */
    public static final double MIN_BLOW = 0.1;

    /** The calibration position, 0..1, as a blow strength. */
    public static double blowFor(double dial) {
        return MIN_BLOW + Math.clamp(dial, 0.0, 1.0) * (1.0 - MIN_BLOW);
    }

    /** And back, for the panel's mark and for reading an older save. */
    public static double dialForBlow(double blow) {
        return Math.clamp((blow - MIN_BLOW) / (1.0 - MIN_BLOW), 0.0, 1.0);
    }

    /** Light taps are slow, gentle and better; the crate arrives set fairly light. */
    @Override
    protected double defaultSetting() {
        return dialForBlow(0.4);
    }

    /**
     * The ram's spring takes a set, so the blow softens with every drop.
     *
     * <p>How hard the hammer falls is chosen for the piece being worked and then left, so it is
     * set on the machine with a wrench rather than dragged in a window.
     */
    @Override
    public play.xponer.astronima.sim.machine.Calibration.Drift drift() {
        return play.xponer.astronima.sim.machine.Calibration.Drift.FORGE;
    }

    public ColdWorking.Piece piece() {
        return piece;
    }

    public boolean isForging() {
        return forging;
    }

    /** Pressure inside the chamber, kPa. */
    public double chamberKPa() {
        return chamberKPa;
    }

    /** How far down the chamber is pumped, 0..1, for the gauge. */
    public float evacuation() {
        return VacuumChamber.evacuation(chamberKPa);
    }

    /** True when the chamber is empty enough for clean metal to bond. */
    public boolean canColdWeld() {
        return VacuumChamber.canWeld(chamberKPa);
    }

    /** Pressure of the room the forge stands in — what the pump works against. */
    private double ambientKPa() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return VacuumChamber.HABITAT_PRESSURE_KPA;
        }
        Atmosphere.RoomReading reading = Atmosphere.get(serverLevel).readingNear(getBlockPos());
        return reading == null ? 0.0 : reading.state().pressureKPa();
    }

    /**
     * Runs the pump and lets the seals leak, every tick.
     *
     * <p>Pumping only happens while there is work to do, so an idle forge in a habitat
     * drifts back up to room pressure rather than holding a vacuum for free.
     */
    @Override
    public void serverTick() {
        double ambient = ambientKPa();
        boolean pumping = needsChamber() && canRun();

        if (pumping && !VacuumChamber.canWeld(chamberKPa)) {
            chamberKPa = VacuumChamber.pump(chamberKPa);
            setChanged();
            return; // this tick went into the pump rather than the work
        }
        if (!pumping) {
            chamberKPa = VacuumChamber.leak(chamberKPa, ambient, 0.05);
        }
        super.serverTick();
    }

    /** Only consolidation needs vacuum; hammering a solid billet does not. */
    private boolean needsChamber() {
        return getItem(SLOT_INPUT).is(ModItems.IRON_NICKEL_GRAINS.get());
    }

    @Override
    public play.xponer.astronima.menu.ProcessingMenu.Kind kind() {
        return play.xponer.astronima.menu.ProcessingMenu.Kind.FORGE;
    }

    @Override
    public int workRequired() {
        return forging ? BLOW_WORK : CONSOLIDATE_WORK;
    }

    @Override
    public boolean hasFeed() {
        ItemStack input = getItem(SLOT_INPUT);
        // The chamber is pumped as part of running, so having enough grains is the whole
        // requirement. Vacuum is something the machine achieves, not something you find.
        if (input.is(ModItems.IRON_NICKEL_GRAINS.get())
                || input.is(ModItems.PURE_NICKEL.get())) {
            return input.getCount() >= GRAINS_PER_BILLET;
        }
        return input.is(ModItems.METAL_BILLET.get()) && !piece.cracked();
    }

    @Override
    public boolean hasRoomForProduct() {
        return getItem(SLOT_OUTPUT).isEmpty();
    }

    /**
     * A cracked billet is its own stall, and its own remedy.
     *
     * <p>It is fed and the press is free, so the generic rule would report "no feed" and
     * send the player to fetch more metal — for a piece that is already ruined and will
     * never be anything else. Naming it is the difference between a machine that is broken
     * and a workpiece that is.
     */
    @Override
    public WorkState workState() {
        if (getItem(SLOT_INPUT).is(ModItems.METAL_BILLET.get()) && piece.cracked()) {
            return WorkState.SPOILED;
        }
        return super.workState();
    }

    @Override
    protected void finishBatch() {
        ItemStack input = getItem(SLOT_INPUT);

        if (input.is(ModItems.IRON_NICKEL_GRAINS.get())
                || input.is(ModItems.PURE_NICKEL.get())) {
            consolidate(input);
            return;
        }
        if (input.is(ModItems.METAL_BILLET.get())) {
            strikeBillet(input);
        }
    }

    /**
     * Presses grains into a billet, which is only possible with no oxide in the way.
     *
     * <p><strong>What was pressed decides what the billet is.</strong> Chondritic grains are 7 %
     * nickel because that is what the rock contained; refined nickel makes a billet at seam
     * grade, which work-hardens faster and spends its ductility faster with it. That is the
     * whole payoff of the Mond process — not "purer is better", but the player finally choosing
     * the alloy instead of accepting one.
     */
    private void consolidate(ItemStack grains) {
        boolean refined = grains.is(ModItems.PURE_NICKEL.get());
        grains.shrink(GRAINS_PER_BILLET);
        ItemStack billet = new ItemStack(ModItems.METAL_BILLET.get());
        billet.set(ModDataComponents.METAL_NICKEL.get(),
                (float) (refined ? ColdWorking.SEAM_NICKEL : ColdWorking.BASE_NICKEL));
        pushOutput(SLOT_OUTPUT, billet);
        piece = ColdWorking.Piece.fresh();
        forging = false;
    }

    /**
     * Lands one blow on the billet in the input slot.
     *
     * <p>The piece's state lives on the forge rather than the item, so a half-worked
     * billet cannot be pocketed mid-job and finished somewhere safer. Taking it out
     * resets the work, which is the cost of changing your mind.
     */
    private void strikeBillet(ItemStack billet) {
        float nickel = billet.getOrDefault(ModDataComponents.METAL_NICKEL.get(),
                (float) ColdWorking.BASE_NICKEL);
        forging = true;
        piece = ColdWorking.strike(piece, blowStrength(), nickel);

        if (piece.cracked()) {
            // Cracked: the billet is spent, but the metal is not gone — it goes back
            // to grains, minus what the failure cost. Failure takes time and material
            // in progress, never the whole run.
            billet.shrink(1);
            pushOutput(SLOT_OUTPUT, new ItemStack(ModItems.IRON_NICKEL_GRAINS.get(),
                    GRAINS_PER_BILLET / 2));
            piece = ColdWorking.Piece.fresh();
            forging = false;
            return;
        }
        if (piece.isUsable() && blowStrength() <= 0.15) {
            // A deliberate light finishing tap is how the smith says "done".
            billet.shrink(1);
            ItemStack head = new ItemStack(ModItems.TOOL_HEAD.get());
            head.set(ModDataComponents.METAL_NICKEL.get(), nickel);
            head.set(ModDataComponents.METAL_QUALITY.get(),
                    (float) ColdWorking.quality(piece));
            head.set(ModDataComponents.METAL_CRACKED.get(), piece.cracked());
            pushOutput(SLOT_OUTPUT, head);
            piece = ColdWorking.Piece.fresh();
            forging = false;
        }
    }

    /** Removing the billet abandons the work, so state cannot be carried elsewhere. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == SLOT_INPUT && !ItemStack.isSameItem(stack, getItem(SLOT_INPUT))) {
            piece = ColdWorking.Piece.fresh();
            forging = false;
        }
        super.setItem(slot, stack);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_INPUT
                && (stack.is(ModItems.IRON_NICKEL_GRAINS.get())
                    || stack.is(ModItems.METAL_BILLET.get()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("chamber_kpa", chamberKPa);
        output.putDouble("hardness", piece.hardness());
        output.putDouble("ductility", piece.ductility());
        output.putBoolean("cracked", piece.cracked());
        output.putBoolean("forging", forging);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // A forge saved before the ram could tire keeps the blow it was left set to.
        double legacyBlow = legacyValue(input, "blow_strength");
        if (!Double.isNaN(legacyBlow)) {
            setCalibratedTo(dialForBlow(legacyBlow));
        }
        chamberKPa = Math.max(0, input.getDoubleOr("chamber_kpa",
                VacuumChamber.HABITAT_PRESSURE_KPA));
        piece = new ColdWorking.Piece(
                input.getDoubleOr("hardness", ColdWorking.ANNEALED_HARDNESS),
                input.getDoubleOr("ductility", ColdWorking.FRESH_DUCTILITY),
                input.getBooleanOr("cracked", false));
        forging = input.getBooleanOr("forging", false);
    }
}
