package play.xponer.astronima.sim.optics;

/**
 * Pure-math mirror of {@code AsteroidSkyRenderer#skyRotation}: rotates a fixed sky-space
 * direction by the asteroid's own spin, about the same tilted pole, with no dependency on a
 * matrix library, a GPU, or Minecraft (rule 1) — so anything that needs "where is this fixed sky
 * object right now", the spectrograph's targeting math included, can ask without a render
 * context. The renderer keeps its own JOML-based rotation (proven, unchanged); this is a second,
 * independent expression of the identical formula, cross-checked against JOML's own output in
 * {@code SkyRotationTest} so the two can never quietly drift apart.
 */
public final class SkyRotation {

    /** Must match {@code AsteroidSkyRenderer.POLE_TILT_DEGREES}. */
    public static final double POLE_TILT_DEGREES = 35.0;

    /** Must match {@code AsteroidSkyRenderer.ROTATION_PERIOD_TICKS}. */
    public static final double ROTATION_PERIOD_TICKS = 12000.0;

    public record Vec3(double x, double y, double z) {
        public Vec3 normalize() {
            double len = Math.sqrt(x * x + y * y + z * z);
            return new Vec3(x / len, y / len, z / len);
        }

        public double dot(Vec3 other) {
            return x * other.x + y * other.y + z * other.z;
        }
    }

    /** The same tilted spin axis the renderer builds — a real small body does not obligingly
     * rotate about whatever axis a player calls "up". */
    public static Vec3 poleAxis() {
        double tilt = Math.toRadians(POLE_TILT_DEGREES);
        return new Vec3(Math.sin(tilt), Math.cos(tilt), 0.0);
    }

    /** Rodrigues' rotation formula: {@code v} rotated by {@code angleRadians} about the unit
     * axis {@code axis} — the same operation {@code Matrix4f#rotate} performs, written out with
     * no matrix type at all. */
    public static Vec3 rotate(Vec3 v, Vec3 axis, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        double dot = v.dot(axis);
        double crossX = axis.y() * v.z() - axis.z() * v.y();
        double crossY = axis.z() * v.x() - axis.x() * v.z();
        double crossZ = axis.x() * v.y() - axis.y() * v.x();
        return new Vec3(
                v.x() * cos + crossX * sin + axis.x() * dot * (1.0 - cos),
                v.y() * cos + crossY * sin + axis.y() * dot * (1.0 - cos),
                v.z() * cos + crossZ * sin + axis.z() * dot * (1.0 - cos));
    }

    /** Where a fixed sky-space direction currently points after {@code gameTimeTicks} of the
     * asteroid's own spin — the one formula the renderer's marker and the spectrograph's
     * targeting both resolve to, so "what is drawn" and "what can be pointed at" cannot disagree
     * without this test going red first. */
    public static Vec3 currentDirection(Vec3 fixedDirection, long gameTimeTicks) {
        double spinAngle = gameTimeTicks / ROTATION_PERIOD_TICKS * Math.PI * 2.0;
        return rotate(fixedDirection, poleAxis(), spinAngle);
    }

    /** The angle, in degrees, between two directions — how far off-target a look direction is. */
    public static double angleBetweenDegrees(Vec3 a, Vec3 b) {
        Vec3 an = a.normalize();
        Vec3 bn = b.normalize();
        double dot = Math.clamp(an.dot(bn), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private SkyRotation() {}
}
