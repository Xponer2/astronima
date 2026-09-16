package play.xponer.astronima.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.item.LogicPartItem;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.wire.PartFootprint;

/**
 * Client to server: turn the wire-layer part I am holding.
 *
 * <p>Carries nothing at all, deliberately — the server already knows who sent it and what they
 * are holding, and a packet that named a rotation would be a client asserting one. Everything a
 * payload can be trusted with here is the fact that a key was pressed.
 *
 * <p>The rotation lives on the stack rather than in a client-side field so that the ghost, the
 * placement and the item the player puts down all read one value; a client-only preview rotation
 * would be a picture that disagrees with the click, which is the fault
 * {@code PartPlacement} exists to prevent.
 */
public record PartRotatePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PartRotatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "part_rotate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartRotatePayload> STREAM_CODEC =
            StreamCodec.unit(new PartRotatePayload());

    public static void apply(PartRotatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof LogicPartItem) {
                held.set(ModDataComponents.PART_ROTATION.get(),
                        Math.floorMod(LogicPartItem.rotationOf(held) + 1,
                                PartFootprint.ROTATIONS));
                return;
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<PartRotatePayload> type() {
        return TYPE;
    }
}
