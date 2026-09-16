package play.xponer.astronima.client.gravity;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.ModKeybinds;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.Orientation;

/**
 * design/eva-mobility.md §1.2's roll axis, and — since this already runs every real tick — the
 * one other thing that has to happen every real tick: keeping a <em>grounded</em> player's real
 * {@link Orientation} mirroring vanilla's own {@code yRot}/{@code xRot} continuously, not left
 * stale from the last time they were airborne.
 *
 * <p><strong>Found live, the first time this was actually flown:</strong> land, stand around, jump
 * again — the camera snapped straight to whatever tumbled orientation was last active, because
 * nothing had touched the attachment since landing and {@link Orientation#composeLocal} only ever
 * runs while airborne. Fixed by treating "grounded" as "this orientation continuously tracks
 * vanilla," not "this system does nothing" — the moment a player leaves the ground, whatever was
 * written on the very last grounded tick is already the correct, current value, and diverges
 * smoothly from there instead of snapping from something stale.
 *
 * <p><strong>Tracks {@link LocalOrientation}, not {@code ModAttachments.ORIENTATION}</strong> —
 * found live, flown a second time: this class used to read and write the synced attachment
 * directly, which let the server's own echo of the value this class just sent it (see {@code
 * network/OrientationUpdatePayload}) land one round trip later and overwrite this tick's fresh
 * value with an older one — reported as the camera refusing to turn and snapping straight back,
 * worse in real multiplayer than it first seemed in a true single-player world with no listening
 * socket at all. {@link LocalOrientation}'s own doc has the full story; this class still *sends*
 * {@code ModAttachments.ORIENTATION} updates over the wire (that part was always correct — it is
 * the read-back that was not), it just never reads that attachment back for its own use again.
 *
 * <p><strong>Still mirrors every write onto the attachment's own local (client-side) copy</strong>
 * — found live, the third time this was flown: {@code RcsThrustMixin} reads that attachment, never
 * {@link LocalOrientation} (a client-only class the server side of {@code travel} cannot load), so
 * the local copy has to stay fresh too or thrust direction lags a full round trip behind the real
 * roll — reported as thrust pointing world-relative instead of body-relative while rolled 90°. A
 * stray late echo occasionally overwriting one tick's worth of thrust direction is unnoticeable in
 * a way the same echo corrupting every frame of camera rotation never was, which is why camera and
 * this player's own third-person model still avoid reading this back at all.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class OrientationRollInputEvents {

    /** Degrees per tick while a roll key is held — a first-pass, rule-41 number, open to
     *  retuning once this is actually flown. */
    private static final float ROLL_DEGREES_PER_TICK = 3.0f;

    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) {
            return;
        }
        if (player.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        boolean airborne = !Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(player));
        Orientation next;
        if (!airborne) {
            // Grounded: this fact continuously mirrors vanilla rather than sitting idle, so
            // leaving the ground never starts from a stale, previously-tumbled value.
            next = Orientation.fromEulerYXZ(player.getYRot(), player.getXRot(), 0f);
        } else {
            next = applyRollInput(minecraft.screen != null);
        }
        LocalOrientation.set(next);
        player.setData(ModAttachments.ORIENTATION.get(), next);
        if (airborne) {
            // The server's own copy only ever changes when a client tells it to (§1.2's own
            // found-live gap: Entity#turn fires client-side only, and a client-side setData never
            // reaches the server on its own) - sent only while airborne, matching the payload's
            // own server-side gate, so a grounded player never spends bandwidth nobody reads.
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                    play.xponer.astronima.network.OrientationUpdatePayload.of(next));
        }
    }

    private static Orientation applyRollInput(boolean screenOpen) {
        Orientation current = LocalOrientation.get();
        if (screenOpen) {
            return current;
        }
        boolean left = ModKeybinds.ROLL_LEFT.isDown();
        boolean right = ModKeybinds.ROLL_RIGHT.isDown();
        if (left == right) {
            return current; // neither held, or both at once - no net roll either way
        }
        float rollDeg = left ? -ROLL_DEGREES_PER_TICK : ROLL_DEGREES_PER_TICK;
        Orientation delta = Orientation.fromAxisAngle(0, 0, 1, (float) Math.toRadians(rollDeg));
        return current.composeLocal(delta);
    }

    private OrientationRollInputEvents() {}
}
