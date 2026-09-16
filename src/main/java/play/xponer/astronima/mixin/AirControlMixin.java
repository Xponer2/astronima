package play.xponer.astronima.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;

/**
 * design/microgravity.md §7's fix: with nothing to push against, mid-air input must add
 * nothing to velocity — only existing momentum, gravity, and a caught grab rail may change it.
 *
 * <p>{@code LivingEntity.getFrictionInfluencedSpeed} calls {@code getFlyingSpeed()} on exactly
 * the branch where {@code onGround()} is already false — vanilla's own airborne air-control
 * speed, added to {@code deltaMovement} every tick via {@code moveRelative}. Left alone this
 * survives untouched in the asteroid dimension: {@code AsteroidGravity} only ever scales
 * {@code Attributes.GRAVITY}, so a player drifting in zero gravity still steers, accelerates
 * and stops on command — indistinguishable, from inside the game, from walking on an invisible
 * floor. Reported directly in play twice (§7's own quote, and again independently later).
 *
 * <p>Zeroing this one number is the whole fix: {@code moveRelative(0, input)} adds the zero
 * vector regardless of input direction, so {@code deltaMovement} — the body's actual momentum —
 * passes through {@code travelInAir} completely unchanged. Nothing about grounded movement is
 * touched; {@code getFlyingSpeed} is never called from the {@code onGround()} branch, so
 * magnetic boots' grip and their speed cost (both grounded-only) are untouched by this mixin.
 *
 * <p>Applied to every {@code LivingEntity}, not only players — the same reasoning
 * {@link VacuumFallMixin} already gives: a mob that keeps steering next to a player who cannot
 * is the kind of detail that reads as a bug before anyone works out why.
 *
 * <p><strong>Except a player with Creative/Spectator flight active</strong> — found live, reported
 * as vertical thrust computing correctly (confirmed via {@code RcsThrustMixin}'s own diagnostic log:
 * a real, substantial downward acceleration every tick while looking down and holding forward) but
 * never actually producing visible downward motion: Creative flight is vanilla's own, wholly
 * separate movement system, and its own vertical control is tied to jump/sneak, never to look
 * direction — it does not route speed through this method the way `travelInAir` does, but it does
 * reset `deltaMovement.y` toward whatever that separate system wants every tick, which silently
 * erased this system's own contribution before it could ever accumulate into visible movement.
 * This whole feature is a survival mechanic; Creative's own flight predates it and already grants
 * full unrestricted movement, so the two have no business fighting each other — a flying Creative
 * or Spectator player keeps ordinary vanilla flight, untouched.
 */
@Mixin(LivingEntity.class)
public abstract class AirControlMixin {

    @Inject(method = "getFlyingSpeed", at = @At("RETURN"), cancellable = true)
    private void astronima$noAirControlInMicrogravity(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && player.getAbilities().flying) {
            return;
        }
        if (self.level().dimension() == ModDimensions.ASTEROID_LEVEL
                && !Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(self))) {
            cir.setReturnValue(0.0F);
        }
    }
}
