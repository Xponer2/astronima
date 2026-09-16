package play.xponer.astronima.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.sim.lab.Culture;
import play.xponer.astronima.sim.lab.Organism;
import play.xponer.astronima.sim.lab.Isolates;
import play.xponer.astronima.sim.lab.IdentificationKey;
import play.xponer.astronima.sim.lab.GramStain;
import play.xponer.astronima.sim.lab.DiscDiffusion;
import play.xponer.astronima.sim.lab.Antibiotic;
import play.xponer.astronima.sim.pathogen.Treatment;
import play.xponer.astronima.sim.pathogen.Strain;
import play.xponer.astronima.sim.pathogen.Pathogens;
import play.xponer.astronima.sim.pathogen.Immunity;
import play.xponer.astronima.sim.pathogen.Infection;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.atmosphere.Ignition;
import net.minecraft.world.item.ItemStack;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import play.xponer.astronima.item.EvaSuitItem;
import play.xponer.astronima.block.entity.OreCrusherBlockEntity;
import play.xponer.astronima.item.OxygenTanks;
import play.xponer.astronima.sim.metal.CentrifugalBed;
import play.xponer.astronima.sim.metal.ColdWorking;
import play.xponer.astronima.sim.metal.IlmeniteReduction;
import play.xponer.astronima.sim.metal.LaserSintering;
import play.xponer.astronima.block.entity.GasTankBlockEntity;
import play.xponer.astronima.sim.pipe.PressureVessel;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.pipe.PipeNetworks;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.pipe.Conduit;
import play.xponer.astronima.sim.physio.Consciousness;
import play.xponer.astronima.sim.tool.ToolHead;
import play.xponer.astronima.sim.chem.Element;
import play.xponer.astronima.sim.chem.Formula;
import play.xponer.astronima.sim.circuit.Ampacity;
import play.xponer.astronima.sim.circuit.Conductor;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WireTrace;
import play.xponer.astronima.wire.Wires;
import play.xponer.astronima.sim.wire.Face;
import play.xponer.astronima.sim.wire.FaceBasis;
import play.xponer.astronima.sim.wire.PixelGeometry;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.sim.wire.WireRouter;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import play.xponer.astronima.item.WearingToolItem;
import play.xponer.astronima.sim.ore.Comminution;
import play.xponer.astronima.sim.ore.MagneticSeparation;
import play.xponer.astronima.sim.ore.Mineral;
import play.xponer.astronima.sim.ore.OreBody;
import play.xponer.astronima.sim.ore.OreGrade;
import play.xponer.astronima.item.SuitLifeSupport;
import play.xponer.astronima.sim.suit.SuitWear;
import play.xponer.astronima.sim.suit.ThermalProtection;
import play.xponer.astronima.item.SuitLoadout;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.suit.HelmetAtmosphere;
import play.xponer.astronima.sim.suit.SuitCondition;
import play.xponer.astronima.sim.suit.SuitSubsystem;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.GasMixture;
import play.xponer.astronima.sim.Humidity;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.burn.Flammability;
import play.xponer.astronima.sim.physio.Ailment;
import play.xponer.astronima.sim.physio.VitalSigns;

import java.util.Map;
import play.xponer.astronima.sim.tox.GasToxicity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import play.xponer.astronima.sim.magic.ObservationTarget;
import play.xponer.astronima.sim.magic.SpectralLine;
import play.xponer.astronima.sim.magic.Spectrum;
import play.xponer.astronima.sim.magic.SupernovaSpectrum;
import play.xponer.astronima.sim.optics.BlackBody;
import play.xponer.astronima.registry.ModParticles;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import play.xponer.astronima.sim.optics.MoonPhaseLighting;
import play.xponer.astronima.sim.optics.SkyRotation;
import play.xponer.astronima.sim.optics.TelescopeMount;
import play.xponer.astronima.sim.astra.AstraFieldGeometry;
import play.xponer.astronima.sim.world.AsteroidBody;
import play.xponer.astronima.sim.sky.CometSchedule;
import play.xponer.astronima.sim.sky.FlareSchedule;
import play.xponer.astronima.sim.sky.OccultationSchedule;
import play.xponer.astronima.sim.sky.PassingBodySchedule;
import play.xponer.astronima.sim.sky.SkyEventOverride;
import play.xponer.astronima.sim.sky.SupernovaSchedule;

/**
 * Test harness for everything the mod simulates.
 *
 * <p>Atmospheric hazards are hard to reach on demand — an explosive methane mixture,
 * a dangerous CO dose, or a saturated room can take an hour of mining to find and
 * often can't be reproduced at all. These commands set any of it directly and read
 * it back, so a mechanic can be confirmed in seconds rather than inferred.
 *
 * <p>Operator only (permission level 2); they are cheats by design.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class AstronimaCommand {
    private static final SimpleCommandExceptionType UNKNOWN_GRADE = new SimpleCommandExceptionType(
            Component.literal("Unknown grade. Try: chondrite, metal_rich"));
    private static final SimpleCommandExceptionType UNKNOWN_TARGET = new SimpleCommandExceptionType(
            Component.literal("Unknown target. Try: sun, rtg_glow, ice_lens"));
    private static final SimpleCommandExceptionType UNKNOWN_LINE = new SimpleCommandExceptionType(
            Component.literal("Unknown line. Try: h_alpha, sodium_d1, helium_i, forbidden_oiii, ..."));
    private static final SimpleCommandExceptionType NO_SUIT = new SimpleCommandExceptionType(
            Component.literal("No suit worn - put one in the suit slot first."));
    private static final SimpleCommandExceptionType UNKNOWN_SUBSYSTEM = new SimpleCommandExceptionType(
            Component.literal("Unknown subsystem. Try: helmet_seal, tank_mount, regulator, "
                    + "scrubber_bay, thermal_layer, status_display"));
    private static final SimpleCommandExceptionType NO_TANK = new SimpleCommandExceptionType(
            Component.literal("No tank fitted — put one in the suit's tank slot first."));
    private static final SimpleCommandExceptionType NO_ROOM = new SimpleCommandExceptionType(
            Component.literal("No enclosed volume here — stand inside a room or cavity."));
    private static final SimpleCommandExceptionType UNKNOWN_GAS = new SimpleCommandExceptionType(
            Component.literal("Unknown gas. Try: " + String.join(", ", gasNames())));

    @SubscribeEvent
    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astronima")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(roomCommands())
                .then(gasCommands())
                .then(physiologyCommands())
                .then(toolCommand())
                .then(pipeCommand())
                .then(wireCommand())
                .then(Commands.literal("temp")
                        .then(Commands.argument("celsius", DoubleArgumentType.doubleArg(-270, 3000))
                                .executes(AstronimaCommand::setTemperature)))
                .then(Commands.literal("humidity")
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 300))
                                .executes(AstronimaCommand::setHumidity)))
                .then(moldCommands())
                .then(Commands.literal("ignite").executes(AstronimaCommand::forceIgnite))
                .then(Commands.literal("noise").executes(AstronimaCommand::reportNoise))
                .then(suitCommands())
                .then(oreCommands())
                .then(reductionCommands())
                .then(sinterCommands())
                .then(forgeCommands())
                .then(airlockCommand())
                .then(infectionCommands())
                .then(contaminationCommands())
                .then(chronicCommands())
                .then(calibrationCommands())
                .then(labCommands())
                .then(magicCommands())
                .then(particleCommands())
                .then(skyCommands())
                .then(unlockCommands())
                .then(researchCommands())
                .then(telescopeCommands())
                .then(phasechangeCommands())
                .then(heavyWaterCommands())
                .then(titaniumCommands())
                .then(inductionCommands())
                .then(calcinationCommands())
                .then(vrpodCommands())
                .then(cryoCommands())
                .then(radiationCommands())
                .then(storageCommands())
                .then(terminalCommands())
                .then(Commands.literal("kit").executes(AstronimaCommand::giveKit)));
    }

    /**
     * {@code /astronima storage <pos>} — the live connected-frame count and usable slot count
     * for a drive, per the standing debug-command rule (design/data-cells.md §9): a locked slot
     * in a vanilla {@code ChestMenu} has no tooltip explaining why, so this is the honest way to
     * read what the structure actually unlocked.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> storageCommands() {
        return Commands.literal("storage")
                .then(Commands.argument("pos",
                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .executes(AstronimaCommand::reportStorage));
    }

    private static int reportStorage(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument
                .getLoadedBlockPos(context, "pos");
        if (!(context.getSource().getLevel().getBlockEntity(pos)
                instanceof play.xponer.astronima.block.entity.StorageDriveBlockEntity drive)) {
            return feedback(context, "no storage drive at " + pos.toShortString());
        }
        int usable = drive.usableSlots();
        int connectedFrames = usable - play.xponer.astronima.block.entity.StorageDriveBlockEntity.FREE_SLOTS;
        return feedback(context, String.format(java.util.Locale.ROOT,
                "storage drive at %s | %d connected frame(s) | %d/%d slots usable",
                pos.toShortString(), connectedFrames, usable,
                play.xponer.astronima.block.entity.StorageDriveBlockEntity.MAX_CELL_SLOTS));
    }

    /**
     * {@code /astronima terminal <pos>} — every real, live aggregated entry a storage terminal
     * currently sees, straight from {@code TerminalView} against the connected drives' own
     * cells, bypassing the screen entirely. The honest ground truth for "is a displayed total
     * actually wrong, or does the screen just not agree with it" — a question a screenshot alone
     * cannot answer, per the standing debug-command rule.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> terminalCommands() {
        return Commands.literal("terminal")
                .then(Commands.argument("pos",
                                net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .executes(AstronimaCommand::reportTerminal));
    }

    private static int reportTerminal(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument
                .getLoadedBlockPos(context, "pos");
        if (!(context.getSource().getLevel().getBlockEntity(pos)
                instanceof play.xponer.astronima.block.entity.StorageTerminalBlockEntity terminal)) {
            return feedback(context, "no storage terminal at " + pos.toShortString());
        }
        var view = play.xponer.astronima.sim.storage.TerminalView.of(terminal.connectedLedgers())
                .sortedBy(terminal.sort());
        var entries = view.entries();
        CommandSourceStack source = context.getSource();
        line(source, String.format(java.util.Locale.ROOT,
                "storage terminal at %s | %d distinct item(s) | scroll row %d/%d | sort %s",
                pos.toShortString(), entries.size(), terminal.scrollRow(),
                Math.max(0, terminal.totalRows()
                        - play.xponer.astronima.block.entity.StorageTerminalBlockEntity.VISIBLE_ROWS),
                terminal.sort()), ChatFormatting.YELLOW);
        for (var entry : entries) {
            line(source, "  " + entry.itemId() + " x" + entry.totalCount(), ChatFormatting.AQUA);
        }
        // The aggregate above is what TerminalView computes fresh, right now. This is what
        // refreshView() actually wrote into the real display slots the last time it ran (every
        // tick) - the one thing a real client's own Slot/ItemSlot actually reads and syncs. If
        // these two ever disagree, the bug is in refreshView() itself, not in network sync; if
        // they agree but a real client still shows something else, the bug is downstream of here.
        line(source, "  --- what the real slots hold right now ---", ChatFormatting.GRAY);
        for (int row = 0; row < play.xponer.astronima.block.entity.StorageTerminalBlockEntity.MAX_VISIBLE_ROWS; row++) {
            ItemStack shown = terminal.getItem(
                    play.xponer.astronima.block.entity.StorageTerminalBlockEntity.FIRST_DISPLAY_SLOT
                            + row);
            if (!shown.isEmpty()) {
                line(source, "  slot " + row + ": "
                        + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(shown.getItem())
                        + " x" + shown.getCount(), ChatFormatting.GOLD);
            }
        }
        return 1;
    }

    /**
     * Reading breunnerite's own real calcination, per the debug-command rule — the honest first
     * consumer (rule 13) of {@link play.xponer.astronima.sim.ore.Calcination}, since
     * {@code Mineral.CARBONATE} has had a stated purpose and no path to it at all until now (see
     * design/carbonate-calcination.md).
     */
    private static LiteralArgumentBuilder<CommandSourceStack> calcinationCommands() {
        return Commands.literal("calcination")
                .executes(AstronimaCommand::reportCalcination);
    }

    private static int reportCalcination(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double gramsFed = 1000.0;
        double co2 = play.xponer.astronima.sim.ore.Calcination.co2GramsFrom(gramsFed);
        double oxide = play.xponer.astronima.sim.ore.Calcination.oxideGramsFrom(gramsFed);
        line(source, "Calcination - MgCO3 -> MgO + CO2 (breunnerite's own real decomposition)",
                ChatFormatting.AQUA);
        line(source, String.format(java.util.Locale.ROOT,
                        "  %.0f g carbonate -> %.1f g MgO + %.1f g CO2 (mass balances: %.1f g)",
                        gramsFed, oxide, co2, oxide + co2),
                ChatFormatting.GREEN);
        line(source, String.format(java.util.Locale.ROOT,
                        "  retort window: onset %.0f K, complete %.0f K, decrepitates %.0f K"
                                + " (mirror's own hard ceiling is 1287 K)",
                        play.xponer.astronima.sim.ore.Calcination.ONSET_K,
                        play.xponer.astronima.sim.ore.Calcination.COMPLETE_K,
                        play.xponer.astronima.sim.ore.Calcination.DECREPITATION_K),
                ChatFormatting.GRAY);
        double carbonateInCharge = play.xponer.astronima.block.entity.SolarRetortBlockEntity.CHARGE_GRAMS
                * play.xponer.astronima.block.entity.SolarRetortBlockEntity.BAKED_SILICATE_CARBONATE;
        play.xponer.astronima.sim.ore.Calcination.Bake clean =
                play.xponer.astronima.sim.ore.Calcination.bake(carbonateInCharge,
                        play.xponer.astronima.sim.ore.Calcination.COMPLETE_K);
        play.xponer.astronima.sim.ore.Calcination.Bake decrepitated =
                play.xponer.astronima.sim.ore.Calcination.bake(carbonateInCharge,
                        play.xponer.astronima.sim.ore.Calcination.DECREPITATION_K);
        line(source, String.format(java.util.Locale.ROOT,
                        "  one baked-silicate charge (%.0f g, %.1f%% assumed carbonate): clean"
                                + " bake %.1f g MgO, decrepitated %.1f g MgO",
                        play.xponer.astronima.block.entity.SolarRetortBlockEntity.CHARGE_GRAMS,
                        100 * play.xponer.astronima.block.entity.SolarRetortBlockEntity
                                .BAKED_SILICATE_CARBONATE,
                        clean.mgOGrams(), decrepitated.mgOGrams()),
                ChatFormatting.GREEN);
        return 1;
    }

    /**
     * The debug-command rule's own answer to design/vr-simulation-pod.md §6: this project's
     * gametest harness cannot give a real player a real cross-dimension teleport, so this command
     * is the only way to exercise {@link play.xponer.astronima.block.VrSimulationPodBlock#enter}
     * and {@code #exit} end to end without a real client.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> vrpodCommands() {
        return Commands.literal("vrpod")
                .then(Commands.literal("enter").executes(AstronimaCommand::enterVrPod))
                .then(Commands.literal("exit").executes(AstronimaCommand::exitVrPod));
    }

    private static int enterVrPod(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean sent = play.xponer.astronima.block.VrSimulationPodBlock.enter(player);
        return feedback(context, sent
                ? "Sent to the VR Simulation Pod's void - flying, free placement, nothing here"
                        + " can kill you."
                : "Refused - the pod's own dimension failed to load.");
    }

    private static int exitVrPod(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean sent = play.xponer.astronima.block.VrSimulationPodBlock.exit(player);
        return feedback(context, sent
                ? "Returned from the VR Simulation Pod, exactly where you left."
                : "Refused - you are not inside the pod right now.");
    }

    /**
     * Reading the furnace's own real thermodynamics, per the debug-command rule — the honest
     * first consumer (rule 13) of {@link play.xponer.astronima.sim.metal.InductionMelting}.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> inductionCommands() {
        return Commands.literal("induction")
                .executes(AstronimaCommand::reportInduction);
    }

    private static int reportInduction(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double joulesPerKg = play.xponer.astronima.sim.metal.InductionMelting.ironEnergyJoules(1.0);
        line(source, "Induction furnace - melting iron, no room air touched", ChatFormatting.AQUA);
        line(source, String.format(java.util.Locale.ROOT,
                        "  sensible heat: %.0f K to %.0f K, plus real latent heat of fusion",
                        play.xponer.astronima.sim.metal.InductionMelting.AMBIENT_ROOM_K,
                        play.xponer.astronima.sim.metal.InductionMelting.IRON_MELTING_POINT_K),
                ChatFormatting.GRAY);
        line(source, String.format(java.util.Locale.ROOT,
                        "  %.0f J/kg = %.3f kWh/kg (titanium's own real reduction is ~30 kWh/kg)",
                        joulesPerKg, joulesPerKg / 3_600_000.0),
                ChatFormatting.GREEN);
        return 1;
    }

    /**
     * Reading the FFC-Cambridge reduction, per the debug-command rule — the honest first
     * consumer (rule 13) of {@link play.xponer.astronima.sim.ore.TitaniumReduction}, since the
     * block that will actually run it is not built yet (design/titanium-reduction.md §5).
     */
    private static LiteralArgumentBuilder<CommandSourceStack> titaniumCommands() {
        return Commands.literal("titanium")
                .executes(AstronimaCommand::reportTitanium);
    }

    /** The real numbers this whole machine rests on, printed rather than found by building it. */
    private static int reportTitanium(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        line(source, "Titanium cell - FFC-Cambridge reduction", ChatFormatting.AQUA);
        line(source, String.format(java.util.Locale.ROOT,
                        "  decomposition potential: %.3f V (aluminium's own is ~2.73 V)",
                        play.xponer.astronima.sim.ore.TitaniumReduction.DECOMPOSITION_VOLTS),
                ChatFormatting.GRAY);
        line(source, String.format(java.util.Locale.ROOT,
                        "  one charge (%.0f g titania) yields %.1f g titanium (%d items)",
                        play.xponer.astronima.sim.ore.TitaniumReduction.TITANIA_GRAMS_PER_ITEM,
                        play.xponer.astronima.sim.ore.TitaniumReduction.titaniumGramsFrom(
                                play.xponer.astronima.sim.ore.TitaniumReduction
                                        .TITANIA_GRAMS_PER_ITEM),
                        play.xponer.astronima.sim.ore.TitaniumReduction.titaniumItemsPerCharge()),
                ChatFormatting.GREEN);
        return 1;
    }

    /**
     * Reading the heavy-water cascade, per the debug-command rule — the honest first consumer
     * (rule 13) of {@link play.xponer.astronima.sim.chem.HeavyWaterCascade}, since the block that
     * will actually run it is not built yet (design/heavy-water.md §8).
     */
    private static LiteralArgumentBuilder<CommandSourceStack> heavyWaterCommands() {
        return Commands.literal("heavywater")
                .then(Commands.literal("sweep").executes(AstronimaCommand::sweepHeavyWater))
                .then(Commands.literal("at")
                        .then(Commands.argument("stages", IntegerArgumentType.integer(0, 20))
                                .executes(AstronimaCommand::heavyWaterAt)));
    }

    /** The D2O fraction after a chosen number of cascade stages, and how many bottles it cost. */
    private static int heavyWaterAt(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int stages = IntegerArgumentType.getInteger(context, "stages");
        double fraction = play.xponer.astronima.sim.chem.HeavyWaterCascade.fractionAfterStages(
                play.xponer.astronima.sim.chem.HeavyWaterCascade.NATURAL_D2O_FRACTION, stages);
        line(source, String.format("Heavy water - %d stage%s from natural ice-melt", stages,
                        stages == 1 ? "" : "s"), ChatFormatting.AQUA);
        line(source, String.format("  D2O fraction: %.4f%%", fraction * 100),
                fraction >= 0.995 ? ChatFormatting.GREEN
                        : fraction >= 0.5 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
        return 1;
    }

    /** The whole cascade curve, printed rather than found by running eight real batches. */
    private static int sweepHeavyWater(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        line(source, "Heavy water - cascade sweep from natural ice-melt (156 ppm D2O)",
                ChatFormatting.AQUA);
        line(source, "  stage  D2O fraction  bottles for one output bottle", ChatFormatting.GRAY);
        long bottlesPerOutput = 1;
        for (int stage = 0; stage <= 10; stage++) {
            double fraction = play.xponer.astronima.sim.chem.HeavyWaterCascade.fractionAfterStages(
                    play.xponer.astronima.sim.chem.HeavyWaterCascade.NATURAL_D2O_FRACTION, stage);
            if (stage > 0) {
                bottlesPerOutput *= 5;
            }
            line(source, String.format("  %5d  %11.4f%%  %d", stage, fraction * 100, bottlesPerOutput),
                    fraction >= 0.995 ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        }
        return 1;
    }

    /**
     * Sets and reads codex-disclosure state directly — rule's own debug channel: reaching the
     * real rim or mining real ore to test a single locked block is a real, if slow, path;
     * this is the honest shortcut. See design/codex-disclosure.md.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> unlockCommands() {
        return Commands.literal("unlock")
                .then(Commands.literal("status").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    play.xponer.astronima.progression.Unlocks unlocks =
                            player.getData(play.xponer.astronima.registry.ModAttachments.UNLOCKS.get());
                    return feedback(context, unlocks.earned().isEmpty()
                            ? "nothing earned yet"
                            : String.join(", ", unlocks.earned()));
                }))
                .then(Commands.literal("grant")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(context, "id");
                                    play.xponer.astronima.progression.Unlocks unlocks =
                                            player.getData(play.xponer.astronima.registry.ModAttachments.UNLOCKS.get());
                                    player.setData(play.xponer.astronima.registry.ModAttachments.UNLOCKS.get(),
                                            unlocks.with(id));
                                    return feedback(context, "granted " + id);
                                })))
                .then(Commands.literal("clear").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.setData(play.xponer.astronima.registry.ModAttachments.UNLOCKS.get(),
                            play.xponer.astronima.progression.Unlocks.NONE);
                    return feedback(context, "cleared");
                }));
    }

    /**
     * The research layer's own debug channel, per design/astra-research-m1.md §1 — the honest
     * first consumer of {@code sim/magic/Research}/{@code ResearchState} (rule 13), the same
     * standing {@code /astronima magic coherence} was given before the coherence meter existed.
     * There is no atlas screen yet (M2+), so this is the only door in until then.
     *
     * <p>{@code identify} is not in astra-research.md §8's own list of subcommands, added anyway:
     * {@code ResearchState} carries {@code identifiedObjects} as real, saved state, and this
     * project's standing rule is that a mechanic ships a command that can set and read everything
     * it persists — a field with no way to set it here would be rule 9's failure at the command
     * layer instead of the block layer.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> researchCommands() {
        return Commands.literal("research")
                .then(Commands.literal("status").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerPlayer player = source.getPlayerOrException();
                    play.xponer.astronima.sim.magic.ResearchState state =
                            player.getData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
                    line(source, "Research", ChatFormatting.AQUA);
                    line(source, "  held claims: " + (state.heldClaims().isEmpty()
                            ? "none" : String.join(", ", state.heldClaims())), ChatFormatting.WHITE);
                    line(source, "  identified: " + (state.identifiedObjects().isEmpty()
                            ? "none" : String.join(", ", state.identifiedObjects())), ChatFormatting.WHITE);
                    line(source, "  refuted: " + (state.refutedCombinations().isEmpty()
                            ? "none" : String.join(", ", state.refutedCombinations())), ChatFormatting.GRAY);
                    return 1;
                }))
                .then(Commands.literal("reachable").executes(context -> {
                    // sim/magic/ResearchReachability's own honest first consumer (rule 13): the
                    // joint item+research walk (design/astra-atlas-s3d-unlocks.md §3) was written
                    // and guarded before anything in the actual game read it — the same shape
                    // /astronima magic coherence stood in for the coherence meter before it
                    // existed. Prints the walk from an empty world, not this player's own state.
                    CommandSourceStack source = context.getSource();
                    var result = play.xponer.astronima.sim.magic.ResearchReachability.compute();
                    line(source, "Reachable from an empty world", ChatFormatting.AQUA);
                    line(source, "  claims: " + (result.reachableClaims().isEmpty()
                            ? "none" : String.join(", ", result.reachableClaims())), ChatFormatting.WHITE);
                    java.util.List<String> unreachableClaims = new java.util.ArrayList<>();
                    for (var claim : play.xponer.astronima.sim.magic.Claims.ALL) {
                        if (!result.reachableClaims().contains(claim.id())) {
                            unreachableClaims.add(claim.id());
                        }
                    }
                    line(source, "  unreachable claims: " + (unreachableClaims.isEmpty()
                            ? "none" : String.join(", ", unreachableClaims)),
                            unreachableClaims.isEmpty() ? ChatFormatting.WHITE : ChatFormatting.RED);
                    java.util.List<String> gatedItems = new java.util.ArrayList<>();
                    for (var source2 : play.xponer.astronima.crafting.CraftingTree.sources()) {
                        String resultId = switch (source2) {
                            case play.xponer.astronima.crafting.CraftingTree.Shaped shaped -> shaped.result();
                            case play.xponer.astronima.crafting.CraftingTree.Shapeless shapeless -> shapeless.result();
                            default -> null;
                        };
                        if (resultId != null
                                && play.xponer.astronima.crafting.CraftingTree.researchGateOf(resultId) != null) {
                            gatedItems.add(resultId + " (gate: "
                                    + play.xponer.astronima.crafting.CraftingTree.researchGateOf(resultId)
                                    + ", reachable: " + result.reachableItems().contains(resultId) + ")");
                        }
                    }
                    line(source, "  gated items: " + (gatedItems.isEmpty()
                            ? "none" : String.join(", ", gatedItems)), ChatFormatting.GRAY);
                    return 1;
                }))
                .then(Commands.literal("hold")
                        .then(Commands.argument("claim_id", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String claimId = StringArgumentType.getString(context, "claim_id");
                                    var state = player.getData(
                                            play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
                                    player.setData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get(),
                                            state.withHeld(claimId));
                                    return feedback(context, "held " + claimId);
                                })))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("claim_id", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String claimId = StringArgumentType.getString(context, "claim_id");
                                    var state = player.getData(
                                            play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
                                    player.setData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get(),
                                            state.withoutHeld(claimId));
                                    return feedback(context, "revoked " + claimId);
                                })))
                .then(Commands.literal("identify")
                        .then(targetArgument().executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ObservationTarget target = parseTarget(context);
                            String objectId = target.name().toLowerCase(Locale.ROOT);
                            var state = player.getData(
                                    play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
                            player.setData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get(),
                                    state.withIdentified(objectId));
                            return feedback(context, "identified " + objectId);
                        })))
                .then(Commands.literal("plate")
                        .then(targetArgument()
                                .executes(context -> givePlate(context, GasMixture.EARTH_PRESSURE_KPA))
                                .then(Commands.argument("vacuum_kpa",
                                                DoubleArgumentType.doubleArg(0, 200))
                                        .executes(context -> givePlate(context,
                                                DoubleArgumentType.getDouble(context, "vacuum_kpa"))))))
                .then(Commands.literal("resolve")
                        .then(Commands.argument("claim_id", StringArgumentType.word())
                                .then(Commands.argument("required_csv", StringArgumentType.word())
                                        .then(Commands.argument("offered_csv", StringArgumentType.word())
                                                .executes(AstronimaCommand::resolveClaim)))))
                .then(Commands.literal("stage")
                        .then(Commands.argument("claim_id", StringArgumentType.word())
                                .executes(AstronimaCommand::completeStage)));
    }


    /**
     * Completes the current stage of a real claim through the same pure core the atlas's
     * Complete packet runs ({@code StageCompletion.attempt}, rule 13 - the command is the
     * command-line door into the verb, not a second implementation of it). Refuses a hand-in
     * stage it cannot see an inventory for by naming the row, never silently granting: this
     * door exists for the no-inventory stages (the identifications, the owned filters); the
     * plates are handed in through the atlas or a real chest-side flow, not through chat.
     */
    private static int completeStage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String claimId = StringArgumentType.getString(context, "claim_id");
        play.xponer.astronima.sim.magic.Research.Claim claim = null;
        for (play.xponer.astronima.sim.magic.Research.Claim c
                : play.xponer.astronima.sim.magic.Claims.ALL) {
            if (c.id().equals(claimId)) {
                claim = c;
                break;
            }
        }
        if (claim == null) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                    "no claim with id " + claimId));
            return 0;
        }
        var state = player.getData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
        int current = state.stageOf(claim.id());
        play.xponer.astronima.sim.magic.InventoryView inventory = itemId -> 0;
        var outcome = play.xponer.astronima.sim.magic.StageCompletion.attempt(
                claim, current, state, inventory);
        switch (outcome) {
            case play.xponer.astronima.sim.magic.StageCompletion.Outcome.AlreadyDone done -> {
                return feedback(context, claimId + " is already held");
            }
            case play.xponer.astronima.sim.magic.StageCompletion.Outcome.Refused refused -> {
                context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                        "stage " + (current + 1) + " of " + claimId
                                + " is not met - the atlas names the rows"));
                return 0;
            }
            case play.xponer.astronima.sim.magic.StageCompletion.Outcome.Held held -> {
                player.setData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get(),
                        state.withStageComplete(claim.id(), current, claim.totalStages()));
                var unlocks = player.getData(play.xponer.astronima.registry.ModAttachments.UNLOCKS.get());
                var next = unlocks;
                for (String grant : held.grants()) {
                    next = next.with(grant);
                }
                if (next != unlocks) {
                    player.setData(play.xponer.astronima.registry.ModAttachments.UNLOCKS.get(), next);
                }
                return feedback(context, "held: " + claimId);
            }
            case play.xponer.astronima.sim.magic.StageCompletion.Outcome.Advanced advanced -> {
                player.setData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get(),
                        state.withStageComplete(claim.id(), current, claim.totalStages()));
                var unlocks = player.getData(play.xponer.astronima.registry.ModAttachments.UNLOCKS.get());
                var next = unlocks;
                for (String grant : advanced.grants()) {
                    next = next.with(grant);
                }
                if (next != unlocks) {
                    player.setData(play.xponer.astronima.registry.ModAttachments.UNLOCKS.get(), next);
                }
                return feedback(context, "stage " + advanced.nextStageIndex()
                        + " of " + claimId);
            }
        }
    }

    /**
     * Runs {@code sim/magic/Research#resolve} itself, not just {@code ResearchState}'s setters —
     * {@code hold}/{@code revoke} above are force-overrides (the same shape as
     * {@code /astronima phasechange set}), but the actual evidence-matching verb needs its own
     * real door too (rule 13; {@code ArchitectureRulesTest.rule13} fails the build otherwise, the
     * same guard design/sim-lands-with-consumer.md names). There is no real claim roster before
     * M4, so this builds an ad-hoc claim from the command's own arguments — a tester can still
     * exercise a genuine hold/refute without waiting for the atlas.
     */

    private static int resolveClaim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String claimId = StringArgumentType.getString(context, "claim_id");
        Set<String> required = csvToSet(StringArgumentType.getString(context, "required_csv"));
        Set<String> offered = csvToSet(StringArgumentType.getString(context, "offered_csv"));
        var claim = new play.xponer.astronima.sim.magic.Research.Claim(claimId, required);
        var state = player.getData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get());
        var resolution = play.xponer.astronima.sim.magic.Research.resolve(state, claim, offered);
        player.setData(play.xponer.astronima.registry.ModAttachments.RESEARCH.get(), resolution.next());
        return feedback(context, (resolution.held() ? "holds: " : "refuted: ") + claimId);
    }

    /**
     * {@code sim/optics/TelescopeMount}'s own debug channel, per design/astra-telescope.md — the
     * honest first consumer of the mount's slew clamp (rule 13), the same standing every other sim
     * model here is given before the block that owns it exists. Reports the player's own look
     * direction against a ground-tripod mount's limits: no telescope block exists yet, so this is
     * the only door in until it does.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> telescopeCommands() {
        return Commands.literal("telescope")
                .then(Commands.literal("aim").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerPlayer player = source.getPlayerOrException();
                    Vec3 look = player.getLookAngle();
                    SkyRotation.Vec3 desired = new SkyRotation.Vec3(look.x(), look.y(), look.z());
                    SkyRotation.Vec3 up = new SkyRotation.Vec3(0.0, 1.0, 0.0);
                    SkyRotation.Vec3 clamped = TelescopeMount.clamp(desired, up, TelescopeMount.GROUND_TRIPOD);
                    double rawAngleFromUp = SkyRotation.angleBetweenDegrees(desired, up);
                    double clampedAngleFromUp = SkyRotation.angleBetweenDegrees(clamped, up);
                    boolean wasClamped = Math.abs(rawAngleFromUp - clampedAngleFromUp) > 1.0e-6;
                    line(source, "Telescope (ground tripod)", ChatFormatting.AQUA);
                    line(source, "  raw look, degrees from straight up: " + round(rawAngleFromUp),
                            ChatFormatting.WHITE);
                    line(source, wasClamped
                            ? "  clamped to: " + round(clampedAngleFromUp) + " — that direction is past the mount's reach"
                            : "  within the mount's reach, unclamped", wasClamped ? ChatFormatting.RED : ChatFormatting.GREEN);
                    return 1;
                }));
    }

    private static Set<String> csvToSet(String csv) {
        Set<String> ids = new java.util.HashSet<>();
        for (String id : csv.split(",")) {
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static int givePlate(CommandContext<CommandSourceStack> context, double vacuumKPa)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ObservationTarget target = parseTarget(context);
        var captured = new play.xponer.astronima.sim.magic.CapturedSpectrum(target, vacuumKPa);
        ItemStack blank = new ItemStack(play.xponer.astronima.registry.ModItems.SPECTRAL_PLATE.get());
        ItemStack exposed = play.xponer.astronima.item.SpectralPlateItem.exposedCopy(blank, captured);
        if (!player.getInventory().add(exposed)) {
            player.drop(exposed, false);
        }
        return feedback(context, "exposed a plate of " + target.name().toLowerCase(Locale.ROOT)
                + " at " + round(vacuumKPa) + " kPa");
    }

    /**
     * Reads or forces the nearest paraffin thermal mass block's own melt state — the debug
     * channel {@code design/phase-change-blocks.md} needs, since a real melt/freeze cycle
     * otherwise takes real minutes to watch play out. {@code /astronima temp} already forces a
     * room's own temperature, so pushing a room past the melt point to watch this react needs
     * no new hook of its own.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> phasechangeCommands() {
        return Commands.literal("phasechange")
                .then(Commands.literal("status").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerPlayer player = source.getPlayerOrException();
                    ServerLevel level = source.getLevel();
                    var found = nearestParaffinThermalMass(level, player.blockPosition());
                    if (found == null) {
                        source.sendFailure(Component.literal(
                                "No paraffin thermal mass within 6 blocks - stand next to one."));
                        return 0;
                    }
                    RoomState room = Atmosphere.get(level).roomTouching(found.getBlockPos());
                    String roomText = room == null ? "no room"
                            : round(room.temperatureK() - 273.15) + " °C";
                    line(source, "Paraffin thermal mass at " + found.getBlockPos().toShortString(),
                            ChatFormatting.AQUA);
                    line(source, "  meltFraction=" + round(found.meltFraction())
                            + " room=" + roomText, ChatFormatting.YELLOW);
                    return 1;
                }))
                .then(Commands.literal("set")
                        .then(Commands.argument("fraction", DoubleArgumentType.doubleArg(0, 1))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    ServerLevel level = source.getLevel();
                                    double fraction = DoubleArgumentType.getDouble(context, "fraction");
                                    var found = nearestParaffinThermalMass(level, player.blockPosition());
                                    if (found == null) {
                                        source.sendFailure(Component.literal(
                                                "No paraffin thermal mass within 6 blocks - stand next to one."));
                                        return 0;
                                    }
                                    found.setMeltFraction(fraction);
                                    return feedback(context, "meltFraction set to " + fraction);
                                })));
    }

    private static play.xponer.astronima.block.entity.ParaffinThermalMassBlockEntity
            nearestParaffinThermalMass(ServerLevel level, BlockPos origin) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-6, -3, -6), origin.offset(6, 3, 6))) {
            if (level.getBlockEntity(candidate)
                    instanceof play.xponer.astronima.block.entity.ParaffinThermalMassBlockEntity mass) {
                return mass;
            }
        }
        return null;
    }

    /**
     * Reads or forces the nearest dewar's liquid content — design/cryogenics.md's own debug
     * channel, since real boil-off (design's own numbers: hours) and a real BLEVE both take far
     * too long to reach by playing normally.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> cryoCommands() {
        LiteralArgumentBuilder<CommandSourceStack> status = Commands.literal("status")
                .executes(AstronimaCommand::cryoStatus);

        ArgumentBuilder<CommandSourceStack, ?> setMoles =
                Commands.argument("moles", DoubleArgumentType.doubleArg(0))
                        .executes(AstronimaCommand::cryoSet);
        ArgumentBuilder<CommandSourceStack, ?> setCryogen =
                Commands.argument("cryogen", StringArgumentType.word()).then(setMoles);
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set").then(setCryogen);

        ArgumentBuilder<CommandSourceStack, ?> pressurizeKPa =
                Commands.argument("kPa", DoubleArgumentType.doubleArg(0))
                        .executes(AstronimaCommand::cryoPressurize);
        LiteralArgumentBuilder<CommandSourceStack> pressurize =
                Commands.literal("pressurize").then(pressurizeKPa);

        return Commands.literal("cryo").then(status).then(set).then(pressurize);
    }

    private static int cryoStatus(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        var tank = nearestCryoTank(level, player.blockPosition());
        var cooler = nearestCryoCooler(level, player.blockPosition());
        if (tank == null && cooler == null) {
            source.sendFailure(Component.literal(
                    "No cryo tank or cryocooler within 6 blocks - stand next to one."));
            return 0;
        }
        if (tank != null) {
            line(source, "Cryo tank at " + tank.getBlockPos().toShortString(), ChatFormatting.AQUA);
            line(source, "  cryogen=" + tank.cryogen() + " liquidMoles=" + round(tank.liquidMoles())
                    + " aerogel=" + tank.aerogelWrapped(), ChatFormatting.YELLOW);
            line(source, "  pressure=" + round(tank.pressureKPa()) + " kPa condition="
                    + tank.cryoCondition(), ChatFormatting.YELLOW);
        }
        if (cooler != null) {
            line(source, "Cryocooler at " + cooler.getBlockPos().toShortString(), ChatFormatting.AQUA);
            line(source, "  stall=" + cooler.stall() + " watts=" + round(cooler.watts()),
                    ChatFormatting.YELLOW);
        }
        return 1;
    }

    private static int cryoSet(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        var tank = nearestCryoTank(level, player.blockPosition());
        if (tank == null) {
            source.sendFailure(Component.literal("No cryo tank within 6 blocks - stand next to one."));
            return 0;
        }
        play.xponer.astronima.sim.cryo.Cryogen species;
        try {
            species = play.xponer.astronima.sim.cryo.Cryogen.valueOf(
                    StringArgumentType.getString(context, "cryogen").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown cryogen - use LN2, LOX or LH2"));
            return 0;
        }
        double moles = DoubleArgumentType.getDouble(context, "moles");
        tank.debugSetLiquid(species, moles);
        return feedback(context, "liquidMoles set to " + moles + " (" + species + ")");
    }

    private static int cryoPressurize(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        var tank = nearestCryoTank(level, player.blockPosition());
        if (tank == null) {
            source.sendFailure(Component.literal("No cryo tank within 6 blocks - stand next to one."));
            return 0;
        }
        if (tank.cryogen() == null) {
            source.sendFailure(Component.literal(
                    "This tank has never held a liquid yet - /astronima cryo set it first."));
            return 0;
        }
        double targetKPa = DoubleArgumentType.getDouble(context, "kPa");
        // A blunt ramp on the headspace's own gas load: cheap, and this is a debug shortcut,
        // not a physical process.
        double lastPressure = tank.pressureKPa();
        double dose = 50.0;
        for (int i = 0; i < 60 && tank.pressureKPa() < targetKPa; i++) {
            tank.contents().addGasAt(tank.cryogen().gas(), dose, tank.cryogen().boilingPointK());
            if (tank.pressureKPa() <= lastPressure) {
                break; // not climbing any more - stop rather than loop forever
            }
            lastPressure = tank.pressureKPa();
            dose *= 1.5;
        }
        return feedback(context, "headspace now " + round(tank.pressureKPa()) + " kPa (condition="
                + tank.cryoCondition() + ")");
    }

    private static play.xponer.astronima.block.entity.CryoTankBlockEntity
            nearestCryoTank(ServerLevel level, BlockPos origin) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-6, -3, -6), origin.offset(6, 3, 6))) {
            if (level.getBlockEntity(candidate)
                    instanceof play.xponer.astronima.block.entity.CryoTankBlockEntity tank) {
                return tank;
            }
        }
        return null;
    }

    private static play.xponer.astronima.block.entity.CryoCoolerBlockEntity
            nearestCryoCooler(ServerLevel level, BlockPos origin) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-6, -3, -6), origin.offset(6, 3, 6))) {
            if (level.getBlockEntity(candidate)
                    instanceof play.xponer.astronima.block.entity.CryoCoolerBlockEntity cooler) {
                return cooler;
            }
        }
        return null;
    }

    /**
     * Reads or forces gamma dose (design/radiation.md §7) — real accumulation and a real ARS
     * severity classification both take real hours to reach by playing normally.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> radiationCommands() {
        LiteralArgumentBuilder<CommandSourceStack> status = Commands.literal("status")
                .executes(AstronimaCommand::radiationStatus);

        ArgumentBuilder<CommandSourceStack, ?> setSievert =
                Commands.argument("sievert", DoubleArgumentType.doubleArg(0))
                        .executes(AstronimaCommand::radiationSet);
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set").then(setSievert);

        return Commands.literal("radiation").then(status).then(set);
    }

    private static int radiationStatus(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        double rateSvPerH = play.xponer.astronima.atmosphere.RadiationSources
                .totalGammaSvPerH(level, eyePos, player.getEyePosition());
        double doseSv = player.getData(play.xponer.astronima.registry.ModAttachments.RADIATION_DOSE);
        play.xponer.astronima.sim.rad.RadiationDose.Severity severity =
                play.xponer.astronima.sim.rad.RadiationDose.Severity.classify(doseSv);
        line(source, "rate=" + round(rateSvPerH) + " Sv/h  bodyDose=" + round(doseSv)
                + " Sv  severity=" + severity, ChatFormatting.YELLOW);
        return 1;
    }

    private static int radiationSet(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        double sievert = DoubleArgumentType.getDouble(context, "sievert");
        player.setData(play.xponer.astronima.registry.ModAttachments.RADIATION_DOSE.get(),
                (float) sievert);
        return feedback(context, "bodyDose set to " + sievert + " Sv");
    }

    /**
     * Reads the nearest airlock controller's whole state: phase, faults, and what it
     * resolved. The rule's debug channel — the indicator on the block is the player's.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> airlockCommand() {
        return Commands.literal("airlock").executes(context -> {
            CommandSourceStack source = context.getSource();
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();

            play.xponer.astronima.block.entity.AirlockControllerBlockEntity found = null;
            BlockPos foundAt = null;
            BlockPos origin = player.blockPosition();
            for (BlockPos candidate : BlockPos.betweenClosed(
                    origin.offset(-6, -3, -6), origin.offset(6, 3, 6))) {
                if (level.getBlockEntity(candidate)
                        instanceof play.xponer.astronima.block.entity.AirlockControllerBlockEntity controller) {
                    found = controller;
                    foundAt = candidate.immutable();
                    break;
                }
            }
            if (found == null) {
                source.sendFailure(Component.literal(
                        "No airlock controller within 6 blocks - stand next to one."));
                return 0;
            }
            var state = level.getBlockState(foundAt);
            var facing = state.getValue(
                    play.xponer.astronima.block.AirlockControllerBlock.FACING);
            line(source, "Airlock at " + foundAt.toShortString() + " facing " + facing,
                    ChatFormatting.AQUA);
            line(source, "  phase=" + found.phase()
                    + " chamber=" + found.chamberFault() + " cycle=" + found.cycleFault(),
                    ChatFormatting.YELLOW);
            line(source, String.format("  chamber=%.1f kPa, habitat=%.1f kPa",
                    found.chamberPressureKPa(), found.habitatPressureKPa()), ChatFormatting.YELLOW);
            // The commissioning record: what the player put on each terminal and what is
            // wrong with it. The panel is where they read this; this is the debug channel.
            for (var role : play.xponer.astronima.sim.airlock.DeviceBinding.Role.values()) {
                long device = found.bindings().device(role);
                String where = device
                        == play.xponer.astronima.sim.airlock.DeviceBinding.NONE
                        ? "unbound" : BlockPos.of(device).toShortString();
                line(source, "  " + role.label() + " = " + where
                        + " (" + found.problem(role).words() + ")",
                        found.problem(role)
                                == play.xponer.astronima.sim.airlock.DeviceBinding.Problem.NONE
                                ? ChatFormatting.GRAY : ChatFormatting.RED);
            }
            // What SCAN would propose, for comparison with what they actually bound.
            var resolved = play.xponer.astronima.airlock.AirlockResolver.resolve(
                    level, foundAt, facing);
            line(source, resolved.ok()
                    ? "  scan would propose: doors=" + resolved.airlock().doorFeet()
                            + " pump=" + resolved.airlock().pump().toShortString()
                            + " tank=" + resolved.airlock().tank().toShortString()
                    : "  scan would propose nothing (" + resolved.fault() + ")",
                    ChatFormatting.DARK_GRAY);
            return 1;
        });
    }

    /**
     * The conductor ladder, and a run sized against it — the electrical tier's debug channel.
     *
     * <p>{@code /astronima wire} prints every conductor with its real resistivity and what it
     * can carry; {@code /astronima wire iron 4 20 48 250} sizes an actual run and reports what
     * it costs. Both show <strong>vacuum and cabin air side by side</strong>, because that
     * difference is the whole mechanic (design/electrical.md §2.1) and a single column would
     * hide it.
     *
     * <p>This is the honest first consumer of {@code sim/circuit} and {@code sim/chem}: rule 13
     * says a model nothing consults is a model no player will ever meet, and it fails the build
     * — correctly — until something asks it a question. A debug command asks real questions
     * through the real API before any block exists to do it prettily.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> wireCommand() {
        LiteralArgumentBuilder<CommandSourceStack> command =
                Commands.literal("wire").executes(AstronimaCommand::reportConductorLadder)
                        .then(Commands.literal("net").executes(AstronimaCommand::reportWireNetwork))
                        .then(Commands.literal("anchor").executes(AstronimaCommand::anchorWire))
                        .then(Commands.literal("route")
                                .then(routeArguments("pathfind", WireRouter.Mode.PATHFIND))
                                .then(routeArguments("straight", WireRouter.Mode.STRAIGHT)))
                        .then(Commands.literal("remove").executes(AstronimaCommand::removeWire))
                        .then(placeArguments());
        // Walking the enum rather than writing one branch per metal (rule 20): a conductor
        // added to the ladder is in this command the moment it exists, with nothing to forget.
        for (ConductorMaterial material : ConductorMaterial.values()) {
            command = command.then(Commands.literal(material.name().toLowerCase(Locale.ROOT))
                    .then(Commands.argument("mm2", DoubleArgumentType.doubleArg(0.1, 500))
                            .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0, 512))
                                    .then(Commands.argument("volts",
                                            DoubleArgumentType.doubleArg(1, 100_000))
                                            .then(Commands.argument("watts",
                                                    DoubleArgumentType.doubleArg(0, 10_000_000))
                                                    .executes(context ->
                                                            sizeRun(context, material)))))));
        }
        return command;
    }

    /** Surroundings the ladder is quoted against: an asteroid is cold, not room temperature. */
    private static final double WIRE_AMBIENT_K = 200.0;

    /**
     * {@code place <metal> <colour>} — the wire layer's hands, until the coil item exists.
     *
     * <p>Both arguments walk their own enums (rule 20), so a metal or a colour cannot be added
     * to the game and forgotten here.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> placeArguments() {
        LiteralArgumentBuilder<CommandSourceStack> place = Commands.literal("place");
        for (ConductorMaterial material : ConductorMaterial.values()) {
            place = place.then(Commands.literal(material.name().toLowerCase(Locale.ROOT))
                    .then(Commands.argument("colour", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                    java.util.Arrays.stream(DyeColor.values())
                                            .map(DyeColor::getSerializedName), builder))
                            .executes(context -> placeWire(context, material))));
        }
        return place;
    }

    /**
     * Where each player last anchored a trace.
     *
     * <p>The command's stand-in for the coil item's first click. Kept in memory only: an anchor
     * is a half-finished gesture, not state worth surviving a restart.
     */
    private static final java.util.Map<java.util.UUID, WirePixel> WIRE_ANCHORS =
            new java.util.HashMap<>();

    /**
     * The exact pixel of the surface the player is pointing at.
     *
     * <p>This is what makes routing <em>pixel-precise</em> rather than block-precise: the hit
     * position inside the block face is projected onto that face's own axes, so where you point
     * is which of the sixteen lanes you get (design/electrical.md §4.2).
     */
    private static WirePixel pixelUnderCrosshair(BlockHitResult hit) {
        BlockPos cell = hit.getBlockPos().relative(hit.getDirection());
        Direction facing = hit.getDirection().getOpposite();
        Face face = Faces.of(facing);
        double localX = hit.getLocation().x - cell.getX();
        double localY = hit.getLocation().y - cell.getY();
        double localZ = hit.getLocation().z - cell.getZ();
        int u = pixelAlong(FaceBasis.uAxis(face), localX, localY, localZ);
        int v = pixelAlong(FaceBasis.vAxis(face), localX, localY, localZ);
        return new WirePixel(cell.getX(), cell.getY(), cell.getZ(), face, u, v);
    }

    /** One component of a position inside a cell, as a pixel index 0..15. */
    private static int pixelAlong(Face axis, double localX, double localY, double localZ) {
        double along = axis.dx() != 0 ? localX : axis.dy() != 0 ? localY : localZ;
        return Math.clamp((int) Math.floor(along * FaceBasis.GRID), 0,
                FaceBasis.GRID - 1);
    }

    private static int anchorWire(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockHitResult hit = lookingAtBlock(player);
        if (hit == null) {
            return feedback(context, "Look at a block face to anchor a trace on it");
        }
        WirePixel anchor = pixelUnderCrosshair(hit);
        WIRE_ANCHORS.put(player.getUUID(), anchor);
        return feedback(context, "Anchored at pixel " + anchor.u() + "," + anchor.v()
                + " on " + anchor.face() + " of " + anchor.x() + " " + anchor.y() + " "
                + anchor.z());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> routeArguments(
            String name, WireRouter.Mode mode) {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(name);
        for (ConductorMaterial material : ConductorMaterial.values()) {
            literal = literal.then(Commands.literal(material.name().toLowerCase(Locale.ROOT))
                    .then(Commands.argument("colour", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                    java.util.Arrays.stream(DyeColor.values())
                                            .map(DyeColor::getSerializedName), builder))
                            .executes(context -> routeWire(context, material, mode))));
        }
        return literal;
    }

    /**
     * Routes a trace from the anchor to where the player is pointing, and lays it.
     *
     * <p>The headline verb of the tier, driven through the same {@code WireRouter} the ghost
     * preview will draw and the same one the server will re-run — one router, so a previewed
     * route and a laid route cannot differ (rule 13).
     *
     * <p><strong>Storage is still one segment per surface</strong>, so the pixel-accurate route
     * is collapsed onto the surfaces it crosses as it is laid. The routing is pixel-accurate
     * today; the <em>record</em> of it is not yet, and that gap is named in
     * design/electrical.md §4.2 rather than hidden.
     */
    private static int routeWire(CommandContext<CommandSourceStack> context,
                                 ConductorMaterial material, WireRouter.Mode mode)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        WirePixel anchor = WIRE_ANCHORS.get(player.getUUID());
        if (anchor == null) {
            return feedback(context, "Nothing anchored - run /astronima wire anchor first");
        }
        BlockHitResult hit = lookingAtBlock(player);
        if (hit == null) {
            return feedback(context, "Look at a block face to route to");
        }
        DyeColor colour = DyeColor.byName(
                StringArgumentType.getString(context, "colour").toLowerCase(Locale.ROOT), null);
        if (colour == null) {
            return feedback(context, "No such colour - try white, red, blue...");
        }
        WirePixel target = pixelUnderCrosshair(hit);

        // The world's only say in the matter: is there something to fasten this pixel to, and is
        // anything already in the way — a solid cell, or a part's housing.
        WireRouter.Space space = pixel -> Wires.canPlace(player.level(), pixel);

        var route = WireRouter.route(anchor, target, space, mode);
        if (route.isEmpty()) {
            return feedback(context, mode == WireRouter.Mode.STRAIGHT
                    ? "No straight run gets there - something is in the way"
                    : "No route at all - nothing to fasten a trace to");
        }

        // Never lay a route that is not a real chain. The router is trusted to search well, not
        // to be correct about adjacency — and once the ghost preview is a client sending a
        // proposed path, this is the check that stops a crafted one laying wire through a wall
        // (rule 13: the server owns the answer, whoever asked the question).
        List<WirePixel> pixels = route.get();
        for (int i = 1; i < pixels.size(); i++) {
            if (!PixelGeometry.neighbours(pixels.get(i - 1)).contains(pixels.get(i))) {
                return feedback(context, "Refused: that route jumps a gap at step " + i);
            }
        }

        int laid = Wires.placeAll(player.level(), pixels, colour, material,
                play.xponer.astronima.sim.circuit.WireGauge.DEFAULT);
        line(source, "Routed " + pixels.size() + " pixels (" + laid + " new, "
                + String.format(Locale.ROOT, "%.2f m", pixels.size() / 16.0) + ", "
                + name(mode) + ")", ChatFormatting.AQUA);
        WIRE_ANCHORS.put(player.getUUID(), target);
        line(source, "  anchor moved to the far end - keep clicking to build the chain",
                ChatFormatting.GRAY);
        return 1;
    }

    private static String name(WireRouter.Mode mode) {
        return mode == WireRouter.Mode.STRAIGHT ? "straight" : "pathfind";
    }

    /** Where the player is looking, as a wire slot: the cell beyond the face they clicked. */
    private static BlockHitResult lookingAtBlock(ServerPlayer player) {
        HitResult hit = player.pick(6.0, 1.0f, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block : null;
    }

    private static int placeWire(CommandContext<CommandSourceStack> context,
                                 ConductorMaterial material) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        BlockHitResult hit = lookingAtBlock(player);
        if (hit == null) {
            return feedback(context, "Look at a block face to lay wire on it");
        }
        DyeColor colour = DyeColor.byName(
                StringArgumentType.getString(context, "colour").toLowerCase(Locale.ROOT), null);
        if (colour == null) {
            return feedback(context, "No such colour - try white, red, blue...");
        }

        // Exactly the pixel being pointed at, not the middle of the face.
        WirePixel target = pixelUnderCrosshair(hit);
        if (!Wires.canPlace(player.level(), target)) {
            return feedback(context, "Nothing sturdy to fasten it to there");
        }
        Wires.place(player.level(), target, colour, material, play.xponer.astronima.sim.circuit.WireGauge.DEFAULT);
        line(source, "Laid " + material.name().toLowerCase(Locale.ROOT) + " ("
                + colour.getSerializedName() + ") at pixel " + target.u() + "," + target.v()
                + " on " + target.face(), ChatFormatting.AQUA);
        return 1;
    }

    private static int removeWire(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockHitResult hit = lookingAtBlock(player);
        if (hit == null) {
            return feedback(context, "Look at the surface the wire is on");
        }
        BlockPos cell = hit.getBlockPos().relative(hit.getDirection());
        Direction face = hit.getDirection().getOpposite();
        // The whole bundle: a surface can carry several colours, and picking one of them by
        // eye is what the coil item will be for.
        int pulled = Wires.removeAll(player.level(), cell, face);
        return pulled == 0
                ? feedback(context, "No wire on that surface")
                : feedback(context, "Pulled " + pulled + " pixels off " + cell.toShortString());
    }

    /**
     * What the run you are looking at actually joins up.
     *
     * <p>The honest first instrument for the wire layer: it walks the real network through the
     * real API, so it answers the question the renderer only hints at — <em>is this one circuit
     * or two?</em> — and it names a truncated walk rather than quietly reporting a short run
     * (rule 18).
     */
    private static int reportWireNetwork(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        BlockHitResult hit = lookingAtBlock(player);
        if (hit == null) {
            return feedback(context, "Look at the surface the wire is on");
        }
        BlockPos cell = hit.getBlockPos().relative(hit.getDirection());
        Direction face = hit.getDirection().getOpposite();
        var bundle = Wires.bundleOn(player.level(), cell, face);
        if (bundle.isEmpty()) {
            return feedback(context, "No wire on that surface");
        }
        // Every colour on the surface, each as its own network — so a crossing reports as two
        // separate circuits sharing a wall, which is the thing worth being able to check.
        line(source, bundle.size() + " trace(s) on " + cell.toShortString() + " " + face,
                ChatFormatting.AQUA);
        for (WireTrace trace : bundle) {
            if (trace.pixels().isEmpty()) {
                continue;
            }
            Wires.Network net = Wires.network(player.level(), trace.pixels().get(0),
                    trace.colour());
            ConductorMaterial weakest = net.weakest().orElse(trace.material());
            Conductor run = new Conductor(weakest, Conductor.mm2(Conductor.STANDARD_MM2),
                    net.metres());
            line(source, String.format(Locale.ROOT,
                            "  %-12s %4d px (%.2f m)  worst %s  %.4f ohm",
                            trace.colour().getSerializedName(), net.length(), net.metres(),
                            weakest.name().toLowerCase(Locale.ROOT),
                            run.resistance(ConductorMaterial.REFERENCE_K)),
                    ChatFormatting.WHITE);
            if (net.truncated()) {
                line(source, "    TRUNCATED - leaves loaded chunks, or is very large",
                        ChatFormatting.RED);
            }

            // What this run would actually do with a machine's load on it, at the bus voltage.
            var powered = play.xponer.astronima.wire.WirePower.resolve(
                    player.level(), trace.pixels().get(0), trace.colour());
            double wanted = play.xponer.astronima.sim.thermal.HeatBalance.WORKED_MACHINE_W;
            var split = play.xponer.astronima.sim.circuit.Delivery.send(wanted, 1.0,
                    powered.resistanceOhms(),
                    play.xponer.astronima.sim.circuit.Delivery.BUS_VOLTS);
            line(source, String.format(Locale.ROOT,
                            "    at %.0f V carrying %.0f W: %.1f W lost (%.0f%% arrives), %.2f A",
                            play.xponer.astronima.sim.circuit.Delivery.BUS_VOLTS, wanted,
                            split.heatJ(), split.efficiency() * 100,
                            play.xponer.astronima.sim.circuit.Delivery.amps(wanted,
                                    play.xponer.astronima.sim.circuit.Delivery.BUS_VOLTS)),
                    split.efficiency() > 0.9 ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
            line(source, "    terminals on this circuit: " + powered.terminals().size(),
                    ChatFormatting.GRAY);
        }
        return 1;
    }

    private static int reportConductorLadder(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        line(source, "Conductor ladder - " + Conductor.STANDARD_MM2 + " mm2, ambient "
                + (int) WIRE_AMBIENT_K + " K", ChatFormatting.AQUA);
        line(source, "  metal      formula   ohm/blk   amps vac/air", ChatFormatting.GRAY);
        for (ConductorMaterial material : ConductorMaterial.values()) {
            Conductor wire = new Conductor(material,
                    Conductor.mm2(Conductor.STANDARD_MM2), 1.0);
            Formula composition = material.formula();
            line(source, String.format(Locale.ROOT, "  %-10s %-9s %.4f   %.1f / %.1f",
                            material.name().toLowerCase(Locale.ROOT),
                            composition,
                            wire.resistance(ConductorMaterial.REFERENCE_K),
                            Ampacity.limitAmps(wire, WIRE_AMBIENT_K, 0.0),
                            Ampacity.limitAmps(wire, WIRE_AMBIENT_K, 1.0)),
                    ChatFormatting.WHITE);
        }
        line(source, "  vacuum carries ~60% of the air rating - there is nothing to cool it",
                ChatFormatting.GRAY);
        return 1;
    }

    private static int sizeRun(CommandContext<CommandSourceStack> context,
                               ConductorMaterial material) {
        CommandSourceStack source = context.getSource();
        double mm2 = DoubleArgumentType.getDouble(context, "mm2");
        double blocks = DoubleArgumentType.getDouble(context, "blocks");
        double volts = DoubleArgumentType.getDouble(context, "volts");
        double watts = DoubleArgumentType.getDouble(context, "watts");

        Conductor run = new Conductor(material, Conductor.mm2(mm2), blocks);
        double amps = watts / volts;
        // Cold resistance for the headline figures, then the settled temperature, because the
        // wire's own heating raises what it costs - which is the runaway worth showing.
        double resistance = run.resistance(ConductorMaterial.REFERENCE_K);
        double loss = run.lossWatts(amps, ConductorMaterial.REFERENCE_K);
        double drop = run.voltageDrop(amps, ConductorMaterial.REFERENCE_K);
        double inVacuum = Ampacity.steadyTemperatureK(run.withLength(1), amps,
                WIRE_AMBIENT_K, 0.0);
        double inAir = Ampacity.steadyTemperatureK(run.withLength(1), amps, WIRE_AMBIENT_K, 1.0);
        double ratedVacuum = Ampacity.limitAmps(run.withLength(1), WIRE_AMBIENT_K, 0.0);
        double ratedAir = Ampacity.limitAmps(run.withLength(1), WIRE_AMBIENT_K, 1.0);

        line(source, String.format(Locale.ROOT, "%s  %.1f mm2  %.0f blocks  %.0f V  %.0f W",
                material.name().toLowerCase(Locale.ROOT), mm2, blocks, volts, watts),
                ChatFormatting.AQUA);
        line(source, String.format(Locale.ROOT, "  resistance  %.4f ohm", resistance),
                ChatFormatting.WHITE);
        line(source, String.format(Locale.ROOT, "  current     %.2f A", amps),
                ChatFormatting.WHITE);
        line(source, String.format(Locale.ROOT, "  loss        %.1f W  (%.1f%% of the load)",
                        loss, watts > 0 ? 100.0 * loss / watts : 0.0),
                loss > watts * 0.2 ? ChatFormatting.RED : ChatFormatting.GREEN);
        line(source, String.format(Locale.ROOT, "  volt drop   %.2f V  -> %.1f V at the machine",
                drop, volts - drop), ChatFormatting.WHITE);
        line(source, String.format(Locale.ROOT, "  wire temp   vacuum %.0f K / air %.0f K",
                        inVacuum, inAir),
                inVacuum >= Ampacity.INSULATION_LIMIT_K ? ChatFormatting.RED
                        : ChatFormatting.WHITE);
        line(source, String.format(Locale.ROOT, "  ampacity    vacuum %.1f A / air %.1f A",
                        ratedVacuum, ratedAir),
                amps > ratedVacuum ? ChatFormatting.RED : ChatFormatting.GREEN);
        if (amps > ratedVacuum && amps <= ratedAir) {
            line(source, "  -> fine indoors, cooks itself outside", ChatFormatting.YELLOW);
        } else if (amps > ratedAir) {
            line(source, "  -> over its rating everywhere: thicker wire or higher voltage",
                    ChatFormatting.RED);
        }

        StringBuilder composition = new StringBuilder("  made of     ");
        Formula formula = material.formula();
        for (Map.Entry<Element, Double> part : formula.composition().entrySet()) {
            composition.append(part.getKey().symbol()).append(' ')
                    .append(String.format(Locale.ROOT, "%.0f%% ",
                            100.0 * formula.massFraction(part.getKey())));
        }
        line(source, composition.toString(), ChatFormatting.GRAY);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pipeCommand() {
        return Commands.literal("pipe").executes(context -> {
            CommandSourceStack source = context.getSource();
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = source.getLevel();

            // Whatever the player is standing on or looking at from close range; the
            // point is to name the run you are next to without having to target a
            // single pixel of a thin pipe.
            BlockPos found = null;
            BlockPos origin = player.blockPosition();
            for (BlockPos candidate : BlockPos.betweenClosed(
                    origin.offset(-4, -3, -4), origin.offset(4, 3, 4))) {
                if (PipeNetworks.isNetworkPart(level.getBlockState(candidate))) {
                    found = candidate.immutable();
                    break;
                }
            }
            if (found == null) {
                return feedback(context, "No pipe or port within a few blocks");
            }

            // A pump nearby changes what "working" means: it needs one volume on each
            // of its two faces, not two ports on one run. Reporting the port-to-port
            // rule at a pump setup told the player their correct build was wrong, which
            // is worse than saying nothing.
            BlockPos pump = null;
            for (BlockPos candidate : BlockPos.betweenClosed(
                    origin.offset(-4, -3, -4), origin.offset(4, 3, 4))) {
                if (level.getBlockState(candidate).is(ModBlocks.GAS_PUMP.get())) {
                    pump = candidate.immutable();
                    break;
                }
            }
            if (pump != null) {
                BlockState pumpState = level.getBlockState(pump);
                line(source, "Gas pump at " + pump.toShortString(), ChatFormatting.AQUA);
                reportSide(source, level, "intake  (big mouth)",
                        GasPumpBlock.inletSide(pumpState, pump));
                reportSide(source, level, "outlet  (small spout)",
                        GasPumpBlock.outletSide(pumpState, pump));
                line(source, "  running   "
                        + pumpState.getValue(GasPumpBlock.RUNNING), ChatFormatting.GRAY);
                return 1;
            }

            // The same verdict the wrench gives, first, so the debug channel and the
            // player's instrument cannot disagree about what is wrong with a line.
            var survey = play.xponer.astronima.pipe.PipeSurvey.of(level, found);
            if (survey != null) {
                var verdict = play.xponer.astronima.sim.pipe.RunDiagnosis.verdict(survey);
                line(source, "  verdict   "
                                + play.xponer.astronima.sim.pipe.RunDiagnosis.label(survey),
                        verdict.isFault() ? ChatFormatting.RED : ChatFormatting.GREEN);
            }

            PipeNetworks.Resolved resolved = PipeNetworks.resolve(level, found);
            if (resolved == null) {
                line(source, "Pipe run at " + found.toShortString(), ChatFormatting.AQUA);
                line(source, "  not a network yet. Either join two rooms with two gas"
                        + " ports, or put a pump on the run with one volume on each"
                        + " of its faces", ChatFormatting.GRAY);
                return 1;
            }

            line(source, "Pipe network", ChatFormatting.AQUA);
            line(source, "  pipes     " + resolved.pipes().size() + " blocks",
                    ChatFormatting.GRAY);
            line(source, "  ports     " + resolved.ports().size(), ChatFormatting.GRAY);
            line(source, "  volumes   " + resolved.roomCount()
                    + " (" + resolved.tanks().size() + " tanks)", ChatFormatting.GRAY);
            line(source, String.format("  conductance %.3g m3/s/kPa",
                    Conduit.ofBlocks(Math.max(1, resolved.pipes().size()))),
                    ChatFormatting.GRAY);
            for (BlockPos tank : resolved.tanks()) {
                if (level.getBlockEntity(tank) instanceof GasTankBlockEntity vessel) {
                    line(source, String.format("  tank %s  %.0f kPa  %s (%.0f%% of rating)",
                                    tank.toShortString(), vessel.pressureKPa(),
                                    vessel.condition(),
                                    PressureVessel.gauge(vessel.pressureKPa()) * 100),
                            vessel.condition() == PressureVessel.Condition.NOMINAL
                                    ? ChatFormatting.GRAY : ChatFormatting.RED);
                }
            }
            for (RoomState room : resolved.network().nodes()) {
                line(source, String.format("  room %d  %.1f kPa  ppO2 %.1f kPa",
                                room.id(), room.pressureKPa(),
                                room.partialPressureKPa(Gas.OXYGEN)),
                        ChatFormatting.GRAY);
            }
            return 1;
        });
    }

    /** What one face of a pump is actually plumbed to, and to how much. */
    private static void reportSide(CommandSourceStack source, ServerLevel level,
                                   String label, BlockPos side) {
        PipeNetworks.Resolved resolved = PipeNetworks.resolveSide(level, side);
        int volumes = resolved == null ? 0 : resolved.network().nodes().size();
        String verdict = switch (volumes) {
            case 0 -> "nothing connected";
            case 1 -> "ok, 1 volume";
            default -> volumes + " volumes - a pump handles one per face";
        };
        line(source, "  " + label + "  " + verdict,
                volumes == 1 ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toolCommand() {
        return Commands.literal("tool")
                .then(Commands.literal("read").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ItemStack stack = source.getPlayerOrException().getMainHandItem();
                    if (!(stack.getItem() instanceof WearingToolItem)) {
                        return feedback(context, "Hold a wearing tool to read it");
                    }
                    ToolHead head = WearingToolItem.headOf(stack);
                    line(source, "Tool head", ChatFormatting.AQUA);
                    line(source, "  edge      " + percent(head.edge())
                            + "  (" + head.condition() + ")", ChatFormatting.GRAY);
                    line(source, "  hardness  " + percent(head.hardness())
                            + (head.cracked() ? "  CRACKED" : ""), ChatFormatting.GRAY);
                    line(source, "  speed     " + percent(head.speedMultiplier()),
                            ChatFormatting.GRAY);
                    line(source, "  grinds    " + head.resharpenings() + " left",
                            ChatFormatting.GRAY);
                    line(source, "  life      " + Math.round(head.remainingLifeInBlocks())
                            + " blocks", ChatFormatting.GRAY);
                    return 1;
                }))
                .then(Commands.literal("edge")
                        .then(Commands.argument("fraction", DoubleArgumentType.doubleArg(0, 1))
                                .executes(context -> {
                                    ItemStack stack = context.getSource().getPlayerOrException()
                                            .getMainHandItem();
                                    if (!(stack.getItem() instanceof WearingToolItem)) {
                                        return feedback(context, "Hold a wearing tool to set it");
                                    }
                                    double value = DoubleArgumentType.getDouble(context, "fraction");
                                    ToolHead head = WearingToolItem.headOf(stack);
                                    WearingToolItem.setHead(stack, new ToolHead(value,
                                            head.hardness(), head.cracked(), head.resharpenings()));
                                    return feedback(context, "Edge set to " + percent(value));
                                })))
                .then(Commands.literal("forge")
                        .then(Commands.argument("hardness", DoubleArgumentType.doubleArg(0, 1))
                                .then(Commands.argument("cracked", BoolArgumentType.bool())
                                        .executes(context -> {
                                            ItemStack stack = context.getSource()
                                                    .getPlayerOrException().getMainHandItem();
                                            if (!(stack.getItem() instanceof WearingToolItem)) {
                                                return feedback(context,
                                                        "Hold a wearing tool to reforge it");
                                            }
                                            double hardness = DoubleArgumentType
                                                    .getDouble(context, "hardness");
                                            boolean cracked = BoolArgumentType
                                                    .getBool(context, "cracked");
                                            WearingToolItem.setHead(stack,
                                                    ToolHead.forged(hardness, cracked));
                                            return feedback(context, "Reforged at "
                                                    + percent(hardness)
                                                    + (cracked ? " cracked" : " sound"));
                                        }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> roomCommands() {
        return Commands.literal("room")
                .then(Commands.literal("info").executes(AstronimaCommand::roomInfo))
                .then(Commands.literal("rescan").executes(AstronimaCommand::rescan));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gasCommands() {
        return Commands.literal("gas")
                .then(Commands.literal("add").then(gasArgument()
                        .then(Commands.argument("mol", DoubleArgumentType.doubleArg(0, 100_000))
                                .executes(context -> changeGas(context, true)))))
                .then(Commands.literal("remove").then(gasArgument()
                        .then(Commands.argument("mol", DoubleArgumentType.doubleArg(0, 100_000))
                                .executes(context -> changeGas(context, false)))))
                // Sets a species to a share of the room's contents — the direct way to
                // land inside a flammability window (methane ignites at 5–15 %).
                .then(Commands.literal("mix").then(gasArgument()
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 99))
                                .executes(AstronimaCommand::mixGas))))
                .then(Commands.literal("fill")
                        .then(Commands.argument("kpa", DoubleArgumentType.doubleArg(0, 1000))
                                .executes(AstronimaCommand::fillAir)))
                .then(Commands.literal("clear").executes(AstronimaCommand::clearGas));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> physiologyCommands() {
        return Commands.literal("physio")
                .then(Commands.literal("co")
                        .then(Commands.argument("dose", DoubleArgumentType.doubleArg(0, 1))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    float dose = (float) DoubleArgumentType.getDouble(context, "dose");
                                    player.setData(ModAttachments.CO_DOSE.get(), dose);
                                    return feedback(context, "CO dose set to " + percent(dose)
                                            + " (" + GasToxicity.CoStatus.classify(dose) + ")");
                                })))
                .then(Commands.literal("n2")
                        .then(Commands.argument("kpa", DoubleArgumentType.doubleArg(0, 200))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    float kpa = (float) DoubleArgumentType.getDouble(context, "kpa");
                                    player.setData(ModAttachments.TISSUE_N2.get(), kpa);
                                    return feedback(context, "Tissue N2 set to " + round(kpa) + " kPa");
                                })))
                .then(Commands.literal("status").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    int packed = player.getData(ModAttachments.VITALS);
                    Map<Ailment, Ailment.Severity> active = VitalSigns.unpack(packed);
                    if (active.isEmpty()) {
                        return feedback(context, "No active conditions");
                    }
                    active.forEach((ailment, severity) -> line(context.getSource(),
                            severity + ": " + ailment.displayName() + " — " + ailment.remedy(),
                            ChatFormatting.YELLOW));
                    return 1;
                }))
                .then(Commands.literal("reserve")
                        .then(Commands.argument("fraction", DoubleArgumentType.doubleArg(0, 1))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    float value = (float) DoubleArgumentType.getDouble(context, "fraction");
                                    player.setData(ModAttachments.O2_RESERVE.get(), value);
                                    return feedback(context, "Body O2 reserve set to " + percent(value)
                                            + " (" + Consciousness.classify(value) + ")");
                                })))
                .then(Commands.literal("tuc").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    line(source, "Time of useful consciousness by inspired ppO2",
                            ChatFormatting.AQUA);
                    for (double ppO2 : new double[] {0, 4, 8, 12, 15, 16}) {
                        double seconds = Consciousness.usefulConsciousnessSeconds(ppO2);
                        line(source, String.format("  %4.0f kPa  %s", ppO2,
                                        Double.isInfinite(seconds) ? "indefinite"
                                                : String.format("%.0f s", seconds)),
                                ChatFormatting.GRAY);
                    }
                    return 1;
                }))
                .then(Commands.literal("clear").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.setData(ModAttachments.CO_DOSE.get(), 0f);
                    player.setData(ModAttachments.TISSUE_N2.get(), 0f);
                    player.setData(ModAttachments.O2_RESERVE.get(), 1f);
                    player.setData(ModAttachments.VITALS.get(), 0);
                    return feedback(context, "Physiology cleared: no CO burden, no dissolved nitrogen");
                }));
    }

    /**
     * Inspecting the ore chain, per the debug-command rule.
     *
     * <p>The mechanical tier's whole point is a trade-off between recovery and grade,
     * and that trade is invisible unless you can sweep the crusher setting and see
     * both numbers move. Doing that by mining and cranking would take an hour per
     * data point; {@code /astronima ore sweep} prints the entire curve at once.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> oreCommands() {
        return Commands.literal("ore")
                .then(Commands.literal("assay")
                        .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (OreGrade grade : OreGrade.values()) {
                                        builder.suggest(grade.name().toLowerCase(Locale.ROOT));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(AstronimaCommand::assayOre)))
                .then(Commands.literal("sweep")
                        .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (OreGrade grade : OreGrade.values()) {
                                        builder.suggest(grade.name().toLowerCase(Locale.ROOT));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(AstronimaCommand::sweepCrusher)));
    }

    /**
     * Reading a spectrum, per the debug-command rule — the honest first consumer of {@code
     * sim/magic} (rule 13), the way {@code /astronima wire} was for the electrical tier: real
     * questions through the real API before an instrument exists to ask them prettily.
     *
     * <p>{@code /astronima magic spectrum <target>} reads a named target at cabin pressure;
     * {@code ... <target> <vacuum_kpa>} lets a tester actually find the boundary where the
     * forbidden [O III] line appears, which is the entire point of it existing
     * (design/astra-incognita.md §6, §8.1).
     */
    private static LiteralArgumentBuilder<CommandSourceStack> magicCommands() {
        return Commands.literal("magic")
                .then(Commands.literal("spectrum")
                        .then(targetArgument()
                                .executes(context -> reportSpectrum(context, GasMixture.EARTH_PRESSURE_KPA))
                                .then(Commands.argument("vacuum_kpa",
                                                DoubleArgumentType.doubleArg(0, 200))
                                        .executes(context -> reportSpectrum(context,
                                                DoubleArgumentType.getDouble(context, "vacuum_kpa"))))))
                .then(Commands.literal("coherence").executes(AstronimaCommand::reportCoherence))
                .then(Commands.literal("decode")
                        .then(targetArgument()
                                .then(Commands.argument("candidate_nm", DoubleArgumentType.doubleArg())
                                        .then(filterLineArgument()
                                                .executes(AstronimaCommand::reportDecode)))))
                .then(Commands.literal("decoys").then(targetArgument().executes(AstronimaCommand::reportDecoys)))
                .then(Commands.literal("astra")
                        .then(Commands.literal("here").executes(AstronimaCommand::reportAstraHere))
                        .then(Commands.literal("sweep").executes(AstronimaCommand::reportAstraSweep))
                        .then(Commands.literal("sounding").executes(AstronimaCommand::reportAstraSounding))
                        .then(Commands.literal("ritual")
                                .then(Commands.argument("arm_count", IntegerArgumentType.integer(1, 16))
                                        .then(Commands.argument("baseline_density",
                                                        DoubleArgumentType.doubleArg(0.0, 2.0))
                                                .executes(AstronimaCommand::reportAstraRitual))))
                        .then(Commands.argument("baseline_density", DoubleArgumentType.doubleArg(0.0, 1.0))
                                .executes(AstronimaCommand::reportAstra)));
    }

    /**
     * {@code sim/astra/AstraField}'s own honest first consumer (rule 13) - the first piece of
     * design/astra-core.md's frame to become real code, the same standing every other sim model
     * here is given before the block that owns it exists. No per-chunk field storage exists yet
     * (a separate, larger leaf per that document's own §2.4/§14), so this demonstrates the pure
     * model against a hypothetical site at the density the caller names, exactly the shape
     * {@code /astronima research reachable} already uses for "from an empty world" rather than
     * this player's own real state.
     */
    private static int reportAstra(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double baseline = DoubleArgumentType.getDouble(context, "baseline_density");
        line(source, String.format(Locale.ROOT,
                "Astra field - a hypothetical site at baseline density %.2f", baseline),
                ChatFormatting.AQUA);

        double density = baseline;
        for (int sweepNumber = 1; sweepNumber <= 3; sweepNumber++) {
            double collected = play.xponer.astronima.sim.astra.AstraField.swept(density);
            density = play.xponer.astronima.sim.astra.AstraField.densityAfterSweep(density);
            boolean burnt = play.xponer.astronima.sim.astra.AstraField.isBurnt(density, baseline);
            line(source, String.format(Locale.ROOT,
                    "  sweep %d: collected %.3f, ground now %.3f%s",
                    sweepNumber, collected, density, burnt ? " (burnt)" : ""),
                    burnt ? ChatFormatting.RED : ChatFormatting.WHITE);
        }

        line(source, "  refill after leaving it alone:", ChatFormatting.GRAY);
        for (double minutes : new double[] {1.0, 10.0, 60.0}) {
            double refilled = play.xponer.astronima.sim.astra.AstraField.refilled(density, baseline, minutes * 60.0);
            line(source, String.format(Locale.ROOT, "    +%.0f min: %.3f", minutes, refilled),
                    ChatFormatting.GRAY);
        }
        return 1;
    }

    /**
     * {@code AstraFieldGeometry.baselineDensity}'s own honest first consumer (rule 13) - design/
     * astra-field-live.md §1's real door: the player's own position, reduced to
     * {@code AsteroidBody.shellCoordinate} exactly the way {@code AsteroidChunkGenerator} already
     * reduces it for ore (rule 46), and a flare intensity read through
     * {@code SkyEventOverride.resolveFlareIntensity} - the same call {@code /astronima sky}
     * already uses - so a forced test flare ({@code /astronima sky flare <0-1>}) moves this
     * reading too, not a second, disagreeing copy of "what flare is happening right now."
     *
     * <p>design/astra-extraction-loop.md §1.4: now reports the field's real, depletion-aware
     * density too — {@link AstraFieldStorage#densityAt}, the same query {@code sweep} below
     * mutates — alongside the plain baseline, so a player (or a tester) can see the two agree
     * exactly on ground nobody has ever swept.
     */
    private static int reportAstraHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Vec3 pos = player.position();

        double r = Math.hypot(pos.x, pos.z);
        double shellCoordinate = AsteroidBody.shellCoordinate(r, pos.y);
        boolean insideBody = AsteroidBody.isInside(r);
        long flareSeed = 20260810L;
        double flareIntensity = SkyEventOverride.resolveFlareIntensity(level.getGameTime(), flareSeed);
        double baseline = AstraFieldGeometry.baselineDensity(shellCoordinate, flareIntensity);
        double real = play.xponer.astronima.astra.AstraFieldStorage.get(level)
                .densityAt(player.getBlockX(), player.getBlockY(), player.getBlockZ(), baseline, level.getGameTime());

        line(source, String.format(Locale.ROOT,
                        "Astra field here - r=%.1f y=%.1f shellCoordinate=%.3f%s",
                        r, pos.y, shellCoordinate, insideBody ? "" : " (past the rim)"),
                ChatFormatting.AQUA);
        line(source, String.format(Locale.ROOT,
                        "  baseline density %.3f (flare intensity %.2f)", baseline, flareIntensity),
                ChatFormatting.WHITE);
        line(source, String.format(Locale.ROOT, "  real density here (depletion included) %.3f", real),
                real < baseline ? ChatFormatting.RED : ChatFormatting.WHITE);
        return 1;
    }

    /**
     * design/astra-extraction-loop.md §1.4's second half — the honest first consumer of {@link
     * AstraFieldStorage#sweep}, before either the meter or the collector exists. Sweeps the real
     * cell at the player's real position and reports exactly what {@code reportAstraHere} would
     * now read there.
     */
    private static int reportAstraSweep(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Vec3 pos = player.position();

        double r = Math.hypot(pos.x, pos.z);
        double shellCoordinate = AsteroidBody.shellCoordinate(r, pos.y);
        double flareIntensity = SkyEventOverride.resolveFlareIntensity(level.getGameTime(), 20260810L);
        double baseline = AstraFieldGeometry.baselineDensity(shellCoordinate, flareIntensity);

        double collected = play.xponer.astronima.astra.AstraFieldStorage.get(level)
                .sweep(player.getBlockX(), player.getBlockY(), player.getBlockZ(), baseline, level.getGameTime());

        line(source, String.format(Locale.ROOT, "Astra field swept here - collected %.3f", collected),
                collected > 0.0 ? ChatFormatting.AQUA : ChatFormatting.RED);
        return 1;
    }

    /**
     * {@code sim/astra/CrustSounding}'s own honest first consumer (rule 13) — design/
     * astra-crust-sounding.md §2's real column scan, read straight off the real, already-placed
     * world (astra-systems.md §12's own "reports those minerals and nothing else"), not a
     * re-rolled approximation of worldgen's own noise. Shares {@code astra.CrustColumn}'s own real
     * scan with {@code item.AstraSounderItem} (rule 46) — the same door the mechanic's own
     * instrument uses now, not a stand-in this method invents on its own.
     */
    private static int reportAstraSounding(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        BlockPos feet = player.blockPosition();

        final int soundingDepthBlocks = play.xponer.astronima.astra.CrustColumn.SOUNDING_DEPTH_BLOCKS;
        play.xponer.astronima.sim.ore.OreBody column =
                play.xponer.astronima.astra.CrustColumn.scanBelow(level, feet);

        double r = Math.hypot(player.getX(), player.getZ());
        double shellCoordinate = AsteroidBody.shellCoordinate(r, player.getY());
        play.xponer.astronima.sim.astra.CrustSounding.Signature signature =
                play.xponer.astronima.sim.astra.CrustSounding.signatureOf(column, shellCoordinate);

        line(source, String.format(Locale.ROOT,
                        "Crust sounding - %d blocks beneath, shellCoordinate=%.3f", soundingDepthBlocks,
                        shellCoordinate),
                ChatFormatting.AQUA);
        boolean anyBand = false;
        for (play.xponer.astronima.sim.ore.Mineral mineral
                : play.xponer.astronima.sim.astra.CrustSounding.namedBands()) {
            double fraction = signature.fractionOf(mineral);
            if (fraction > 0.01) {
                line(source, String.format(Locale.ROOT, "  %s: %.2f",
                                mineral.displayName(), fraction), ChatFormatting.WHITE);
                anyBand = true;
            }
        }
        if (signature.unaccountedFraction() > 0.01) {
            line(source, String.format(Locale.ROOT, "  ??? (unaccounted): %.2f",
                            signature.unaccountedFraction()), ChatFormatting.LIGHT_PURPLE);
            anyBand = true;
        }
        if (!anyBand) {
            line(source, "  nothing recognisable in range", ChatFormatting.GRAY);
        }
        return 1;
    }

    /**
     * {@code sim/astra/AstraRitual}'s own honest first consumer (rule 13) — design/
     * astra-ritual-grammar.md's own mechanic, before any block, structure detection or item cost
     * exists. Simulates a hypothetical figure of {@code arm_count} arms, each tapping a real point
     * at the player's own real position and {@code baseline_density} — matching
     * {@code /astronima magic astra <baseline_density>}'s own "hypothetical site" shape rather
     * than this player's real field state, since no real arm-tip positions exist to read yet.
     */
    private static int reportAstraRitual(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int armCount = IntegerArgumentType.getInteger(context, "arm_count");
        double baseline = DoubleArgumentType.getDouble(context, "baseline_density");

        java.util.List<play.xponer.astronima.sim.astra.AstraRitual.TapPoint> tapPoints =
                new java.util.ArrayList<>();
        for (int i = 0; i < armCount; i++) {
            tapPoints.add(new play.xponer.astronima.sim.astra.AstraRitual.TapPoint(baseline, baseline));
        }

        play.xponer.astronima.sim.astra.AstraRitual.Outcome outcome =
                play.xponer.astronima.sim.astra.AstraRitual.run(tapPoints, armCount);

        line(source, String.format(Locale.ROOT,
                        "Ritual - %d arms, each tapping density %.2f", armCount, baseline),
                ChatFormatting.AQUA);
        if (outcome.completed()) {
            int yielded = play.xponer.astronima.sim.astra.AstraPrecipitation.itemsYielded(outcome.totalDrawn());
            line(source, String.format(Locale.ROOT,
                            "  completed in %.0fs - drew %.3f total - yields %d asterium_grains",
                            outcome.elapsedSeconds(), outcome.totalDrawn(), yielded),
                    ChatFormatting.LIGHT_PURPLE);
        } else {
            line(source, String.format(Locale.ROOT,
                            "  STALLED after %.0fs - the site could not sustain the draw",
                            outcome.elapsedSeconds()),
                    ChatFormatting.RED);
        }
        return 1;
    }

    /**
     * Reading a spectrum for real, per the debug-command rule — the honest first consumer of
     * {@code Spectrum.decode} (rule 13), the same shape as {@code /astronima magic spectrum}.
     * Read-only on purpose: it reports the outcome without touching {@code ResearchState}, the
     * same split {@code /astronima research identify} already makes between "read the sim" and
     * "force the state" — the real, earned write only happens through
     * {@code SpectrumDecodeAttemptPayload}, in play.
     */
    private static int reportDecode(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ObservationTarget target = parseTarget(context);
        double candidateNm = DoubleArgumentType.getDouble(context, "candidate_nm");
        SpectralLine filter = parseFilterLine(context);

        Spectrum.DecodeResult result = Spectrum.decode(target, candidateNm, filter);
        String verdict = switch (result.outcome()) {
            case NOT_ABOVE_NOISE -> "not above noise";
            case WRONG_FILTER -> "a real peak, but not this filter's colour";
            case MATCH -> "match: " + result.matchedLine().displayName();
        };
        line(source, target.name().toLowerCase(Locale.ROOT) + " @ " + round(candidateNm)
                        + " nm with " + filter.displayName() + " -> " + verdict,
                result.outcome() == Spectrum.DecodeOutcome.MATCH
                        ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        return 1;
    }

    /** Dumps a target's own decoy bumps, per the debug-command rule — otherwise nothing outside
     *  {@code SpectrumTest} could ever see what a given object's strip actually looks like. */
    private static int reportDecoys(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ObservationTarget target = parseTarget(context);
        line(source, target.name().toLowerCase(Locale.ROOT) + " decoys:", ChatFormatting.AQUA);
        for (Spectrum.NoiseBump bump : Spectrum.decoys(target)) {
            line(source, String.format(Locale.ROOT, "  %.1f nm, width %.1f, height %.2f",
                    bump.centerNm(), bump.widthNm(), bump.heightFrac()), ChatFormatting.GRAY);
        }
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            filterLineArgument() {
        return Commands.argument("filter_line", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (SpectralLine line : SpectralLine.values()) {
                        builder.suggest(line.name().toLowerCase(Locale.ROOT));
                    }
                    return builder.buildFuture();
                });
    }

    private static SpectralLine parseFilterLine(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "filter_line");
        for (SpectralLine line : SpectralLine.values()) {
            if (line.name().equalsIgnoreCase(name)) {
                return line;
            }
        }
        throw UNKNOWN_LINE.create();
    }

    /**
     * Reading the site, per the debug-command rule — the honest first consumer of
     * {@code sim/magic/Coherence} (rule 13), the same shape as {@code /astronima magic spectrum}.
     *
     * <p>Reads real room state at the player's own position: {@code Atmosphere.readingNear} for
     * temperature and pressure (the same reading barotrauma and the retort already trust) and
     * {@code Atmosphere.noiseDbAt} for the vibration term (the same number {@code
     * Acoustics.blocksRest} reads to stop sleep). Radiation dose and field exposure report zero
     * honestly — nothing in the game emits either yet, so those two bars are named but always
     * read as no limit, rather than being left off the list.
     *
     * <p>Prints the reading twice: once with the player counted as present (the true answer to
     * "what would a run see right now"), and once without (the site's own ceiling) — because the
     * entire point of {@link play.xponer.astronima.sim.magic.Coherence.Term#OBSERVER} is that
     * those two numbers are supposed to be very different, and a player has to see both to
     * believe it.
     */
    private static int reportCoherence(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        BlockPos pos = player.blockPosition();

        play.xponer.astronima.atmosphere.Atmosphere atmosphere =
                play.xponer.astronima.atmosphere.Atmosphere.get(level);
        var reading = atmosphere.readingNear(pos);
        double temperatureK = reading != null ? reading.state().temperatureK() : 0.0;
        double pressureKPa = reading != null ? reading.state().pressureKPa() : 0.0;
        double noiseDb = atmosphere.noiseDbAt(pos);

        line(source, String.format(Locale.ROOT,
                        "Coherence survey - %.0f K, %.0f kPa, %.0f dB",
                        temperatureK, pressureKPa, noiseDb),
                ChatFormatting.AQUA);

        reportCoherenceFor(source, "with you standing here", level, pos, true);
        reportCoherenceFor(source, "if you stepped out", level, pos, false);
        return 1;
    }

    private static void reportCoherenceFor(CommandSourceStack source, String label,
                                           ServerLevel level, BlockPos pos, boolean observerPresent) {
        var breakdown = play.xponer.astronima.atmosphere.CoherenceReading.at(level, pos, observerPresent);
        double totalSeconds = play.xponer.astronima.sim.magic.Coherence.totalSeconds(breakdown);
        line(source, "  " + label + ": " + play.xponer.astronima.sim.magic.Coherence.formatSeconds(totalSeconds),
                ChatFormatting.WHITE);
        for (var term : play.xponer.astronima.sim.magic.Coherence.worstFirst(breakdown)) {
            line(source, String.format(Locale.ROOT, "    %-10s %s",
                            term.name().toLowerCase(Locale.ROOT),
                            play.xponer.astronima.sim.magic.Coherence.formatSeconds(breakdown.get(term))),
                    ChatFormatting.GRAY);
        }
    }

    /**
     * Reads the sky's own live celestial attributes, per the debug-command rule — the tool that
     * would have caught the Moon's own bug in one look rather than a screenshot round-trip: this
     * engine's own day timeline (design/sky.md) holds {@code MOON_ANGLE} at a constant offset
     * from {@code SUN_ANGLE} (confirmed by printing both, not guessed), so a phase computed from
     * their relative geometry always reads as full — reported back as "the Moon is just white no
     * matter the time of day", and true for exactly that reason. {@code MOON_PHASE} is the real,
     * separately-varying value the Moon's own shading was rebuilt to read instead.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> skyCommands() {
        return Commands.literal("sky")
                .executes(AstronimaCommand::reportSky)
                .then(Commands.literal("flare")
                        .then(Commands.argument("intensity", DoubleArgumentType.doubleArg(0.0, 1.0))
                                .executes(AstronimaCommand::forceFlareIntensity))
                        .then(Commands.literal("clear").executes(AstronimaCommand::clearFlareOverride)))
                .then(Commands.literal("occultation")
                        .then(Commands.argument("depth",
                                        DoubleArgumentType.doubleArg(0.0, OccultationSchedule.MAX_DEPTH))
                                .executes(AstronimaCommand::forceOccultationDepth))
                        .then(Commands.literal("clear").executes(AstronimaCommand::clearOccultationOverride)))
                .then(Commands.literal("impact")
                        .then(Commands.literal("now").executes(AstronimaCommand::triggerImpactNow)))
                .then(Commands.literal("passing")
                        .then(Commands.literal("now").executes(AstronimaCommand::triggerPassingBodyNow)))
                .then(Commands.literal("comet")
                        .then(Commands.literal("now").executes(AstronimaCommand::triggerCometNow))
                        .then(Commands.literal("clear").executes(AstronimaCommand::clearCometOverride)))
                .then(Commands.literal("supernova")
                        .then(Commands.literal("now").executes(AstronimaCommand::triggerSupernovaNow))
                        .then(Commands.literal("clear").executes(AstronimaCommand::clearSupernovaOverride)));
    }

    /**
     * The impact flash is a one-tick pulse, not a held value (unlike the flare/occultation) — so
     * "force it" means "make one happen right now" rather than overriding a continuous state.
     * {@link play.xponer.astronima.sky.ImpactFlashEvents#triggerNear} is the exact same code path
     * the real schedule fires, called directly against the commanding player.
     */
    private static int triggerImpactNow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        play.xponer.astronima.sky.ImpactFlashEvents.triggerNear(level, player);
        return feedback(context, "Impact flash triggered near you");
    }

    /**
     * A passing body is a held multi-second event, not a one-tick pulse like the impact flash —
     * "force it" means "start a real pass right now," resolved through {@link
     * SkyEventOverride#triggerPassingBodyNow} so it runs out its own genuine
     * {@link PassingBodySchedule#DURATION_TICKS} window exactly like a natural one would, rather
     * than freezing on a single forced frame the way the occultation's first, since-abandoned
     * design did.
     */
    private static int triggerPassingBodyNow(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        SkyEventOverride.triggerPassingBodyNow(level.getGameTime());
        return feedback(context, "Passing body pass triggered - watch the sky for the next "
                + PassingBodySchedule.DURATION_TICKS + " ticks");
    }

    /**
     * The real schedule's own apparition runs {@link CometSchedule#DURATION_TICKS} — a little over
     * two real weeks, not a realistic thing to sit and watch for a test. This plays back the
     * identical grow/hold/fade shape compressed into {@link CometSchedule#PREVIEW_DURATION_TICKS}
     * (twenty real seconds) instead, via {@link SkyEventOverride#triggerCometNow}.
     */
    private static int triggerCometNow(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        SkyEventOverride.triggerCometNow(level.getGameTime());
        return feedback(context, "Comet preview triggered - a compressed grow/hold/fade over the next "
                + CometSchedule.PREVIEW_DURATION_TICKS + " ticks (astronima sky comet clear to cancel it early)");
    }

    private static int clearCometOverride(CommandContext<CommandSourceStack> context) {
        SkyEventOverride.clearCometOverride();
        return feedback(context, "Comet preview cleared - back to the real schedule");
    }

    /**
     * The real schedule's own supernova runs {@link SupernovaSchedule#DURATION_TICKS} — over six
     * real months, further still from realistic to sit and watch than even the comet's own real
     * schedule. Plays back the identical rise/plateau/decline shape compressed into {@link
     * SupernovaSchedule#PREVIEW_DURATION_TICKS} (twenty real seconds) instead.
     */
    private static int triggerSupernovaNow(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        SkyEventOverride.triggerSupernovaNow(level.getGameTime());
        return feedback(context, "Supernova preview triggered - a compressed rise/plateau/decline over the next "
                + SupernovaSchedule.PREVIEW_DURATION_TICKS
                + " ticks (astronima sky supernova clear to cancel it early)");
    }

    private static int clearSupernovaOverride(CommandContext<CommandSourceStack> context) {
        SkyEventOverride.clearSupernovaOverride();
        return feedback(context, "Supernova preview cleared - back to the real schedule");
    }

    /**
     * The debug-command rule's other half — not just reading the sky's scheduled events but
     * forcing them, since {@link FlareSchedule#PERIOD_TICKS} and
     * {@link OccultationSchedule#PERIOD_TICKS} make waiting for a real one impractical to test
     * against. Resolved through {@link SkyEventOverride} rather than a command-local field, so
     * the forced value is visible to both the renderer and (for occultation)
     * {@code SkyExposure}'s own mechanical dimming — a test override that only changed the
     * picture, not the retort's own reading, would be exactly the kind of two-copies-that-can-
     * disagree bug rule 46 exists to prevent.
     */
    private static int forceFlareIntensity(CommandContext<CommandSourceStack> context) {
        double intensity = DoubleArgumentType.getDouble(context, "intensity");
        SkyEventOverride.setFlareIntensity(intensity);
        return feedback(context, String.format(Locale.ROOT,
                "Flare intensity forced to %.2f (astronima sky flare clear to release)", intensity));
    }

    private static int clearFlareOverride(CommandContext<CommandSourceStack> context) {
        SkyEventOverride.clearFlareOverride();
        return feedback(context, "Flare override cleared - back to the real schedule");
    }

    private static int forceOccultationDepth(CommandContext<CommandSourceStack> context) {
        double depth = DoubleArgumentType.getDouble(context, "depth");
        SkyEventOverride.setOccultationDepth(depth);
        return feedback(context, String.format(Locale.ROOT,
                "Occultation depth forced to %.2f - the retort's own sunlight() will read this too "
                        + "(astronima sky occultation clear to release)", depth));
    }

    private static int clearOccultationOverride(CommandContext<CommandSourceStack> context) {
        SkyEventOverride.clearOccultationOverride();
        return feedback(context, "Occultation override cleared - back to the real schedule");
    }

    private static int reportSky(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Vec3 pos = player.position();
        var attributes = level.environmentAttributes();

        float sunAngle = attributes.getValue(EnvironmentAttributes.SUN_ANGLE, pos, null);
        float moonAngle = attributes.getValue(EnvironmentAttributes.MOON_ANGLE, pos, null);
        float starAngle = attributes.getValue(EnvironmentAttributes.STAR_ANGLE, pos, null);
        float starBrightness = attributes.getValue(EnvironmentAttributes.STAR_BRIGHTNESS, pos, null);
        MoonPhase attributePhase = attributes.getValue(EnvironmentAttributes.MOON_PHASE, pos, null);
        int renderedPhaseIndex = MoonPhaseLighting.phaseIndexForSunAngle(sunAngle);

        line(source, String.format(Locale.ROOT, "Sky - day %d, tick %d",
                        level.getGameTime() / 24000L, level.getGameTime() % 24000L),
                ChatFormatting.AQUA);
        line(source, String.format(Locale.ROOT, "  sun_angle %.1f  moon_angle %.1f  star_angle %.1f",
                        sunAngle, moonAngle, starAngle),
                ChatFormatting.WHITE);
        // Printed side by side on purpose: attribute_phase is the real MOON_PHASE attribute, which
        // this dimension's own timeline never writes (design/sky.md's own account of finding this)
        // and will always read full_moon here; rendered_phase_index is what the Moon on screen
        // actually shows, now paced against sun_angle so it moves the instant /time set does. If
        // these two are ever meant to agree again, this line is what will make that obvious.
        line(source, String.format(Locale.ROOT, "  moon_phase(attribute, unused) %s | moon_phase(rendered) index %d of %d",
                        attributePhase.getSerializedName(), renderedPhaseIndex,
                        MoonPhaseLighting.PHASE_COUNT),
                ChatFormatting.WHITE);
        line(source, String.format(Locale.ROOT, "  star_brightness %.2f", starBrightness),
                ChatFormatting.GRAY);

        // design/sky.md §S5.1: paced against real elapsed gameTime, deliberately independent of
        // /time set (a flare is a real-time hazard warning, not a day-cycle cosmetic) - printed
        // here specifically so that independence doesn't read as "stuck" the way the Moon's own
        // gameTime-paced attempt did before this file existed to show the difference. Resolved
        // through SkyEventOverride, not FlareSchedule directly, so this line shows a forced test
        // value exactly the same way the sky itself does.
        long flareSeed = 20260810L;
        double flareIntensity = SkyEventOverride.resolveFlareIntensity(level.getGameTime(), flareSeed);
        long ticksUntilNextFlare = FlareSchedule.nextFlareStart(level.getGameTime(), flareSeed) - level.getGameTime();
        line(source, String.format(Locale.ROOT,
                        "  flare intensity %.2f (paced by gameTime, not /time set) | next in %d ticks"
                                + " | astronima sky flare <0-1> to force, ...flare clear to release",
                        flareIntensity, ticksUntilNextFlare),
                ChatFormatting.GRAY);

        long occultationSeed = 20260811L;
        double occultationDepth = SkyEventOverride.resolveOccultationDepth(level.getGameTime(), occultationSeed);
        long ticksUntilNextTransit = OccultationSchedule
                .nextTransitStart(level.getGameTime(), occultationSeed) - level.getGameTime();
        line(source, String.format(Locale.ROOT,
                        "  occultation depth %.2f (of max %.2f) | next in %d ticks"
                                + " | astronima sky occultation <0-%.2f> to force, ...occultation clear to release",
                        occultationDepth, OccultationSchedule.MAX_DEPTH, ticksUntilNextTransit,
                        OccultationSchedule.MAX_DEPTH),
                ChatFormatting.GRAY);

        long impactSeed = 20260812L;
        long ticksUntilNextImpact =
                play.xponer.astronima.sim.sky.ImpactSchedule.nextImpactTick(level.getGameTime(), impactSeed)
                        - level.getGameTime();
        line(source, String.format(Locale.ROOT,
                        "  next impact flash in %d ticks | astronima sky impact now to trigger one immediately",
                        ticksUntilNextImpact),
                ChatFormatting.GRAY);

        long passingBodySeed = 20260813L;
        long activePassingStart =
                SkyEventOverride.resolvePassingBodyActiveStart(level.getGameTime(), passingBodySeed);
        String passingStatus = activePassingStart >= 0
                ? String.format(Locale.ROOT, "ACTIVE now (%d/%d ticks in)",
                        level.getGameTime() - activePassingStart, PassingBodySchedule.DURATION_TICKS)
                : "next in " + (PassingBodySchedule.nextEventStart(level.getGameTime(), passingBodySeed)
                        - level.getGameTime()) + " ticks";
        line(source, String.format(Locale.ROOT,
                        "  passing body %s | astronima sky passing now to trigger one immediately",
                        passingStatus),
                ChatFormatting.GRAY);

        long cometSeed = 20260814L;
        long activeCometStart = SkyEventOverride.resolveCometActiveStart(level.getGameTime(), cometSeed);
        String cometStatus;
        if (activeCometStart >= 0) {
            long elapsed = level.getGameTime() - activeCometStart;
            boolean preview = SkyEventOverride.isCometPreviewActive(level.getGameTime());
            double growth = preview
                    ? CometSchedule.previewGrowthForElapsed(elapsed)
                    : CometSchedule.growthForElapsedTicks(elapsed);
            cometStatus = String.format(Locale.ROOT, "%s growth %.2f (%d/%d ticks in)",
                    preview ? "PREVIEW" : "ACTIVE", growth, elapsed,
                    preview ? CometSchedule.PREVIEW_DURATION_TICKS : CometSchedule.DURATION_TICKS);
        } else {
            cometStatus = "next in "
                    + (CometSchedule.nextEventStart(level.getGameTime(), cometSeed) - level.getGameTime())
                    + " ticks";
        }
        line(source, String.format(Locale.ROOT,
                        "  comet %s | astronima sky comet now for a compressed preview, ...comet clear to cancel",
                        cometStatus),
                ChatFormatting.GRAY);

        long supernovaSeed = 20260815L;
        long activeSupernovaStart = SkyEventOverride.resolveSupernovaActiveStart(level.getGameTime(), supernovaSeed);
        String supernovaStatus;
        if (activeSupernovaStart >= 0) {
            long elapsed = level.getGameTime() - activeSupernovaStart;
            boolean preview = SkyEventOverride.isSupernovaPreviewActive(level.getGameTime());
            double growth = preview
                    ? SupernovaSchedule.previewGrowthForElapsed(elapsed)
                    : SupernovaSchedule.growthForElapsedTicks(elapsed);
            supernovaStatus = String.format(Locale.ROOT, "%s growth %.2f (%d/%d ticks in)",
                    preview ? "PREVIEW" : "ACTIVE", growth, elapsed,
                    preview ? SupernovaSchedule.PREVIEW_DURATION_TICKS : SupernovaSchedule.DURATION_TICKS);
        } else {
            supernovaStatus = "next in "
                    + (SupernovaSchedule.nextEventStart(level.getGameTime(), supernovaSeed) - level.getGameTime())
                    + " ticks";
        }
        line(source, String.format(Locale.ROOT,
                        "  supernova %s | astronima sky supernova now for a compressed preview, "
                                + "...supernova clear to cancel, magic spectrum supernova to read it",
                        supernovaStatus),
                ChatFormatting.GRAY);
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            targetArgument() {
        return Commands.argument("target", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (ObservationTarget target : ObservationTarget.values()) {
                        builder.suggest(target.name().toLowerCase(Locale.ROOT));
                    }
                    // Not an ObservationTarget - it has no fixed temperature or line set to give
                    // one, which is the entire point of it (see reportSupernovaSpectrum's own
                    // comment) - suggested here anyway so it is discoverable the same way.
                    builder.suggest("supernova");
                    return builder.buildFuture();
                });
    }

    private static ObservationTarget parseTarget(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "target");
        for (ObservationTarget target : ObservationTarget.values()) {
            if (target.name().equalsIgnoreCase(name)) {
                return target;
            }
        }
        throw UNKNOWN_TARGET.create();
    }

    private static int reportSpectrum(CommandContext<CommandSourceStack> context, double vacuumKPa)
            throws CommandSyntaxException {
        if (StringArgumentType.getString(context, "target").equalsIgnoreCase("supernova")) {
            return reportSupernovaSpectrum(context, vacuumKPa);
        }
        CommandSourceStack source = context.getSource();
        ObservationTarget target = parseTarget(context);

        double peakNm = Spectrum.peakWavelengthNm(target.temperatureK());
        line(source, target.name().toLowerCase(Locale.ROOT) + " - "
                + (int) target.temperatureK() + " K, vacuum " + round(vacuumKPa) + " kPa",
                ChatFormatting.AQUA);
        line(source, Double.isInfinite(peakNm)
                        ? "  no continuum - cold and dark"
                        : String.format(Locale.ROOT, "  continuum peaks at %.1f nm", peakNm),
                ChatFormatting.WHITE);

        SortedSet<SpectralLine> captured = Spectrum.capture(
                target.temperatureK(), target.lines(), vacuumKPa);
        if (captured.isEmpty()) {
            line(source, "  no lines captured", ChatFormatting.GRAY);
            return 1;
        }
        for (SpectralLine line : captured) {
            line(source, String.format(Locale.ROOT, "  %6.1f nm  %s%s",
                            line.wavelengthNm(), line.name().toLowerCase(Locale.ROOT),
                            line.forbidden() ? "  [forbidden]" : ""),
                    line.forbidden() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE);
        }
        return 1;
    }

    /**
     * A supernova has no fixed {@link ObservationTarget} entry, and cannot honestly get one — every
     * other entry in that enum really is a fixed, permanent temperature and line set, and this is
     * the one target design/sky.md itself calls out as needing to change over time. Reads {@link
     * SupernovaSpectrum#temperatureKAt}/{@code linesAt} for whatever tick has actually elapsed
     * since the currently active (real or debug-preview) supernova began — through {@code
     * Spectrum.capture} exactly as every fixed target already is, so this is not a second, parallel
     * spectrum pipeline, just a time-varying source feeding the one real one.
     */
    private static int reportSupernovaSpectrum(CommandContext<CommandSourceStack> context, double vacuumKPa)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = context.getSource().getLevel();
        long supernovaSeed = 20260815L;
        long activeStart = SkyEventOverride.resolveSupernovaActiveStart(level.getGameTime(), supernovaSeed);
        if (activeStart < 0) {
            line(source, "supernova - not currently visible"
                            + " (astronima sky supernova now for a compressed preview)",
                    ChatFormatting.AQUA);
            return 1;
        }
        long elapsed = level.getGameTime() - activeStart;
        double temperatureK = SupernovaSpectrum.temperatureKAt(elapsed);
        Set<SpectralLine> presentLines = SupernovaSpectrum.linesAt(elapsed);

        double peakNm = Spectrum.peakWavelengthNm(temperatureK);
        line(source, String.format(Locale.ROOT, "supernova - %d K, vacuum %s kPa",
                        (int) temperatureK, round(vacuumKPa)),
                ChatFormatting.AQUA);
        line(source, String.format(Locale.ROOT, "  continuum peaks at %.1f nm", peakNm), ChatFormatting.WHITE);

        SortedSet<SpectralLine> captured = Spectrum.capture(temperatureK, presentLines, vacuumKPa);
        if (captured.isEmpty()) {
            line(source, "  no lines captured", ChatFormatting.GRAY);
            return 1;
        }
        for (SpectralLine line : captured) {
            line(source, String.format(Locale.ROOT, "  %6.1f nm  %s%s",
                            line.wavelengthNm(), line.name().toLowerCase(Locale.ROOT),
                            line.forbidden() ? "  [forbidden]" : ""),
                    line.forbidden() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE);
        }
        return 1;
    }

    /**
     * Sweeping the forge, per the debug-command rule.
     *
     * <p>Finding the work-hardening optimum by hammering costs a billet per data
     * point, so the sweep prints where each blow strength lands: how many blows to a
     * usable head, how much give is left, and whether it cracks.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> forgeCommands() {
        return Commands.literal("forge")
                .then(Commands.literal("sweep").executes(AstronimaCommand::sweepForge));
    }

    private static int sweepForge(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        line(source, "Cold forge - blow strength sweep", ChatFormatting.AQUA);
        line(source, "  blow   blows  hardness  give   quality  outcome", ChatFormatting.GRAY);

        for (double strength = 0.15; strength <= 1.0001; strength += 0.15) {
            ColdWorking.Piece piece = ColdWorking.Piece.fresh();
            int blows = 0;
            while (!piece.isUsable() && !piece.cracked() && blows < 200) {
                piece = ColdWorking.strike(piece, strength, ColdWorking.BASE_NICKEL);
                blows++;
            }
            line(source, String.format("  %4.0f%%  %5d  %7.2f  %5.2f  %6.0f%%  %s",
                            strength * 100, blows, piece.hardness(), piece.ductility(),
                            ColdWorking.quality(piece) * 100,
                            ColdWorking.verdict(piece)),
                    piece.cracked() ? ChatFormatting.RED : ChatFormatting.GRAY);
        }
        return 1;
    }

    /** What is actually in one block of this rock, mineral by mineral. */
    private static int assayOre(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        OreGrade grade = parseGrade(context);
        OreBody body = grade.body();

        line(source, grade.displayName() + " - " + Math.round(body.totalMass()) + " g per block",
                ChatFormatting.AQUA);
        for (Mineral mineral : Mineral.values()) {
            double mass = body.massOf(mineral);
            if (mass <= 0) {
                continue;
            }
            line(source, String.format("  %-12s %s  %4.1f%%  tier %s",
                            mineral.displayName(), mineral.formula(),
                            body.fractionOf(mineral) * 100, mineral.tier()),
                    mineral.tier() == Mineral.Tier.MECHANICAL
                            ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        }
        line(source, "Contained metal: " + Math.round(body.containedMetalGrams()) + " g",
                ChatFormatting.YELLOW);
        return 1;
    }

    /** The recovery-versus-grade curve, printed rather than discovered by grinding. */
    private static int sweepCrusher(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        OreGrade grade = parseGrade(context);
        line(source, grade.displayName() + " - crusher sweep", ChatFormatting.AQUA);
        line(source, "  setting  size    liberation  recovery  grade  turns", ChatFormatting.GRAY);

        for (double setting = 0.0; setting <= 1.0001; setting += 0.2) {
            double microns = Comminution.particleSizeMicrons(setting);
            double liberation = Comminution.liberation(microns);
            MagneticSeparation.Result result = MagneticSeparation.separate(
                    grade.body(), liberation, setting,
                    0.75);
            int turns = OreCrusherBlockEntity.BASE_WORK
                    + (int) Math.round(Comminution.workPerKilogram(microns) * 4);
            line(source, String.format("  %5.0f%%  %5.0f um  %6.0f%%    %6.0f%%  %5.0f%%  %4d",
                            setting * 100, microns, liberation * 100,
                            result.recovery() * 100, result.grade() * 100, turns),
                    ChatFormatting.GRAY);
        }
        return 1;
    }

    private static OreGrade parseGrade(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "grade");
        for (OreGrade grade : OreGrade.values()) {
            if (grade.name().equalsIgnoreCase(name)) {
                return grade;
            }
        }
        throw UNKNOWN_GRADE.create();
    }

    /**
     * Reading the centrifugal fluidized-bed reactor, per the debug-command rule.
     *
     * <p>The reactor's one dial is the drum speed, and how well it reduces depends on matching
     * that speed to how finely the ilmenite was ground — spin too slow and the bed blows out of
     * the exhaust, too fast and it packs to the wall. Finding that match by building the machine
     * and watching it fail is expensive, so the sweep prints, for a given grind, what each drum
     * speed does: the artificial gravity it makes, the regime the bed falls into, and how much of
     * a ten-mole charge is recovered as iron rather than lost out the vent.
     *
     * <p>This was the sim model's first consumer (rule 1: the physics before the block). The
     * reactor block ({@code FluidizedBedBlock}) now exists too and drives the same
     * {@code CentrifugalBed} → {@code IlmeniteReduction} path; this command stays as the readable
     * way to see the whole spin curve without building and re-spinning one. See
     * {@code design/fluidized-bed.md}.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> reductionCommands() {
        return Commands.literal("fluidbed")
                .then(Commands.literal("sweep")
                        .then(Commands.argument("grind_microns",
                                        DoubleArgumentType.doubleArg(Comminution.FINEST_MICRONS,
                                                Comminution.COARSEST_MICRONS))
                                .executes(AstronimaCommand::sweepReactor)))
                .then(Commands.literal("at")
                        .then(Commands.argument("grind_microns",
                                        DoubleArgumentType.doubleArg(Comminution.FINEST_MICRONS,
                                                Comminution.COARSEST_MICRONS))
                                .then(Commands.argument("rpm", DoubleArgumentType.doubleArg(1, 3000))
                                        .executes(AstronimaCommand::reactorAt))));
    }

    /** One operating point of the reactor: regime, contact, entrainment, iron recovered. */
    private static int reactorAt(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double grind = DoubleArgumentType.getDouble(context, "grind_microns");
        double rpm = DoubleArgumentType.getDouble(context, "rpm");

        CentrifugalBed.Regime regime = CentrifugalBed.regimeAt(grind, rpm);
        double gravityG = CentrifugalBed.artificialGravity(rpm) / 9.81;
        double recovered = recoveredIron(grind, rpm);

        line(source, String.format("Fluidized bed - %.0f um ground, %.0f rpm", grind, rpm),
                ChatFormatting.AQUA);
        line(source, String.format("  artificial gravity: %.2f g", gravityG), ChatFormatting.GRAY);
        line(source, "  bed regime: " + regimeLabel(regime), regimeColour(regime));
        line(source, String.format("  contact %.0f%%   entrainment %.0f%%",
                        CentrifugalBed.contactQuality(grind, rpm) * 100,
                        CentrifugalBed.entrainmentSeverity(grind, rpm) * 100),
                ChatFormatting.GRAY);
        line(source, String.format("  iron recovered: %.2f of 10 mol", recovered),
                recovered > 9 ? ChatFormatting.GREEN
                        : recovered > 1 ? ChatFormatting.YELLOW : ChatFormatting.RED);
        return 1;
    }

    /** The recovery-versus-spin curve at one grind, printed rather than found by building it. */
    private static int sweepReactor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double grind = DoubleArgumentType.getDouble(context, "grind_microns");

        line(source, String.format("Fluidized bed - %.0f um ground, drum-speed sweep", grind),
                ChatFormatting.AQUA);
        line(source, "   rpm   gravity   regime        contact  blowout  iron/10",
                ChatFormatting.GRAY);
        // Geometric steps across the whole crusher-to-drum interesting range: the window
        // slides with the grind, so the same sweep shows a coarse feed packing where a fine
        // one is still blowing out.
        for (double rpm : new double[] {2, 5, 10, 20, 50, 100, 200, 400, 800, 1600}) {
            CentrifugalBed.Regime regime = CentrifugalBed.regimeAt(grind, rpm);
            line(source, String.format("  %5.0f  %6.1fg   %-12s   %4.0f%%    %4.0f%%   %5.2f",
                            rpm, CentrifugalBed.artificialGravity(rpm) / 9.81,
                            regimeLabel(regime),
                            CentrifugalBed.contactQuality(grind, rpm) * 100,
                            CentrifugalBed.entrainmentSeverity(grind, rpm) * 100,
                            recoveredIron(grind, rpm)),
                    regimeColour(regime));
        }
        return 1;
    }

    /**
     * Runs a ten-mole charge through the real two-call machine path and returns the iron left
     * to tap, exactly as the reactor block will drive it.
     */
    private static double recoveredIron(double grindMicrons, double rpm) {
        double contact = CentrifugalBed.contactQuality(grindMicrons, rpm);
        double entrainment = CentrifugalBed.entrainmentSeverity(grindMicrons, rpm);
        IlmeniteReduction.Charge charge = IlmeniteReduction.Charge.ofIlmenite(10);
        for (int i = 0; i < 3000 && !charge.isReduced(); i++) {
            charge = IlmeniteReduction.step(charge, 1e9, contact, entrainment, 0.1).charge();
        }
        return charge.ironMol();
    }

    private static String regimeLabel(CentrifugalBed.Regime regime) {
        return switch (regime) {
            case PACKED -> "PACKED (fast)";
            case BOILING -> "BOILING";
            case BLOWING_OUT -> "BLOWING OUT";
        };
    }

    private static ChatFormatting regimeColour(CentrifugalBed.Regime regime) {
        return switch (regime) {
            case BOILING -> ChatFormatting.GREEN;
            case PACKED -> ChatFormatting.YELLOW;
            case BLOWING_OUT -> ChatFormatting.RED;
        };
    }

    /**
     * Selective laser sintering: the debug window onto the only two-dial machine, where the answer
     * is a pocket in a plane rather than a point on a line ({@code design/sls.md}). It is the sim
     * model's first consumer (rule 1: the physics before the block), and reading the plane in the
     * chat is far cheaper than finding it by printing porous parts one power-and-speed at a time.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> sinterCommands() {
        return Commands.literal("sinter")
                .then(Commands.literal("at")
                        .then(Commands.argument("power_w", DoubleArgumentType.doubleArg(
                                        LaserSintering.MIN_POWER_W, LaserSintering.MAX_POWER_W))
                                .then(Commands.argument("speed_mms",
                                                DoubleArgumentType.doubleArg(
                                                        LaserSintering.MIN_SPEED_MMS,
                                                        LaserSintering.MAX_SPEED_MMS))
                                        .executes(AstronimaCommand::sinterAt))))
                .then(Commands.literal("sweep")
                        .then(Commands.argument("power_w", DoubleArgumentType.doubleArg(
                                        LaserSintering.MIN_POWER_W, LaserSintering.MAX_POWER_W))
                                .executes(AstronimaCommand::sinterSweep)));
    }

    /** One operating point of the printer: energy density, regime, relative density, soundness. */
    private static int sinterAt(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double power = DoubleArgumentType.getDouble(context, "power_w");
        double speed = DoubleArgumentType.getDouble(context, "speed_mms");

        LaserSintering.Regime regime = LaserSintering.regime(power, speed);
        line(source, String.format("SLS printer - %.0f W, %.0f mm/s", power, speed),
                ChatFormatting.AQUA);
        line(source, String.format("  energy density: %.0f J/mm3",
                LaserSintering.energyDensity(power, speed)), ChatFormatting.GRAY);
        line(source, "  regime: " + sinterLabel(regime), sinterColour(regime));
        line(source, String.format("  relative density %.1f%%   track stability %.0f%%",
                        LaserSintering.relativeDensity(power, speed) * 100,
                        LaserSintering.trackStability(speed) * 100),
                ChatFormatting.GRAY);
        double soundness = LaserSintering.soundness(power, speed);
        line(source, String.format("  part soundness: %.1f%% %s", soundness * 100,
                        LaserSintering.isSound(power, speed) ? "(sound)" : "(would fail in use)"),
                LaserSintering.isSound(power, speed) ? ChatFormatting.GREEN
                        : soundness > 0.85 ? ChatFormatting.YELLOW : ChatFormatting.RED);
        return 1;
    }

    /**
     * The soundness-versus-speed curve at one laser power, printed rather than found by printing a
     * shelf of failed parts. Sweeping speed at fixed power walks a horizontal line across the
     * process plane, so the same power shows keyholing when slow and balling when fast.
     */
    private static int sinterSweep(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double power = DoubleArgumentType.getDouble(context, "power_w");

        line(source, String.format("SLS printer - %.0f W, scan-speed sweep", power),
                ChatFormatting.AQUA);
        line(source, "  mm/s    J/mm3   regime         density  sound", ChatFormatting.GRAY);
        for (double speed : new double[] {200, 400, 600, 900, 1200, 1500, 2000, 2600}) {
            LaserSintering.Regime regime = LaserSintering.regime(power, speed);
            line(source, String.format("  %5.0f  %6.0f   %-13s   %4.0f%%   %4.0f%%",
                            speed, LaserSintering.energyDensity(power, speed),
                            sinterLabel(regime),
                            LaserSintering.relativeDensity(power, speed) * 100,
                            LaserSintering.soundness(power, speed) * 100),
                    sinterColour(regime));
        }
        return 1;
    }

    private static String sinterLabel(LaserSintering.Regime regime) {
        return switch (regime) {
            case SOUND -> "SOUND";
            case LACK_OF_FUSION -> "LACK OF FUSION";
            case KEYHOLING -> "KEYHOLING";
            case BALLING -> "BALLING";
        };
    }

    private static ChatFormatting sinterColour(LaserSintering.Regime regime) {
        return switch (regime) {
            case SOUND -> ChatFormatting.GREEN;
            case LACK_OF_FUSION, BALLING -> ChatFormatting.RED;
            case KEYHOLING -> ChatFormatting.YELLOW;
        };
    }

    /**
     * Reading and setting the suit, per the debug-command rule: everything the life
     * support loop depends on can be inspected and forced from here.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> suitCommands() {
        return Commands.literal("suit")
                .then(Commands.literal("status").executes(AstronimaCommand::suitStatus))
                .then(Commands.literal("co2")
                        .then(Commands.argument("kpa", DoubleArgumentType.doubleArg(0, 20))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    float kpa = (float) DoubleArgumentType.getDouble(context, "kpa");
                                    player.setData(ModAttachments.HELMET_CO2.get(), kpa);
                                    return feedback(context, "Helmet CO2 set to " + round(kpa) + " kPa"
                                            + (HelmetAtmosphere.isDangerous(kpa) ? " (dangerous)" : ""));
                                })))
                .then(Commands.literal("wear")
                        .then(Commands.argument("subsystem", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (SuitSubsystem subsystem : SuitSubsystem.values()) {
                                        builder.suggest(subsystem.name().toLowerCase(Locale.ROOT));
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("life", IntegerArgumentType.integer(0, SuitWear.FULL))
                                        .executes(AstronimaCommand::setSuitWear))))
                .then(Commands.literal("repair")
                        .then(Commands.argument("subsystem", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (SuitSubsystem subsystem : SuitSubsystem.values()) {
                                        builder.suggest(subsystem.name().toLowerCase(Locale.ROOT));
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("quality", DoubleArgumentType.doubleArg(0, 1))
                                        .executes(AstronimaCommand::forceRepair))))
                .then(Commands.literal("vent").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.setData(ModAttachments.HELMET_CO2.get(), (float) HelmetAtmosphere.vented());
                    return feedback(context, "Helmet vented");
                }))
                .then(Commands.literal("drain")
                        .then(Commands.argument("fraction", DoubleArgumentType.doubleArg(0, 1))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    double left = DoubleArgumentType.getDouble(context, "fraction");
                                    ItemStack tank = SuitLoadout.tank(player);
                                    if (tank.isEmpty()) {
                                        throw NO_TANK.create();
                                    }
                                    tank.setDamageValue((int) Math.round(tank.getMaxDamage() * (1 - left)));
                                    return feedback(context, "Tank set to " + percent((float) left));
                                })));
    }

    private static int suitStatus(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        int condition = SuitLoadout.condition(player);

        line(source, "Suit: " + (SuitLoadout.suit(player).isEmpty() ? "none worn"
                : SuitCondition.isFullyRepaired(condition) ? "fully repaired"
                : "damaged"), ChatFormatting.AQUA);
        int wear = EvaSuitItem.wearOf(SuitLoadout.suit(player));
        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            boolean working = SuitCondition.isWorking(condition, subsystem);
            String life = working
                    ? " (" + percent(SuitWear.fraction(wear, subsystem)) + " life)"
                    : "";
            line(source, "  " + subsystem.displayName() + ": " + (working ? "ok" : "FAULT") + life,
                    working ? ChatFormatting.GRAY : ChatFormatting.RED);
        }
        line(source, "Holds pressure: " + SuitCondition.canHoldPressure(condition),
                ChatFormatting.GRAY);
        line(source, "Thermal exposure here: "
                        + ThermalProtection.classify(SuitLifeSupport.ambientCelsius(
                                Atmosphere.get(source.getLevel())
                                        .readingNear(player.blockPosition()))),
                ChatFormatting.GRAY);
        line(source, "Tank: " + fractionText(OxygenTanks.fittedTankFraction(player)),
                ChatFormatting.GRAY);
        line(source, "Cartridge: " + fractionText(OxygenTanks.fittedCartridgeFraction(player)),
                ChatFormatting.GRAY);

        float ppCo2 = player.getData(ModAttachments.HELMET_CO2);
        line(source, "Helmet CO2: " + round(ppCo2) + " kPa"
                + (HelmetAtmosphere.isDangerous(ppCo2) ? " — DANGEROUS" : ""),
                HelmetAtmosphere.isDangerous(ppCo2) ? ChatFormatting.RED : ChatFormatting.GRAY);
        return 1;
    }

    private static int setSuitWear(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SuitSubsystem subsystem = parseSubsystem(context);
        ItemStack suit = SuitLoadout.suit(player);
        if (suit.isEmpty()) {
            throw NO_SUIT.create();
        }
        int life = IntegerArgumentType.getInteger(context, "life");
        EvaSuitItem.setWear(suit, SuitWear.withLife(EvaSuitItem.wearOf(suit), subsystem, life));
        return feedback(context, subsystem.displayName() + " life set to " + life
                + "/" + SuitWear.FULL + (life == 0 ? " (now reads as failed)" : ""));
    }

    /** Skips the bench, so a repair outcome can be tested without playing it out. */
    private static int forceRepair(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SuitSubsystem subsystem = parseSubsystem(context);
        ItemStack suit = SuitLoadout.suit(player);
        if (suit.isEmpty()) {
            throw NO_SUIT.create();
        }
        float quality = (float) DoubleArgumentType.getDouble(context, "quality");
        EvaSuitItem.setWear(suit, SuitWear.withLife(EvaSuitItem.wearOf(suit), subsystem, 0));
        EvaSuitItem.setCondition(suit,
                SuitCondition.damage(EvaSuitItem.repairedMask(suit), subsystem));
        EvaSuitItem.repair(suit, subsystem, quality);
        return feedback(context, subsystem.displayName() + " repaired at quality "
                + percent(quality) + " - starting life "
                + SuitWear.startingLife(quality) + "/" + SuitWear.FULL);
    }

    private static SuitSubsystem parseSubsystem(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "subsystem");
        for (SuitSubsystem subsystem : SuitSubsystem.values()) {
            if (subsystem.name().equalsIgnoreCase(name)) {
                return subsystem;
            }
        }
        throw UNKNOWN_SUBSYSTEM.create();
    }

    private static String fractionText(float fraction) {
        return fraction < 0 ? "none fitted" : percent(fraction);
    }

    // ------------------------------------------------------------------ actions

    private static int roomInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Atmosphere atmosphere = Atmosphere.get(level);
        Atmosphere.RoomReading reading = atmosphere.readingNear(pos);
        if (reading == null) {
            throw NO_ROOM.create();
        }
        RoomState room = reading.state();

        line(source, "— Room #" + room.id() + " —", ChatFormatting.GOLD);
        line(source, "volume " + room.volumeBlocks() + " blocks | " + round(room.pressureKPa()) + " kPa | "
                + round(room.temperatureK() - 273.15) + " °C | " + round(room.gases().totalMoles()) + " mol",
                ChatFormatting.WHITE);
        line(source, "sealed=" + reading.sealed() + " openToSpace=" + reading.openToSpace()
                + " unsealable=" + reading.unsealableEnclosure(), ChatFormatting.GRAY);
        line(source, "humidity " + (int) Math.round(100 * Humidity.relativeHumidity(room)) + "%"
                + " | mold-favourable=" + Humidity.moldFavourable(room)
                + " | noise " + Math.round(atmosphere.noiseDbAt(pos)) + " dB", ChatFormatting.GRAY);
        line(source, "ignition: " + Flammability.assess(room), ChatFormatting.GRAY);
        for (Gas gas : Gas.values()) {
            double pp = room.partialPressureKPa(gas);
            if (pp > 0.0001) {
                line(source, String.format("  %-4s %8.3f kPa  %8.2f mol  %5.2f%%",
                        gas.symbol(), pp, room.gases().get(gas), 100 * room.gases().fraction(gas)),
                        ChatFormatting.AQUA);
            }
        }
        return 1;
    }

    private static int rescan(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Atmosphere.get(source.getLevel()).invalidate(pos);
        return feedback(context, "Room geometry invalidated; it will be re-scanned on next access");
    }

    private static int changeGas(CommandContext<CommandSourceStack> context, boolean add)
            throws CommandSyntaxException {
        RoomState room = requireRoom(context);
        Gas gas = parseGas(context);
        double mol = DoubleArgumentType.getDouble(context, "mol");
        if (add) {
            room.addGasAt(gas, mol, room.temperatureK());
        } else {
            mol = room.removeGas(gas, mol);
        }
        return feedback(context, (add ? "Added " : "Removed ") + round(mol) + " mol of " + gas.symbol()
                + " — now " + round(room.partialPressureKPa(gas)) + " kPa");
    }

    private static int mixGas(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RoomState room = requireRoom(context);
        Gas gas = parseGas(context);
        double fraction = DoubleArgumentType.getDouble(context, "percent") / 100.0;

        // Solve for the amount that makes this species the requested share:
        // target / (others + target) = f  =>  target = others * f / (1 - f)
        double others = room.gases().totalMoles() - room.gases().get(gas);
        double target = others * fraction / (1.0 - fraction);
        double current = room.gases().get(gas);
        if (target > current) {
            room.addGasAt(gas, target - current, room.temperatureK());
        } else {
            room.removeGas(gas, current - target);
        }
        return feedback(context, gas.symbol() + " set to " + round(100 * room.gases().fraction(gas))
                + "% (" + round(room.partialPressureKPa(gas)) + " kPa) — ignition: "
                + Flammability.assess(room));
    }

    private static int fillAir(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RoomState room = requireRoom(context);
        double kpa = DoubleArgumentType.getDouble(context, "kpa");
        room.gases().extractFraction(1.0);
        room.addMixtureAt(GasMixture.earthAir(room.volumeM3(), kpa, 293.0), 293.0);
        return feedback(context, "Filled with Earth air at " + round(kpa) + " kPa");
    }

    private static int clearGas(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RoomState room = requireRoom(context);
        room.gases().extractFraction(1.0);
        return feedback(context, "Room evacuated to vacuum");
    }

    private static int setTemperature(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RoomState room = requireRoom(context);
        double celsius = DoubleArgumentType.getDouble(context, "celsius");
        room.setTemperatureK(Math.max(1.0, celsius + 273.15));
        return feedback(context, "Room temperature set to " + round(celsius)
                + " °C (it will drift back toward the surrounding structure)");
    }

    private static int setHumidity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RoomState room = requireRoom(context);
        setRoomHumidityPercent(room, DoubleArgumentType.getDouble(context, "percent"));
        return feedback(context, "Relative humidity set to "
                + (int) Math.round(100 * Humidity.relativeHumidity(room)) + "%"
                + " (mold-favourable=" + Humidity.moldFavourable(room) + ")");
    }

    /**
     * Mold's own debug channel (the debug-command rule) — reported live as never actually seen
     * in play. {@code seedMold} in {@code Atmosphere.tick()} only rolls a 0.2% chance per room
     * every half-second, and only on a cell it picks at random from the room's *whole* volume —
     * most of which is not floor, so most successful rolls still fail {@code MoldBlock.trySeed}'s
     * own floor check right after. Both are real, deliberate rarity (design says "hours, not
     * seconds"), but "hours" with no way to skip ahead is indistinguishable from "broken" to
     * someone testing it. {@code seed} forces a colony in immediately; {@code status} reads the
     * same real numbers {@code seedMold} itself checks, so a report of "not growing" is provably
     * a reading rather than a guess.
     */
    private static final double MOLD_FAVOURABLE_CELSIUS = 25.0;
    private static final double MOLD_FAVOURABLE_HUMIDITY_PERCENT = 85.0;
    private static final double MOLD_UNFAVOURABLE_CELSIUS = 0.0;
    private static final double MOLD_UNFAVOURABLE_HUMIDITY_PERCENT = 5.0;

    private static LiteralArgumentBuilder<CommandSourceStack> moldCommands() {
        return Commands.literal("mold")
                .then(Commands.literal("seed").executes(AstronimaCommand::seedMold))
                .then(Commands.literal("status").executes(AstronimaCommand::moldStatus))
                .then(Commands.literal("grow").executes(AstronimaCommand::growMold))
                .then(Commands.literal("dieback").executes(AstronimaCommand::diebackMold))
                .then(Commands.literal("favour").executes(AstronimaCommand::favourMold))
                .then(Commands.literal("unfavour").executes(AstronimaCommand::unfavourMold))
                .then(Commands.literal("rush")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 500))
                                .executes(AstronimaCommand::rushMold)))
                .then(Commands.literal("outbreak")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 6))
                                .executes(AstronimaCommand::moldOutbreak)))
                .then(Commands.literal("clear")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 16))
                                .executes(AstronimaCommand::clearMold)));
    }

    private static int seedMold(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = player.blockPosition();
        play.xponer.astronima.block.MoldBlock.trySeed((ServerLevel) source.getLevel(), pos);
        boolean placed = source.getLevel().getBlockState(pos)
                .is(play.xponer.astronima.registry.ModBlocks.MOLD.get());
        return feedback(context, placed
                ? "Mold seeded at your feet."
                : "Refused - not standing on a solid, air-clear floor cell (trySeed's own"
                        + " canSurvive check).");
    }

    private static int moldStatus(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        RoomState room = requireRoom(context);
        double tempC = room.temperatureK() - 273.15;
        boolean favourable = Humidity.moldFavourable(room);
        line(context.getSource(), "Mold - room reading", ChatFormatting.AQUA);
        line(context.getSource(), String.format(java.util.Locale.ROOT,
                        "  %.0f%% RH (needs >70%%), %.1f C (needs 5-40 C)",
                        100 * Humidity.relativeHumidity(room), tempC),
                ChatFormatting.GRAY);
        line(context.getSource(), "  favourable=" + favourable
                        + " - growth roll is 20% and spread roll is 30% per randomTick while"
                        + " favourable (design/mold-growth.md)",
                favourable ? ChatFormatting.GREEN : ChatFormatting.RED);
        moldAgeAtPlayer(context).ifPresentOrElse(
                age -> line(context.getSource(), "  colony at your feet: age " + age + "/"
                                + play.xponer.astronima.sim.MoldGrowth.MAX_AGE
                                + (play.xponer.astronima.sim.MoldGrowth.canSpread(age)
                                        ? " (mature - can spread)" : " (growing)"),
                        ChatFormatting.GREEN),
                () -> line(context.getSource(), "  no colony at your feet", ChatFormatting.GRAY));
        return 1;
    }

    private static int growMold(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        java.util.Optional<Integer> age = moldAgeAtPlayer(context);
        if (age.isEmpty()) {
            return feedback(context, "No mold colony at your feet to grow.");
        }
        CommandSourceStack source = context.getSource();
        BlockPos pos = source.getPlayerOrException().blockPosition();
        BlockState state = source.getLevel().getBlockState(pos);
        int grown = play.xponer.astronima.sim.MoldGrowth.grown(age.get());
        source.getLevel().setBlockAndUpdate(pos,
                state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3, grown));
        return feedback(context, "Colony advanced to age " + grown + "/"
                + play.xponer.astronima.sim.MoldGrowth.MAX_AGE + ".");
    }

    /** The AGE of the mold block under the player's feet, if there is one. */
    private static java.util.Optional<Integer> moldAgeAtPlayer(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = source.getPlayerOrException().blockPosition();
        BlockState state = source.getLevel().getBlockState(pos);
        if (!state.is(play.xponer.astronima.registry.ModBlocks.MOLD.get())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3));
    }

    private static int diebackMold(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        java.util.Optional<Integer> age = moldAgeAtPlayer(context);
        if (age.isEmpty()) {
            return feedback(context, "No mold colony at your feet to thin.");
        }
        CommandSourceStack source = context.getSource();
        BlockPos pos = source.getPlayerOrException().blockPosition();
        int thinner = play.xponer.astronima.sim.MoldGrowth.diedBack(age.get());
        if (thinner < 0) {
            source.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            return feedback(context, "Colony died back completely.");
        }
        BlockState state = source.getLevel().getBlockState(pos);
        source.getLevel().setBlockAndUpdate(pos,
                state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_3, thinner));
        return feedback(context, "Colony thinned to age " + thinner + "/"
                + play.xponer.astronima.sim.MoldGrowth.MAX_AGE + ".");
    }

    /** Sets the current room to a real, sustained mold-favourable reading (>70% RH, 5-40 C -
     *  {@code Humidity.moldFavourable}) in one call, instead of two separate {@code /astronima
     *  temp}/{@code humidity} calls worked out by hand each time. */
    private static int favourMold(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        RoomState room = requireRoom(context);
        room.setTemperatureK(MOLD_FAVOURABLE_CELSIUS + 273.15);
        setRoomHumidityPercent(room, MOLD_FAVOURABLE_HUMIDITY_PERCENT);
        return feedback(context, String.format(java.util.Locale.ROOT,
                "Room set to %.0f°C / %.0f%% RH - mold-favourable=%s",
                MOLD_FAVOURABLE_CELSIUS, MOLD_FAVOURABLE_HUMIDITY_PERCENT,
                Humidity.moldFavourable(room)));
    }

    /** The opposite of {@code favour} - cold and dry, for testing die-back without waiting for a
     *  dehumidifier to actually run. */
    private static int unfavourMold(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        RoomState room = requireRoom(context);
        room.setTemperatureK(MOLD_UNFAVOURABLE_CELSIUS + 273.15);
        setRoomHumidityPercent(room, MOLD_UNFAVOURABLE_HUMIDITY_PERCENT);
        return feedback(context, String.format(java.util.Locale.ROOT,
                "Room set to %.0f°C / %.0f%% RH - mold-favourable=%s",
                MOLD_UNFAVOURABLE_CELSIUS, MOLD_UNFAVOURABLE_HUMIDITY_PERCENT,
                Humidity.moldFavourable(room)));
    }

    private static void setRoomHumidityPercent(RoomState room, double percent) {
        double target = percent / 100.0;
        double wantedKPa = target * Humidity.saturationPressureKPa(room.temperatureK());
        double wantedMol = wantedKPa * 1000.0 * room.volumeM3() / (GasMixture.R * room.temperatureK());
        double current = room.gases().get(Gas.WATER_VAPOR);
        if (wantedMol > current) {
            room.addGasAt(Gas.WATER_VAPOR, wantedMol - current, room.temperatureK());
        } else {
            room.removeGas(Gas.WATER_VAPOR, current - wantedMol);
        }
    }

    /**
     * Replays {@code count} real {@code randomTick}s against the colony at your feet instead of
     * waiting for vanilla's own random-tick lottery to hand them out one at a time (~once per 68
     * real seconds per block at default random-tick speed) - the same dice, just rolled fast
     * (design/mold-growth.md §3, "Pacing"). Does nothing useful if the room is not favourable;
     * run {@code favour} first.
     */
    private static int rushMold(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        if (moldAgeAtPlayer(context).isEmpty()) {
            return feedback(context, "No mold colony at your feet to rush.");
        }
        CommandSourceStack source = context.getSource();
        ServerLevel level = (ServerLevel) source.getLevel();
        BlockPos pos = source.getPlayerOrException().blockPosition();
        int ticks = IntegerArgumentType.getInteger(context, "ticks");
        for (int i = 0; i < ticks; i++) {
            play.xponer.astronima.block.MoldBlock.simulateFavourableTick(level, pos, level.getRandom());
        }
        java.util.Optional<Integer> age = moldAgeAtPlayer(context);
        return feedback(context, "Replayed " + ticks + " randomTick(s). Colony is now "
                + age.map(a -> "age " + a + "/" + play.xponer.astronima.sim.MoldGrowth.MAX_AGE)
                        .orElse("gone (died back)")
                + ".");
    }

    /**
     * Sets the room favourable and seeds+rushes every eligible cell in a cube around the player -
     * "show me a whole outbreak right now" in one call, rather than seeding and rushing each
     * surface by hand.
     */
    private static int moldOutbreak(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        RoomState room = requireRoom(context);
        room.setTemperatureK(MOLD_FAVOURABLE_CELSIUS + 273.15);
        setRoomHumidityPercent(room, MOLD_FAVOURABLE_HUMIDITY_PERCENT);

        CommandSourceStack source = context.getSource();
        ServerLevel level = (ServerLevel) source.getLevel();
        BlockPos centre = source.getPlayerOrException().blockPosition();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int seeded = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius),
                centre.offset(radius, radius, radius))) {
            BlockPos immutable = pos.immutable();
            play.xponer.astronima.block.MoldBlock.trySeed(level, immutable);
            if (level.getBlockState(immutable).is(play.xponer.astronima.registry.ModBlocks.MOLD.get())) {
                seeded++;
                for (int i = 0; i < 50; i++) {
                    play.xponer.astronima.block.MoldBlock.simulateFavourableTick(level, immutable, level.getRandom());
                }
            }
        }
        return feedback(context, "Outbreak: seeded and grew " + seeded + " colon"
                + (seeded == 1 ? "y" : "ies") + " within radius " + radius + ".");
    }

    /** Removes every mold block in a cube around the player - the cleanup counterpart to
     *  {@code outbreak}, so a test area can be reset without breaking each block by hand. */
    private static int clearMold(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = (ServerLevel) source.getLevel();
        BlockPos centre = source.getPlayerOrException().blockPosition();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int cleared = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius),
                centre.offset(radius, radius, radius))) {
            if (level.getBlockState(pos).is(play.xponer.astronima.registry.ModBlocks.MOLD.get())) {
                level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                cleared++;
            }
        }
        return feedback(context, "Cleared " + cleared + " mold block(s) within radius " + radius + ".");
    }

    private static int forceIgnite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPos.containing(source.getPosition());
        RoomState room = requireRoom(context);
        Flammability.IgnitionStatus status = Flammability.assess(room);
        boolean ignited = Ignition.tryIgnite(source.getLevel(), pos);
        return feedback(context, ignited
                ? "Ignited (" + status + ")"
                : "No ignition — " + status);
    }

    /**
     * Sets and reads an infection, which is what makes the pathogen model real (rule 13).
     *
     * <p>An illness is the one hazard whose whole design is that you <em>cannot</em> see it coming:
     * it hides, it borrows its symptoms, and its name arrives tiers later. That is a fine thing to
     * do to a player and an impossible thing to develop against, so this is the channel that says
     * out loud what the biomonitor is deliberately refusing to.
     *
     * <p>{@code /astronima infect <source>} starts one. {@code /astronima infect status} reads the
     * whole of it. {@code /astronima infect advance <seconds>} runs it forward without waiting.
     */
    /**
     * {@code /astronima contaminate} — set and read the chain, per the debug-command rule.
     *
     * <p>Every load in this system is a number a player is supposed to watch climb, so the command
     * that <em>reads</em> it matters as much as the one that sets it: without a way to check, the
     * only way to test transmission is to get ill and guess why.
     */
    /** {@code /astronima chronic} — read the accumulating damage, and force it if you must. */
    /**
     * {@code /astronima calibrate} — read what drift does to a machine before it is wired in.
     *
     * <p>The honest first consumer of {@link play.xponer.astronima.sim.machine.Calibration}: the
     * model is proven and the mechanic is not built yet, and a debug command is the way to meet a
     * model without pretending a machine already uses it.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> calibrationCommands() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("calibrate")
                // The machine in front of you, which is the question anybody actually has: what is
                // this one set to, where has it drifted to, and is it worth the walk. Reading the
                // model in the abstract was the first consumer; a real machine is the honest one.
                .then(Commands.literal("at")
                        .then(Commands.argument("pos",
                                        net.minecraft.commands.arguments.coordinates
                                                .BlockPosArgument.blockPos())
                                .executes(context -> reportMachine(context, null))
                                .then(Commands.argument("set",
                                                DoubleArgumentType.doubleArg(0, 1))
                                        .executes(context -> reportMachine(context,
                                                DoubleArgumentType.getDouble(context, "set"))))));
        for (var drift : play.xponer.astronima.sim.machine.Calibration.Drift.values()) {
            root = root.then(Commands.literal(drift.name().toLowerCase(java.util.Locale.ROOT))
                    .then(Commands.argument("work", DoubleArgumentType.doubleArg(0, 1e6))
                            .executes(context -> {
                                double work = DoubleArgumentType.getDouble(context, "work");
                                double set = 0.5;
                                double now = play.xponer.astronima.sim.machine.Calibration
                                        .after(set, drift, work);
                                // What the same work does to a machine a servo is holding: the
                                // wear stops being charged once it has reached the deadband, so
                                // the error settles there rather than at zero.
                                double charged = 0;
                                for (double done = 0; done < work; done += 1) {
                                    charged += play.xponer.astronima.sim.machine.Calibration
                                            .workCharged(charged, 1, drift, true);
                                }
                                double held = play.xponer.astronima.sim.machine.Calibration
                                        .after(set, drift, charged);
                                return feedback(context, String.format(java.util.Locale.ROOT,
                                        "%s after %.0f work: set %.2f -> %.3f (error %.3f)%s"
                                                + " | with a servo: %.2f | service every "
                                                + drift.workToService() + " work",
                                        drift, work, set, now,
                                        play.xponer.astronima.sim.machine.Calibration
                                                .error(set, now),
                                        play.xponer.astronima.sim.machine.Calibration
                                                .isWorthResetting(set, now)
                                                ? " - WORTH RESETTING" : "",
                                        held));
                            })));
        }
        return root;
    }

    /**
     * Reads - and optionally sets - the calibration of a real machine in the world.
     *
     * <p>Sets what the operator would have set, not where the machine has got to: there is no way
     * to hand a crusher a drift, because a drift is something it earns. Re-setting it also zeroes
     * the wear, exactly as a wrench does, so this is the tool doing its job instantly rather than a
     * back door round the mechanic.
     */
    private static int reportMachine(CommandContext<CommandSourceStack> context,
                                     @org.jspecify.annotations.Nullable Double set)
            throws CommandSyntaxException {
        BlockPos pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument
                .getLoadedBlockPos(context, "pos");
        if (!(context.getSource().getLevel().getBlockEntity(pos)
                instanceof play.xponer.astronima.block.entity.ProcessingBlockEntity machine)) {
            return feedback(context, "no machine at " + pos.toShortString());
        }
        if (set != null) {
            machine.calibrateTo(set);
        }
        var drift = machine.drift();
        if (!drift.drifts()) {
            return feedback(context, String.format(java.util.Locale.ROOT,
                    "%s at %s does not drift; its setting is %.3f",
                    machine.kind(), pos.toShortString(), machine.calibratedTo()));
        }
        return feedback(context, String.format(java.util.Locale.ROOT,
                "%s at %s | %s, set by %s | set %.3f -> now %.3f (error %.3f)%s | service every %d"
                        + " work | %s",
                machine.kind(), pos.toShortString(), drift, drift.setBy(),
                machine.calibratedTo(), machine.calibratedSetting(), machine.calibrationError(),
                machine.isWorthRecalibrating() ? " - WORTH RESETTING" : "",
                drift.workToService(),
                machine.isBeingCalibrated()
                        ? String.format(java.util.Locale.ROOT, "calibrating %.0f%%",
                                machine.calibrationProgress() * 100)
                        : machine.isServoHolding() ? "servo holding" : drift.fault()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> chronicCommands() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("chronic")
                .then(Commands.literal("status").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    var body = play.xponer.astronima.physio.Chronics.of(player);
                    StringBuilder said = new StringBuilder();
                    for (var condition
                            : play.xponer.astronima.sim.physio.Chronic.Condition.values()) {
                        said.append(String.format(java.util.Locale.ROOT, "%s %.2f%s | ",
                                condition.key(), body.insultOf(condition),
                                body.hasScarred(condition) ? " SCARRED" : ""));
                    }
                    said.append(String.format(java.util.Locale.ROOT,
                            "ppO2 floor %.1f kPa | immunity x%.2f",
                            play.xponer.astronima.physio.Chronics.ppO2FloorKPa(player),
                            body.immunityFactor()));
                    return feedback(context, said.toString());
                }));
        for (var condition : play.xponer.astronima.sim.physio.Chronic.Condition.values()) {
            root = root.then(Commands.literal(condition.key())
                    .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 1e7))
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                var body = play.xponer.astronima.physio.Chronics.of(player);
                                body.strain(condition,
                                        DoubleArgumentType.getDouble(context, "seconds"));
                                player.setData(play.xponer.astronima.registry.ModAttachments
                                                .CHRONIC.get(),
                                        play.xponer.astronima.physio.CarriedChronic.of(body));
                                return feedback(context, condition.key() + " now "
                                        + String.format(java.util.Locale.ROOT, "%.2f",
                                                body.insultOf(condition))
                                        + (body.hasScarred(condition) ? " SCARRED" : ""));
                            })));
        }
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> contaminationCommands() {
        return Commands.literal("contaminate")
                .then(Commands.literal("status").executes(AstronimaCommand::reportContamination))
                .then(Commands.literal("clean").executes(context -> {
                    play.xponer.astronima.physio.Contaminations.decontaminate(
                            context.getSource().getPlayerOrException());
                    return feedback(context, "Gloves and hands clean.");
                }))
                .then(Commands.literal("gloves")
                        .then(Commands.argument("load", DoubleArgumentType.doubleArg(0, 1))
                                .executes(context -> setLoad(context,
                                        DoubleArgumentType.getDouble(context, "load"), true))))
                .then(Commands.literal("skin")
                        .then(Commands.argument("load", DoubleArgumentType.doubleArg(0, 1))
                                .executes(context -> setLoad(context,
                                        DoubleArgumentType.getDouble(context, "load"), false))))
                .then(Commands.literal("surface")
                        .then(Commands.argument("load", DoubleArgumentType.doubleArg(0, 1))
                                .executes(context -> {
                                    ServerPlayer player =
                                            context.getSource().getPlayerOrException();
                                    net.minecraft.core.BlockPos at =
                                            player.blockPosition().below();
                                    play.xponer.astronima.physio.Surfaces.set(player.level(), at,
                                            new play.xponer.astronima.sim.pathogen.Contamination(
                                                    play.xponer.astronima.sim.pathogen.Strain
                                                            .Source.COMMENSAL,
                                                    DoubleArgumentType.getDouble(context, "load")));
                                    return feedback(context, "Surface under you set to "
                                            + DoubleArgumentType.getDouble(context, "load"));
                                })))
                .then(Commands.literal("touchface").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    play.xponer.astronima.physio.Contaminations.handToMouth(player);
                    return reportContamination(context);
                }));
    }

    private static int setLoad(CommandContext<CommandSourceStack> context, double load,
                               boolean gloves) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var carried = play.xponer.astronima.physio.Contaminations.of(player);
        var source = carried.source();
        player.setData(play.xponer.astronima.registry.ModAttachments.CONTAMINATION.get(),
                new play.xponer.astronima.physio.CarriedContamination(source,
                        gloves ? load : carried.glove(), gloves ? carried.skin() : load));
        return reportContamination(context);
    }

    private static int reportContamination(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var carried = play.xponer.astronima.physio.Contaminations.of(player);
        double dose = play.xponer.astronima.sim.pathogen.Contamination.INFECTIOUS_DOSE;
        var intact = play.xponer.astronima.physio.Contaminations.barriersOf(player);
        double under = play.xponer.astronima.physio.Surfaces
                .at(player.level(), player.blockPosition().below()).load();
        return feedback(context, String.format(java.util.Locale.ROOT,
                "%s | gloves %.3f | skin %.3f (dose %.2f) | floor %.3f | barriers %s",
                carried.source(), carried.glove(), carried.skin(), dose, under,
                intact.isEmpty() ? "none" : intact));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> infectionCommands() {
        LiteralArgumentBuilder<CommandSourceStack> start = Commands.literal("infect");
        for (Strain.Source source : Strain.Source.values()) {
            start = start.then(Commands.literal(source.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(context -> catchIt(context, source)));
        }
        return start
                .then(Commands.literal("status").executes(AstronimaCommand::reportInfection))
                .then(Commands.literal("clear").executes(context -> {
                    context.getSource().getPlayerOrException().setData(
                            play.xponer.astronima.registry.ModAttachments.INFECTION.get(),
                            play.xponer.astronima.physio.CarriedInfection.NONE);
                    return feedback(context, "Infection cleared.");
                }))
                .then(Commands.literal("advance")
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 100000))
                                .executes(context -> advanceInfection(context,
                                        DoubleArgumentType.getDouble(context, "seconds")))));
    }

    private static int catchIt(CommandContext<CommandSourceStack> context, Strain.Source source)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Strain strain = Pathogens.of(source);
        player.setData(play.xponer.astronima.registry.ModAttachments.INFECTION.get(),
                play.xponer.astronima.physio.CarriedInfection.of(new Infection(strain)));
        return feedback(context, "Infected with " + strain.designator() + " (" + source
                + ", " + strain.route() + "). It will show nothing for "
                + Math.round(strain.incubationSeconds()) + " s.");
    }

    private static int advanceInfection(CommandContext<CommandSourceStack> context, double seconds)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Infection infection = player.getData(
                play.xponer.astronima.registry.ModAttachments.INFECTION)
                .revive(play.xponer.astronima.physio.Infections.KNOWN);
        if (infection == null) {
            return feedback(context, "Not infected.");
        }
        // The player's real immunity, not a constant: running the clock forward has to answer the
        // same question a night's sleep would.
        double immunity = play.xponer.astronima.physio.Infections.immunityOf(player);
        for (double at = 0; at < seconds; at += 1) {
            infection.step(1, immunity);
        }
        player.setData(play.xponer.astronima.registry.ModAttachments.INFECTION.get(),
                play.xponer.astronima.physio.CarriedInfection.of(infection));
        return feedback(context, describe(infection, player));
    }

    private static int reportInfection(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Infection infection = player.getData(
                play.xponer.astronima.registry.ModAttachments.INFECTION)
                .revive(play.xponer.astronima.physio.Infections.KNOWN);
        if (infection == null) {
            return feedback(context, "Not infected. Immunity "
                    + Math.round(play.xponer.astronima.physio.Infections.immunityOf(player) * 100)
                    + "%.");
        }
        return feedback(context, describe(infection, player));
    }

    private static String describe(Infection infection, ServerPlayer player) {
        if (infection.isCleared()) {
            return infection.strain().designator() + ": cleared.";
        }
        // Who is winning, in the terms the model actually uses. Without this line the report
        // says HIDDEN over and over and gives no clue that the body is beating it - which is
        // exactly what a well-fed player does to a cryophile, by design, invisibly.
        double immunity = play.xponer.astronima.physio.Infections.immunityOf(player);
        double virulence = infection.strain().virulence();
        String tide = immunity > virulence
                ? "YOUR BODY IS WINNING - it will clear on its own"
                : "THE ORGANISM IS WINNING - it will get worse";
        StringBuilder out = new StringBuilder(infection.strain().designator())
                .append(": ").append(infection.stage())
                .append("  [immunity ").append(Math.round(immunity * 100))
                .append("% vs virulence ").append(Math.round(virulence * 100))
                .append("% - ").append(tide).append(']');
        if (infection.isIncubating()) {
            out.append(" (incubating - showing nothing)");
        }
        if (!infection.symptoms().isEmpty()) {
            out.append("  showing ").append(infection.symptoms().stream()
                    .map(ailment -> ailment.displayName()).toList());
        }
        if (infection.course() != null) {
            out.append("  on ").append(infection.course().name())
                    .append(infection.courseFinished() ? " (course complete)" : " (mid-course)");
        }
        if (!infection.resistances().isEmpty()) {
            out.append("  RESISTANT TO ").append(infection.resistances().stream()
                    .map(Treatment::name).toList());
        }
        return out.toString();
    }

    /**
     * The bench, driven by hand: the whole identification workflow before it has any machines.
     *
     * <p>Rule 13's consumer for {@code sim/lab}, and it is not a stub — every step here is the step
     * the machine will perform, in the order a laboratory performs them, and it works on a
     * <strong>dish</strong> rather than on an abstract organism. That is the important part: the
     * plate is the notebook, so what one bench finds out the next bench receives.
     *
     * <p>{@code /astronima lab stain 30} is the interesting one. Thirty seconds of alcohol
     * over-decolorizes, the slide reads Gram-negative, the plate carries that faithfully to every
     * bench after it — and only the microscope contradicts it.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> labCommands() {
        LiteralArgumentBuilder<CommandSourceStack> streak = Commands.literal("streak");
        for (Organism organism : Isolates.all()) {
            streak = streak.then(Commands.literal(
                            organism.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(context -> streakPlate(context, organism, 1.0))
                    .then(Commands.argument("quality", DoubleArgumentType.doubleArg(0, 1))
                            .executes(context -> streakPlate(context, organism,
                                    DoubleArgumentType.getDouble(context, "quality")))));
        }
        LiteralArgumentBuilder<CommandSourceStack> give = Commands.literal("give");
        for (Organism organism : Isolates.all()) {
            give = give.then(Commands.literal(organism.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(context -> giveDish(context, organism, 1.0))
                    .then(Commands.argument("quality", DoubleArgumentType.doubleArg(0, 1))
                            .executes(context -> giveDish(context, organism,
                                    DoubleArgumentType.getDouble(context, "quality")))));
        }
        return Commands.literal("lab")
                .then(give)
                .then(streak)
                .then(Commands.literal("incubate")
                        .then(Commands.argument("hours", DoubleArgumentType.doubleArg(0, 200))
                                .then(Commands.argument("celsius",
                                                DoubleArgumentType.doubleArg(-50, 120))
                                        .executes(context -> incubate(context,
                                                DoubleArgumentType.getDouble(context, "hours"),
                                                DoubleArgumentType.getDouble(context,
                                                        "celsius"))))))
                .then(Commands.literal("stain")
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 120))
                                .executes(context -> stain(context,
                                        DoubleArgumentType.getDouble(context, "seconds")))))
                .then(Commands.literal("scope").executes(AstronimaCommand::microscope))
                .then(Commands.literal("catalase").executes(context -> biochemical(context, true)))
                .then(Commands.literal("oxidase").executes(context -> biochemical(context, false)))
                .then(Commands.literal("plate")
                        .then(Commands.argument("inoculum", DoubleArgumentType.doubleArg(0.05, 100))
                                .executes(context -> discs(context,
                                        DoubleArgumentType.getDouble(context, "inoculum")))))
                .then(Commands.literal("read").executes(AstronimaCommand::readPlate));
    }

    /**
     * Hands over a streaked dish as an <strong>item</strong>, ready for the machines.
     *
     * <p>Sampling is not a mechanic yet — there is no swab and no wreck to swab — so without this
     * the incubator, the microscope and the disc reader have nothing to work on and cannot be
     * exercised at all. A debug channel that unblocks three machines is worth more than waiting for
     * the mechanic that will replace it.
     */
    private static int giveDish(CommandContext<CommandSourceStack> context, Organism organism,
                                double quality) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        net.minecraft.world.item.ItemStack dish =
                new net.minecraft.world.item.ItemStack(
                        play.xponer.astronima.registry.ModItems.PETRI_DISH.get());
        play.xponer.astronima.item.PetriDishItem.setCulture(dish,
                Culture.freshly(organism.name(), quality));
        if (!player.getInventory().add(dish)) {
            player.drop(dish, false);
        }
        return feedback(context, "Streaked " + organism.name() + " onto a dish (quality "
                + quality + "). It needs " + Math.round(Culture.READY_HOURS)
                + " h at 37 C in an incubator.");
    }

    /**
     * The dish on the bench.
     *
     * <p>Command-scoped rather than saved, because the Petri dish item that will carry this does
     * not exist yet and inventing its save format now would be inventing its design.
     */
    private static final java.util.Map<java.util.UUID, Culture> DISHES = new java.util.HashMap<>();

    private static int streakPlate(CommandContext<CommandSourceStack> context, Organism organism,
                                   double quality) throws CommandSyntaxException {
        DISHES.put(context.getSource().getPlayerOrException().getUUID(),
                Culture.freshly(organism.name(), quality));
        return feedback(context, "Streaked, four quadrants, quality " + quality
                + ". Nothing on it yet - it needs " + Math.round(Culture.READY_HOURS)
                + " h at 37 C.");
    }

    private static int incubate(CommandContext<CommandSourceStack> context, double hours,
                                double celsius) throws CommandSyntaxException {
        Culture dish = dishOf(context);
        if (dish == null) {
            return feedback(context, "No dish on the bench.");
        }
        Culture after = dish.incubated(hours, celsius + 273.15);
        DISHES.put(context.getSource().getPlayerOrException().getUUID(), after);
        return feedback(context, String.join("  ", after.label()));
    }

    private static int stain(CommandContext<CommandSourceStack> context, double seconds)
            throws CommandSyntaxException {
        Culture dish = pickable(context);
        if (dish == null) {
            return feedback(context, notReady(context));
        }
        GramStain.Result result = GramStain.run(dish.growing(), GramStain.properOrder(), seconds);
        DISHES.put(context.getSource().getPlayerOrException().getUUID(), dish.stained(result));
        String colour = switch (result) {
            case POSITIVE -> "PURPLE - gram positive";
            case NEGATIVE -> "PINK - gram negative";
            case SPOILED -> "nothing usable - the slide is ruined";
        };
        return feedback(context, "Decolorized " + seconds + " s. The slide reads " + colour + ".");
    }

    private static int microscope(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Culture dish = pickable(context);
        if (dish == null) {
            return feedback(context, notReady(context));
        }
        Organism organism = dish.growing();
        DISHES.put(context.getSource().getPlayerOrException().getUUID(),
                dish.seen(organism.shape(), organism.arrangement()));
        return feedback(context, "1000x: "
                + organism.shape().name().toLowerCase(java.util.Locale.ROOT) + ", "
                + organism.arrangement().name().toLowerCase(java.util.Locale.ROOT) + ".");
    }

    private static int biochemical(CommandContext<CommandSourceStack> context, boolean isCatalase)
            throws CommandSyntaxException {
        Culture dish = pickable(context);
        if (dish == null) {
            return feedback(context, notReady(context));
        }
        boolean positive = isCatalase ? dish.growing().catalase() : dish.growing().oxidase();
        DISHES.put(context.getSource().getPlayerOrException().getUUID(),
                dish.tested(isCatalase, positive));
        if (isCatalase) {
            return feedback(context, positive
                    ? "Peroxide: it fizzes. Catalase positive."
                    : "Peroxide: nothing happens. Catalase negative.");
        }
        return feedback(context, positive
                ? "The reagent goes deep purple within ten seconds. Oxidase positive."
                : "No colour change. Oxidase negative.");
    }

    /** Kirby-Bauer: lay the discs, and read the rings with a ruler. */
    private static int discs(CommandContext<CommandSourceStack> context, double inoculum)
            throws CommandSyntaxException {
        Culture dish = pickable(context);
        if (dish == null) {
            return feedback(context, notReady(context));
        }
        StringBuilder out = new StringBuilder("Zones at inoculum x" + inoculum + ":");
        for (Antibiotic drug : Antibiotic.all()) {
            double zone = DiscDiffusion.zoneMm(dish.growing(), drug, inoculum);
            out.append("  ").append(drug.name()).append(' ')
                    .append(String.format(java.util.Locale.ROOT, "%.0f", zone)).append(" mm ")
                    .append(drug.read(zone));
        }
        return feedback(context, out.toString());
    }

    private static int readPlate(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Culture dish = dishOf(context);
        if (dish == null) {
            return feedback(context, "No dish on the bench.");
        }
        IdentificationKey key = dish.key();
        java.util.List<Organism> left = key.candidates(Isolates.all());
        String label = String.join("  ", dish.label());
        if (!key.isSelfConsistent()) {
            return feedback(context, label + "  |  These cannot all be true - the stain and the"
                    + " microscope disagree. Re-stain it and watch the alcohol.");
        }
        if (left.isEmpty()) {
            return feedback(context, label + "  |  Nothing matches.");
        }
        if (left.size() == 1) {
            return feedback(context, label + "  |  Identified: " + left.get(0).name() + ".");
        }
        return feedback(context, label + "  |  " + left.size() + " candidates "
                + left.stream().map(Organism::name).toList() + ", still to run: "
                + key.outstanding() + ".");
    }

    private static Culture dishOf(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return DISHES.get(context.getSource().getPlayerOrException().getUUID());
    }

    /** A dish you can actually take a colony off, or null. */
    private static Culture pickable(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Culture dish = dishOf(context);
        return dish != null && dish.hasColonies() && dish.growing() != null ? dish : null;
    }

    private static String notReady(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Culture dish = dishOf(context);
        if (dish == null) {
            return "No dish on the bench.";
        }
        return "Nothing to pick: " + String.join("  ", dish.label()) + ".";
    }


    private static int reportNoise(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPos.containing(source.getPosition());
        double db = Atmosphere.get(source.getLevel()).noiseDbAt(pos);
        return feedback(context, "Sound level here: " + Math.round(db) + " dB"
                + (db > 60 ? " (too loud to rest)" : " (restful)"));
    }

    private static int giveKit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int given = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Astronima.MODID)) {
                ItemStack stack = new ItemStack(item, item.getDefaultMaxStackSize() > 1 ? 16 : 1);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                given++;
            }
        }
        return feedback(context, "Gave " + given + " Astronima item stacks");
    }

    // ------------------------------------------------------------------ helpers

    private static RoomState requireRoom(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Atmosphere.RoomReading reading = Atmosphere.get(source.getLevel()).readingNear(pos);
        if (reading == null) {
            throw NO_ROOM.create();
        }
        return reading.state();
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> gasArgument() {
        return Commands.argument("gas", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(gasNames(), builder));
    }

    private static Gas parseGas(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "gas").toLowerCase(Locale.ROOT);
        for (Gas gas : Gas.values()) {
            if (gas.symbol().toLowerCase(Locale.ROOT).equals(name) || gas.name().toLowerCase(Locale.ROOT).equals(name)) {
                return gas;
            }
        }
        throw UNKNOWN_GAS.create();
    }

    private static String[] gasNames() {
        return Arrays.stream(Gas.values()).map(gas -> gas.symbol().toLowerCase(Locale.ROOT)).toArray(String[]::new);
    }

    /**
     * Previews the particle system, per the debug-command rule.
     *
     * <p>The solar retort only ever reaches whatever temperatures daylight and its focus
     * dial happen to land on, which is a handful of points on a curve that spans 798 K
     * to white-hot. {@code /astronima particle incandescence <kelvin>} spawns the real
     * emitter at any temperature on demand, in front of the player, so the whole range
     * (design/presentation.md §3) can be checked by eye without building a working retort
     * and waiting on the sun for every setting.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> particleCommands() {
        return Commands.literal("particle")
                .then(Commands.literal("incandescence")
                        .then(Commands.argument("kelvin", DoubleArgumentType.doubleArg(300, 10_000))
                                .executes(AstronimaCommand::spawnIncandescence)));
    }

    private static int spawnIncandescence(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        double kelvin = DoubleArgumentType.getDouble(context, "kelvin");

        int rgb = BlackBody.rgb(kelvin);
        double intensity = BlackBody.intensity01(kelvin);
        boolean glowing = BlackBody.isVisiblyGlowing(kelvin);
        line(source, String.format(Locale.ROOT, "%.0f K  #%06X  intensity %s  %s",
                        kelvin, rgb, percent(intensity),
                        glowing ? "glowing" : "below the Draper point (798 K) - nothing spawned"),
                glowing ? ChatFormatting.GOLD : ChatFormatting.GRAY);
        if (glowing) {
            // A mix of both roles rather than one big burst: each mote rolls its own random
            // lifetime (RetortGlow's real spread), so forty-odd of them already die at
            // staggered moments across a couple of seconds rather than all at once - the fix
            // for "spawns once and disappears immediately" is a longer, varied lifetime, not a
            // preview-only special case that could drift from what the real emitter does.
            Vec3 at = player.position().add(player.getLookAngle().scale(2.0)).add(0, 1.0, 0);
            level.sendParticles(new ModParticles.IncandescenceOptions(rgb, (float) intensity, false),
                    at.x, at.y, at.z, 40, 0.3, 0.3, 0.3, 0.0);
            level.sendParticles(new ModParticles.IncandescenceOptions(rgb, (float) intensity, true),
                    at.x, at.y, at.z, (int) play.xponer.astronima.sim.ore.RetortGlow.ACCENT_BURST_PARTICLES,
                    0.2, 0.15, 0.2, 0.0);
        }
        return 1;
    }

    private static int feedback(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String text, ChatFormatting color) {
        source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    private AstronimaCommand() {}
}
