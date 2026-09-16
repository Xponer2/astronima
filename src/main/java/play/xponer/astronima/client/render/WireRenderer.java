package play.xponer.astronima.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.SectionPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.model.WireModels;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.wire.Contrast;
import play.xponer.astronima.sim.wire.TraceElevation;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WireChunk;
import play.xponer.astronima.wire.WirePower;
import play.xponer.astronima.wire.WireTrace;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drawing the wire layer.
 *
 * <p>Wire is not a block, so nothing in the ordinary block pipeline draws it — it lives in chunk
 * data and has to be submitted by hand. This walks the chunks around the camera and puts one
 * pixel cube on every pixel that carries conductor.
 *
 * <p><strong>Submitted through {@code SubmitCustomGeometryEvent}</strong> — the collector the
 * mod's working indicators already draw through. This mod has already shipped a whole tier of
 * gauges that were invisible because they went to {@code debugQuads}, a pipeline vanilla renders
 * nothing through, while every test on their values passed.
 *
 * <p><strong>One cube per pixel and no joining geometry.</strong> Consecutive pixels are exactly
 * one pixel apart, so a run comes out solid by construction — which deletes the whole class of
 * bug an arms-and-hubs model had, where an arm starting a hair inside its hub put two surfaces in
 * one place at every junction.
 *
 * <p>Everything lies <em>flat</em> on its surface. Height is earned, not assigned: see
 * {@link TraceElevation}.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class WireRenderer {

    /** How far out to look for wire, in chunks. Beyond this a pixel is smaller than a pixel. */
    private static final int CHUNK_RADIUS = 4;

    /** What insulation looks like once it has cooked: dark, dull and unmistakably wrong. */
    private static final int CHARRED = 0x241A16;

    private static ModelPart pixel;

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        if (pixel == null) {
            pixel = minecraft.getEntityModels().bakeLayer(WireModels.WIRE).getChild("pixel");
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        int centreX = SectionPos.blockToSectionCoord((int) Math.floor(camera.x));
        int centreZ = SectionPos.blockToSectionCoord((int) Math.floor(camera.z));

        // Whichever circuit the player is pointing at, so it can be lit as one thing.
        Set<WirePixel> highlighted = WireHighlight.current();
        // And which circuits are carrying, so the wire shows it rather than only its ends.
        WireLive live = WireLive.current(level);

        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                // Never load or generate from the renderer: asking for terrain while drawing it
                // is how a frame turns into a world-generation stall.
                ChunkAccess chunk = level.getChunk(centreX + dx, centreZ + dz,
                        ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                WireChunk wires = chunk.getData(ModAttachments.WIRES.get());
                if (wires.isEmpty()) {
                    continue;
                }
                // Grouped once per chunk rather than once per trace: T traces each asking
                // wires.allOn for their own bundle costs O(T^2) in the chunk's own trace count,
                // and this is a whole frame's worth of chunks doing that every single frame -
                // see groupedBySurface's own comment for why that mattered in practice.
                Map<WirePower.Face, List<WireTrace>> grouped = wires.groupedBySurface();
                for (WireTrace trace : wires.traces()) {
                    List<WireTrace> onThisFace = grouped.get(new WirePower.Face(trace.cell(), trace.face()));
                    draw(level, trace, onThisFace, poseStack, collector, camera, highlighted, live);
                }
            }
        }
    }

    private static void draw(ClientLevel level, WireTrace trace, List<WireTrace> onThisFace,
                             PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
                             Set<WirePixel> highlighted, WireLive live) {
        // Cooked insulation, before anything is lost. The design asked for a black-body glow
        // and the physics refuses — insulation fails at 400 K and metal does not glow until
        // about 798 K — so what is drawn is what really happens at these temperatures: the
        // jacket darkens, browns and finally chars. See sim/circuit/Scorch.
        int fresh = trace.colour().getTextureDiffuseColor() & 0xFFFFFF;
        int base = 0xFF000000 | Contrast.mix(fresh, CHARRED, trace.scorch());
        int lit = WireHighlight.brighten(base);

        for (WirePixel point : trace.pixels()) {
            double climb = TraceElevation.blocksAt(point.u(), point.v(),
                    (u, v) -> demandAt(onThisFace, trace, u, v));
            // Plus, not minus: a bigger standoff is further from the surface the trace is
            // fastened to. Subtracting sank the crossing into the floor instead of stepping it
            // over — the bug reported as "на месте пересечения пиксели опускаются".
            double standoff = WireModels.STANDOFF / 16.0 + climb;
            double[] centre = WireTrace.centreOf(point, standoff);

            poseStack.pushPose();
            // World rendering puts the origin at the camera, so every position is relative to it.
            poseStack.translate(point.x() + centre[0] - camera.x,
                    point.y() + centre[1] - camera.y,
                    point.z() + centre[2] - camera.z);
            // With a circuit lit, everything else is pushed down so the one being traced can
            // be followed through a wall of wiring — reported as "если много проводов ничего
            // не видно". Contrast does the job a see-through pass would, and does it without
            // a bespoke render pipeline nobody has looked at yet.
            int shade = highlighted.isEmpty() ? base
                    : highlighted.contains(point) ? lit : WireHighlight.fade(base);
            // Charge marching along a live line: "is this circuit carrying" answered by the wire
            // itself, instead of by walking to both ends and reasoning about it.
            // Charge marching along a live line. The pip's colour is chosen *against* the wire's
            // own — see WireHighlight.charge — because the obvious "make it brighter" is
            // invisible on a white run, which is the colour everybody wires their first circuit
            // in. A live line that is not currently under a pip is also lifted a little, so a
            // carrying run reads as carrying even between pips.
            // A crest gliding along the run rather than a row of blinking dots: the pixel is
            // blended from "live" toward the charge colour by how near the crest it is, so the
            // wake behind the front fades smoothly and the direction of travel is visible in a
            // still frame. The shape is ChargePulse's; this only paints it.
            if (live.isCarrying(point, trace.colour())) {
                shade = 0xFF000000 | Contrast.mix(lit & 0xFFFFFF,
                        WireHighlight.charge(base) & 0xFFFFFF, live.chargeAt(point));
            }

            // Wire is drawn as wire everywhere, including where it lands on a machine. It used to
            // fatten there to show a made connection, which was the right idea in the wrong
            // place: TerminalRenderer now draws every stud whether or not anything is on it, so
            // the fat pixel was a second answer to a question already answered — and it made a
            // wired machine look like it had a swelling.
            collector.submitModelPart(pixel, poseStack,
                    RenderTypes.entitySolid(WireModels.WIRE_TEXTURE),
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, shade, null);
            poseStack.popPose();
        }
    }

    /**
     * How many traces this one has to climb over at a pixel.
     *
     * <p>Ordered by insulation colour so the answer is the same for everybody: the lower-numbered
     * colour keeps the floor and the higher one goes over the top. Anything else — first laid, or
     * whatever the iteration happened to reach first — would make which trace bridges depend on
     * history, and two players looking at one crossing could see it differently (rule 19).
     */
    private static int demandAt(List<WireTrace> onThisFace, WireTrace self, int u, int v) {
        int below = 0;
        for (WireTrace other : onThisFace) {
            if (other.colour().ordinal() < self.colour().ordinal() && other.has(u, v)) {
                below++;
            }
        }
        return below;
    }

    private WireRenderer() {}
}
