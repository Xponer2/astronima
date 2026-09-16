package play.xponer.astronima.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.Orientation;
import play.xponer.astronima.sim.gravity.RcsThrust;

/**
 * design/eva-mobility.md §3.5: reported directly in play as genuinely uncomfortable that WASD does
 * nothing at all while airborne ({@code AirControlMixin}'s own correct, deliberate zero) — real
 * flight control was wanted, camera-relative in the full 3D sense (point down at the surface below
 * and hold forward, actually fly there), not merely a small correction on top of otherwise
 * uncontrolled drift. Applies {@link RcsThrust}'s camera-relative acceleration on top of whatever
 * {@code travel} already computed, rather than replacing any of it — momentum stays real (this only
 * ever adds acceleration on top of existing velocity, never resets or caps it, so stopping still
 * means burning the opposite direction), so the phase's whole point ({@code Microgravity}'s own "you
 * cannot stop") survives even though the acceleration itself is strong enough to feel like real
 * piloting.
 *
 * <p>Injected at the {@code TAIL} of {@code LivingEntity#travel}, after vanilla's own friction and
 * gravity are already folded into {@code deltaMovement} (and after {@code AirControlMixin} has
 * already zeroed vanilla's own air control for this tick) — this simply adds one more small vector
 * on top, never fighting or duplicating vanilla's own physics.
 *
 * <p>Reads the real orientation from the synced attachment, not {@code LocalOrientation} — unlike
 * the camera and this player's own model, {@code travel} runs identically on both the owning
 * client (for local prediction) and the server (authoritative), so the source has to be one both
 * sides agree on. A tick's worth of staleness in a thrust *direction* is not perceptible the way it
 * would be in a camera's own per-frame rotation.
 *
 * <p><strong>Except a player with Creative/Spectator flight active</strong> — found live via a
 * temporary diagnostic log (now removed, its job done): the computed thrust was real and correct
 * every tick (a substantial downward vector while looking down and holding forward), but never
 * produced visible motion, because Creative flight's own wholly separate vertical control (tied to
 * jump/sneak, never to look direction) resets {@code deltaMovement.y} toward what it wants before
 * this addition could ever accumulate. See {@code AirControlMixin}'s own matching note — this whole
 * feature is a survival mechanic, and a flying Creative or Spectator player keeps ordinary vanilla
 * flight, untouched.
 */
@Mixin(LivingEntity.class)
public abstract class RcsThrustMixin {

    @Inject(method = "travel", at = @At("TAIL"))
    private void astronima$applyRcsThrust(Vec3 input, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player) || player.getAbilities().flying) {
            return;
        }
        if (player.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        if (Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(player))) {
            return;
        }
        Orientation orientation = player.getData(ModAttachments.ORIENTATION.get());
        double[] thrust = RcsThrust.accelerationFor(
                orientation, (float) input.x, (float) input.y, (float) input.z);
        if (thrust[0] == 0.0 && thrust[1] == 0.0 && thrust[2] == 0.0) {
            return;
        }
        player.setDeltaMovement(player.getDeltaMovement().add(thrust[0], thrust[1], thrust[2]));
    }
}
