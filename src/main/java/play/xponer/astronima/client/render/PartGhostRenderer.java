package play.xponer.astronima.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.model.WireModels;
import play.xponer.astronima.item.LogicPartItem;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.PartPlacement;
import play.xponer.astronima.wire.WirePart;
import play.xponer.astronima.wire.WireTrace;

/**
 * Where the part in your hand would go, drawn before it goes there.
 *
 * <p><strong>Missing from the first build, and reported as missing</strong> — <em>"нету
 * предпросмотра для мелкой логики"</em>. Wire has had a ghost since the coil shipped, for a
 * reason that applies twice over to a part: a five-pixel component on a sixteen-pixel face has a
 * corner, a footprint <em>and</em> a rotation, and all three are decided by where you are standing
 * and which way you are looking. Committing first and finding out afterwards is the tool being
 * hostile.
 *
 * <p><strong>A refused placement is drawn red rather than hidden.</strong> A ghost that simply
 * vanishes when the part will not fit tells the player nothing about why — and "why" here is
 * almost always a specific pixel: another part, a trace under the housing, or the edge of the
 * face. Showing the footprint where it would have gone is what makes that visible.
 *
 * <p>Pads are drawn in their own colours even in the ghost, because <em>which way round it lands</em>
 * is the decision being previewed. A grey rectangle would preview the size and hide the point.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class PartGhostRenderer {

    /** How far a proposal is worth drawing, in blocks — the same reach the coil's ghost uses. */
    private static final double REACH = 6.0;

    /** It fits: the same green the coil marks the end it is holding with. */
    private static final int WILL_FIT = 0xFF74E88C;

    /** It does not: and the shape is still drawn, so the obstruction is visible. */
    private static final int BLOCKED = 0xFFE2564A;

    private static final int PAD_IN = 0xFF6FA8DC;
    private static final int PAD_OUT = 0xFFFFC24A;

    private static ModelPart pixel;

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ItemStack held = heldPart(player);
        if (held.isEmpty()) {
            return;
        }
        if (!(player.pick(REACH, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false),
                false) instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (pixel == null) {
            pixel = minecraft.getEntityModels().bakeLayer(WireModels.WIRE).getChild("pixel");
        }

        PartType type = ((LogicPartItem) held.getItem()).type();
        // The same call the click makes. A preview that disagreed with the click would be worse
        // than none: the player aims by the picture and gets something else (rule 20).
        WirePart proposed = PartPlacement.proposed(player, hit, type,
                LogicPartItem.circuitOf(held), LogicPartItem.rotationOf(held));
        boolean fits = PartPlacement.allowed(minecraft.level, proposed);

        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        // A slow breath, so a ghost is never mistaken for a part that is already there. It is
        // the cheapest possible signal for "this has not happened yet" and it costs one multiply.
        float pulse = 0.72f + 0.28f * (float) Math.sin(
                System.currentTimeMillis() / 260.0);
        for (int[] at : proposed.body()) {
            PartType.Pad pad = proposed.padAt(at[0], at[1]);
            int colour = !fits ? BLOCKED
                    : pad == null ? WILL_FIT : pad.drives() ? PAD_OUT : PAD_IN;
            submit(collector, poseStack, camera, proposed, at[0], at[1], scale(colour, pulse));
        }
    }

    private static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera,
                               WirePart part, int u, int v, int colour) {
        WirePixel at = new WirePixel(part.cell().getX(), part.cell().getY(), part.cell().getZ(),
                Faces.of(part.face()), u, v);
        // Standing off a little further than a laid part, so a proposal over an existing one is
        // still readable — which is exactly the case where it is refused and needs to be seen.
        double[] centre = WireTrace.centreOf(at, (WireModels.STANDOFF + 1.1) / 16.0);
        poseStack.pushPose();
        poseStack.translate(at.x() + centre[0] - camera.x, at.y() + centre[1] - camera.y,
                at.z() + centre[2] - camera.z);
        poseStack.scale(0.85f, 0.85f, 0.85f);
        collector.submitModelPart(pixel, poseStack,
                RenderTypes.entitySolid(WireModels.WIRE_TEXTURE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, colour, null);
        poseStack.popPose();
    }

    /** Dims a colour without touching its hue, so the breath does not change what it says. */
    private static int scale(int argb, float factor) {
        int r = Math.clamp(Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        int g = Math.clamp(Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        int b = Math.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static ItemStack heldPart(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof LogicPartItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private PartGhostRenderer() {}
}
