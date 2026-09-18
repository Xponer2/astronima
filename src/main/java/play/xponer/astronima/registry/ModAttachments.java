package play.xponer.astronima.registry;

import com.mojang.serialization.Codec;
import play.xponer.astronima.sim.physio.Hypothermia;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.wire.WireChunk;

/** Per-entity persistent data. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Astronima.MODID);

    /**
     * Carboxyhemoglobin proxy dose, 0..1 (see sim.tox.GasToxicity). Synced so the
     * pulse-oximeter HUD reads it directly; not copied on death — dying "clears" it.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> CO_DOSE =
            ATTACHMENTS.register("co_dose", () -> AttachmentType.builder(() -> 0f)
                    .serialize(Codec.FLOAT.fieldOf("dose"))
                    .sync(ByteBufCodecs.FLOAT)
                    .build());

    /**
     * Accumulated gamma body dose, real sievert (see sim.rad.RadiationDose). Synced so the
     * Geiger counter's HUD reads it directly; not copied on death — dying "clears" it, the
     * same choice {@link #CO_DOSE} already makes.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> RADIATION_DOSE =
            ATTACHMENTS.register("radiation_dose", () -> AttachmentType.builder(() -> 0f)
                    .serialize(Codec.FLOAT.fieldOf("sievert"))
                    .sync(ByteBufCodecs.FLOAT)
                    .build());

    /**
     * Accumulated real hydrofluoric-acid contact dose, in grams (see sim.physio.ChemicalBurn,
     * design/halogens.md §22-23, Part C2). Synced so the biomonitor/VitalsHud can show it the
     * instant a contact event registers; not copied on death, the same choice {@link #RADIATION_DOSE}
     * already makes.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> CHEMICAL_BURN_DOSE =
            ATTACHMENTS.register("chemical_burn_dose", () -> AttachmentType.builder(() -> 0f)
                    .serialize(Codec.FLOAT.fieldOf("grams"))
                    .sync(ByteBufCodecs.FLOAT)
                    .build());

    /**
     * What the player is carrying on their gloves and on their skin.
     *
     * <p>Synced, because rule 7 says a hazard needs an instrument and this one is invisible: the
     * biomonitor has to be able to show a player they are one mistake from a dose <em>before</em>
     * they make it. Not copied on death.
     */
    public static final DeferredHolder<AttachmentType<?>,
            AttachmentType<play.xponer.astronima.physio.CarriedContamination>> CONTAMINATION =
            ATTACHMENTS.register("contamination", () -> AttachmentType.builder(
                            () -> play.xponer.astronima.physio.CarriedContamination.NONE)
                    .serialize(play.xponer.astronima.physio.CarriedContamination.CODEC
                            .fieldOf("carried"))
                    .sync(ByteBufCodecs.fromCodec(
                            play.xponer.astronima.physio.CarriedContamination.CODEC))
                    .build());

    /**
     * A player's three real macronutrient reserves — protein, carbohydrate, fat
     * ({@code sim.physio.Macronutrition}, design/macronutrients.md).
     *
     * <p>Not synced, the same choice {@link #INFECTION} already makes: no HUD reads this yet
     * (design/macronutrients.md §6), only the server-side {@code immunityOf} rewire and the
     * {@code /astronima nutrition} report. Not copied on death, matching every other real dose in
     * this file — a fresh body starts with full reserves rather than inheriting a starved one.
     */
    public static final DeferredHolder<AttachmentType<?>,
            AttachmentType<play.xponer.astronima.physio.CarriedMacronutrition>> MACRONUTRITION =
            ATTACHMENTS.register("macronutrition", () -> AttachmentType
                    .builder(() -> play.xponer.astronima.physio.CarriedMacronutrition.FULL)
                    .serialize(play.xponer.astronima.physio.CarriedMacronutrition.CODEC
                            .fieldOf("macronutrition"))
                    .build());

    /**
     * What a player has earned toward a locked codex block — see
     * {@code progression/Unlocks.java}. Synced, deliberately the opposite of
     * {@link #INFECTION}'s own choice: the codex is a client-side renderer deciding what to draw
     * every frame, so the client needs this without a round trip.
     *
     * <p>The codec lives here, not on {@code Unlocks} itself — {@code Unlocks}'s own doc explains
     * why: {@code com.mojang.serialization} is not on the test classpath, and this file already
     * needs it for every attachment declared in it, so this is where a codec's cost belongs.
     */
    private static final Codec<play.xponer.astronima.progression.Unlocks> UNLOCKS_CODEC =
            Codec.STRING.listOf().xmap(
                    (java.util.List<String> ids) -> new play.xponer.astronima.progression.Unlocks(
                            new java.util.HashSet<>(ids)),
                    (play.xponer.astronima.progression.Unlocks unlocks) ->
                            new java.util.ArrayList<>(unlocks.earned()));

    public static final DeferredHolder<AttachmentType<?>,
            AttachmentType<play.xponer.astronima.progression.Unlocks>> UNLOCKS =
            ATTACHMENTS.register("unlocks", () -> AttachmentType.builder(
                            () -> play.xponer.astronima.progression.Unlocks.NONE)
                    .serialize(UNLOCKS_CODEC.fieldOf("unlocks"))
                    .sync(ByteBufCodecs.fromCodec(UNLOCKS_CODEC))
                    .build());

    /**
     * Ambient pressure around the player last tick, kPa, updated every tick in every
     * environment (vacuum included) so barotrauma can see a <em>rate</em>.
     *
     * <p>Starts at one atmosphere: a player who has just loaded in has not, from the
     * simulation's point of view, arrived there at any speed. Synced, and doing double duty
     * as the client's read of "how much air is around my head right now" — the same number
     * {@link play.xponer.astronima.client.PressureSoundAttenuation} scales sound by, rather
     * than a second copy that could disagree (rule 13).
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> LAST_AMBIENT_KPA =
            ATTACHMENTS.register("last_ambient_kpa", () -> AttachmentType.builder(() -> 101.325f)
                    .serialize(Codec.FLOAT.fieldOf("kpa"))
                    .sync(ByteBufCodecs.FLOAT)
                    .build());

    /**
     * Dissolved nitrogen tension in tissue, kPa (sim.physio.DecompressionModel).
     * Starts at sea-level equilibrium: the player has been breathing cabin air.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> TISSUE_N2 =
            ATTACHMENTS.register("tissue_n2", () -> AttachmentType.builder(() -> 79f)
                    .serialize(Codec.FLOAT.fieldOf("kpa"))
                    .sync(ByteBufCodecs.FLOAT)
                    .build());

    /**
     * Packed {@link play.xponer.astronima.sim.physio.VitalSigns}: which conditions are
     * currently harming the player and how badly. Synced so the medical monitor can
     * name the cause rather than leaving the player to guess.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> VITALS =
            ATTACHMENTS.register("vitals", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("packed"))
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    /**
     * The illness a player is carrying, if any.
     *
     * <p><strong>Not synced.</strong> That is the design rather than an omission: the whole tier
     * turns on the client knowing only what an instrument tells it. What crosses to the client is
     * the packed {@link #VITALS} — symptoms with no name — and nothing else, so the biomonitor
     * cannot accidentally reveal an organism the player has not identified yet.
     */
    public static final DeferredHolder<AttachmentType<?>,
            AttachmentType<play.xponer.astronima.physio.CarriedInfection>> INFECTION =
            ATTACHMENTS.register("infection", () -> AttachmentType
                    .builder(() -> play.xponer.astronima.physio.CarriedInfection.NONE)
                    .serialize(play.xponer.astronima.physio.CarriedInfection.CODEC
                            .fieldOf("infection"))
                    .build());

    /** Carbon dioxide inside a sealed helmet, kPa. Synced so the suit panel can show it. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> HELMET_CO2 =
            ATTACHMENTS.register("helmet_co2", () -> AttachmentType.builder(() -> 0f)
                    .serialize(Codec.FLOAT.fieldOf("kpa"))
                    .sync(ByteBufCodecs.FLOAT)
                    .build());

    /**
     * Body oxygen reserve, 0..1. Synced because the bubble row draws it every frame.
     *
     * <p>This is what turns suffocation from a state change into an interval: the
     * player watches it fall and has time to act, which is both more accurate and more
     * frightening than dying between two ticks.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> O2_RESERVE =
            ATTACHMENTS.register("o2_reserve", () -> AttachmentType.builder(() -> 1f)
                    .serialize(Codec.FLOAT.fieldOf("reserve"))
                    .sync(ByteBufCodecs.FLOAT)
                    .build());

    /**
     * Body core temperature, K. Synced because the HUD draws it while it is below normal.
     *
     * <p>Persisted for the same reason the oxygen reserve is: a chill that reset on every
     * relog would make the one mechanic in this phase whose whole point is <em>duration</em>
     * into something a player could clear by quitting.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> CORE_TEMPERATURE_K =
            ATTACHMENTS.register("core_temperature_k",
                    () -> AttachmentType.builder(() -> (float) Hypothermia.NORMAL_CORE_K)
                            .serialize(Codec.FLOAT.fieldOf("kelvin"))
                            .sync(ByteBufCodecs.FLOAT)
                            .build());

    /**
     * Speed carried at the end of last tick, blocks/tick.
     *
     * <p>Remembered because a collision destroys the evidence of itself: by the time anything
     * can see that a player has stopped, the velocity that stopped them is already zero.
     * Not synced — the client draws its own speed, which it knows first-hand.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> LAST_SPEED =
            ATTACHMENTS.register("last_speed", () -> AttachmentType.builder(() -> 0f)
                    .serialize(Codec.FLOAT.fieldOf("blocks_per_tick"))
                    .build());

    /** Fractional seconds banked toward the next whole second of suit wear. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> SUIT_STRESS_CARRY =
            ATTACHMENTS.register("suit_stress_carry", () -> AttachmentType.builder(() -> 0f)
                    .serialize(Codec.FLOAT.fieldOf("carry"))
                    .build());

    /** Packed suit self-report; see SuitTelemetry. Synced to drive the suit panel. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> SUIT_TELEMETRY =
            ATTACHMENTS.register("suit_telemetry", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT.fieldOf("packed"))
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    /** Leftover fractional durability for the tank, so slow wear is not lost to rounding. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> SUIT_WEAR_CARRY =
            ATTACHMENTS.register("suit_wear_carry", () -> AttachmentType.builder(() -> 0f)
                    .serialize(Codec.FLOAT.fieldOf("carry"))
                    .build());

    /** The same, for the scrubber cartridge. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> CARTRIDGE_WEAR_CARRY =
            ATTACHMENTS.register("cartridge_wear_carry", () -> AttachmentType.builder(() -> 0f)
                    .serialize(Codec.FLOAT.fieldOf("carry"))
                    .build());

    /**
     * Every length of wire in a chunk (see {@code wire/WireChunk}).
     *
     * <p>The one attachment here that is <strong>not</strong> on a player, and the reason the
     * electrical tier can exist at all: wire is not a block, so it needs somewhere to live that
     * is neither a block nor a block entity. A chunk already saves itself, ships itself to
     * clients and unloads itself, which is exactly the lifecycle a run of cable wants.
     *
     * <p>Synced because the client has to draw it, and changes are pushed with
     * {@code chunk.syncData(...)} after every edit — a wire nobody can see is worse than no
     * wire, because the player will keep laying it.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WireChunk>> WIRES =
            ATTACHMENTS.register("wires", () -> AttachmentType.builder(holder -> new WireChunk())
                    .serialize(WireChunk.CODEC)
                    .sync(WireChunk.STREAM_CODEC)
                    .build());

    /**
     * Lasting damage: scarred lungs, damaged marrow, and how close the player is to each.
     *
     * <p>Synced, because a permanent consequence the player could not watch approach is a
     * punishment (rule 7). Not copied on death.
     */
    public static final DeferredHolder<AttachmentType<?>,
            AttachmentType<play.xponer.astronima.physio.CarriedChronic>> CHRONIC =
            ATTACHMENTS.register("chronic", () -> AttachmentType.builder(
                            () -> play.xponer.astronima.physio.CarriedChronic.NONE)
                    .serialize(play.xponer.astronima.physio.CarriedChronic.CODEC
                            .fieldOf("chronic"))
                    .sync(ByteBufCodecs.fromCodec(
                            play.xponer.astronima.physio.CarriedChronic.CODEC))
                    .build());


    private static Codec<java.util.Set<String>> stringSetCodec() {
        return Codec.STRING.listOf().xmap(java.util.HashSet::new, java.util.ArrayList::new);
    }

    private static Codec<java.util.Map<String, Integer>> stringIntMapCodec() {
        return Codec.unboundedMap(Codec.STRING, Codec.INT);
    }

    /**
     * Public for the gametest save fixture (scenario_research_stage_save_fixture), which
     * round-trips a real save through this exact codec on the game thread - the one place
     * both this class and JSON exist together. Read-only by type.
     */
    public static final Codec<play.xponer.astronima.sim.magic.ResearchState> RESEARCH_CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                    stringSetCodec().fieldOf("captured_targets")
                            .forGetter(play.xponer.astronima.sim.magic.ResearchState::capturedTargets),
                    stringSetCodec().fieldOf("held_claims")
                            .forGetter(play.xponer.astronima.sim.magic.ResearchState::heldClaims),
                    stringSetCodec().fieldOf("identified_objects")
                            .forGetter(play.xponer.astronima.sim.magic.ResearchState::identifiedObjects),
                    stringSetCodec().fieldOf("refuted_combinations")
                            .forGetter(play.xponer.astronima.sim.magic.ResearchState::refutedCombinations),
                    // rule 60: added after this attachment already shipped, so a save written
                    // before this field existed must still load - optionalFieldOf defaults to
                    // empty rather than the whole record failing to decode.
                    stringSetCodec().optionalFieldOf("opened_branches", java.util.Set.of())
                            .forGetter(play.xponer.astronima.sim.magic.ResearchState::openedBranches),
                    // rule 60 again (design/astra-atlas-s3-progression.md 3): stage progress
                    // post-dates every existing save; absent means every claim at stage 0.
                    // The round-trip fixture is the gametest research_stage_save_fixture:
                    // the plain JUnit runner cannot load this class (tried it - hits
                    // NoClassDefFoundError: MappedRegistry), so rule 14 moves the assertion
                    // rather than dropping it.
                    stringIntMapCodec().optionalFieldOf("stage_progress", java.util.Map.of())
                            .forGetter(play.xponer.astronima.sim.magic.ResearchState::stageProgress)
            ).apply(instance, play.xponer.astronima.sim.magic.ResearchState::new));

    /**
     * A player's own research (design/astra-research.md §8, design/astra-research-m1.md).
     *
     * <p><strong>Synced</strong> — added for design/astra-research-m2a.md, the atlas's first real
     * reader: rule 26 is about state a client <em>draws</em>, and the atlas screen now does.
     * (M1 shipped this attachment unsynced on purpose, since nothing read it client-side yet —
     * see that leaf's own note; this is the planned-for graduation, not a change of mind.) Not
     * copied on death, matching {@link #UNLOCKS}: knowledge is not equipment.
     */
    public static final DeferredHolder<AttachmentType<?>,
            AttachmentType<play.xponer.astronima.sim.magic.ResearchState>> RESEARCH =
            ATTACHMENTS.register("research", () -> AttachmentType.builder(
                            () -> play.xponer.astronima.sim.magic.ResearchState.NONE)
                    .serialize(RESEARCH_CODEC.fieldOf("research"))
                    .sync(ByteBufCodecs.fromCodec(RESEARCH_CODEC))
                    .build());

    /** design/eva-mobility.md §1.2's one real fact, four floats, real-valued round-trip. */
    public static final Codec<play.xponer.astronima.sim.gravity.Orientation> ORIENTATION_CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                    Codec.FLOAT.fieldOf("w").forGetter(play.xponer.astronima.sim.gravity.Orientation::w),
                    Codec.FLOAT.fieldOf("x").forGetter(play.xponer.astronima.sim.gravity.Orientation::x),
                    Codec.FLOAT.fieldOf("y").forGetter(play.xponer.astronima.sim.gravity.Orientation::y),
                    Codec.FLOAT.fieldOf("z").forGetter(play.xponer.astronima.sim.gravity.Orientation::z)
            ).apply(instance, play.xponer.astronima.sim.gravity.Orientation::new));

    /**
     * A player's real, free-flight facing (design/eva-mobility.md §1.2) — server-authoritative,
     * synced to every observer so a rolled player's model actually renders rolled for everyone
     * watching, not only for themselves. Not copied on death, matching {@code RESEARCH}: a fresh
     * body starts oriented the ordinary way.
     */
    public static final DeferredHolder<AttachmentType<?>,
            AttachmentType<play.xponer.astronima.sim.gravity.Orientation>> ORIENTATION =
            ATTACHMENTS.register("orientation", () -> AttachmentType.builder(
                            () -> play.xponer.astronima.sim.gravity.Orientation.IDENTITY)
                    .serialize(ORIENTATION_CODEC.fieldOf("orientation"))
                    .sync(ByteBufCodecs.fromCodec(ORIENTATION_CODEC))
                    .build());

    /** design/eva-mobility.md §3.2's one real fact: whether a tether is active, and if so where. */
    public static final Codec<play.xponer.astronima.sim.gravity.TetherState> TETHER_CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.fieldOf("active")
                            .forGetter(play.xponer.astronima.sim.gravity.TetherState::active),
                    Codec.DOUBLE.fieldOf("anchor_x")
                            .forGetter(play.xponer.astronima.sim.gravity.TetherState::anchorX),
                    Codec.DOUBLE.fieldOf("anchor_y")
                            .forGetter(play.xponer.astronima.sim.gravity.TetherState::anchorY),
                    Codec.DOUBLE.fieldOf("anchor_z")
                            .forGetter(play.xponer.astronima.sim.gravity.TetherState::anchorZ),
                    Codec.DOUBLE.fieldOf("rest_length")
                            .forGetter(play.xponer.astronima.sim.gravity.TetherState::restLength)
            ).apply(instance, play.xponer.astronima.sim.gravity.TetherState::new));

    /**
     * A player's real safety line (design/eva-mobility.md §3) — server-authoritative and synced,
     * so a tethered player's own line renders for every observer, not only for themselves. Not
     * copied on death: a fresh body starts with nothing anchored.
     */
    public static final DeferredHolder<AttachmentType<?>,
            AttachmentType<play.xponer.astronima.sim.gravity.TetherState>> TETHER =
            ATTACHMENTS.register("tether", () -> AttachmentType.builder(
                            () -> play.xponer.astronima.sim.gravity.TetherState.NONE)
                    .serialize(TETHER_CODEC.fieldOf("tether"))
                    .sync(ByteBufCodecs.fromCodec(TETHER_CODEC))
                    .build());

    /**
     * What is on the surfaces of a chunk.
     *
     * <p>Synced, and for the reason rule 26 was written down: a contaminated bench nobody can
     * survey is a bench the player will keep working at. The goggles read this on the client.
     */
    public static final DeferredHolder<AttachmentType<?>,
            AttachmentType<play.xponer.astronima.physio.SurfaceContamination>> SURFACES =
            ATTACHMENTS.register("surfaces", () -> AttachmentType.builder(
                            holder -> new play.xponer.astronima.physio.SurfaceContamination())
                    .serialize(play.xponer.astronima.physio.SurfaceContamination.CODEC)
                    .sync(ByteBufCodecs.fromCodec(
                            play.xponer.astronima.physio.SurfaceContamination.CODEC.codec()))
                    .build());

    /**
     * {@link play.xponer.astronima.vrpod.VrPodReturn}'s own codec, kept here rather than on the
     * record itself for the same reason {@link #UNLOCKS_CODEC} is: that record is deliberately
     * Minecraft-light so its own round-trip can be a plain unit test, and this file already pays
     * the {@code com.mojang.serialization} cost for every attachment declared in it.
     */
    private static final Codec<play.xponer.astronima.vrpod.VrPodReturn> VR_POD_RETURN_CODEC =
            com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("dimension")
                            .forGetter(play.xponer.astronima.vrpod.VrPodReturn::dimensionId),
                    Codec.DOUBLE.fieldOf("x").forGetter(play.xponer.astronima.vrpod.VrPodReturn::x),
                    Codec.DOUBLE.fieldOf("y").forGetter(play.xponer.astronima.vrpod.VrPodReturn::y),
                    Codec.DOUBLE.fieldOf("z").forGetter(play.xponer.astronima.vrpod.VrPodReturn::z),
                    Codec.FLOAT.fieldOf("yaw").forGetter(play.xponer.astronima.vrpod.VrPodReturn::yaw),
                    Codec.FLOAT.fieldOf("pitch").forGetter(play.xponer.astronima.vrpod.VrPodReturn::pitch),
                    Codec.BOOL.fieldOf("previous_flying")
                            .forGetter(play.xponer.astronima.vrpod.VrPodReturn::previousFlying),
                    Codec.BOOL.fieldOf("previous_may_fly")
                            .forGetter(play.xponer.astronima.vrpod.VrPodReturn::previousMayFly),
                    Codec.BOOL.fieldOf("previous_instabuild")
                            .forGetter(play.xponer.astronima.vrpod.VrPodReturn::previousInstabuild)
            ).apply(instance, play.xponer.astronima.vrpod.VrPodReturn::new));

    /**
     * Where to send a player back to when they leave the VR Simulation Pod, or empty if they are
     * not currently inside it (design/vr-simulation-pod.md §5). Server-only: nothing about the
     * pod's own state is drawn on the client, so this is never synced. Not copied on death —
     * dying for real, outside the pod, has nothing to do with a visit to it.
     */
    public static final DeferredHolder<AttachmentType<?>,
            AttachmentType<java.util.Optional<play.xponer.astronima.vrpod.VrPodReturn>>> VR_POD_RETURN =
            ATTACHMENTS.register("vr_pod_return", () -> AttachmentType
                    .builder((java.util.function.Supplier<java.util.Optional<play.xponer.astronima.vrpod.VrPodReturn>>)
                            java.util.Optional::empty)
                    .serialize(VR_POD_RETURN_CODEC.optionalFieldOf("return"))
                    .build());

    /**
     * The working designator of whatever infection is currently active, or empty when none is
     * (design/biomonitor-chips.md §4). Synced, unlike {@link #INFECTION} itself: a designator is
     * invented by the analyzer rather than looked up ("Isolate B-2"), and knowing it teaches a
     * player nothing about the real organism a research bench would eventually reveal — the thing
     * that must stay hidden is the identity behind it, not the fact that a label exists. Whether to
     * actually draw it is decided client-side by whether an analyzer chip is equipped, the same way
     * the client already knows its own Curios loadout for every other slot.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> VISIBLE_DESIGNATOR =
            ATTACHMENTS.register("visible_designator", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING.fieldOf("designator"))
                    .sync(ByteBufCodecs.STRING_UTF8)
                    .build());

    private ModAttachments() {}
}
