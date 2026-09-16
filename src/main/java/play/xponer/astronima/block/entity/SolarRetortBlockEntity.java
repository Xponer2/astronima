package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.atmosphere.SkyExposure;
import play.xponer.astronima.menu.ProcessingMenu;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.registry.ModParticles;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.optics.BlackBody;
import play.xponer.astronima.sim.ore.Calcination;
import play.xponer.astronima.sim.ore.ChlorateDecomposition;
import play.xponer.astronima.sim.ore.Dehydroxylation;
import play.xponer.astronima.sim.ore.RetortGlow;
import play.xponer.astronima.sim.ore.RetortProcess;
import play.xponer.astronima.sim.ore.SolarConcentrator;

/**
 * Bakes the water out of hydrated rock with concentrated sunlight.
 *
 * <p>The asteroid is more than half phyllosilicate and that mineral carries about an eighth
 * of its mass as water, bonded into the lattice. Until now every gram of it went to
 * tailings: the game had the source ({@code OreBody} is 52 % serpentine), it had the sink
 * ({@code DehumidifierBlockEntity} condenses vapour into bottles), and nothing in between.
 * This is the between.
 *
 * <p><strong>Why solar.</strong> {@code design/machines.md} §5 fixes that tier 1 has no
 * power, on purpose. Combustion is already forbidden without oxygen, and spending oxygen to
 * make water would be an absurd trade. A concentrating mirror needs none of it — which is
 * also why every serious proposal for asteroid water extraction is some arrangement of
 * exactly this.
 *
 * <p><strong>The placement this asks for</strong> is the one the physics asks for: the
 * mirror wants sky, the steam wants somewhere to go, so the retort belongs in the roof of a
 * sealed room. Neither half is a rule imposed on the player — with no sky it is not a
 * heater, and with nowhere to vent it stalls against its own back-pressure.
 */
public class SolarRetortBlockEntity extends ProcessingBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    /** Work for one charge. Long enough that the focus is set, not fiddled with. */
    public static final int BATCH_WORK = 220;

    /**
     * Mass of one charge, in grams. A stack of tailings is a bucket of rock, and a bucket
     * of CM chondrite really does hold a few hundred grams of water.
     */
    public static final double CHARGE_GRAMS = 4000.0;

    /** Hydrated fraction of a tailings charge: the magnet took the metal, this is the rest. */
    public static final double TAILINGS_HYDRATION = 0.62;

    /** Raw rock still carries its metal and its organics, so less of it is the wet mineral. */
    public static final double ROCK_HYDRATION = 0.48;

    /**
     * Breunnerite as a fraction of a baked-silicate charge's mass.
     *
     * <p>Chondrite's own real whole-body fraction is 4 % ({@code OreBody.chondrite}); baked
     * silicate is tailings with its serpentine water already driven off
     * ({@code TAILINGS_HYDRATION * Dehydroxylation.WATER_FRACTION_OF_SERPENTINE} ≈ 8 % of the
     * original charge mass, gone as steam), which concentrates everything left by the same
     * "removing one component raises everyone else's share" logic {@code TAILINGS_HYDRATION}
     * itself already carries relative to raw rock. Rounded to something a player can reason
     * about, the same way every {@code OreBody} fraction already is.
     */
    public static final double BAKED_SILICATE_CARBONATE = 0.045;

    /**
     * Grams of sodium chlorate in one charge.
     *
     * <p>The same {@link #CHARGE_GRAMS} as a charge of rock, and for the plainest of
     * reasons: it is the same vessel. Sizing the chlorate charge separately would have been
     * a balance knob wearing a physical costume — and the first draft did exactly that at
     * 1800 g, which made a crystal worth 1.5 % more through the retort than as a candle.
     * A route nobody would ever walk.
     */
    public static final double CHLORATE_CHARGE_GRAMS = CHARGE_GRAMS;

    public SolarRetortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_RETORT.get(), pos, state, 2);
    }

    @Override
    public ProcessingMenu.Kind kind() {
        return ProcessingMenu.Kind.RETORT;
    }

    // ------------------------------------------------------------------ the mirror

    /**
     * How much sun is on the mirror, 0 to 1.
     *
     * <p>Sky access and daylight, asked of the level rather than assumed, because both are
     * things the player changes: roofing the retort over is a mistake they can make and
     * night is a condition they have to plan around.
     */
    public double sunlight() {
        return level instanceof ServerLevel serverLevel
                ? SkyExposure.sunlightAt(serverLevel, worldPosition) : 0;
    }

    /** What the vessel is actually at, for the panel's bar and for the reaction. */
    public double temperatureK() {
        return SolarConcentrator.temperatureK(sunlight(), focus());
    }

    /**
     * The mirror's own aim, 0 to 1 — computed fresh every call, never set by a player.
     *
     * <p>{@link SolarConcentrator#focusFor} is the exact inverse of
     * {@link SolarConcentrator#temperatureK}: given the sun actually on the dish right now
     * and a target kelvin, it returns the concentration that gets there. The target is
     * {@link RetortProcess#targetK()} — a safe point inside the loaded process's own
     * complete-to-spoil window — so the retort chases its own right answer the way the sun
     * moves it, rather than a player nursing a slider across a window they cannot see from
     * outside the machine.
     */
    public double focus() {
        return SolarConcentrator.focusFor(sunlight(), process().targetK());
    }

    // ------------------------------------------------------------------ operation

    @Override
    public int workRequired() {
        return BATCH_WORK;
    }

    @Override
    public boolean hasFeed() {
        return processOf(getItem(SLOT_INPUT)) != null;
    }

    @Override
    public boolean hasRoomForProduct() {
        return !hasFeed() || hasRoom(SLOT_OUTPUT, residueFor(process()));
    }

    /**
     * The stalls a retort has that no other machine does.
     *
     * <p>Reported in the order the player can act on them: a machine with nothing in it is
     * a feed problem whatever else is true, but once it is loaded the reason it is not
     * running is about where it was put, and that is what needs saying.
     */
    @Override
    public WorkState workState() {
        WorkState general = super.workState();
        if (!general.isWorking()) {
            return general;
        }
        if (sunlight() <= 0) {
            return WorkState.UNLIT;
        }
        if (receivingRoom() == null) {
            return WorkState.BACKPRESSURE;
        }
        if (temperatureK() < process().onsetK()) {
            return WorkState.TOO_COLD;
        }
        return general;
    }

    @Override
    public boolean canRun() {
        return workState().isWorking();
    }

    /**
     * The room the steam goes into, or null when there is nowhere worth sending it.
     *
     * <p>Two conditions, and the second one is the whole placement puzzle:
     *
     * <ul>
     *   <li>The room <em>touching</em> the retort, not the one it sits in — the vessel is
     *       set into a wall or a roof with its mirror outside, so the block is part of the
     *       shell and the air it opens onto is next door.
     *   <li>That room must be <strong>sealed</strong>. A retort standing free on the
     *       surface also touches a "room": the exterior. Venting into it loses every gram,
     *       and the first version of this check accepted it happily — the machine ran, the
     *       bar filled, and the water went to space with nothing said. Requiring a seal is
     *       what turns that from a silent loss into a stall the panel can name.
     * </ul>
     */
    private RoomState receivingRoom() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Atmosphere.RoomReading reading = Atmosphere.get(serverLevel).readingNear(worldPosition);
        if (reading == null || !reading.sealed() || reading.openToSpace()) {
            return null;
        }
        return reading.state();
    }

    @Override
    protected void finishBatch() {
        ItemStack charge = getItem(SLOT_INPUT);
        RetortProcess process = processOf(charge);
        if (process == null) {
            return;
        }
        RoomState room = receivingRoom();
        if (room == null) {
            return; // guarded by workState, but never bake a charge into nowhere
        }

        if (process == RetortProcess.CHLORATE) {
            finishChlorate(room);
        } else if (process == RetortProcess.CALCINATION) {
            finishCalcination(room);
        } else {
            finishWater(room, hydrationOf(charge));
        }
        pushOutput(SLOT_OUTPUT, residueFor(process));
        charge.shrink(1);
        spawnCompletionFlare();
    }

    private void finishWater(RoomState room, double hydration) {
        Dehydroxylation.Bake bake =
                Dehydroxylation.bake(CHARGE_GRAMS, hydration, temperatureK());

        // Steam at the vessel's temperature, into the air next door. From here it is the
        // habitat's problem and its instruments: humidity climbs, the analyzer shows it,
        // and a dehumidifier turns it into bottles. Nothing new is needed downstream.
        room.addGasAt(Gas.WATER_VAPOR, bake.waterMoles(), room.temperatureK());
        lastYieldMoles = bake.waterMoles();
        lastSpoiled = bake.sintered();
        lastChlorineMoles = 0;
    }

    /**
     * MgO and CO2 out of a second, hotter bake of the same rock dehydroxylation already
     * touched once. Real fines-scattering (decrepitation) at the very top of the retort's own
     * reach, not an invented hazard — {@code Calcination}'s own class doc names why this
     * process alone sits at the mirror's hard ceiling rather than comfortably under it.
     */
    private void finishCalcination(RoomState room) {
        double carbonateGrams = CHARGE_GRAMS * BAKED_SILICATE_CARBONATE;
        Calcination.Bake bake = Calcination.bake(carbonateGrams, temperatureK());

        room.addGasAt(Gas.CARBON_DIOXIDE, bake.co2Moles(), room.temperatureK());
        lastYieldMoles = bake.co2Moles();
        lastSpoiled = bake.decrepitated();
        lastChlorineMoles = 0;
    }

    /**
     * Oxygen out of chlorate — and chlorine, when the dial was left where the rock wanted it.
     *
     * <p><strong>Both gases go into the same room the player breathes</strong>, which is the
     * entire point rather than an oversight. The steam route can afford to vent into a
     * habitat because wet air is a nuisance; this one cannot, and it is the first machine in
     * the mod whose output is worth standing next to and whose failure is worth running from.
     * The analyzer already reads room composition and the toxicity model already knows what
     * chlorine does, so the hazard arrives with its instruments built (rule 7).
     */
    private void finishChlorate(RoomState room) {
        ChlorateDecomposition.Bake bake =
                ChlorateDecomposition.bake(CHLORATE_CHARGE_GRAMS, temperatureK());

        room.addGasAt(Gas.OXYGEN, bake.oxygenMoles(), room.temperatureK());
        if (bake.gassed()) {
            room.addGasAt(Gas.CHLORINE, bake.chlorineMoles(), room.temperatureK());
        }
        lastYieldMoles = bake.oxygenMoles();
        lastChlorineMoles = bake.chlorineMoles();
        // Chlorate does not fuse; its way of being ruined is the gas, not the residue.
        lastSpoiled = false;
    }

    /** What the last completed charge gave up, for the panel. */
    private double lastYieldMoles;
    private boolean lastSpoiled;
    private double lastChlorineMoles;

    /** Chlorine the last charge put into the room, 0 when the window was held. */
    public double lastChlorineMoles() {
        return lastChlorineMoles;
    }

    public double lastYieldMoles() {
        return lastYieldMoles;
    }

    /** True when the last charge went wrong at the top of its window — fused for
     *  dehydroxylation, decrepitated for calcination, the readout's warning either way. */
    public boolean lastSpoiled() {
        return lastSpoiled;
    }

    /**
     * What this stack would be cooked <em>for</em>, or null if the retort cannot cook it.
     *
     * <p>The vessel does not care what is in it — a solar furnace is a mirror and a hot box —
     * so which reaction is running is a property of the charge, not of the machine. Every
     * consumer of the window asks this rather than assuming water.
     */
    public static RetortProcess processOf(ItemStack stack) {
        if (stack.is(ModItems.CHLORATE_POWDER.get())) {
            return RetortProcess.CHLORATE;
        }
        if (stack.is(ModItems.BAKED_SILICATE.get())) {
            return RetortProcess.CALCINATION;
        }
        return hydrationOf(stack) > 0 ? RetortProcess.DEHYDROXYLATION : null;
    }

    /** The process for whatever is loaded now, falling back to water so the panel still reads. */
    public RetortProcess process() {
        RetortProcess loaded = processOf(getItem(SLOT_INPUT));
        return loaded == null ? RetortProcess.DEHYDROXYLATION : loaded;
    }

    /** What comes out of the vessel when this charge is done. */
    private static ItemStack residueFor(RetortProcess process) {
        return new ItemStack(switch (process) {
            // NaClO3 -> NaCl + 1.5 O2. The halogen stays behind as common salt, which is
            // already an item here, so the oxygen route feeds the same salt supply the
            // chemistry tier wants rather than inventing a residue nobody can use.
            case CHLORATE -> ModItems.MINERAL_SALTS.get();
            // The real flux, and calcination's whole point (design/carbonate-calcination.md).
            case CALCINATION -> ModItems.MAGNESIUM_OXIDE.get();
            case DEHYDROXYLATION -> ModItems.BAKED_SILICATE.get();
        });
    }

    /** How wet a charge of this is; zero for anything the retort cannot bake. */
    public static double hydrationOf(ItemStack stack) {
        if (stack.is(ModItems.TAILINGS.get())) {
            return TAILINGS_HYDRATION;
        }
        if (stack.is(ModItems.ASTEROID_ROCK.get())) {
            return ROCK_HYDRATION;
        }
        return 0;
    }

    // ------------------------------------------------------------------ presentation

    /**
     * What a renderer draws as the vessel's own glow — real {@link BlackBody} colour and
     * brightness, at the temperature the moment it was last computed.
     *
     * <p>Published rather than left for a client-side BER to compute, because it cannot: {@link
     * #sunlight()} only resolves on a {@code ServerLevel}, so {@link #temperatureK()} called from
     * the client silently reads as zero. This is {@link ReadableBlockEntity}'s own doctrine
     * ("the reading travels, not the fields") applied to the glow mesh instead of the gauge —
     * a second, parallel published value rather than a field on the shared {@code Reading} record,
     * since no other machine has anything like it.
     */
    public record Glow(int rgb, float intensity01) {}

    private @Nullable Glow publishedGlow;

    /** The vessel's published glow, or null while it is not visibly incandescent. */
    public @Nullable Glow glow() {
        return publishedGlow;
    }

    /**
     * Recomputes the glow alongside the gauge, on the same "only the server decides" guard
     * {@link ReadableBlockEntity#refreshReading()} already enforces.
     */
    @Override
    protected boolean refreshReading() {
        boolean changed = super.refreshReading();
        if (!(level instanceof ServerLevel)) {
            return changed; // the client keeps what it was told; it does not get a vote either
        }
        double temperatureK = temperatureK();
        Glow now = BlackBody.isVisiblyGlowing(temperatureK)
                ? new Glow(BlackBody.rgb(temperatureK), (float) BlackBody.intensity01(temperatureK))
                : null;
        if (java.util.Objects.equals(now, publishedGlow)) {
            return changed;
        }
        publishedGlow = now;
        return true;
    }

    /**
     * The vessel's own black-body glow, real and not decoration (see {@link RetortGlow}'s
     * javadoc on why this machine, unlike a wire, honestly crosses the Draper point).
     *
     * <p>Ticks whether or not a batch is running — a vessel does not go instantly cold the
     * moment it stops working — so this reads {@link #temperatureK()} directly rather than
     * gating on {@link #canRun()}. {@code setChanged()} runs here too, every tick, so the
     * published {@link #glow()} tracks sunlight sliding across the sky even on an idle vessel
     * that has no other reason to mark itself dirty this tick.
     *
     * <p>The ambient spark stream used to be spawned from here too ({@code serverLevel.sendParticles}
     * of {@code IncandescenceOptions} every tick). It is gone: design/vfx-craft.md §1.1/S2 moved
     * that read to a purely client-side Photon emitter ({@code RetortPhotonSparks}) driven straight
     * from the {@link #glow()} this method already publishes — the same reading, zero particle
     * packets. {@link #spawnCompletionFlare()}'s one-shot accent burst is unrelated and unchanged.
     */
    @Override
    protected void onServerTick() {
        setChanged();
    }

    /**
     * One bright flare exactly when a charge completes — design/vfx-craft.md §2's accent role,
     * tied to a real event a player already cares about (their charge is done) rather than a
     * timer. Only when the batch actually left the vessel glowing; a charge finished stone cold
     * has nothing to flare from.
     */
    private void spawnCompletionFlare() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double temperatureK = temperatureK();
        if (!BlackBody.isVisiblyGlowing(temperatureK)) {
            return;
        }
        serverLevel.sendParticles(
                new ModParticles.IncandescenceOptions(
                        BlackBody.rgb(temperatureK), (float) BlackBody.intensity01(temperatureK), true),
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5,
                (int) RetortGlow.ACCENT_BURST_PARTICLES, 0.3, 0.2, 0.3, 0.0);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("lastYield", lastYieldMoles);
        // Key name kept as "lastSintered" for save compatibility - the field it feeds was
        // renamed to lastSpoiled to cover calcination's decrepitation honestly, but the NBT
        // key itself is not worth a migration for.
        output.putBoolean("lastSintered", lastSpoiled);
        output.putDouble("lastChlorine", lastChlorineMoles);
        // Redundant with temperatureK() and worth it for the same reason the gauge is: a
        // freshly loaded chunk gets a correct-looking glow on the first frame rather than one
        // tick of darkness before onServerTick() gets around to it.
        output.putBoolean("hasGlow", publishedGlow != null);
        if (publishedGlow != null) {
            output.putInt("glowRgb", publishedGlow.rgb());
            output.putFloat("glowIntensity", publishedGlow.intensity01());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lastYieldMoles = input.getDoubleOr("lastYield", 0.0);
        lastSpoiled = input.getBooleanOr("lastSintered", false);
        lastChlorineMoles = input.getDoubleOr("lastChlorine", 0.0);
        publishedGlow = input.getBooleanOr("hasGlow", false)
                ? new Glow(input.getIntOr("glowRgb", 0), input.getFloatOr("glowIntensity", 0f))
                : null;
    }
}
