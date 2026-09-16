package play.xponer.astronima.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.GasPortBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasValveBlock;
import play.xponer.astronima.block.entity.GasPumpBlockEntity;
import play.xponer.astronima.block.entity.GasTankBlockEntity;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Randomised plumbing, checked against laws rather than expectations.
 *
 * <p>The scenario tests in {@link PlayerScenarios} only cover situations someone thought
 * to describe. That is a real improvement on asserting computed numbers, and it is still
 * not enough: the six-pump layout that exposed the dead valve was not a case anybody had
 * written down, and neither is whatever breaks next.
 *
 * <p>So this builds plumbing <em>nobody designed</em> — random port counts, random pump
 * directions, random valve settings, several pumps drawing on one room — and asserts only
 * things that must hold no matter what was built:
 *
 * <ol>
 *   <li><strong>Gas is conserved.</strong> Pumping moves it; nothing creates or destroys
 *       it. This is the invariant a concurrency fault breaks first.</li>
 *   <li><strong>Nothing goes negative.</strong> A negative amount silently poisons every
 *       calculation downstream of it.</li>
 *   <li><strong>Nothing becomes NaN.</strong> One division by an empty volume and the
 *       whole habitat's readouts turn to nonsense.</li>
 *   <li><strong>Normal operation never bursts a vessel.</strong> A pump stalls at the
 *       rating, so no arrangement of pumps should get past it.</li>
 * </ol>
 *
 * <p>Seeded, so a failure names the seed that produced it and can be replayed exactly.
 */
public final class GasInvariants {
    public static final DeferredRegister<Consumer<GameTestHelper>> INVARIANTS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Astronima.MODID);

    /** Enough shapes to hit the awkward ones; small enough to run in a gametest tick. */
    private static final int LAYOUTS = 24;
    private static final int STEPS = 120;
    private static final double TOLERANCE = 1e-6;

    @SuppressWarnings("unused")
    private static final Object FUZZ =
            INVARIANTS.register("invariant_random_plumbing",
                    () -> GasInvariants::randomPlumbing);

    private static void randomPlumbing(GameTestHelper helper) {
        int layoutsThatMovedGas = 0;
        for (int layout = 0; layout < LAYOUTS; layout++) {
            Outcome outcome = runLayout(helper, layout);
            if (outcome == Outcome.BROKEN) {
                return; // helper.fail already called, with the seed
            }
            if (outcome == Outcome.MOVED_GAS) {
                layoutsThatMovedGas++;
            }
        }

        // Rule 11 applied to this test itself. Conservation holds trivially if nothing
        // ever moves, so a fuzzer whose pumps all sat idle would pass while proving
        // nothing at all. It has to be shown to have done work.
        if (layoutsThatMovedGas < LAYOUTS / 4) {
            helper.fail("only " + layoutsThatMovedGas + " of " + LAYOUTS
                    + " random layouts moved any gas - this test is not exercising"
                    + " the pumps and its conservation check means nothing");
            return;
        }
        helper.succeed();
    }

    private enum Outcome { MOVED_GAS, IDLE, BROKEN }

    /**
     * Builds one random arrangement and checks the laws hold through it.
     *
     * @return whether an invariant broke, and whether any gas actually moved
     */
    private static Outcome runLayout(GameTestHelper helper, int seed) {
        RandomSource random = RandomSource.create(seed);
        BlockPos inside = new BlockPos(2, 2, 2);
        ModTestFunctions.buildBoxAround(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (room == null) {
            helper.fail("seed " + seed + ": the sealed box did not become a room");
            return Outcome.BROKEN;
        }
        for (Gas gas : Gas.values()) {
            room.removeGas(gas, room.gases().get(gas));
        }
        room.addGasAt(Gas.OXYGEN, 20 + random.nextInt(400), 293.15);

        // Several pumps drawing on one room at once: the arrangement that found the
        // dead valve, and the one most likely to break conservation, since every pump
        // ticks separately and mutates the room the instant it runs.
        int branches = 1 + random.nextInt(4);
        List<BlockPos> pumps = new ArrayList<>();
        List<BlockPos> tanks = new ArrayList<>();

        Direction[] outward = {Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        for (int branch = 0; branch < branches; branch++) {
            Direction out = outward[branch];
            BlockPos port = inside.relative(out, 2);
            BlockPos valve = port.relative(out);
            BlockPos pump = valve.relative(out);
            BlockPos tank = pump.relative(out);

            helper.setBlock(port, ModBlocks.GAS_PORT.get().defaultBlockState()
                    .setValue(GasPortBlock.FACING, out.getOpposite()));
            helper.setBlock(valve, ModBlocks.GAS_VALVE.get().defaultBlockState()
                    .setValue(GasValveBlock.SETTING, random.nextInt(5)));
            // Random facing, so some pumps push the wrong way and some push nowhere.
            helper.setBlock(pump, ModBlocks.GAS_PUMP.get().defaultBlockState()
                    .setValue(GasPumpBlock.FACING,
                            random.nextBoolean() ? out : out.getOpposite()));
            helper.setBlock(tank, ModBlocks.GAS_TANK.get().defaultBlockState());
            atmosphere.invalidate(helper.absolutePos(port));

            pumps.add(pump);
            tanks.add(tank);
        }

        double before = totalMoles(helper, inside, tanks);

        for (int step = 0; step < STEPS; step++) {
            for (BlockPos pump : pumps) {
                GasPumpBlockEntity motor =
                        helper.getBlockEntity(pump, GasPumpBlockEntity.class);
                if (motor == null) {
                    continue;
                }
                BlockPos absolute = helper.absolutePos(pump);
                motor.pumpOnce(helper.getLevel(), absolute,
                        helper.getLevel().getBlockState(absolute));
            }
            if (!checkSane(helper, seed, step, inside, tanks)) {
                return Outcome.BROKEN;
            }
        }

        double after = totalMoles(helper, inside, tanks);
        if (Math.abs(after - before) > Math.max(1e-6, before * TOLERANCE)) {
            helper.fail("seed " + seed + ": " + branches + " pumps changed the total gas"
                    + " in the system from " + before + " to " + after
                    + " mol - it should only have moved");
            return Outcome.BROKEN;
        }

        double moved = 0;
        for (BlockPos tank : tanks) {
            GasTankBlockEntity vessel = helper.getBlockEntity(tank, GasTankBlockEntity.class);
            if (vessel != null) {
                moved += vessel.contents().gases().get(Gas.OXYGEN);
            }
        }

        // Leave the plot as it was found, or the next layout inherits this one's pipes.
        for (int branch = 0; branch < branches; branch++) {
            Direction out = outward[branch];
            for (int step = 2; step <= 5; step++) {
                helper.setBlock(inside.relative(out, step), Blocks.AIR.defaultBlockState());
            }
        }
        return moved > 1e-6 ? Outcome.MOVED_GAS : Outcome.IDLE;
    }

    /** Non-negative, finite, and never past burst. Checked every step, not just at the end. */
    private static boolean checkSane(GameTestHelper helper, int seed, int step,
                                     BlockPos inside, List<BlockPos> tanks) {
        RoomState room = Atmosphere.get(helper.getLevel())
                .roomAt(helper.absolutePos(inside));
        if (room != null) {
            for (Gas gas : Gas.values()) {
                double amount = room.gases().get(gas);
                if (amount < -1e-9 || !Double.isFinite(amount)) {
                    helper.fail("seed " + seed + " step " + step + ": room holds "
                            + amount + " mol of " + gas);
                    return false;
                }
            }
        }
        for (BlockPos tank : tanks) {
            GasTankBlockEntity vessel = helper.getBlockEntity(tank, GasTankBlockEntity.class);
            if (vessel == null) {
                continue;
            }
            double pressure = vessel.pressureKPa();
            if (pressure < -1e-9 || !Double.isFinite(pressure)) {
                helper.fail("seed " + seed + " step " + step + ": tank at "
                        + pressure + " kPa");
                return false;
            }
            if (vessel.condition() == play.xponer.astronima.sim.pipe
                    .PressureVessel.Condition.BURST) {
                helper.fail("seed " + seed + " step " + step + ": ordinary pumping burst"
                        + " a vessel at " + pressure + " kPa - a pump must stall first");
                return false;
            }
        }
        return true;
    }

    private static double totalMoles(GameTestHelper helper, BlockPos inside,
                                     List<BlockPos> tanks) {
        double total = 0;
        RoomState room = Atmosphere.get(helper.getLevel())
                .roomAt(helper.absolutePos(inside));
        if (room != null) {
            for (Gas gas : Gas.values()) {
                total += room.gases().get(gas);
            }
        }
        for (BlockPos tank : tanks) {
            GasTankBlockEntity vessel = helper.getBlockEntity(tank, GasTankBlockEntity.class);
            if (vessel != null) {
                for (Gas gas : Gas.values()) {
                    total += vessel.contents().gases().get(gas);
                }
            }
        }
        return total;
    }

    private GasInvariants() {}
}
