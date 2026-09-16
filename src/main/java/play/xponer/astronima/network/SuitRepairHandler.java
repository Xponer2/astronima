package play.xponer.astronima.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.item.EvaSuitItem;
import play.xponer.astronima.item.SuitRepairItem;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.organic.Polymer;
import play.xponer.astronima.sim.suit.SuitCondition;
import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Applies a completed repair, server-side.
 *
 * <p>The client owns the minigame; the server owns the consequences. It re-checks the
 * player is holding both the suit and the correct part, and that the subsystem really
 * is broken, so the packet can only ever finish a repair the player could have done.
 */
public final class SuitRepairHandler {
    public static void apply(SuitRepairDonePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        SuitSubsystem[] subsystems = SuitSubsystem.values();
        if (payload.subsystemOrdinal() < 0 || payload.subsystemOrdinal() >= subsystems.length) {
            return;
        }
        SuitSubsystem subsystem = subsystems[payload.subsystemOrdinal()];

        ItemStack suit = findHeld(player, ModItems.EVA_SUIT.get());
        if (suit.isEmpty()) {
            return;
        }
        InteractionHand partHand = heldPartHand(player, subsystem);
        if (partHand == null || SuitCondition.isWorking(EvaSuitItem.conditionOf(suit), subsystem)) {
            return;
        }

        float quality = Math.clamp(payload.quality(), 0f, 1f);
        ItemStack part = player.getItemInHand(partHand);
        if (part.is(ModItems.KAPTON_TAPE.get()) || part.is(ModItems.MYLAR.get())) {
            // Cold plastic goes brittle: a repair made outside in the cold starts life shorter,
            // the honest way to show a rushed-looking patch rather than refusing it outright
            // (design/petrochemicals.md §4).
            quality = (float) Polymer.embrittledQuality(quality, ambientTemperatureK(player));
        }
        EvaSuitItem.repair(suit, subsystem, quality);
        part.shrink(1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.PLAYERS, 0.7f, 1.3f);

        int mask = EvaSuitItem.conditionOf(suit);
        player.sendSystemMessage(SuitCondition.isFullyRepaired(mask)
                ? Component.translatable("astronima.suit.rebuilt").withStyle(ChatFormatting.GREEN)
                : Component.translatable("astronima.suit.repaired",
                        Component.literal(subsystem.displayName()),
                        SuitCondition.faults(mask).size()).withStyle(ChatFormatting.YELLOW));
    }

    /** The sealed room's temperature, or the ambient rock/vacuum temperature outside one. */
    private static double ambientTemperatureK(ServerPlayer player) {
        Atmosphere.RoomReading reading = Atmosphere.get(player.level())
                .readingNear(player.blockPosition());
        if (reading != null && reading.sealed() && !reading.openToSpace()) {
            RoomState room = reading.state();
            return room.temperatureK();
        }
        return Atmosphere.AMBIENT_ROCK_TEMP_K;
    }

    private static ItemStack findHeld(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).is(item)) {
                return player.getItemInHand(hand);
            }
        }
        return ItemStack.EMPTY;
    }

    private static InteractionHand heldPartHand(ServerPlayer player, SuitSubsystem subsystem) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SuitRepairItem part && part.handles(subsystem)) {
                return hand;
            }
        }
        return null;
    }

    private SuitRepairHandler() {}
}
