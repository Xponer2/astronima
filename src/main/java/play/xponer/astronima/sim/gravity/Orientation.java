package play.xponer.astronima.sim.gravity;

/**
 * A real, free orientation — design/eva-mobility.md §1.1: one unit quaternion, hand-rolled rather
 * than {@code org.joml.Quaternionf}, for the same reason {@code AstraGrid} hand-rolled its own
 * cell-key packing instead of {@code BlockPos.asLong} — this mod's plain-JUnit source set has
 * nothing beyond the JDK and this project's own classes on its classpath, and JOML ships as part
 * of the client/Minecraft dependency graph, not a library this runner is guaranteed to see.
 * Minecraft-free (rule 1).
 *
 * <h2>Why composition, not replacement, is what actually avoids gimbal lock</h2>
 * Vanilla turns an entity by <em>setting</em> absolute yaw/pitch each frame — Euler angles, and
 * Euler angles are exactly what gimbal-locks at the poles. {@link #composeLocal} instead takes a
 * small rotation and left-multiplies it onto the current orientation: every frame's turn happens
 * relative to whatever this already faces, so there is no axis it can ever lock against.
 *
 * <h2>The one place this still touches Euler angles, and why that is safe</h2>
 * {@link #toEulerYawPitchRoll()} exists only to feed
 * {@code net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles}, whose own API is
 * three floats — the boundary is the render event's, not this class's own control scheme, which
 * never stores or accumulates Euler angles anywhere. {@link #fromEulerYXZ} is the exact inverse,
 * a direct port of {@code Camera#setRotation}'s own construction
 * ({@code rotationYXZ(π - yaw·d2r, -pitch·d2r, -roll·d2r)}, verified against this project's own
 * decompiled sources per rule 2) so a value round-trips through both without drifting.
 */
public record Orientation(float w, float x, float y, float z) {

    /**
     * The zero rotation — leaves any vector completely unchanged, including
     * {@code FORWARDS = (0, 0, -1)}. <strong>Not the same thing as "yaw 0°" in this class's own
     * yaw/pitch/roll convention</strong> — {@link #fromEulerYXZ}'s {@code Y = π - yaw·d2r} term
     * (a direct port of {@code Camera#setRotation}'s own construction) means
     * {@code fromEulerYXZ(0, 0, 0)} is a real 180° turn about Y, not this. Call
     * {@code fromEulerYXZ(0, 0, 0)} for "facing the convention's own yaw zero," not this field, if
     * that is what is actually wanted (found live, writing this class's own test).
     */
    public static final Orientation IDENTITY = new Orientation(1f, 0f, 0f, 0f);

    /** A right-handed rotation of {@code radians} about the given (already unit-length) axis. */
    public static Orientation fromAxisAngle(float ax, float ay, float az, float radians) {
        float half = radians * 0.5f;
        float s = (float) Math.sin(half);
        return new Orientation((float) Math.cos(half), ax * s, ay * s, az * s);
    }

    /**
     * The Camera's own construction, exactly (rule 2 — verified against
     * {@code Camera#setRotation(float, float, float)}'s decompiled body): a rotation of
     * {@code Y = π - yawDeg·d2r} about Y, then {@code X = -pitchDeg·d2r} about X (in the frame Y
     * just produced), then {@code Z = -rollDeg·d2r} about Z (in the frame X just produced) —
     * {@code qY.times(qX).times(qZ)}, never the other composition order.
     */
    public static Orientation fromEulerYXZ(float yawDeg, float pitchDeg, float rollDeg) {
        float d2r = (float) (Math.PI / 180.0);
        float bigY = (float) Math.PI - yawDeg * d2r;
        float bigX = -pitchDeg * d2r;
        float bigZ = -rollDeg * d2r;
        Orientation qy = fromAxisAngle(0, 1, 0, bigY);
        Orientation qx = fromAxisAngle(1, 0, 0, bigX);
        Orientation qz = fromAxisAngle(0, 0, 1, bigZ);
        return qy.times(qx).times(qz);
    }

    /** The Hamilton product {@code this ⊗ other} — applying the result rotates a vector by
     *  {@code other} first, then by {@code this}, in {@code other}'s own resulting local frame. */
    public Orientation times(Orientation other) {
        return new Orientation(
                w * other.w - x * other.x - y * other.y - z * other.z,
                w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w
        ).normalized();
    }

    /**
     * Composes a small rotation onto this orientation <strong>in this orientation's own current
     * local frame</strong> — design/eva-mobility.md §1.1's whole mechanism. A mouse-look delta or
     * a roll-key tick becomes a small {@code delta} via {@link #fromAxisAngle} and is folded in
     * with {@code this.times(delta)}, so "pitch up" always means "toward this orientation's own
     * current up," never toward a fixed world axis.
     */
    public Orientation composeLocal(Orientation delta) {
        return this.times(delta);
    }

    /**
     * Spherical interpolation toward {@code target}, {@code t} in {@code [0, 1]} — the render-tick
     * smoothing this system needs for the same reason vanilla lerps {@code xRotO}→{@code xRot}
     * every frame: this system's own updates land once per real tick (20/s), far coarser than the
     * screen's own frame rate, and feeding a render event the raw per-tick value produces a
     * visibly stepped roll rather than a smooth one (found live: reported as "дёрганые" - jerky -
     * the first time this was actually flown). Takes the shorter of the two paths between the
     * quaternions (negating {@code target} when the dot product is negative), which is what makes
     * this correct and not merely a per-component lerp with a normalize bolted on.
     */
    public Orientation slerp(Orientation target, float t) {
        float dot = w * target.w + x * target.x + y * target.y + z * target.z;
        Orientation to = target;
        if (dot < 0f) {
            to = new Orientation(-target.w, -target.x, -target.y, -target.z);
            dot = -dot;
        }
        if (dot > 0.9995f) {
            // Too close for sin(theta) to stay numerically safe - a plain lerp is
            // indistinguishable at this range and normalized() below makes it a real rotation.
            return new Orientation(
                    w + (to.w - w) * t,
                    x + (to.x - x) * t,
                    y + (to.y - y) * t,
                    z + (to.z - z) * t
            ).normalized();
        }
        float theta0 = (float) Math.acos(Math.min(1f, dot));
        float theta = theta0 * t;
        float sinTheta = (float) Math.sin(theta);
        float sinTheta0 = (float) Math.sin(theta0);
        float s0 = (float) Math.cos(theta) - dot * sinTheta / sinTheta0;
        float s1 = sinTheta / sinTheta0;
        return new Orientation(
                w * s0 + to.w * s1,
                x * s0 + to.x * s1,
                y * s0 + to.y * s1,
                z * s0 + to.z * s1
        ).normalized();
    }

    public Orientation normalized() {
        float lengthSq = w * w + x * x + y * y + z * z;
        if (lengthSq < 1.0e-12f) {
            return IDENTITY;
        }
        float inv = (float) (1.0 / Math.sqrt(lengthSq));
        return new Orientation(w * inv, x * inv, y * inv, z * inv);
    }

    /** Rotates the vector {@code (vx, vy, vz)} by this orientation. */
    public float[] rotate(float vx, float vy, float vz) {
        // v' = v + 2w(qv × v) + 2(qv × (qv × v)) — the standard optimised quaternion rotation,
        // never the full q·v·q⁻¹ product expanded by hand.
        float qvx = x;
        float qvy = y;
        float qvz = z;
        float tx = 2f * (qvy * vz - qvz * vy);
        float ty = 2f * (qvz * vx - qvx * vz);
        float tz = 2f * (qvx * vy - qvy * vx);
        float rx = vx + w * tx + (qvy * tz - qvz * ty);
        float ry = vy + w * ty + (qvz * tx - qvx * tz);
        float rz = vz + w * tz + (qvx * ty - qvy * tx);
        return new float[] {rx, ry, rz};
    }

    /**
     * The inverse of {@link #fromEulerYXZ} — yaw/pitch/roll in degrees, in the exact convention
     * {@code ViewportEvent.ComputeCameraAngles} consumes. Computed from first principles (the
     * rotated forward and up vectors), not a closed-form matrix-element extraction, so it is
     * provably self-consistent with {@link #rotate} and {@link #fromEulerYXZ} rather than a
     * second, independently-derived formula that could silently disagree with them (rule 46).
     *
     * <p>Degenerate, as every Euler representation is, only where {@code pitch} is exactly ±90° —
     * yaw and roll become one degree of freedom there. This never affects the real control scheme
     * ({@link #composeLocal} never touches Euler angles at all); it is a display-only seam.
     */
    public float[] toEulerYawPitchRoll() {
        float[] forward = rotate(0, 0, -1);
        float r2d = (float) (180.0 / Math.PI);
        float pitchRad = (float) Math.asin(clamp(-forward[1], -1f, 1f));
        float yawRad = (float) Math.atan2(-forward[0], forward[2]);
        float yawDeg = yawRad * r2d;
        float pitchDeg = pitchRad * r2d;

        // The zero-roll reference "up" at this exact yaw/pitch, built the same way this
        // orientation itself would have been (self-consistent, not a second formula) - the
        // signed angle from it to this orientation's own real "up" is the roll.
        Orientation zeroRoll = fromEulerYXZ(yawDeg, pitchDeg, 0f);
        float[] referenceUp = zeroRoll.rotate(0, 1, 0);
        float[] actualUp = rotate(0, 1, 0);
        float cosRoll = dot(referenceUp, actualUp);
        float[] cross = cross(forward, referenceUp);
        float sinRoll = dot(cross, actualUp);
        float rollDeg = (float) Math.atan2(sinRoll, cosRoll) * r2d;

        return new float[] {yawDeg, pitchDeg, rollDeg};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
