package play.xponer.astronima.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.Orientation;

/**
 * Client to server: my own real orientation, this tick — design/eva-mobility.md §1.2's missing
 * half, found live: {@link ModAttachments#ORIENTATION} syncs server→client automatically on every
 * server-side write, but {@code OrientationInputMixin}/{@code OrientationRollInputEvents} only
 * ever write it <em>client-side</em> ({@code Entity#turn} fires only on the local player's own
 * client entity). A client-side {@code setData} call never reaches the server at all, so every
 * other client kept reading the server's own stale, never-updated copy — reported directly in
 * play as "in multiplayer the other player just flies normally, no tilt at all."
 *
 * <p>Fire-and-forget, once per client tick while airborne in this dimension — the same trust
 * level this mod already gives the local client for a purely visual, non-exploitable fact (no
 * position, no velocity, no reward). The server re-derives nothing from it; it simply becomes the
 * new authoritative value and rides the attachment's own existing sync to every other observer.
 *
 * <p><strong>Normalized before it is ever stored</strong> — design/eva-mobility.md §1.7's own
 * network/anti-cheat question, named there and left for "a real check at that leaf, not assumed
 * clear by this one's own answer," closed here once this fact actually started feeding real
 * physics: {@link Orientation#rotate} assumes a unit quaternion, and a non-unit one silently scales
 * whatever it rotates instead of only turning it. {@code RcsThrustMixin} rotates a thrust vector by
 * exactly this stored orientation every tick a player holds a movement key — a client sending an
 * oversized quaternion here would have been a real, undetected speed exploit, not merely a visual
 * glitch. Normalizing on receipt costs a legitimate client nothing (its own quaternion is already
 * unit length) and makes a malformed or malicious one harmless by construction.
 */
public record OrientationUpdatePayload(float w, float x, float y, float z) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OrientationUpdatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "orientation_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrientationUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, OrientationUpdatePayload::w,
                    ByteBufCodecs.FLOAT, OrientationUpdatePayload::x,
                    ByteBufCodecs.FLOAT, OrientationUpdatePayload::y,
                    ByteBufCodecs.FLOAT, OrientationUpdatePayload::z,
                    OrientationUpdatePayload::new);

    public static OrientationUpdatePayload of(Orientation orientation) {
        return new OrientationUpdatePayload(orientation.w(), orientation.x(), orientation.y(), orientation.z());
    }

    public Orientation orientation() {
        return new Orientation(w, x, y, z);
    }

    public static void apply(OrientationUpdatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        if (Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(player))) {
            return;
        }
        player.setData(ModAttachments.ORIENTATION.get(), payload.orientation().normalized());
    }

    @Override
    public CustomPacketPayload.Type<OrientationUpdatePayload> type() {
        return TYPE;
    }
}
