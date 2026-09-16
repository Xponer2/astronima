package play.xponer.astronima.client.gravity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.sim.gravity.Orientation;

import java.util.HashMap;
import java.util.Map;

/**
 * The render-frame smoothing every real consumer of {@link Orientation} needs — design/
 * eva-mobility.md §1.2/§1.3/§1.4. The authoritative value updates once per real tick at best (this
 * player's own roll input, or the grounded/airborne target it tracks), and for a remote player it
 * updates only as fast as the network delivers it — both far coarser than the screen's own frame
 * rate. Rendering the raw, most-recently-known value produces a visibly stepped roll (found live,
 * reported as "дёрганые") and a jarring instant snap the moment a target changes, such as landing
 * (also found live: "камера прям моментально снапится когда приземляешся").
 *
 * <p><strong>One smoothed value per entity, not one global pair</strong> — this project's first
 * attempt at this tracked only the local player, which left every remote player's own model (and
 * this player's own third-person view of themselves) rendering the raw, unsmoothed value. A frame
 * that has never seen an entity before starts its smoothed value exactly at the first real target
 * it is given, never at a default that would itself need to be smoothed away from.
 *
 * <p>Exponential smoothing toward the latest known target, not a fixed-window two-point
 * interpolation — deliberately: a two-point scheme needs to know exactly when the *next* real
 * update will land to interpolate correctly, which this system cannot promise for a network-driven
 * remote player the way a fixed tick rate can for a purely local value. Blending a fraction of the
 * remaining distance toward the target every frame converges quickly, never overshoots, and needs
 * no knowledge of when the next real update is due — the same shape a critically damped spring
 * takes, applied to a quaternion via {@link Orientation#slerp}.
 *
 * <p><strong>Keyed by (entity, purpose), not entity alone</strong> — found live, reported as the
 * camera refusing to move at all and jittering violently in third person: the local player is both
 * the camera's own target ({@code OrientationTarget.forCamera}) and, whenever their own avatar gets
 * rendered (third person, or just the shadow under first person), the model's own target
 * ({@code OrientationTarget.forModel}) — two different targets, computed independently, but a
 * single shared slot keyed only by {@code entityId} let whichever one ran last each frame stomp the
 * other's state, so neither consumer was ever actually converging toward its own real target.
 *
 * <p><strong>Cleans up after itself.</strong> {@link #forget} existed from the start but was never
 * actually called by anything — every entity ID this class ever smoothed (every remote player who
 * has ever tumbled in view, not only the local one) sat in this map forever, an unbounded leak over
 * a long session on a busy server. {@link EntityLeaveLevelEvent} — the same client-only hook fires
 * for a remote entity leaving render distance as for a real disconnect — is what actually calls it.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class OrientationSmoothing {

    /** Fraction of the remaining distance closed per frame at 60 fps — the shape a spring's own
     *  damping takes, not a physical constant. Scaled by the actual frame time so this reads the
     *  same regardless of frame rate. */
    private static final float SMOOTHING_PER_60FPS_FRAME = 0.35f;

    /** The two real consumers (§1.3, §1.4) — see the class doc's own "found live" note. */
    public enum Purpose { CAMERA, MODEL }

    private record Key(int entityId, Purpose purpose) {}

    private record State(Orientation orientation, long lastUpdateNanos) {}

    private static final Map<Key, State> smoothed = new HashMap<>();

    /**
     * The orientation to actually render this frame for {@code entityId}'s {@code purpose},
     * closing part of the gap toward {@code target}. Tracks its own real elapsed time per key
     * internally (real {@link System#nanoTime()} deltas, not an assumed fixed frame time) rather
     * than trusting a caller to compute it, so a dropped frame or a remote player's own uneven
     * update cadence can never accidentally desync the smoothing rate from what actually elapsed.
     */
    public static Orientation smoothedToward(int entityId, Purpose purpose, Orientation target) {
        Key key = new Key(entityId, purpose);
        long now = System.nanoTime();
        State previous = smoothed.get(key);
        if (previous == null) {
            smoothed.put(key, new State(target, now));
            return target;
        }
        float deltaSeconds = (now - previous.lastUpdateNanos()) / 1.0e9f;
        // 1 - (1 - perFrame)^(deltaSeconds * 60) - the per-frame blend factor generalised to a
        // real elapsed time, so this converges at the same real-world rate at 30fps or at 240fps.
        float framesElapsed = Math.max(0f, deltaSeconds) * 60f;
        float factor = 1f - (float) Math.pow(1.0 - SMOOTHING_PER_60FPS_FRAME, framesElapsed);
        Orientation next = previous.orientation().slerp(target, Math.min(1f, factor));
        smoothed.put(key, new State(next, now));
        return next;
    }

    /** Drops any cached state for an entity no longer worth tracking (left render distance,
     *  disconnected) - a leak this cache would otherwise carry forever. Both purposes share one
     *  entity lifecycle, so both are dropped together. */
    public static void forget(int entityId) {
        smoothed.remove(new Key(entityId, Purpose.CAMERA));
        smoothed.remove(new Key(entityId, Purpose.MODEL));
    }

    @SubscribeEvent
    private static void onEntityLeave(EntityLeaveLevelEvent event) {
        forget(event.getEntity().getId());
    }

    private OrientationSmoothing() {}
}
