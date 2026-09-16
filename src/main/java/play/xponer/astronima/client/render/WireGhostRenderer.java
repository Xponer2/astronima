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
import play.xponer.astronima.item.WireCoil;
import play.xponer.astronima.item.WireCoilItem;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.sim.wire.WireRouter;
import play.xponer.astronima.wire.WireTrace;

import java.util.List;
import java.util.Optional;

/**
 * The ghost: what the run <em>would</em> be, drawn before it is.
 *
 * <p>Two things, and the first matters as much as the second.
 *
 * <p><strong>The pixel under the crosshair is marked.</strong> A face is a 16×16 grid and a
 * one-pixel trace is a small target; without a mark, which lane you got would be a lottery
 * decided by sub-degree aim. With one, choosing the lane is a decision the player makes on
 * purpose — which is the whole point of routing to the pixel rather than to the block.
 *
 * <p><strong>And the route itself is drawn while you move.</strong> Holding the end of a run,
 * the trace to wherever you are pointing appears in dimmed insulation colour, following the same
 * pathfinder the server will re-run when you commit — so what you see is what you get, and a
 * straight-mode run that <em>cannot</em> get there simply shows nothing rather than laying
 * something surprising.
 *
 * <p>Recomputed each frame rather than cached: a route is cheap next to the fact that the player
 * is moving the target continuously, and a cache keyed on a moving crosshair would spend more
 * effort staying correct than the search costs.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class WireGhostRenderer {

    /** How far a marker or ghost is worth drawing, in blocks. */
    private static final double REACH = 6.0;

    private static ModelPart pixel;

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ItemStack held = heldCoil(player);
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

        WireCoil coil = WireCoilItem.settingOf(held);
        WirePixel target = WireCoilItem.pixelAt(minecraft.level, hit);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        // The route first, so the crosshair marker sits on top of its own far end.
        WireCoil.Anchor anchor = WireCoilItem.anchorOf(held);
        if (anchor != null) {
            Optional<List<WirePixel>> route = WireRouter.route(WireCoilItem.pixelOf(anchor),
                    target, WireCoilItem.spaceIn(minecraft.level, coil.colour()), coil.mode(),
                    WireCoilItem.MAX_LEG_PIXELS);
            route.ifPresent(pixels -> {
                int ghost = dim(coil.colour().getTextureDiffuseColor());
                for (WirePixel point : pixels) {
                    submit(collector, poseStack, camera, point, coil, ghost);
                }
                // Anything of this colour the leg would end up joined to, lit amber. Two
                // conductors touching are one circuit — the physical answer — and the fault was
                // never the rule but that it happened invisibly, decided by the router rather
                // than by the player. Now it is a thing you see before you click.
                for (WirePixel joined : play.xponer.astronima.wire.Wires.wouldJoin(
                        minecraft.level, pixels, coil.colour(), JOIN_LIMIT)) {
                    submit(collector, poseStack, camera, joined, coil, WOULD_JOIN);
                }
            });
        }

        // Loose ends nearby, so a run cut by a broken block can be found and picked up again.
        for (WirePixel end : WireLooseEnds.near(minecraft.level, player.blockPosition(),
                coil.colour())) {
            submit(collector, poseStack, camera, end, coil, LOOSE_END);
        }

        // The end in your hand. Without it the only sign a run was in progress was the ghost
        // itself, which disappears the moment you look somewhere it cannot route to — so a
        // player who turned around could not tell whether they were still holding anything.
        if (anchor != null) {
            submit(collector, poseStack, camera, WireCoilItem.pixelOf(anchor), coil, HELD_END);
        }

        // The marker: the pixel a click would use, in white so it reads against any insulation.
        submit(collector, poseStack, camera, target, coil, 0xFFFFFFFF);
    }

    /**
     * The colour of a joinable end.
     *
     * <p>Amber rather than the insulation's own colour, and deliberately not white: white is the
     * crosshair marker, and an end you can join to is a different kind of thing from the pixel
     * you are about to use.
     */
    private static final int LOOSE_END = 0xFFFFC24A;

    /** The end currently in the player's hand — green, because it is theirs rather than found. */
    private static final int HELD_END = 0xFF74E88C;

    /**
     * Existing conductor this leg would merge with.
     *
     * <p>Deliberately the alarming end of the palette. Merging two circuits by accident is the
     * one mistake in this system with no symptom at all: everything still lights, and the two
     * things you thought were separate now switch together.
     */
    private static final int WOULD_JOIN = 0xFFFF7A3D;

    /** How many joined pixels are worth drawing before the point is made. */
    private static final int JOIN_LIMIT = 192;

    private static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera,
                               WirePixel point, WireCoil coil, int colour) {
        // Flat on the surface, like everything else: a proposal must show where the wire will
        // actually sit. Its crossings are not known until it is laid, so it does not pretend to
        // hop — TraceElevation gives it the bridge the moment it becomes real.
        double[] centre = WireTrace.centreOf(point, WireModels.STANDOFF / 16.0);
        poseStack.pushPose();
        poseStack.translate(point.x() + centre[0] - camera.x,
                point.y() + centre[1] - camera.y,
                point.z() + centre[2] - camera.z);
        // Slightly proud of a laid trace, so a ghost over existing wire is still visible.
        poseStack.scale(1.35f, 1.35f, 1.35f);
        collector.submitModelPart(pixel, poseStack,
                RenderTypes.entitySolid(WireModels.WIRE_TEXTURE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, colour, null);
        poseStack.popPose();
    }

    /** Half brightness, so the proposal reads as a proposal rather than as laid wire. */
    private static int dim(int rgb) {
        int r = ((rgb >> 16) & 0xFF) / 2;
        int g = ((rgb >> 8) & 0xFF) / 2;
        int b = (rgb & 0xFF) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static ItemStack heldCoil(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WireCoilItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private WireGhostRenderer() {}
}
