package play.xponer.astronima.client.gravity;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.sim.gravity.Orientation;

/**
 * Reported directly: "the camera smoothly drifts like I'm flying the instant I join a world —
 * jumping fixes it immediately." Root cause was never a rendering bug at all — it is
 * {@link LocalOrientation} starting life at {@link Orientation#IDENTITY}, which {@link Orientation}'s
 * own doc names explicitly as a real 180° turn away from this convention's own "yaw 0", not the
 * player's real spawn facing (0°, 0° — see {@code spawn/SpawnHandler}'s own teleport call).
 *
 * <p><strong>Why this shows up as camera drift, not an instant snap.</strong>
 * {@code GroundedTracker#isEffectivelyGrounded} has no history for a freshly-joined entity, and
 * vanilla's own {@code onGround()} genuinely needs a real physics tick to resolve true after a
 * teleport — so for the first tick or two after joining, {@code OrientationRollInputEvents} takes
 * its "airborne" branch, which leaves {@link LocalOrientation} exactly at its stale default rather
 * than writing the player's real facing. The moment grounded is correctly detected a tick later,
 * {@link LocalOrientation} snaps to the correct value, but {@code OrientationCameraEvents}' own
 * landing-smoothing window (it just "left airborne", from its own perspective) spends 400 ms
 * visibly interpolating the camera from that wrong, 180°-off value to the real one — precisely
 * the "smooth flying" symptom. Jumping right after joining "fixes" it only because, by then, at
 * least one real grounded tick has already run and silently corrected the stale value before the
 * jump's own landing-smoothing window has anything wrong left to interpolate away from.
 *
 * <p><strong>The fix is at the source, not in the smoothing or the grounded check.</strong> Both
 * of those serve real, separate purposes ({@code GroundedTracker}'s coyote time bridges an
 * ordinary one-block step, {@code OrientationCameraEvents}' smoothing is what makes a real landing
 * look good) — the actual bug is that nothing ever told {@link LocalOrientation} what the player's
 * real orientation is the instant a world is joined. Resynced here, the moment the client's own
 * player object exists (login) or gets replaced (respawn, dimension change) — before any tick of
 * {@code OrientationRollInputEvents} has a chance to see a stale value at all.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class OrientationJoinSync {

    @SubscribeEvent
    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        sync(event.getPlayer());
    }

    @SubscribeEvent
    private static void onClone(ClientPlayerNetworkEvent.Clone event) {
        sync(event.getNewPlayer());
    }

    private static void sync(LocalPlayer player) {
        LocalOrientation.set(Orientation.fromEulerYXZ(player.getYRot(), player.getXRot(), 0f));
    }

    private OrientationJoinSync() {}
}
