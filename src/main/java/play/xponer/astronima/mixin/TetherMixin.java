package play.xponer.astronima.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.Tether;
import play.xponer.astronima.sim.gravity.TetherState;

/**
 * design/eva-mobility.md §3.2/§3.4: applies {@link Tether}'s own damped-spring restoring force
 * each tick, and the two conditions that let go of the line automatically rather than only on a
 * second key press — landing (the same {@code !onGround()} gate every mechanic in this design
 * shares: a tether is an airborne concern) and the anchor block itself stopping being solid (mined
 * out from under the line, or blown up).
 *
 * <p>Injected at the {@code TAIL} of {@code LivingEntity#travel}, the same site {@code
 * RcsThrustMixin} already uses and for the same reason: by then vanilla's own friction and gravity
 * are already folded into {@code deltaMovement}, so this only ever adds one more vector on top.
 * Excludes a player with Creative/Spectator flight active, matching {@code AirControlMixin}'s own
 * note — this whole feature is a survival mechanic and has no business fighting vanilla's own,
 * already-unrestricted flight.
 */
@Mixin(LivingEntity.class)
public abstract class TetherMixin {

    @Inject(method = "travel", at = @At("TAIL"))
    private void astronima$applyTether(Vec3 input, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player) || player.getAbilities().flying) {
            return;
        }
        if (player.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        TetherState tether = player.getData(ModAttachments.TETHER.get());
        if (!tether.active()) {
            return;
        }
        if (player.onGround()) {
            player.setData(ModAttachments.TETHER.get(), TetherState.NONE);
            return;
        }
        BlockPos anchorPos = BlockPos.containing(tether.anchorX(), tether.anchorY(), tether.anchorZ());
        BlockState anchorState = player.level().getBlockState(anchorPos);
        if (anchorState.isAir()) {
            player.setData(ModAttachments.TETHER.get(), TetherState.NONE);
            return;
        }
        if (Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(player))) {
            return;
        }
        double toAnchorX = tether.anchorX() - player.getX();
        double toAnchorY = tether.anchorY() - player.getY();
        double toAnchorZ = tether.anchorZ() - player.getZ();
        Vec3 velocity = player.getDeltaMovement();
        double[] accel = Tether.restoringAcceleration(toAnchorX, toAnchorY, toAnchorZ,
                tether.restLength(), velocity.x, velocity.y, velocity.z);
        if (accel[0] == 0.0 && accel[1] == 0.0 && accel[2] == 0.0) {
            return;
        }
        player.setDeltaMovement(velocity.add(accel[0], accel[1], accel[2]));
    }
}
