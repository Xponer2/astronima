package play.xponer.astronima.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.item.CircuitPlate;
import play.xponer.astronima.item.LogicPartItem;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.logic.Circuit;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.wire.WirePart;
import play.xponer.astronima.wire.Wires;

import java.util.Optional;

/**
 * Client to server: the player edited a plate — the one in their hand, or one on a wall.
 *
 * <p>The editor changes nothing by itself. It asks, and the server decides — so an edit survives
 * only if the sender really is holding a plate, or really is within reach of the mounted one they
 * name. The circuit itself is validated by {@link Circuit}'s own constructor on the way in, so a
 * packet describing a looping circuit cannot be stored at all.
 *
 * @param mounted where the plate is on a wall, or empty when it is the one in the player's hand
 * @param name    the plate's custom name, "" for none — carried alongside the circuit so renaming
 *                is one edit rather than a second, separately-plumbed packet
 */
public record CircuitPlatePayload(Circuit circuit, Optional<At> mounted, String name)
        implements CustomPacketPayload {

    /** A plate in the wire layer: which pixel of which face it is bolted to. */
    public record At(BlockPos cell, Direction face, int u, int v) { }

    /**
     * How far a player may reach to edit a mounted plate.
     *
     * <p>Checked rather than trusted: the position comes off the network, and without this a
     * client could rewrite the logic of any plate in the world it happened to know about.
     * Generous — the editor is a screen and a player will drift while it is open.
     */
    private static final double REACH_SQUARED = 8 * 8;

    public CircuitPlatePayload(Circuit circuit, Optional<At> mounted) {
        this(circuit, mounted, "");
    }

    public CircuitPlatePayload(Circuit circuit) {
        this(circuit, Optional.empty(), "");
    }

    public static final CustomPacketPayload.Type<CircuitPlatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "circuit_plate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CircuitPlatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        CircuitPlate.STREAM_CODEC.encode(buffer, payload.circuit());
                        buffer.writeBoolean(payload.mounted().isPresent());
                        payload.mounted().ifPresent(at -> {
                            buffer.writeBlockPos(at.cell());
                            buffer.writeVarInt(at.face().ordinal());
                            buffer.writeVarInt(at.u());
                            buffer.writeVarInt(at.v());
                        });
                        buffer.writeUtf(payload.name());
                    },
                    buffer -> {
                        Circuit circuit = CircuitPlate.STREAM_CODEC.decode(buffer);
                        Optional<At> mounted = Optional.empty();
                        if (buffer.readBoolean()) {
                            BlockPos cell = buffer.readBlockPos();
                            Direction[] faces = Direction.values();
                            Direction face = faces[Math.floorMod(buffer.readVarInt(), faces.length)];
                            mounted = Optional.of(new At(cell, face, buffer.readVarInt(),
                                    buffer.readVarInt()));
                        }
                        String name = buffer.readUtf();
                        return new CircuitPlatePayload(circuit, mounted, name);
                    });

    public static void apply(CircuitPlatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (payload.mounted().isPresent()) {
            applyToWall(payload, player);
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof LogicPartItem part && isPlate(part.type())) {
                held.set(ModDataComponents.CIRCUIT.get(), payload.circuit());
                if (payload.name().isEmpty()) {
                    held.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                } else {
                    held.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            net.minecraft.network.chat.Component.literal(payload.name()));
                }
                return;
            }
        }
    }

    /**
     * Writes the circuit into a plate mounted on a wall.
     *
     * <p>Everything about the target is checked here rather than assumed: that it is in reach,
     * that something is actually at that pixel, and that what is there is a plate. A packet is
     * untrusted input, and the failure of not checking would be silent — a client rewriting a
     * neighbour's interlock with nothing to see.
     */
    private static void applyToWall(CircuitPlatePayload payload, ServerPlayer player) {
        At at = payload.mounted().orElseThrow();
        if (player.distanceToSqr(at.cell().getX() + 0.5, at.cell().getY() + 0.5,
                at.cell().getZ() + 0.5) > REACH_SQUARED) {
            return;
        }
        Wires.partAt(player.level(), at.cell(), at.face(), at.u(), at.v())
                .filter(part -> isPlate(part.type()))
                .ifPresent(part -> {
                    WirePart edited = part.withCircuit(payload.circuit()).withName(payload.name());
                    Wires.mount(player.level(), edited);
                });
    }

    /**
     * Whichever plate flavour, saving works the same way — this is the check every save path has
     * to share, since a check written for {@code PartType.PLATE} alone silently drops every edit
     * to a macro plate rather than rejecting or accepting it visibly (PLAN.md rule, macro-plate
     * open/save routing).
     */
    private static boolean isPlate(PartType type) {
        return type == PartType.PLATE || type == PartType.MACRO_PLATE;
    }

    @Override
    public CustomPacketPayload.Type<CircuitPlatePayload> type() {
        return TYPE;
    }
}
