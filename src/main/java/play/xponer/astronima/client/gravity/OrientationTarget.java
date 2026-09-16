package play.xponer.astronima.client.gravity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.Orientation;

/**
 * The one rule both real consumers of {@link Orientation} (the camera, §1.3, and the third-person
 * model, §1.4) have to agree on identically (rule 46): grounded, the target is whatever vanilla's
 * own {@code yRot}/{@code xRot} already say — real, current, and correct for anyone, not only the
 * local player; airborne, it is the real synced fact. Both consumers smooth toward this same
 * target ({@link OrientationSmoothing}) rather than snapping to it, which is what makes landing a
 * transition instead of an instant snap for exactly the same reason it makes a stepped per-tick
 * roll read as continuous.
 */
final class OrientationTarget {

    /** For the camera (§1.3): grounded, this must equal exactly what vanilla itself would have
     *  computed, since the camera's own rotation always tracks {@code yRot}/{@code xRot}/roll
     *  unconditionally — there is only one thing ever computing it, so matching vanilla here is
     *  correct, not redundant. Airborne, reads {@link LocalOrientation} rather than the synced
     *  attachment — there is only ever one camera, and it is always this player's own, so it must
     *  never see a network round trip of its own input (see {@link LocalOrientation}'s own doc). */
    static Orientation forCamera(LivingEntity entity) {
        if (Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(entity))) {
            return Orientation.fromEulerYXZ(entity.getYRot(), entity.getXRot(), 0f);
        }
        return LocalOrientation.get();
    }

    /**
     * For the third-person model (§1.4): grounded, this must be {@link Orientation#IDENTITY} — no
     * extra transform at all — never "matching vanilla's own yaw/pitch" the way the camera's own
     * target does. Found live: applying a real pitch/roll rotation to the *whole body* even while
     * standing on the ground made the character visibly lean just from looking up or down, which
     * vanilla's own body never does — only the head does, through a wholly separate mechanism this
     * class must never compete with. {@code setupRotations}' own {@code bodyRot} yaw and the
     * model's own head-pitch animation already own the grounded case completely; this class's own
     * extra {@code mulPose} has no business existing there at all.
     *
     * <p><strong>Yaw stripped even while airborne</strong> — the same double-rotation risk one
     * level up: {@code setupRotations} always applies its own {@code bodyRot} yaw underneath
     * whatever this class returns (its transform is pushed second, nested inside this one), so
     * returning this player's real yaw here would compound with vanilla's own, not replace it.
     * Only the pitch/roll deviation is this class's to add; yaw stays vanilla's own fact to own,
     * airborne or not. Not a perfect physical composition in every extreme roll-then-yaw case
     * (real rigid-body composition is order-sensitive, §1.1's own point about
     * {@link Orientation#composeLocal}) — a real, honestly-named remaining limit, not a claim this
     * is exact, and worth a `runClient` look rather than a guess at how much it matters felt.
     *
     * <p><strong>Stripping yaw means passing 180°, not 0°</strong> — found live, reported as a
     * remote player facing directly away from an observer they were actually facing, with the
     * tilt also visibly wrong: {@link Orientation#IDENTITY}'s own doc already names this trap
     * ({@code fromEulerYXZ(0, 0, 0)} is a real 180° turn about Y, not a no-op) and this call was
     * the one place in this codebase that still walked straight into it, passing a literal
     * {@code 0f} where "no net yaw" actually means {@code 180f} under this class's own convention.
     *
     * <p><strong>The local player's own avatar reads {@link LocalOrientation}, not the synced
     * attachment</strong> — the same reason {@link #forCamera} does: this player's own third-person
     * view of themselves must never see a round trip of their own input either, or it fights the
     * camera in exactly the way {@link LocalOrientation}'s own doc describes. A remote player has
     * no local copy to read at all, so the synced attachment stays the only real source for them.
     */
    static Orientation forModel(LivingEntity entity) {
        if (Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(entity))) {
            return Orientation.IDENTITY;
        }
        Orientation real = entity == Minecraft.getInstance().player
                ? LocalOrientation.get()
                : entity.getData(ModAttachments.ORIENTATION.get());
        float[] yawPitchRoll = real.toEulerYawPitchRoll();
        return Orientation.fromEulerYXZ(180f, yawPitchRoll[1], yawPitchRoll[2]);
    }

    private OrientationTarget() {}
}
