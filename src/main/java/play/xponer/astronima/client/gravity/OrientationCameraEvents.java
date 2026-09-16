package play.xponer.astronima.client.gravity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.Orientation;

/**
 * design/eva-mobility.md §1.3: the camera, fed from the one real fact. Confirmed against this
 * project's own decompiled {@code Camera.alignWithEntity} (rule 2): every frame it posts
 * {@code ViewportEvent.ComputeCameraAngles} and feeds the result straight into
 * {@code Camera#setRotation(yaw, pitch, roll)} — real, mutable {@code setYaw/setPitch/setRoll} on
 * the event, the sanctioned NeoForge hook for exactly this, not a workaround. No mixin: this only
 * ever changes how the world is drawn, never any entity's own facts.
 *
 * <p><strong>Smooths only while airborne, or for a brief window right after landing</strong> —
 * found live, reported as an instant snap on landing: originally gating this off the moment
 * {@code onGround()} became true handed the camera straight back to vanilla's own raw computation
 * with no transition at all. Smoothing unconditionally, every frame, fixed that but overcorrected:
 * {@link OrientationTarget#forCamera}'s grounded target tracks the live mouse continuously (it is
 * recomputed fresh every single frame, never tick-quantized the way the airborne target is), so
 * chasing it with an exponential filter adds a real, constant phase lag to ordinary standing-still
 * mouse look — reported as the camera itself feeling smoothed/mushy even just standing on a block.
 * {@link #LANDING_SMOOTH_WINDOW_NANOS} bridges only the actual discontinuity (a tumbled orientation
 * snapping upright on landing); once it elapses, grounded play is byte-for-byte what
 * {@code forCamera} already promises — vanilla's own zero-latency reading, nothing added.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class OrientationCameraEvents {

    /** How long after landing the camera keeps smoothing the tumble back to upright, before
     *  handing off to vanilla's own instant reading — one smoothing time constant's worth is
     *  already enough to erase the snap; longer than that is pure added input latency. There is
     *  only ever one local camera, so one static timestamp (not a per-entity map) is enough. */
    private static final long LANDING_SMOOTH_WINDOW_NANOS = 400_000_000L;

    private static long lastAirborneNanos = Long.MIN_VALUE;

    @SubscribeEvent
    private static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Entity cameraEntity = event.getCamera().entity();
        if (!(cameraEntity instanceof Player player)) {
            return;
        }
        if (player.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        boolean airborne = !Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(player));
        long now = System.nanoTime();
        if (airborne) {
            lastAirborneNanos = now;
        }
        Orientation target = OrientationTarget.forCamera(player);
        Orientation display;
        if (airborne || now - lastAirborneNanos < LANDING_SMOOTH_WINDOW_NANOS) {
            display = OrientationSmoothing.smoothedToward(
                    player.getId(), OrientationSmoothing.Purpose.CAMERA, target);
        } else {
            display = target;
        }
        float[] yawPitchRoll = display.toEulerYawPitchRoll();
        event.setYaw(yawPitchRoll[0]);
        event.setPitch(yawPitchRoll[1]);
        event.setRoll(yawPitchRoll[2]);
    }

    private OrientationCameraEvents() {}
}
