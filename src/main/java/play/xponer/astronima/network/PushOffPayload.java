package play.xponer.astronima.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.LocomotionAids;
import play.xponer.astronima.sim.gravity.Microgravity;

/**
 * Client to server: push off the face I am aiming at, along its own outward normal —
 * design/eva-mobility.md §2.
 *
 * <p>Carries the targeted face's own outward normal, resolved client-side rather than re-derived
 * server-side — the same trust level {@code OrientationUpdatePayload} already gives the local
 * client for a purely non-exploitable movement fact, and the only way to guarantee the push
 * matches exactly what the player's own crosshair showed: the client's own raycast already reads
 * the real, unclamped {@code LocalOrientation} (see {@code OrientationViewVectorMixin}/{@code
 * OrientationEyePositionMixin}), which the server has no equivalent for without duplicating both
 * fixes a second time for a single-use case. Sent only on an actual hit — a miss costs nothing and
 * sends nothing, matching §2's own "no penalty for a wrong attempt."
 *
 * <p><strong>Re-normalized server-side before use</strong> — design/eva-mobility.md §1.7's own
 * network/anti-cheat question, closed here: a legitimate client only ever sends a real
 * {@code Direction}'s own unit-length step vector, but nothing stopped a modified client from
 * sending an arbitrarily large one — {@link LocomotionAids#pushOffImpulse} scales whatever it is
 * given directly by {@code PUSH_IMPULSE_SPEED}, with no cap of its own, so an oversized vector
 * here would have been a real, unbounded velocity exploit. Normalizing costs a legitimate client
 * nothing and makes the exploit impossible by construction; a degenerate (near-zero) vector is
 * simply ignored, the same "no penalty, nothing happens" shape a miss already has.
 */
public record PushOffPayload(float normalX, float normalY, float normalZ)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PushOffPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "push_off"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PushOffPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, PushOffPayload::normalX,
                    ByteBufCodecs.FLOAT, PushOffPayload::normalY,
                    ByteBufCodecs.FLOAT, PushOffPayload::normalZ,
                    PushOffPayload::new);

    public static void apply(PushOffPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        // Pushing off while standing on the floor is not this mechanic's job - ordinary walking
        // already exists. Deliberately real, literal onGround() here, not GroundedTracker's own
        // grace period - found live, a discrete key press silently refused to fire within
        // GroundedTracker's coyote-time window (right after a jump, exactly when this is most
        // wanted); that window exists to smooth continuous systems through a momentary flicker,
        // not to gate a one-shot action.
        if (Microgravity.hasAirControl(player.onGround())) {
            return;
        }
        double[] normal = LocomotionAids.normalizedOrZero(
                payload.normalX(), payload.normalY(), payload.normalZ());
        if (normal[0] == 0.0 && normal[1] == 0.0 && normal[2] == 0.0) {
            return;
        }
        double[] impulse = LocomotionAids.pushOffImpulse(normal[0], normal[1], normal[2]);
        player.setDeltaMovement(player.getDeltaMovement().add(impulse[0], impulse[1], impulse[2]));
        player.hurtMarked = true;
    }

    @Override
    public CustomPacketPayload.Type<PushOffPayload> type() {
        return TYPE;
    }
}
