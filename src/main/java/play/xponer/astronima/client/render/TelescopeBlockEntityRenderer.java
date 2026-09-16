package play.xponer.astronima.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.entity.TelescopeBlockEntity;
import play.xponer.astronima.client.model.TelescopeModels;
import play.xponer.astronima.sim.optics.TelescopeMount;
import play.xponer.astronima.telescope.TelescopeMountEntity;

/**
 * Turns the telescope's tube to whatever it is actually aimed at, per direct request after
 * playtest: "надо сделать чтобы труба крутилась" — {@code models/block/telescope.json} draws
 * only the plinth/cap/post/collar now; the yoke and optical tube are real geometry here instead,
 * because a static JSON model can only ever show one fixed pose (design/astra-telescope.md §6).
 *
 * <p>Two nested rotations, matching the two axes an alt-azimuth mount actually has: azimuth
 * (about the vertical) at the yoke's own pivot, then elevation (about the horizontal) at the
 * trunnion pivot 1.7 pixels above it — worked by hand and cross-checked against
 * {@code TelescopeMountEntity.directionFromRotation} (the same yaw/pitch convention already
 * proven against decompiled {@code Entity#calculateViewVector}):
 *
 * <ul>
 *   <li>At rest ({@code pitchDegrees == -90}, {@code GROUND_TRIPOD}'s own zero) the tube points
 *   straight up with no elevation rotation at all — the exact pose the original static model was
 *   built in, so azimuth alone cannot visibly move it (yaw is genuinely undefined at that pole,
 *   which is the same fact {@code GROUND_TRIPOD}'s dead zone exists to avoid for the rider).</li>
 *   <li>At the horizon ({@code pitchDegrees == 0}) the tube tips a full 90 degrees off vertical,
 *   into the horizontal plane the rider's own {@code yawDegrees} points across.</li>
 * </ul>
 */
public class TelescopeBlockEntityRenderer
        implements BlockEntityRenderer<TelescopeBlockEntity, TelescopeRenderState> {

    /** The azimuth pivot, in block fractions — the collar's own top centre
     * ({@code models/block/telescope.json}'s removed "collar" element topped out at y=11.1). */
    private static final double AZIMUTH_PIVOT_Y = 11.1 / 16.0;

    private final ModelPart yoke;
    private final ModelPart tube;

    public TelescopeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.yoke = context.bakeLayer(TelescopeModels.YOKE);
        this.tube = context.bakeLayer(TelescopeModels.TUBE);
    }

    @Override
    public TelescopeRenderState createRenderState() {
        return new TelescopeRenderState();
    }

    @Override
    public void extractRenderState(TelescopeBlockEntity telescope, TelescopeRenderState state,
                                   float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(telescope, state, breakProgress);
        TelescopeMountEntity mount = telescope.getLevel() != null
                ? TelescopeMountEntity.findAt(telescope.getLevel(), telescope.getBlockPos())
                : null;
        float[] rotation;
        if (mount != null) {
            rotation = new float[] {mount.getYRot(), mount.getXRot()};
        } else if (telescope.hasSavedAim()) {
            rotation = new float[] {telescope.savedYaw(), telescope.savedPitch()};
        } else {
            rotation = TelescopeMountEntity.restRotation();
        }
        state.yawDegrees = rotation[0];
        state.pitchDegrees = rotation[1];
    }

    @Override
    public void submit(TelescopeRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        // TelescopeMountEntity.directionFromRotation: yaw 0 points +Z, and a positive yaw turns
        // that direction toward -X — which is a NEGATIVE rotation about +Y in the standard
        // right-handed sense poseStack rotations use, hence the sign flip here.
        float azimuthRadians = (float) Math.toRadians(-state.yawDegrees);
        // pitchDegrees is -90 at GROUND_TRIPOD's own rest (straight up, no elevation rotation)
        // and 0 at the horizon (a full 90 degrees of tip) — i.e. elevation-from-up is pitch+90.
        float elevationRadians = (float) Math.toRadians(state.pitchDegrees + 90.0);

        poseStack.pushPose();
        poseStack.translate(0.5, AZIMUTH_PIVOT_Y, 0.5);
        poseStack.mulPose(new Quaternionf().rotationY(azimuthRadians));
        collector.submitModelPart(yoke, poseStack, RenderTypes.entitySolid(TelescopeModels.TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY, null);

        poseStack.translate(0.0, TelescopeMount.TRUNNION_RISE / 16.0, 0.0);
        poseStack.mulPose(new Quaternionf().rotationX(elevationRadians));
        collector.submitModelPart(tube, poseStack, RenderTypes.entitySolid(TelescopeModels.TEXTURE),
                state.lightCoords, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
    }
}
