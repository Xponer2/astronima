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
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.MoldBlock;
import play.xponer.astronima.block.entity.MoldBlockEntity;
import play.xponer.astronima.client.model.MoldModels;
import play.xponer.astronima.sim.MoldBranching;

/**
 * Draws a colony as {@link MoldBranching} computes it: one small unit twig, positioned and
 * stretched per segment, never a baked model or a sampled texture (design/mold-growth.md §3c).
 *
 * <p>Only one outer transform is needed regardless of which surface a colony is on: {@link
 * MoldBlock#awayDirection} gives the real-world direction {@link MoldBranching}'s own local +Y
 * axis (its "up, away from the surface" direction) has to end up pointing at, and a single
 * single-axis rotation pivoted at the block's centre gets it there for every one of the six
 * possible directions — derived directly from that mapping rather than reused from a shape with
 * a meaningful facing (buttons' own rotation table), because a random branching clump has no
 * such asymmetry to preserve.
 */
public class MoldBlockEntityRenderer implements BlockEntityRenderer<MoldBlockEntity, MoldRenderState> {

    /** A base near-white so the per-twig tint is not fighting a coloured texture underneath it
     *  — the same reasoning {@link play.xponer.astronima.client.model.WireModels} states for
     *  reusing this exact texture for a tinted shape. */
    private static final int MOLD_LOW = 0xFF3A4A32;
    private static final int MOLD_HIGH = 0xFF7C8F5A;

    private final ModelPart twig;

    public MoldBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.twig = context.bakeLayer(MoldModels.TWIG).getChild("twig");
    }

    @Override
    public MoldRenderState createRenderState() {
        return new MoldRenderState();
    }

    @Override
    public void extractRenderState(MoldBlockEntity entity, MoldRenderState state, float partialTick,
                                    Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(entity, state, breakProgress);
        state.away = MoldBlock.awayDirection(entity.getBlockState());
        state.segments = entity.twigs();
    }

    @Override
    public void submit(MoldRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                        CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(awayRotation(state.away));
        poseStack.translate(-0.5, -0.5, -0.5);

        var renderType = RenderTypes.entitySolid(MoldModels.TWIG_TEXTURE);
        for (MoldBranching.Segment segment : state.segments) {
            poseStack.pushPose();
            poseStack.translate(segment.loX() / 16.0, segment.loY() / 16.0, segment.loZ() / 16.0);
            poseStack.scale(segment.hiX() - segment.loX(), segment.hiY() - segment.loY(),
                    segment.hiZ() - segment.loZ());
            int tint = ARGB.srgbLerp(segment.shade(), MOLD_LOW, MOLD_HIGH) | 0xFF000000;
            collector.submitModelPart(twig, poseStack, renderType, state.lightCoords,
                    OverlayTexture.NO_OVERLAY, null, tint, null);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** A rotation mapping local +Y to {@code away}, pivoted (by the caller) at the block centre
     *  — solved directly per direction rather than composed from a general-purpose formula,
     *  since there are only six of them and each is a single 90-or-180-degree turn. */
    private static Quaternionf awayRotation(Direction away) {
        return switch (away) {
            case UP -> new Quaternionf();
            case DOWN -> new Quaternionf().rotationX((float) Math.PI);
            case NORTH -> new Quaternionf().rotationX((float) -Math.PI / 2f);
            case SOUTH -> new Quaternionf().rotationX((float) Math.PI / 2f);
            case EAST -> new Quaternionf().rotationZ((float) -Math.PI / 2f);
            case WEST -> new Quaternionf().rotationZ((float) Math.PI / 2f);
        };
    }
}
