package play.xponer.astronima.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import play.xponer.astronima.client.gravity.LocalOrientation;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.Orientation;

/**
 * design/eva-mobility.md §1.3, the same real bug found once already for the camera
 * ({@code OrientationCameraPositionMixin}), found again in a second, separate place: block
 * interaction's own raycast <em>origin</em>, not only the camera's own render position.
 *
 * <p>Confirmed against this project's own decompiled source (rule 2): {@code Entity#pick} — block
 * breaking, placing, the crosshair itself — reads its ray's start point from {@code
 * getEyePosition(partialTicks)}, which anchors the eye a fixed {@code eyeHeight} <strong>straight up
 * in world space</strong> from the feet, exactly the same naive offset {@code Camera#alignWithEntity}
 * used before this project's own fix — just in a wholly separate code path this mod had not touched
 * yet. Reported directly in play as the raycast being "thrown from somewhere else" (a rolled or
 * pitched player's true eye is nowhere near a world-up offset from their feet), on top of round 6's
 * own view-<em>direction</em> fix — a correct direction from the wrong origin still misses.
 *
 * <p>Same fix as the camera's own: the eye offset rotated by this player's real
 * {@link LocalOrientation}, not added straight up. Only the interpolated overload is touched —
 * {@code getEyePosition()} (no-arg, via {@code getEyeY()}) backs gameplay-mechanical checks like
 * breathing and fluid detection, which are correctly about the player's real world position and
 * have nothing to do with where they are looking.
 *
 * <p><strong>Deliberately NOT clipped, unlike the camera's own equivalent fix</strong> — tried
 * once, reverted the same round: {@code OrientationCameraPositionMixin} clips its own rotated
 * offset ({@code EyeOffsetClip}) because an un-clipped render camera can show the inside of a
 * block, a real but purely cosmetic problem. Doing the same here broke something worse: a steep
 * pitch (aiming at a block's own top face needs one) swings this rotated offset from mostly
 * vertical toward mostly horizontal — geometrically correct for a real full-body rotation — which
 * made it graze nearby ground far more often than the old fixed vertical offset ever did, and a
 * clip firing there pulled the raycast's own origin somewhere that no longer agreed with the
 * direction {@code OrientationViewVectorMixin} was already reporting. Reported directly in play as
 * the tether attaching to a block's side but never its top. A raycast that occasionally starts a
 * few pixels inside a wall is a much smaller problem than one that silently answers the wrong
 * question, so this stays the plain rotated offset, unclipped.
 */
@Mixin(Entity.class)
public abstract class OrientationEyePositionMixin {

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true)
    private void astronima$rotateEyeOffsetWithBody(float partialTickTime,
                                                     CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player player) || !self.level().isClientSide()) {
            return;
        }
        if (net.minecraft.client.Minecraft.getInstance().player != player) {
            return;
        }
        if (self.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        if (Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(self))) {
            return;
        }
        double feetX = Mth.lerp((double) partialTickTime, self.xo, self.getX());
        double feetY = Mth.lerp((double) partialTickTime, self.yo, self.getY());
        double feetZ = Mth.lerp((double) partialTickTime, self.zo, self.getZ());
        Orientation orientation = LocalOrientation.get();
        float[] up = orientation.rotate(0f, self.getEyeHeight(), 0f);
        cir.setReturnValue(new Vec3(feetX + up[0], feetY + up[1], feetZ + up[2]));
    }
}
