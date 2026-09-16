package play.xponer.astronima.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Vector3fc;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.ProgradeMarker;

/**
 * design/eva-mobility.md §3.5: where you are looking and where you are actually going are
 * different facts the instant you are drifting rather than freshly pushed — a real
 * prograde/retrograde marker, projected onto the view the way a real spacecraft's own indicator
 * is, not merely inferred from the crosshair. {@code BreathHud}'s own closure-rate row (M2 in
 * {@code microgravity.md}) already answers "how fast"; this answers "which way."
 *
 * <p>The camera-local direction {@link ProgradeMarker#project} needs is three dot products
 * against this frame's own real, already-free {@link Camera#forwardVector()}/{@code leftVector}/
 * {@code upVector} — the exact vectors the render itself used, so the marker is never one frame
 * behind or computed from a different fact than what is actually on screen.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class ProgradeMarkerHud {

    /** Below this fraction of {@link Microgravity#FREE_SPEED}, direction is numerically
     *  unstable and practically meaningless — the same shape {@code BreathHud}'s own closure-rate
     *  row already uses to hide itself at rest. */
    private static final float MIN_SPEED_FRACTION = 0.15f;

    private static final int PROGRADE_SIZE = 6;
    private static final int RETROGRADE_SIZE = 6;
    private static final int PROGRADE_COLOR = 0xFF4AA8E8;
    private static final int RETROGRADE_COLOR = 0xFFE8C34A;

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(Astronima.MODID, "prograde_marker"),
                ProgradeMarkerHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        if (player.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        if (Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(player))) {
            return;
        }
        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.length();
        if (speed < Microgravity.FREE_SPEED * MIN_SPEED_FRACTION) {
            return;
        }
        Vec3 direction = velocity.scale(1.0 / speed);

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vector3fc forwardVec = camera.forwardVector();
        Vector3fc leftVec = camera.leftVector();
        Vector3fc upVec = camera.upVector();
        double forward = dot(direction, forwardVec);
        double right = -dot(direction, leftVec);
        double up = dot(direction, upVec);

        float screenWidth = graphics.guiWidth();
        float screenHeight = graphics.guiHeight();
        float fovRadians = (float) Math.toRadians(minecraft.options.fov().get());

        ProgradeMarker.ScreenPosition prograde =
                ProgradeMarker.project(right, up, forward, screenWidth, screenHeight, fovRadians);
        ProgradeMarker.ScreenPosition retrograde = ProgradeMarker.project(
                -right, -up, -forward, screenWidth, screenHeight, fovRadians);

        // The two markers roam the whole screen rather than sitting in a fixed strip, so what
        // gets reserved is their own actual bounding box this frame, not a fixed guess - the
        // same "holds only the space it is using, only while it is showing" rule BreathHud's own
        // reserve call already follows.
        int minX = Math.round(Math.min(prograde.x() - PROGRADE_SIZE, retrograde.x() - RETROGRADE_SIZE));
        int minY = Math.round(Math.min(prograde.y() - PROGRADE_SIZE, retrograde.y() - RETROGRADE_SIZE));
        int maxX = Math.round(Math.max(prograde.x() + PROGRADE_SIZE, retrograde.x() + RETROGRADE_SIZE));
        int maxY = Math.round(Math.max(prograde.y() + PROGRADE_SIZE, retrograde.y() + RETROGRADE_SIZE));
        play.xponer.astronima.client.hud.HudPanels.reserve("prograde_marker", minX, minY,
                maxX - minX, maxY - minY, System.currentTimeMillis() / 1000.0);

        drawDiamond(graphics, prograde, PROGRADE_SIZE, PROGRADE_COLOR);
        drawRing(graphics, retrograde, RETROGRADE_SIZE, RETROGRADE_COLOR);
    }

    private static double dot(Vec3 v, Vector3fc axis) {
        return v.x * axis.x() + v.y * axis.y() + v.z * axis.z();
    }

    /** Prograde: a small filled diamond, the same shape real spacecraft attitude indicators use
     *  for "this way". Dimmed when edge-clamped (behind or outside the field of view) so an
     *  on-screen marker always reads as more certain than an inferred one. */
    private static void drawDiamond(GuiGraphicsExtractor graphics,
                                     ProgradeMarker.ScreenPosition pos, int half, int color) {
        int x = Math.round(pos.x());
        int y = Math.round(pos.y());
        int c = pos.onScreen() ? color : dim(color);
        for (int i = -half; i <= half; i++) {
            int w = half - Math.abs(i);
            graphics.fill(x - w, y + i, x + w + 1, y + i + 1, c);
        }
    }

    /** Retrograde: a hollow square, deliberately not the same shape as prograde so the two are
     *  never confused at a glance. */
    private static void drawRing(GuiGraphicsExtractor graphics,
                                  ProgradeMarker.ScreenPosition pos, int half, int color) {
        int x = Math.round(pos.x());
        int y = Math.round(pos.y());
        int c = pos.onScreen() ? color : dim(color);
        graphics.fill(x - half, y - half, x + half + 1, y - half + 1, c);
        graphics.fill(x - half, y + half, x + half + 1, y + half + 1, c);
        graphics.fill(x - half, y - half, x - half + 1, y + half + 1, c);
        graphics.fill(x + half, y - half, x + half + 1, y + half + 1, c);
    }

    private static int dim(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int dimmedAlpha = a * 3 / 5;
        return (dimmedAlpha << 24) | (argb & 0x00FFFFFF);
    }

    private ProgradeMarkerHud() {}
}
