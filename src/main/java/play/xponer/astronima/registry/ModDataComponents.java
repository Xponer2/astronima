package play.xponer.astronima.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.item.CircuitPlate;
import play.xponer.astronima.item.WireCoil;
import play.xponer.astronima.item.WireRibbon;
import play.xponer.astronima.sim.logic.Circuit;
import play.xponer.astronima.sim.tool.ToolHead;

/** Data carried on item stacks. */
public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Astronima.MODID);

    /**
     * Packed {@link play.xponer.astronima.sim.suit.SuitCondition} mask: which of a
     * suit's subsystems work. Living on the stack means a suit carries its own repair
     * history — a salvaged spare is exactly as broken as it was left.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SUIT_CONDITION =
            COMPONENTS.register("suit_condition", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * Remaining life of each repaired subsystem, packed a nibble apiece (SuitWear).
     * Separate from the condition mask because they answer different questions: the
     * mask says whether a subsystem was ever fixed, this says how long that fix has
     * left.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SUIT_WEAR =
            COMPONENTS.register("suit_wear", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Sub-nibble stress accumulated toward the next unit of wear (SuitWear). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> SUIT_STRESS =
            COMPONENTS.register("suit_stress", () -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build());

    /**
     * Grade and crusher setting of a crushed batch, packed (see OreGrade).
     *
     * <p>Packed rather than carrying the mineral assemblage itself, so two batches
     * ground at the same setting stack together. An inventory of one-item piles would
     * be a worse outcome than any fidelity the full assemblage would buy.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ORE_BATCH =
            COMPONENTS.register("ore_batch", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * The crusher setting a batch of crushed ilmenite was ground at, in permille
     * ({@code SETTING_SCALE} grammar), from which {@code Comminution.particleSizeMicrons} gives
     * the grain size the fluidized bed's fluidization window slides on.
     *
     * <p>Separate from {@link #ORE_BATCH} because crushed ilmenite is a pure mineral, not an
     * assemblage: it has a grind but no grade, and packing a meaningless grade into it would be a
     * number that lies. Two batches ground at the same setting stack, exactly as crushed ore does.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GRIND_FINENESS =
            COMPONENTS.register("grind_fineness", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Nickel fraction of a piece of metal, carried from seam to billet to tool. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> METAL_NICKEL =
            COMPONENTS.register("metal_nickel", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    /** How well a head was forged, 0..1, which becomes the tool's durability. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> METAL_QUALITY =
            COMPONENTS.register("metal_quality", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    /**
     * How sound a sintered frame came out, 0..1 — {@code LaserSintering.soundness()} at the
     * moment its batch finished, stamped onto the item the same way {@link #METAL_QUALITY}
     * already stamps a forged tool head. Present but weak when printed outside the sound
     * pocket, exactly as {@code design/sls.md} §S1 describes.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> SINTER_SOUNDNESS =
            COMPONENTS.register("sinter_soundness", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    private static final Codec<ToolHead> TOOL_HEAD_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.DOUBLE.fieldOf("edge").forGetter(ToolHead::edge),
                    Codec.DOUBLE.fieldOf("hardness").forGetter(ToolHead::hardness),
                    Codec.BOOL.fieldOf("cracked").forGetter(ToolHead::cracked),
                    Codec.INT.fieldOf("resharpenings").forGetter(ToolHead::resharpenings)
            ).apply(instance, ToolHead::new));

    private static final StreamCodec<ByteBuf, ToolHead> TOOL_HEAD_STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, ToolHead::edge,
                    ByteBufCodecs.DOUBLE, ToolHead::hardness,
                    ByteBufCodecs.BOOL, ToolHead::cracked,
                    ByteBufCodecs.VAR_INT, ToolHead::resharpenings,
                    ToolHead::new);

    /**
     * Set on a Petri dish that has been wiped across a surface: true if something came off it,
     * false if the swab found nothing.
     *
     * <p>False is the useful value. A plate that came back sterile is <em>spent</em> and the fact
     * that it came back sterile is the information the player paid a plate and a night for — so it
     * has to survive on the item rather than being a message that scrolls away.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SWABBED =
            COMPONENTS.register("swabbed", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /** True when a head was worked past its ductility limit and will chip in use. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> METAL_CRACKED =
            COMPONENTS.register("metal_cracked", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /**
     * The live state of a tool: how sharp it is, how hard its head was forged, whether
     * that head is cracked, and how many grinds it has left.
     *
     * <p>Lives on the stack so a tool carries its own history. Two pickaxes off the
     * same bench are not interchangeable, because the metal in them was not worked the
     * same way.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolHead>> TOOL_STATE =
            COMPONENTS.register("tool_state", () -> DataComponentType.<ToolHead>builder()
                    .persistent(TOOL_HEAD_CODEC)
                    .networkSynchronized(TOOL_HEAD_STREAM_CODEC)
                    .build());

    /**
     * The terminal a wrench is armed to fill, set by clicking a terminal on a machine
     * panel and cleared by the click that lands a device on it.
     *
     * <p>On the stack rather than on the machine so the tool can say what it is about to do
     * — an armed wrench is a mode, and a mode the player cannot see is a trap.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<play.xponer.astronima.item.TerminalArm>>
            TERMINAL_ARM = COMPONENTS.register("terminal_arm",
                    () -> DataComponentType.<play.xponer.astronima.item.TerminalArm>builder()
                            .persistent(play.xponer.astronima.item.TerminalArm.CODEC)
                            .networkSynchronized(play.xponer.astronima.item.TerminalArm.STREAM_CODEC)
                            .build());

    /** What a coil of wire is set to: metal, insulation colour and routing mode. */
    /**
     * Which antimicrobial is in a vial.
     *
     * <p>A name on the stack rather than three item ids: a broad-spectrum dose and a narrow one are
     * the same idea with a different target, and rule 8 refuses three registrations for that.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DOSE =
            COMPONENTS.register("dose", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /**
     * What is growing on a Petri dish, and everything found out about it so far.
     *
     * <p>On the <strong>item</strong>, because the dish is the notebook: it goes from the incubator
     * to the microscope to the disc reader accumulating observations, and each machine writes what
     * it found onto the thing it hands on. A laboratory whose knowledge lived in the machines would
     * be a laboratory where the plates are interchangeable, which is the opposite of one.
     */
    public static final DeferredHolder<DataComponentType<?>,
            DataComponentType<play.xponer.astronima.sim.lab.Culture>> CULTURE =
            COMPONENTS.register("culture", () -> DataComponentType
                    .<play.xponer.astronima.sim.lab.Culture>builder()
                    .persistent(play.xponer.astronima.item.PetriDishItem.CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WireCoil>> WIRE_COIL =
            COMPONENTS.register("wire_coil", () -> DataComponentType.<WireCoil>builder()
                    .persistent(WireCoil.CODEC)
                    .networkSynchronized(WireCoil.STREAM_CODEC)
                    .build());

    /**
     * Where the loose end of a coil is currently held.
     *
     * <p>Synced, because the ghost preview is drawn on the client and it has to know which pixel
     * the run is being pulled from.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WireCoil.Anchor>> WIRE_ANCHOR =
            COMPONENTS.register("wire_anchor", () -> DataComponentType.<WireCoil.Anchor>builder()
                    .persistent(WireCoil.Anchor.CODEC)
                    .networkSynchronized(WireCoil.Anchor.STREAM_CODEC)
                    .build());

    /**
     * The first pixel the snips have marked, waiting for a second click to cut the run between
     * them. Reuses {@link WireCoil.Anchor} rather than a second "one pixel on a face" record —
     * it is the same fact wire snips wants that the coil already wanted (rule 46).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WireCoil.Anchor>> SNIPS_ANCHOR =
            COMPONENTS.register("snips_anchor", () -> DataComponentType.<WireCoil.Anchor>builder()
                    .persistent(WireCoil.Anchor.CODEC)
                    .networkSynchronized(WireCoil.Anchor.STREAM_CODEC)
                    .build());

    /** What a ribbon is set to: lane count and routing mode. See {@code design/bus.md}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WireRibbon>> WIRE_RIBBON =
            COMPONENTS.register("wire_ribbon", () -> DataComponentType.<WireRibbon>builder()
                    .persistent(WireRibbon.CODEC)
                    .networkSynchronized(WireRibbon.STREAM_CODEC)
                    .build());

    /**
     * Where lane 0 of a ribbon is currently held — reuses {@link WireCoil.Anchor} the same way
     * {@link #SNIPS_ANCHOR} already does (rule 46): it is the same fact, "one pixel on one face",
     * that every tool in this tier that holds an end wants.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WireCoil.Anchor>> RIBBON_ANCHOR =
            COMPONENTS.register("ribbon_anchor", () -> DataComponentType.<WireCoil.Anchor>builder()
                    .persistent(WireCoil.Anchor.CODEC)
                    .networkSynchronized(WireCoil.Anchor.STREAM_CODEC)
                    .build());

    /** The circuit designed into a plate. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Circuit>> CIRCUIT =
            COMPONENTS.register("circuit", () -> DataComponentType.<Circuit>builder()
                    .persistent(CircuitPlate.CODEC)
                    .networkSynchronized(CircuitPlate.STREAM_CODEC)
                    .build());

    /**
     * How far the part in your hand has been turned before it is placed.
     *
     * <p>Placement points a part's output the way the player is looking, which is right most of
     * the time and wrong the rest of it. The wrench turns one that is already on the wall; this is
     * the same choice made <em>before</em> committing, so a row of ten gates all facing the same
     * awkward way is a keypress rather than ten wrench clicks.
     *
     * <p>Rides on the stack, so it is remembered between placements and the ghost — drawn on the
     * client — can read it without a packet of its own.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            PART_ROTATION = COMPONENTS.register("part_rotation",
                    () -> DataComponentType.<Integer>builder()
                            .persistent(com.mojang.serialization.Codec.INT)
                            .networkSynchronized(
                                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                            .build());

    private static final Codec<play.xponer.astronima.sim.magic.CapturedSpectrum> CAPTURED_SPECTRUM_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.xmap(play.xponer.astronima.sim.magic.ObservationTarget::valueOf,
                                    Enum::name)
                            .fieldOf("target")
                            .forGetter(play.xponer.astronima.sim.magic.CapturedSpectrum::target),
                    Codec.DOUBLE.fieldOf("vacuum_kpa")
                            .forGetter(play.xponer.astronima.sim.magic.CapturedSpectrum::vacuumKPa)
            ).apply(instance, play.xponer.astronima.sim.magic.CapturedSpectrum::new));

    private static final StreamCodec<ByteBuf, play.xponer.astronima.sim.magic.CapturedSpectrum>
            CAPTURED_SPECTRUM_STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.map(
                            play.xponer.astronima.sim.magic.ObservationTarget::valueOf, Enum::name),
                    play.xponer.astronima.sim.magic.CapturedSpectrum::target,
                    ByteBufCodecs.DOUBLE, play.xponer.astronima.sim.magic.CapturedSpectrum::vacuumKPa,
                    play.xponer.astronima.sim.magic.CapturedSpectrum::new);

    /**
     * A spectral plate's exposure, or absent for a blank one. See
     * {@link play.xponer.astronima.sim.magic.CapturedSpectrum}'s own javadoc for why this stores
     * the inputs rather than the resulting lines.
     */
    public static final DeferredHolder<DataComponentType<?>,
            DataComponentType<play.xponer.astronima.sim.magic.CapturedSpectrum>> CAPTURED_SPECTRUM =
            COMPONENTS.register("captured_spectrum", () -> DataComponentType
                    .<play.xponer.astronima.sim.magic.CapturedSpectrum>builder()
                    .persistent(CAPTURED_SPECTRUM_CODEC)
                    .networkSynchronized(CAPTURED_SPECTRUM_STREAM_CODEC)
                    .build());

    private static final Codec<play.xponer.astronima.sim.astra.AstraCharge> ASTRA_CHARGE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.fieldOf("amount")
                            .forGetter(play.xponer.astronima.sim.astra.AstraCharge::amount),
                    Codec.LONG.fieldOf("last_update_game_time")
                            .forGetter(play.xponer.astronima.sim.astra.AstraCharge::lastUpdateGameTime)
            ).apply(instance, play.xponer.astronima.sim.astra.AstraCharge::new));

    private static final StreamCodec<ByteBuf, play.xponer.astronima.sim.astra.AstraCharge>
            ASTRA_CHARGE_STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.FLOAT, play.xponer.astronima.sim.astra.AstraCharge::amount,
                    ByteBufCodecs.VAR_LONG, play.xponer.astronima.sim.astra.AstraCharge::lastUpdateGameTime,
                    play.xponer.astronima.sim.astra.AstraCharge::new);

    /**
     * How much raw astra a collector is currently holding, and as of when — design/
     * astra-extraction-loop.md §3.3's original "tiny buffer, no leak modelled" simplification,
     * upgraded in design/astra-precipitation.md §1.2 to a real, leaking
     * {@link play.xponer.astronima.sim.astra.AstraCharge}: still a small buffer standing in for a
     * real vessel, but the leak itself is real now, computed lazily on read exactly the way
     * {@code AstraFieldStorage} already computes a cell's real density.
     */
    public static final DeferredHolder<DataComponentType<?>,
            DataComponentType<play.xponer.astronima.sim.astra.AstraCharge>> ASTRA_HELD =
            COMPONENTS.register("astra_held", () -> DataComponentType
                    .<play.xponer.astronima.sim.astra.AstraCharge>builder()
                    .persistent(ASTRA_CHARGE_CODEC)
                    .networkSynchronized(ASTRA_CHARGE_STREAM_CODEC)
                    .build());

    /**
     * D2O fraction of a water-bottle potion stack, 0..1 — absent means natural water
     * ({@code HeavyWaterCascade.NATURAL_D2O_FRACTION}), not zero. Carried on the exact same
     * vanilla potion item {@code WaterElectrolyzerBlockEntity} already reads, rather than a new
     * item (design/heavy-water.md §5's own named simplification): the number is what a cascade
     * stage changes, not the container.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> HEAVY_WATER_FRACTION =
            COMPONENTS.register("heavy_water_fraction", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());

    private static final Codec<play.xponer.astronima.sim.storage.DataLedger.Entry>
            DATA_LEDGER_ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("item")
                            .forGetter(play.xponer.astronima.sim.storage.DataLedger.Entry::itemId),
                    Codec.LONG.fieldOf("count")
                            .forGetter(play.xponer.astronima.sim.storage.DataLedger.Entry::count)
            ).apply(instance, play.xponer.astronima.sim.storage.DataLedger.Entry::new));

    private static final Codec<play.xponer.astronima.sim.storage.DataLedger> DATA_LEDGER_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("items_per_slot")
                            .forGetter(play.xponer.astronima.sim.storage.DataLedger::itemsPerSlot),
                    DATA_LEDGER_ENTRY_CODEC.listOf().fieldOf("entries")
                            .forGetter(play.xponer.astronima.sim.storage.DataLedger::entries)
            ).apply(instance, play.xponer.astronima.sim.storage.DataLedger::new));

    private static final StreamCodec<ByteBuf, play.xponer.astronima.sim.storage.DataLedger.Entry>
            DATA_LEDGER_ENTRY_STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    play.xponer.astronima.sim.storage.DataLedger.Entry::itemId,
                    ByteBufCodecs.VAR_LONG,
                    play.xponer.astronima.sim.storage.DataLedger.Entry::count,
                    play.xponer.astronima.sim.storage.DataLedger.Entry::new);

    private static final StreamCodec<ByteBuf, play.xponer.astronima.sim.storage.DataLedger>
            DATA_LEDGER_STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    play.xponer.astronima.sim.storage.DataLedger::itemsPerSlot,
                    DATA_LEDGER_ENTRY_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    play.xponer.astronima.sim.storage.DataLedger::entries,
                    play.xponer.astronima.sim.storage.DataLedger::new);

    /**
     * A data cell's own real manifest — which item, and how many, per
     * {@code sim/storage/DataLedger} (design/data-cells.md §1-2). Lives on the stack, the same
     * "the item is the notebook" reasoning {@link #CULTURE} already carries: a cell is portable
     * precisely because what it holds travels with it.
     */
    public static final DeferredHolder<DataComponentType<?>,
            DataComponentType<play.xponer.astronima.sim.storage.DataLedger>> DATA_CELL_LEDGER =
            COMPONENTS.register("data_cell_ledger", () -> DataComponentType
                    .<play.xponer.astronima.sim.storage.DataLedger>builder()
                    .persistent(DATA_LEDGER_CODEC)
                    .networkSynchronized(DATA_LEDGER_STREAM_CODEC)
                    .build());

    private ModDataComponents() {}
}
