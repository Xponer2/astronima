package play.xponer.astronima.network;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.block.entity.AirlockControllerBlockEntity;
import play.xponer.astronima.item.TerminalArm;
import play.xponer.astronima.item.WrenchItem;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.sim.airlock.DeviceBinding.Role;

/**
 * Client → server: the player worked a terminal on the airlock panel.
 *
 * <p>Three things a terminal can be told, all of them the player's half of rule 17:
 * <strong>arm</strong> it (the next wrench click lands a device here), <strong>clear</strong>
 * it, or run <strong>scan</strong> across the whole panel to propose bindings for whatever
 * is still empty.
 *
 * <p>Its own packet rather than a writable data slot, for the reason
 * {@link MachineSettingPayload} gives: these are <em>instructions</em>, the position comes
 * from the client, and a menu being open is not a licence to re-commission airlocks across
 * the map. Reach-checked and type-checked before anything happens.
 */
public record AirlockTerminalPayload(BlockPos pos, int role, int action)
        implements CustomPacketPayload {

    /** Arm this terminal: the next wrench click binds a device to it. */
    public static final int ARM = 0;
    /** Empty this terminal. */
    public static final int CLEAR = 1;
    /** Propose bindings for every terminal still empty. */
    public static final int SCAN = 2;

    public static final CustomPacketPayload.Type<AirlockTerminalPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "airlock_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AirlockTerminalPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.pos);
                        buffer.writeVarInt(payload.role);
                        buffer.writeVarInt(payload.action);
                    },
                    buffer -> new AirlockTerminalPayload(buffer.readBlockPos(),
                            buffer.readVarInt(), buffer.readVarInt()));

    public static void apply(AirlockTerminalPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.blockPosition().closerThan(payload.pos(), 8.0)) {
            return;
        }
        if (!(level.getBlockEntity(payload.pos())
                instanceof AirlockControllerBlockEntity controller)) {
            return;
        }
        Role[] roles = Role.values();
        if (payload.role() < 0 || payload.role() >= roles.length) {
            return;
        }
        Role role = roles[payload.role()];

        switch (payload.action()) {
            case ARM -> arm(player, controller, payload.pos(), role);
            case CLEAR -> controller.clearBinding(role);
            case SCAN -> {
                boolean proposed = controller.scan(level);
                say(player, proposed
                        ? Component.translatable("astronima.airlock.scan_proposed")
                                .withStyle(ChatFormatting.AQUA)
                        : Component.translatable("astronima.airlock.scan_found_nothing")
                                .withStyle(ChatFormatting.RED));
            }
            default -> { }
        }
    }

    /**
     * Puts the wrench in the player's hand into binding mode and gets out of the way.
     *
     * <p>The panel closes on purpose: the next click has to happen on a block out in the
     * world, and leaving the screen open over it would be a mode with the door shut. With
     * no wrench in either hand this says so rather than arming nothing — an armed terminal
     * that quietly did not arm is the failure that would send a player clicking at a door
     * wondering why it keeps opening.
     */
    private static void arm(ServerPlayer player, AirlockControllerBlockEntity controller,
                            BlockPos pos, Role role) {
        ItemStack wrench = wrenchIn(player);
        if (wrench.isEmpty()) {
            say(player, Component.translatable("astronima.airlock.need_wrench")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        wrench.set(ModDataComponents.TERMINAL_ARM.get(), new TerminalArm(pos, role));
        player.closeContainer();
        say(player, Component.translatable("astronima.airlock.armed", role.label())
                .withStyle(ChatFormatting.AQUA));
    }

    private static ItemStack wrenchIn(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof WrenchItem) {
                return held;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Action bar, not chat: this is a one-off event tied to a click (PLAN §0.5.0). */
    private static void say(ServerPlayer player, Component message) {
        player.sendSystemMessage(message, true);
    }

    @Override
    public CustomPacketPayload.Type<AirlockTerminalPayload> type() {
        return TYPE;
    }
}
