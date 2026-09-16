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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.block.GasValveBlock;
import play.xponer.astronima.block.entity.GasValveBlockEntity;
import play.xponer.astronima.block.entity.SolarRetortBlockEntity;
import play.xponer.astronima.client.model.PlumbingModels;

/**
 * The moving parts and light on the plumbing that the menu and the crosshair readout cannot
 * show — a control that has to be seen turning, or a vessel that is genuinely a light source.
 *
 * <p><strong>The world-space gauge this class used to also draw is gone.</strong> Every
 * machine's detailed reading now lives at the crosshair ({@code client.LookAtReadout},
 * screen-space, per design/indicators.md §6) — the same size at four metres and at fourteen,
 * which a panel hovering over a one-metre block could never be. Six machines
 * (tank, pump, valve, airlock, condenser, scrubber) had a block-entity renderer here for no
 * other reason than to draw that panel; once the panel was redundant with the crosshair
 * readout, so were they, and they are deleted rather than kept as decoration nothing needed.
 * What remains is real 3D geometry — the valve's handwheel, the retort's own glow — that a
 * flat screen-space panel cannot substitute for.
 */
public final class PlumbingIndicatorRenderers {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    /** Beyond this, a moving part or a light this small is not worth extracting or drawing. */
    private static final double PART_VIEW_RANGE = 16.0;

    /**
     * The retort's own light — real colour and rate from the same {@link BlackBody} the
     * particle emitter draws from (design/vfx-craft.md), not a second, separate readout.
     *
     * <p>Used to bake a vanilla billboard mesh (camera-facing crossed quads) here. Removed:
     * reported from play as reading like "a flat square always turned to face the camera" — true
     * of any billboard viewed close to edge-on, which is exactly the failure mode a flat quad
     * cannot avoid. {@link RetortPhotonGlow} replaces it with a genuine 3D sphere (Photon,
     * {@code Model} render mode), which has no wrong angle to be seen from and so needs no
     * billboard trick at all. This class now only extracts the render state both
     * {@link RetortPhotonGlow} and {@link RetortPhotonSparks} are driven from.
     */
    public static class Retort
            implements BlockEntityRenderer<SolarRetortBlockEntity, Retort.RetortState> {

        public static class RetortState extends BlockEntityRenderState {
            public SolarRetortBlockEntity.@Nullable Glow glow;
            public BlockPos pos = BlockPos.ZERO;
            public @Nullable Level level;
        }

        public Retort(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public RetortState createRenderState() {
            return new RetortState();
        }

        @Override
        public void extractRenderState(SolarRetortBlockEntity blockEntity, RetortState state,
                                       float partialTicks, Vec3 cameraPosition,
                                       ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
            double distanceSq = Vec3.atCenterOf(blockEntity.getBlockPos())
                    .distanceToSqr(cameraPosition);
            state.glow = distanceSq < PART_VIEW_RANGE * PART_VIEW_RANGE ? blockEntity.glow() : null;
            state.pos = blockEntity.getBlockPos();
            state.level = blockEntity.getLevel();
        }

        @Override
        public void submit(RetortState state, PoseStack poseStack,
                           SubmitNodeCollector collector, CameraRenderState camera) {
            if (state.level != null) {
                RetortPhotonGlow.update(state.level, state.pos, state.glow);
                RetortPhotonSparks.update(state.level, state.pos, state.glow);
            }
        }

        @Override
        public int getViewDistance() {
            return (int) PART_VIEW_RANGE;
        }
    }

    // ---------------------------------------------------------------- valve wheel

    /** Carries the interpolated wheel angle and the valve's axis. */
    public static class WheelState extends BlockEntityRenderState {
        public double angleDegrees;
        public boolean visible;
        public Direction.Axis axis = Direction.Axis.Z;
    }

    /**
     * The valve's handwheel, turning to the setting.
     *
     * <p>Replaced five static textures that drew it at five fixed poses — a handwheel is a
     * continuous control, and five pictures of one both misstate the object and cannot show it
     * responding when you turn it. How far open the valve reads now comes from the crosshair
     * ({@code LookAtReadout}), not from a second gauge bolted to this renderer.
     */
    public static class Valve implements BlockEntityRenderer<GasValveBlockEntity, WheelState> {
        private final ModelPart wheel;

        public Valve(BlockEntityRendererProvider.Context context) {
            this.wheel = context.bakeLayer(PlumbingModels.VALVE_WHEEL);
        }

        @Override
        public WheelState createRenderState() {
            return new WheelState();
        }

        @Override
        public void extractRenderState(GasValveBlockEntity valve, WheelState state,
                                       float partialTicks, Vec3 cameraPosition,
                                       ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderState.extractBase(valve, state, breakProgress);
            double distanceSq = Vec3.atCenterOf(valve.getBlockPos()).distanceToSqr(cameraPosition);
            state.visible = distanceSq < PART_VIEW_RANGE * PART_VIEW_RANGE;
            if (state.visible) {
                state.angleDegrees = valve.wheelAngle(partialTicks);
                state.axis = valve.getBlockState().getValue(GasValveBlock.AXIS);
            }
        }

        @Override
        public void submit(WheelState state, PoseStack poseStack,
                           SubmitNodeCollector collector, CameraRenderState camera) {
            if (!state.visible) {
                return;
            }
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            // The bonnet sits perpendicular to the flow: up for a horizontal valve, out to
            // the side for a vertical one — so the wheel follows the axis you turned it to,
            // instead of always capping the top.
            if (state.axis == Direction.Axis.Y) {
                poseStack.mulPose(new Quaternionf().rotationZ((float) (-Math.PI / 2)));
            }
            poseStack.mulPose(new Quaternionf().rotationY((float) state.angleDegrees * DEG_TO_RAD));
            collector.submitModelPart(wheel, poseStack,
                    RenderTypes.entitySolid(PlumbingModels.STEEL_TEXTURE),
                    state.lightCoords, OverlayTexture.NO_OVERLAY, null);
            poseStack.popPose();
        }

        @Override
        public int getViewDistance() {
            return (int) PART_VIEW_RANGE;
        }
    }

    private PlumbingIndicatorRenderers() {}
}
