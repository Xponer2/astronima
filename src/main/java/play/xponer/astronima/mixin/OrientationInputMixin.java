package play.xponer.astronima.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import play.xponer.astronima.client.gravity.LocalOrientation;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.Orientation;

/**
 * design/eva-mobility.md §1.2: the client-side half of a real, composed orientation. Vanilla's own
 * mouse-look, {@code Entity#turn(double, double)}, is called only from {@code MouseHandler} on the
 * local player's own client-side entity instance — never on the server, never for any other
 * entity in ordinary play.
 *
 * <p>Composes the <em>same</em> per-frame mouse delta into the real orientation, in this
 * orientation's own current local frame ({@link Orientation#composeLocal}) — never a second,
 * independently-accumulated Euler pair that could drift from what {@code xRot}/{@code yRot} show.
 * Gated exactly like {@link AirControlMixin}: only in {@link ModDimensions#ASTEROID_LEVEL}, only
 * while airborne ({@link Microgravity#hasAirControl}), and only for the actual local player — a
 * grounded or a remote player is untouched by construction.
 *
 * <p><strong>§1.5's original "leave xRot/yRot completely alone" is revised, found wrong in play
 * (rule 2's own lesson applied to a design decision instead of an API claim):</strong> the block
 * interaction raycast (and every other consumer of "what is this player aiming at" — breaking,
 * placing, the crosshair itself) reads {@code xRot}/{@code yRot} directly, not this system's real
 * {@link Orientation}. Reported directly in play, first attempt: the camera showed one block, a
 * completely different one broke. Fixed at {@code TAIL} rather than {@code HEAD} — after vanilla's
 * own delta-based update has already run and would otherwise overwrite anything written earlier in
 * the same call — by writing this tick's real yaw/pitch back onto {@code xRot}/{@code yRot} too.
 * {@code setXRot}'s own unconditional ±90° clamp still applies past-vertical aiming, exactly the
 * narrower, honestly-named cost the very first draft of this document flagged before the ambitious
 * rewrite made it seem avoidable — it was avoidable for the *camera*, never for *aiming*.
 *
 * <p><strong>Reads {@link LocalOrientation}, not the synced attachment</strong> — found live, the
 * second time this was actually flown: composing onto {@code ModAttachments.ORIENTATION} directly
 * let the server's own echo of this same value (see {@code network/OrientationUpdatePayload})
 * round-trip back and stomp this tick's fresh mouse input with an older one, reported as the camera
 * refusing to turn at all. {@link LocalOrientation}'s own doc has the full story.
 *
 * <p><strong>Still mirrors the write onto the attachment's own local (client-side) copy</strong> —
 * found live, the third time this was flown: {@code RcsThrustMixin} needs a source that means the
 * same thing on the server as it does here (it runs on both, for local prediction to agree with
 * server authority), so it reads the synced attachment, never {@link LocalOrientation} (a
 * client-only class a dedicated server cannot even load). Not mirroring this write left that
 * attachment client-side copy stale by a full round trip for the local player specifically,
 * reported as thrust pointing the wrong way while visibly rolled — this class's own write keeps the
 * *local* copy fresh again; only camera and this player's own third-person model still avoid
 * reading it back, since a stray late echo overwriting one tick's worth of thrust direction is
 * unnoticeable in a way the same echo corrupting every frame of camera rotation never was.
 */
@Mixin(Entity.class)
public abstract class OrientationInputMixin {

    /** Same scale vanilla's own {@code Entity#turn} uses, so the free-flight look speed matches
     *  the grounded one a player already knows. */
    private static final float MOUSE_SENSITIVITY = 0.15f;

    @Inject(method = "turn", at = @At("TAIL"))
    private void astronima$composeRealOrientation(double xo, double yo, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player player) || !self.level().isClientSide()) {
            return;
        }
        if (Minecraft.getInstance().player != player) {
            return;
        }
        if (self.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        if (Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(self))) {
            return;
        }
        Orientation current = LocalOrientation.get();
        float pitchDeltaRad = (float) Math.toRadians(-yo * MOUSE_SENSITIVITY);
        float yawDeltaRad = (float) Math.toRadians(-xo * MOUSE_SENSITIVITY);
        Orientation pitchDelta = Orientation.fromAxisAngle(1, 0, 0, pitchDeltaRad);
        Orientation yawDelta = Orientation.fromAxisAngle(0, 1, 0, yawDeltaRad);
        Orientation updated = current.composeLocal(pitchDelta).composeLocal(yawDelta);
        LocalOrientation.set(updated);
        player.setData(ModAttachments.ORIENTATION.get(), updated);

        // Aiming has to agree with what the camera shows, even though it can only ever agree up
        // to setXRot's own clamp - see the class doc's own revision note.
        float[] yawPitchRoll = updated.toEulerYawPitchRoll();
        self.setYRot(yawPitchRoll[0]);
        self.setXRot(yawPitchRoll[1]);
    }
}
