package play.xponer.astronima.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import play.xponer.astronima.item.WireCoilItem;
import play.xponer.astronima.item.WireSnipsItem;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WireAim;
import play.xponer.astronima.wire.WireTrace;
import play.xponer.astronima.wire.Wires;

import java.util.List;
import java.util.Optional;

/**
 * The snips' own ghost: what a click would cut, drawn before it does.
 *
 * <p>The reason this exists as a separate class rather than a branch inside
 * {@link WireGhostRenderer}: the two tools answer different questions. The coil's ghost previews
 * a route through <em>open space</em> — a proposal the router just invented. The snips' ghost
 * previews a route through <em>wire that already exists</em> — {@link Wires#pathBetween} finding
 * the one path the network itself already has, which is a fact about the world rather than a
 * proposal about to become one. Sharing a class would mean one method juggling "search obstacles"
 * and "search a network" behind the same `if`, which is exactly the kind of merge rule 20 warns
 * against — two things one flag apart are still two things.
 *
 * <p><strong>The one warning drawn before the click, not after it.</strong> When the marked start
 * and the crosshair sit on a loop, cutting the shortest path between them would not actually
 * separate anything — {@link Wires#wouldStillBeJoinedAfter} is exactly this check, run here so
 * the ghost turns the warning colour before the click lands rather than after the server refuses
 * it. A player who never sees the amber line has no reason to know the check exists at all.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class WireSnipsGhostRenderer {

    private static final double REACH = 6.0;

    /** The path a click would cut, dimmed insulation colour — the coil ghost's own convention. */
    private static int ghost(DyeColor colour) {
        int rgb = colour.getTextureDiffuseColor();
        int r = ((rgb >> 16) & 0xFF) / 2;
        int g = ((rgb >> 8) & 0xFF) / 2;
        int b = (rgb & 0xFF) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** A cut that would not actually separate anything — the one case worth a warning colour. */
    private static final int WOULD_BYPASS = 0xFFFF7A3D;

    /** The marked start, waiting for a second click. */
    private static final int MARKED_START = 0xFF74E88C;

    private static ModelPart pixel;

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ItemStack held = heldSnips(player);
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

        WirePixel target = WireAim.at(minecraft.level, hit, true).pixel();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        WireCoil.Anchor anchor = held.get(ModDataComponents.SNIPS_ANCHOR.get());
        if (anchor != null) {
            WirePixel start = WireCoilItem.pixelOf(anchor);
            Optional<WireTrace> startTrace = traceAt(minecraft.level, start);
            startTrace.ifPresent(trace -> {
                List<WirePixel> path = Wires.pathBetween(minecraft.level, start, target,
                        trace.colour(), Wires.DEFAULT_LIMIT);
                if (!path.isEmpty()) {
                    boolean bypassed = Wires.wouldStillBeJoinedAfter(minecraft.level, path,
                            trace.colour(), Wires.DEFAULT_LIMIT);
                    int colour = bypassed ? WOULD_BYPASS : ghost(trace.colour());
                    for (WirePixel point : path) {
                        submit(collector, poseStack, camera, point, colour);
                    }
                }
            });
            submit(collector, poseStack, camera, start, MARKED_START);
        }

        // The marker: the pixel a click would use, in white so it reads against any insulation.
        submit(collector, poseStack, camera, target, 0xFFFFFFFF);
    }

    /** Whichever trace, of any colour, actually occupies this pixel — the marked start's own. */
    private static Optional<WireTrace> traceAt(net.minecraft.world.level.Level level,
                                               WirePixel target) {
        BlockPos cell = Wires.cellOf(target);
        Direction face = Faces.of(target.face());
        for (WireTrace trace : Wires.bundleOn(level, cell, face)) {
            if (trace.has(target.u(), target.v())) {
                return Optional.of(trace);
            }
        }
        return Optional.empty();
    }

    private static void submit(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera,
                               WirePixel point, int colour) {
        double[] centre = WireTrace.centreOf(point, WireModels.STANDOFF / 16.0);
        poseStack.pushPose();
        poseStack.translate(point.x() + centre[0] - camera.x,
                point.y() + centre[1] - camera.y,
                point.z() + centre[2] - camera.z);
        poseStack.scale(1.35f, 1.35f, 1.35f);
        collector.submitModelPart(pixel, poseStack,
                RenderTypes.entitySolid(WireModels.WIRE_TEXTURE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, colour, null);
        poseStack.popPose();
    }

    private static ItemStack heldSnips(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WireSnipsItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private WireSnipsGhostRenderer() {}
}
