package play.xponer.astronima.telescope;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import play.xponer.astronima.block.TelescopeBlock;
import play.xponer.astronima.block.entity.TelescopeBlockEntity;
import play.xponer.astronima.sim.optics.SkyRotation;
import play.xponer.astronima.sim.optics.TelescopeMount;

import java.util.UUID;

/**
 * design/astra-telescope.md §2.1 — the entity a player looks through while observing a telescope.
 *
 * <p><strong>Not ridden.</strong> Three separate rounds tried to make riding this entity (real
 * vehicle-passenger mechanics, {@code Entity#startRiding}) carry the camera, and each round found
 * a new way that vanilla's own passenger machinery quietly did not do what it looked like it did
 * (PLAN.md rules 77-79). Direct correction: "тебе надо позицию камеры просто менять а не как на
 * лошади ездить." Nobody rides this any more — the observer's own body stays exactly where it was
 * standing when it clicked the telescope. {@code TelescopeCamera} (client) points
 * {@code Minecraft}'s camera at this entity directly ({@code Minecraft#setCameraEntity}) instead,
 * the same supported mechanism spectator mode uses to look through a different entity while the
 * real player entity keeps existing untouched.
 *
 * <p>Rotation is driven by {@link #setClampedAim} on both sides, independently: the client
 * ({@code client.TelescopeCamera}) owns its own local, unclamped aim accumulator and pushes the
 * absolute result here every frame; the server applies whatever a client last reported
 * ({@code TelescopeAimUpdatePayload}) the identical way, never trusting it outright — either path
 * re-derives {@link TelescopeMount#clamp} itself, the same trust boundary already kept for
 * occlusion and the target lookup.
 */
public class TelescopeMountEntity extends Entity {

    /** {@code GROUND_TRIPOD} is the only mount profile that exists today
     * (design/astra-telescope.md §9, open question 2 — a craftable quality tier is a real, later
     * question). {@code baseUp} is world up, matching every mount placed so far. */
    private static final SkyRotation.Vec3 BASE_UP = new SkyRotation.Vec3(0.0, 1.0, 0.0);

    /** How much narrower the eyepiece view is than the player's own configured FOV — the single
     * source both {@code TelescopeHud}'s real FOV narrowing and this class's own sensitivity
     * compensation read, so the two can never quietly drift apart (rule 46). Not yet a tuned
     * playtest value (design/astra-research.md §14's "state a tuning number as the thing a player
     * experiences" applies here too). */
    public static final float FOV_DIVISOR = 6.0F;

    /** The pivot's own height — both the yoke's azimuth axis and, in real-world terms, near enough
     * to the eyepiece cell's own centre (§6 of the design document) that using one shared height
     * for "where the pivot is" and "how high the eye starts from" is honest rather than two
     * numbers pretending to agree by coincidence. */
    private static final double SEAT_HEIGHT = 0.8;

    /** Synced (not a plain field): the client needs its own honest answer to "where is my
     * telescope," for both the eye position above and the block-entity renderer's own occupancy
     * lookup ({@link #findAt}) — found the hard way (PLAN.md rule 77) when a plain field was only
     * ever its default on every client, which read as "camera underground." */
    private static final EntityDataAccessor<BlockPos> DATA_TELESCOPE_POS =
            SynchedEntityData.defineId(TelescopeMountEntity.class, EntityDataSerializers.BLOCK_POS);

    /** Server-only, never synced: who is allowed to move this mount and end its own session. The
     * client only ever needs to know a mount exists at all, never whose it is. */
    private @Nullable UUID observerId;

    /** Smooths *incoming* network rotation/position syncs for bystanders — direct report: "в
     * мультиплеёре сама моделька телескопа двигается типо не гладко а как-будто бы фпс ей мало."
     * {@code Entity#getInterpolation} returns {@code null} by default, meaning every other client's
     * view of this mount was snapping straight to whatever rotation last arrived over the network
     * (every tick this project's own {@code TelescopeAimUpdatePayload} sends one while a session is
     * active) with no smoothing at all — exactly what "not enough FPS" looks like from the outside.
     * Only ever matters for {@code moveOrInterpolateTo}, the client-side entry point incoming sync
     * packets call; the observer's own session never goes through it at all ({@code setClampedAim}
     * writes {@code setYRot}/{@code setPos} directly), and {@code client.TelescopeCamera}'s own
     * per-frame push in {@code ComputeCameraAngles} always runs after this tick-phase smoothing
     * would, so it can only ever overwrite a transient blended value the observer's own camera was
     * never going to read anyway — the fix is real for bystanders and free for the observer. */
    private final InterpolationHandler interpolation = new InterpolationHandler(this);

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    public TelescopeMountEntity(EntityType<? extends TelescopeMountEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.blocksBuilding = false;
    }

    /** Spawns a mount at the telescope, starting from either the block's own remembered aim
     * ({@code blockEntity}, if it has one) or the mount's neutral rest angle — never a bare (0, 0)
     * that ignores {@code GROUND_TRIPOD}'s own limits. */
    public static TelescopeMountEntity spawnAt(Level level, BlockPos telescopePos,
                                               TelescopeBlockEntity blockEntity,
                                               EntityType<TelescopeMountEntity> type) {
        TelescopeMountEntity mount = new TelescopeMountEntity(type, level);
        mount.entityData.set(DATA_TELESCOPE_POS, telescopePos.immutable());

        float[] restRotation = blockEntity != null && blockEntity.hasSavedAim()
                ? new float[] {blockEntity.savedYaw(), blockEntity.savedPitch()}
                : restRotation();
        mount.setYRot(restRotation[0]);
        mount.setXRot(restRotation[1]);
        // snapEyeToCurrentAim() reads the rotation just set above — the camera has to start clear
        // of the eyepiece's own diagonal housing, not inside it (design/astra-telescope.md §2.3.2).
        // Also handles the "old rotation defaults to 0/0" snap a fresh entity would otherwise show
        // for one frame the instant a telescope first comes into view.
        mount.snapEyeToCurrentAim();
        return mount;
    }

    /** The neutral rest angle for a mount with no saved aim yet — {@code GROUND_TRIPOD}'s own
     * dead-zone boundary just short of the pole, never a bare (0, 0) that ignores its limits.
     * Shared with {@code TelescopeBlockEntityRenderer} (an idle, never-yet-aimed telescope has to
     * draw its tube at this exact angle, not a second guess at the same rest pose — rule 46). */
    public static float[] restRotation() {
        SkyRotation.Vec3 rest = TelescopeMount.clamp(BASE_UP, BASE_UP, TelescopeMount.GROUND_TRIPOD);
        return TelescopeMount.rotationFromDirection(rest);
    }

    public BlockPos telescopePos() {
        return entityData.get(DATA_TELESCOPE_POS);
    }

    /** The yoke's own azimuth axis — a fixed point. */
    private static Vec3 pivotPositionFor(BlockPos telescopePos) {
        return Vec3.atBottomCenterOf(telescopePos).add(0.0, SEAT_HEIGHT, 0.0);
    }

    private Vec3 pivotPosition() {
        return pivotPositionFor(telescopePos());
    }

    /** Where the eye sits for a telescope at {@code telescopePos} aimed at {@code (yaw, pitch)} —
     * the pivot, offset by {@link TelescopeMount#eyeOffsetFromPivot}. Static and independent of any
     * live mount instance so {@code TelescopeBlock} can check it *before* spawning one, refusing to
     * start a session whose restored aim would put the eye inside solid block geometry
     * (design/astra-telescope.md §2.3.3 step 6 — closes "разрешает зайти в телескоп даже если блоки
     * вокруг и над ним"). */
    public static Vec3 eyePositionFor(BlockPos telescopePos, float yaw, float pitch) {
        double[] offset = TelescopeMount.eyeOffsetFromPivot(yaw, pitch,
                TelescopeMount.EYE_LOCAL_Y, TelescopeMount.EYE_LOCAL_Z);
        return pivotPositionFor(telescopePos).add(offset[0] / 16.0, offset[1] / 16.0, offset[2] / 16.0);
    }

    /** Where the camera's own eye sits right now — recomputed every time the mount's rotation
     * changes, not just once at spawn (§2.3.2's own "the eye travels with both azimuth and
     * elevation" requirement). */
    private Vec3 eyePosition() {
        return eyePositionFor(telescopePos(), this.getYRot(), this.getXRot());
    }

    /** Recomputes the eye position from the mount's *current* rotation and re-commits it as both
     * the "old" and current position/rotation, collapsing {@code Camera#alignWithEntity}'s own
     * per-frame interpolation to an exact value (design/astra-telescope.md §2.3.1's "the position
     * lever, stated exactly"). Idempotent, cheap, and safe to call unconditionally every frame —
     * {@code client.TelescopeCamera} does exactly that in {@code ViewportEvent.ComputeCameraAngles},
     * the one hook proven to run immediately before {@code Camera}'s own {@code setPosition} call
     * consumes these fields. */
    public void snapEyeToCurrentAim() {
        this.setPos(eyePosition());
        this.setOldPosAndRot();
    }

    /** How far around a telescope to search for its own mount — one shared answer for both
     * {@code TelescopeBlock} (does this telescope already have an active session) and the
     * block-entity renderer (is anyone currently aiming it, for the tube's own live orientation)
     * rather than two copies of the same search (rule 46). */
    private static final double SEARCH_RADIUS = 1.0;

    /** The mount currently linked to {@code telescopePos}, if any — {@code null} for an
     * unoccupied telescope, not an exception; callers decide what "nobody is aiming this" means
     * for their own purpose. */
    public static @Nullable TelescopeMountEntity findAt(Level level, BlockPos telescopePos) {
        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(telescopePos).inflate(SEARCH_RADIUS);
        java.util.List<TelescopeMountEntity> found = level.getEntitiesOfClass(TelescopeMountEntity.class, searchBox,
                mount -> mount.telescopePos().equals(telescopePos));
        return found.isEmpty() ? null : found.get(0);
    }

    /** Server-only: is {@code player} the one currently allowed to move this mount and end its own
     * session — never true for anyone else, including a completely different player who merely
     * happens to be looking at the same telescope block. */
    public boolean isObservedBy(Player player) {
        return observerId != null && observerId.equals(player.getUUID());
    }

    /** Called once, server-side, the moment a session actually starts. */
    public void beginObserving(Player player) {
        this.observerId = player.getUUID();
    }

    /** Sets this mount's aim to a claimed absolute direction, clamped — what the server calls with
     * whatever (yaw, pitch) a client last reported ({@code TelescopeAimUpdatePayload}), never
     * trusting it outright (the same "re-derive the clamp, do not trust a claimed direction" rule
     * this design already keeps for occlusion and the target lookup). Also what
     * {@code client.TelescopeCamera} calls on its own local copy of this mount every frame — v4
     * dropped the earlier {@code advanceAim} in favour of the client owning its own unclamped aim
     * accumulator entirely and only ever pushing an already-computed absolute value here
     * (design/astra-telescope.md §2.3.0's bug 3): reading this entity's own {@code getYRot()} as an
     * accumulation base was exactly what let a stale, server-synced value contaminate the very next
     * frame's delta. */
    public void setClampedAim(float claimedYaw, float claimedPitch) {
        float[] rotation = TelescopeMount.clampRotation(claimedYaw, claimedPitch, BASE_UP,
                TelescopeMount.GROUND_TRIPOD);
        this.setYRot(rotation[0]);
        this.setXRot(rotation[1]);
        snapEyeToCurrentAim();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_TELESCOPE_POS, BlockPos.ZERO);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.read("TelescopePos", BlockPos.CODEC)
                .ifPresent(pos -> entityData.set(DATA_TELESCOPE_POS, pos));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("TelescopePos", BlockPos.CODEC, telescopePos());
    }

    /** Never pushed by other entities, and nothing pushes past it either — a mount is furniture,
     * not an obstacle. */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** Furniture, not a combat target — nothing about a mount is meant to be destroyed by hitting
     * it; breaking the telescope block itself is the only way to remove one (§8's T1 test plan). */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    /** Ends this session, server-side: saves the aim it ends on (what a future observer finds
     * waiting for them) and discards. Called from the block's own second-click handling, never
     * from {@link #tick} (a telescope broken out from under a session has no block entity left to
     * save anything into). */
    public void stopObserving() {
        if (!level().isClientSide()
                && level().getBlockEntity(telescopePos()) instanceof TelescopeBlockEntity blockEntity) {
            blockEntity.saveAim(getYRot(), getXRot());
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        interpolation.interpolate();
        if (!level().isClientSide() && !isRemoved()) {
            if (!(level().getBlockState(telescopePos()).getBlock() instanceof TelescopeBlock)) {
                // The telescope was broken out from under an active observation — no aim to save,
                // the block (and its block entity) is already gone (design/astra-telescope.md §8's
                // T1 test plan).
                discard();
            } else if (observerId != null && level() instanceof ServerLevel serverLevel
                    && serverLevel.getPlayerByUUID(observerId) == null) {
                // The observing player disconnected without ever clicking to leave — nothing rides
                // this any more to eject automatically the way vanilla's own passenger removal
                // used to, so this is the session's only cleanup path left.
                discard();
            }
        }
    }

    @Override
    public boolean shouldBeSaved() {
        // The entity itself is still ephemeral (§9 open question 4) — only the aim it last held
        // survives, via TelescopeBlockEntity, not a save/load round trip of this entity.
        return false;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }
}
