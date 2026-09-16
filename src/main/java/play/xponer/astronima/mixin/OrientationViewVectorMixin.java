package play.xponer.astronima.mixin;

import net.minecraft.client.Minecraft;
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
 * design/eva-mobility.md §1.5, revised again: the real, unclamped look direction, for the one
 * consumer that actually matters most — block interaction.
 *
 * <p>Confirmed against this project's own decompiled source (rule 2): {@code Entity#pick} (block
 * breaking/placing, the crosshair itself) calls {@code getViewVector(partialTicks)} directly, which
 * derives from {@code getViewXRot}/{@code getViewYRot} — this player's stored {@code xRot} field,
 * which {@code setXRot} clamps to ±90° unconditionally. {@code OrientationInputMixin} already names
 * that clamp as an accepted, narrower cost for *aiming past vertical*; reported directly in play as
 * block breaking still targeting a different block than the crosshair shows, and far more often now
 * that tumbling is genuinely flyable — a rolled orientation's decomposed pitch routinely exceeds
 * what a single clamped float can represent, so the "narrow" edge case turned out not to be narrow
 * at all once actually flown.
 *
 * <p>Rather than fight the clamp, this bypasses it for the one consumer that needs the true,
 * unclamped direction: {@link LocalOrientation}'s own forward vector, exactly what the camera
 * already shows, with nothing lost to a ±90° ceiling that only ever existed because {@code xRot} is
 * a single float, never a real 3D orientation.
 *
 * <p>Targets {@code getViewVector} specifically, not the lower-level {@code calculateViewVector} —
 * that helper is reused for angles that are <em>not</em> "where is this entity looking" at all (an
 * item's own swing-particle offset, among others), so overriding it unconditionally would have
 * hijacked unrelated geometry along with the real fix. Client-only, like {@code
 * OrientationInputMixin}: the block a player targets is decided client-side and sent to the server
 * as a position, never re-derived server-side from rotation.
 */
@Mixin(Entity.class)
public abstract class OrientationViewVectorMixin {

    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void astronima$useRealUnclampedOrientation(float partialTicks,
                                                         CallbackInfoReturnable<Vec3> cir) {
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
        Orientation orientation = LocalOrientation.get();
        float[] forward = orientation.rotate(0f, 0f, -1f);
        cir.setReturnValue(new Vec3(forward[0], forward[1], forward[2]));
    }
}
