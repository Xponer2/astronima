package play.xponer.astronima.sim.optics;

/**
 * A telescope's mount is bolted to a fixed base and cannot slew through it: a real tripod stops at
 * the horizon (never points below the ground it stands on), and most mounts also cannot swing
 * straight through their own zenith without a meridian flip this design does not model yet
 * (design/astra-telescope.md §8, open question 2). This is that clamp, written with no render
 * context so the block that owns a mount and the test that proves the mount honest read the
 * identical formula (rule 1) — the same discipline {@link SkyRotation} already keeps.
 */
public final class TelescopeMount {

    /**
     * How far from the mount's own "straight up" it may point, measured as an angle from
     * {@code baseUp}: {@code maxAngleFromUpDegrees} is the horizon stop, {@code
     * minAngleFromUpDegrees} is the zenith dead zone (0 for a mount with none).
     */
    public record Limits(double minAngleFromUpDegrees, double maxAngleFromUpDegrees) {}

    /** A small dead zone directly overhead, a hard stop at the horizon — the common tripod case.
     * The dead zone is not decoration: exactly at the zenith, yaw stops being a well-defined
     * angle at all (every yaw value points the same real direction, straight up) — real mounts
     * cannot track through it for exactly this reason, and confirmed independently the hard way
     * here: {@code TelescopeMountEntity} used to rest new mounts exactly at this singular point,
     * and a gametest turning a mount's yaw while parked there moved the *number* without moving
     * the *direction*, which is the same instability a human reported as "uncomfortable
     * sensitivity" pointing anywhere near true zenith. */
    public static final Limits GROUND_TRIPOD = new Limits(5.0, 90.0);

    /** How far above the azimuth pivot the elevation (trunnion) pivot sits, in 1/16-block "pixel"
     * units — the same number {@code models/block/telescope.json}'s now-removed elements were
     * authored against. Shared (rule 46) between {@code TelescopeModels} (draws the tube there)
     * and {@code ModEntityTypes} (the mount's own eye height, once it became the real camera
     * entity — design/astra-telescope.md §2.1's "detach the camera" fix): both need the same
     * "how far above the pivot is the eyepiece" number, and a common (non-client) home is the only
     * place both a client-only rendering class and a common registration class can both read it. */
    public static final float TRUNNION_RISE = 1.7F;

    /** Where the eyepiece's own eye point sits, in tube-local pixels relative to the trunnion
     * pivot — design/astra-telescope.md §2.3.2's star-diagonal derivation, shared (rule 46)
     * between {@code TelescopeModels} (draws the diagonal housing and barrel there) and this
     * class's own {@link #eyeOffsetFromPivot} (places the camera there): {@code EYE_LOCAL_Y} is
     * the tube's rear face, {@code EYE_LOCAL_Z} is the housing half-width (1.1) plus the barrel
     * (2.0) plus 2.0 px of eye relief along the diagonal's own −Z emission axis. */
    public static final float EYE_LOCAL_Y = -1.0F;
    public static final float EYE_LOCAL_Z = -5.1F;

    /**
     * {@code desired}, clamped to what {@code limits} actually lets this mount point at, given the
     * mount's own {@code baseUp} (the direction its tripod calls "straight up" — not necessarily
     * world up on a body with its own local gravity, per this mod's own asteroid gravity).
     */
    public static SkyRotation.Vec3 clamp(SkyRotation.Vec3 desired, SkyRotation.Vec3 baseUp,
                                         Limits limits) {
        SkyRotation.Vec3 up = baseUp.normalize();
        SkyRotation.Vec3 dir = desired.normalize();
        double angleFromUp = SkyRotation.angleBetweenDegrees(dir, up);
        if (angleFromUp >= limits.minAngleFromUpDegrees()
                && angleFromUp <= limits.maxAngleFromUpDegrees()) {
            return dir;
        }
        double targetAngle = Math.clamp(angleFromUp,
                limits.minAngleFromUpDegrees(), limits.maxAngleFromUpDegrees());
        return rotateToAngleFromUp(dir, up, angleFromUp, targetAngle);
    }

    /**
     * Rotates {@code dir} — currently {@code currentAngleDegrees} from {@code up} — until it sits
     * exactly {@code targetAngleDegrees} from {@code up}: the closest achievable direction to what
     * was actually asked for, in the same vertical plane as the request, rather than an arbitrary
     * fallback direction.
     */
    private static SkyRotation.Vec3 rotateToAngleFromUp(SkyRotation.Vec3 dir, SkyRotation.Vec3 up,
                                                         double currentAngleDegrees,
                                                         double targetAngleDegrees) {
        SkyRotation.Vec3 axis = perpendicularAxis(dir, up);
        // Rotating dir by a POSITIVE angle about axis = dir × up moves dir toward up (shrinks the
        // angle from up) — verified against SkyRotationTest's own worked example. Shrinking the
        // angle from currentAngleDegrees to targetAngleDegrees therefore needs exactly that many
        // degrees of positive rotation; growing it needs the same magnitude, negative.
        double deltaRadians = Math.toRadians(currentAngleDegrees - targetAngleDegrees);
        return SkyRotation.rotate(dir, axis, deltaRadians);
    }

    /**
     * An axis perpendicular to both {@code dir} and {@code up} to rotate about — their cross
     * product, or an arbitrary perpendicular to {@code up} if the two are (anti-)parallel, where a
     * cross product is undefined but any perpendicular axis rotates dir to the correct angle from
     * up by symmetry (there is no preferred "vertical plane" when already pointed straight at or
     * away from up).
     */
    private static SkyRotation.Vec3 perpendicularAxis(SkyRotation.Vec3 dir, SkyRotation.Vec3 up) {
        double crossX = dir.y() * up.z() - dir.z() * up.y();
        double crossY = dir.z() * up.x() - dir.x() * up.z();
        double crossZ = dir.x() * up.y() - dir.y() * up.x();
        double lengthSquared = crossX * crossX + crossY * crossY + crossZ * crossZ;
        if (lengthSquared < 1.0e-9) {
            return arbitraryPerpendicular(up);
        }
        return new SkyRotation.Vec3(crossX, crossY, crossZ).normalize();
    }

    private static SkyRotation.Vec3 arbitraryPerpendicular(SkyRotation.Vec3 up) {
        SkyRotation.Vec3 reference = Math.abs(up.x()) < 0.9
                ? new SkyRotation.Vec3(1.0, 0.0, 0.0)
                : new SkyRotation.Vec3(0.0, 1.0, 0.0);
        double crossX = up.y() * reference.z() - up.z() * reference.y();
        double crossY = up.z() * reference.x() - up.x() * reference.z();
        double crossZ = up.x() * reference.y() - up.y() * reference.x();
        return new SkyRotation.Vec3(crossX, crossY, crossZ).normalize();
    }

    /**
     * Where a point fixed to the tube — given in tube-local pixels relative to the trunnion pivot,
     * {@code (0, localY, localZ)} — actually sits in world space once the tube is aimed at
     * {@code (yawDegrees, pitchDegrees)}. design/astra-telescope.md §2.3.2: the BER composes
     * {@code rotateY(-yaw)} then {@code rotateX(e)} with {@code e = pitch + 90}; this is that same
     * composition applied to an arbitrary local offset instead of the tube's own drawn geometry,
     * so the eye and the drawn eyepiece can never disagree (one rotation, read twice — rule 46).
     *
     * <p>Cross-checked against {@code TelescopeMountEntity.directionFromRotation} (design's own
     * "prove it, do not assert it," rule 12): feeding this the tube's own axis {@code (0, 1, 0)}
     * reproduces {@code directionFromRotation(yaw, pitch)} exactly, term for term —
     * {@code TelescopeMountTest} holds this as an executable test, not just document prose.
     *
     * @return {@code {dx, dy, dz}}, the world-space displacement from the pivot, in the same pixel
     *         units {@code localY}/{@code localZ} were given in
     */
    public static double[] eyeOffsetFromPivot(double yawDegrees, double pitchDegrees,
                                              double localY, double localZ) {
        double elevationFromUp = Math.toRadians(pitchDegrees + 90.0);
        double y1 = localY * Math.cos(elevationFromUp) - localZ * Math.sin(elevationFromUp);
        double z1 = localY * Math.sin(elevationFromUp) + localZ * Math.cos(elevationFromUp);
        double yawRad = Math.toRadians(yawDegrees);
        return new double[] {-z1 * Math.sin(yawRad), y1, z1 * Math.cos(yawRad)};
    }

    /** The exact inverse of {@code Entity#calculateViewVector}, confirmed against the decompiled
     * source rather than assumed: {@code calculateViewVector(xRot, yRot)} returns {@code
     * (sin(-yRot)*cos(xRot), -sin(xRot), cos(-yRot)*cos(xRot))}. Home here (not
     * {@code TelescopeMountEntity}) so both the mount and {@code client.TelescopeCamera} — which
     * has no entity to call a method on for its own client-local aim accumulator — read the
     * identical formula (rule 46). */
    public static SkyRotation.Vec3 directionFromRotation(float yawDegrees, float pitchDegrees) {
        double xRot = Math.toRadians(pitchDegrees);
        double yRot = Math.toRadians(-yawDegrees);
        double yCos = Math.cos(yRot);
        double ySin = Math.sin(yRot);
        double xCos = Math.cos(xRot);
        double xSin = Math.sin(xRot);
        return new SkyRotation.Vec3(ySin * xCos, -xSin, yCos * xCos);
    }

    public static float[] rotationFromDirection(SkyRotation.Vec3 direction) {
        double pitchDegrees = Math.toDegrees(Math.asin(Math.clamp(-direction.y(), -1.0, 1.0)));
        double yawDegrees = -Math.toDegrees(Math.atan2(direction.x(), direction.z()));
        return new float[] {(float) yawDegrees, (float) pitchDegrees};
    }

    /** {@code (yawDegrees, pitchDegrees)}, clamped to {@code limits} given {@code baseUp} —
     * {@link #directionFromRotation}, {@link #clamp} and {@link #rotationFromDirection} composed
     * into the one operation every caller of the three together actually wants (rule 46: the
     * client's own local aim accumulator and {@code TelescopeMountEntity}'s server-side application
     * of a claimed aim both need exactly this, and previously each re-composed the same three calls
     * inline). */
    public static float[] clampRotation(float yawDegrees, float pitchDegrees,
                                        SkyRotation.Vec3 baseUp, Limits limits) {
        SkyRotation.Vec3 desired = directionFromRotation(yawDegrees, pitchDegrees);
        SkyRotation.Vec3 clamped = clamp(desired, baseUp, limits);
        return rotationFromDirection(clamped);
    }

    private TelescopeMount() {}
}
