package play.xponer.astronima.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import play.xponer.astronima.sim.airlock.DeviceBinding.Role;

/**
 * A wrench that has been told which terminal it is about to fill.
 *
 * <p>Commissioning needs two clicks in two different places — the terminal on the panel,
 * then the device out in the world — so something has to remember the first while the
 * player walks to the second. It lives on the <em>wrench stack</em> rather than on the
 * controller for two reasons: a wrench that is armed can say so in its own tooltip, and
 * two players commissioning two panels in the same room cannot arm each other's terminals.
 *
 * <p>The wrench was chosen over a new item because it is already "the thing you configure
 * things with" — it rotates the pump, it turns the valve's axis — and an airlock that
 * needed a second tool crafted before it would run at all would put a wall in front of the
 * one machine this tier exists for (design/commissioning.md §C3).
 *
 * @param controller the panel whose terminal is armed, so a click far from it can be refused
 * @param role       the terminal waiting for a device
 */
public record TerminalArm(BlockPos controller, Role role) {

    public static final Codec<TerminalArm> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.fieldOf("controller").forGetter(TerminalArm::controller),
                    Codec.INT.fieldOf("role").forGetter(arm -> arm.role().ordinal())
            ).apply(instance, TerminalArm::of));

    public static final StreamCodec<ByteBuf, TerminalArm> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TerminalArm::controller,
            ByteBufCodecs.VAR_INT, arm -> arm.role().ordinal(),
            TerminalArm::of);

    /** An ordinal off the wire or out of a save is untrusted; an odd one falls back. */
    public static TerminalArm of(BlockPos controller, int roleOrdinal) {
        Role[] roles = Role.values();
        Role role = roleOrdinal >= 0 && roleOrdinal < roles.length ? roles[roleOrdinal] : roles[0];
        return new TerminalArm(controller, role);
    }
}
