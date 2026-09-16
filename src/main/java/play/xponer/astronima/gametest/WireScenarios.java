package play.xponer.astronima.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.logic.Circuit;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.sim.wire.WireRouter;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.PartLogic;
import play.xponer.astronima.block.entity.ProcessingBlockEntity;
import play.xponer.astronima.wire.WireChunk;
import play.xponer.astronima.wire.WirePart;
import play.xponer.astronima.wire.Wires;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a player builds out of wire and parts, and what they should see.
 *
 * <p><strong>Written because the control tier shipped with none of this, and it showed.</strong>
 * The reported failure was <em>"попробовал аирлок со свитчом и AND, не сработало, хотя подключил
 * всё правильно"</em> — and there was no way to tell whether the fault was the player's wiring or
 * the mod's, because nothing in the build ever wired anything up. Rule 11 asks for scenarios; the
 * whole of this tier had unit tests on its geometry and its truth tables and not one assertion
 * that a signal ever crossed a wire.
 *
 * <p>Each of these lays real wire with the real router onto real pads, then asks a real gate what
 * it is putting out. The one thing not driven through the player's own hands is the coil click: it
 * needs a hit position inside a block face and the harness has no camera, so the route is laid
 * through {@code Wires.placeAll} — the same call the item makes after routing, with the same
 * router in front of it (rule 14: the assertion moves, it does not vanish).
 *
 * <h2>The layouts got smaller, and that is the point of the tier</h2>
 * These scenarios used to need <strong>a block of clearance between every component</strong>,
 * because two gate blocks flush together left nowhere for the wire between them — and
 * {@code design/electrical.md} records that as "the likeliest explanation of the original report".
 * Parts lie in the wire layer, so two of them now sit on <em>one face of one block</em> with a run
 * between them, and the clearance rule has nothing left to apply to.
 */
public final class WireScenarios {

    @SuppressWarnings("unused")
    private static final Object CARRIES_A_SIGNAL =
            PlayerScenarios.SCENARIOS.register("scenario_wire_carries_a_signal",
                    () -> WireScenarios::wireCarriesASignal);

    @SuppressWarnings("unused")
    private static final Object ONLY_ON_A_TERMINAL =
            PlayerScenarios.SCENARIOS.register("scenario_wire_only_connects_on_a_terminal",
                    () -> WireScenarios::wireOnlyConnectsOnATerminal);

    @SuppressWarnings("unused")
    private static final Object AND_NEEDS_BOTH =
            PlayerScenarios.SCENARIOS.register("scenario_and_gate_needs_both",
                    () -> WireScenarios::andGateNeedsBoth);

    @SuppressWarnings("unused")
    private static final Object COLOURS_ARE_SEPARATE =
            PlayerScenarios.SCENARIOS.register("scenario_colours_are_separate_circuits",
                    () -> WireScenarios::coloursAreSeparateCircuits);

    @SuppressWarnings("unused")
    private static final Object GROUPED_BY_SURFACE_AGREES_WITH_ALL_ON =
            PlayerScenarios.SCENARIOS.register("scenario_grouped_by_surface_agrees_with_all_on",
                    () -> WireScenarios::groupedBySurfaceAgreesWithAllOn);

    @SuppressWarnings("unused")
    private static final Object PARTS_SHARE_A_FACE =
            PlayerScenarios.SCENARIOS.register("scenario_two_parts_share_one_face",
                    () -> WireScenarios::twoPartsShareOneFace);

    @SuppressWarnings("unused")
    private static final Object BURIED_LOGIC_WORKS =
            PlayerScenarios.SCENARIOS.register("scenario_logic_buried_in_a_wall_still_works",
                    () -> WireScenarios::logicBuriedInAWallStillWorks);

    @SuppressWarnings("unused")
    private static final Object SUPPORT_TAKES_IT =
            PlayerScenarios.SCENARIOS.register("scenario_breaking_the_support_drops_the_part",
                    () -> WireScenarios::breakingTheSupportDropsThePart);

    @SuppressWarnings("unused")
    private static final Object SNAPS_TO_A_PAD =
            PlayerScenarios.SCENARIOS.register("scenario_aiming_at_a_part_snaps_to_its_pad",
                    () -> WireScenarios::aimingAtAPartSnapsToItsPad);

    @SuppressWarnings("unused")
    private static final Object SWITCH_IS_CLICKABLE =
            PlayerScenarios.SCENARIOS.register("scenario_a_switch_is_operated_by_clicking_it",
                    () -> WireScenarios::aSwitchIsOperatedByClickingIt);

    @SuppressWarnings("unused")
    private static final Object EXPLODED_WALL =
            PlayerScenarios.SCENARIOS.register("scenario_a_wall_removed_any_way_drops_its_wiring",
                    () -> WireScenarios::aWallRemovedAnyWayDropsItsWiring);

    @SuppressWarnings("unused")
    private static final Object PLACED_BY_HAND =
            PlayerScenarios.SCENARIOS.register("scenario_a_part_is_placed_by_using_the_item",
                    () -> WireScenarios::aPartIsPlacedByUsingTheItem);

    @SuppressWarnings("unused")
    private static final Object BUTTON_SPRINGS_BACK =
            PlayerScenarios.SCENARIOS.register("scenario_a_button_drives_and_springs_back",
                    () -> WireScenarios::aButtonDrivesAndSpringsBack);

    @SuppressWarnings("unused")
    private static final Object WRENCH_TURNS_A_PART =
            PlayerScenarios.SCENARIOS.register("scenario_the_wrench_turns_a_part",
                    () -> WireScenarios::theWrenchTurnsAPart);

    @SuppressWarnings("unused")
    private static final Object CUTTERS_TAKE_A_PART =
            PlayerScenarios.SCENARIOS.register("scenario_cutters_take_a_part_off_the_wall",
                    () -> WireScenarios::cuttersTakeAPartOffTheWall);

    @SuppressWarnings("unused")
    private static final Object A_NAMED_PLATE_KEEPS_ITS_NAME =
            PlayerScenarios.SCENARIOS.register("scenario_a_named_plate_keeps_its_name_off_the_wall",
                    () -> WireScenarios::aNamedPlateKeepsItsNameOffTheWall);

    @SuppressWarnings("unused")
    private static final Object GAUGE_IS_A_DECISION =
            PlayerScenarios.SCENARIOS.register("scenario_a_thin_run_loses_more_than_a_thick_one",
                    () -> WireScenarios::aThinRunLosesMoreThanAThickOne);

    @SuppressWarnings("unused")
    private static final Object THIN_WIRE_BURNS =
            PlayerScenarios.SCENARIOS.register("scenario_an_overloaded_run_cooks_and_then_fails",
                    () -> WireScenarios::anOverloadedRunCooksAndThenFails);

    @SuppressWarnings("unused")
    private static final Object FUSE_GOES_FIRST =
            PlayerScenarios.SCENARIOS.register("scenario_a_fuse_goes_instead_of_the_wire",
                    () -> WireScenarios::aFuseGoesInsteadOfTheWire);

    @SuppressWarnings("unused")
    private static final Object PROTECTION_IS_SIZED =
            PlayerScenarios.SCENARIOS.register("scenario_protection_is_sized_to_the_wire",
                    () -> WireScenarios::protectionIsSizedToTheWire);

    @SuppressWarnings("unused")
    private static final Object BREAKER_RESETS =
            PlayerScenarios.SCENARIOS.register("scenario_a_breaker_is_thrown_back_on",
                    () -> WireScenarios::aBreakerIsThrownBackOn);

    @SuppressWarnings("unused")
    private static final Object A_LONG_THIN_RUN_STARVES =
            PlayerScenarios.SCENARIOS.register("scenario_a_long_thin_run_starves_a_machine",
                    () -> WireScenarios::aLongThinRunStarvesAMachine);

    @SuppressWarnings("unused")
    private static final Object A_SHARED_RUN_RUNS_COOLER =
            PlayerScenarios.SCENARIOS.register("scenario_a_shared_run_runs_cooler",
                    () -> WireScenarios::aSharedRunRunsCooler);

    @SuppressWarnings("unused")
    private static final Object A_SECOND_WIRE_HELPS =
            PlayerScenarios.SCENARIOS.register("scenario_a_second_wire_shares_the_load",
                    () -> WireScenarios::aSecondWireSharesTheLoad);

    @SuppressWarnings("unused")
    private static final Object TRUNK_CARRIES_BOTH =
            PlayerScenarios.SCENARIOS.register("scenario_a_trunk_carries_what_its_branches_draw",
                    () -> WireScenarios::aTrunkCarriesWhatItsBranchesDraw);

    @SuppressWarnings("unused")
    private static final Object A_PLATE_COMPUTES =
            PlayerScenarios.SCENARIOS.register("scenario_a_plate_computes_what_it_was_given",
                    () -> WireScenarios::aPlateComputesWhatItWasGiven);

    @SuppressWarnings("unused")
    private static final Object A_NESTED_SUBCIRCUIT_SURVIVES_SAVING =
            PlayerScenarios.SCENARIOS.register("scenario_a_nested_subcircuit_survives_being_saved",
                    () -> WireScenarios::aNestedSubcircuitSurvivesBeingSaved);

    @SuppressWarnings("unused")
    private static final Object A_FIVE_PIN_NESTED_PLATE_WIRES_THROUGH_A_REAL_MACRO_PLATE =
            PlayerScenarios.SCENARIOS.register("scenario_a_five_pin_nested_plate_wires_through_a_real_macro_plate",
                    () -> WireScenarios::aFivePinNestedPlateWiresThroughARealMacroPlate);

    @SuppressWarnings("unused")
    private static final Object A_REAL_EDGE_TRIGGERED_COUNTER_ACTUALLY_COUNTS =
            PlayerScenarios.SCENARIOS.register("scenario_a_real_edge_triggered_counter_actually_counts",
                    () -> WireScenarios::aRealEdgeTriggeredCounterActuallyCounts);

    @SuppressWarnings("unused")
    private static final Object A_CLOCKED_COUNTER_COUNTS_WITHOUT_ANY_PLAYER_AT_ALL =
            PlayerScenarios.SCENARIOS.register("scenario_a_clocked_counter_counts_without_any_player_at_all",
                    () -> WireScenarios::aClockedCounterCountsWithoutAnyPlayerAtAll);

    @SuppressWarnings("unused")
    private static final Object A_CLOCK_DRIVEN_COUNTER_EVENTUALLY_VISITS_EVERY_VALUE =
            PlayerScenarios.SCENARIOS.register(
                    "scenario_a_clock_driven_counter_eventually_visits_every_value",
                    () -> WireScenarios::aClockDrivenCounterEventuallyVisitsEveryValue);

    @SuppressWarnings("unused")
    private static final Object A_CLOCK_TOGGLES_ON_ITS_OWN_OVER_REAL_TIME =
            PlayerScenarios.SCENARIOS.register("scenario_a_clock_toggles_on_its_own_over_real_time",
                    () -> WireScenarios::aClockTogglesOnItsOwnOverRealTime);

    @SuppressWarnings("unused")
    private static final Object A_RAM_CHIP_WRITES_ONLY_ON_THE_RISING_EDGE =
            PlayerScenarios.SCENARIOS.register("scenario_a_ram_chip_writes_only_on_the_rising_edge",
                    () -> WireScenarios::aRamChipWritesOnlyOnTheRisingEdge);

    @SuppressWarnings("unused")
    private static final Object THE_REFERENCE_MACHINE_DRAWS_A_BAR_FROM_ITS_SWITCHES =
            PlayerScenarios.SCENARIOS.register(
                    "scenario_the_reference_machine_draws_a_bar_from_its_switches",
                    () -> WireScenarios::theReferenceMachineDrawsABarFromItsSwitches);

    @SuppressWarnings("unused")
    private static final Object A_RIBBON_LANDS_ON_A_PAD_ROW_AND_STAYS_SEPARATE =
            PlayerScenarios.SCENARIOS.register("scenario_a_ribbon_lands_on_a_pad_row_and_stays_separate",
                    () -> WireScenarios::aRibbonLandsOnAPadRowAndStaysSeparate);

    @SuppressWarnings("unused")
    private static final Object A_PRE_PIN_COUNT_SAVE_STILL_LOADS =
            PlayerScenarios.SCENARIOS.register("scenario_a_pre_pin_count_saved_plate_still_loads",
                    () -> WireScenarios::aPrePinCountSavedPlateStillLoads);

    @SuppressWarnings("unused")
    private static final Object A_PRE_NAME_FIELD_SAVE_STILL_LOADS =
            PlayerScenarios.SCENARIOS.register("scenario_a_pre_name_field_saved_nested_plate_still_loads",
                    () -> WireScenarios::aPreNameFieldSavedNestedPlateStillLoads);

    @SuppressWarnings("unused")
    private static final Object A_PRE_WIDE_GATE_STATE_SAVE_STILL_LOADS =
            PlayerScenarios.SCENARIOS.register("scenario_a_pre_wide_gate_state_saved_part_still_loads",
                    () -> WireScenarios::aPreWideGateStateSavedPartStillLoads);

    /**
     * The floor everything in here is bolted to.
     *
     * <p>Parts and wire live in the cell <em>above</em> the floor, pressed against its top — so
     * the whole of a scenario is one row of stone and one plane of pixels, which is what a real
     * control board looks like too.
     */
    private static final Direction FACE = Direction.DOWN;

    @SuppressWarnings("unused")
    private static final Object A_SERVO_HOLDS_A_CALIBRATION =
            PlayerScenarios.SCENARIOS.register("scenario_a_servo_holds_a_calibration",
                    () -> WireScenarios::aServoHoldsACalibration);

    @SuppressWarnings("unused")
    private static final Object A_RUN_PAST_A_STUD_IS_NOT_WIRED_TO_IT =
            PlayerScenarios.SCENARIOS.register("scenario_a_run_past_a_stud_is_not_wired_to_it",
                    () -> WireScenarios::aRunPastAStudIsNotWiredToIt);

    @SuppressWarnings("unused")
    private static final Object A_WIRE_ACROSS_A_POWER_STUD_DOES_NOT_CUT_IT =
            PlayerScenarios.SCENARIOS.register(
                    "scenario_a_wire_across_a_power_stud_does_not_cut_it",
                    () -> WireScenarios::aWireAcrossAPowerStudDoesNotCutIt);

    @SuppressWarnings("unused")
    private static final Object A_SERVO_WIRE_IS_NOT_AN_ENABLE =
            PlayerScenarios.SCENARIOS.register("scenario_a_servo_wire_is_not_an_enable",
                    () -> WireScenarios::aServoWireIsNotAnEnable);

    // ---- the scenarios ---------------------------------------------------------

    /**
     * <em>"I flip the switch and the thing at the other end of the wire knows."</em>
     *
     * <p>The most basic claim the control tier makes. Every later scenario is this one plus a
     * component, so if this breaks they all break — and finding out why from any of the others
     * would take an afternoon.
     */
    private static void wireCarriesASignal(GameTestHelper helper) {
        floor(helper, 1, 6);
        WirePart hand = mount(helper, PartType.SWITCH, 1, 2, 6);
        WirePart gate = mount(helper, PartType.GATE_NOT, 3, 4, 6);
        if (hand == null || gate == null || !wire(helper, hand, 0, gate, 0, DyeColor.WHITE)) {
            return;
        }

        // A NOT with a dead input drives; closing the switch must stop it.
        if (!driving(helper, gate)) {
            helper.fail("a NOT gate with nothing on its input should be driving");
            return;
        }
        close(helper, hand);
        if (driving(helper, gate)) {
            helper.fail("the switch closed and the signal never reached the gate along the wire");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I ran the wire past the part and it did nothing."</em>
     *
     * <p>Which is correct, and is the one rule separating this from a toy: a run has to land on
     * the pad. Worth a scenario precisely because the failure is invisible — wire one pixel off a
     * pad looks exactly like wire on it.
     */
    private static void wireOnlyConnectsOnATerminal(GameTestHelper helper) {
        floor(helper, 1, 4);
        WirePart hand = mount(helper, PartType.SWITCH, 1, 2, 6);
        WirePart gate = mount(helper, PartType.GATE_NOT, 3, 4, 6);
        if (hand == null || gate == null) {
            return;
        }
        close(helper, hand);

        WirePixel pad = padPixel(gate, 0, false);
        // Two pixels below the pad: as close as a careless run gets, and still not connected.
        WirePixel missed = new WirePixel(pad.x(), pad.y(), pad.z(), pad.face(),
                pad.u(), Math.max(0, pad.v() - 2));
        if (!layTo(helper, padPixel(hand, 0, true), missed, DyeColor.WHITE)) {
            return;
        }

        if (!driving(helper, gate)) {
            helper.fail("a run that missed the pad still drove the gate - pads mean nothing");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I wired an AND and it did not work."</em>
     *
     * <p>The reported failure, turned into a gate. Neither, then one, then both — because an AND
     * that is secretly an OR passes the first check and fails the second, and an AND whose two
     * inputs are on one circuit passes all three while being entirely wrong.
     */
    private static void andGateNeedsBoth(GameTestHelper helper) {
        floor(helper, 1, 4);
        WirePart west = mount(helper, PartType.SWITCH, 1, 1, 1);
        WirePart east = mount(helper, PartType.SWITCH, 1, 1, 11);
        WirePart gate = mount(helper, PartType.GATE_AND, 3, 5, 5);
        if (west == null || east == null || gate == null) {
            return;
        }
        // Two colours on purpose. One line for both legs would be a single circuit feeding both
        // inputs — which is exactly how a broken AND looks like a working one.
        if (!wire(helper, west, 0, gate, 0, DyeColor.WHITE)
                || !wire(helper, east, 0, gate, 1, DyeColor.RED)) {
            return;
        }

        if (driving(helper, gate)) {
            helper.fail("an AND with both switches open was driving");
            return;
        }
        close(helper, west);
        if (driving(helper, gate)) {
            helper.fail("an AND drove on one input - it is behaving as an OR");
            return;
        }
        close(helper, east);
        if (!driving(helper, gate)) {
            helper.fail("an AND with both inputs live did not drive");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Two runs crossing one wall are two circuits."</em>
     *
     * <p>The rule the whole colour system rests on, and the one a player breaks first by wiring
     * everything white. If it stops holding, every circuit in a base silently becomes one and
     * nothing looks wrong.
     *
     * <p><strong>An earlier version of this test asserted something false</strong> and is worth
     * recording: it assumed a switch "drives white". It does not — a source drives whatever colour
     * is landed on its pad. What colour actually separates is <em>circuits</em>, so the honest
     * test is two runs of different colours that physically meet and must still not conduct.
     */
    private static void coloursAreSeparateCircuits(GameTestHelper helper) {
        floor(helper, 1, 4);
        WirePart hand = mount(helper, PartType.SWITCH, 1, 2, 6);
        WirePart gate = mount(helper, PartType.GATE_NOT, 3, 4, 6);
        if (hand == null || gate == null) {
            return;
        }
        close(helper, hand);

        // Both runs end on the same pixel of the cell between them, so they genuinely meet — and
        // being different colours, must still be two circuits.
        BlockPos middle = helper.absolutePos(new BlockPos(2, 2, 1));
        WirePixel meeting = new WirePixel(middle.getX(), middle.getY(), middle.getZ(),
                Faces.of(FACE), 8, 8);
        if (!layTo(helper, padPixel(hand, 0, true), meeting, DyeColor.WHITE)
                || !layTo(helper, padPixel(gate, 0, false), meeting, DyeColor.RED)) {
            return;
        }

        // The gate listens on red; the switch drives white. They touch and must not conduct, so
        // an inverter with nothing live on its input has to be driving.
        if (!driving(helper, gate)) {
            helper.fail("a white circuit drove a red one where they met - colours do not separate");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"когда много проводов начинает нереально сильно лагать"</em> — once there is a lot of
     * wire, it starts lagging unbelievably badly.
     *
     * <p>The renderer used to ask {@link WireChunk#allOn} for its own bundle once per trace while
     * walking every trace in the chunk — <em>T</em> scans of up to <em>T</em> traces each,
     * quadratic in the chunk's own trace count, paid every single frame. {@link
     * WireChunk#groupedBySurface} answers the identical question in one pass instead, and this
     * scenario is the guard that the faster path actually agrees with the one it replaced (rule
     * 46) — on real, Minecraft-backed chunk data, since this mod's plain unit test source set is
     * deliberately Minecraft-free and cannot construct a {@code WireChunk} at all.
     *
     * <p>Three different colours laid on one real surface — a bundle {@code allOn} has always had
     * to find correctly — then both methods are asked for that one surface's own bundle and must
     * agree exactly.
     */
    private static void groupedBySurfaceAgreesWithAllOn(GameTestHelper helper) {
        floor(helper, 1, 1);
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 1));
        var face = Faces.of(FACE);
        List<WirePixel> whitePixels = new java.util.ArrayList<>();
        List<WirePixel> redPixels = new java.util.ArrayList<>();
        List<WirePixel> bluePixels = new java.util.ArrayList<>();
        for (int u = 0; u < 4; u++) {
            whitePixels.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), face, u, 0));
            redPixels.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), face, u, 4));
            bluePixels.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), face, u, 8));
        }
        var gauge = play.xponer.astronima.sim.circuit.WireGauge.DEFAULT;
        Wires.placeAll(helper.getLevel(), whitePixels, DyeColor.WHITE, ConductorMaterial.IRON, gauge);
        Wires.placeAll(helper.getLevel(), redPixels, DyeColor.RED, ConductorMaterial.IRON, gauge);
        Wires.placeAll(helper.getLevel(), bluePixels, DyeColor.BLUE, ConductorMaterial.IRON, gauge);

        // allOn/WirePower.Face key on the real Minecraft Direction (FACE), not the mod's own Face
        // type (Faces.of(FACE)) WirePixel itself takes above - the two are different types for a
        // real reason (Faces.of picks the six-face frame the pixel grid is built in), and this is
        // exactly the kind of mismatch worth a real compiler catching rather than a guess.
        WireChunk wires = helper.getLevel().getChunk(cell).getData(ModAttachments.WIRES.get());
        List<play.xponer.astronima.wire.WireTrace> expected = wires.allOn(cell, FACE);
        if (expected.size() != 3) {
            helper.fail("test setup itself is wrong: expected 3 traces on the surface, allOn found "
                    + expected.size());
            return;
        }
        List<play.xponer.astronima.wire.WireTrace> actual =
                wires.groupedBySurface().get(new play.xponer.astronima.wire.WirePower.Face(cell, FACE));
        if (actual == null || !java.util.Set.copyOf(expected).equals(java.util.Set.copyOf(actual))) {
            helper.fail("groupedBySurface disagreed with allOn: allOn found " + expected
                    + " but groupedBySurface found " + actual);
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Two gates, one block."</em>
     *
     * <p><strong>The thing the block version could not do at all</strong>, and the reason this
     * tier exists. As blocks, a switch and a gate set side by side had their facing terminals in
     * each other's cells and could not be wired together; this scenario puts both on <em>one face
     * of one block</em>, three pixels apart, with a run between them.
     */
    private static void twoPartsShareOneFace(GameTestHelper helper) {
        floor(helper, 1, 1);
        WirePart hand = mount(helper, PartType.SWITCH, 1, 0, 0);
        WirePart gate = mount(helper, PartType.GATE_NOT, 1, 5, 0);
        if (hand == null || gate == null) {
            return;
        }
        // And you cannot route a wire *under* one. A housing pixel is occupied; its pad is not.
        WirePixel housing = new WirePixel(gate.cell().getX(), gate.cell().getY(),
                gate.cell().getZ(), Faces.of(FACE), gate.u() + 2, gate.v() + 2);
        if (Wires.canPlace(helper.getLevel(), housing)) {
            helper.fail("a trace can be routed straight through a gate's housing");
            return;
        }
        if (!Wires.canPlace(helper.getLevel(), padPixel(gate, 0, false))) {
            helper.fail("a pad refuses wire, so nothing can ever be connected to it");
            return;
        }
        if (!wire(helper, hand, 0, gate, 0, DyeColor.WHITE)) {
            helper.fail("two parts on one face could not be wired to each other");
            return;
        }
        if (!driving(helper, gate)) {
            helper.fail("a NOT gate with nothing live on its input should be driving");
            return;
        }
        close(helper, hand);
        if (driving(helper, gate)) {
            helper.fail("the switch drove the gate on the same face and the gate did not notice");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I plastered over the whole circuit and it kept working."</em>
     *
     * <p>The flagship promise of the wire layer, now that there is something in it other than
     * wire. A part is not fastened to the cell it lies in — it hangs on the surface behind it — so
     * filling that cell with a wall encloses it and changes nothing.
     */
    private static void logicBuriedInAWallStillWorks(GameTestHelper helper) {
        floor(helper, 1, 4);
        WirePart hand = mount(helper, PartType.SWITCH, 1, 2, 6);
        WirePart gate = mount(helper, PartType.GATE_NOT, 3, 4, 6);
        if (hand == null || gate == null || !wire(helper, hand, 0, gate, 0, DyeColor.WHITE)) {
            return;
        }
        close(helper, hand);
        if (driving(helper, gate)) {
            helper.fail("the circuit was not working before it was buried");
            return;
        }
        // Now wall the lot in. Every cell the parts and the run lie in becomes solid.
        for (int x = 1; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.STONE);
        }
        if (driving(helper, gate)) {
            helper.fail("burying the circuit in a wall broke it - the tier's whole promise");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I broke the wall and got my gate back."</em>
     *
     * <p>The other half of the burial rule, and the one that decides whether a mistake is
     * expensive. A part hangs on the surface behind it: break <em>that</em>, and it comes off —
     * as an item, because a hand-authored plate lost to a misplaced pickaxe would be punishment
     * nobody designs on purpose.
     *
     * <p><strong>The block is removed the way most blocks really go</strong> — not through a
     * player's break event. That is deliberate and it is what this scenario found: hooking the
     * break event alone would have left a gate hanging in the air after an explosion, a piston or
     * a command, working and undrawable and impossible to take down. The support check lives in
     * {@code WireTicker} instead, which is why this waits a refresh before looking.
     */
    private static void breakingTheSupportDropsThePart(GameTestHelper helper) {
        floor(helper, 1, 1);
        WirePart gate = mount(helper, PartType.GATE_AND, 1, 4, 4);
        if (gate == null) {
            return;
        }
        helper.destroyBlock(new BlockPos(1, 1, 1));
        // Two refresh periods: the ticker runs every five ticks, and asserting on the first one
        // would be a race the test would lose about half the time.
        helper.runAfterDelay(2L * play.xponer.astronima.wire.WireTicker.REFRESH_TICKS, () -> {
            if (Wires.partAt(helper.getLevel(), gate.cell(), gate.face(), gate.u(), gate.v())
                    .isPresent()) {
                helper.fail("the gate is still hanging on a block that no longer exists");
                return;
            }
            helper.assertItemEntityCountIs(ModItems.part(PartType.GATE_AND),
                    new BlockPos(1, 2, 1), 3.0, 1);
            helper.succeed();
        });
    }

    /**
     * <em>"The plate does what the four gates did."</em>
     *
     * <p>The claim a plate makes, asserted through real wire on real pads rather than by calling
     * the netlist. {@code CircuitTest} already walks the truth tables; what this adds is that a
     * plate on a wall reads its inputs and drives its outputs at all, which is the half no unit
     * test can see (rule 13).
     */
    private static void aPlateComputesWhatItWasGiven(GameTestHelper helper) {
        floor(helper, 1, 4);
        Circuit inverter = Circuit.empty()
                .added(play.xponer.astronima.sim.logic.Gate.NOT, 0, 0)
                .linked(0, 0, Circuit.Source.fromInput(0))
                .withOutput(0, Circuit.Source.fromNode(0));

        // The board layout has to survive being written down, and that cannot be checked in a
        // unit test: the MC-free test source set has no codec library on it, deliberately (rule
        // 1). So the assertion moves here, where it can run (rule 14) — a plate that came back
        // from a save with its gates re-laid would have lost the only documentation it carries.
        Circuit reloaded = play.xponer.astronima.item.CircuitPlate.CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, inverter)
                .flatMap(tag -> play.xponer.astronima.item.CircuitPlate.CODEC
                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, tag))
                .result().orElse(null);
        if (reloaded == null || !reloaded.equals(inverter)) {
            helper.fail("a plate's circuit or its board layout did not survive being saved");
            return;
        }

        WirePart hand = mount(helper, PartType.SWITCH, 1, 2, 12);
        WirePart plate = mountWith(helper, PartType.PLATE, 3, 4, 4, inverter);
        if (hand == null || plate == null || !wire(helper, hand, 0, plate, 0, DyeColor.WHITE)) {
            return;
        }
        if (!driving(helper, plate)) {
            helper.fail("a plate holding an inverter, with a dead input, should be driving W");
            return;
        }
        close(helper, hand);
        if (driving(helper, plate)) {
            helper.fail("the plate's input went live and its inverted output did not go dead");
            return;
        }
        // And the answer has to reach the record the *client* reads, or the part is invisible
        // exactly when it is working. The client has no ServerLevel and cannot ask a plate what
        // it is doing; every signal source in this mod has had to publish its own answer since
        // the charge animation shipped, and this is the parts' version of that obligation.
        helper.runAfterDelay(2L * play.xponer.astronima.wire.WireTicker.REFRESH_TICKS, () -> {
            WirePart stored = Wires.partAt(helper.getLevel(), plate.cell(), plate.face(),
                    plate.u(), plate.v()).orElse(null);
            if (stored == null || stored.drivingAnything()) {
                helper.fail("the plate's stored output still says it is driving, so a client"
                        + " would draw a lit pad on a dead one");
                return;
            }
            open(helper, hand);
            helper.runAfterDelay(2L * play.xponer.astronima.wire.WireTicker.REFRESH_TICKS, () -> {
                WirePart now = Wires.partAt(helper.getLevel(), plate.cell(), plate.face(),
                        plate.u(), plate.v()).orElse(null);
                if (now == null || !now.drivingAnything()) {
                    helper.fail("the plate went back to driving and never wrote it down - a"
                            + " client cannot draw what the server did not publish");
                    return;
                }
                helper.succeed();
            });
        });
    }

    /**
     * <em>"Nested plates were dead code — armed, never wired to a screen, and would have crashed
     * on save."</em>
     *
     * <p>A node built from a subcircuit has {@code gate() == null}; before this the hand-written
     * {@code STREAM_CODEC} wrote {@code node.gate().ordinal()} unconditionally and
     * {@code NODE_CODEC} required a non-null gate — either path was a guaranteed NPE or a refused
     * save the moment a real nested plate existed. Proven here the same way the plain-gate case
     * already is (rule 14, `aPlateComputesWhatItWasGiven`'s own reasoning): the MC-free test source
     * set has no codec library, so the round trip has to run where a codec actually exists.
     */
    private static void aNestedSubcircuitSurvivesBeingSaved(GameTestHelper helper) {
        floor(helper, 1, 4);
        Circuit inner = Circuit.empty()
                .added(play.xponer.astronima.sim.logic.Gate.NOT, 0, 0)
                .linked(0, 0, Circuit.Source.fromInput(0))
                .withOutput(0, Circuit.Source.fromNode(0));
        Circuit outer = Circuit.empty()
                .addedSubcircuit(inner, 1, 1, 4)
                .linked(0, 0, Circuit.Source.fromInput(0))
                .withOutput(0, Circuit.Source.fromNode(0));

        Circuit reloadedViaNbt = play.xponer.astronima.item.CircuitPlate.CODEC
                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, outer)
                .flatMap(tag -> play.xponer.astronima.item.CircuitPlate.CODEC
                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, tag))
                .result().orElse(null);
        if (reloadedViaNbt == null || !reloadedViaNbt.equals(outer)) {
            helper.fail("a nested plate did not survive an NBT save - the item stack round trip");
            return;
        }

        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            play.xponer.astronima.item.CircuitPlate.STREAM_CODEC.encode(buffer, outer);
            Circuit reloadedViaNetwork = play.xponer.astronima.item.CircuitPlate.STREAM_CODEC
                    .decode(buffer);
            if (!reloadedViaNetwork.equals(outer)) {
                helper.fail("a nested plate did not survive the network round trip");
                return;
            }
        } finally {
            buffer.release();
        }

        // And the nested gate's own answer has to reach the outer plate's evaluation, not just
        // survive being stored - flattened() is what a mounted plate actually calls.
        boolean[] withDeadInput = outer.flattened().evaluate(new boolean[] {false});
        if (!withDeadInput[0]) {
            helper.fail("a nested NOT gate with a dead input should drive the outer plate's W");
            return;
        }
        boolean[] withLiveInput = outer.flattened().evaluate(new boolean[] {true});
        if (withLiveInput[0]) {
            helper.fail("a nested NOT gate with a live input should not drive the outer plate's W");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Платы которые я вставляю в продвинутую плату имеют всего два соединения на вход и один
     * на выход хоть по факту 4 и там и там."</em>
     *
     * <p>{@code CircuitTest} already proves the model handles a five-pin nested plate correctly in
     * isolation; this is the same claim {@code aPlateComputesWhatItWasGiven} makes for a plain
     * plate, made here for a nested one — real switches on a real {@code MACRO_PLATE}'s real pads,
     * not a call to the model directly (rule 13). The nested chip's third input (fed from the
     * plate's own pin A), fifth input (pin B) and fourth input (pin C) all have to reach it, and
     * both of its outputs have to reach two different pads on the outer plate — the exact four
     * pins this bug left unreachable.
     */
    private static void aFivePinNestedPlateWiresThroughARealMacroPlate(GameTestHelper helper) {
        floor(helper, 1, 6);
        // The nested chip: output 0 = AND(its own pin C, its own pin E); output 1 = its own pin D,
        // passed straight through — deliberately touching every one of the three spare inputs and
        // both outputs a two-pin node could never have reached.
        Circuit inner = Circuit.empty()
                .added(play.xponer.astronima.sim.logic.Gate.AND, 0, 0)
                .linked(0, 0, Circuit.Source.fromInput(2))
                .linked(0, 1, Circuit.Source.fromInput(4))
                .withOutput(0, Circuit.Source.fromNode(0))
                .withOutput(1, Circuit.Source.fromInput(3));
        Circuit outer = Circuit.empty()
                .addedSubcircuit(inner, 1, 1, 5)
                .linked(0, 2, Circuit.Source.fromInput(0))   // outer pin A -> nested pin C
                .linked(0, 4, Circuit.Source.fromInput(1))   // outer pin B -> nested pin E
                .linked(0, 3, Circuit.Source.fromInput(2))   // outer pin C -> nested pin D
                .withOutput(0, Circuit.Source.fromNodeOutput(0, 0))
                .withOutput(1, Circuit.Source.fromNodeOutput(0, 1));

        WirePart switchA = mount(helper, PartType.SWITCH, 1, 2, 12);
        WirePart switchB = mount(helper, PartType.SWITCH, 2, 2, 12);
        WirePart switchC = mount(helper, PartType.SWITCH, 3, 2, 12);
        WirePart plate = mountWith(helper, PartType.MACRO_PLATE, 5, 5, 5, outer);
        if (switchA == null || switchB == null || switchC == null || plate == null) {
            return;
        }
        if (!wire(helper, switchA, 0, plate, 0, DyeColor.WHITE)
                || !wire(helper, switchB, 0, plate, 1, DyeColor.RED)
                || !wire(helper, switchC, 0, plate, 2, DyeColor.LIME)) {
            return;
        }

        if (bit(helper, plate, 0) || bit(helper, plate, 1)) {
            helper.fail("a blank-switch macro plate should not be driving either output yet");
            return;
        }

        close(helper, switchA);
        if (bit(helper, plate, 0)) {
            helper.fail("the nested AND fired with only one of its two real inputs live");
            return;
        }
        close(helper, switchB);
        if (!bit(helper, plate, 0)) {
            helper.fail("the outer plate's pins A and B never reached the nested chip's third"
                    + " and fifth inputs - it is still driving as if they do not exist");
            return;
        }
        if (bit(helper, plate, 1)) {
            helper.fail("the nested chip's second output is reading pin C before it was ever wired");
            return;
        }

        close(helper, switchC);
        if (!bit(helper, plate, 1)) {
            helper.fail("the outer plate's pin C never reached the nested chip's fourth input, or"
                    + " the nested chip's second output never reached the outer plate at all");
            return;
        }
        if (!bit(helper, plate, 0)) {
            helper.fail("wiring the fourth input silently disturbed the third and fifth");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"опять тоже самое собрал CPU_Counter4Bit постоянно все выходы... горят"</em> — reported
     * a second time, after switching to an edge-triggered {@code Register4Bit_Edge}. {@code
     * CircuitTest#anEdgeTriggeredCounterCountsWhereALevelTriggeredOneChases} already proves the
     * <em>model</em> counts correctly through this exact topology; what it cannot prove is the real
     * pipeline a mounted plate actually runs through — a live {@code BUTTON} part, its own
     * auto-release, {@code WireTicker}'s five-tick refresh, and {@code gate_state} persisted on the
     * real {@link WirePart} across ticks rather than threaded through one Java variable by hand.
     * Built with a real {@code MACRO_PLATE}, a real button and a real switch, pressed the way a
     * player's finger would, and read off the part's own stored {@link WirePart#outputs()} — the
     * exact value {@code WireTicker.refresh}'s stateful branch writes, never
     * {@link PartLogic#evaluate}, which resets a looped circuit's memory to all-false on every call
     * and would report a stateful counter's own progress wrong (see {@code bit}/{@code driving}'s
     * own note - fine for a stateless gate, wrong for this).
     */
    private static void aRealEdgeTriggeredCounterActuallyCounts(GameTestHelper helper) {
        floor(helper, 1, 6);
        Circuit counter = buildEdgeTriggeredCounter();

        WirePart button = mount(helper, PartType.BUTTON, 1, 2, 12);
        WirePart plusOne = mount(helper, PartType.SWITCH, 2, 2, 12);
        WirePart plate = mountWith(helper, PartType.MACRO_PLATE, 4, 5, 5, counter);
        if (button == null || plusOne == null || plate == null) {
            return;
        }
        if (!wire(helper, button, 0, plate, 0, DyeColor.WHITE)      // clock -> register's E
                || !wire(helper, plusOne, 0, plate, 1, DyeColor.RED)) { // +1 enable -> adder's E
            return;
        }
        close(helper, plusOne);

        pressAndCheckCount(helper, button, plate, 1, -1);
    }

    /**
     * The same edge-triggered counter build as {@link #aRealEdgeTriggeredCounterActuallyCounts},
     * pulled out so {@link #aClockedCounterCountsWithoutAnyPlayerAtAll} can wire the identical
     * circuit to a real {@code CLOCK} part instead of a button — the two tests share the topology
     * on purpose, since the claim being tested is <em>only</em> "what drives the clock pin changed",
     * not "the counter is a different circuit".
     */
    private static Circuit buildEdgeTriggeredCounter() {
        List<Circuit.Source> dlOuts = new java.util.ArrayList<>(Circuit.empty().outputs());
        dlOuts.set(0, Circuit.Source.fromNode(3));
        Circuit dLatch = new Circuit(
                List.of(
                        new Circuit.Node(play.xponer.astronima.sim.logic.Gate.NOT,
                                Circuit.Source.fromInput(0), Circuit.Source.fromInput(0), 0, 0),
                        new Circuit.Node(play.xponer.astronima.sim.logic.Gate.NAND,
                                Circuit.Source.fromInput(0), Circuit.Source.fromInput(1), 1, 0),
                        new Circuit.Node(play.xponer.astronima.sim.logic.Gate.NAND,
                                Circuit.Source.fromNode(0), Circuit.Source.fromInput(1), 2, 0),
                        new Circuit.Node(play.xponer.astronima.sim.logic.Gate.NAND,
                                Circuit.Source.fromNode(1), Circuit.Source.fromNode(4), 3, 0),
                        new Circuit.Node(play.xponer.astronima.sim.logic.Gate.NAND,
                                Circuit.Source.fromNode(2), Circuit.Source.fromNode(3), 4, 0)),
                List.copyOf(dlOuts));

        Circuit dff = Circuit.empty()
                .added(play.xponer.astronima.sim.logic.Gate.NOT, 0, 0)
                .addedSubcircuit(dLatch, 1, 0, 5)
                .addedSubcircuit(dLatch, 2, 0, 5)
                .linked(0, 0, Circuit.Source.fromInput(1))
                .linked(1, 0, Circuit.Source.fromInput(0))
                .linked(1, 1, Circuit.Source.fromNode(0))
                .linked(2, 0, Circuit.Source.fromNodeOutput(1, 0))
                .linked(2, 1, Circuit.Source.fromInput(1))
                .withOutput(0, Circuit.Source.fromNodeOutput(2, 0));

        List<Circuit.Source> faOuts = new java.util.ArrayList<>(Circuit.empty().outputs());
        faOuts.set(0, Circuit.Source.fromNode(1));
        faOuts.set(1, Circuit.Source.fromNode(4));
        Circuit fullAdder = new Circuit(
                List.of(
                        new Circuit.Node(play.xponer.astronima.sim.logic.Gate.XOR,
                                Circuit.Source.fromInput(0), Circuit.Source.fromInput(1), 0, 0),
                        new Circuit.Node(play.xponer.astronima.sim.logic.Gate.XOR,
                                Circuit.Source.fromNode(0), Circuit.Source.fromInput(2), 1, 0),
                        new Circuit.Node(play.xponer.astronima.sim.logic.Gate.AND,
                                Circuit.Source.fromInput(0), Circuit.Source.fromInput(1), 2, 0),
                        new Circuit.Node(play.xponer.astronima.sim.logic.Gate.AND,
                                Circuit.Source.fromNode(0), Circuit.Source.fromInput(2), 3, 0),
                        new Circuit.Node(play.xponer.astronima.sim.logic.Gate.OR,
                                Circuit.Source.fromNode(2), Circuit.Source.fromNode(3), 4, 0)),
                List.copyOf(faOuts));
        Circuit adder4Bit = Circuit.empty()
                .addedSubcircuit(fullAdder, 0, 0, 5)
                .addedSubcircuit(fullAdder, 1, 0, 5)
                .addedSubcircuit(fullAdder, 2, 0, 5)
                .addedSubcircuit(fullAdder, 3, 0, 5)
                .linked(0, 0, Circuit.Source.fromInput(0)).linked(0, 2, Circuit.Source.fromInput(4))
                .linked(1, 0, Circuit.Source.fromInput(1)).linked(1, 2, Circuit.Source.fromNodeOutput(0, 1))
                .linked(2, 0, Circuit.Source.fromInput(2)).linked(2, 2, Circuit.Source.fromNodeOutput(1, 1))
                .linked(3, 0, Circuit.Source.fromInput(3)).linked(3, 2, Circuit.Source.fromNodeOutput(2, 1))
                .withOutput(0, Circuit.Source.fromNodeOutput(0, 0))
                .withOutput(1, Circuit.Source.fromNodeOutput(1, 0))
                .withOutput(2, Circuit.Source.fromNodeOutput(2, 0))
                .withOutput(3, Circuit.Source.fromNodeOutput(3, 0))
                .withOutput(4, Circuit.Source.fromNodeOutput(3, 1));

        Circuit register4BitEdge = Circuit.empty()
                .addedSubcircuit(dff, 0, 0, 5)
                .addedSubcircuit(dff, 1, 0, 5)
                .addedSubcircuit(dff, 2, 0, 5)
                .addedSubcircuit(dff, 3, 0, 5)
                .linked(0, 0, Circuit.Source.fromInput(0)).linked(0, 1, Circuit.Source.fromInput(4))
                .linked(1, 0, Circuit.Source.fromInput(1)).linked(1, 1, Circuit.Source.fromInput(4))
                .linked(2, 0, Circuit.Source.fromInput(2)).linked(2, 1, Circuit.Source.fromInput(4))
                .linked(3, 0, Circuit.Source.fromInput(3)).linked(3, 1, Circuit.Source.fromInput(4))
                .withOutput(0, Circuit.Source.fromNodeOutput(0, 0))
                .withOutput(1, Circuit.Source.fromNodeOutput(1, 0))
                .withOutput(2, Circuit.Source.fromNodeOutput(2, 0))
                .withOutput(3, Circuit.Source.fromNodeOutput(3, 0));

        // Same raw-constructor shape CircuitTest uses for the outer cycle - a chain of .linked()
        // calls between two nodes that read each other is not yet a cycle at the moment of the
        // first call, and normalised() can legally swap which index names which node.
        return new Circuit(
                List.of(
                        new Circuit.Node(adder4Bit, List.of(
                                Circuit.Source.fromNodeOutput(1, 0),
                                Circuit.Source.fromNodeOutput(1, 1),
                                Circuit.Source.fromNodeOutput(1, 2),
                                Circuit.Source.fromNodeOutput(1, 3),
                                Circuit.Source.fromInput(1)), 0, 0, 5),
                        new Circuit.Node(register4BitEdge, List.of(
                                Circuit.Source.fromNodeOutput(0, 0),
                                Circuit.Source.fromNodeOutput(0, 1),
                                Circuit.Source.fromNodeOutput(0, 2),
                                Circuit.Source.fromNodeOutput(0, 3),
                                Circuit.Source.fromInput(0)), 1, 0, 5)),
                List.of(
                        Circuit.Source.fromNodeOutput(1, 0),
                        Circuit.Source.fromNodeOutput(1, 1),
                        Circuit.Source.fromNodeOutput(1, 2),
                        Circuit.Source.fromNodeOutput(1, 3),
                        Circuit.Source.fromNodeOutput(0, 4)));
    }

    /**
     * One press-release-settle cycle of {@link #aRealEdgeTriggeredCounterActuallyCounts}, then the
     * next one — real ticks between each step, the same margin
     * {@code aButtonDrivesAndSpringsBack} already trusts to cover both the button's own release and
     * at least one {@code WireTicker} refresh afterwards.
     *
     * <p>The first press is not checked against a fixed value: a NAND-latch settling from blank
     * {@code gate_state} at mount time — before any real edge has ever been seen — lands on a real,
     * defined but arbitrary power-on value (proven here to always be fifteen, the same "both
     * outputs forced high" state a real cross-coupled NAND latch settles to from a blank start),
     * exactly the way a real 74-series flip-flop's {@code Q} is undefined until the first clock
     * edge. What actually matters, and what the report was about, is whether every press after that
     * moves the count by exactly one - so {@code previous} carries the last reading forward and
     * every click from the second on is checked against it, rather than against a value this test
     * would otherwise have to assume.
     */
    private static void pressAndCheckCount(GameTestHelper helper, WirePart button, WirePart plate,
                                           int click, int previous) {
        WirePart freshButton = Wires.partAt(helper.getLevel(), button.cell(), button.face(),
                button.u(), button.v()).orElse(button);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        play.xponer.astronima.wire.PartInteraction.operate(helper.getLevel(), player, freshButton);

        helper.runAfterDelay(play.xponer.astronima.wire.PartInteraction.HELD_TICKS
                + 3L * play.xponer.astronima.wire.WireTicker.REFRESH_TICKS, () -> {
                    WirePart current = Wires.partAt(helper.getLevel(), plate.cell(), plate.face(),
                            plate.u(), plate.v()).orElse(null);
                    if (current == null) {
                        helper.fail("the counter plate came off the wall mid-test");
                        return;
                    }
                    int actual = current.outputs() & 0b1111;
                    if (previous >= 0 && actual != (previous + 1) % 16) {
                        helper.fail("press " + click + " should have moved the count from "
                                + previous + " to " + ((previous + 1) % 16) + ", but it reads "
                                + actual + " - a real button press did not advance the register");
                        return;
                    }
                    if (click >= 18) {
                        helper.succeed();
                        return;
                    }
                    pressAndCheckCount(helper, button, plate, click + 1, actual);
                });
    }

    /**
     * {@code design/computer.md} Phase 2's own claim, checked the way {@code
     * aRealEdgeTriggeredCounterActuallyCounts} checks its own: for real, mounted on a wall, ticked
     * by the real {@code WireTicker} — not called from a unit test. The one thing different here is
     * the point of the phase: <strong>nothing in this test ever touches the counter.</strong> No
     * button, no player, no {@code PartInteraction} call anywhere — a real {@code CLOCK} part wired
     * to the same edge-triggered counter {@link #buildEdgeTriggeredCounter} builds, left running,
     * counting entirely on its own. The first genuinely autonomous circuit in the mod.
     *
     * <p>The assertion is a delta, not an absolute value, for the same reason {@code
     * pressAndCheckCount}'s is: the counter's power-on reading is real but arbitrary (see that
     * method's own note). What is checked is that <em>one more refresh</em> — five real ticks,
     * with no player action inside them at all — advances the count by exactly
     * {@code WireTicker.INTERNAL_CYCLES_PER_REFRESH} modulo sixteen, which is the whole of what
     * "internal stepping" promises: many pulses settled in software per refresh, not one.
     */
    private static void aClockedCounterCountsWithoutAnyPlayerAtAll(GameTestHelper helper) {
        floor(helper, 1, 6);
        Circuit counter = buildEdgeTriggeredCounter();

        WirePart clock = mount(helper, PartType.CLOCK, 1, 2, 12);
        WirePart plusOne = mount(helper, PartType.SWITCH, 2, 2, 12);
        WirePart plate = mountWith(helper, PartType.MACRO_PLATE, 4, 5, 5, counter);
        if (clock == null || plusOne == null || plate == null) {
            return;
        }
        if (!wire(helper, clock, 0, plate, 0, DyeColor.WHITE)       // clock -> register's E
                || !wire(helper, plusOne, 0, plate, 1, DyeColor.RED)) { // +1 enable -> adder's E
            return;
        }
        close(helper, plusOne);

        // Generous head start: several refreshes' worth of settling, so the very first reading
        // taken is already well past whatever the power-on transient looked like.
        helper.runAfterDelay(3L * play.xponer.astronima.wire.WireTicker.REFRESH_TICKS, () -> {
            WirePart afterHeadStart = Wires.partAt(helper.getLevel(), plate.cell(), plate.face(),
                    plate.u(), plate.v()).orElse(null);
            if (afterHeadStart == null) {
                helper.fail("the counter plate came off the wall mid-test");
                return;
            }
            int before = afterHeadStart.outputs() & 0b1111;

            // Exactly one more refresh boundary is crossed by waiting exactly REFRESH_TICKS more,
            // regardless of what tick this happened to land on - the check WireTicker itself makes
            // is a plain modulus, so any REFRESH_TICKS-long window contains exactly one of them.
            helper.runAfterDelay(play.xponer.astronima.wire.WireTicker.REFRESH_TICKS, () -> {
                WirePart afterOneMoreRefresh = Wires.partAt(helper.getLevel(), plate.cell(),
                        plate.face(), plate.u(), plate.v()).orElse(null);
                if (afterOneMoreRefresh == null) {
                    helper.fail("the counter plate came off the wall mid-test");
                    return;
                }
                int after = afterOneMoreRefresh.outputs() & 0b1111;
                int expected = (before + play.xponer.astronima.wire.WireTicker.INTERNAL_CYCLES_PER_REFRESH) % 16;
                if (after != expected) {
                    helper.fail("one refresh with nobody touching anything should have moved the"
                            + " count from " + before + " to " + expected + " (a full "
                            + play.xponer.astronima.wire.WireTicker.INTERNAL_CYCLES_PER_REFRESH
                            + " internal pulses), but it reads " + after
                            + " - either the clock was not detected, or internal stepping did not run");
                    return;
                }
                helper.succeed();
            });
        });
    }

    /**
     * Rule 64's own guard, and the test the shipped bug needed all along: not "does one refresh
     * move the count by the expected step" (which {@link #aClockedCounterCountsWithoutAnyPlayerAtAll}
     * already checks, and which stayed green through the bug because it verified the step against
     * itself) but the property a player actually watches for — a counter driven by a real
     * {@link PartType#CLOCK} must eventually show every one of its sixteen values, not cycle
     * through a subset of them forever. {@code gcd(WireTicker.INTERNAL_CYCLES_PER_REFRESH, 16)}
     * must be 1 for this to be possible at all; this is what actually proves it is, on the real
     * wired circuit, rather than trusted from the constant's own value.
     */
    private static void aClockDrivenCounterEventuallyVisitsEveryValue(GameTestHelper helper) {
        floor(helper, 1, 6);
        Circuit counter = buildEdgeTriggeredCounter();

        WirePart clock = mount(helper, PartType.CLOCK, 1, 2, 12);
        WirePart plusOne = mount(helper, PartType.SWITCH, 2, 2, 12);
        WirePart plate = mountWith(helper, PartType.MACRO_PLATE, 4, 5, 5, counter);
        if (clock == null || plusOne == null || plate == null) {
            return;
        }
        if (!wire(helper, clock, 0, plate, 0, DyeColor.WHITE)
                || !wire(helper, plusOne, 0, plate, 1, DyeColor.RED)) {
            return;
        }
        close(helper, plusOne);

        visitCounterValues(helper, plate, new HashSet<>(), 20);
    }

    /**
     * One refresh at a time, records what the counter currently reads and asks for one more —
     * until either all sixteen values have been seen (success) or the budget runs out (failure,
     * naming exactly how many distinct values turned up, which is what makes rule 64's own
     * regression readable from the failure message alone rather than requiring a debugger).
     */
    private static void visitCounterValues(GameTestHelper helper, WirePart plate, Set<Integer> seen,
                                           int refreshesLeft) {
        WirePart current = Wires.partAt(helper.getLevel(), plate.cell(), plate.face(),
                plate.u(), plate.v()).orElse(null);
        if (current == null) {
            helper.fail("the counter plate came off the wall mid-test");
            return;
        }
        seen.add(current.outputs() & 0b1111);
        if (seen.size() == 16) {
            helper.succeed();
            return;
        }
        if (refreshesLeft <= 0) {
            helper.fail("after generous real time, the counter only ever showed " + seen.size()
                    + " of its sixteen values (" + seen + ") - it is cycling through a subset"
                    + " instead of counting through all of them (rule 64: the internal step and the"
                    + " counter width shared a common factor)");
            return;
        }
        helper.runAfterDelay(play.xponer.astronima.wire.WireTicker.REFRESH_TICKS,
                () -> visitCounterValues(helper, plate, seen, refreshesLeft - 1));
    }

    /**
     * <em>"no such node: 1" took a real world's player-load down with it.</em> A plate saved before
     * a node could carry more than two inputs has {@code a}/{@code b} fields and no {@code inputs}
     * list at all — the pin-count fix's own codec initially only read {@code inputs}, so a plate
     * already on disk from a previous session could not be read back, and {@code Circuit}'s strict
     * validation then threw all the way up through NBT decoding into entity load, refusing the
     * player's whole save. Built by hand rather than round-tripped, because nothing left in this
     * codebase can produce the old shape any more — only a save already on disk can.
     */
    private static void aPrePinCountSavedPlateStillLoads(GameTestHelper helper) {
        net.minecraft.nbt.CompoundTag gate = new net.minecraft.nbt.CompoundTag();
        gate.putString("gate", "and");
        net.minecraft.nbt.CompoundTag a = new net.minecraft.nbt.CompoundTag();
        a.putInt("node", -1);
        a.putInt("pin", 0);
        net.minecraft.nbt.CompoundTag b = new net.minecraft.nbt.CompoundTag();
        b.putInt("node", -1);
        b.putInt("pin", 1);
        gate.put("a", a);
        gate.put("b", b);
        gate.putInt("x", 0);
        gate.putInt("y", 0);

        net.minecraft.nbt.ListTag gates = new net.minecraft.nbt.ListTag();
        gates.add(gate);

        net.minecraft.nbt.ListTag outputs = new net.minecraft.nbt.ListTag();
        net.minecraft.nbt.CompoundTag outW = new net.minecraft.nbt.CompoundTag();
        outW.putInt("node", 0);
        outW.putInt("pin", -1);
        outputs.add(outW);
        for (int i = 1; i < Circuit.OUTPUTS; i++) {
            net.minecraft.nbt.CompoundTag off = new net.minecraft.nbt.CompoundTag();
            off.putInt("node", -1);
            off.putInt("pin", -1);
            outputs.add(off);
        }

        net.minecraft.nbt.CompoundTag plate = new net.minecraft.nbt.CompoundTag();
        plate.put("gates", gates);
        plate.put("outputs", outputs);

        Circuit loaded = play.xponer.astronima.item.CircuitPlate.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, plate)
                .result().orElse(null);
        if (loaded == null) {
            helper.fail("a plate saved before the pin-count fix failed to load at all");
            return;
        }
        if (loaded.nodes().size() != 1
                || loaded.nodes().get(0).gate() != play.xponer.astronima.sim.logic.Gate.AND) {
            helper.fail("the old node's gate did not survive loading");
            return;
        }
        if (!loaded.evaluate(new boolean[] {true, true})[0]) {
            helper.fail("the old node's two inputs did not survive loading");
            return;
        }
        helper.succeed();
    }

    /**
     * A nested plate saved before a node could carry its own name has {@code inputs}/{@code pins}/
     * {@code subcircuit} but no {@code name} key at all — the same shape rule 59's own save has,
     * one field further out. Must read as unnamed rather than refusing to load.
     */
    private static void aPreNameFieldSavedNestedPlateStillLoads(GameTestHelper helper) {
        net.minecraft.nbt.CompoundTag innerCircuit = new net.minecraft.nbt.CompoundTag();
        innerCircuit.put("gates", new net.minecraft.nbt.ListTag());
        net.minecraft.nbt.ListTag innerOutputs = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < Circuit.OUTPUTS; i++) {
            net.minecraft.nbt.CompoundTag off = new net.minecraft.nbt.CompoundTag();
            off.putInt("node", -1);
            off.putInt("pin", -1);
            innerOutputs.add(off);
        }
        innerCircuit.put("outputs", innerOutputs);

        net.minecraft.nbt.CompoundTag node = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag inputs = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 4; i++) {
            net.minecraft.nbt.CompoundTag off = new net.minecraft.nbt.CompoundTag();
            off.putInt("node", -1);
            off.putInt("pin", -1);
            inputs.add(off);
        }
        node.put("inputs", inputs);
        node.putInt("pins", 4);
        node.put("subcircuit", innerCircuit);
        node.putInt("x", 0);
        node.putInt("y", 0);
        // Deliberately no "name" key at all - the exact shape of a save from before this fix.

        net.minecraft.nbt.ListTag gates = new net.minecraft.nbt.ListTag();
        gates.add(node);

        net.minecraft.nbt.ListTag outputs = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < Circuit.OUTPUTS; i++) {
            net.minecraft.nbt.CompoundTag off = new net.minecraft.nbt.CompoundTag();
            off.putInt("node", -1);
            off.putInt("pin", -1);
            outputs.add(off);
        }

        net.minecraft.nbt.CompoundTag plate = new net.minecraft.nbt.CompoundTag();
        plate.put("gates", gates);
        plate.put("outputs", outputs);

        Circuit loaded = play.xponer.astronima.item.CircuitPlate.CODEC
                .parse(net.minecraft.nbt.NbtOps.INSTANCE, plate)
                .result().orElse(null);
        if (loaded == null) {
            helper.fail("a nested plate saved before names existed failed to load at all");
            return;
        }
        if (loaded.nodes().size() != 1 || !loaded.nodes().get(0).isSubcircuit()) {
            helper.fail("the old nested node did not survive loading as a subcircuit node");
            return;
        }
        if (!loaded.nodes().get(0).name().isEmpty()) {
            helper.fail("a nested node saved with no name field at all should read as unnamed, got '"
                    + loaded.nodes().get(0).name() + "'");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>Gate memory was one {@code int} before nesting could make a flattened circuit outgrow
     * thirty-two gates — every part saved before that fix has {@code gate_state} as a bare number,
     * not a list.</em> Encodes a real part, then overwrites just that one field with the old shape
     * before decoding, the same way {@code scenario_a_pre_pin_count_saved_plate_still_loads} proves
     * the sibling fix.
     */
    private static void aPreWideGateStateSavedPartStillLoads(GameTestHelper helper) {
        WirePart part = WirePart.placed(new BlockPos(1, 2, 3), Direction.NORTH, 1, 1, 0,
                PartType.GATE_AND, Circuit.empty(), "");
        var encoded = WirePart.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, part)
                .result().orElse(null);
        if (!(encoded instanceof net.minecraft.nbt.CompoundTag tag)) {
            helper.fail("could not encode a plain WirePart to NBT at all");
            return;
        }
        tag.putLong("gate_state", 5L);

        WirePart loaded = WirePart.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag)
                .result().orElse(null);
        if (loaded == null) {
            helper.fail("a part saved with a pre-list gate_state failed to load at all");
            return;
        }
        if (!loaded.gateState().equals(List.of(5L))) {
            helper.fail("a bare saved gate_state number did not become its list's one word - got "
                    + loaded.gateState());
            return;
        }
        helper.succeed();
    }

    /** Whether a part is driving one specific output pad, read live through {@link PartLogic}. */
    private static boolean bit(GameTestHelper helper, WirePart part, int index) {
        WirePart current = Wires.partAt(helper.getLevel(), part.cell(), part.face(),
                part.u(), part.v()).orElse(part);
        return (PartLogic.evaluate(helper.getLevel(), current) >> index & 1) != 0;
    }

    /**
     * <em>"Провода постоянно снапятся на ближайшие провода, а не к мелкому коннекту."</em>
     *
     * <p>Reported, and exactly right: {@code WireAim} knew about the studs of <strong>blocks</strong>
     * and nothing else, so aiming at a five-pixel part with a trace running past it snapped to the
     * trace. Which is the one failure that looks like success — the run lands a pixel or two off
     * the pad, draws as a continuous line, and does nothing.
     *
     * <p>A connection point beats loose conductor, and a part's pad is a connection point.
     */
    private static void aimingAtAPartSnapsToItsPad(GameTestHelper helper) {
        floor(helper, 1, 2);
        WirePart gate = mount(helper, PartType.GATE_AND, 1, 5, 5);
        if (gate == null) {
            return;
        }
        // A trace running flush past the gate's input edge — the distraction that used to win.
        WirePixel padPixel = padPixel(gate, 0, false);
        List<WirePixel> decoy = new java.util.ArrayList<>();
        for (int v = 0; v < 12; v++) {
            decoy.add(new WirePixel(padPixel.x(), padPixel.y(), padPixel.z(), padPixel.face(),
                    gate.u() - 1, v));
        }
        Wires.placeAll(helper.getLevel(), decoy, DyeColor.WHITE, ConductorMaterial.IRON, play.xponer.astronima.sim.circuit.WireGauge.DEFAULT);

        // Aim just inside the housing beside input A — which is where somebody wiring that pad
        // points, and where the decoy trace is one pixel away in the other direction.
        BlockPos support = gate.support();
        var aim = play.xponer.astronima.wire.WireAim.at(helper.getLevel(),
                hitOnTop(support, gate.u() + 1, gate.v()), false);

        if (!aim.onTerminal()) {
            helper.fail("aiming at a gate found no connection point at all");
            return;
        }
        if (aim.part() == null || !aim.pixel().equals(padPixel)) {
            helper.fail("aiming at a gate snapped to " + aim.pixel() + " instead of its pad at "
                    + padPixel + " - a run would land next to the part and do nothing");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Мелкий свич вообще походу не работает."</em>
     *
     * <p><strong>And every other scenario here would have kept passing.</strong> They close a
     * switch by writing the part back to the chunk — the effect of a click, never the click — so
     * the whole path a player actually uses was untested, which is rule 13's entire subject:
     * <em>enter through the same door the player does.</em>
     *
     * <p>What was wrong is aim. A switch is three pixels square and interaction asked for the
     * <em>exact</em> pixel under the crosshair, which is the demand {@code WireAim} exists to
     * refuse. So this clicks <strong>deliberately off</strong> the part — two pixels clear of its
     * corner, where a real player's crosshair lands — and requires that the switch still throws.
     */
    private static void aSwitchIsOperatedByClickingIt(GameTestHelper helper) {
        floor(helper, 1, 4);
        WirePart hand = mount(helper, PartType.SWITCH, 1, 4, 4);
        WirePart gate = mount(helper, PartType.GATE_NOT, 3, 4, 6);
        if (hand == null || gate == null || !wire(helper, hand, 0, gate, 0, DyeColor.WHITE)) {
            return;
        }
        if (!driving(helper, gate)) {
            helper.fail("a NOT gate with nothing live on its input should be driving");
            return;
        }

        // Aimed past the switch's top-left corner, not on it.
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        WirePart aimed = play.xponer.astronima.wire.PartInteraction.under(helper.getLevel(),
                hitOnTop(hand.support(), hand.u() - 2, hand.v() - 2));
        if (aimed == null) {
            helper.fail("clicking two pixels off a three-pixel switch found nothing at all -"
                    + " it is unhittable, which is exactly how it was reported");
            return;
        }
        play.xponer.astronima.wire.PartInteraction.operate(helper.getLevel(), player, aimed);

        // The same press arriving a second time in the same tick — exactly what the client does
        // when it walks from the main hand to the off hand, and it **re-resolves the part from
        // the chunk** the way the handler does rather than reusing the object above. That detail
        // is the whole bug: a stale snapshot toggles back to where it already is and looks
        // harmless, while a fresh one undoes the press. Reported as "нажимаю ПКМ по свичу,
        // постоянно пишет open".
        WirePart offHand = play.xponer.astronima.wire.PartInteraction.under(helper.getLevel(),
                hitOnTop(hand.support(), hand.u() - 2, hand.v() - 2));
        if (offHand != null) {
            play.xponer.astronima.wire.PartInteraction.operate(helper.getLevel(), player, offHand);
        }

        if (driving(helper, gate)) {
            helper.fail("the switch was clicked and closed nothing - either the click was lost,"
                    + " or one press toggled it twice and it came back open");
            return;
        }

        // A second *press*, a tick later, opens it again: a switch is a state you set, not a
        // pulse. Later rather than immediately, because that is the difference between a player
        // pressing twice and one press arriving twice.
        helper.runAfterDelay(2, () -> {
            WirePart again = play.xponer.astronima.wire.PartInteraction.under(helper.getLevel(),
                    hitOnTop(hand.support(), hand.u() + 1, hand.v() + 1));
            if (again == null) {
                helper.fail("the switch could not be found a second time");
                return;
            }
            play.xponer.astronima.wire.PartInteraction.operate(helper.getLevel(), player, again);
            if (!driving(helper, gate)) {
                helper.fail("a switch pressed twice stayed closed - it is behaving as a button");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * <em>"I blew the wall up and the wiring was still floating there."</em>
     *
     * <p>PLAN rule 24, and the half of it that was left unpaid when parts were fixed:
     * {@code BreakBlockEvent} fires for a player's pickaxe and for nothing else, so wire survived
     * an explosion, a piston and a {@code /setblock} by hanging in mid-air — still conducting, and
     * with nothing left to break to get rid of it.
     *
     * <p>The block here is removed the way <strong>most</strong> blocks really go, not the way a
     * player takes one: {@code destroyBlock} raises no interaction event at all, which is exactly
     * why this scenario can tell the two mechanisms apart.
     */
    private static void aWallRemovedAnyWayDropsItsWiring(GameTestHelper helper) {
        floor(helper, 1, 2);
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 1));
        WirePixel on = new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), 8, 8);
        Wires.place(helper.getLevel(), on, DyeColor.WHITE, ConductorMaterial.IRON, play.xponer.astronima.sim.circuit.WireGauge.DEFAULT);
        if (!Wires.has(helper.getLevel(), on, DyeColor.WHITE)) {
            helper.fail("the test could not lay its own wire");
            return;
        }

        helper.destroyBlock(new BlockPos(1, 1, 1));
        helper.runAfterDelay(2L * play.xponer.astronima.wire.WireTicker.REFRESH_TICKS, () -> {
            if (Wires.has(helper.getLevel(), on, DyeColor.WHITE)) {
                helper.fail("the wall is gone and its wire is still hanging in the air -"
                        + " conducting, and impossible to take down");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * <em>"I took a gate out of my inventory and put it on the wall."</em>
     *
     * <p><strong>The first thing anybody does with a part, and nothing tested it.</strong> Every
     * scenario here mounts parts by writing them into the chunk — the effect of placing one, never
     * the placing — which is precisely the hole that hid the switch being unclickable for a whole
     * release. Rule 13: enter through the same door the player does, and the door here is
     * {@code LogicPartItem.useOn}.
     *
     * <p>Three things are asserted because three separate things go wrong: the part appears, the
     * item is <em>spent</em>, and a second attempt in the same place is refused without quietly
     * eating another one.
     */
    private static void aPartIsPlacedByUsingTheItem(GameTestHelper helper) {
        floor(helper, 1, 1);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack held = new ItemStack(ModItems.part(PartType.GATE_AND), 4);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);

        BlockPos support = helper.absolutePos(new BlockPos(1, 1, 1));
        held.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(
                helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, held,
                hitOnTop(support, 8, 8)));

        BlockPos cell = support.above();
        List<WirePart> mounted = Wires.partsOn(helper.getLevel(), cell, FACE);
        if (mounted.size() != 1 || mounted.get(0).type() != PartType.GATE_AND) {
            helper.fail("using an AND gate on a floor put " + mounted.size()
                    + " parts on it - the item's own placement path does nothing");
            return;
        }
        if (held.getCount() != 3) {
            helper.fail("placing a part left " + held.getCount() + " in the stack of four -"
                    + " it should have cost exactly one");
            return;
        }

        // The same click again: the footprint is taken, so it must refuse and cost nothing.
        held.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(
                helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, held,
                hitOnTop(support, 8, 8)));
        if (Wires.partsOn(helper.getLevel(), cell, FACE).size() != 1) {
            helper.fail("a second gate went down on top of the first");
            return;
        }
        if (held.getCount() != 3) {
            helper.fail("a refused placement still charged the player an item");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Press it, watch the pip leave, and see it come back."</em>
     *
     * <p>The button exists to <strong>test a circuit</strong> — build the logic, prove it with a
     * press, and only then wire it to something that cycles an airlock while you are standing in
     * it. A button that stuck down would be a switch with extra steps, and one that never drove at
     * all would take the whole point of the part with it. Neither had any coverage.
     */
    private static void aButtonDrivesAndSpringsBack(GameTestHelper helper) {
        floor(helper, 1, 4);
        WirePart button = mount(helper, PartType.BUTTON, 1, 4, 4);
        WirePart gate = mount(helper, PartType.GATE_NOT, 3, 4, 6);
        if (button == null || gate == null || !wire(helper, button, 0, gate, 0, DyeColor.WHITE)) {
            return;
        }
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        play.xponer.astronima.wire.PartInteraction.operate(helper.getLevel(), player, button);

        if (driving(helper, gate)) {
            helper.fail("the button was pressed and the gate never saw the pulse");
            return;
        }
        // It has to let go by itself. Long enough after the hold that a slow refresh is not
        // mistaken for a stuck contact.
        helper.runAfterDelay(play.xponer.astronima.wire.PartInteraction.HELD_TICKS
                + 3L * play.xponer.astronima.wire.WireTicker.REFRESH_TICKS, () -> {
                    if (!driving(helper, gate)) {
                        helper.fail("the button never sprang back - it is behaving as a switch,"
                                + " and a circuit under test would stay latched on");
                        return;
                    }
                    helper.succeed();
                });
    }

    /**
     * {@code design/computer.md} Phase 2: {@code PartType#CLOCK} toggles on its own, over real
     * time, with nothing wired to it and nobody clicking it — the property {@code
     * scenario_a_clocked_counter_counts_without_any_player_at_all} builds on but never actually
     * checks directly, since that test only cares whether a clock is <em>present</em>, not what its
     * own real-time phase happens to be. This one checks the phase itself: read once after letting
     * a real refresh actually run {@code clocked()} at least once (a freshly mounted part's stored
     * {@code held} is whatever it was placed with, not yet the deterministic phase — reading before
     * the first refresh would be checking the placement default, not the clock), wait past a full
     * half-period more, read again, and the reading must have flipped.
     */
    private static void aClockTogglesOnItsOwnOverRealTime(GameTestHelper helper) {
        floor(helper, 1, 1);
        WirePart clock = mount(helper, PartType.CLOCK, 1, 4, 4);
        if (clock == null) {
            return;
        }
        helper.runAfterDelay(play.xponer.astronima.wire.WireTicker.REFRESH_TICKS + 1, () -> {
            boolean first = driving(helper, clock);
            helper.runAfterDelay(play.xponer.astronima.wire.WireTicker.CLOCK_HALF_PERIOD_TICKS, () -> {
                boolean second = driving(helper, clock);
                if (second == first) {
                    helper.fail("a clock left alone for more than half its own period should"
                            + " have toggled, and did not - it is behaving as a switch, not an"
                            + " oscillator");
                    return;
                }
                helper.succeed();
            });
        });
    }

    /**
     * {@code design/memory-chips.md} §5's own sharpest row: <em>"a write that shouldn't have
     * happened is worse than a write that didn't."</em> A real switch-bank build — ten switches for
     * address, data, write-enable and clock, wired straight to a real {@code RAM} chip's fourteen
     * real pads — proves four things in sequence: a rising edge with write-enable live actually
     * writes; holding the clock line high and changing the data underneath it does {@code
     * <strong>not</strong>} write a second time, which is the exact rule-63 hazard (a transparent
     * write reading its own downstream) checked directly rather than assumed from the design; every
     * cell is independently addressable, so reading address 0 does not disturb address 5's own
     * contents; and a clock edge arriving with write-enable open never writes at all.
     */
    private static void aRamChipWritesOnlyOnTheRisingEdge(GameTestHelper helper) {
        floor(helper, 1, 26);
        WirePart a0 = mount(helper, PartType.SWITCH, 1, 2, 2);
        WirePart a1 = mount(helper, PartType.SWITCH, 2, 2, 2);
        WirePart a2 = mount(helper, PartType.SWITCH, 3, 2, 2);
        WirePart a3 = mount(helper, PartType.SWITCH, 4, 2, 2);
        WirePart d0 = mount(helper, PartType.SWITCH, 5, 2, 2);
        WirePart d1 = mount(helper, PartType.SWITCH, 6, 2, 2);
        WirePart d2 = mount(helper, PartType.SWITCH, 7, 2, 2);
        WirePart d3 = mount(helper, PartType.SWITCH, 8, 2, 2);
        WirePart we = mount(helper, PartType.SWITCH, 9, 2, 2);
        WirePart clk = mount(helper, PartType.SWITCH, 10, 2, 2);
        WirePart ram = mountWith(helper, PartType.RAM, 13, 0, 0, Circuit.empty());
        if (a0 == null || a1 == null || a2 == null || a3 == null || d0 == null || d1 == null
                || d2 == null || d3 == null || we == null || clk == null || ram == null) {
            return;
        }
        // Ten independent lines packed onto one small chip's worth of pads: same-colour runs that
        // end up physically adjacent MERGE into one electrical network by this mod's own rule (any
        // driving switch on a network makes the whole network live) - a distinct colour per line
        // is what keeps ten separate signals from silently becoming fewer than ten.
        WirePart[] pins = {a0, a1, a2, a3, d0, d1, d2, d3, we, clk};
        DyeColor[] colours = {DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
                DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.CYAN, DyeColor.PURPLE,
                DyeColor.RED};
        for (int i = 0; i < pins.length; i++) {
            if (!wire(helper, pins[i], 0, ram, i, colours[i])) {
                return;
            }
        }

        long refresh = play.xponer.astronima.wire.WireTicker.REFRESH_TICKS;
        setNibble(helper, a0, a1, a2, a3, 5);   // address 5
        setNibble(helper, d0, d1, d2, d3, 9);   // data 9
        close(helper, we);
        close(helper, clk);                     // rising edge queued for the next refresh

        helper.runAfterDelay(refresh + 1, () -> assertRamCell(helper, ram, 9,
                "a real rising edge with write-enable live should have written 9 into cell 5", () -> {
                    // Still holding clk high: swap the data lines under it. A level-sensitive
                    // write would silently overwrite the cell a second time — rule 63's own
                    // hazard, here on a byte array instead of one bit.
                    setNibble(helper, d0, d1, d2, d3, 6);

                    helper.runAfterDelay(refresh, () -> assertRamCell(helper, ram, 9,
                            "clk stayed high and the data lines changed under it - a real chip must"
                                    + " not write a second time, so cell 5 should still read the"
                                    + " original 9 (a value that changed here is a level-sensitive"
                                    + " write, not an edge-triggered one)", () -> {
                                open(helper, clk);
                                open(helper, we);

                                helper.runAfterDelay(refresh, () -> {
                                    setNibble(helper, a0, a1, a2, a3, 0);
                                    helper.runAfterDelay(refresh, () -> assertRamCell(helper, ram, 0,
                                            "cell 0 was never written and should read 0 - either"
                                                    + " address decode is wrong or a write leaked"
                                                    + " across cells", () -> {
                                                setNibble(helper, a0, a1, a2, a3, 5);
                                                helper.runAfterDelay(refresh, () -> assertRamCell(
                                                        helper, ram, 9, "cell 5 should still read 9"
                                                                + " after reading a different address"
                                                                + " in between", () -> {
                                                            setNibble(helper, a0, a1, a2, a3, 10);
                                                            close(helper, clk); // we is open here
                                                            helper.runAfterDelay(refresh,
                                                                    () -> assertRamCell(helper, ram, 0,
                                                                            "a clock edge arrived with"
                                                                                    + " write-enable open"
                                                                                    + " (off) - cell 10"
                                                                                    + " should stay"
                                                                                    + " untouched, but a"
                                                                                    + " write happened"
                                                                                    + " anyway",
                                                                            helper::succeed));
                                                        }));
                                            }));
                                });
                            }));
                }));
    }

    @SuppressWarnings("unused")
    private static final Object A_FRAMEBUFFER_SHOWS_EXACTLY_WHAT_WAS_WRITTEN =
            PlayerScenarios.SCENARIOS.register(
                    "scenario_a_framebuffer_shows_exactly_what_was_written",
                    () -> WireScenarios::aFramebufferShowsExactlyWhatWasWritten);

    @SuppressWarnings("unused")
    private static final Object TWO_FRAMEBUFFERS_NEVER_SHARE_A_PIXEL =
            PlayerScenarios.SCENARIOS.register("scenario_two_framebuffers_never_share_a_pixel",
                    () -> WireScenarios::twoFramebuffersNeverShareAPixel);

    @SuppressWarnings("unused")
    private static final Object SNIPS_CUT_MID_RUN_SPLITS_THE_NETWORK =
            PlayerScenarios.SCENARIOS.register("scenario_snips_cut_mid_run_splits_the_network",
                    () -> WireScenarios::snipsCutMidRunSplitsTheNetwork);

    @SuppressWarnings("unused")
    private static final Object SNIPS_REFUSE_A_LOOP_THAT_WOULD_STILL_BE_JOINED =
            PlayerScenarios.SCENARIOS.register(
                    "scenario_snips_refuse_a_loop_that_would_still_be_joined",
                    () -> WireScenarios::snipsRefuseALoopThatWouldStillBeJoined);

    @SuppressWarnings("unused")
    private static final Object A_CLIPBOARD_PASTE_ROUND_TRIPS_A_REAL_CIRCUIT =
            PlayerScenarios.SCENARIOS.register(
                    "scenario_a_clipboard_paste_round_trips_a_real_circuit",
                    () -> WireScenarios::aClipboardPasteRoundTripsARealCircuit);

    @SuppressWarnings("unused")
    private static final Object A_CORRUPTED_CLIPBOARD_PASTE_IS_REFUSED =
            PlayerScenarios.SCENARIOS.register(
                    "scenario_a_corrupted_clipboard_paste_is_refused",
                    () -> WireScenarios::aCorruptedClipboardPasteIsRefused);

    /**
     * {@code design/display.md} §5's own "the row that matters most": what is on screen must be
     * what is in {@code memory}, at every one of the sixty-four positions — checked here against a
     * picture with a lit pixel in every quadrant, not a spot check that would pass on a transposed
     * layout or an off-by-one address decode just as easily as on a correct one. Folds in the
     * edge-triggered-write check {@code aRamChipWritesOnlyOnTheRisingEdge} already proved for
     * {@code RAM} — the same {@link play.xponer.astronima.wire.MemoryLogic}, so the same hazard
     * (rule 63) is worth re-proving on the type that actually draws it rather than assumed carried
     * over for free.
     */
    private static void aFramebufferShowsExactlyWhatWasWritten(GameTestHelper helper) {
        floor(helper, 1, 26);
        WirePart a0 = mount(helper, PartType.SWITCH, 1, 2, 2);
        WirePart a1 = mount(helper, PartType.SWITCH, 2, 2, 2);
        WirePart a2 = mount(helper, PartType.SWITCH, 3, 2, 2);
        WirePart a3 = mount(helper, PartType.SWITCH, 4, 2, 2);
        WirePart d0 = mount(helper, PartType.SWITCH, 5, 2, 2);
        WirePart d1 = mount(helper, PartType.SWITCH, 6, 2, 2);
        WirePart d2 = mount(helper, PartType.SWITCH, 7, 2, 2);
        WirePart d3 = mount(helper, PartType.SWITCH, 8, 2, 2);
        WirePart we = mount(helper, PartType.SWITCH, 9, 2, 2);
        WirePart clk = mount(helper, PartType.SWITCH, 10, 2, 2);
        WirePart screen = mountWith(helper, PartType.FRAMEBUFFER, 13, 0, 0, Circuit.empty());
        if (a0 == null || a1 == null || a2 == null || a3 == null || d0 == null || d1 == null
                || d2 == null || d3 == null || we == null || clk == null || screen == null) {
            return;
        }
        WirePart[] pins = {a0, a1, a2, a3, d0, d1, d2, d3, we, clk};
        DyeColor[] colours = {DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
                DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.CYAN, DyeColor.PURPLE,
                DyeColor.RED};
        for (int i = 0; i < pins.length; i++) {
            if (!wire(helper, pins[i], 0, screen, i, colours[i])) {
                return;
            }
        }

        // cell 0 (row 0, left half) = 0b1010, cell 1 (row 0, right half) = 0b0101,
        // cell 14 (row 7, left half) = 0b1111, cell 15 (row 7, right half) = 0b0011,
        // every other cell left at its reset value of zero.
        int[][] writes = {{0, 0b1010}, {1, 0b0101}, {14, 0b1111}, {15, 0b0011}};
        writeCellSequence(helper, a0, a1, a2, a3, d0, d1, d2, d3, we, clk, writes, 0, () -> {
            long refresh = play.xponer.astronima.wire.WireTicker.REFRESH_TICKS;
            assertScreenMatches(helper, screen,
                    Set.of(pixel(1, 0), pixel(3, 0), pixel(4, 0), pixel(6, 0),
                            pixel(0, 7), pixel(1, 7), pixel(2, 7), pixel(3, 7), pixel(4, 7), pixel(5, 7)),
                    "after four writes at known addresses", () -> {
                        // writeCellSequence always leaves clk and we open behind it, so the edge
                        // check needs its own genuine hold: a real write first (cell 0 -> 0b1111,
                        // all four left pixels of row 0 lit), then — without ever opening clk again
                        // — the data lines change underneath it. A level-sensitive write would
                        // silently redraw cell 0 a second time; an edge-triggered one must not.
                        setNibble(helper, a0, a1, a2, a3, 0);
                        setNibble(helper, d0, d1, d2, d3, 0b1111);
                        close(helper, we);
                        close(helper, clk);
                        helper.runAfterDelay(refresh, () -> assertScreenMatches(helper, screen,
                                Set.of(pixel(0, 0), pixel(1, 0), pixel(2, 0), pixel(3, 0), pixel(4, 0), pixel(6, 0),
                                        pixel(0, 7), pixel(1, 7), pixel(2, 7), pixel(3, 7), pixel(4, 7), pixel(5, 7)),
                                "after a real rising edge wrote 0b1111 into cell 0", () -> {
                                    setNibble(helper, d0, d1, d2, d3, 0b0000); // clk still held high
                                    helper.runAfterDelay(refresh, () -> assertScreenMatches(helper, screen,
                                            Set.of(pixel(0, 0), pixel(1, 0), pixel(2, 0), pixel(3, 0),
                                                    pixel(4, 0), pixel(6, 0), pixel(0, 7), pixel(1, 7),
                                                    pixel(2, 7), pixel(3, 7), pixel(4, 7), pixel(5, 7)),
                                            "clk stayed high and the data lines changed under it - a"
                                                    + " level-sensitive write would have redrawn cell 0"
                                                    + " to 0b0000 (all four left pixels of row 0 dark),"
                                                    + " and the picture must not have changed",
                                            helper::succeed));
                                }));
                    });
        });
    }

    /** One (x, y) pixel coordinate, for describing an expected picture as a set of lit points. */
    private record ScreenPixel(int x, int y) { }

    private static ScreenPixel pixel(int x, int y) {
        return new ScreenPixel(x, y);
    }

    /**
     * Runs one address/data/we/clk write per entry of {@code writes}, each a genuine rising edge
     * (clk is left low between writes so the next {@code close} is a real 0-to-1 transition), then
     * calls {@code onDone}.
     */
    private static void writeCellSequence(GameTestHelper helper, WirePart a0, WirePart a1, WirePart a2,
                                          WirePart a3, WirePart d0, WirePart d1, WirePart d2, WirePart d3,
                                          WirePart we, WirePart clk, int[][] writes, int index, Runnable onDone) {
        if (index >= writes.length) {
            onDone.run();
            return;
        }
        long refresh = play.xponer.astronima.wire.WireTicker.REFRESH_TICKS;
        setNibble(helper, a0, a1, a2, a3, writes[index][0]);
        setNibble(helper, d0, d1, d2, d3, writes[index][1]);
        close(helper, we);
        close(helper, clk);
        helper.runAfterDelay(refresh, () -> {
            open(helper, clk);
            open(helper, we);
            helper.runAfterDelay(refresh, () -> writeCellSequence(helper, a0, a1, a2, a3, d0, d1, d2, d3,
                    we, clk, writes, index + 1, onDone));
        });
    }

    /** Every one of the sixty-four pixels checked against {@code expectedLit}, not a spot check. */
    private static void assertScreenMatches(GameTestHelper helper, WirePart screen,
                                             Set<ScreenPixel> expectedLit, String context,
                                             Runnable onSuccess) {
        WirePart current = Wires.partAt(helper.getLevel(), screen.cell(), screen.face(),
                screen.u(), screen.v()).orElse(null);
        if (current == null) {
            helper.fail("the framebuffer came off the wall mid-test");
            return;
        }
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                boolean actual = play.xponer.astronima.wire.MemoryLogic.pixelAt(current.memory(), x, y);
                boolean expected = expectedLit.contains(pixel(x, y));
                if (actual != expected) {
                    helper.fail("pixel (" + x + "," + y + ") " + context + " should be "
                            + (expected ? "lit" : "dark") + " but was " + (actual ? "lit" : "dark"));
                    return;
                }
            }
        }
        onSuccess.run();
    }

    /**
     * Two panels, each written a different picture, must never show a pixel of the other's. Since
     * {@code memory} lives on each {@link WirePart} independently, this is really a check that
     * placement and addressing do not collide between two mounted instances of the same type — the
     * multi-block-monitor claim design/display.md §2.5 makes falls out of parts being parts, and
     * this is the one gametest that actually mounts two and asks.
     */
    private static void twoFramebuffersNeverShareAPixel(GameTestHelper helper) {
        floor(helper, 1, 40);
        WirePart a0 = mount(helper, PartType.SWITCH, 1, 2, 2);
        WirePart a1 = mount(helper, PartType.SWITCH, 2, 2, 2);
        WirePart a2 = mount(helper, PartType.SWITCH, 3, 2, 2);
        WirePart a3 = mount(helper, PartType.SWITCH, 4, 2, 2);
        WirePart d0 = mount(helper, PartType.SWITCH, 5, 2, 2);
        WirePart d1 = mount(helper, PartType.SWITCH, 6, 2, 2);
        WirePart d2 = mount(helper, PartType.SWITCH, 7, 2, 2);
        WirePart d3 = mount(helper, PartType.SWITCH, 8, 2, 2);
        WirePart we = mount(helper, PartType.SWITCH, 9, 2, 2);
        WirePart clk = mount(helper, PartType.SWITCH, 10, 2, 2);
        WirePart left = mountWith(helper, PartType.FRAMEBUFFER, 13, 0, 0, Circuit.empty());
        WirePart right = mountWith(helper, PartType.FRAMEBUFFER, 27, 0, 0, Circuit.empty());
        if (a0 == null || a1 == null || a2 == null || a3 == null || d0 == null || d1 == null
                || d2 == null || d3 == null || we == null || clk == null || left == null || right == null) {
            return;
        }
        WirePart[] pins = {a0, a1, a2, a3, d0, d1, d2, d3, we, clk};
        DyeColor[] colours = {DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
                DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.CYAN, DyeColor.PURPLE,
                DyeColor.RED};
        for (int i = 0; i < pins.length; i++) {
            if (!wire(helper, pins[i], 0, left, i, colours[i])) {
                return;
            }
        }
        // The bus reaches only the left panel; the right one never gets a rising edge and must
        // still read as a fresh, dark chip — nothing crosses between two parts that share no pad.
        int[][] writeLeft = {{0, 0b1111}};
        writeCellSequence(helper, a0, a1, a2, a3, d0, d1, d2, d3, we, clk, writeLeft, 0, () ->
                assertScreenMatches(helper, left, Set.of(pixel(0, 0), pixel(1, 0), pixel(2, 0), pixel(3, 0)),
                        "on the wired panel after its own write", () ->
                                assertScreenMatches(helper, right, Set.of(),
                                        "on the unwired panel, which received nothing",
                                        helper::succeed)));
    }

    /**
     * <em>"I cut a piece out of the middle — did I actually get two runs, or one run with a gap
     * that still reads as connected?"</em>
     *
     * <p>Rule 16's own invariant ("a network derived on demand cannot split wrongly, because
     * there is nothing to split") had never been checked against a genuine mid-run cut before the
     * snips existed — nothing in the build made one. This lays a single straight run of twenty
     * pixels, removes ten from the middle exactly the way {@code WireSnipsItem#commitCut} does
     * (same {@link Wires#pathBetween}, same {@link Wires#remove} loop — rule 14, the click is not
     * driven through a fake camera but the primitive underneath it is the real one), and checks
     * both halves: each is its own network, neither claims a pixel from the other side or from
     * the cut itself.
     */
    private static void snipsCutMidRunSplitsTheNetwork(GameTestHelper helper) {
        floor(helper, 1, 4);
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 1));
        DyeColor colour = DyeColor.WHITE;
        // Sixteen pixels: a face is only sixteen wide (0..15), so this is one straight run
        // covering it, not one run among several - the largest single-face run there is.
        List<WirePixel> run = new java.util.ArrayList<>();
        for (int u = 0; u < 16; u++) {
            run.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, 5));
        }
        Wires.placeAll(helper.getLevel(), run, colour, ConductorMaterial.IRON,
                play.xponer.astronima.sim.circuit.WireGauge.DEFAULT);

        WirePixel cutStart = run.get(4);
        WirePixel cutEnd = run.get(11);
        List<WirePixel> path = Wires.pathBetween(helper.getLevel(), cutStart, cutEnd, colour,
                Wires.DEFAULT_LIMIT);
        if (path.size() != 8) {
            helper.fail("the shortest path between pixel 4 and pixel 11 of a straight run came "
                    + "back " + path.size() + " pixels long, not the 8 a straight run between "
                    + "them actually is");
            return;
        }
        if (Wires.wouldStillBeJoinedAfter(helper.getLevel(), path, colour, Wires.DEFAULT_LIMIT)) {
            helper.fail("a straight run with no other route between the two cut points was "
                    + "reported as still joined after the cut - there is no loop here to bypass "
                    + "through");
            return;
        }
        for (WirePixel pixel : path) {
            Wires.remove(helper.getLevel(), pixel, colour);
        }

        Wires.Network nearSide = Wires.network(helper.getLevel(), run.get(0), colour);
        Wires.Network farSide = Wires.network(helper.getLevel(), run.get(15), colour);
        if (nearSide.length() != 4) {
            helper.fail("the near side of the cut has " + nearSide.length()
                    + " pixels, not the 4 left over from a 16-pixel run minus an 8-pixel cut "
                    + "starting at pixel 4");
            return;
        }
        if (farSide.length() != 4) {
            helper.fail("the far side of the cut has " + farSide.length()
                    + " pixels, not the 4 left over on the other side");
            return;
        }
        // Neither side may claim a single pixel of the other, or of what was cut - two networks
        // derived from one cut trace, not one network still quietly whole under two names.
        for (WirePixel pixel : farSide.pixels()) {
            if (nearSide.pixels().contains(pixel)) {
                helper.fail("the near and far networks after the cut share a pixel: " + pixel
                        + " - the cut did not actually separate them");
                return;
            }
        }
        for (WirePixel pixel : path) {
            if (nearSide.pixels().contains(pixel) || farSide.pixels().contains(pixel)) {
                helper.fail("a pixel that was cut, " + pixel + ", is still claimed by one of the "
                        + "two remaining networks");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * <em>"I marked two points on a loop — does the tool notice cutting between them would not
     * actually separate anything?"</em>
     *
     * <p>{@code design} note (Phase B): a run on a loop has more than one way from one marked
     * point to the other, so the shortest path between them is not the only path — cutting it
     * leaves the "two sides" the player meant to separate still one circuit, joined the long way
     * round. Built here as a plain square ring, well inside one face so every step is ordinary
     * 4-neighbour adjacency, with the two marked points at opposite corners so the shortest arc is
     * unambiguous and the long arc is the bypass this test exists to catch.
     */
    private static void snipsRefuseALoopThatWouldStillBeJoined(GameTestHelper helper) {
        floor(helper, 1, 4);
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 1));
        DyeColor colour = DyeColor.LIME;
        List<WirePixel> ring = new java.util.ArrayList<>();
        for (int u = 2; u <= 10; u++) {
            ring.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, 2));
        }
        for (int v = 3; v <= 10; v++) {
            ring.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), 10, v));
        }
        for (int u = 9; u >= 2; u--) {
            ring.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, 10));
        }
        for (int v = 9; v >= 3; v--) {
            ring.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), 2, v));
        }
        Wires.placeAll(helper.getLevel(), ring, colour, ConductorMaterial.IRON,
                play.xponer.astronima.sim.circuit.WireGauge.DEFAULT);

        WirePixel corner1 = new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), 2, 2);
        WirePixel corner2 = new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), 10, 10);
        List<WirePixel> shortestArc = Wires.pathBetween(helper.getLevel(), corner1, corner2, colour,
                Wires.DEFAULT_LIMIT);
        if (shortestArc.isEmpty()) {
            helper.fail("the two marked corners of a closed ring came back as not on the same "
                    + "run at all");
            return;
        }
        if (!Wires.wouldStillBeJoinedAfter(helper.getLevel(), shortestArc, colour,
                Wires.DEFAULT_LIMIT)) {
            helper.fail("cutting the shortest arc between two opposite corners of a closed ring "
                    + "was reported as fully separating them - the other arc around the ring is "
                    + "a real bypass that this check exists to catch");
            return;
        }
        helper.succeed();
    }

    /** Sets four switches to the low four bits of {@code value}, LSB first — an address or a nibble of data. */
    private static void setNibble(GameTestHelper helper, WirePart b0, WirePart b1, WirePart b2,
                                  WirePart b3, int value) {
        if ((value & 1) != 0) {
            close(helper, b0);
        } else {
            open(helper, b0);
        }
        if ((value & 2) != 0) {
            close(helper, b1);
        } else {
            open(helper, b1);
        }
        if ((value & 4) != 0) {
            close(helper, b2);
        } else {
            open(helper, b2);
        }
        if ((value & 8) != 0) {
            close(helper, b3);
        } else {
            open(helper, b3);
        }
    }

    /** Reads a RAM chip's currently addressed cell and either fails with context or continues. */
    private static void assertRamCell(GameTestHelper helper, WirePart ram, int expected,
                                      String failureMessage, Runnable onSuccess) {
        WirePart current = Wires.partAt(helper.getLevel(), ram.cell(), ram.face(), ram.u(), ram.v())
                .orElse(null);
        if (current == null) {
            helper.fail("the RAM chip came off the wall mid-test");
            return;
        }
        int actual = current.outputs() & 0xF;
        if (actual != expected) {
            helper.fail(failureMessage + " (expected " + expected + ", read " + actual + ")");
            return;
        }
        onSuccess.run();
    }

    /**
     * {@code design/bus.md} §5's own "the row that matters most": every lane of a laid ribbon must
     * end up its own separate {@link Wires.Network}, and landing on a real chip's pad row must seat
     * every lane on its own pin, not merely "close by". Run through exactly the sequence
     * {@code WireRibbonItem.layLeg} runs — {@link WireRouter} for lane 0, {@link
     * play.xponer.astronima.sim.wire.RibbonGeometry#laneRoutes} for the rest, {@link
     * Wires#placeAll} per lane — because the click itself needs a hit position inside a block face
     * and the harness has no camera, the same limit {@link #layTo} is already written around.
     */
    private static void aRibbonLandsOnAPadRowAndStaysSeparate(GameTestHelper helper) {
        floor(helper, 1, 20);
        WirePart ram = mountWith(helper, PartType.RAM, 13, 0, 0, Circuit.empty());
        if (ram == null) {
            return;
        }

        // Four cells west of RAM, at the same v the a-pads sit at (2) — a straight run east
        // arrives exactly on a0, because a straight crossing preserves v through every seam it
        // wraps (design/bus.md §1.1).
        BlockPos anchorCell = helper.absolutePos(new BlockPos(9, 2, 1));
        WirePixel anchor = new WirePixel(anchorCell.getX(), anchorCell.getY(), anchorCell.getZ(),
                Faces.of(FACE), 8, 2);
        WirePixel a0 = ram.padPixel(PartType.RAM.pads().get(0));

        WireRouter.Space space = pixel -> Wires.canPlace(helper.getLevel(), pixel)
                || pixel.equals(anchor) || pixel.equals(a0);
        List<WirePixel> lane0 = WireRouter.route(anchor, a0, space, WireRouter.Mode.STRAIGHT)
                .orElse(null);
        if (lane0 == null || !lane0.get(lane0.size() - 1).equals(a0)) {
            helper.fail("test setup itself is wrong: no straight route from the anchor to a0");
            return;
        }

        List<List<WirePixel>> lanes = play.xponer.astronima.sim.wire.RibbonGeometry
                .laneRoutes(lane0, 4).orElse(null);
        if (lanes == null) {
            helper.fail("four lanes should fit here - test's own layout is wrong");
            return;
        }
        for (int lane = 0; lane < 4; lane++) {
            WirePixel expected = ram.padPixel(PartType.RAM.pads().get(lane));
            WirePixel arrived = lanes.get(lane).get(lanes.get(lane).size() - 1);
            if (!arrived.equals(expected)) {
                helper.fail("lane " + lane + " should have landed on a" + lane + " at " + expected
                        + " but arrived at " + arrived);
                return;
            }
        }

        var gauge = play.xponer.astronima.sim.circuit.WireGauge.SIGNAL;
        DyeColor[] colours = play.xponer.astronima.item.WireRibbonItem.LANE_COLOURS;
        for (int lane = 0; lane < 4; lane++) {
            Wires.placeAll(helper.getLevel(), lanes.get(lane), colours[lane], ConductorMaterial.IRON, gauge);
        }

        // The property the whole phase exists for: every lane is its own network, never merged
        // with any other lane's — the exact hazard a shared colour or gap-only separation would
        // produce (memory-chips.md §6's own real-world example of this).
        for (int lane = 0; lane < 4; lane++) {
            Wires.Network network = Wires.network(helper.getLevel(), lanes.get(lane).get(0), colours[lane]);
            if (network.length() != lanes.get(lane).size()) {
                helper.fail("lane " + lane + "'s own network is " + network.length()
                        + " pixels, but " + lanes.get(lane).size() + " were laid - it merged with something");
                return;
            }
            for (int other = 0; other < 4; other++) {
                if (other == lane) {
                    continue;
                }
                for (WirePixel point : lanes.get(other)) {
                    if (network.pixels().contains(point)) {
                        helper.fail("lane " + lane + "'s network reaches into lane " + other
                                + " at " + point + " - two lanes merged");
                        return;
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * {@code design/processor.md} §6/§8: the whole reference machine, wired exactly as its own
     * block diagram shows, running the worked example program from its own JEI page — a real
     * assembled program driving a real screen through a real chip, not a hand-computed number
     * asserted directly. Exercises {@code LDA}, {@code STA}, {@code SUB}, {@code SHL}, {@code OR},
     * {@code IN}, {@code OUT}, {@code STROBE} and {@code JMP}/{@code JZ} — every kind of
     * instruction the ISA has, in one program short enough to type from the codex page.
     *
     * <p>The program: read the four input switches as a count (0-15), then light that many pixels
     * — clamped to four, since one nibble is all one screen cell can show — from the left of the
     * framebuffer's very first row, by building a low-bits mask one shift at a time and writing it
     * through the address/data/strobe protocol every {@code RAM}-shaped chip in this set already
     * uses.
     */
    private static void theReferenceMachineDrawsABarFromItsSwitches(GameTestHelper helper) {
        floor(helper, 1, 40);
        WirePart i0 = mount(helper, PartType.SWITCH, 1, 2, 2);
        WirePart i1 = mount(helper, PartType.SWITCH, 2, 2, 2);
        WirePart i2 = mount(helper, PartType.SWITCH, 3, 2, 2);
        WirePart i3 = mount(helper, PartType.SWITCH, 4, 2, 2);
        WirePart run = mount(helper, PartType.SWITCH, 5, 2, 2);
        WirePart we = mount(helper, PartType.SWITCH, 6, 2, 2);
        WirePart processor = mountWith(helper, PartType.PROCESSOR, 13, 0, 0, Circuit.empty());
        WirePart screen = mountWith(helper, PartType.FRAMEBUFFER, 27, 0, 0, Circuit.empty());
        if (i0 == null || i1 == null || i2 == null || i3 == null || run == null || we == null
                || processor == null || screen == null) {
            return;
        }

        // Fifteen lines converging on two twelve-pixel chips: the same hazard
        // design/memory-chips.md §6 found the hard way, guarded against here the same way -
        // every line its own colour, none reused.
        DyeColor[] colours = {DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
                DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.GRAY, DyeColor.LIGHT_GRAY,
                DyeColor.CYAN, DyeColor.PURPLE, DyeColor.BLUE, DyeColor.BROWN, DyeColor.GREEN,
                DyeColor.RED};
        int c = 0;
        if (!wire(helper, i0, 0, processor, 0, colours[c++])
                || !wire(helper, i1, 0, processor, 1, colours[c++])
                || !wire(helper, i2, 0, processor, 2, colours[c++])
                || !wire(helper, i3, 0, processor, 3, colours[c++])
                || !wire(helper, run, 0, processor, 4, colours[c++])
                // p0..p3 (processor outputs 0-3) -> a0..a3 (screen inputs 0-3)
                || !wire(helper, processor, 0, screen, 0, colours[c++])
                || !wire(helper, processor, 1, screen, 1, colours[c++])
                || !wire(helper, processor, 2, screen, 2, colours[c++])
                || !wire(helper, processor, 3, screen, 3, colours[c++])
                // q0..q3 (processor outputs 4-7) -> d0..d3 (screen inputs 4-7)
                || !wire(helper, processor, 4, screen, 4, colours[c++])
                || !wire(helper, processor, 5, screen, 5, colours[c++])
                || !wire(helper, processor, 6, screen, 6, colours[c++])
                || !wire(helper, processor, 7, screen, 7, colours[c++])
                // stb (processor output 8) -> clk (screen input 9)
                || !wire(helper, processor, 8, screen, 9, colours[c++])
                // we switch -> we (screen input 8)
                || !wire(helper, we, 0, screen, 8, colours[c++])) {
            return;
        }

        String source = """
                start:
                    LDA #0
                    STA 240
                    IN 0
                    STA 241
                loop:
                    LDA 241
                    SUB #0
                    JZ draw
                    SUB one
                    STA 241
                    LDA 240
                    SHL
                    OR one
                    STA 240
                    JMP loop
                draw:
                    LDA #0
                    OUT 0
                    LDA 240
                    OUT 1
                    STROBE
                    JMP start
                .org 250
                one:
                    .byte 1
                """;
        play.xponer.astronima.sim.processor.Assembler.Result assembled =
                play.xponer.astronima.sim.processor.Assembler.assemble(source);
        if (!assembled.ok()) {
            helper.fail("the reference machine's own worked example failed to assemble: "
                    + assembled.errors());
            return;
        }
        WirePart programmed = processor.withProgram(source,
                play.xponer.astronima.sim.processor.Machine.loaded(assembled.bytes()).pack());
        Wires.mount(helper.getLevel(), programmed);

        close(helper, we);
        // Input = 3 (LSB first: i0, i1 closed; i2, i3 open) - the loop this program runs needs
        // roughly thirty instructions for this value, comfortably inside one refresh's budget.
        close(helper, i0);
        close(helper, i1);
        close(helper, run);

        long refresh = play.xponer.astronima.wire.WireTicker.REFRESH_TICKS;
        helper.runAfterDelay(refresh * 2, () -> assertScreenMatches(helper, screen,
                Set.of(pixel(0, 0), pixel(1, 0), pixel(2, 0)),
                "after the reference machine drew input=3 as a three-pixel bar", helper::succeed));
    }

    /**
     * <em>"It went on facing the wrong way, so I turned it."</em>
     *
     * <p>Placement points a part's output where the player was looking, which is right most of the
     * time. The wrench is the answer for the rest of it (rule 22 made it the mod's one "turn this"
     * verb) — and a turn that did not move the pads would be a part that looks different and wires
     * the same, which is the worst of both.
     */
    private static void theWrenchTurnsAPart(GameTestHelper helper) {
        floor(helper, 1, 1);
        WirePart gate = mount(helper, PartType.GATE_AND, 1, 5, 5);
        if (gate == null) {
            return;
        }
        WirePixel before = padPixel(gate, 0, true);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        play.xponer.astronima.wire.PartInteraction.turn(helper.getLevel(), player, gate);

        WirePart turned = Wires.partAt(helper.getLevel(), gate.cell(), gate.face(),
                gate.u(), gate.v()).orElse(null);
        if (turned == null) {
            helper.fail("the gate vanished when it was turned");
            return;
        }
        if (padPixel(turned, 0, true).equals(before)) {
            helper.fail("the wrench turned the gate and its output pad did not move - it looks"
                    + " different and wires exactly the same");
            return;
        }
        if (turned.rotation() == gate.rotation()) {
            helper.fail("the part's rotation did not change");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I got the gate back off the wall without breaking the wall."</em>
     *
     * <p>The only way a part comes off deliberately. Without it the sole method is destroying
     * whatever it is bolted to, which for a gate on a habitat hull is not a repair, it is a hole.
     */
    private static void cuttersTakeAPartOffTheWall(GameTestHelper helper) {
        floor(helper, 1, 1);
        WirePart gate = mount(helper, PartType.GATE_XOR, 1, 5, 5);
        if (gate == null) {
            return;
        }
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack cutters = new ItemStack(ModItems.WIRE_CUTTERS.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, cutters);

        cutters.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(
                helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, cutters,
                hitOnTop(gate.support(), gate.u() + 2, gate.v() + 2)));

        if (Wires.partAt(helper.getLevel(), gate.cell(), gate.face(), gate.u(), gate.v())
                .isPresent()) {
            helper.fail("the cutters were used on the gate and it is still on the wall");
            return;
        }
        if (!player.getInventory().contains(
                new ItemStack(ModItems.part(PartType.GATE_XOR)))) {
            helper.fail("the gate came off the wall and was not handed back");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Renamed it, mounted it, mined it back — and it was blank again."</em>
     *
     * <p>The name lives on {@link WirePart}, not on the item stack, because the stack is
     * consumed the moment a plate is bolted to a wall ({@code LogicPartItem#useOn}). Anything not
     * copied into the part at that moment is simply gone — which is exactly what a name was,
     * before this field existed. Proven the same way {@code cuttersTakeAPartOffTheWall} proves the
     * part itself comes back: mount it named, cut it, read the stack the cutters handed back.
     */
    private static void aNamedPlateKeepsItsNameOffTheWall(GameTestHelper helper) {
        floor(helper, 1, 1);
        WirePart plate = mountWith(helper, PartType.PLATE, 1, 5, 5, Circuit.empty());
        if (plate == null) {
            return;
        }
        Wires.mount(helper.getLevel(), plate.withName("Inverter"));

        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack cutters = new ItemStack(ModItems.WIRE_CUTTERS.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, cutters);
        cutters.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(
                helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, cutters,
                hitOnTop(plate.support(), plate.u() + 2, plate.v() + 2)));

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(ModItems.part(PartType.PLATE))) {
                continue;
            }
            var customName = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            if (customName != null && "Inverter".equals(customName.getString())) {
                helper.succeed();
                return;
            }
        }
        helper.fail("a named plate lost its name coming off the wall");
    }

    /**
     * <em>"I saved metal on the run and the machine slowed down."</em>
     *
     * <p><strong>The second axis of the ladder, in the world</strong> (§3.2). Same metal, same
     * length, same route — only the cross-section differs, and what arrives at the far end differs
     * with it. If that ever stops being true the gauge is a cosmetic dropdown.
     *
     * <p>Asserted on the <em>run</em> rather than on a conductor, because the thing that can rot is
     * the seam: a trace has to carry its gauge into the chunk, back out of it, and through the walk
     * that resolves a circuit. The arithmetic underneath is {@code WireGaugeTest}'s job.
     */
    private static void aThinRunLosesMoreThanAThickOne(GameTestHelper helper) {
        floor(helper, 1, 4);
        double thin = resistanceOfRun(helper, DyeColor.WHITE,
                play.xponer.astronima.sim.circuit.WireGauge.SIGNAL, 2);
        double thick = resistanceOfRun(helper, DyeColor.RED,
                play.xponer.astronima.sim.circuit.WireGauge.HEAVY, 6);

        if (thin <= 0 || thick <= 0) {
            helper.fail("a run reported no resistance at all: thin=" + thin + " thick=" + thick);
            return;
        }
        double areaRatio = play.xponer.astronima.sim.circuit.WireGauge.HEAVY.squareMillimetres()
                / play.xponer.astronima.sim.circuit.WireGauge.SIGNAL.squareMillimetres();
        if (Math.abs(thin / thick - areaRatio) > areaRatio * 0.05) {
            helper.fail("the same length of signal and heavy wire resist " + thin + " and "
                    + thick + " ohms, a ratio of " + (thin / thick) + " where the areas differ by "
                    + areaRatio + " - the gauge is not reaching the run, so choosing it is"
                    + " cosmetic");
            return;
        }
        helper.succeed();
    }

    /** Lays one metre of a given gauge on the floor and asks the power walk what it resists. */
    private static double resistanceOfRun(GameTestHelper helper, DyeColor colour,
                                          play.xponer.astronima.sim.circuit.WireGauge gauge,
                                          int v) {
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 1));
        List<WirePixel> run = new java.util.ArrayList<>();
        for (int u = 0; u < 16; u++) {
            run.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, v));
        }
        Wires.placeAll(helper.getLevel(), run, colour, ConductorMaterial.IRON, gauge);
        return play.xponer.astronima.wire.WirePower
                .resolve(helper.getLevel(), run.get(0), colour).resistanceOhms();
    }

    /**
     * <em>"I ran a machine down signal wire and it cooked."</em>
     *
     * <p><strong>E7's consequence, and it took the gauge axis to make it reachable.</strong> Every
     * trace used to be 4 mm2, rated 13.7 A against a machine's 5.2 A, so nothing a player could
     * build got near its limit. Signal wire is rated 2.9 A: a single machine is nearly twice over
     * it, which is the ordinary accident this hazard exists for — using the conductor that was
     * lying in the toolbox.
     *
     * <p>Three things are asserted because three separate things have to be true, and the middle
     * one is what stops this being a trap: it <strong>warms rather than failing instantly</strong>
     * (a wire has mass, and rule 7 wants the warning readable before the loss), it
     * <strong>discolours on the way</strong>, and only then is it gone.
     *
     * <p>The same current on standard wire must do none of it — otherwise the hazard is not about
     * sizing a conductor, it is about using electricity.
     */
    private static void anOverloadedRunCooksAndThenFails(GameTestHelper helper) {
        floor(helper, 1, 2);
        var thin = laid(helper, DyeColor.WHITE, play.xponer.astronima.sim.circuit.WireGauge.SIGNAL,
                4);
        var thick = laid(helper, DyeColor.RED, play.xponer.astronima.sim.circuit.WireGauge.STANDARD,
                10);

        // A machine's worth of draw, a second of game time at a time. How long it takes is
        // measured rather than assumed: the time constant falls as the cube of temperature, so a
        // warming wire accelerates and any hand-computed answer would be wrong in an interesting
        // way. What matters is not the number — it is that there IS a window, and that the wire
        // looks wrong inside it.
        double worstScorch = 0;
        int failedAt = -1;
        for (int second = 1; second <= 600 && failedAt < 0; second++) {
            var resolved = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), thin,
                    DyeColor.WHITE);
            if (resolved.isEmpty()) {
                failedAt = second;
                break;
            }
            worstScorch = Math.max(worstScorch, play.xponer.astronima.sim.circuit.Scorch.of(
                    temperatureOf(helper, thin, DyeColor.WHITE),
                    play.xponer.astronima.wire.WireTrace.REST_K));
            play.xponer.astronima.wire.WirePower.send(helper.getLevel(), resolved, 250, 1.0);
            // The same call the server tick makes: everything that used a run this tick has
            // spoken, so now the run knows what it carried and the wire ages once by it.
            play.xponer.astronima.wire.WireTicker.age(helper.getLevel(), 1.0);
        }
        run(helper, thick, DyeColor.RED, 600);

        if (failedAt < 0) {
            helper.fail("ten minutes at nearly twice its rating and the signal wire survived at "
                    + Math.round(temperatureOf(helper, thin, DyeColor.WHITE)) + " K - the"
                    + " thinnest rung in the ladder cannot be overloaded, so sizing a conductor"
                    + " decides nothing");
            return;
        }
        // Rule 7: the warning has to be readable before anything is lost. A wire has mass, and
        // that mass is what turns an overload into a window rather than a verdict.
        if (failedAt < 30) {
            helper.fail("signal wire failed after " + failedAt + " s - a hazard that fires"
                    + " before its own warning can be acted on is a trap, not a lesson");
            return;
        }
        if (worstScorch < 0.3) {
            helper.fail("the wire burned through having never looked worse than "
                    + Math.round(worstScorch * 100) + "% cooked - nothing about it said it was"
                    + " in trouble while there was still time");
            return;
        }
        if (!Wires.has(helper.getLevel(), thick, DyeColor.RED)) {
            helper.fail("the standard gauge burned under one machine - that is not a hazard about"
                    + " sizing a conductor, it is a hazard about using electricity");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"The fuse is spent. The breaker I throw back on — once I have fixed what tripped it."</em>
     *
     * <p><strong>The whole component in one scenario, and it is a comparison rather than a
     * description.</strong> Rule 8 forbids one mechanic reskinned with different constants, and the
     * breaker trips at exactly the current the fuse blows at — so the only honest test is the
     * <em>same click</em> on both, with opposite outcomes. Anything less would pass on a fuse that
     * had been recoloured.
     *
     * <p>Five claims, in the order a player meets them. A hand can <strong>kill</strong> the
     * circuit, which no fuse can do. The same hand <strong>brings it back</strong>, with nothing
     * consumed. It <strong>trips</strong> on an overload and the wire survives. It
     * <strong>cannot be reset into a live fault</strong> — that is the headline, and nothing in the
     * code checks for it: the ordinary overload check simply finds the overload still there. And
     * the same click on a <strong>blown fuse does nothing at all</strong>.
     */
    private static void aBreakerIsThrownBackOn(GameTestHelper helper) {
        floor(helper, 1, 3);
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 1));
        var gauge = play.xponer.astronima.sim.circuit.WireGauge.STANDARD;

        WirePart breaker = mount(helper, PartType.BREAKER, 1, 7, 7);
        WirePart fuse = mount(helper, PartType.FUSE, 3, 7, 7);
        if (breaker == null || fuse == null) {
            return;
        }
        List<WirePixel> left = new java.util.ArrayList<>();
        List<WirePixel> right = new java.util.ArrayList<>();
        for (int u = 0; u <= breaker.u(); u++) {
            left.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, 8));
        }
        for (int u = breaker.u() + 2; u < 16; u++) {
            right.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, 8));
        }
        Wires.placeAll(helper.getLevel(), left, DyeColor.WHITE, ConductorMaterial.IRON, gauge);
        Wires.placeAll(helper.getLevel(), right, DyeColor.WHITE, ConductorMaterial.IRON, gauge);
        WirePixel far = right.get(right.size() - 1);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

        if (!reaches(helper, left.get(0), far)) {
            helper.fail("a breaker that is on did not join its two sides - a run cannot pass"
                    + " through it, so it protects nothing and interrupts nothing");
            return;
        }

        // Thrown off by hand: the thing a fuse cannot do, and the reason a real panel has these.
        throwLever(helper, player, breaker);
        if (reaches(helper, left.get(0), far)) {
            helper.fail("the breaker was thrown off and the circuit carried on through it - it"
                    + " isolates nothing, so there is no safe way to work on a live run");
            return;
        }

        // And thrown back: no item spent, the same part, the circuit whole again.
        helper.runAfterDelay(2, () -> {
            throwLever(helper, player, breaker);
            if (!reaches(helper, left.get(0), far)) {
                helper.fail("the breaker was thrown back on and stayed dead - a protection you"
                        + " cannot reset is a fuse, and a fuse already exists");
                return;
            }

            // Three machines' worth: over the 12 A it trips at, and over what the wire survives.
            overload(helper, left.get(0));
            if (!tripped(helper, breaker)) {
                helper.fail("three machines drew through a 12 A breaker and it held - the run is"
                        + " protected by nothing at all");
                return;
            }
            if (!Wires.has(helper.getLevel(), left.get(0), DyeColor.WHITE)
                    || !Wires.has(helper.getLevel(), right.get(0), DyeColor.WHITE)) {
                helper.fail("the breaker tripped and the wire burned anyway - the cheap thing"
                        + " failing instead of the expensive one is the entire point");
                return;
            }

            helper.runAfterDelay(2, () -> {
                // The headline: thrown back on with the fault still there. Nothing in the code
                // asks whether it is - the ordinary overload check runs and finds it.
                throwLever(helper, player, breaker);
                if (tripped(helper, breaker)) {
                    helper.fail("the breaker would not even go back on while the fault was there -"
                            + " it has to be resettable, or the lesson is unreachable");
                    return;
                }
                overload(helper, left.get(0));
                if (!tripped(helper, breaker)) {
                    helper.fail("the breaker was reset into a live overload and held - so the way"
                            + " past a fault is to keep flipping the lever, and nothing ever"
                            + " teaches you to fix the circuit first");
                    return;
                }

                helper.runAfterDelay(2, () -> {
                    // Fault fixed - two machines instead of three - and now it holds.
                    throwLever(helper, player, breaker);
                    var whole = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(),
                            left.get(0), DyeColor.WHITE);
                    play.xponer.astronima.wire.WirePower.send(helper.getLevel(), whole, 500, 1.0);
                    play.xponer.astronima.wire.WireTicker.age(helper.getLevel(), 1.0);
                    if (tripped(helper, breaker)) {
                        helper.fail("two machines tripped a 12 A breaker - it protects a bus it is"
                                + " too small for, so the fix for an overload is fewer machines"
                                + " than the wire can carry");
                        return;
                    }

                    // The same click, on a blown fuse: nothing. That contrast is the component.
                    var line = padPixel(fuse, 0, false);
                    Wires.place(helper.getLevel(), line, DyeColor.LIME, ConductorMaterial.IRON,
                            gauge);
                    Wires.place(helper.getLevel(), padPixel(fuse, 1, false), DyeColor.LIME,
                            ConductorMaterial.IRON, gauge);
                    overload(helper, line, DyeColor.LIME);
                    if (!tripped(helper, fuse)) {
                        helper.fail("the fuse did not blow, so there is nothing to compare a"
                                + " breaker's reset against");
                        return;
                    }
                    throwLever(helper, player, fuse);
                    if (!tripped(helper, fuse)) {
                        helper.fail("clicking a blown fuse mended it - then the breaker is the"
                                + " same part with a different texture, and one of them should"
                                + " not exist");
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    /** A real click on a part: found by aiming at it, then operated the way the handler does. */
    private static void throwLever(GameTestHelper helper, net.minecraft.world.entity.player.Player
            player, WirePart part) {
        WirePart aimed = play.xponer.astronima.wire.PartInteraction.under(helper.getLevel(),
                hitOnTop(part.support(), part.u() + 1, part.v() + 1));
        if (aimed == null) {
            helper.fail("aiming at the middle of a " + part.type() + " found nothing to click");
            return;
        }
        play.xponer.astronima.wire.PartInteraction.operate(helper.getLevel(), player, aimed);
    }

    /** Whether that part has given up, read back from the chunk rather than from a snapshot. */
    private static boolean tripped(GameTestHelper helper, WirePart part) {
        return Wires.partAt(helper.getLevel(), part.cell(), part.face(), part.u(), part.v())
                .map(WirePart::held).orElse(false);
    }

    /** Whether a run started at one pixel gets as far as another. */
    private static boolean reaches(GameTestHelper helper, WirePixel from, WirePixel to) {
        return play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), from, DyeColor.WHITE)
                .pixels().contains(to);
    }

    private static void overload(GameTestHelper helper, WirePixel at) {
        overload(helper, at, DyeColor.WHITE);
    }

    /** Three machines' worth down one run, then one tick of settlement. */
    private static void overload(GameTestHelper helper, WirePixel at, DyeColor colour) {
        var run = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), at, colour);
        play.xponer.astronima.wire.WirePower.send(helper.getLevel(), run, 750, 1.0);
        play.xponer.astronima.wire.WireTicker.age(helper.getLevel(), 1.0);
    }

    /**
     * <em>"The fuse never went. The wire did."</em>
     *
     * <p><strong>Protection was a flat twelve amps</strong>, quoted against 4 mm² iron, and that is
     * decoration on anything thinner: signal wire gives up under three amps, so a fuse waiting for
     * twelve waits for a current the conductor cannot survive reaching. The previous slices made it
     * reachable rather than theoretical — one machine five metres down signal wire pulls over four
     * amps.
     *
     * <p><strong>Not three fuses.</strong> A 2.3 A fuse and an 11 A fuse are the same idea with a
     * different number, and rule 8 refuses three item ids for that. The rating comes from the run
     * instead, which is also what a fuse physically is: a deliberately weak length of the same
     * conductor.
     *
     * <p>Two claims. Signal wire is now <strong>protected</strong> at a current that used to sail
     * past. And a busbar trunk with <strong>one pixel of signal wire spliced into it</strong> is
     * protected at the signal rating — a chain is its weakest link, exactly as it already is for
     * resistance, and one pixel of the wrong wire now ruins a trunk's protection as well as its
     * resistance.
     */
    private static void protectionIsSizedToTheWire(GameTestHelper helper) {
        double signalAmps = play.xponer.astronima.sim.circuit.Protection.ratingAmps(
                play.xponer.astronima.sim.circuit.WireGauge.SIGNAL, ConductorMaterial.IRON);
        double busbarAmps = play.xponer.astronima.sim.circuit.Protection.ratingAmps(
                play.xponer.astronima.sim.circuit.WireGauge.BUSBAR, ConductorMaterial.IRON);
        if (!(busbarAmps > signalAmps * 2)) {
            helper.fail("busbar protects at " + busbarAmps + " A and signal at " + signalAmps
                    + " A - the two rungs are too close for this scenario to tell them apart");
            return;
        }
        // Between the SIGNAL and STANDARD rungs, not between signal and busbar - and the
        // difference decides whether this scenario proves anything. The old flat rating was twelve
        // amps, so a current above twelve blows every fuse in the game and "signal wire is
        // protected now" would pass against the very model it is meant to condemn. Sat below
        // twelve, the first and third claims each go red on their own (rule 19).
        double standardAmps = play.xponer.astronima.sim.circuit.Protection.ratingAmps(
                play.xponer.astronima.sim.circuit.WireGauge.STANDARD, ConductorMaterial.IRON);
        double between = (signalAmps + standardAmps) / 2;
        double watts = between * play.xponer.astronima.sim.circuit.Delivery.BUS_VOLTS;

        floor(helper, 1, 4);
        boolean thinWent = protectionGoes(helper, 1, DyeColor.WHITE,
                play.xponer.astronima.sim.circuit.WireGauge.SIGNAL, false, watts);
        boolean thickHeld = !protectionGoes(helper, 3, DyeColor.LIME,
                play.xponer.astronima.sim.circuit.WireGauge.BUSBAR, false, watts);
        boolean splicedWent = protectionGoes(helper, 5, DyeColor.MAGENTA,
                play.xponer.astronima.sim.circuit.WireGauge.BUSBAR, true, watts);

        if (!thinWent) {
            helper.fail("a signal run carried " + Math.round(between) + " A - four times what it"
                    + " survives - and its fuse did not go. Protection that waits for a current"
                    + " the conductor cannot reach is a component that watches the wire burn");
            return;
        }
        if (!thickHeld) {
            helper.fail("the same " + Math.round(between) + " A blew a busbar run's fuse, which"
                    + " carries it comfortably - protection sized to the thinnest thing in the"
                    + " game is protection nobody can build a bus with");
            return;
        }
        if (!splicedWent) {
            helper.fail("a busbar trunk with one pixel of signal wire spliced into it held at "
                    + Math.round(between) + " A - the weakest link decides what a run can carry,"
                    + " and the protection has to agree with the conductor or it is guarding a"
                    + " wire that is not there");
            return;
        }
        helper.succeed();
    }

    /**
     * Builds a run of one gauge with protection in the middle, drives it, and says whether it went.
     *
     * <p><strong>A metre, not a pixel.</strong> A gauge is a property of a trace — one cell, one
     * face, one colour — rather than of a pixel, so the finest a splice can be is one block's worth
     * of wall. That is the real granularity of the mistake and the scenario has to use it: a test
     * that spliced a single pixel would be testing a model this mod does not have.
     *
     * @param spliced continue the run into the next cell in signal wire, however thick it started
     */
    private static boolean protectionGoes(GameTestHelper helper, int x, DyeColor colour,
                                          play.xponer.astronima.sim.circuit.WireGauge gauge,
                                          boolean spliced, double watts) {
        BlockPos cell = helper.absolutePos(new BlockPos(x, 2, 1));
        WirePart fuse = mount(helper, PartType.FUSE, x, 7, 7);
        if (fuse == null) {
            return false;
        }
        List<WirePixel> left = new java.util.ArrayList<>();
        List<WirePixel> right = new java.util.ArrayList<>();
        for (int u = 0; u <= fuse.u(); u++) {
            left.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, 8));
        }
        for (int u = fuse.u() + 2; u < 16; u++) {
            right.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, 8));
        }
        Wires.placeAll(helper.getLevel(), left, colour, ConductorMaterial.IRON, gauge);
        Wires.placeAll(helper.getLevel(), right, colour, ConductorMaterial.IRON, gauge);
        if (spliced) {
            BlockPos next = helper.absolutePos(new BlockPos(x + 1, 2, 1));
            List<WirePixel> thin = new java.util.ArrayList<>();
            for (int u = 0; u < 16; u++) {
                thin.add(new WirePixel(next.getX(), next.getY(), next.getZ(), Faces.of(FACE),
                        u, 8));
            }
            Wires.placeAll(helper.getLevel(), thin, colour, ConductorMaterial.IRON,
                    play.xponer.astronima.sim.circuit.WireGauge.SIGNAL);
        }

        var run = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), left.get(0),
                colour);
        if (run.isEmpty()) {
            helper.fail("the run at x=" + x + " did not resolve - the layout is wrong");
            return false;
        }
        var expected = spliced ? play.xponer.astronima.sim.circuit.WireGauge.SIGNAL : gauge;
        if (run.gauge() != expected) {
            helper.fail("the run at x=" + x + " came out as " + run.gauge() + " where the layout"
                    + " meant " + expected + " - the two stretches did not join, so this is"
                    + " measuring the test rather than the game");
            return false;
        }
        play.xponer.astronima.wire.WirePower.send(helper.getLevel(), run, watts, 1.0);
        play.xponer.astronima.wire.WireTicker.age(helper.getLevel(), 1.0);
        return Wires.partAt(helper.getLevel(), fuse.cell(), fuse.face(), fuse.u(), fuse.v())
                .map(WirePart::held).orElse(false);
    }

    /** Drives a machine's draw down a run for {@code seconds} of game time. */
    private static void run(GameTestHelper helper, WirePixel at, DyeColor colour, int seconds) {
        for (int second = 0; second < seconds; second++) {
            var resolved = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), at,
                    colour);
            if (resolved.isEmpty()) {
                return;      // it has already burned through; there is nothing left to drive
            }
            play.xponer.astronima.wire.WirePower.send(helper.getLevel(), resolved, 250, 1.0);
            play.xponer.astronima.wire.WireTicker.age(helper.getLevel(), 1.0);
        }
    }

    /**
     * <em>"The fuse went, so the wall did not."</em>
     *
     * <p><strong>E10, and the reason {@code design/electrical.md} §4.2b calls a fuse not
     * optional.</strong> An overloaded run has no other cheap failure: without one, the only thing
     * that gives is the conductor, slowly, in whichever place its cooling was worst — which may be
     * behind a wall built afterwards.
     *
     * <p>Three claims, and the third is the one that makes it a component rather than a warning
     * light: it <strong>goes first</strong>, the wire is <strong>unharmed</strong>, and the circuit
     * is <strong>broken</strong> — a run through a blown fuse is two runs, and what was downstream
     * stops.
     */
    private static void aFuseGoesInsteadOfTheWire(GameTestHelper helper) {
        floor(helper, 1, 2);
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 1));

        // A fuse in the middle of a standard run: wire up to its line pad, wire on from its load.
        WirePart fuse = mount(helper, PartType.FUSE, 1, 7, 7);
        if (fuse == null) {
            return;
        }
        List<WirePixel> left = new java.util.ArrayList<>();
        List<WirePixel> right = new java.util.ArrayList<>();
        for (int u = 0; u <= fuse.u(); u++) {
            left.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, 8));
        }
        for (int u = fuse.u() + 2; u < 16; u++) {
            right.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, 8));
        }
        var gauge = play.xponer.astronima.sim.circuit.WireGauge.STANDARD;
        Wires.placeAll(helper.getLevel(), left, DyeColor.WHITE, ConductorMaterial.IRON, gauge);
        Wires.placeAll(helper.getLevel(), right, DyeColor.WHITE, ConductorMaterial.IRON, gauge);

        // While it holds, the two sides are one circuit: the fuse is a piece of conductor.
        var whole = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(),
                left.get(0), DyeColor.WHITE);
        if (!whole.pixels().contains(right.get(right.size() - 1))) {
            helper.fail("an intact fuse did not join its two sides - a run cannot pass through it,"
                    + " so it protects nothing and interrupts nothing");
            return;
        }

        // Three machines' worth: over the fuse's 12 A and over what the wire will survive.
        play.xponer.astronima.wire.WirePower.send(helper.getLevel(), whole, 750, 1.0);
        play.xponer.astronima.wire.WireTicker.age(helper.getLevel(), 1.0);

        WirePart after = Wires.partAt(helper.getLevel(), fuse.cell(), fuse.face(), fuse.u(),
                fuse.v()).orElse(null);
        if (after == null || !after.held()) {
            helper.fail("three machines drew through a 12 A fuse and it did not go - the run is"
                    + " protected by nothing at all");
            return;
        }
        if (!Wires.has(helper.getLevel(), left.get(0), DyeColor.WHITE)
                || !Wires.has(helper.getLevel(), right.get(0), DyeColor.WHITE)) {
            helper.fail("the fuse blew and the wire burned anyway - the cheap thing failing"
                    + " instead of the expensive one is the entire point");
            return;
        }
        var broken = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(),
                left.get(0), DyeColor.WHITE);
        if (broken.pixels().contains(right.get(right.size() - 1))) {
            helper.fail("the fuse blew and the circuit carried on through it - a fuse that does"
                    + " not interrupt is a warning light");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"Both branches look fine and the trunk is cooking."</em>
     *
     * <p><strong>§4.2b's headline, and it was not true.</strong> That section describes the mistake
     * this tier exists to teach — <em>"the trunk now carries both branch currents, so an undersized
     * trunk overheats while both branches look fine. That is a real mistake with a real
     * symptom."</em> Every consumer asked its run for power on its own, so a run feeding two
     * machines saw two separate draws and never once saw their sum.
     *
     * <p>It is not a rounding error: loss goes as the <strong>square</strong> of the current, so two
     * machines waste four times what one does rather than twice. Whether a trunk is undersized was
     * the one decision in the tier that could not be got wrong, because nothing added it up.
     *
     * <p>Asserted by temperature, because that is what a player sees: the same wire carrying two
     * machines has to end up hotter than the same wire carrying one.
     */
    private static void aTrunkCarriesWhatItsBranchesDraw(GameTestHelper helper) {
        floor(helper, 1, 2);
        var gauge = play.xponer.astronima.sim.circuit.WireGauge.SIGNAL;
        WirePixel loaded = laid(helper, DyeColor.WHITE, gauge, 4);
        WirePixel spur = laid(helper, DyeColor.RED, gauge, 10);

        // The two machines on the trunk enter it at OPPOSITE ENDS, and that detail is
        // load-bearing. A run has no id, so the ledger has to name it by something derived — and
        // if it named it by "whichever pixel the walk started from", two consumers would file two
        // different names for one circuit and the sum would never happen. Resolving both draws
        // from the same pixel hides that completely, which is rule 19's third lesson: assert the
        // property, not the repetition.
        WirePixel farEnd = new WirePixel(loaded.x(), loaded.y(), loaded.z(), loaded.face(), 15, 4);
        for (int second = 0; second < 20; second++) {
            var fromOneEnd = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(),
                    loaded, DyeColor.WHITE);
            var fromTheOther = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(),
                    farEnd, DyeColor.WHITE);
            var quiet = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), spur,
                    DyeColor.RED);
            if (fromOneEnd.isEmpty() || fromTheOther.isEmpty() || quiet.isEmpty()) {
                break;
            }
            play.xponer.astronima.wire.WirePower.send(helper.getLevel(), fromOneEnd, 250, 1.0);
            play.xponer.astronima.wire.WirePower.send(helper.getLevel(), fromTheOther, 250, 1.0);
            play.xponer.astronima.wire.WirePower.send(helper.getLevel(), quiet, 250, 1.0);
            play.xponer.astronima.wire.WireTicker.age(helper.getLevel(), 1.0);
        }

        double trunk = temperatureOf(helper, loaded, DyeColor.WHITE);
        double branch = temperatureOf(helper, spur, DyeColor.RED);
        if (trunk <= 0 || branch <= 0) {
            helper.fail("a run vanished before the comparison could be made: trunk=" + trunk
                    + " branch=" + branch);
            return;
        }
        // Four times the loss, not twice: I squared R. A trunk that merely matched its branch
        // would mean the two draws were still being counted separately.
        if (!(trunk > branch + 15)) {
            helper.fail("two machines left the wire at " + Math.round(trunk) + " K and one left it"
                    + " at " + Math.round(branch) + " K - the run is still adding up its"
                    + " consumers one at a time, so whether a trunk is undersized cannot be got"
                    + " wrong");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"The run was getting hot, so I ran a second wire beside it — and it got worse."</em>
     *
     * <p><strong>§5.1, and the thing it promised that was not true.</strong> A run's resistance was
     * its <em>pixel count</em> times ρ/A: every pixel in the network treated as one series chain.
     * So the two most ordinary things a player does to a circuit came out backwards.
     *
     * <ul>
     *   <li>Laying a <strong>second conductor</strong> beside the first is two resistors in
     *       parallel and must be <em>half</em> the resistance. Counting made it twice — the correct
     *       fix for an overheating run made it hotter.</li>
     *   <li>A <strong>dead-end spur</strong>, left over from a route that was abandoned, carries no
     *       current and must cost nothing. Counting charged for every pixel of it.</li>
     * </ul>
     *
     * <p>Measured as <strong>joules that arrive</strong> rather than as an ohm figure, because
     * arriving is what a player sees: the cell at the far end fills faster (rule 11).
     */
    private static void aSecondWireSharesTheLoad(GameTestHelper helper) {
        // Two cells with wall between them, wired along the faces the terminals are actually on.
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(1, 2, 1),
                play.xponer.astronima.registry.ModBlocks.POWER_CELL.get());
        helper.setBlock(new BlockPos(5, 2, 1),
                play.xponer.astronima.registry.ModBlocks.POWER_CELL.get());

        WirePixel from = powerPad(helper, new BlockPos(1, 2, 1));
        WirePixel to = powerPad(helper, new BlockPos(5, 2, 1));
        if (from == null || to == null) {
            helper.fail("a power cell published no power terminal - the test's layout is wrong");
            return;
        }
        if (!layTo(helper, from, to, DyeColor.WHITE)) {
            return;
        }

        double single = arriving(helper, from);
        var run = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), from,
                DyeColor.WHITE);
        if (run.terminals().size() < 2) {
            helper.fail("the run reached " + run.terminals().size() + " power terminals, so there"
                    + " is no 'between' to measure and this scenario proves nothing");
            return;
        }

        // A second conductor laid alongside the first, pixel for pixel: exactly what a player does
        // to a run that is running hot, and the two are one circuit because they touch.
        List<WirePixel> alongside = new java.util.ArrayList<>();
        for (WirePixel point : run.pixels()) {
            if (point.v() + 1 < play.xponer.astronima.sim.wire.FaceBasis.GRID) {
                alongside.add(new WirePixel(point.x(), point.y(), point.z(), point.face(),
                        point.u(), point.v() + 1));
            }
        }
        Wires.placeAll(helper.getLevel(), alongside, DyeColor.WHITE, ConductorMaterial.IRON,
                play.xponer.astronima.sim.circuit.WireGauge.DEFAULT);

        double doubled = arriving(helper, from);
        if (!(doubled > single)) {
            helper.fail("a second wire laid beside the first delivered " + Math.round(doubled)
                    + " J where one delivered " + Math.round(single) + " - doubling up a"
                    + " conductor is the correct fix for an overheating run and the game is"
                    + " punishing it");
            return;
        }

        // And a spur to nowhere, touching the run at exactly one pixel. Current cannot enter it -
        // it would have to come back out the way it went in - so it must cost precisely nothing.
        WirePixel middle = run.pixels().get(run.pixels().size() / 2);
        List<WirePixel> spur = new java.util.ArrayList<>();
        for (int step = 1; step <= 4 && middle.v() - step >= 0; step++) {
            spur.add(new WirePixel(middle.x(), middle.y(), middle.z(), middle.face(),
                    middle.u(), middle.v() - step));
        }
        Wires.placeAll(helper.getLevel(), spur, DyeColor.WHITE, ConductorMaterial.IRON,
                play.xponer.astronima.sim.circuit.WireGauge.DEFAULT);

        // The loss has to land somewhere, and all of it: whatever weighting decides which room
        // gets warmed, it may not invent or lose joules on the way. A property rather than a
        // figure, because the figures are this code's own arithmetic (rule 19).
        var solved = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), from,
                DyeColor.WHITE);
        double placed = solved.heat().values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(placed - 1.0) > 1e-6) {
            helper.fail("the run places " + placed + " of its own loss into the world - heat that"
                    + " does not add up to what was made is heat appearing or vanishing in a"
                    + " corridor somebody has to live in");
            return;
        }

        double withSpur = arriving(helper, from);
        if (Math.abs(withSpur - doubled) > 1.0) {
            helper.fail("a dead-end spur changed what arrives from " + Math.round(doubled)
                    + " J to " + Math.round(withSpur) + " J - no current can enter a dead end, so"
                    + " the run is still being counted rather than solved");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I doubled the wire, the loss halved — and the panel still says it is fully loaded."</em>
     *
     * <p><strong>The other half of §5.1.</strong> Once a run's resistance correctly halves for a
     * doubled-up conductor, every trace on it was <em>still</em> being marked with the run's whole
     * current. Both wires of a parallel pair read as fully loaded, each was heated as if it were
     * alone — four times over, because I²R — and the instrument told the player their fix had not
     * worked. Worse, the pair warmed two corridors as hard as one wire warmed its own, which is
     * more heat than the circuit made.
     *
     * <p>Two runs, side by side in one test, driven with the <strong>same watts for the same
     * time</strong>: one wire, and two. Compared rather than measured against a figure, because a
     * figure would be this code's own arithmetic read back (rule 11) — and because "cooler than the
     * single wire" is exactly the sentence a player would use.
     */
    private static void aSharedRunRunsCooler(GameTestHelper helper) {
        WirePixel single = wiredPair(helper, 2, DyeColor.WHITE, false);
        WirePixel doubled = wiredPair(helper, 5, DyeColor.LIME, true);
        if (single == null || doubled == null) {
            return;
        }

        for (int second = 0; second < 90; second++) {
            var one = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), single,
                    DyeColor.WHITE);
            var two = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), doubled,
                    DyeColor.LIME);
            if (one.isEmpty() || two.isEmpty()) {
                helper.fail("a run burned through before the comparison could be made");
                return;
            }
            play.xponer.astronima.wire.WirePower.send(helper.getLevel(), one, 108, 1.0);
            play.xponer.astronima.wire.WirePower.send(helper.getLevel(), two, 108, 1.0);
            play.xponer.astronima.wire.WireTicker.age(helper.getLevel(), 1.0);
        }

        double alone = temperatureOf(helper, single, DyeColor.WHITE);
        double shared = temperatureOf(helper, doubled, DyeColor.LIME);
        if (alone <= 0 || shared <= 0) {
            helper.fail("a run vanished before it could be read: one=" + alone
                    + " two=" + shared);
            return;
        }
        if (!(shared < alone - 5)) {
            helper.fail("one wire came out at " + Math.round(alone) + " K and two wires carrying"
                    + " the same current between them came out at " + Math.round(shared) + " K -"
                    + " every trace is still being marked with the whole run's current, so the"
                    + " right fix for an overheating run reads as no fix at all");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"The battery is full and the crusher still crawls."</em>
     *
     * <p><strong>The last part of §5.1, which the design promised and the code never did.</strong>
     * A machine used to work out what the run would waste and ask the battery for <em>that much
     * extra</em> — so a hundred metres of signal wire to a crusher cost nothing but charge and the
     * crusher ran at full speed. The only thing a bad run ever did was flatten the battery sooner.
     *
     * <p>That is not what a wire does. The bus has a fixed voltage; the line and the machine divide
     * it between them; and the machine gets whatever share its own resistance won. Half the voltage
     * is a <strong>quarter</strong> of the work, which is what makes the gauge ladder a decision
     * instead of a price list.
     *
     * <p>Two claims, and the second is the one nobody expects. The far machine is
     * <strong>slower</strong>. And it is <strong>cheaper</strong>: an undervolted machine draws less
     * current, so a badly wired base does not drain its batteries faster — it just gets nothing
     * done.
     */
    private static void aLongThinRunStarvesAMachine(GameTestHelper helper) {
        double rated = play.xponer.astronima.sim.thermal.HeatBalance.WORKED_MACHINE_W;
        double volts = play.xponer.astronima.sim.circuit.Delivery.BUS_VOLTS;
        var short_ = play.xponer.astronima.sim.circuit.WireGauge.STANDARD
                .over(ConductorMaterial.IRON, 4.0)
                .resistance(ConductorMaterial.REFERENCE_K);
        var far = play.xponer.astronima.sim.circuit.WireGauge.SIGNAL
                .over(ConductorMaterial.IRON, 100.0)
                .resistance(ConductorMaterial.REFERENCE_K);

        double nearSpeed = play.xponer.astronima.sim.circuit.Sag.fraction(short_, rated, volts);
        double farSpeed = play.xponer.astronima.sim.circuit.Sag.fraction(far, rated, volts);
        if (!(nearSpeed > 0.97)) {
            helper.fail("four metres of standard conductor already costs a machine "
                    + Math.round((1 - nearSpeed) * 100) + "% of its speed - every base in the"
                    + " world would break on a run nobody would call long");
            return;
        }
        if (!(farSpeed < nearSpeed / 3)) {
            helper.fail("a hundred metres of signal wire runs a machine at "
                    + Math.round(farSpeed * 100) + "% where four metres of standard runs it at "
                    + Math.round(nearSpeed * 100) + "% - a full battery is still hiding the run,"
                    + " so the gauge ladder is a price list rather than a decision");
            return;
        }
        double nearDraw = play.xponer.astronima.sim.circuit.Sag.drawWatts(short_, rated, volts);
        double farDraw = play.xponer.astronima.sim.circuit.Sag.drawWatts(far, rated, volts);
        if (!(farDraw < nearDraw)) {
            helper.fail("the starved machine drew " + Math.round(farDraw) + " W where the near one"
                    + " drew " + Math.round(nearDraw) + " W - a badly wired base is being taught"
                    + " that its problem is battery capacity");
            return;
        }

        // And the same thing, through the machine a player actually builds: a crusher fed from a
        // full cell down a run of each kind. Same feed, same time, same charge available.
        double near = groundOut(helper, 2, DyeColor.WHITE,
                play.xponer.astronima.sim.circuit.WireGauge.STANDARD);
        double distant = groundOut(helper, 7, DyeColor.LIME,
                play.xponer.astronima.sim.circuit.WireGauge.SIGNAL);
        if (near < 0 || distant < 0) {
            return;
        }
        if (!(distant < near)) {
            helper.fail("a crusher on signal wire ground out " + Math.round(distant * 100)
                    + "% where one on standard ground out " + Math.round(near * 100) + "% - with"
                    + " the battery full, the run the player chose changes nothing at all");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I am tired of walking to my crusher with a wrench."</em>
     *
     * <p>The reward at the end of the electrical tier, and the reason the manual version shipped
     * first: a wire to the servo stud holds the machine's calibration inside a deadband, so it
     * never asks again. Asserted against an <strong>identical unservoed crusher on the same
     * power</strong>, because "it did not drift much" is a claim only a comparison can support —
     * a servo that did nothing and a machine that never drifted look the same from one number.
     *
     * <p>It also asserts the servo does not hold it <em>perfectly</em>. A servo that cancelled
     * drift outright would switch the whole mechanic off for everybody past this tier, and the
     * machines would go back to being boxes with a button (design/calibration.md §6.2).
     */
    private static void aServoHoldsACalibration(GameTestHelper helper) {
        var servoed = poweredCrusher(helper, 2, true);
        var alone = poweredCrusher(helper, 6, false);
        if (servoed == null || alone == null) {
            return;
        }

        // Long enough that an unattended machine is well past wanting a wrench. Longer than the
        // arithmetic suggests, because a machine on a real run does not get a full draw every
        // tick - which is the point of running the comparison rather than trusting a number.
        for (int tick = 0; tick < 1500; tick++) {
            drain(servoed);
            drain(alone);
            servoed.serverTick();
            alone.serverTick();
        }

        if (!alone.isWorthRecalibrating()) {
            helper.fail("the unservoed crusher only drifted " + alone.calibrationError()
                    + " over a long run on power, so this rig cannot tell a servo from a"
                    + " machine that was never going to drift anyway");
            return;
        }
        if (!servoed.isServoHolding()) {
            helper.fail("the servoed crusher does not think anything is holding it - the wire"
                    + " landed on the stud and the machine never noticed");
            return;
        }
        if (servoed.isWorthRecalibrating()) {
            helper.fail("a servoed crusher still wants a wrench (error "
                    + servoed.calibrationError() + ") - the wire, the run and the whole tier"
                    + " bought the player nothing");
            return;
        }
        if (!(servoed.calibrationError() > 0)) {
            helper.fail("the servo held the crusher exactly true, so past the electrical tier"
                    + " calibration stops existing and the machines are boxes with a button again");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I ran the power cable along the strip and the machine wired up its own enable."</em>
     *
     * <p>A machine's terminal strip is a row of studs, so a power run approaching along that row
     * <strong>crosses</strong> the enable stud on its way to the power stud. Under "any trace
     * occupying the pixel" that counted as wiring, and every machine in the game read its own
     * power cable as somebody having taken charge of its enable — a feature dead since the day it
     * shipped, invisible because both halves were doing exactly what they said.
     *
     * <p>Landing is <em>ending</em>, the way a screw terminal works. Both halves are asserted here,
     * because a rule that connects nothing would pass the first check on its own: a run driven
     * through the stud is not wired to it, and a stub that stops on the stud is.
     */
    private static void aRunPastAStudIsNotWiredToIt(GameTestHelper helper) {
        if (poweredCrusher(helper, 2, false) == null) {
            return;
        }
        BlockPos crusherPos = new BlockPos(4, 2, 1);
        WirePixel enable = terminalPad(helper, crusherPos,
                play.xponer.astronima.wire.Terminated.ENABLE, Direction.NORTH);
        if (enable == null) {
            return;
        }
        if (tracesOn(helper, enable).equals("nothing")) {
            helper.fail("the power run does not even cross the enable stud in this rig, so it"
                    + " cannot show that crossing one is not wiring it");
            return;
        }
        if (play.xponer.astronima.wire.WireSignal.hasSignalWiring(helper.getLevel(),
                helper.absolutePos(crusherPos),
                play.xponer.astronima.wire.Terminated.ENABLE)) {
            helper.fail("a power cable running past the enable stud counts as an enable line, so"
                    + " every powered machine believes somebody has taken charge of it and waits"
                    + " for a signal nobody is sending");
            return;
        }

        // And a wire that actually stops on the stud is wired to it.
        WirePart hand = mountOnFace(helper, PartType.SWITCH, new BlockPos(5, 2, 1),
                Direction.NORTH, 6, 12);
        if (hand == null || !layTo(helper, padPixel(hand, 0, true), enable, DyeColor.LIME)) {
            return;
        }
        if (!play.xponer.astronima.wire.WireSignal.hasSignalWiring(helper.getLevel(),
                helper.absolutePos(crusherPos),
                play.xponer.astronima.wire.Terminated.ENABLE)) {
            helper.fail("a run laid deliberately onto the enable stud is not wired to it either -"
                    + " the rule connects nothing at all and the strip is decoration");
            return;
        }
        helper.succeed();
    }

    /**
     * <em>"I ran a control wire past my crusher and it stopped getting power."</em>
     *
     * <p>The other half of the landing rule, and the more expensive half. A machine looks for its
     * power by asking each of its power studs whether a run is on it — and it takes the
     * <strong>first</strong> one it finds. Under "any trace occupying the pixel", a control line
     * crossing the power stud on the far side answered first, so the machine resolved its supply
     * onto a circuit with no batteries on it and quietly got nothing. The correctly wired cable on
     * the other face was never asked.
     *
     * <p>Asserted against an identical crusher with no stray wire near it, because "it made some
     * progress" is not the claim — the claim is that a wire crossing a stud changes nothing at all.
     */
    private static void aWireAcrossAPowerStudDoesNotCutIt(GameTestHelper helper) {
        var crossed = crusherOnAWall(helper, 5, true);
        var clear = crusherOnAWall(helper, 9, false);
        if (crossed == null || clear == null) {
            return;
        }

        for (int tick = 0; tick < 120; tick++) {
            drain(crossed);
            drain(clear);
            crossed.serverTick();
            clear.serverTick();
        }

        if (clear.work() <= 0) {
            helper.fail("the crusher with nothing near it did no work either, so this rig cannot"
                    + " show that a stray wire costs nothing");
            return;
        }
        if (crossed.work() != clear.work()) {
            helper.fail("a crusher with a control wire crossing one of its power studs did "
                    + crossed.work() + " work against " + clear.work() + " for an identical one"
                    + " with a clear strip - the stray run answered for the supply, so the machine"
                    + " resolved its power onto a circuit with no batteries on it");
            return;
        }
        helper.succeed();
    }

    /**
     * A crusher fed from a cell along a wall, wired on a face that is <em>not</em> asked first.
     *
     * <p>The geometry is the test. A machine asks its power studs in a fixed order and takes the
     * first answer, so a stray run only ever costs anything when it crosses a stud that comes
     * <em>before</em> the one carrying the supply. Wiring the supply along the wall's west face and
     * laying the stray across the south face puts them in that order; a rig that happened to put
     * them the other way round would pass this test with the bug present, which is what the first
     * version of it did.
     *
     * @param stray lay a run straight through the south power stud, going nowhere
     */
    private static @Nullable ProcessingBlockEntity crusherOnAWall(GameTestHelper helper, int x,
                                                                  boolean stray) {
        for (int z = 1; z <= 4; z++) {
            helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        }
        BlockPos cellPos = new BlockPos(x, 2, 1);
        BlockPos crusherPos = new BlockPos(x, 2, 4);
        helper.setBlock(cellPos, play.xponer.astronima.registry.ModBlocks.POWER_CELL.get());
        helper.setBlock(crusherPos, play.xponer.astronima.registry.ModBlocks.ORE_CRUSHER.get());

        WirePixel from = terminalPad(helper, cellPos,
                play.xponer.astronima.wire.Terminated.POWER, Direction.WEST);
        WirePixel to = terminalPad(helper, crusherPos,
                play.xponer.astronima.wire.Terminated.POWER, Direction.WEST);
        if (from == null || to == null || !layTo(helper, from, to, DyeColor.WHITE)) {
            return null;
        }
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(cellPos))
                instanceof play.xponer.astronima.block.entity.PowerCellBlockEntity battery)
                || !(helper.getLevel().getBlockEntity(helper.absolutePos(crusherPos))
                instanceof ProcessingBlockEntity crusher)) {
            helper.fail("the rig did not build - the layout is wrong");
            return null;
        }
        battery.charge(Double.MAX_VALUE / 4);
        crusher.setItem(ProcessingBlockEntity.SLOT_FEED,
                new net.minecraft.world.item.ItemStack(
                        play.xponer.astronima.registry.ModItems.METAL_RICH_ORE.get(), 64));

        if (stray) {
            WirePixel stud = terminalPad(helper, crusherPos,
                    play.xponer.astronima.wire.Terminated.POWER, Direction.SOUTH);
            if (stud == null) {
                return null;
            }
            List<WirePixel> across = new java.util.ArrayList<>();
            for (int u = stud.u() - 3; u <= stud.u() + 3; u++) {
                if (play.xponer.astronima.sim.wire.FaceBasis.onGrid(u)) {
                    across.add(new WirePixel(stud.x(), stud.y(), stud.z(), stud.face(), u,
                            stud.v()));
                }
            }
            Wires.placeAll(helper.getLevel(), across, DyeColor.LIME, ConductorMaterial.IRON,
                    play.xponer.astronima.sim.circuit.WireGauge.SIGNAL);
            if (!Wires.has(helper.getLevel(), stud, DyeColor.LIME)) {
                helper.fail("the stray run did not land across the south power stud at all, so"
                        + " this rig proves nothing about crossing one");
                return null;
            }
        }
        return crusher;
    }

    /**
     * <em>"I wired a servo and my switched-off machine started up."</em>
     *
     * <p>The interaction bug that adding a second signal input creates. The enable is read as
     * <em>"is any input of this block live"</em>, which is a sound question only while a machine
     * has one kind of input — and the moment it has two, a live servo answers a question about the
     * enable. A machine that ignores its own off switch is about as bad as this gets.
     */
    private static void aServoWireIsNotAnEnable(GameTestHelper helper) {
        var told = poweredCrusher(helper, 2, true);
        var free = poweredCrusher(helper, 8, true);
        if (told == null || free == null) {
            return;
        }
        // One of them has an enable line that is wired and dead: somebody has taken charge of that
        // machine and told it no. The other has a servo and nothing else, exactly as before.
        WirePart off = mountOnFace(helper, PartType.SWITCH, new BlockPos(5, 2, 1),
                Direction.NORTH, 6, 12);
        WirePixel stud = terminalPad(helper, new BlockPos(4, 2, 1),
                play.xponer.astronima.wire.Terminated.ENABLE, Direction.NORTH);
        if (off == null || stud == null
                || !layTo(helper, padPixel(off, 0, true), stud, DyeColor.LIME)) {
            return;
        }

        for (int tick = 0; tick < 200; tick++) {
            drain(told);
            drain(free);
            told.serverTick();
            free.serverTick();
        }

        if (told.isServoHolding()) {
            helper.fail("a crusher told to stop still has its servo running - the dead enable did"
                    + " not cut the power, so the live servo wire is answering a question about"
                    + " the enable [enable wired="
                    + play.xponer.astronima.wire.WireSignal.hasSignalWiring(helper.getLevel(),
                            helper.absolutePos(new BlockPos(4, 2, 1)),
                            play.xponer.astronima.wire.Terminated.ENABLE)
                    + " enable live="
                    + play.xponer.astronima.wire.WireSignal.anyInputLive(helper.getLevel(),
                            helper.absolutePos(new BlockPos(4, 2, 1)),
                            play.xponer.astronima.wire.Terminated.ENABLE)
                    + " any wired="
                    + play.xponer.astronima.wire.WireSignal.hasSignalWiring(helper.getLevel(),
                            helper.absolutePos(new BlockPos(4, 2, 1)))
                    + " on the stud: " + tracesOn(helper, stud) + "]");
            return;
        }
        if (!free.isServoHolding()) {
            helper.fail("the crusher nobody switched off is not holding either, so this rig cannot"
                    + " tell an enable from a servo and proves nothing");
            return;
        }
        if (!(told.progress() < free.progress())) {
            helper.fail("a crusher with a dead enable line got as far as one without ("
                    + told.progress() + " against " + free.progress() + ") - the servo wire"
                    + " answered for the enable, so the machine ignores its own off switch");
            return;
        }
        helper.succeed();
    }

    /**
     * A crusher on a charged cell, fed, optionally with a closed switch on its servo stud.
     *
     * @param x where along the row to build it
     */
    private static @Nullable ProcessingBlockEntity poweredCrusher(
            GameTestHelper helper, int x, boolean servo) {
        BlockPos cellPos = new BlockPos(x, 2, 1);
        BlockPos crusherPos = new BlockPos(x + 2, 2, 1);
        for (int step = 0; step <= 3; step++) {
            helper.setBlock(new BlockPos(x + step, 2, 1), Blocks.STONE);
        }
        helper.setBlock(cellPos, play.xponer.astronima.registry.ModBlocks.POWER_CELL.get());
        helper.setBlock(crusherPos,
                play.xponer.astronima.registry.ModBlocks.ORE_CRUSHER.get());

        WirePixel from = powerPad(helper, cellPos);
        WirePixel to = powerPad(helper, crusherPos);
        if (from == null || to == null) {
            helper.fail("a block published no power terminal - the layout is wrong");
            return null;
        }
        if (!layTo(helper, from, to, DyeColor.WHITE)) {
            return null;
        }
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(cellPos))
                instanceof play.xponer.astronima.block.entity.PowerCellBlockEntity battery)
                || !(helper.getLevel().getBlockEntity(helper.absolutePos(crusherPos))
                instanceof ProcessingBlockEntity crusher)) {
            helper.fail("the rig did not build - the layout is wrong");
            return null;
        }
        battery.charge(Double.MAX_VALUE / 4);
        crusher.setItem(ProcessingBlockEntity.SLOT_FEED,
                new net.minecraft.world.item.ItemStack(
                        play.xponer.astronima.registry.ModItems.METAL_RICH_ORE.get(), 64));

        if (servo) {
            // On the plate between the two, so the run to the stud is a metre of wire on one
            // continuous face rather than a climb the router cannot support.
            WirePart hand = mountOnFace(helper, PartType.SWITCH,
                    new BlockPos(x + 1, 2, 1), Direction.SOUTH, 6, 6);
            WirePixel stud = terminalPad(helper, crusherPos,
                    play.xponer.astronima.wire.Terminated.SERVO, Direction.SOUTH);
            if (hand == null || stud == null
                    || !layTo(helper, padPixel(hand, 0, true), stud, DyeColor.MAGENTA)) {
                return null;
            }
            close(helper, hand);
        }
        return crusher;
    }

    /**
     * Takes the product away, the way a hopper on a real base does.
     *
     * <p>Necessary rather than tidy. A crusher stamps its output with a size band, so the first
     * band boundary the drift crosses gives a product that will not stack with what is already in
     * the slot — and an unattended machine with nowhere to put it stops after a fraction of one
     * service interval. That is correct behaviour and it is exactly what a hopper is for, but a
     * scenario about drift that quietly stopped measuring after four batches would prove nothing.
     */
    private static void drain(ProcessingBlockEntity machine) {
        for (int slot = 1; slot < machine.getContainerSize(); slot++) {
            machine.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    /** Every trace occupying one pixel, by colour - for working out what a stud is really on. */
    private static String tracesOn(GameTestHelper helper, WirePixel at) {
        StringBuilder said = new StringBuilder();
        for (var trace : Wires.bundleOn(helper.getLevel(), Wires.cellOf(at),
                play.xponer.astronima.wire.Faces.of(at.face()))) {
            if (trace.has(at.u(), at.v())) {
                said.append(trace.colour()).append(' ');
            }
        }
        return said.isEmpty() ? "nothing" : said.toString();
    }

    /** Bolts a part to one face of a block, where a machine's terminal strip also lives. */
    private static @Nullable WirePart mountOnFace(GameTestHelper helper, PartType type,
                                                  BlockPos support, Direction outward,
                                                  int u, int v) {
        helper.setBlock(support, Blocks.STONE);
        BlockPos cell = helper.absolutePos(support).relative(outward);
        WirePart part = WirePart.placed(cell, outward.getOpposite(), u, v, 0, type,
                Circuit.empty(), "");
        if (!Wires.canMount(helper.getLevel(), part)) {
            helper.fail("could not mount a " + type + " on the " + outward + " face at " + support
                    + " - the test's own layout is wrong");
            return null;
        }
        Wires.mount(helper.getLevel(), part);
        return part;
    }

    /**
     * The exact pixel a machine publishes as the named stud on one of its faces.
     *
     * <p>Which face matters, and that is not a detail of the rig. A machine's strip is repeated on
     * all four sides, and two studs on the <em>same</em> face can be crossed by one careless run:
     * a wire routed to the servo stud that happens to pass over the enable stud is landed on both,
     * because a terminal is a pixel and a trace either occupies it or does not. That is honest —
     * the trace is drawn and the player can see it — but it means these two lines want opposite
     * sides of the machine, exactly as they would on a real one.
     */
    private static @Nullable WirePixel terminalPad(GameTestHelper helper, BlockPos at,
                                                   String label, Direction outward) {
        BlockPos block = helper.absolutePos(at);
        var state = helper.getLevel().getBlockState(block);
        if (!(state.getBlock() instanceof play.xponer.astronima.wire.Terminated terminated)) {
            helper.fail("the block at " + at + " publishes no terminals at all");
            return null;
        }
        for (var terminal : terminated.terminals(state)) {
            if (label.equals(terminal.label()) && terminal.outward() == outward) {
                return terminal.pixel(block);
            }
        }
        helper.fail("no terminal labelled " + label + " on the " + outward + " face of " + at
                + " - the strip has changed and this scenario is aiming at nothing");
        return null;
    }

    /**
     * A crusher wired to a charged cell down a run of one gauge, and how far it got.
     *
     * @return progress after a fixed run of ticks, or -1 when the layout failed
     */
    private static double groundOut(GameTestHelper helper, int y, DyeColor colour,
                                    play.xponer.astronima.sim.circuit.WireGauge gauge) {
        // Five metres between them, because the machine's rate has four steps and a sag under an
        // eighth is invisible in play. That quantising is a design decision, not an accident - a
        // run has to be genuinely bad before it costs anything - and it means a scenario about
        // undervolting needs a genuinely bad run rather than a token one.
        for (int x = 2; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(1, y, 1),
                play.xponer.astronima.registry.ModBlocks.POWER_CELL.get());
        helper.setBlock(new BlockPos(6, y, 1),
                play.xponer.astronima.registry.ModBlocks.ORE_CRUSHER.get());

        WirePixel from = powerPad(helper, new BlockPos(1, y, 1));
        WirePixel to = powerPad(helper, new BlockPos(6, y, 1));
        if (from == null || to == null) {
            helper.fail("a block published no power terminal - the layout is wrong");
            return -1;
        }
        if (!layTo(helper, from, to, colour, gauge)) {
            return -1;
        }
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, y, 1)))
                instanceof play.xponer.astronima.block.entity.PowerCellBlockEntity cell)
                || !(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(6, y, 1)))
                instanceof play.xponer.astronima.block.entity.ProcessingBlockEntity crusher)) {
            helper.fail("the rig did not build - the layout is wrong");
            return -1;
        }
        cell.charge(Double.MAX_VALUE / 4);
        crusher.setItem(play.xponer.astronima.block.entity.ProcessingBlockEntity.SLOT_FEED,
                new net.minecraft.world.item.ItemStack(
                        play.xponer.astronima.registry.ModItems.METAL_RICH_ORE.get(), 64));
        for (int tick = 0; tick < 40; tick++) {
            crusher.serverTick();
        }
        return crusher.progress();
    }

    /**
     * Two power cells with wall between them and a run joining their terminals.
     *
     * @param twin lay a second conductor alongside the first, pixel for pixel
     * @return a pixel of the run, or null when the layout failed
     */
    private static @Nullable WirePixel wiredPair(GameTestHelper helper, int y, DyeColor colour,
                                                 boolean twin) {
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(1, y, 1),
                play.xponer.astronima.registry.ModBlocks.POWER_CELL.get());
        helper.setBlock(new BlockPos(5, y, 1),
                play.xponer.astronima.registry.ModBlocks.POWER_CELL.get());

        WirePixel from = powerPad(helper, new BlockPos(1, y, 1));
        WirePixel to = powerPad(helper, new BlockPos(5, y, 1));
        if (from == null || to == null) {
            helper.fail("a power cell published no power terminal - the layout is wrong");
            return null;
        }
        var gauge = play.xponer.astronima.sim.circuit.WireGauge.SIGNAL;
        if (!layTo(helper, from, to, colour, gauge)) {
            return null;
        }
        if (twin) {
            var run = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), from, colour);
            List<WirePixel> alongside = new java.util.ArrayList<>();
            for (WirePixel point : run.pixels()) {
                if (point.v() + 1 < play.xponer.astronima.sim.wire.FaceBasis.GRID) {
                    alongside.add(new WirePixel(point.x(), point.y(), point.z(), point.face(),
                            point.u(), point.v() + 1));
                }
            }
            Wires.placeAll(helper.getLevel(), alongside, colour, ConductorMaterial.IRON, gauge);
        }
        return from;
    }

    /** The exact pixel a block publishes as its power terminal — where a player would aim. */
    private static @Nullable WirePixel powerPad(GameTestHelper helper, BlockPos at) {
        BlockPos block = helper.absolutePos(at);
        var state = helper.getLevel().getBlockState(block);
        if (!(state.getBlock() instanceof play.xponer.astronima.wire.Terminated terminated)) {
            return null;
        }
        for (var terminal : terminated.terminals(state)) {
            if (terminal.kind() == play.xponer.astronima.wire.Terminal.Kind.POWER
                    && terminal.outward() == Direction.NORTH) {
                return terminal.pixel(block);
            }
        }
        return null;
    }

    /** A kilojoule pushed in at one end, and what comes out at the other. */
    private static double arriving(GameTestHelper helper, WirePixel at) {
        var run = play.xponer.astronima.wire.WirePower.resolve(helper.getLevel(), at,
                DyeColor.WHITE);
        double delivered = play.xponer.astronima.wire.WirePower.send(helper.getLevel(), run,
                1000, 1.0);
        play.xponer.astronima.wire.WireLoad.forget(helper.getLevel());
        return delivered;
    }

    /** Lays one metre of a gauge on the floor and hands back a pixel of it. */
    private static WirePixel laid(GameTestHelper helper, DyeColor colour,
                                  play.xponer.astronima.sim.circuit.WireGauge gauge, int v) {
        BlockPos cell = helper.absolutePos(new BlockPos(1, 2, 1));
        List<WirePixel> run = new java.util.ArrayList<>();
        for (int u = 0; u < 16; u++) {
            run.add(new WirePixel(cell.getX(), cell.getY(), cell.getZ(), Faces.of(FACE), u, v));
        }
        Wires.placeAll(helper.getLevel(), run, colour, ConductorMaterial.IRON, gauge);
        return run.get(0);
    }

    private static double temperatureOf(GameTestHelper helper, WirePixel at, DyeColor colour) {
        return Wires.trace(helper.getLevel(), at, colour)
                .map(play.xponer.astronima.wire.WireTrace::temperatureK).orElse(0.0);
    }

    /** A look at the top face of a block, landing on a chosen pixel of it. */
    private static net.minecraft.world.phys.BlockHitResult hitOnTop(BlockPos support, int u,
                                                                    int v) {
        // The top face's own axes are east (u) and south (v), so the hit position follows them.
        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(
                support.getX() + (u + 0.5) / 16.0,
                support.getY() + 1.0,
                support.getZ() + (v + 0.5) / 16.0);
        return new net.minecraft.world.phys.BlockHitResult(at, Direction.UP, support, false);
    }

    // ---- plumbing --------------------------------------------------------------

    /** A floor to fasten parts and wire to, from x to x+length. */
    private static void floor(GameTestHelper helper, int from, int length) {
        for (int x = from; x <= from + length; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
        }
    }

    private static @Nullable WirePart mount(GameTestHelper helper, PartType type, int x,
                                            int u, int v) {
        return mountWith(helper, type, x, u, v, Circuit.empty());
    }

    /** Bolts a part to the top of the floor at x, and hands back what actually got stored. */
    private static @Nullable WirePart mountWith(GameTestHelper helper, PartType type, int x,
                                                int u, int v, Circuit circuit) {
        BlockPos cell = helper.absolutePos(new BlockPos(x, 2, 1));
        WirePart part = WirePart.placed(cell, FACE, u, v, 0, type, circuit, "");
        if (!Wires.canMount(helper.getLevel(), part)) {
            helper.fail("could not mount a " + type + " at " + u + "," + v
                    + " - the test's own layout is wrong");
            return null;
        }
        Wires.mount(helper.getLevel(), part);
        return part;
    }

    /** Closes a switch, the way a click on it would. */
    private static void close(GameTestHelper helper, WirePart hand) {
        WirePart closed = hand.withHeld(true, 0L);
        Wires.mount(helper.getLevel(), play.xponer.astronima.wire.PartInteraction.sourceUpdate(closed));
    }

    /** And opens it again. */
    private static void open(GameTestHelper helper, WirePart hand) {
        WirePart opened = hand.withHeld(false, 0L);
        Wires.mount(helper.getLevel(), play.xponer.astronima.wire.PartInteraction.sourceUpdate(opened));
    }

    /** The pixel one of a part's pads occupies. */
    private static WirePixel padPixel(WirePart part, int index, boolean drives) {
        var pads = drives ? part.type().outputs() : part.type().inputs();
        return part.padPixel(pads.get(index));
    }

    private static boolean wire(GameTestHelper helper, WirePart from, int fromPad,
                                WirePart to, int toPad, DyeColor colour) {
        return layTo(helper, padPixel(from, fromPad, true), padPixel(to, toPad, false), colour);
    }

    /**
     * Lays a run between two pixels with the real router.
     *
     * <p>Not through the coil item: that needs a click with a hit position inside a block face
     * and the harness has no camera ({@code reference-gametest-harness-limits}). The router in
     * front of this call is the same object the item uses, and it has its own MC-free tests —
     * what these scenarios are for is the <em>circuit</em>.
     */
    private static boolean layTo(GameTestHelper helper, WirePixel from, WirePixel to,
                                 DyeColor colour) {
        return layTo(helper, from, to, colour, play.xponer.astronima.sim.circuit.WireGauge.DEFAULT);
    }

    private static boolean layTo(GameTestHelper helper, WirePixel from, WirePixel to,
                                 DyeColor colour,
                                 play.xponer.astronima.sim.circuit.WireGauge gauge) {
        // The pixel-level space, which is the one that knows a part's housing is in the way.
        WireRouter.Space space = pixel -> Wires.canPlace(helper.getLevel(), pixel)
                || pixel.equals(from) || pixel.equals(to);
        List<WirePixel> route = WireRouter.route(from, to, space, WireRouter.Mode.PATHFIND)
                .orElse(null);
        if (route == null) {
            helper.fail("no route between " + from + " and " + to
                    + " - the test's own layout is wrong");
            return false;
        }
        Wires.placeAll(helper.getLevel(), route, colour, ConductorMaterial.IRON, gauge);
        return true;
    }

    /**
     * What a part is putting out right now, on its first driving pad.
     *
     * <p>Read through {@link PartLogic}, which is what the wire itself asks — not from the stored
     * bits, which are a quarter of a second behind and exist for the renderer.
     */
    private static boolean driving(GameTestHelper helper, WirePart part) {
        WirePart current = Wires.partAt(helper.getLevel(), part.cell(), part.face(),
                part.u(), part.v()).orElse(part);
        return (PartLogic.evaluate(helper.getLevel(), current) & 1) != 0;
    }

    /** Not used by the scenarios; kept so the drop's item form is named where it is asserted. */
    @SuppressWarnings("unused")
    private static ItemStack dropped(PartType type) {
        return new ItemStack(ModItems.part(type));
    }

    /**
     * Phase C's own contract: a circuit written to a clipboard string and read back is the same
     * circuit, node for node and wire for wire. {@link play.xponer.astronima.item.CircuitClipboard}
     * needs {@code JsonOps}, which — like {@code CircuitPlate.CODEC} through {@code NbtOps} a few
     * methods up — is not safe to exercise from a plain, Minecraft-free unit test, so this is a
     * gametest rather than a {@code sim/}-style one.
     */
    private static void aClipboardPasteRoundTripsARealCircuit(GameTestHelper helper) {
        Circuit original = Circuit.empty()
                .withNode(new Circuit.Node(play.xponer.astronima.sim.logic.Gate.AND,
                        Circuit.Source.fromInput(0), Circuit.Source.fromInput(1)))
                .withNode(new Circuit.Node(play.xponer.astronima.sim.logic.Gate.NOT,
                        Circuit.Source.fromNode(0), Circuit.Source.off()))
                .withOutput(0, Circuit.Source.fromNode(1));

        String pasted = play.xponer.astronima.item.CircuitClipboard.encode(original);
        Circuit reloaded = play.xponer.astronima.item.CircuitClipboard.decode(pasted).orElse(null);

        if (reloaded == null) {
            helper.fail("a circuit this method itself just encoded failed to decode");
            return;
        }
        if (!reloaded.equals(original)) {
            helper.fail("the pasted circuit is not the one that was copied: " + reloaded);
            return;
        }
        helper.succeed();
    }

    /**
     * The other half of Phase C's own contract: a garbled paste must be refused, not silently
     * read back as a blank plate — a player who fumbled a copy deserves to be told, not handed
     * an empty board that looks like their own work vanished.
     */
    private static void aCorruptedClipboardPasteIsRefused(GameTestHelper helper) {
        Circuit original = Circuit.empty()
                .withNode(new Circuit.Node(play.xponer.astronima.sim.logic.Gate.OR,
                        Circuit.Source.fromInput(0), Circuit.Source.fromInput(1)))
                .withOutput(0, Circuit.Source.fromNode(0));
        String pasted = play.xponer.astronima.item.CircuitClipboard.encode(original);
        String corrupted = pasted.substring(0, pasted.length() - 4) + "xxxx";

        if (play.xponer.astronima.item.CircuitClipboard.decode(corrupted).isPresent()) {
            helper.fail("a corrupted paste decoded anyway instead of being refused");
            return;
        }
        if (play.xponer.astronima.item.CircuitClipboard.decode("not a circuit at all").isPresent()) {
            helper.fail("plain text that was never a paste decoded as a circuit anyway");
            return;
        }
        helper.succeed();
    }

    /**
     * Forces this class to load, so its registrations happen.
     *
     * <p>A {@code DeferredRegister} field only registers when its class is initialised, and
     * nothing else in the mod mentions this one. PLAN rule 20's table records exactly this
     * failure shape: a gametest that was written, was correct, and never ran — with every gate
     * green the whole time.
     */
    public static void init() {
        // Deliberately empty. Calling it is the point.
    }

    private WireScenarios() {}
}
