package play.xponer.astronima.datagen;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.OxygenCandleBlock;
import play.xponer.astronima.block.GasValveBlock;
import play.xponer.astronima.sim.pipe.Valve;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import play.xponer.astronima.block.GasPipeBlock;
import play.xponer.astronima.block.PowerCableBlock;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.data.PackOutput;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;

/**
 * Model/blockstate generation. Placeholder art policy: every block is a plain cube and
 * every item a flat sprite until real models arrive; generating (rather than
 * hand-writing) the JSON keeps us aligned with the current formats.
 */
public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, Astronima.MODID);
    }

    /**
     * The light stick is hand-authored: it is a small stick lying on the floor, and
     * datagen has no template for custom geometry.
     */
    /**
     * The candle, showing whether it has been used.
     *
     * <p>The oldest legibility defect in the mod: a spent candle rendered identically to
     * a fresh one, so a player holding a used one had no way to know why it would not
     * light. It was reported as "oxygen candle is bugged", which is exactly what an
     * invisible state looks like from outside.
     *
     * <p>Three states, because a chlorate candle genuinely has three: unlit, burning,
     * and consumed. Spent is sooted and darkened — visibly a thing that has burned.
     */
    private static void oxygenCandle(BlockModelGenerators blockModels) {
        Identifier unlit = candleModel(blockModels, "", "_side");
        Identifier lit = candleModel(blockModels, "_lit", "_side_lit");
        Identifier spent = candleModel(blockModels, "_spent", "_side_spent");

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.OXYGEN_CANDLE.get())
                        .with(PropertyDispatch.initial(OxygenCandleBlock.SPENT,
                                        OxygenCandleBlock.LIT)
                                // A spent candle cannot be lit, so both spent rows show
                                // the burnt model rather than inventing a fourth state.
                                .select(true, true, BlockModelGenerators.plainVariant(spent))
                                .select(true, false, BlockModelGenerators.plainVariant(spent))
                                .select(false, true, BlockModelGenerators.plainVariant(lit))
                                .select(false, false, BlockModelGenerators.plainVariant(unlit))));
    }

    /**
     * The decon booth, lit and unlit.
     *
     * <p>Two states because a booth that is running has to read as running from across the room —
     * the lamp is the whole point of it, and a machine whose only sign of life is a tooltip is the
     * kind of thing a player walks away from mid-cycle.
     */
    private static void deconStation(BlockModelGenerators blockModels) {
        Block booth = ModBlocks.DECON_STATION.get();
        TextureMapping off = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(booth, "_side"))
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(booth, "_top"))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(booth, "_top"));
        TextureMapping on = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(booth, "_side_lit"))
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(booth, "_top"))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(booth, "_top"));
        Identifier idle = ModelTemplates.CUBE_BOTTOM_TOP.create(booth, off, blockModels.modelOutput);
        Identifier lit = ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(booth, "_lit", on,
                blockModels.modelOutput);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(booth)
                        .with(PropertyDispatch.initial(
                                        play.xponer.astronima.block.DeconStationBlock.RUNNING)
                                .select(true, BlockModelGenerators.plainVariant(lit))
                                .select(false, BlockModelGenerators.plainVariant(idle))));
    }

    private static Identifier candleModel(BlockModelGenerators blockModels,
                                          String suffix, String sideSuffix) {
        Block candle = ModBlocks.OXYGEN_CANDLE.get();
        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(candle, sideSuffix))
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(candle, "_top"))
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(candle, "_bottom"));
        return suffix.isEmpty()
                ? ModelTemplates.CUBE_BOTTOM_TOP.create(candle, mapping, blockModels.modelOutput)
                : ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(candle, suffix, mapping,
                        blockModels.modelOutput);
    }

    /**
     * The alarm, showing whether it is alarming.
     *
     * <p>An alarm you cannot see is one you cannot locate, which defeats the point of
     * having one in a habitat with more than one room.
     */
    /**
     * The bulkhead door, with the interlock latch as a visible state.
     *
     * <p>Vanilla's {@code createDoor} covers facing × half × hinge × open. This adds the
     * fifth dimension the interlock needs, and does it as a <strong>texture</strong> swap on
     * the same eight door models rather than new geometry: the latch has to be visible in
     * world (rule 9), and a door that changed shape when it locked would be new surfaces to
     * get wrong at half-pixel resolution for no gain (rule 15).
     *
     * <p>The 64 variants are generated rather than written out, because the rotation table
     * is the interesting part and it is the same one vanilla uses: a closed door faces its
     * facing, an open one swings a quarter turn one way or the other depending on which side
     * the hinge is. Writing that out sixty-four times by hand is how a single transposed
     * entry ships and nobody notices until a door in one orientation faces backwards.
     */
    private static void bulkheadDoor(BlockModelGenerators blockModels) {
        Block door = ModBlocks.BULKHEAD_DOOR.get();
        TextureMapping free = TextureMapping.door(door);
        TextureMapping held = TextureMapping.door(
                TextureMapping.getBlockTexture(door, "_locked_top"),
                TextureMapping.getBlockTexture(door, "_locked_bottom"));

        // [locked][half][hinge][open] — the eight door shapes, twice over.
        ModelTemplate[] shapes = {
                ModelTemplates.DOOR_BOTTOM_LEFT, ModelTemplates.DOOR_BOTTOM_LEFT_OPEN,
                ModelTemplates.DOOR_BOTTOM_RIGHT, ModelTemplates.DOOR_BOTTOM_RIGHT_OPEN,
                ModelTemplates.DOOR_TOP_LEFT, ModelTemplates.DOOR_TOP_LEFT_OPEN,
                ModelTemplates.DOOR_TOP_RIGHT, ModelTemplates.DOOR_TOP_RIGHT_OPEN,
        };
        MultiVariant[] freeModels = new MultiVariant[shapes.length];
        MultiVariant[] heldModels = new MultiVariant[shapes.length];
        for (int i = 0; i < shapes.length; i++) {
            freeModels[i] = BlockModelGenerators.plainVariant(
                    shapes[i].create(door, free, blockModels.modelOutput));
            heldModels[i] = BlockModelGenerators.plainVariant(
                    shapes[i].createWithSuffix(door, "_locked", held, blockModels.modelOutput));
        }

        var dispatch = PropertyDispatch.initial(
                BlockStateProperties.HORIZONTAL_FACING,
                BlockStateProperties.DOUBLE_BLOCK_HALF,
                BlockStateProperties.DOOR_HINGE,
                BlockStateProperties.OPEN,
                BlockStateProperties.LOCKED);
        for (Direction facing : DOOR_FACINGS) {
            for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                for (DoorHingeSide hinge : DoorHingeSide.values()) {
                    for (boolean open : new boolean[] {false, true}) {
                        for (boolean locked : new boolean[] {false, true}) {
                            int shape = (half == DoubleBlockHalf.UPPER ? 4 : 0)
                                    + (hinge == DoorHingeSide.RIGHT ? 2 : 0) + (open ? 1 : 0);
                            MultiVariant model = locked ? heldModels[shape] : freeModels[shape];
                            dispatch = dispatch.select(facing, half, hinge, open, locked,
                                    turn(model, facing, hinge, open));
                        }
                    }
                }
            }
        }
        blockModels.registerSimpleFlatItemModel(door.asItem());
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(door).with(dispatch));
    }

    /** East first, because that is the orientation the door models are drawn in. */
    private static final Direction[] DOOR_FACINGS = {
            Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};

    /**
     * How far round a door model turns for a given facing.
     *
     * <p>A shut door simply faces its facing. An open one has already swung a quarter turn
     * in the model, so it needs one less (or one more) depending on which side the hinge is
     * — which is the whole reason a left-hinged and a right-hinged door of the same facing
     * point opposite ways when open.
     */
    private static MultiVariant turn(MultiVariant model, Direction facing, DoorHingeSide hinge,
                                     boolean open) {
        int step = switch (facing) {
            case EAST -> 0;
            case SOUTH -> 1;
            case WEST -> 2;
            default -> 3;
        };
        if (open) {
            step += hinge == DoorHingeSide.LEFT ? 1 : 3;
        }
        return switch (step % 4) {
            case 1 -> model.with(BlockModelGenerators.Y_ROT_90);
            case 2 -> model.with(BlockModelGenerators.Y_ROT_180);
            case 3 -> model.with(BlockModelGenerators.Y_ROT_270);
            default -> model;
        };
    }

    /**
     * The purge valve: a hull plate with a vent boss, shut or open.
     *
     * <p>Two textures rather than one with a lamp, because the state matters more than any
     * other block's in this mod — an open one is emptying the room you are standing in — and a
     * shut disc against an open throat is legible from across a habitat, which a coloured pip
     * is not (rule 9).
     */
    private static void purgeValve(BlockModelGenerators blockModels) {
        Block block = ModBlocks.PURGE_VALVE.get();
        MultiVariant shut = BlockModelGenerators.plainVariant(
                TexturedModel.CUBE_TOP_BOTTOM.get(block).createWithSuffix(
                        block, "_shut", blockModels.modelOutput));
        MultiVariant open = BlockModelGenerators.plainVariant(
                TexturedModel.CUBE_TOP_BOTTOM.get(block)
                        .updateTextures(t -> t.put(TextureSlot.SIDE,
                                TextureMapping.getBlockTexture(block, "_open")))
                        .createWithSuffix(block, "_open", blockModels.modelOutput));

        var dispatch = PropertyDispatch.initial(
                BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.OPEN);
        for (Direction facing : DOOR_FACINGS) {
            for (boolean isOpen : new boolean[] {false, true}) {
                dispatch = dispatch.select(facing, isOpen,
                        turnTo(isOpen ? open : shut, facing));
            }
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(dispatch));
    }

    /** A quarter turn per compass step, counted from east as the door models are. */
    private static MultiVariant turnTo(MultiVariant model, Direction facing) {
        return switch (facing) {
            case SOUTH -> model.with(BlockModelGenerators.Y_ROT_90);
            case WEST -> model.with(BlockModelGenerators.Y_ROT_180);
            case NORTH -> model.with(BlockModelGenerators.Y_ROT_270);
            default -> model;
        };
    }

    /** The mat, lit while somebody is on it — the state its whole job is to report. */
    private static void presenceSensor(BlockModelGenerators blockModels) {
        Block block = ModBlocks.PRESENCE_SENSOR.get();
        Identifier idle = ModelTemplates.CUBE_ALL.create(block,
                TextureMapping.cube(TextureMapping.getBlockTexture(block)),
                blockModels.modelOutput);
        Identifier stood = ModelTemplates.CUBE_ALL.createWithSuffix(block, "_on",
                TextureMapping.cube(TextureMapping.getBlockTexture(block, "_on")),
                blockModels.modelOutput);
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block)
                        .with(BlockModelGenerators.createBooleanModelDispatch(
                                play.xponer.astronima.block.PresenceSensorBlock.LIT,
                                BlockModelGenerators.plainVariant(stood),
                                BlockModelGenerators.plainVariant(idle))));
    }

    /** An alarm, lit while it is alarmed — which is the whole of what it has to say. */
    private static void alarmSensor(BlockModelGenerators blockModels, Block block) {
        Identifier quiet = ModelTemplates.CUBE_ALL.create(block,
                TextureMapping.cube(TextureMapping.getBlockTexture(block)),
                blockModels.modelOutput);
        Identifier raised = ModelTemplates.CUBE_ALL.createWithSuffix(block, "_on",
                TextureMapping.cube(TextureMapping.getBlockTexture(block, "_on")),
                blockModels.modelOutput);
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block)
                        .with(BlockModelGenerators.createBooleanModelDispatch(
                                play.xponer.astronima.block.EnvironmentSensorBlock.ALARM,
                                BlockModelGenerators.plainVariant(raised),
                                BlockModelGenerators.plainVariant(quiet))));
    }

    private static void alarm(BlockModelGenerators blockModels) {
        Block block = ModBlocks.ALARM.get();
        Identifier quiet = ModelTemplates.CUBE_BOTTOM_TOP.create(block,
                new TextureMapping()
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"))
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                        .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_bottom")),
                blockModels.modelOutput);
        Identifier sounding = ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(block, "_lit",
                new TextureMapping()
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side_lit"))
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                        .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_bottom")),
                blockModels.modelOutput);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block)
                        .with(BlockModelGenerators.createBooleanModelDispatch(
                                BlockStateProperties.LIT,
                                BlockModelGenerators.plainVariant(sounding),
                                BlockModelGenerators.plainVariant(quiet))));
    }

    /**
     * The tank: a hand-authored pressure vessel — an octagonal shell on a foot ring with
     * the valve boss on top — because the mod's whole point of storage deserves to look
     * like a vessel rather than a crate. Datagen has no template for custom geometry, so
     * the model lives in {@code src/main/resources} like the pipe's and the valve's.
     */
    private static void gasTank(BlockModelGenerators blockModels) {
        Identifier model =
                Identifier.fromNamespaceAndPath(Astronima.MODID, "block/gas_tank");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(
                ModBlocks.GAS_TANK.get(), BlockModelGenerators.plainVariant(model)));
        blockModels.registerSimpleItemModel(ModBlocks.GAS_TANK.get(), model);
    }

    /**
     * The port, showing which face opens into the room.
     *
     * <p>Rule 9: the model is the lesson — and the machine-io plan records how it went
     * wrong once: the bore shipped as a flat texture painted to look like a hole, and a
     * painted hole is not a hole. The model is now hand-authored geometry with a genuinely
     * recessed mouth behind a rim, so "point the hole at the air" needs no explaining and
     * a port buried in rock is visibly buried. Its collision stays the full cube (no
     * {@code getShape} override on the block), which is what keeps a port airtight in a
     * wall.
     */
    private static void gasPort(BlockModelGenerators blockModels) {
        Identifier model = Identifier.fromNamespaceAndPath(Astronima.MODID, "block/gas_port");
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.GAS_PORT.get(),
                                BlockModelGenerators.plainVariant(model))
                        .with(BlockModelGenerators.ROTATION_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.GAS_PORT.get(), model);
    }

    /**
     * The airlock controller: a panel face toward the chamber, plated on the other five.
     *
     * <p>The same orientable pattern as the port, because the lesson is the same — the
     * panel is the mouth of the machine and it faces the space it serves.
     */
    private static void airlockController(BlockModelGenerators blockModels) {
        Identifier model = ModelTemplates.CUBE_ORIENTABLE.create(
                ModBlocks.AIRLOCK_CONTROLLER.get(),
                new TextureMapping()
                        .put(TextureSlot.SIDE,
                                TextureMapping.getBlockTexture(ModBlocks.AIRLOCK_CONTROLLER.get()))
                        .put(TextureSlot.FRONT,
                                TextureMapping.getBlockTexture(ModBlocks.AIRLOCK_CONTROLLER.get(), "_panel"))
                        .put(TextureSlot.TOP,
                                TextureMapping.getBlockTexture(ModBlocks.AIRLOCK_CONTROLLER.get())),
                blockModels.modelOutput);
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.AIRLOCK_CONTROLLER.get(),
                                BlockModelGenerators.plainVariant(model))
                        .with(BlockModelGenerators.ROTATION_FACING));
    }

    /**
     * The valve: a slim inline fitting, showing its setting two ways.
     *
     * <p>The body is a pipe-diameter tube (hand-authored, since datagen has no template for
     * custom geometry) with the bore texture on its ends, so the setting is read by looking
     * down the run as the aperture narrows. The handwheel is drawn on top by the block
     * entity renderer and turns with the setting — a second channel for the same value,
     * because the bore alone is a few pixels and the wheel alone is unreadable at distance.
     *
     * <p>The tube is authored along Z; the {@code AXIS} rotations point it along X or Y so a
     * valve dropped into any run sits flush with the pipes on either side.
     */
    private static void gasValve(BlockModelGenerators blockModels) {
        PropertyDispatch.C2<MultiVariant, Integer, Direction.Axis> dispatch =
                PropertyDispatch.initial(GasValveBlock.SETTING, GasValveBlock.AXIS);
        for (int setting = 0; setting < Valve.SETTINGS; setting++) {
            MultiVariant body = BlockModelGenerators.plainVariant(
                    Identifier.fromNamespaceAndPath(Astronima.MODID, "block/gas_valve_" + setting));
            dispatch = dispatch
                    .select(setting, Direction.Axis.Z, body)
                    .select(setting, Direction.Axis.X, body.with(BlockModelGenerators.Y_ROT_90))
                    .select(setting, Direction.Axis.Y, body.with(BlockModelGenerators.X_ROT_90));
        }
        // The item shows the open valve with its wheel baked in, so it is not mistaken for
        // a bare pipe in the inventory.
        blockModels.registerSimpleItemModel(ModBlocks.GAS_VALVE.get(),
                Identifier.fromNamespaceAndPath(Astronima.MODID, "block/gas_valve_inventory"));

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.GAS_VALVE.get()).with(dispatch));
    }

    /**
     * The heat pipe: a slim sealed tube, one body texture, no dial — unlike the valve it
     * shares an axis-property shape with, there is nothing on it for a player to set.
     */
    private static void ammoniaHeatPipe(BlockModelGenerators blockModels) {
        MultiVariant body = BlockModelGenerators.plainVariant(
                Identifier.fromNamespaceAndPath(Astronima.MODID, "block/ammonia_heat_pipe"));
        PropertyDispatch.C1<MultiVariant, Direction.Axis> dispatch =
                PropertyDispatch.initial(play.xponer.astronima.block.AmmoniaHeatPipeBlock.AXIS)
                        .select(Direction.Axis.Z, body)
                        .select(Direction.Axis.X, body.with(BlockModelGenerators.Y_ROT_90))
                        .select(Direction.Axis.Y, body.with(BlockModelGenerators.X_ROT_90));

        blockModels.registerSimpleItemModel(ModBlocks.AMMONIA_HEAT_PIPE.get(),
                Identifier.fromNamespaceAndPath(Astronima.MODID, "block/ammonia_heat_pipe"));

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.AMMONIA_HEAT_PIPE.get()).with(dispatch));
    }

    /**
     * The pump, showing direction and whether it is running.
     *
     * <p>The arrow is on the outlet face so which way it pushes is visible at placement
     * rather than discovered afterwards, and the lamp separates "running" from "stalled"
     * — two states that look identical without it and need opposite fixes.
     */
    private static void gasPump(BlockModelGenerators blockModels) {
        Identifier stopped = pumpModel("gas_pump");
        Identifier running = pumpModel("gas_pump_on");

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(ModBlocks.GAS_PUMP.get())
                        .with(BlockModelGenerators.createBooleanModelDispatch(
                                GasPumpBlock.RUNNING,
                                BlockModelGenerators.plainVariant(running),
                                BlockModelGenerators.plainVariant(stopped)))
                        .with(BlockModelGenerators.ROTATION_FACING));
    }

    /**
     * One pump model: suction on the back, discharge on the front, body on the rest.
     *
     * <p>{@code FACING} is the direction it pushes, and {@code ROTATION_FACING} treats
     * north as the unrotated front — so north carries the discharge and south, the face
     * behind it, carries the suction.
     */
    /**
     * The pump's two models, hand-authored under {@code src/main/resources}.
     *
     * <p>Geometry, not painted textures. The discharge is a stub that genuinely
     * protrudes three units clear of the face and the suction is a rim leaving a real
     * 8x8 hole you can see into. The previous attempt drew both as flat 16x16 images
     * and at any normal viewing distance they read as the same dark square — a painted
     * hole is not a hole, and only real elements catch the light differently.
     *
     * <p>{@code ModelTemplate} can only fill in a parent and textures, so as with the
     * pipe (rule 3) the geometry is authored and only the blockstate is generated.
     */
    private static Identifier pumpModel(String name) {
        return Identifier.fromNamespaceAndPath(Astronima.MODID, "block/" + name);
    }

    /**
     * The pipe's blockstate: a core, plus an arm for each face it is joined on.
     *
     * <p>Multipart rather than one model per combination, because six booleans is
     * sixty-four models and every one of them would have to be regenerated to change
     * the shape once.
     *
     * <p>The two geometry models are hand-authored under {@code src/main/resources}.
     * Datagen owns JSON (rule 3), but {@code ModelTemplate} can only fill in a parent
     * and textures — it cannot express arbitrary elements — so vanilla ships its own
     * fence and pane geometry the same way.
     */
    private static void gasPipe(BlockModelGenerators blockModels) {
        MultiVariant core = BlockModelGenerators.plainVariant(
                Identifier.fromNamespaceAndPath(Astronima.MODID, "block/gas_pipe_core"));
        Identifier arm = Identifier.fromNamespaceAndPath(Astronima.MODID, "block/gas_pipe_arm");

        MultiPartGenerator generator = MultiPartGenerator.multiPart(ModBlocks.GAS_PIPE.get())
                .with(core);
        for (Direction direction : Direction.values()) {
            generator = generator.with(
                    BlockModelGenerators.condition()
                            .term(GasPipeBlock.property(direction), true),
                    armVariant(arm, direction));
        }
        blockModels.blockStateOutput.accept(generator);
    }

    /**
     * The cable's blockstate: the pipe's core-plus-arms shape, on the electrical network.
     *
     * <p>Identical machinery to {@link #gasPipe} — a slim core and an arm per joined face,
     * multipart so one shape change does not regenerate sixty-four models, and the two geometry
     * models hand-authored because {@code ModelTemplate} cannot express arbitrary elements. The
     * arm rotation table is literally the pipe's, reused. What differs is the block and its
     * texture; the point of a cable looking like a run rather than a wall is the same.
     */
    private static void powerCable(BlockModelGenerators blockModels) {
        MultiVariant core = BlockModelGenerators.plainVariant(
                Identifier.fromNamespaceAndPath(Astronima.MODID, "block/power_cable_core"));
        Identifier arm = Identifier.fromNamespaceAndPath(Astronima.MODID, "block/power_cable_arm");

        MultiPartGenerator generator = MultiPartGenerator.multiPart(ModBlocks.POWER_CABLE.get())
                .with(core);
        for (Direction direction : Direction.values()) {
            generator = generator.with(
                    BlockModelGenerators.condition()
                            .term(PowerCableBlock.property(direction), true),
                    armVariant(arm, direction));
        }
        blockModels.blockStateOutput.accept(generator);
    }

    /** The arm model is authored pointing north; every other face is a rotation of it. */
    private static MultiVariant armVariant(Identifier arm, Direction direction) {
        MultiVariant variant = BlockModelGenerators.plainVariant(arm);
        return switch (direction) {
            case NORTH -> variant;
            case EAST -> variant.with(BlockModelGenerators.Y_ROT_90);
            case SOUTH -> variant.with(BlockModelGenerators.Y_ROT_180);
            case WEST -> variant.with(BlockModelGenerators.Y_ROT_270);
            case UP -> variant.with(BlockModelGenerators.X_ROT_270);
            case DOWN -> variant.with(BlockModelGenerators.X_ROT_90);
        };
    }

    @Override
    protected java.util.stream.Stream<? extends net.minecraft.core.Holder<net.minecraft.world.level.block.Block>> getKnownBlocks() {
        return super.getKnownBlocks().filter(holder -> holder.value() != ModBlocks.GLOW_STICK.get());
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // Non-template, like GRAB_RAIL: the model is hand-authored (a plinth and a column, not
        // anything the cube generators can describe) — this call only wires the blockstate and
        // item model to the model file that already exists on disk.
        blockModels.createNonTemplateHorizontalBlock(ModBlocks.TELESCOPE.get());
        gasPipe(blockModels);
        gasTank(blockModels);
        gasPort(blockModels);
        gasValve(blockModels);
        ammoniaHeatPipe(blockModels);
        gasPump(blockModels);
        airlockController(blockModels);
        presenceSensor(blockModels);
        for (var sensor : java.util.List.of(ModBlocks.VACUUM_SENSOR, ModBlocks.STARVING_SENSOR,
                ModBlocks.FREEZING_SENSOR)) {
            alarmSensor(blockModels, sensor.get());
        }
        blockModels.createTrivialCube(ModBlocks.ASTEROID_ROCK.get());
        blockModels.createTrivialCube(ModBlocks.REGOLITH.get());
        blockModels.createTrivialCube(ModBlocks.WATER_ICE.get());
        blockModels.createTrivialCube(ModBlocks.CHLORATE_ORE.get());
        blockModels.createTrivialCube(ModBlocks.HEMATITE_ORE.get());
        blockModels.createTrivialCube(ModBlocks.METAL_RICH_ORE.get());
        blockModels.createTrivialCube(ModBlocks.ASTERIUM_BLOCK.get());
        // Simplified, named per rule 8: a plain cube for now, not the distinctive shape a real
        // altar deserves - design/astra-ritual-grammar.md's own multiblock leaf is about the
        // structure detection and the live draw, not the model art.
        blockModels.createTrivialCube(ModBlocks.ASTRA_ALTAR.get());
        blockModels.createTrivialCube(ModBlocks.PACKED_TAILINGS.get());
        blockModels.createTrivialBlock(ModBlocks.INCUBATOR.get(), TexturedModel.CUBE_TOP_BOTTOM);
        blockModels.createTrivialBlock(ModBlocks.MICROSCOPE.get(), TexturedModel.CUBE_TOP_BOTTOM);
        deconStation(blockModels);
        blockModels.createTrivialBlock(ModBlocks.SYNTHESISER.get(), TexturedModel.CUBE_TOP_BOTTOM);
        blockModels.createTrivialBlock(ModBlocks.ORE_CRUSHER.get(), TexturedModel.CUBE_TOP_BOTTOM);
        blockModels.createTrivialBlock(ModBlocks.COLD_FORGE.get(), TexturedModel.CUBE_TOP_BOTTOM);
        blockModels.createTrivialBlock(ModBlocks.MAGNETIC_SEPARATOR.get(), TexturedModel.CUBE_TOP_BOTTOM);
        blockModels.createTrivialBlock(ModBlocks.WINNOWING_TABLE.get(), TexturedModel.CUBE_TOP_BOTTOM);
        blockModels.createTrivialBlock(ModBlocks.CARGO_CRATE.get(), TexturedModel.CUBE_TOP_BOTTOM);
        blockModels.createTrivialBlock(ModBlocks.SOLAR_RETORT.get(), TexturedModel.CUBE_TOP_BOTTOM);
        blockModels.createTrivialCube(ModBlocks.HULL_PLATE.get());
        blockModels.createTrivialCube(ModBlocks.INSULATED_HULL_PLATE.get());
        blockModels.createTrivialCube(ModBlocks.PAINTED_HULL_PLATE.get());
        blockModels.createTrivialCube(ModBlocks.PAINTED_INSULATED_HULL_PLATE.get());
        // Non-template, like vanilla's ladder: the model is hand-authored because a rail is
        // two flat faces rather than anything the cube generators can describe.
        blockModels.createNonTemplateHorizontalBlock(ModBlocks.GRAB_RAIL.get());
        purgeValve(blockModels);
        blockModels.createTrivialCube(ModBlocks.SOLAR_ARRAY.get());
        blockModels.createTrivialCube(ModBlocks.POWER_CELL.get());
        powerCable(blockModels);
        blockModels.createTrivialCube(ModBlocks.COMBUSTION_GENERATOR.get());
        blockModels.createTrivialCube(ModBlocks.FUEL_CELL.get());
        blockModels.createTrivialCube(ModBlocks.CARBONYL_REFINER.get());
        blockModels.createTrivialCube(ModBlocks.FLUIDIZED_BED.get());
        blockModels.createTrivialCube(ModBlocks.ILMENITE_ORE.get());
        blockModels.createTrivialCube(ModBlocks.ELECTROLYSIS_CELL.get());
        blockModels.createTrivialCube(ModBlocks.SLS_PRINTER.get());
        blockModels.createTrivialCube(ModBlocks.CRACKING_TOWER.get());
        blockModels.createTrivialCube(ModBlocks.POLYMERIZER.get());
        blockModels.createTrivialCube(ModBlocks.ACOUSTIC_FOAM.get());
        blockModels.createNonTemplateHorizontalBlock(ModBlocks.SLEEPING_BAG.get());
        blockModels.registerSimpleFlatItemModel(ModBlocks.SLEEPING_BAG.get());
        blockModels.createTrivialCube(ModBlocks.WATER_ELECTROLYZER.get());
        blockModels.createTrivialCube(ModBlocks.SABATIER_REACTOR.get());
        blockModels.createTrivialCube(ModBlocks.BOSCH_REACTOR.get());
        blockModels.createTrivialCube(ModBlocks.TROILITE_ROASTER.get());
        blockModels.createTrivialCube(ModBlocks.HALITE_ORE.get());
        blockModels.createTrivialCube(ModBlocks.DOWNS_CELL.get());
        blockModels.createTrivialCube(ModBlocks.SULFURIC_ACID_PLANT.get());
        blockModels.createTrivialCube(ModBlocks.HEAVY_WATER_CELL.get());
        blockModels.createTrivialCube(ModBlocks.TITANIUM_CELL.get());
        blockModels.createTrivialCube(ModBlocks.ZONE_REFINER.get());
        blockModels.createTrivialCube(ModBlocks.FLUORITE_ORE.get());
        blockModels.createTrivialCube(ModBlocks.HF_DIGESTER.get());
        blockModels.createTrivialCube(ModBlocks.CLEANROOM_CONTROLLER.get());
        blockModels.createTrivialCube(ModBlocks.ETCH_STATION.get());
        blockModels.createTrivialCube(ModBlocks.STORAGE_FRAME.get());
        blockModels.createTrivialCube(ModBlocks.STORAGE_DRIVE.get());
        blockModels.createTrivialCube(ModBlocks.STORAGE_TERMINAL.get());
        blockModels.createTrivialCube(ModBlocks.INDUCTION_FURNACE.get());
        blockModels.createTrivialCube(ModBlocks.IRON_SMELTER.get());
        blockModels.createTrivialCube(ModBlocks.VR_SIMULATION_POD.get());
        blockModels.createTrivialCube(ModBlocks.PARAFFIN_THERMAL_MASS.get());
        blockModels.createTrivialCube(ModBlocks.CRYO_TANK.get());
        blockModels.createTrivialCube(ModBlocks.CRYO_COOLER.get());
        blockModels.createTrivialCube(ModBlocks.FREEZE_DRYER.get());
        blockModels.createTrivialCube(ModBlocks.RTG.get());
        blockModels.registerSimpleFlatItemModel(ModBlocks.GRAB_RAIL.get());
        // Machines get a distinct deck and underside. A cube wearing one texture on
        // all six faces reads as a sticker box, and the top being another copy of the
        // front is exactly what gives it away.
        oxygenCandle(blockModels);
        blockModels.createTrivialBlock(ModBlocks.SCRUBBER.get(), TexturedModel.CUBE_TOP_BOTTOM);
        alarm(blockModels);
        blockModels.createTrivialBlock(ModBlocks.PRE_BREATHE_STATION.get(), TexturedModel.CUBE_TOP_BOTTOM);
        blockModels.createTrivialBlock(ModBlocks.DEHUMIDIFIER.get(), TexturedModel.CUBE_TOP_BOTTOM);
        // MoldBlock.getRenderShape() is INVISIBLE - the real geometry is a live, per-position
        // branching cluster (sim/MoldBranching), submitted every frame by
        // MoldBlockEntityRenderer, not a baked model at all (design/mold-growth.md §3c). This
        // trivial cube is never actually drawn; it exists only because an INVISIBLE-shape block
        // still needs a real model/blockstate on disk, the same as GAS_POCKET_CORE just below.
        blockModels.createTrivialCube(ModBlocks.MOLD.get());
        // Torch geometry (upright + wall) with our own spent texture.
        blockModels.createNormalTorch(ModBlocks.UNLIT_TORCH.get(), ModBlocks.UNLIT_WALL_TORCH.get());
        blockModels.createTrivialCube(ModBlocks.GAS_POCKET_CORE.get());
        bulkheadDoor(blockModels);

        blockModels.registerSimpleFlatItemModel(ModBlocks.PURGE_VALVE.get(),
                "_side");

        itemModels.generateFlatItem(ModItems.GAS_ANALYZER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WRENCH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WIRE_COIL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WIRE_RIBBON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WIRE_CUTTERS.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.WIRE_SNIPS.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.DIAGNOSTIC_GOGGLES.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BASIC_BIOMONITOR_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PATHOGEN_ANALYZER_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PETRI_DISH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DOSE.get(), ModelTemplates.FLAT_ITEM);
        // Every wire-layer part, from the set that defines them (rule 20). An item with no
        // model renders as the missing-texture chequerboard, and nothing but a person standing
        // in front of it can see that.
        for (var part : ModItems.PARTS.values()) {
            itemModels.generateFlatItem(part.get(), ModelTemplates.FLAT_ITEM);
        }
        itemModels.generateFlatItem(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HEPA_FILTER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CHLORATE_POWDER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MINERAL_SALTS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.OXYGEN_TANK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.OXYGEN_TANK_EMPTY.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.THOLIN_CLUMP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.IRON_ROD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.REFRACTORY_LINING.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PRECISION_BEARING.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CONTROL_BOARD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GAS_SEAL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.REINFORCED_FRAME.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CRUSHED_ORE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CRUSHED_ILMENITE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.IRON_POWDER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TITANIA.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SILICA_AEROGEL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RTG_CORE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GEIGER_COUNTER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MAGNESIUM_OXIDE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SLAG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SILICON_ELECTRODE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ALUMINUM_ELECTRODE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SILICON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ALUMINUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SODIUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WAFER_SILICON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HYDROFLUORIC_ACID.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GYPSUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ETCHED_DIE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FLUOROSILICIC_ACID.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DATA_CELL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CELL_COMPRESSOR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SINTERED_FRAME.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.POLYETHYLENE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MYLAR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.KAPTON_TAPE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CARBON_POWDER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SULFURIC_ACID.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.AMMONIA_CANISTER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.AMMONIA_CANISTER_EMPTY.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PARAFFIN_WAX.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.METAL_BILLET.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TOOL_HEAD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HAMMER_STONE.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.METEORIC_PICKAXE.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.PRY_BAR.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.IMPROVISED_PICKAXE.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.METEORIC_SHOVEL.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.METEORIC_AXE.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.IRON_NICKEL_GRAINS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PLATINUM_GROUP_GRAINS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TAILINGS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BAKED_SILICATE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MAGNETIC_BOOTS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SLUDGE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PURE_NICKEL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TITANIUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SEISMIC_PROBE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STRIKER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EVA_SUIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SEALANT_PATCH.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LATCH_SET.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CALIBRATED_VALVE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.INSULATION_WEAVE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SALVAGED_CIRCUIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPECTROGRAPH.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.SPECTRAL_PLATE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.COHERENCE_METER.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.ASTRA_FIELD_METER.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.ASTRA_COLLECTOR.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.ASTRA_SOUNDER.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        itemModels.generateFlatItem(ModItems.ASTERIUM_GRAINS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CELESTIAL_ATLAS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WIDE_APERTURE_LENS.get(), ModelTemplates.FLAT_ITEM);
        // One flat item per filter token (design/astra-research-m4b.md §1), from the same
        // enum-driven roster ModItems.FILTER_TOKENS registers from — the same rule 20 reasoning
        // as the wire-layer parts loop above.
        for (var token : ModItems.FILTER_TOKENS.values()) {
            itemModels.generateFlatItem(token.get(), ModelTemplates.FLAT_ITEM);
        }
    }
}
