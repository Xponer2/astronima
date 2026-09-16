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
import play.xponer.astronima.sim.gravity.Tether;
import play.xponer.astronima.sim.gravity.TetherState;

/**
 * Client to server: fire the tether at this exact point — design/eva-mobility.md §3.2.
 *
 * <p>Carries the real raycast hit point, resolved client-side rather than re-derived server-side —
 * the same trust level {@code PushOffPayload} already gives the local client for a purely
 * non-exploitable movement fact, and for the same reason: re-deriving the same fixed
 * view-vector/eye-position raycast server-side for this one use would duplicate fixes already made
 * for the camera's own sake. The server does not re-raycast, but does check the claimed point is
 * not absurdly far from the player — a sanity margin against a clearly-bogus claim, not a
 * substitute for the real check the client already did.
 */
public record TetherFirePayload(double anchorX, double anchorY, double anchorZ)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TetherFirePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "tether_fire"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TetherFirePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, TetherFirePayload::anchorX,
                    ByteBufCodecs.DOUBLE, TetherFirePayload::anchorY,
                    ByteBufCodecs.DOUBLE, TetherFirePayload::anchorZ,
                    TetherFirePayload::new);

    public static void apply(TetherFirePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        // Deliberately real, literal onGround() here, not GroundedTracker's own grace period -
        // see ClientTickHandler#toggleTether's own note: a discrete key press has no business
        // reading a grace window built to smooth continuous systems through a momentary flicker.
        if (Microgravity.hasAirControl(player.onGround())) {
            return;
        }
        double dx = payload.anchorX() - player.getX();
        double dy = payload.anchorY() - player.getY();
        double dz = payload.anchorZ() - player.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > Tether.RANGE + 2.0) {
            return;
        }
        player.setData(ModAttachments.TETHER.get(), new TetherState(
                true, payload.anchorX(), payload.anchorY(), payload.anchorZ(), distance));
    }

    @Override
    public CustomPacketPayload.Type<TetherFirePayload> type() {
        return TYPE;
    }
}
