package play.xponer.astronima.sim.gravity;

/**
 * design/eva-mobility.md §3.5: where you are looking and where you are actually going are
 * different facts the instant you are drifting rather than freshly pushed —
 * {@code Microgravity}'s own closure-rate instrument (M2 in {@code microgravity.md}) already
 * answers "how fast," never "which way." This answers "which way": a real velocity-vector
 * marker, projected onto the view the same way a real spacecraft's own prograde/retrograde
 * indicator is, not merely inferred from the crosshair.
 *
 * <p>Minecraft-free (rule 1): the caller resolves the player's velocity into the camera's own
 * local frame (three dot products against {@code Camera#forwardVector}/{@code leftVector}/
 * {@code upVector} — Minecraft types this class never needs to see), and this does the
 * projection arithmetic and the off-screen convention.
 */
public final class ProgradeMarker {

    /** Where a marker draws, and whether that is a genuine point in view or an edge-clamped
     *  indicator of an off-screen (or behind-camera) direction. */
    public record ScreenPosition(float x, float y, boolean onScreen) {}

    /** How close to the screen edge an edge-clamped marker sits, as a fraction of each half-
     *  dimension — never flush with the true edge, so the marker's own icon never clips. */
    private static final float EDGE_FRACTION = 0.42f;

    /**
     * Projects a world direction, already resolved into the camera's own local
     * {@code (right, up, forward)} components, onto the screen.
     *
     * <p>Inside the field of view ({@code forward} positive and the perspective-projected point
     * within bounds): the exact point, {@code onScreen} true — a real spacecraft's prograde
     * marker sitting directly over where the ship is actually headed. Outside it (behind the
     * camera, or beyond the edge of the view): clamped to near the screen's own edge along the
     * direction of {@code (right, up)} alone — a deliberately simple, stable convention (KSP's
     * own navball marker takes the same shape) rather than a literal perspective projection that
     * would otherwise flip through infinity right at the horizon of {@code forward = 0}.
     */
    public static ScreenPosition project(double right, double up, double forward,
                                          float screenWidth, float screenHeight,
                                          float fovRadians) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        if (forward > 1.0e-4) {
            float projScale = (screenHeight / 2f) / (float) Math.tan(fovRadians / 2f);
            float x = centerX + (float) (right / forward) * projScale;
            float y = centerY - (float) (up / forward) * projScale;
            if (x >= 0f && x <= screenWidth && y >= 0f && y <= screenHeight) {
                return new ScreenPosition(x, y, true);
            }
        }
        double lengthRightUp = Math.sqrt(right * right + up * up);
        if (lengthRightUp < 1.0e-6) {
            // No lateral component at all (moving exactly along the camera's own forward/back
            // axis) - never actually reachable in practice, since any real drift has some
            // lateral component the instant the camera is not perfectly aligned, but still a
            // real input this function must answer rather than divide by zero on.
            return new ScreenPosition(centerX, screenHeight * (0.5f - EDGE_FRACTION), false);
        }
        float edgeRadiusX = screenWidth * EDGE_FRACTION;
        float edgeRadiusY = screenHeight * EDGE_FRACTION;
        float x = centerX + (float) (right / lengthRightUp) * edgeRadiusX;
        float y = centerY - (float) (up / lengthRightUp) * edgeRadiusY;
        return new ScreenPosition(x, y, false);
    }

    private ProgradeMarker() {}
}
