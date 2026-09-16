package play.xponer.astronima.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.Config;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.atmosphere.CombustionEvents;
import play.xponer.astronima.atmosphere.Ignition;
import play.xponer.astronima.block.UnlitTorchBlock;
import play.xponer.astronima.item.EvaSuitItem;
import play.xponer.astronima.item.SuitRepairItem;
import play.xponer.astronima.block.entity.ColdForgeBlockEntity;
import play.xponer.astronima.block.entity.MagneticSeparatorBlockEntity;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.ore.OreGrade;
import play.xponer.astronima.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import play.xponer.astronima.crafting.CraftingTree;
import play.xponer.astronima.sim.suit.SuitCondition;
import play.xponer.astronima.sim.suit.SuitWear;
import play.xponer.astronima.sim.suit.SuitSubsystem;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.O2Status;
import net.minecraft.core.Direction;
import play.xponer.astronima.block.GasPortBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasValveBlock;
import play.xponer.astronima.block.entity.GasPumpBlockEntity;
import play.xponer.astronima.block.entity.GasTankBlockEntity;
import play.xponer.astronima.pipe.PipeNetworks;
import play.xponer.astronima.sim.GasTransfer;
import play.xponer.astronima.sim.pipe.GasNetwork;
import play.xponer.astronima.sim.pipe.PressureVessel;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.tool.ToolHead;

import java.util.function.Consumer;

/**
 * In-world integration tests for the atmosphere system, run headlessly by
 * {@code gradlew runGameTestServer}. The hull box is built directly into the level
 * beside the (1×1×1) test plot because vanilla ships no larger empty template.
 */
public final class ModTestFunctions {
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Astronima.MODID);

    @SuppressWarnings("unused")
    private static final Object SEALED_MODULE =
            TEST_FUNCTIONS.register("sealed_module", () -> ModTestFunctions::sealedModule);

    @SuppressWarnings("unused")
    private static final Object METHANE_IGNITION =
            TEST_FUNCTIONS.register("methane_ignition", () -> ModTestFunctions::methaneIgnition);

    @SuppressWarnings("unused")
    private static final Object METHANE_NO_OXYGEN =
            TEST_FUNCTIONS.register("methane_no_oxygen", () -> ModTestFunctions::methaneNoOxygen);

    @SuppressWarnings("unused")
    private static final Object UNSEALABLE_HOLDS_GAS =
            TEST_FUNCTIONS.register("unsealable_holds_gas", () -> ModTestFunctions::unsealableHoldsGas);

    @SuppressWarnings("unused")
    private static final Object SUIT_REPAIR_CYCLE =
            TEST_FUNCTIONS.register("suit_repair_cycle", () -> ModTestFunctions::suitRepairCycle);

    @SuppressWarnings("unused")
    private static final Object COLD_FORGE_RULES =
            TEST_FUNCTIONS.register("cold_forge_rules", () -> ModTestFunctions::coldForgeRules);

    /**
     * The two rules the cold forge exists to enforce.
     *
     * <p>Consolidation is gated on vacuum because that is the actual physics — no air,
     * no oxide film, so clean metal welds — and it is the reason metalwork here is an
     * expedition rather than a crafting step. Worth a gametest because the check reads
     * live atmosphere state, which no unit test can stand in for.
     */
    private static void coldForgeRules(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.COLD_FORGE.get().defaultBlockState());

        ColdForgeBlockEntity forge =
                helper.getBlockEntity(pos, ColdForgeBlockEntity.class);
        if (forge == null) {
            helper.fail("Cold forge should have a block entity");
            return;
        }

        // Open test world: no enclosure, so it counts as vacuum and welding works.
        forge.setItem(ColdForgeBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.IRON_NICKEL_GRAINS.get(),
                        ColdForgeBlockEntity.GRAINS_PER_BILLET));
        if (!forge.canRun()) {
            helper.fail("A forge in vacuum with enough grains should be able to press a billet");
            return;
        }

        // Too few grains is not a batch, whatever the atmosphere is doing.
        forge.setItem(ColdForgeBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.IRON_NICKEL_GRAINS.get(), 1));
        if (forge.canRun()) {
            helper.fail("One grain must not press into a billet");
            return;
        }

        // Ore, rock and tools are not forge feed.
        forge.setItem(ColdForgeBlockEntity.SLOT_INPUT,
                new ItemStack(ModItems.CRUSHED_ORE.get(), 8));
        if (forge.canRun()) {
            helper.fail("The forge must not accept crushed ore");
            return;
        }
        helper.succeed();
    }

    @SuppressWarnings("unused")
    private static final Object ORE_TIER_LOOP =
            TEST_FUNCTIONS.register("ore_tier_loop", () -> ModTestFunctions::oreTierLoop);

    /**
     * The whole mechanical tier on real item stacks: rock in, metal out.
     *
     * <p>Worth a gametest rather than only unit tests because the interesting part is
     * the join. The model is verified in isolation elsewhere; what this checks is that
     * the crusher's setting actually survives on the item component, that the
     * separator reads it back, and that the trade the design promises is visible in
     * the items a player would end up holding.
     */
    private static void oreTierLoop(GameTestHelper helper) {
        // Crushing coarse and fine must produce genuinely different batches.
        ItemStack coarse = crushedBatch(OreGrade.METAL_RICH, 0.1);
        ItemStack fine = crushedBatch(OreGrade.METAL_RICH, 0.9);

        MagneticSeparatorBlockEntity.Split coarseRun = MagneticSeparatorBlockEntity.run(coarse, 0.75);
        MagneticSeparatorBlockEntity.Split fineRun = MagneticSeparatorBlockEntity.run(fine, 0.75);

        if (fineRun.recovery() <= coarseRun.recovery()) {
            helper.fail("Grinding finer must free more metal: coarse "
                    + coarseRun.recovery() + " vs fine " + fineRun.recovery());
            return;
        }
        // Grade is not monotonic and must not be asserted as if it were: a coarse
        // grind sends composite rock to the magnet, so the concentrate is dirty at
        // both ends of the dial and cleanest in between. What has to hold is that
        // over-grinding costs purity relative to the optimum.
        MagneticSeparatorBlockEntity.Split middling =
                MagneticSeparatorBlockEntity.run(crushedBatch(OreGrade.METAL_RICH, 0.6), 0.75);
        if (middling.grade() <= fineRun.grade()) {
            helper.fail("A middling grind should give the cleanest concentrate: "
                    + middling.grade() + " vs fine " + fineRun.grade());
            return;
        }
        if (middling.grade() <= coarseRun.grade()) {
            helper.fail("A middling grind should beat a coarse one on purity: "
                    + middling.grade() + " vs coarse " + coarseRun.grade());
            return;
        }
        if (fineRun.grains().isEmpty()) {
            helper.fail("A metal-rich seam must actually yield grains");
            return;
        }
        if (!fineRun.grains().is(ModItems.IRON_NICKEL_GRAINS.get())) {
            helper.fail("The tier's product should be native metal grains");
            return;
        }

        // Ordinary rock has to be worth processing, just less so than a seam.
        MagneticSeparatorBlockEntity.Split plain =
                MagneticSeparatorBlockEntity.run(crushedBatch(OreGrade.CHONDRITE, 0.6), 0.75);
        MagneticSeparatorBlockEntity.Split seam =
                MagneticSeparatorBlockEntity.run(crushedBatch(OreGrade.METAL_RICH, 0.6), 0.75);
        if (seam.grains().getCount() <= plain.grains().getCount()) {
            helper.fail("A seam must out-yield ordinary chondrite, or prospecting is pointless");
            return;
        }

        // A batch with no component at all must not crash the machine.
        MagneticSeparatorBlockEntity.Split bare =
                MagneticSeparatorBlockEntity.run(new ItemStack(ModItems.CRUSHED_ORE.get()), 0.75);
        if (bare.recovery() < 0 || bare.recovery() > 1) {
            helper.fail("An uncomponented batch produced a nonsense recovery");
            return;
        }
        helper.succeed();
    }

    private static ItemStack crushedBatch(OreGrade grade, double fineness) {
        ItemStack stack = new ItemStack(ModItems.CRUSHED_ORE.get());
        stack.set(ModDataComponents.ORE_BATCH.get(), OreGrade.pack(grade, fineness));
        return stack;
    }

    @SuppressWarnings("unused")
    private static final Object FORGED_QUALITY_REACHES_THE_TOOL =
            TEST_FUNCTIONS.register("forged_quality_reaches_the_tool",
                    () -> ModTestFunctions::forgedQualityReachesTheTool);

    /**
     * The forged quality of a head has to survive the crafting grid.
     *
     * <p>It did not. A plain shaped recipe discards data components, so the work
     * hardening, the optimum and the crack risk all evaporated at the bench and every
     * pickaxe came out identical however well it was made. The recipe loading without
     * a parse error proves nothing about that, which is exactly why this test crafts
     * one and looks at what comes out.
     */
    private static void forgedQualityReachesTheTool(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        Astronima.MODID, "meteoric_pickaxe"));

        var holder = manager.byKey(key).orElse(null);
        if (holder == null) {
            helper.fail("The meteoric pickaxe recipe did not load at all");
            return;
        }
        if (!(holder.value() instanceof play.xponer.astronima.crafting.ForgedToolRecipe recipe)) {
            helper.fail("The pickaxe recipe is a " + holder.value().getClass().getSimpleName()
                    + ", not a ForgedToolRecipe - forged quality will be discarded");
            return;
        }

        ItemStack goodTool = craftPickaxe(recipe, 0.9f, false);
        ItemStack poorTool = craftPickaxe(recipe, 0.4f, false);
        ItemStack crackedTool = craftPickaxe(recipe, 0.9f, true);

        ToolHead good = goodTool.get(ModDataComponents.TOOL_STATE.get());
        ToolHead poor = poorTool.get(ModDataComponents.TOOL_STATE.get());
        ToolHead cracked = crackedTool.get(ModDataComponents.TOOL_STATE.get());

        if (good == null || poor == null || cracked == null) {
            helper.fail("A crafted pickaxe carried no tool state at all");
            return;
        }
        if (!(good.hardness() > poor.hardness())) {
            helper.fail("Better metal must make a harder tool: "
                    + good.hardness() + " vs " + poor.hardness());
            return;
        }
        if (!(good.remainingLifeInBlocks() > poor.remainingLifeInBlocks() * 1.5)) {
            helper.fail("Forging well must buy markedly more tool life: "
                    + good.remainingLifeInBlocks() + " vs " + poor.remainingLifeInBlocks());
            return;
        }
        if (!cracked.cracked()) {
            helper.fail("A cracked head must make a cracked tool, or chipping never happens");
            return;
        }
        if (cracked.speedMultiplier() <= 0) {
            helper.fail("Even a cracked tool must cut");
            return;
        }

        // The other rung: grains pressed straight into a head, no forging. A different
        // branch of the same recipe, so it gets looked at rather than assumed.
        var pressedHolder = manager.byKey(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        Astronima.MODID, "improvised_pickaxe"))).orElse(null);
        if (pressedHolder == null
                || !(pressedHolder.value() instanceof play.xponer.astronima.crafting.ForgedToolRecipe pressedRecipe)) {
            helper.fail("The improvised pickaxe recipe is missing or the wrong type");
            return;
        }
        ItemStack grains = new ItemStack(ModItems.IRON_NICKEL_GRAINS.get());
        ItemStack rod = new ItemStack(ModItems.IRON_ROD.get());
        ItemStack improvised = pressedRecipe.assemble(
                net.minecraft.world.item.crafting.CraftingInput.of(3, 3, java.util.List.of(
                        grains.copy(), grains.copy(), grains.copy(),
                        ItemStack.EMPTY, rod.copy(), ItemStack.EMPTY,
                        ItemStack.EMPTY, rod.copy(), ItemStack.EMPTY)));

        ToolHead pressed = improvised.get(ModDataComponents.TOOL_STATE.get());
        if (pressed == null) {
            helper.fail("An improvised pickaxe carried no tool state");
            return;
        }
        if (!(pressed.remainingLifeInBlocks() * 3 < good.remainingLifeInBlocks())) {
            helper.fail("Forging must be worth far more than pressing, or the forge is"
                    + " pointless: " + pressed.remainingLifeInBlocks()
                    + " vs " + good.remainingLifeInBlocks());
            return;
        }
        if (pressed.resharpenings() >= good.resharpenings()) {
            helper.fail("A green compact should have fewer grinds in it than a forged head");
            return;
        }
        helper.succeed();
    }

    /** Three heads of a given quality and two rods, in the recipe's pattern. */
    private static ItemStack craftPickaxe(play.xponer.astronima.crafting.ForgedToolRecipe recipe,
                                          float quality, boolean cracked) {
        ItemStack head = new ItemStack(ModItems.TOOL_HEAD.get());
        head.set(ModDataComponents.METAL_QUALITY.get(), quality);
        head.set(ModDataComponents.METAL_CRACKED.get(), cracked);
        ItemStack rod = new ItemStack(ModItems.IRON_ROD.get());

        return recipe.assemble(net.minecraft.world.item.crafting.CraftingInput.of(3, 3,
                java.util.List.of(
                        head.copy(), head.copy(), head.copy(),
                        ItemStack.EMPTY, rod.copy(), ItemStack.EMPTY,
                        ItemStack.EMPTY, rod.copy(), ItemStack.EMPTY)));
    }

    @SuppressWarnings("unused")
    private static final Object PIPES_MOVE_GAS =
            TEST_FUNCTIONS.register("pipes_move_gas", () -> ModTestFunctions::pipesMoveGas);

    /**
     * Two sealed rooms, one with air and one without, joined by real placed pipe.
     *
     * <p>The unit tests already prove the maths conserves gas and settles. What they
     * cannot prove is that the blocks are wired to it at all — that a port finds its
     * room, that the flood fill walks the run, that the ticker fires. A pipe network
     * that computes beautifully and is connected to nothing looks identical from a
     * green test suite, which is exactly the failure this exists to catch.
     */
    private static void pipesMoveGas(GameTestHelper helper) {
        BlockPos leftInside = new BlockPos(2, 2, 2);
        BlockPos rightInside = new BlockPos(2, 2, 8);
        buildBoxAround(helper, leftInside);
        buildBoxAround(helper, rightInside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        BlockPos leftAbs = helper.absolutePos(leftInside);
        BlockPos rightAbs = helper.absolutePos(rightInside);

        RoomState left = atmosphere.roomAt(leftAbs);
        RoomState right = atmosphere.roomAt(rightAbs);
        if (left == null || right == null) {
            helper.fail("Both boxes should be rooms before any plumbing is placed");
            return;
        }
        // Charge one side only. The other is vacuum, which is the interesting case:
        // this is a habitat being pressurised from a neighbour.
        left.addGasAt(Gas.OXYGEN, 60, 293.15);
        right.removeGas(Gas.OXYGEN, right.gases().get(Gas.OXYGEN));
        double startingRight = right.gases().get(Gas.OXYGEN);
        double startingTotal = left.gases().get(Gas.OXYGEN) + startingRight;

        // A port in each box's wall, opening inward, joined by a run of pipe between.
        BlockPos leftPort = leftInside.offset(0, 0, 2);
        BlockPos rightPort = rightInside.offset(0, 0, -2);
        helper.setBlock(leftPort, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.NORTH));
        helper.setBlock(rightPort, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.SOUTH));
        for (int z = leftPort.getZ() + 1; z < rightPort.getZ(); z++) {
            helper.setBlock(new BlockPos(leftPort.getX(), leftPort.getY(), z),
                    ModBlocks.GAS_PIPE.get().defaultBlockState());
        }
        atmosphere.invalidate(helper.absolutePos(leftPort));
        atmosphere.invalidate(helper.absolutePos(rightPort));

        PipeNetworks.Resolved resolved =
                PipeNetworks.resolve(helper.getLevel(), helper.absolutePos(leftPort));
        if (resolved == null) {
            helper.fail("The placed run did not resolve into a network at all");
            return;
        }
        if (resolved.roomCount() != 2) {
            helper.fail("Expected the run to join exactly two rooms, got "
                    + resolved.roomCount());
            return;
        }

        for (int step = 0; step < 600; step++) {
            resolved.network().tick(1.0);
        }

        RoomState settledRight = atmosphere.roomAt(rightAbs);
        RoomState settledLeft = atmosphere.roomAt(leftAbs);
        if (settledRight == null || settledLeft == null) {
            helper.fail("A room stopped existing while the plumbing ran");
            return;
        }
        double endingRight = settledRight.gases().get(Gas.OXYGEN);
        if (!(endingRight > startingRight + 1)) {
            helper.fail("Gas did not cross the pipe: the far room went from "
                    + startingRight + " to " + endingRight);
            return;
        }
        double endingTotal = settledLeft.gases().get(Gas.OXYGEN) + endingRight;
        if (Math.abs(endingTotal - startingTotal) > startingTotal * 1e-6) {
            helper.fail("Plumbing leaked or invented gas: " + startingTotal
                    + " became " + endingTotal);
            return;
        }
        // A pipe equalises and stops; it must not keep pushing past equilibrium.
        if (endingRight > startingTotal * 0.75) {
            helper.fail("A passive pipe moved more than equalisation allows: "
                    + endingRight + " of " + startingTotal);
            return;
        }

        // Breaking a port must isolate the room, or there is no way to stop a leak.
        helper.setBlock(leftPort, Blocks.AIR.defaultBlockState());
        if (PipeNetworks.resolve(helper.getLevel(), helper.absolutePos(rightPort)) != null) {
            helper.fail("Breaking a port left the network intact - a leak could not be stopped");
            return;
        }
        helper.succeed();
    }

    @SuppressWarnings("unused")
    private static final Object PUMP_LIFTS_PRESSURE =
            TEST_FUNCTIONS.register("pump_lifts_pressure",
                    () -> ModTestFunctions::pumpLiftsPressure);

    /**
     * The thing a pipe cannot do.
     *
     * <p>A pipe equalises and stops. A pump does work, so it must be able to drive a
     * tank <em>above</em> the pressure of the room feeding it — and it must stall at
     * the vessel's rating rather than driving on to burst, because that is what makes
     * filling a tank safe by default rather than safe if you are watching.
     */
    private static void pumpLiftsPressure(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        buildBoxAround(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        RoomState room = atmosphere.roomAt(helper.absolutePos(inside));
        if (room == null) {
            helper.fail("The room should exist before plumbing is placed");
            return;
        }
        room.addGasAt(Gas.OXYGEN, 400, 293.15);
        double roomPressureBefore = room.pressureKPa();

        // room -[port]- pipe - pump -> tank
        BlockPos port = inside.offset(0, 0, 2);
        BlockPos pipe = port.offset(0, 0, 1);
        BlockPos pump = pipe.offset(0, 0, 1);
        BlockPos tank = pump.offset(0, 0, 1);

        helper.setBlock(port, ModBlocks.GAS_PORT.get().defaultBlockState()
                .setValue(GasPortBlock.FACING, Direction.NORTH));
        helper.setBlock(pipe, ModBlocks.GAS_PIPE.get().defaultBlockState());
        helper.setBlock(pump, ModBlocks.GAS_PUMP.get().defaultBlockState()
                .setValue(GasPumpBlock.FACING, Direction.SOUTH));
        helper.setBlock(tank, ModBlocks.GAS_TANK.get().defaultBlockState());
        atmosphere.invalidate(helper.absolutePos(port));

        if (!(helper.getBlockEntity(tank, GasTankBlockEntity.class)
                instanceof GasTankBlockEntity vessel)) {
            helper.fail("The tank has no block entity to hold gas in");
            return;
        }

        BlockState pumpState = helper.getBlockState(pump);
        GasPumpBlockEntity motor = helper.getBlockEntity(pump, GasPumpBlockEntity.class);
        if (motor == null) {
            helper.fail("The pump has no block entity");
            return;
        }

        double startingTotal = room.gases().get(Gas.OXYGEN)
                + vessel.contents().gases().get(Gas.OXYGEN);
        for (int step = 0; step < 4000; step++) {
            GasTransfer.pump(room, vessel.contents(),
                    GasPumpBlockEntity.DISPLACEMENT_M3_PER_S, 1.0,
                    PressureVessel.WORKING_PRESSURE_KPA);
        }

        double tankPressure = vessel.pressureKPa();
        if (!(tankPressure > roomPressureBefore)) {
            helper.fail("A pump must raise the tank above the room feeding it: tank "
                    + tankPressure + " kPa vs room " + roomPressureBefore + " kPa");
            return;
        }
        if (vessel.condition() != PressureVessel.Condition.NOMINAL) {
            helper.fail("A pump stalling at the working pressure must never overpressure"
                    + " a vessel, got " + vessel.condition() + " at " + tankPressure);
            return;
        }
        double endingTotal = room.gases().get(Gas.OXYGEN)
                + vessel.contents().gases().get(Gas.OXYGEN);
        if (Math.abs(endingTotal - startingTotal) > startingTotal * 1e-6) {
            helper.fail("Pumping leaked or invented gas: " + startingTotal
                    + " became " + endingTotal);
            return;
        }

        // A backwards pump must move nothing. The suction and discharge faces are the
        // whole mechanic, so a pump that worked either way round would make the
        // geometry that teaches it a lie.
        double tankBefore = vessel.contents().gases().get(Gas.OXYGEN);
        RoomState emptyRoom = new play.xponer.astronima.sim.RoomState(
                99, 27, new play.xponer.astronima.sim.GasMixture(), 293.15);
        for (int step = 0; step < 500; step++) {
            // Tank as the inlet, empty room as the outlet: the wrong way round.
            GasTransfer.pump(vessel.contents(), emptyRoom,
                    GasPumpBlockEntity.DISPLACEMENT_M3_PER_S, 1.0,
                    PressureVessel.WORKING_PRESSURE_KPA);
        }
        if (emptyRoom.gases().get(Gas.OXYGEN) <= 0) {
            helper.fail("A pump reversed should still move gas the way it faces;"
                    + " it moved nothing at all, so direction is not being applied");
            return;
        }
        if (vessel.contents().gases().get(Gas.OXYGEN) >= tankBefore) {
            helper.fail("Reversing the pump did not draw from the tank, so the"
                    + " suction and discharge faces are interchangeable");
            return;
        }

        // A shut valve must stop the PUMP, not merely the passive edges.
        //
        // The previous version of this test asserted that a shut valve produced
        // zero-conductance edges, and passed the whole time a shut valve did nothing
        // whatsoever to a pump — because the pump never read the edges. It resolved the
        // volume on each face and pumped straight between them. Reported from play:
        // six pumps behind six valves, closing every valve changed nothing.
        helper.setBlock(pipe, ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(GasValveBlock.SETTING, 0));
        PipeNetworks.Resolved throughShut = PipeNetworks.resolveSide(
                helper.getLevel(), helper.absolutePos(pipe));
        if (throughShut != null && throughShut.network().nodes().size() > 1) {
            helper.fail("A shut valve must sever the run; the walk still reached "
                    + throughShut.network().nodes().size() + " volumes through it");
            return;
        }

        // And a part-open valve must throttle it rather than being ignored.
        helper.setBlock(pipe, ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(GasValveBlock.SETTING, 1));
        PipeNetworks.Resolved throttled = PipeNetworks.resolveSide(
                helper.getLevel(), helper.absolutePos(pipe));
        if (throttled == null || !(throttled.openFraction() < 1.0)) {
            helper.fail("A part-open valve must report a restriction the pump can honour");
            return;
        }

        // The old assertion, kept: the tightest valve sets the whole run.
        helper.setBlock(pipe, ModBlocks.GAS_VALVE.get().defaultBlockState()
                .setValue(GasValveBlock.SETTING, 0));
        PipeNetworks.Resolved shut =
                PipeNetworks.resolveSide(helper.getLevel(), helper.absolutePos(port));
        if (shut != null) {
            for (GasNetwork.Edge edge : shut.network().edges()) {
                if (edge.conductance() > 0) {
                    helper.fail("A shut valve still passed gas, so nothing can be isolated");
                    return;
                }
            }
        }
        helper.succeed();
    }

    @SuppressWarnings("unused")
    private static final Object ROOM_IDENTITY_STABLE =
            TEST_FUNCTIONS.register("room_identity_stable", () -> ModTestFunctions::roomIdentityStable);

    /**
     * A room keeps its identity through ordinary building work.
     *
     * <p>Reported from play: placing a door inside a room, or opening and closing one,
     * minted a fresh room id every time. The cause was that identity required the
     * previous room to be consumed down to nothing, so a single placed block — which
     * takes one cell out of the enclosure and leaves the old room holding that orphan
     * cell — was enough to make the game consider it a different room.
     *
     * <p>This walks the exact sequence a player does: build a sealed box, note the id,
     * put a door in the middle of it, open it, close it, break it. The id must survive
     * all of it, because a person would say it is obviously the same room throughout.
     */
    private static void roomIdentityStable(GameTestHelper helper) {
        BlockPos inside = new BlockPos(2, 2, 2);
        buildSealedBox(helper, inside);

        Atmosphere atmosphere = Atmosphere.get(helper.getLevel());
        BlockPos absoluteInside = helper.absolutePos(inside);
        long original = requireRoomId(helper, atmosphere, absoluteInside, "after building");
        if (original < 0) {
            return;
        }

        // A door placed in open floor inside the room: the case that was broken.
        BlockPos doorPos = inside.offset(1, 0, 0);
        helper.setBlock(doorPos, ModBlocks.BULKHEAD_DOOR.get().defaultBlockState());
        atmosphere.invalidate(helper.absolutePos(doorPos));
        if (!sameRoom(helper, atmosphere, absoluteInside, original, "placing a door inside")) {
            return;
        }

        // Toggling it must not re-identify the room either.
        BlockState door = helper.getBlockState(doorPos);
        helper.setBlock(doorPos, door.setValue(net.minecraft.world.level.block.state.properties
                .BlockStateProperties.OPEN, true));
        atmosphere.invalidate(helper.absolutePos(doorPos));
        if (!sameRoom(helper, atmosphere, absoluteInside, original, "opening the door")) {
            return;
        }

        helper.setBlock(doorPos, helper.getBlockState(doorPos).setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, false));
        atmosphere.invalidate(helper.absolutePos(doorPos));
        if (!sameRoom(helper, atmosphere, absoluteInside, original, "closing the door")) {
            return;
        }

        // And breaking it puts the cell back, which is still the same room.
        helper.setBlock(doorPos, Blocks.AIR.defaultBlockState());
        atmosphere.invalidate(helper.absolutePos(doorPos));
        if (!sameRoom(helper, atmosphere, absoluteInside, original, "breaking the door")) {
            return;
        }
        helper.succeed();
    }

    /**
     * A sealed 3x3x3 shell centred on {@code inside}.
     *
     * <p>Unlike {@link #buildSealedBox}, which writes a box at fixed coordinates
     * whatever it is handed, this one actually goes where it is told — which is what
     * lets a test build two rooms and reason about gas moving between them.
     */
    static void buildBoxAround(GameTestHelper helper, BlockPos inside) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean shell = Math.abs(dx) == 2 || Math.abs(dy) == 2 || Math.abs(dz) == 2;
                    helper.setBlock(inside.offset(dx, dy, dz), shell
                            ? ModBlocks.HULL_PLATE.get().defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void buildSealedBox(GameTestHelper helper, BlockPos inside) {
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    boolean shell = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
                    helper.setBlock(new BlockPos(x, y, z),
                            shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static long requireRoomId(GameTestHelper helper, Atmosphere atmosphere,
                                      BlockPos absolutePos, String stage) {
        Atmosphere.RoomReading reading = atmosphere.readingNear(absolutePos);
        if (reading == null) {
            helper.fail("No room " + stage);
            return -1;
        }
        return reading.state().id();
    }

    private static boolean sameRoom(GameTestHelper helper, Atmosphere atmosphere,
                                    BlockPos absolutePos, long expected, String stage) {
        long id = requireRoomId(helper, atmosphere, absolutePos, stage);
        if (id < 0) {
            return false;
        }
        if (id != expected) {
            helper.fail("Room id changed from " + expected + " to " + id + " after " + stage
                    + " — a player would call that the same room");
            return false;
        }
        return true;
    }

    @SuppressWarnings("unused")
    private static final Object SUIT_DEGRADATION =
            TEST_FUNCTIONS.register("suit_degradation", () -> ModTestFunctions::suitDegradation);

    /**
     * The whole degradation loop on a real item stack: a repair records its quality,
     * use spends it, and a worn-out subsystem reads as broken again.
     *
     * <p>Worth testing here rather than only in unit tests because it spans the item's
     * data components, the wear encoding, and the condition mask — three things that
     * each work alone and could easily disagree with each other.
     */
    private static void suitDegradation(GameTestHelper helper) {
        ItemStack suit = EvaSuitItem.wrecked(ModItems.EVA_SUIT.get());

        // A clean repair and a fumbled one must not start in the same condition.
        EvaSuitItem.repair(suit, SuitSubsystem.HELMET_SEAL, 1.0f);
        EvaSuitItem.repair(suit, SuitSubsystem.REGULATOR, 0.4f);
        int wear = EvaSuitItem.wearOf(suit);
        int clean = SuitWear.lifeOf(wear, SuitSubsystem.HELMET_SEAL);
        int fumbled = SuitWear.lifeOf(wear, SuitSubsystem.REGULATOR);
        if (clean <= fumbled) {
            helper.fail("A clean repair (" + clean + ") must outlast a fumbled one (" + fumbled + ")");
            return;
        }
        if (!SuitCondition.isWorking(EvaSuitItem.conditionOf(suit), SuitSubsystem.REGULATOR)) {
            helper.fail("A fumbled repair must still work when it is finished");
            return;
        }

        // Wearing the regulator out must make the suit report it as broken again.
        EvaSuitItem.setWear(suit, SuitWear.withLife(EvaSuitItem.wearOf(suit),
                SuitSubsystem.REGULATOR, 0));
        if (SuitCondition.isWorking(EvaSuitItem.conditionOf(suit), SuitSubsystem.REGULATOR)) {
            helper.fail("A worn-out subsystem must read as a fault");
            return;
        }
        if (!SuitCondition.isWorking(EvaSuitItem.conditionOf(suit), SuitSubsystem.HELMET_SEAL)) {
            helper.fail("Wearing out one subsystem must not disturb another");
            return;
        }

        // And it must be repairable again, which is what stops wear being a dead end.
        if (!EvaSuitItem.repair(suit, SuitSubsystem.REGULATOR, 1.0f)) {
            helper.fail("A worn-out subsystem must accept a fresh repair");
            return;
        }
        if (SuitWear.lifeOf(EvaSuitItem.wearOf(suit), SuitSubsystem.REGULATOR) != SuitWear.FULL) {
            helper.fail("Repairing it again should restore full life");
            return;
        }

        // Stress must survive a copy, or a suit would reset by being moved.
        ItemStack copy = suit.copy();
        if (EvaSuitItem.wearOf(copy) != EvaSuitItem.wearOf(suit)
                || EvaSuitItem.stressOf(copy) != EvaSuitItem.stressOf(suit)) {
            helper.fail("Wear and stress must travel with the item");
            return;
        }
        helper.succeed();
    }

    @SuppressWarnings("unused")
    private static final Object EVERY_SUIT_SUBSYSTEM_HAS_A_REPAIR_PART =
            TEST_FUNCTIONS.register("every_suit_subsystem_has_a_repair_part",
                    () -> ModTestFunctions::everySuitSubsystemHasARepairPart);

    /**
     * The gap that left sealed gloves unrepairable forever: {@code SuitSubsystem}'s own
     * flavour text can name a repair part that no {@code SuitRepairItem} was ever wired
     * to (PLAN.md rule 57). Walks the real item registry — a hand-kept list here would
     * have the exact same blind spot that let the gap ship in the first place.
     */
    private static void everySuitSubsystemHasARepairPart(GameTestHelper helper) {
        java.util.EnumSet<SuitSubsystem> covered = java.util.EnumSet.noneOf(SuitSubsystem.class);
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof SuitRepairItem part)) {
                continue;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(Astronima.MODID)) {
                continue;
            }
            for (SuitSubsystem subsystem : SuitSubsystem.values()) {
                if (part.handles(subsystem)) {
                    covered.add(subsystem);
                }
            }
        }
        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            if (!covered.contains(subsystem)) {
                helper.fail(subsystem + " has no registered SuitRepairItem — it can never be repaired");
                return;
            }
        }
        helper.succeed();
    }

    @SuppressWarnings("unused")
    private static final Object RECIPE_OUTPUTS_STACK =
            TEST_FUNCTIONS.register("recipe_outputs_stack", () -> ModTestFunctions::recipeOutputsStack);

    /**
     * Crafting cannot hand back more of an item than a stack can hold, so a recipe
     * whose count exceeds its result's stack size silently loses the remainder.
     *
     * <p>This is easy to introduce from a distance: making an item non-stackable
     * lives in {@code ModItems}, while the recipe count lives in {@code CraftingTree},
     * and nothing connects the two. It happened once already when the LiOH cartridge
     * became a fitted component. Checking the whole tree costs nothing and means it
     * cannot happen quietly again.
     */
    private static void recipeOutputsStack(GameTestHelper helper) {
        for (CraftingTree.Source source : CraftingTree.sources()) {
            String result;
            int count;
            switch (source) {
                case CraftingTree.Shaped shaped -> {
                    result = shaped.result();
                    count = shaped.count();
                }
                case CraftingTree.Shapeless shapeless -> {
                    result = shapeless.result();
                    count = shapeless.count();
                }
                default -> {
                    continue; // cooking and world sources yield a single item
                }
            }
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(result));
            int maxStack = new ItemStack(item).getMaxStackSize();
            if (count > maxStack) {
                helper.fail(result + " is crafted " + count + " at a time but stacks to "
                        + maxStack + " — the surplus would be discarded");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The opening arc in miniature: a suit out of the wreck is useless, each part
     * restores exactly one subsystem, and a tank only becomes usable once the suit
     * can both seal and feed — which is what makes repair a sequence.
     */
    private static void suitRepairCycle(GameTestHelper helper) {
        ItemStack suit = EvaSuitItem.wrecked(ModItems.EVA_SUIT.get());
        if (SuitCondition.canHoldPressure(EvaSuitItem.conditionOf(suit))) {
            helper.fail("A wrecked suit must not hold pressure");
            return;
        }
        if (SuitCondition.tankDrainMultiplier(EvaSuitItem.conditionOf(suit)) != 2.0) {
            helper.fail("A broken regulator should double tank drain");
            return;
        }

        if (!EvaSuitItem.repair(suit, SuitSubsystem.HELMET_SEAL)) {
            helper.fail("The helmet seal should have been repairable");
            return;
        }
        if (EvaSuitItem.repair(suit, SuitSubsystem.HELMET_SEAL)) {
            helper.fail("Repairing a working subsystem must be refused, not waste the part");
            return;
        }
        if (SuitCondition.canHoldPressure(EvaSuitItem.conditionOf(suit))) {
            helper.fail("A sealed helmet alone is still not a working suit");
            return;
        }

        EvaSuitItem.repair(suit, SuitSubsystem.TANK_MOUNT);
        if (!SuitCondition.canHoldPressure(EvaSuitItem.conditionOf(suit))) {
            helper.fail("Seal plus tank mount should hold pressure");
            return;
        }

        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            EvaSuitItem.repair(suit, subsystem);
        }
        if (!SuitCondition.isFullyRepaired(EvaSuitItem.conditionOf(suit))) {
            helper.fail("Fitting every part should rebuild the suit");
            return;
        }
        if (SuitCondition.tankDrainMultiplier(EvaSuitItem.conditionOf(suit)) != 1.0) {
            helper.fail("A repaired regulator should stop wasting gas");
            return;
        }
        // The condition must survive being written to and read back from the stack.
        ItemStack copy = suit.copy();
        if (EvaSuitItem.conditionOf(copy) != EvaSuitItem.conditionOf(suit)) {
            helper.fail("Repair state must travel with the item");
            return;
        }
        helper.succeed();
    }

    @SuppressWarnings("unused")
    private static final Object FURNACE_NEEDS_OXYGEN =
            TEST_FUNCTIONS.register("furnace_needs_oxygen", () -> ModTestFunctions::furnaceNeedsOxygen);

    /**
     * A furnace burns fuel, so it needs oxygen and it competes with the player's
     * lungs for it: smelting in a sealed room must visibly draw the room's oxygen
     * down and put CO2 back.
     */
    private static void furnaceNeedsOxygen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = buildHullBox(level, helper.absolutePos(BlockPos.ZERO).offset(4, 20, 4));
        Atmosphere atmosphere = Atmosphere.get(level);
        if (!atmosphere.pressurizeWithEarthAir(center)) {
            helper.fail("Could not pressurize test module");
            return;
        }
        BlockPos furnacePos = center.offset(1, -1, 0);
        level.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());

        RoomState room = atmosphere.roomAt(center);
        if (room == null) {
            helper.fail("No room around the furnace");
            return;
        }
        double o2Before = room.gases().get(Gas.OXYGEN);
        double co2Before = room.gases().get(Gas.CARBON_DIOXIDE);

        // Drive the mixin directly: a real smelt needs fuel and input we do not need
        // to model here, and this isolates the atmosphere contract under test.
        if (!(level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace)) {
            helper.fail("Furnace block entity missing");
            return;
        }
        for (int i = 0; i < 40; i++) {
            AbstractFurnaceBlockEntity.serverTick(level, furnacePos, level.getBlockState(furnacePos), furnace);
        }

        RoomState after = atmosphere.roomAt(center);
        if (after == null || after.gases().get(Gas.OXYGEN) >= o2Before) {
            helper.fail("A running furnace must consume room oxygen");
            return;
        }
        if (after.gases().get(Gas.CARBON_DIOXIDE) <= co2Before) {
            helper.fail("Combustion must exhaust CO2 into the room");
            return;
        }

        // Starve the room: the furnace must stall rather than smelt in vacuum.
        after.gases().extractFraction(1.0);
        double emptied = after.gases().totalMoles();
        for (int i = 0; i < 20; i++) {
            AbstractFurnaceBlockEntity.serverTick(level, furnacePos, level.getBlockState(furnacePos), furnace);
        }
        RoomState starved = atmosphere.roomAt(center);
        if (starved == null || starved.gases().totalMoles() > emptied + 1e-6) {
            helper.fail("A starved furnace must not keep burning");
            return;
        }
        helper.succeed();
    }

    @SuppressWarnings("unused")
    private static final Object FLAMES_NEED_OXYGEN =
            TEST_FUNCTIONS.register("flames_need_oxygen", () -> ModTestFunctions::flamesNeedOxygen);

    /**
     * Regression for "I walked into a methane room with a torch and nothing
     * happened", and for flames surviving in vacuum. A torch placed in a breathable
     * room stays lit; the same torch in an evacuated room goes out.
     */
    private static void flamesNeedOxygen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = buildHullBox(level, helper.absolutePos(BlockPos.ZERO).offset(4, 20, 4));
        Atmosphere atmosphere = Atmosphere.get(level);
        if (!atmosphere.pressurizeWithEarthAir(center)) {
            helper.fail("Could not pressurize test module");
            return;
        }

        BlockPos torchPos = center.offset(1, 0, 0);
        level.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState());
        if (CombustionEvents.snuffIfStarved(level, torchPos, level.getBlockState(torchPos))) {
            helper.fail("A torch in breathable air must stay lit");
            return;
        }

        // Evacuate the room: the same torch can no longer burn.
        RoomState room = atmosphere.roomAt(center);
        if (room == null) {
            helper.fail("No room after pressurizing");
            return;
        }
        room.gases().extractFraction(1.0);
        if (!CombustionEvents.snuffIfStarved(level, torchPos, level.getBlockState(torchPos))) {
            helper.fail("A torch in vacuum must go out");
            return;
        }
        // It must become an unlit torch, not vanish: the block stays put, gives no
        // light, and can be relit. A separate block (rather than a state flag) is what
        // stops dynamic-lighting mods lighting the way through hard vacuum.
        if (!level.getBlockState(torchPos).is(ModBlocks.UNLIT_TORCH.get())) {
            helper.fail("A snuffed torch should become an unlit torch, was "
                    + level.getBlockState(torchPos));
            return;
        }
        if (level.getBlockState(torchPos).getLightEmission() != 0) {
            helper.fail("An unlit torch must emit no light");
            return;
        }

        // Relighting must be refused while the air still cannot carry a flame.
        if (UnlitTorchBlock.relight(level, torchPos, level.getBlockState(torchPos))) {
            helper.fail("Relighting should fail in vacuum");
            return;
        }
        // Restore the air and it lights again.
        atmosphere.pressurizeWithEarthAir(center);
        if (!UnlitTorchBlock.relight(level, torchPos, level.getBlockState(torchPos))
                || !level.getBlockState(torchPos).is(Blocks.TORCH)) {
            helper.fail("A torch should relight once there is oxygen again");
            return;
        }
        helper.succeed();
    }

    /**
     * Regression for the field report "no gases showed in the analyzer": an enclosed
     * volume too large to pressurize must still hold — and report — whatever gas is
     * released into it. Only a path to vacuum may empty a space.
     */
    private static void unsealableHoldsGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = buildHullBox(level, helper.absolutePos(BlockPos.ZERO).offset(4, 20, 4));
        Atmosphere atmosphere = Atmosphere.get(level);

        // Make the volume unpressurizable *before* it is first scanned, so this tests
        // venting alone — shrinking the cap under a live room would instead exercise
        // the split/inherit path, which legitimately divides gas between fragments.
        int previousCap = Config.MAX_ROOM_VOLUME.get();
        Config.MAX_ROOM_VOLUME.set(8);
        try {
            atmosphere.invalidate(center);
            Atmosphere.RoomReading reading = atmosphere.readingNear(center);
            if (reading == null) {
                helper.fail("Oversized enclosure should still be a readable volume");
                return;
            }
            if (reading.sealed() || reading.openToSpace()) {
                helper.fail("Expected an enclosed-but-unsealable volume, sealed=" + reading.sealed()
                        + " openToSpace=" + reading.openToSpace());
                return;
            }

            // Simulate a breached pocket venting into the space.
            RoomState room = reading.state();
            room.addGasAt(Gas.METHANE, 40.0, room.temperatureK());

            // Tick the atmosphere: the old bug emptied this volume to vacuum.
            for (int i = 0; i < 5; i++) {
                atmosphere.tick();
            }
            RoomState after = atmosphere.roomAt(center);
            if (after == null || after.gases().get(Gas.METHANE) < 39.9) {
                helper.fail("Unsealable enclosure must retain its gas, methane="
                        + (after == null ? "no room" : after.gases().get(Gas.METHANE)));
                return;
            }
        } finally {
            Config.MAX_ROOM_VOLUME.set(previousCap);
        }
        helper.succeed();
    }

    /** Builds a hull box with a 3×3×3 interior; returns the interior center. */
    private static BlockPos buildHullBox(ServerLevel level, BlockPos base) {
        BlockPos interiorCenter = base.offset(2, 2, 2);
        for (BlockPos pos : BlockPos.betweenClosed(base, base.offset(4, 4, 4))) {
            boolean interior = Math.abs(pos.getX() - interiorCenter.getX()) <= 1
                    && Math.abs(pos.getY() - interiorCenter.getY()) <= 1
                    && Math.abs(pos.getZ() - interiorCenter.getZ()) <= 1;
            level.setBlockAndUpdate(pos, interior
                    ? Blocks.AIR.defaultBlockState()
                    : ModBlocks.HULL_PLATE.get().defaultBlockState());
        }
        return interiorCenter;
    }

    /** A methane-air mixture in the flammable window must explode when sparked. */
    private static void methaneIgnition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = buildHullBox(level, helper.absolutePos(BlockPos.ZERO).offset(4, 20, 4));
        Atmosphere atmosphere = Atmosphere.get(level);
        if (!atmosphere.pressurizeWithEarthAir(center)) {
            helper.fail("Could not pressurize test module");
            return;
        }
        RoomState room = atmosphere.roomAt(center);
        if (room == null) {
            helper.fail("No room in test module");
            return;
        }
        room.addGasAt(Gas.METHANE, room.gases().totalMoles() * 0.10, room.temperatureK());

        if (!Ignition.tryIgnite(level, center)) {
            helper.fail("Flammable mixture failed to ignite");
            return;
        }
        RoomState burnt = atmosphere.roomAt(center);
        if (burnt != null && burnt.gases().fraction(Gas.METHANE) > 0.02) {
            helper.fail("Methane should have burned, fraction=" + burnt.gases().fraction(Gas.METHANE));
            return;
        }
        helper.succeed();
    }

    /** The same fuel with no oxygen at all must not ignite — vacuum is fire-safe. */
    private static void methaneNoOxygen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = buildHullBox(level, helper.absolutePos(BlockPos.ZERO).offset(4, 20, 4));
        RoomState room = Atmosphere.get(level).roomAt(center);
        if (room == null) {
            helper.fail("No room in test module");
            return;
        }
        room.addGasAt(Gas.METHANE, 60.0, room.temperatureK());
        room.addGasAt(Gas.NITROGEN, 400.0, room.temperatureK());

        if (Ignition.tryIgnite(level, center)) {
            helper.fail("Ignited with no oxygen present");
            return;
        }
        RoomState after = Atmosphere.get(level).roomAt(center);
        if (after == null || Math.abs(after.gases().get(Gas.METHANE) - 60.0) > 1e-6) {
            helper.fail("Fuel should be untouched without oxygen");
            return;
        }
        helper.succeed();
    }

    /**
     * Builds a hull-plate box with a 3×3×3 interior, pressurizes it, and verifies the
     * room system reports a sealed, breathable 27-block room; then knocks a hole in
     * the wall and verifies the room reads unsealed.
     */
    private static void sealedModule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).offset(4, 20, 4);
        BlockPos interiorCenter = base.offset(2, 2, 2);

        for (BlockPos pos : BlockPos.betweenClosed(base, base.offset(4, 4, 4))) {
            boolean interior = Math.abs(pos.getX() - interiorCenter.getX()) <= 1
                    && Math.abs(pos.getY() - interiorCenter.getY()) <= 1
                    && Math.abs(pos.getZ() - interiorCenter.getZ()) <= 1;
            level.setBlockAndUpdate(pos, interior
                    ? Blocks.AIR.defaultBlockState()
                    : ModBlocks.HULL_PLATE.get().defaultBlockState());
        }

        Atmosphere atmosphere = Atmosphere.get(level);
        Atmosphere.RoomReading reading = atmosphere.readingAt(interiorCenter);
        if (reading == null) {
            helper.fail("No room detected inside the hull box");
            return;
        }
        if (!reading.sealed()) {
            helper.fail("Hull box should read sealed");
            return;
        }
        if (reading.state().volumeBlocks() != 27) {
            helper.fail("Expected 27-block room, got " + reading.state().volumeBlocks());
            return;
        }

        if (!atmosphere.pressurizeWithEarthAir(interiorCenter)) {
            helper.fail("Pressurization failed");
            return;
        }
        RoomState room = atmosphere.roomAt(interiorCenter);
        if (room == null || O2Status.classify(room.partialPressureKPa(Gas.OXYGEN)) != O2Status.NORMAL) {
            helper.fail("Pressurized module should be breathable");
            return;
        }

        // Breach one wall; the room must re-read as unsealed (open to the exterior).
        level.setBlockAndUpdate(base.offset(2, 2, 0), Blocks.AIR.defaultBlockState());
        Atmosphere.RoomReading breached = atmosphere.readingAt(interiorCenter);
        if (breached == null || breached.sealed()) {
            helper.fail("Breached module should read unsealed");
            return;
        }

        // Regression for the live-play door bug: doors toggle with the don't-notify
        // flag, which used to leave rooms stale. Fit a bulkhead door into a two-high
        // doorway and toggle it exactly the way DoorBlock does (flag 10).
        level.setBlockAndUpdate(base.offset(2, 2, 0), ModBlocks.HULL_PLATE.get().defaultBlockState());
        BlockPos doorLower = base.offset(2, 1, 0);
        BlockPos doorUpper = base.offset(2, 2, 0);
        BlockState lowerState = ModBlocks.BULKHEAD_DOOR.get().defaultBlockState();
        level.setBlockAndUpdate(doorLower, lowerState);
        level.setBlockAndUpdate(doorUpper, lowerState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        Atmosphere.RoomReading closedDoor = atmosphere.readingAt(interiorCenter);
        if (closedDoor == null || !closedDoor.sealed()) {
            helper.fail("Room with closed bulkhead door should read sealed");
            return;
        }

        level.setBlock(doorLower, level.getBlockState(doorLower).setValue(DoorBlock.OPEN, true), 10);
        Atmosphere.RoomReading openDoor = atmosphere.readingAt(interiorCenter);
        if (openDoor == null || openDoor.sealed()) {
            helper.fail("Room with open bulkhead door should read unsealed");
            return;
        }

        level.setBlock(doorLower, level.getBlockState(doorLower).setValue(DoorBlock.OPEN, false), 10);
        Atmosphere.RoomReading reclosed = atmosphere.readingAt(interiorCenter);
        if (reclosed == null || !reclosed.sealed()) {
            helper.fail("Re-closing the bulkhead door should re-seal the room");
            return;
        }
        helper.succeed();
    }

    private ModTestFunctions() {}
}
