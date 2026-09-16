package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.ore.Elutriation;
import play.xponer.astronima.sim.ore.Mineral;
import play.xponer.astronima.sim.ore.OreBody;
import play.xponer.astronima.sim.ore.OreGrade;

/**
 * A winnowing table: crushed ore in, a gas stream up through the bed, heavies and lights out.
 *
 * <p>The second separation axis tier 1 can reach, and the one that recovers what the magnet
 * refuses to touch. {@link play.xponer.astronima.sim.ore.MagneticSeparation} sorts on
 * magnetism and is honest that no field will pull nickel out of pentlandite; this sorts on
 * <em>density</em>, which pentlandite has plenty of.
 *
 * <p><strong>It has no dial, deliberately.</strong> How well it separates is decided by how
 * finely the feed was ground, because drag depends on a particle's size as well as its
 * density — so a ragged feed settles by size and density cannot express itself at all. That
 * gives the crusher's existing control a second and different consequence rather than adding
 * a third slider to tune, and it means a grind chosen for the magnet may be wrong here.
 *
 * <p>And it needs air. Drag scales with gas density, so on the surface there is nothing to
 * carry anything and the table simply cannot classify — reported as
 * {@link WorkState#UNPRESSURISED} rather than as a machine that mysteriously does nothing.
 */
public class WinnowingTableBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_HEAVIES = 1;
    public static final int SLOT_LIGHTS = 2;

    /**
     * Turns of the blower per batch.
     *
     * <p>Slower than the magnet: a classifier has to let the bed settle between puffs, and
     * rushing it is what mixes the streams back together. It is also the price of the second
     * axis — a player who wants the nickel works for it.
     */
    public static final int BASE_WORK = 110;

    /**
     * Grams of native metal per grain item — the magnet's figure, deliberately.
     *
     * <p>Same metal, same grains: a machine that produced a different amount of the same
     * item from the same rock would be saying the metal came from somewhere, and it did not.
     */
    private static final double GRAMS_PER_HEAVY = 55.0;

    /** Spoil is bulk: one item stands for a lot of rock, exactly as the magnet's tailings do. */
    private static final double GRAMS_PER_LIGHT = 1200.0;

    private float lastRecovery;
    private float lastGrade;

    public WinnowingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WINNOWING_TABLE.get(), pos, state, 3);
    }

    public float lastRecovery() {
        return lastRecovery;
    }

    public float lastGrade() {
        return lastGrade;
    }

    /**
     * The pressure of the room the table stands in.
     *
     * <p>{@code roomTouching} rather than the room it is inside: the table is a block, so it
     * is never <em>in</em> the air — it is beside it, exactly as the scrubber and the
     * dehumidifier are.
     */
    public double roomPressureKPa() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        RoomState room = Atmosphere.get(serverLevel).roomTouching(worldPosition);
        return room == null ? 0 : room.pressureKPa();
    }

    /** True when there is enough gas around it to carry a particle at all. */
    public boolean hasDraught() {
        return roomPressureKPa() >= Elutriation.MINIMUM_PRESSURE_KPA;
    }

    /**
     * The stall that belongs to this machine and no other.
     *
     * <p>Reported ahead of the generic ones on purpose: a table carried outside has no feed
     * problem and no output problem, and telling a player to check the slots when the real
     * answer is "there is no air here" sends them to the wrong place entirely.
     */
    @Override
    public WorkState workState() {
        WorkState general = super.workState();
        if (general.isWorking() && !hasDraught()) {
            return WorkState.UNPRESSURISED;
        }
        return general;
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    @Override
    public play.xponer.astronima.menu.ProcessingMenu.Kind kind() {
        return play.xponer.astronima.menu.ProcessingMenu.Kind.WINNOWER;
    }

    @Override
    public int workRequired() {
        return BASE_WORK;
    }

    @Override
    public boolean hasFeed() {
        return getItem(SLOT_INPUT).is(ModItems.CRUSHED_ORE.get());
    }

    @Override
    public boolean hasRoomForProduct() {
        ItemStack input = getItem(SLOT_INPUT);
        if (!input.is(ModItems.CRUSHED_ORE.get())) {
            return true;
        }
        Split split = split(input);
        return hasRoom(SLOT_HEAVIES, split.heavies()) && hasRoom(SLOT_LIGHTS, split.lights());
    }

    @Override
    protected void finishBatch() {
        ItemStack input = getItem(SLOT_INPUT);
        Split split = split(input);
        lastRecovery = (float) split.recovery();
        lastGrade = (float) split.grade();

        pushOutput(SLOT_HEAVIES, split.heavies());
        pushOutput(SLOT_LIGHTS, split.lights());
        input.shrink(1);
    }

    /** The two streams plus the numbers the panel reports. */
    public record Split(ItemStack heavies, ItemStack lights, double recovery, double grade) {
        public static Split nothing() {
            return new Split(ItemStack.EMPTY, ItemStack.EMPTY, 0, 0);
        }
    }

    public Split split(ItemStack batch) {
        return run(batch, roomPressureKPa());
    }

    /**
     * Runs the model and turns its two streams into items.
     *
     * <p>Static and taking the pressure as an argument so the same split can be computed for
     * a JEI page or a test without a table standing in a room.
     */
    public static Split run(ItemStack batch, double pressureKPa) {
        if (!batch.is(ModItems.CRUSHED_ORE.get())) {
            return Split.nothing();
        }
        int packed = batch.getOrDefault(ModDataComponents.ORE_BATCH.get(), 0);
        OreGrade grade = OreGrade.gradeOf(packed);
        double fineness = OreGrade.finenessOf(packed);

        OreBody feed = grade.body();
        Elutriation.Result result = Elutriation.separate(feed, fineness, pressureKPa);

        // Only native metal leaves this tier as usable metal — the same rule the magnet
        // states, and the one this machine broke on its first outing. Density concentrates
        // the sulfides beautifully and that is exactly the point of it, but concentrating
        // pentlandite is not the same as reducing it: the nickel is still bonded to sulfur
        // and stays that way until the chemical tier exists. Converting the heavy stream's
        // whole mass into grains handed out metal from sulfide and oxide alike, which is
        // the one thing the ore tier is built to refuse.
        double nativeMetal = result.heavies().massOf(Mineral.KAMACITE)
                * Mineral.KAMACITE.metalMassFraction();

        // Everything else in both streams is spoil for now. The dense half is a genuinely
        // good sulfide concentrate and it is banked as bulk rather than as its own item,
        // because an item produced by the ton with nothing to do with it is precisely what
        // NoDeadEndsTest was written to catch.
        double spoil = result.lights().totalMass()
                + (result.heavies().totalMass() - nativeMetal);

        int heavyItems = (int) Math.floor(nativeMetal / GRAMS_PER_HEAVY);
        int lightItems = (int) Math.floor(spoil / GRAMS_PER_LIGHT);

        ItemStack heavies = heavyItems > 0
                ? new ItemStack(ModItems.IRON_NICKEL_GRAINS.get(), heavyItems) : ItemStack.EMPTY;
        ItemStack lights = lightItems > 0
                ? new ItemStack(ModItems.TAILINGS.get(), lightItems) : ItemStack.EMPTY;
        return new Split(heavies, lights, result.recovery(), result.grade());
    }

    /**
     * How well the batch currently in the feed can be classified at all, 0..1.
     *
     * <p>What the panel shows where other machines show their dial. It is not a control —
     * it is the consequence of two decisions already made: how finely the crusher ground
     * this batch, and whether the room holds air. Showing it where the hand expects a knob
     * is the point: it says "the decision was upstream" rather than leaving the player
     * hunting for a control that does not exist.
     */
    public double classifiability() {
        ItemStack input = getItem(SLOT_INPUT);
        if (!input.is(ModItems.CRUSHED_ORE.get())) {
            return 0;
        }
        double fineness = OreGrade.finenessOf(
                input.getOrDefault(ModDataComponents.ORE_BATCH.get(), 0));
        return Elutriation.sharpness(fineness, roomPressureKPa()) / Elutriation.BEST_SHARPNESS;
    }

    /** What the heaviest thing in a chondrite is, for the JEI page's prose. */
    public static Mineral heaviestMineral() {
        Mineral heaviest = Mineral.KAMACITE;
        for (Mineral mineral : Mineral.values()) {
            if (mineral.densityGPerCm3() > heaviest.densityGPerCm3()) {
                heaviest = mineral;
            }
        }
        return heaviest;
    }
}
