package play.xponer.astronima.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import play.xponer.astronima.client.gravity.OrientationRenderEvents;
import play.xponer.astronima.sim.gravity.Orientation;

/**
 * design/eva-mobility.md §1.4, revised: applies this player's own pitch/roll <em>inside</em>
 * vanilla's own body-yaw rotation, not wrapped outside the entire render the way the first attempt
 * ({@code OrientationRenderEvents}'s own {@code RenderLivingEvent.Pre}/{@code Post}, now removed)
 * did.
 *
 * <p><strong>Found live, the second time a tumbling model was actually watched, reported as pitch
 * input reading as roll on the model and the model facing backwards / upside down at ordinary
 * facings:</strong> wrapping the entire render in one outer {@code poseStack.mulPose} put our
 * rotation <em>outside</em> vanilla's own {@code LivingEntityRenderer#setupRotations}, which applies
 * the entity's real body yaw — {@code poseStack.mulPose(Axis.YP.rotationDegrees(180 - bodyRot))},
 * confirmed against this project's own decompiled source (rule 2) — <em>nested inside</em> whatever
 * the outer wrapper already applied. A world-space wrapper rotation and a local-space nested
 * rotation do not commute: the two only agreed at the one facing where this system's own
 * "yaw stripped" reference (180°, see {@code OrientationTarget#forModel}) happened to coincide with
 * the entity's real yaw. At any other facing pitch and roll got scrambled together with the wrong
 * yaw, which is exactly what "pitch reads as roll" is a symptom of.
 *
 * <p>The fix is to stop wrapping and inject <em>into</em> {@code setupRotations} itself, right after
 * vanilla's own body-yaw {@code mulPose} call — at that point in the pose stack vanilla's real yaw
 * is already applied, so multiplying in this player's own pitch/roll next nests it correctly
 * <em>inside</em> real yaw, exactly the composition {@code OrientationTarget#forModel} always
 * intended (yaw outermost and real to the entity; pitch/roll innermost and this system's own). The
 * stashed value itself did not need to change — {@code RegisterRenderStateModifiersEvent}'s own
 * extraction-time stash in {@link OrientationRenderEvents} was always correct; only where it got
 * consumed was wrong.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class OrientationModelTiltMixin {

    @Inject(method = "setupRotations", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V",
            ordinal = 0, shift = At.Shift.AFTER))
    private void astronima$applyPitchRollInsideBodyYaw(LivingEntityRenderState state,
            PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
        Orientation orientation = state.getRenderData(OrientationRenderEvents.ORIENTATION_KEY);
        if (orientation == null) {
            return;
        }
        poseStack.mulPose(new Quaternionf(
                orientation.x(), orientation.y(), orientation.z(), orientation.w()));
    }
}
