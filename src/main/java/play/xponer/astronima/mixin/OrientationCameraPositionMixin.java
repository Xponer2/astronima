package play.xponer.astronima.mixin;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import play.xponer.astronima.client.gravity.LocalOrientation;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.Orientation;

/**
 * design/eva-mobility.md §1.3, revised: the third-person orbit pivot itself, not only the rotation
 * around it.
 *
 * <p><strong>Found live, reported as the F5 camera reading as detached from the character and
 * pivoting near the legs whenever tumbling, even after {@code OrientationModelTiltMixin} fixed the
 * model itself:</strong> {@code Camera#alignWithEntity}'s own eye anchor (confirmed against this
 * project's own decompiled source, rule 2) is {@code y = lerp(feetY) + lerp(eyeHeight)} — a fixed
 * <strong>world-up</strong> offset from the feet, never one rotated by the entity's own facing,
 * because vanilla's own "up" is always world-up. The third-person backward offset itself
 * ({@code Camera#move}) already correctly uses this player's own real, rolled rotation (it runs
 * after {@code ViewportEvent.ComputeCameraAngles}, which this mod already drives) — only the
 * *point it orbits around* stayed anchored near the feet in world space instead of rotating with
 * the body, which is exactly what "camera detached, pivots near the legs" is a symptom of.
 *
 * <p>Redirects the one {@code setPosition} call {@code alignWithEntity} makes for a non-passenger
 * camera, replacing the naive vertical add with the same offset rotated by this player's own real
 * {@link Orientation} — {@link LocalOrientation}, never the synced attachment, for the same reason
 * the camera and this player's own model already read it (see {@link LocalOrientation}'s own doc):
 * there is only ever one local camera, and it must never see a network round trip of its own input.
 *
 * <p><strong>Clipped, not trusted blindly</strong> — reported directly in play as the head
 * sometimes clipping into textures: the collision box stays upright on purpose (rule 2's own
 * confirmed boundary), so a heavy pitch or roll can point this rotated offset into a block the box
 * itself was never touching. {@link play.xponer.astronima.client.gravity.EyeOffsetClip}'s own doc
 * has the full story.
 */
@Mixin(Camera.class)
public abstract class OrientationCameraPositionMixin {

    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void astronima$rotateEyeAnchorWithBody(Camera camera, double x, double y, double z,
                                                    float partialTicks) {
        Entity cameraEntity = camera.entity();
        if (!(cameraEntity instanceof Player player)
                || player.level().dimension() != ModDimensions.ASTEROID_LEVEL
                || Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(player))) {
            this.setPosition(x, y, z);
            return;
        }
        double feetX = Mth.lerp((double) partialTicks, player.xo, player.getX());
        double feetY = Mth.lerp((double) partialTicks, player.yo, player.getY());
        double feetZ = Mth.lerp((double) partialTicks, player.zo, player.getZ());
        float eyeHeightLerp = Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight);
        float[] up = LocalOrientation.get().rotate(0f, eyeHeightLerp, 0f);
        net.minecraft.world.phys.Vec3 safeOrigin =
                new net.minecraft.world.phys.Vec3(feetX, feetY + eyeHeightLerp, feetZ);
        net.minecraft.world.phys.Vec3 candidate = new net.minecraft.world.phys.Vec3(
                feetX + up[0], feetY + up[1], feetZ + up[2]);
        net.minecraft.world.phys.Vec3 clipped = play.xponer.astronima.client.gravity.EyeOffsetClip
                .clip(player.level(), player, safeOrigin, candidate);
        this.setPosition(clipped.x, clipped.y, clipped.z);
    }
}
