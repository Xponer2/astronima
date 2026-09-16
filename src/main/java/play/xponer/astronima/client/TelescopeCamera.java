package play.xponer.astronima.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.block.TelescopeBlock;
import play.xponer.astronima.network.TelescopeAimUpdatePayload;
import play.xponer.astronima.network.TelescopeStopObservingPayload;
import play.xponer.astronima.telescope.TelescopeMountEntity;

/**
 * Actually detaches the local view from the player and attaches it to the telescope —
 * design/astra-telescope.md §2.3 (v4), PLAN.md rule 79. Direct correction after riding proved
 * unreliable for camera control: "тебе надо позицию камеры просто менять а не как на лошади
 * ездить." Nothing here ever makes the player a passenger of anything; this class only ever points
 * {@code Minecraft}'s camera at a {@link TelescopeMountEntity} directly
 * ({@link Minecraft#setCameraEntity}) and feeds that mount its own aim every frame.
 *
 * <p><strong>v3 → v4, three bugs traced to specific lines (§2.3.0), fixed by three specific
 * changes:</strong>
 * <ul>
 *   <li><em>Judder.</em> v3 harvested input in {@code ClientTickEvent.Post} (20 Hz) while
 *   {@code MouseHandler} actually turns the player once per *frame*, and called
 *   {@code setOldPosAndRot()} on every change, destroying {@code Camera}'s own interpolation. v4
 *   harvests and places in the single {@link #onComputeCameraAngles} handler — not the
 *   {@code RenderFrameEvent.Pre} this design originally called for, which turned out (found only
 *   by reading {@code Minecraft.renderFrame} directly, rule 2) to fire *after*
 *   {@code GameRenderer.update()} already calls {@code Camera.update()}/{@code alignWithEntity()}
 *   for the frame — meaning input read there always missed the camera that had just been aligned,
 *   read one frame stale on the very next. {@code ComputeCameraAngles} is the hook actually proven
 *   to run immediately after {@code MouseHandler} turns the player and immediately before the
 *   camera consumes the result, so there is no second, earlier place left to get out of order
 *   with.</li>
 *   <li><em>Server fighting client.</em> v3's {@code advanceAim} read the entity's own
 *   {@code getYRot()} as its accumulation base, which a routine server resync could quietly make
 *   stale. v4 keeps the authoritative *client-side* aim ({@link #clientYaw}/{@link #clientPitch})
 *   entirely in this class, never reading it back from the entity except once, at session start —
 *   every frame pushes this class's own value onto the mount and nothing reads it back except to
 *   resync after the mount's own clamp (which can only ever pull the value *toward* what this
 *   class already asked for, not away from it).</li>
 *   <li><em>Seeing your own body.</em> A deliberate NeoForge patch renders the local player the
 *   instant the camera looks through something else. {@link #onHideLocalPlayer} cancels it,
 *   client-side only, exactly the "hide locally, keep it real for everyone else" request.</li>
 * </ul>
 *
 * <p>No aim state of its own beyond "which telescope, if any" ({@code TelescopeHud}'s own standing
 * rule): {@link #currentMount()} is read fresh every frame.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class TelescopeCamera {

    /** How much narrower the eyepiece view is than the raw mouse input — the client-side twin of
     * the constant this used to be on {@code TelescopeMountEntity} before v4 moved aim-harvesting
     * off the entity entirely (rule 46: {@code TelescopeHud}'s real FOV narrowing and this scale
     * still have to agree, so both read {@code TelescopeMountEntity.FOV_DIVISOR}). */
    private static final float SENSITIVITY_SCALE = 1.0F / TelescopeMountEntity.FOV_DIVISOR;

    /** Beyond this, a session ends on its own — the crosshair can never reach the telescope block
     * again once its camera is at the eyepiece (§2.3.0 bug C), so walking away has to be a real
     * exit door, not just sneaking. */
    private static final double WALK_AWAY_DISTANCE_SQ = 3.0 * 3.0;

    /** Set the instant the player clicks an unoccupied telescope, before the server has actually
     * created (and synced back) the mount that click will spawn — {@link #onClientTick} keeps
     * looking for it every tick until it appears. */
    private static @Nullable BlockPos pendingTelescopePos;
    private static @Nullable TelescopeMountEntity activeMount;

    /** The fixed baseline the player's own rotation is held at for the whole session — read once
     * when the session starts, reasserted every frame so their body does not visibly spin for a
     * view nobody is looking through any more ("вижу как крутится мой персонаж"). */
    private static float sessionYaw;
    private static float sessionPitch;

    /** This client's own authoritative telescope aim — unclamped, accumulated every frame from raw
     * mouse delta, resynced to the mount's own (clamped) result immediately after each push so the
     * next frame's delta is measured from where the tube actually ended up, not an overshot phantom
     * value. Never read back from the entity except once, at session start (see the class doc's
     * "server fighting client" note). */
    private static float clientYaw;
    private static float clientPitch;

    private static boolean sneakWasDown;

    /** The mount the local player is currently looking through, or {@code null} — read fresh every
     * frame by {@code TelescopeHud} instead of tracking a second copy of this same state. */
    public static @Nullable TelescopeMountEntity currentMount() {
        return activeMount;
    }

    /** The same right-click a player uses to start or stop an observation session
     * ({@code TelescopeBlock.useWithoutItem}) fires this event on the client too — this is the
     * client's own half of that interaction, entirely independent of the server's (which only ever
     * tracks who owns a session, never touches the camera). */
    @SubscribeEvent
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof TelescopeBlock)) {
            return;
        }
        BlockPos pos = event.getPos().immutable();
        if (TelescopeMountEntity.findAt(event.getLevel(), pos) != null) {
            stopObserving();
        } else {
            pendingTelescopePos = pos;
        }
    }

    /** Ends the session locally and tells the server, unconditionally — safe even when the server
     * already knows (a right-click also ends it server-side through the ordinary block interaction;
     * a redundant stop payload there just finds no observed mount left and does nothing). The one
     * path this is *the only* way the server ever finds out: sneak and walk-away, neither of which
     * is a block interaction at all. */
    private static void stopObserving() {
        BlockPos telescopePos = pendingTelescopePos;
        pendingTelescopePos = null;
        if (activeMount != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.setCameraEntity(minecraft.player);
            }
        }
        activeMount = null;
        if (telescopePos != null) {
            ClientPacketDistributor.sendToServer(new TelescopeStopObservingPayload(telescopePos));
        }
    }

    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        if (activeMount != null && activeMount.isRemoved()) {
            // The telescope was broken out from under this session — nothing rides it any more to
            // eject automatically, so this tick is the first chance to notice.
            stopObserving();
        }

        if (activeMount == null && pendingTelescopePos != null) {
            TelescopeMountEntity found = TelescopeMountEntity.findAt(minecraft.level, pendingTelescopePos);
            if (found != null) {
                activeMount = found;
                minecraft.setCameraEntity(found);
                // A fixed baseline for the whole session: the player's own rotation is reset to
                // exactly this every frame from here on, so it never drifts.
                sessionYaw = player.getYRot();
                sessionPitch = player.getXRot();
                // This class's own authoritative aim starts from whatever the mount is *actually*
                // aimed at right now (a restored saved aim, or the neutral rest angle) — the one
                // and only time this ever reads the entity's rotation back.
                clientYaw = found.getYRot();
                clientPitch = found.getXRot();
            }
        }

        if (activeMount == null) {
            return;
        }

        // §2.3.3 step 6: sneak or walking away are the only reachable exits once the crosshair can
        // no longer touch the telescope block (bug C) — edge-detected so holding sneak from before
        // the session started does not immediately end it.
        boolean sneakDown = minecraft.options.keyShift.isDown();
        boolean walkedAway = player.position().distanceToSqr(
                net.minecraft.world.phys.Vec3.atCenterOf(activeMount.telescopePos())) > WALK_AWAY_DISTANCE_SQ;
        boolean sneakPressed = sneakDown && !sneakWasDown;
        sneakWasDown = sneakDown;
        if (sneakPressed || walkedAway) {
            stopObserving();
            return;
        }

        // The server needs the aim for capture attempts and for saving it when the session ends —
        // tick rate is plenty for that (§2.3.3 step 7); the camera itself never waits on this.
        ClientPacketDistributor.sendToServer(new TelescopeAimUpdatePayload(
                activeMount.telescopePos(), activeMount.getYRot(), activeMount.getXRot()));
    }

    /** §2.3.3 steps 3+4, merged into the one hook actually proven to run at the right moment
     * (this class's own javadoc, "Judder"): {@code ComputeCameraAngles} fires from inside
     * {@code Camera#alignWithEntity}, called by {@code GameRenderer.update()} — which
     * {@code Minecraft.renderFrame} calls strictly *after* {@code MouseHandler} has already turned
     * the player for this exact frame, and strictly *before* {@code Camera} consumes whatever this
     * handler sets. Harvesting input here, rather than in a separate, earlier-firing hook, means
     * there is no second place left to be out of order with — the previous design's actual bug.
     *
     * <p>Idempotent regardless: if this event ever fires more than once in a frame, repeating the
     * same read (the player's rotation has not changed since the first call) and the same push
     * produces the identical result. */
    @SubscribeEvent
    private static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (activeMount == null || event.getCamera().entity() != activeMount) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        float rawDeltaYaw = player.getYRot() - sessionYaw;
        float rawDeltaPitch = player.getXRot() - sessionPitch;
        clientYaw += rawDeltaYaw * SENSITIVITY_SCALE;
        clientPitch += rawDeltaPitch * SENSITIVITY_SCALE;
        activeMount.setClampedAim(clientYaw, clientPitch);
        // Resync to the committed (possibly clamped) value, not this class's own unclamped one —
        // the next frame's delta is measured from where the tube actually ended up, so pushing
        // against a limit for a while and then reversing responds immediately instead of first
        // having to "unwind" through however far the accumulator overshot.
        clientYaw = activeMount.getYRot();
        clientPitch = activeMount.getXRot();
        event.setYaw(activeMount.getYRot());
        event.setPitch(activeMount.getXRot());
        event.setRoll(0.0F);

        // The player's own head has no view left to answer for — held at the session's fixed
        // baseline instead of visibly spinning with every mouse movement actually meant for the
        // telescope, not the player's own body ("вижу как крутится мой персонаж"). Both current
        // *and* old values: Entity#turn advances xRotO/yRotO by the same raw delta it applies to
        // xRot/yRot, so leaving the old values adrift would still show as the player's own body
        // jittering for anyone else watching this player's interpolated rotation.
        player.setYRot(sessionYaw);
        player.setXRot(sessionPitch);
        player.setOldRot();
        player.setYHeadRot(sessionYaw);
    }

    /** §2.3.3 step 5, direct request: "надо локально скрывать игрока но не на сервере что-бы
     * другие игроки видели." {@code LevelRenderer} carries an explicit NeoForge patch that renders
     * the local player the instant the camera looks through a different entity — deliberate, for
     * every other legitimate use of {@code setCameraEntity} (spectating, dev cameras), and exactly
     * wrong for this one. Cancelling the render here is purely a client-side draw call: nothing
     * about the player's own entity state changes, so the server and every other client still see
     * an ordinary player standing there. */
    @SubscribeEvent
    private static void onHideLocalPlayer(RenderPlayerEvent.Pre<?> event) {
        if (activeMount == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && event.getRenderState().id == minecraft.player.getId()) {
            event.setCanceled(true);
        }
    }

    private TelescopeCamera() {}
}
