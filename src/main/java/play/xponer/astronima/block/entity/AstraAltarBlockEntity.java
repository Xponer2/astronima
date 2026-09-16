package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.astra.AstraFieldStorage;
import play.xponer.astronima.sim.astra.AstraFieldGeometry;
import play.xponer.astronima.sim.astra.AstraPrecipitation;
import play.xponer.astronima.sim.astra.AstraRitual;
import play.xponer.astronima.sim.sky.SkyEventOverride;
import play.xponer.astronima.sim.world.AsteroidBody;

import java.util.ArrayList;
import java.util.List;

/**
 * The ritual's focus — design/astra-ritual-grammar.md §1/§2, the Precipitation Rite made a real,
 * buildable structure. Detects a figure (2–4 straight cardinal arms, each ending in an
 * {@code asterium_block} anchor, inside a closed boundary ring), and on activation draws live on
 * the field at each anchor's own real position for real, persisted through {@link
 * AstraFieldStorage} exactly the way the collector already does — never an in-memory copy of the
 * ground that could drift from what a meter standing there would read afterward.
 *
 * <h2>Why this does not reuse {@code AstraRitual.step}/{@code State} directly</h2>
 * That pair is a self-contained simulation for a caller with no real storage to consult (the debug
 * command's "what would this figure do" question). A real altar has real storage: it reads each
 * tap point's live density fresh from {@link AstraFieldStorage} every step (which already reflects
 * real refill since the last touch) and persists its own draw straight back through {@link
 * AstraFieldStorage#drawAt} — so a player who breaks off with a meter mid-ritual reads the exact
 * ground the ritual actually left, not a number this class invented on the side. What the two share,
 * factored out precisely so they cannot quietly disagree (rule 46): {@link
 * AstraRitual#drawRatePerSecond}, {@link AstraRitual#stepSeconds} and {@link AstraRitual#drawShares}.
 */
public class AstraAltarBlockEntity extends ReadableBlockEntity {

    /** A figure needs at least this many arms to be a figure at all — design/
     *  astra-ritual-grammar.md §1's own "sentence", not a single word. */
    public static final int MIN_ARMS = 2;

    /** How far an arm may reach before giving up on finding an anchor in that direction. */
    public static final int MAX_ARM_LENGTH = 6;

    private static final Direction[] ARM_DIRECTIONS =
            {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /** Must match {@code AtmosphereEvents.FLARE_SEED} exactly — see that field's own doc. */
    private static final long FLARE_SEED = 20260810L;

    public enum Phase { IDLE, RUNNING, COMPLETED, STALLED }

    public enum ActivationResult { STARTED, ALREADY_RUNNING, NO_ARMS, NO_BOUNDARY }

    private Phase phase = Phase.IDLE;
    private List<BlockPos> tapPositions = List.of();
    private double elapsedSeconds;
    private double totalDrawn;
    private long lastStepGameTime;

    public AstraAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASTRA_ALTAR.get(), pos, state);
    }

    public Phase phase() {
        return phase;
    }

    public double elapsedSeconds() {
        return elapsedSeconds;
    }

    public double totalDrawn() {
        return totalDrawn;
    }

    public int armCount() {
        return tapPositions.size();
    }

    // ------------------------------------------------------------------ activation

    /**
     * Attempts to activate the figure built around this altar. Detects arms and the boundary
     * fresh (rule 25 — a screen, and by extension an interaction, may draw or trigger, but the
     * decision is computed here, not cached); on success, consumes every anchor immediately,
     * whether or not the ritual that follows ever completes (design/astra-ritual-grammar.md §2's
     * own "the anchors are spent regardless of outcome").
     */
    public ActivationResult activate(ServerLevel level) {
        if (phase == Phase.RUNNING) {
            return ActivationResult.ALREADY_RUNNING;
        }

        List<Arm> arms = detectArms(level);
        if (arms.size() < MIN_ARMS) {
            return ActivationResult.NO_ARMS;
        }
        int maxArmLength = arms.stream().mapToInt(Arm::length).max().orElse(0);
        if (!hasClosedBoundary(level, maxArmLength + 1)) {
            return ActivationResult.NO_BOUNDARY;
        }

        List<BlockPos> anchors = new ArrayList<>(arms.size());
        for (Arm arm : arms) {
            level.setBlock(arm.anchorPos(), Blocks.AIR.defaultBlockState(), 3);
            anchors.add(arm.anchorPos());
        }

        tapPositions = List.copyOf(anchors);
        elapsedSeconds = 0.0;
        totalDrawn = 0.0;
        lastStepGameTime = level.getGameTime();
        phase = Phase.RUNNING;
        setChanged();
        return ActivationResult.STARTED;
    }

    private record Arm(Direction direction, int length, BlockPos anchorPos) {}

    /** One straight cardinal line per direction: solid blocks out to an {@code asterium_block}
     *  anchor, or nothing if the line has a gap, has no anchor within {@link #MAX_ARM_LENGTH}, or
     *  runs straight into open air. */
    private List<Arm> detectArms(Level level) {
        List<Arm> found = new ArrayList<>(ARM_DIRECTIONS.length);
        for (Direction direction : ARM_DIRECTIONS) {
            for (int length = 1; length <= MAX_ARM_LENGTH; length++) {
                BlockPos checkPos = worldPosition.relative(direction, length);
                BlockState state = level.getBlockState(checkPos);
                if (state.is(ModBlocks.ASTERIUM_BLOCK.get())) {
                    found.add(new Arm(direction, length, checkPos));
                    break;
                }
                if (state.isAir()) {
                    break; // a gap - no anchor can complete this line
                }
                // any other solid block: the arm's own body, keep extending
            }
        }
        return found;
    }

    /** A closed square perimeter of solid blocks at {@code radius}, same Y as the altar — just
     *  past the figure's own longest reach, per design/astra-ritual-grammar.md §1's boundary. */
    private boolean hasClosedBoundary(Level level, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                    continue; // interior - the boundary is the perimeter only
                }
                BlockPos ringPos = worldPosition.offset(dx, 0, dz);
                if (level.getBlockState(ringPos).isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ the live draw

    /** Called every game tick; only actually advances the ritual once per real second, matching
     *  {@link AstraRitual#stepSeconds}. */
    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || phase != Phase.RUNNING) {
            return;
        }
        long elapsedTicks = serverLevel.getGameTime() - lastStepGameTime;
        if (elapsedTicks < 20L) {
            return;
        }
        lastStepGameTime = serverLevel.getGameTime();

        int n = tapPositions.size();
        double[] density = new double[n];
        double[] baseline = new double[n];
        for (int i = 0; i < n; i++) {
            BlockPos tap = tapPositions.get(i);
            baseline[i] = baselineAt(serverLevel, tap);
            density[i] = AstraFieldStorage.get(serverLevel)
                    .densityAt(tap.getX(), tap.getY(), tap.getZ(), baseline[i], serverLevel.getGameTime());
        }

        double drawThisStep = AstraRitual.drawRatePerSecond(n) * AstraRitual.stepSeconds();
        double[] shares = AstraRitual.drawShares(density, drawThisStep);
        if (shares == null) {
            phase = Phase.STALLED;
            setChanged();
            return;
        }

        for (int i = 0; i < n; i++) {
            BlockPos tap = tapPositions.get(i);
            AstraFieldStorage.get(serverLevel).drawAt(
                    tap.getX(), tap.getY(), tap.getZ(), baseline[i], shares[i], serverLevel.getGameTime());
        }

        elapsedSeconds += AstraRitual.stepSeconds();
        totalDrawn += drawThisStep;
        if (elapsedSeconds >= AstraRitual.requiredDurationSeconds()) {
            complete(serverLevel);
        } else {
            setChanged();
        }
    }

    private void complete(ServerLevel level) {
        phase = Phase.COMPLETED;
        int yielded = AstraPrecipitation.itemsYieldedFromRitual(totalDrawn);
        if (yielded > 0) {
            ItemStack grains = new ItemStack(ModItems.ASTERIUM_GRAINS.get(), yielded);
            level.addFreshEntity(new ItemEntity(level,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5,
                    grains));
        }
        setChanged();
    }

    private static double baselineAt(ServerLevel level, BlockPos pos) {
        double r = Math.hypot(pos.getX(), pos.getZ());
        double shellCoordinate = AsteroidBody.shellCoordinate(r, pos.getY());
        double flareIntensity = SkyEventOverride.resolveFlareIntensity(level.getGameTime(), FLARE_SEED);
        return AstraFieldGeometry.baselineDensity(shellCoordinate, flareIntensity);
    }

    // ------------------------------------------------------------------ presentation

    /** A short status line for chat — the altar's own honest first door (rule 13), before any
     *  particle/sound presentation exists for this leaf. */
    public Component statusMessage() {
        return switch (phase) {
            case IDLE -> Component.translatable("astronima.astra_altar.no_arms");
            case RUNNING -> Component.translatable("astronima.astra_altar.running",
                    (int) elapsedSeconds + "/" + (int) AstraRitual.requiredDurationSeconds() + "s");
            case COMPLETED -> Component.translatable("astronima.astra_altar.completed",
                    AstraPrecipitation.itemsYieldedFromRitual(totalDrawn));
            case STALLED -> Component.translatable("astronima.astra_altar.stalled");
        };
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("phase", phase.name());
        output.putDouble("elapsed_seconds", elapsedSeconds);
        output.putDouble("total_drawn", totalDrawn);
        output.putLong("last_step_game_time", lastStepGameTime);
        output.putInt("tap_count", tapPositions.size());
        for (int i = 0; i < tapPositions.size(); i++) {
            BlockPos tap = tapPositions.get(i);
            output.putInt("tap_" + i + "_x", tap.getX());
            output.putInt("tap_" + i + "_y", tap.getY());
            output.putInt("tap_" + i + "_z", tap.getZ());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        phase = phaseOr(input.getStringOr("phase", Phase.IDLE.name()), Phase.IDLE);
        elapsedSeconds = input.getDoubleOr("elapsed_seconds", 0.0);
        totalDrawn = input.getDoubleOr("total_drawn", 0.0);
        lastStepGameTime = input.getLongOr("last_step_game_time", 0L);
        int count = input.getIntOr("tap_count", 0);
        List<BlockPos> loaded = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            loaded.add(new BlockPos(
                    input.getIntOr("tap_" + i + "_x", 0),
                    input.getIntOr("tap_" + i + "_y", 0),
                    input.getIntOr("tap_" + i + "_z", 0)));
        }
        tapPositions = List.copyOf(loaded);
    }

    private static Phase phaseOr(String name, Phase fallback) {
        for (Phase phase : Phase.values()) {
            if (phase.name().equals(name)) {
                return phase;
            }
        }
        return fallback;
    }
}
