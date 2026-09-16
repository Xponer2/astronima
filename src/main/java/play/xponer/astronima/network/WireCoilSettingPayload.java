package play.xponer.astronima.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.item.WireCoil;
import play.xponer.astronima.item.WireCoilItem;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.wire.WireRouter;

/**
 * Client → server: the player set their coil from the panel.
 *
 * <p>Its own packet rather than a writable slot, for the reason {@link MachineSettingPayload}
 * gives: the client is asking, not telling. Everything it names is validated here — an ordinal
 * off the wire is untrusted input, and the only stack it can ever change is the coil the sender
 * is actually holding.
 */
public record WireCoilSettingPayload(int material, int colour, int mode)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WireCoilSettingPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "wire_coil_setting"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WireCoilSettingPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.material);
                        buffer.writeVarInt(payload.colour);
                        buffer.writeVarInt(payload.mode);
                    },
                    buffer -> new WireCoilSettingPayload(buffer.readVarInt(),
                            buffer.readVarInt(), buffer.readVarInt()));

    public static WireCoilSettingPayload of(WireCoil coil) {
        return new WireCoilSettingPayload(coil.material().ordinal(), coil.colour().ordinal(),
                coil.mode().ordinal());
    }

    public static void apply(WireCoilSettingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        // Only a coil, and only one the sender is holding: a packet must not be able to
        // reconfigure something in a chest, or in somebody else's hand.
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof WireCoilItem) {
                held.set(ModDataComponents.WIRE_COIL.get(), new WireCoil(
                        value(ConductorMaterial.values(), payload.material),
                        value(DyeColor.values(), payload.colour),
                        value(WireRouter.Mode.values(), payload.mode)));
                return;
            }
        }
    }

    private static <T> T value(T[] values, int ordinal) {
        return values[Math.floorMod(ordinal, values.length)];
    }

    @Override
    public CustomPacketPayload.Type<WireCoilSettingPayload> type() {
        return TYPE;
    }
}
