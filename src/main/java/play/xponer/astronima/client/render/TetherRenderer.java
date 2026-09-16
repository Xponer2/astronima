package play.xponer.astronima.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.TetherState;

/**
 * design/eva-mobility.md §3.3: the tether's own line, drawn with vanilla's own leash geometry
 * rather than hand-rolled vertex math. Confirmed against this project's own decompiled
 * {@code LeashFeatureRenderer} (rule 2): {@code EntityRenderState.LeashState} is a plain,
 * publicly-fielded, no-arg-constructible class never actually coupled to a real leashed entity
 * underneath — {@code start}/{@code end} only ever feed a difference and a per-end light sample —
 * and {@code SubmitNodeCollector#submitLeash} is a real, first-class member of the same collector
 * this mod's own {@code WireRenderer} already submits through. Nothing here computes a single
 * vertex by hand; the whole visual (the sagging curve, the shader, the lighting) is vanilla's own,
 * reused rather than reimplemented.
 *
 * <p>The eye position is the {@code partialTick = 1.0F} overload, not the plain no-arg one — the
 * only one {@code OrientationEyePositionMixin} corrects for a rolled player, and the rope's own
 * start point has to agree with wherever the camera and the crosshair already say the eye is.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class TetherRenderer {

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || player.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        TetherState tether = player.getData(ModAttachments.TETHER.get());
        if (!tether.active()) {
            return;
        }
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 anchor = new Vec3(tether.anchorX(), tether.anchorY(), tether.anchorZ());
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();

        EntityRenderState.LeashState leashState = new EntityRenderState.LeashState();
        leashState.start = start;
        leashState.end = anchor;
        leashState.offset = Vec3.ZERO;
        leashState.slack = true;
        BlockPos startPos = BlockPos.containing(start.x, start.y, start.z);
        BlockPos endPos = BlockPos.containing(anchor.x, anchor.y, anchor.z);
        leashState.startBlockLight = player.level().getBrightness(LightLayer.BLOCK, startPos);
        leashState.startSkyLight = player.level().getBrightness(LightLayer.SKY, startPos);
        leashState.endBlockLight = player.level().getBrightness(LightLayer.BLOCK, endPos);
        leashState.endSkyLight = player.level().getBrightness(LightLayer.SKY, endPos);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(start.x - camera.x, start.y - camera.y, start.z - camera.z);
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        collector.submitLeash(poseStack, leashState);
        poseStack.popPose();
    }

    private TetherRenderer() {}
}
