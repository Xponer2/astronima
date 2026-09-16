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
import net.minecraft.world.item.DyeColor;
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
import play.xponer.astronima.item.WireCoil;
import play.xponer.astronima.item.WireRibbon;
import play.xponer.astronima.item.WireRibbonItem;
import play.xponer.astronima.sim.wire.RibbonGeometry;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.sim.wire.WireRouter;
import play.xponer.astronima.wire.WireAim;
import play.xponer.astronima.wire.WireTrace;
import play.xponer.astronima.wire.Wires;

import java.util.List;
import java.util.Optional;

/**
 * The ribbon's own ghost: every lane {@link RibbonGeometry#laneRoutes} would derive, each in its
 * own fixed colour, exactly like {@link WireGhostRenderer} draws one. A tool that lands N lanes at
 * once and shows nothing beforehand would be the one wire-layer tool this mod's own rule against —
 * {@code wire-parts.md}: <em>"a ghost that vanishes tells you nothing about why"</em> — does not
 * cover, since a ribbon can refuse for reasons a single trace never could (no room to fan out, a
 * misaligned landing) and those are exactly the moments a player most needs to see what stopped it.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class WireRibbonGhostRenderer {

    private static final double REACH = 6.0;

    /** A lane that cannot be derived at all right now — no room, or the target pad row refuses. */
    private static final int REFUSED = 0xFFFF7A3D;

    private static ModelPart pixel;

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ItemStack held = heldRibbon(player);
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

        WireRibbon setting = WireRibbonItem.settingOf(held);
        WireAim.Aim aim = WireAim.at(minecraft.level, hit, false);
        WirePixel target = aim.pixel();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        WireCoil.Anchor anchor = WireRibbonItem.anchorOf(held);
        if (anchor != null) {
            WirePixel from = new WirePixel(anchor.cell().getX(), anchor.cell().getY(),
                    anchor.cell().getZ(), play.xponer.astronima.wire.Faces.of(anchor.face()),
                    anchor.u(), anchor.v());
            drawLeg(minecraft, collector, poseStack, camera, from, target, setting);
            submit(collector, poseStack, camera, from, HELD_END);
        }

        submit(collector, poseStack, camera, target, 0xFFFFFFFF);
    }

    private static void drawLeg(Minecraft minecraft, SubmitNodeCollector collector, PoseStack poseStack,
                                Vec3 camera, WirePixel from, WirePixel to, WireRibbon setting) {
        if (from.equals(to)) {
            return;
        }
        Optional<List<WirePixel>> lane0 = WireRouter.route(from, to,
                pixel -> Wires.canPlace(minecraft.level, pixel), setting.mode(), MAX_LEG_PIXELS);
        if (lane0.isEmpty()) {
            return;
        }
        Optional<List<List<WirePixel>>> lanes = RibbonGeometry.laneRoutes(lane0.get(), setting.width());
        if (lanes.isEmpty()) {
            // Say where it stopped: lane 0 itself is real, so show that much dimmed, and mark its
            // far end in the refusal colour rather than drawing nothing at all.
            for (WirePixel point : lane0.get()) {
                submit(collector, poseStack, camera, point, dim(REFUSED));
            }
            return;
        }
        for (int lane = 0; lane < lanes.get().size(); lane++) {
            DyeColor colour = WireRibbonItem.LANE_COLOURS[lane];
            for (WirePixel point : lanes.get().get(lane)) {
                submit(collector, poseStack, camera, point, dim(colour.getTextureDiffuseColor()));
            }
        }
    }

    private static final int MAX_LEG_PIXELS = 2_048;

    private static final int HELD_END = 0xFF74E88C;

    private static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera,
                               WirePixel point, int colour) {
        double[] centre = WireTrace.centreOf(point, WireModels.STANDOFF / 16.0);
        poseStack.pushPose();
        poseStack.translate(point.x() + centre[0] - camera.x, point.y() + centre[1] - camera.y,
                point.z() + centre[2] - camera.z);
        poseStack.scale(1.35f, 1.35f, 1.35f);
        collector.submitModelPart(pixel, poseStack,
                RenderTypes.entitySolid(WireModels.WIRE_TEXTURE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, colour, null);
        poseStack.popPose();
    }

    private static int dim(int rgb) {
        int r = ((rgb >> 16) & 0xFF) / 2;
        int g = ((rgb >> 8) & 0xFF) / 2;
        int b = (rgb & 0xFF) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static ItemStack heldRibbon(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WireRibbonItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private WireRibbonGhostRenderer() {}
}
