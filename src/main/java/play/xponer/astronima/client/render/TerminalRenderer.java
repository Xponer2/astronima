package play.xponer.astronima.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.model.WireModels;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Terminal;
import play.xponer.astronima.wire.WireTrace;

/**
 * Studs on every block that takes a wire, whether or not one is on them yet.
 *
 * <p><strong>This is the half that was missing, and it is why the system read as a toy.</strong>
 * Connection points existed only in code: a player had no way to see where a wire was supposed to
 * go, so "wire it up" meant waving a coil at a machine until something happened. A terminal you
 * can see is a terminal you can aim at — and, just as importantly, one you can fail to hit and
 * notice you failed.
 *
 * <p>Colour is the kind, so what a stud is for reads at a glance without a tooltip: <strong>brass
 * for power, blue for an input, amber for an output</strong>. That also makes a mis-wired gate
 * visible from across the room — a wire on a blue stud goes in, a wire on an amber one comes out,
 * and two amber studs joined by one line is an argument between two outputs.
 *
 * <p>Drawn slightly proud of the block so a stud on a surface that also carries wire is still
 * distinguishable from the wire crossing it.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class TerminalRenderer {

    /** Bare brass: a power connection. */
    private static final int POWER = 0xFFE0C27A;
    /** Cold blue: something listens here. */
    private static final int SIGNAL_IN = 0xFF6FA8DC;
    /** Warm amber: something drives here. */
    private static final int SIGNAL_OUT = 0xFFFFB347;

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
        // Only while the player is actually wiring: studs on every machine all the time is a
        // readout nobody asked for, and it made a base look like it had a rash.
        if (!TerminalScan.wanted(minecraft)) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        for (TerminalScan.Found found : TerminalScan.around(level, BlockPos.containing(camera))) {
            draw(poseStack, collector, camera, found.block(), found.terminal());
        }
    }

    private static void draw(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
                             BlockPos block, Terminal terminal) {
        WirePixel at = terminal.pixel(block);
        // Sitting a little further off the surface than wire does, so a stud under a trace is
        // still visible as a stud.
        double[] centre = WireTrace.centreOf(at, WireModels.STANDOFF / 16.0 - 0.5 / 16.0);
        poseStack.pushPose();
        poseStack.translate(at.x() + centre[0] - camera.x, at.y() + centre[1] - camera.y,
                at.z() + centre[2] - camera.z);
        poseStack.scale(2.4f, 2.4f, 2.4f);
        collector.submitModelPart(pixel, poseStack,
                RenderTypes.entitySolid(WireModels.WIRE_TEXTURE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
                colourOf(terminal), null);
        poseStack.popPose();
    }

    private static int colourOf(Terminal terminal) {
        if (terminal.kind() == Terminal.Kind.POWER) {
            return POWER;
        }
        String label = terminal.label() == null ? "" : terminal.label().toLowerCase(java.util.Locale.ROOT);
        return switch (label) {
            case "clk" -> 0xFF00FFCC;    // Bright Cyan for Clock
            case "data" -> 0xFFFF9900;   // Bright Orange for Data
            case "x0", "x1" -> 0xFFD000FF;// Purple/Magenta for X address
            case "y0", "y1" -> 0xFFFFD700;// Gold/Yellow for Y address
            case "clr" -> 0xFFFF3333;    // Crimson Red for Clear
            case "mode" -> 0xFF3388FF;   // Azure Blue for Mode
            default -> terminal.kind() == Terminal.Kind.SIGNAL_IN ? SIGNAL_IN : SIGNAL_OUT;
        };
    }

    private TerminalRenderer() {}
}
