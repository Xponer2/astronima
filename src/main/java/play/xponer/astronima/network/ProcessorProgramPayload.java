package play.xponer.astronima.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.processor.Assembler;
import play.xponer.astronima.sim.processor.Machine;
import play.xponer.astronima.wire.WirePart;
import play.xponer.astronima.wire.Wires;

/**
 * Client to server: a player saved a program into a mounted {@link PartType#PROCESSOR}'s code
 * editor. {@code design/processor.md} §5.1's "save assembles."
 *
 * <h2>The client already assembled this once — the server does it again anyway</h2>
 * {@link Assembler} is Minecraft-free, so {@code ProcessorEditorScreen} calls it directly and never
 * sends a source string the player's own screen already rejected — the error line-numbers a player
 * sees are instant, no round trip. But a packet is untrusted input regardless of what the sender's
 * own UI checked, so this handler re-assembles from the raw {@link #source()} and only ever writes
 * to the chip if <em>that</em> assembly succeeds too — the same "never trust the client" rule
 * {@link CircuitPlatePayload} already follows for a circuit's own validation.
 *
 * <p>A refused assembly writes nothing: the chip's existing {@code program} and {@code machine}
 * are simply never touched, which is what "a refusal changes nothing" (§5.1, §8) means at the
 * network boundary.
 */
public record ProcessorProgramPayload(String source, At mounted) implements CustomPacketPayload {

    public record At(BlockPos cell, Direction face, int u, int v) { }

    /** Generous, the same reach {@link CircuitPlatePayload} allows — the editor is a screen. */
    private static final double REACH_SQUARED = 8 * 8;

    public static final CustomPacketPayload.Type<ProcessorProgramPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "processor_program"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProcessorProgramPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buffer, payload.source());
                        buffer.writeBlockPos(payload.mounted().cell());
                        buffer.writeVarInt(payload.mounted().face().ordinal());
                        buffer.writeVarInt(payload.mounted().u());
                        buffer.writeVarInt(payload.mounted().v());
                    },
                    buffer -> {
                        String source = ByteBufCodecs.STRING_UTF8.decode(buffer);
                        BlockPos cell = buffer.readBlockPos();
                        Direction[] faces = Direction.values();
                        Direction face = faces[Math.floorMod(buffer.readVarInt(), faces.length)];
                        int u = buffer.readVarInt();
                        int v = buffer.readVarInt();
                        return new ProcessorProgramPayload(source, new At(cell, face, u, v));
                    });

    public static void apply(ProcessorProgramPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        At at = payload.mounted();
        if (player.distanceToSqr(at.cell().getX() + 0.5, at.cell().getY() + 0.5,
                at.cell().getZ() + 0.5) > REACH_SQUARED) {
            return;
        }
        Assembler.Result assembled = Assembler.assemble(payload.source());
        if (!assembled.ok()) {
            return;
        }
        Wires.partAt(player.level(), at.cell(), at.face(), at.u(), at.v())
                .filter(part -> part.type() == PartType.PROCESSOR)
                .ifPresent(part -> {
                    WirePart programmed = part.withProgram(payload.source(),
                            Machine.loaded(assembled.bytes()).pack());
                    Wires.mount(player.level(), programmed);
                });
    }

    @Override
    public CustomPacketPayload.Type<ProcessorProgramPayload> type() {
        return TYPE;
    }
}
