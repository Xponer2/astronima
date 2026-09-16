package play.xponer.astronima.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.block.entity.ProcessingBlockEntity;

/**
 * Client → server: the player moved a machine's control.
 *
 * <p>A separate packet rather than a writable {@code ContainerData} slot, because a
 * setting is an <em>instruction</em> and has to be validated: the position is checked
 * against what the player can actually reach, and the value is clamped by the machine
 * itself. A client that sends nonsense gets a clamped value, not a broken machine.
 */
public record MachineSettingPayload(BlockPos pos, float setting) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MachineSettingPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "machine_setting"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineSettingPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeFloat(payload.setting);
                    },
                    buffer -> new MachineSettingPayload(buffer.readBlockPos(), buffer.readFloat()));

    public static void apply(MachineSettingPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        // Reach check: a menu being open is not a licence to operate machines across
        // the map, and the position arrives from the client.
        if (!player.blockPosition().closerThan(payload.pos(), 8.0)) {
            return;
        }
        net.minecraft.world.level.block.entity.BlockEntity target =
                player.level().getBlockEntity(payload.pos());
        // A machine set with a wrench has no handle in its window at all, so a value arriving for
        // one did not come from a player dragging anything. Refusing here rather than relying on
        // the switch having no arm, because "no arm" is indistinguishable from the hole this whole
        // file's worst bug lived in (rule 39).
        if (target instanceof ProcessingBlockEntity machine && machine.isSetByWrench()) {
            return;
        }
        // None of the retort, the refiner or the fluidized bed has a case here any more: the
        // retort's mirror aims itself (SolarRetortBlockEntity#focus()), the refiner's vessel
        // runs its own temperature journey (CarbonylRefinerBlockEntity#setpointK()), and the
        // bed's drum matches its own spin to the feed (FluidizedBedBlockEntity#setting()), so a
        // setting packet arriving for any of them is a stale or modified client, and there is
        // nothing to apply.
        switch (target) {
            case play.xponer.astronima.block.entity.IncubatorBlockEntity incubator ->
                    incubator.setFromDial(payload.setting());
            case play.xponer.astronima.block.entity.SynthesiserBlockEntity synthesiser ->
                    synthesiser.setFromDial(payload.setting());
            default -> { }
        }
    }

    @Override
    public CustomPacketPayload.Type<MachineSettingPayload> type() {
        return TYPE;
    }
}
