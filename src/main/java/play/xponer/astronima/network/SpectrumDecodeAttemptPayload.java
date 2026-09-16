package play.xponer.astronima.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.item.FilterTokenItem;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.magic.ObservationTarget;
import play.xponer.astronima.sim.magic.ResearchState;
import play.xponer.astronima.sim.magic.SpectralLine;
import play.xponer.astronima.sim.magic.Spectrum;

import java.util.Locale;

/**
 * "I marked this position on the strip — try this filter against it, the atlas armed it from
 * whatever I own" (design/astra-research-m4b.md §3, revised after the first cut's "hold it in
 * your hand" gesture proved unusable in real play — off-hand felt natural to try and silently did
 * nothing, and neither hand puts the filter anywhere the atlas screen itself can show as
 * selected). The client only reports where it clicked and which line it armed; the server
 * independently re-derives the target's real (shifted) lines and confirms the sending player
 * actually owns a {@link FilterTokenItem} for that line <em>somewhere in their inventory</em>
 * before ever touching {@link ResearchState} — ownership, not hand placement, is the real
 * precondition, the same trust boundary {@code ClaimResolvePayload} and
 * {@code TelescopeCaptureAttemptPayload} already keep: a client that could report its own match
 * could report one it never actually earned.
 */
public record SpectrumDecodeAttemptPayload(String objectId, double candidateNm, SpectralLine appliedFilter)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpectrumDecodeAttemptPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "spectrum_decode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpectrumDecodeAttemptPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SpectrumDecodeAttemptPayload::objectId,
                    ByteBufCodecs.DOUBLE, SpectrumDecodeAttemptPayload::candidateNm,
                    ByteBufCodecs.STRING_UTF8.map(SpectralLine::valueOf, Enum::name),
                    SpectrumDecodeAttemptPayload::appliedFilter,
                    SpectrumDecodeAttemptPayload::new);

    public static void apply(SpectrumDecodeAttemptPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ObservationTarget target = parseTarget(payload.objectId());
        if (target == null) {
            return;
        }
        ResearchState before = player.getData(ModAttachments.RESEARCH.get());
        // A decode attempt on an object the player never actually captured, or has already
        // identified, is either a stale click or a spoofed one - nothing left to earn either way.
        if (!before.captured(payload.objectId()) || before.identified(payload.objectId())) {
            return;
        }
        if (!ownsFilter(player, payload.appliedFilter())) {
            fail(player, "astronima.atlas.decode.no_filter");
            return;
        }

        Spectrum.DecodeResult result = Spectrum.decode(target, payload.candidateNm(), payload.appliedFilter());
        switch (result.outcome()) {
            case NOT_ABOVE_NOISE -> fail(player, "astronima.atlas.decode.noise");
            case WRONG_FILTER -> fail(player, "astronima.atlas.decode.wrong_filter");
            case MATCH -> {
                player.setData(ModAttachments.RESEARCH.get(), before.withIdentified(payload.objectId()));
                player.sendSystemMessage(Component.translatable("astronima.atlas.decode.identified",
                                result.matchedLine().displayName(), payload.objectId().replace('_', ' '))
                        .withStyle(ChatFormatting.GREEN));
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.6f, 1.6f);
            }
        }
    }

    /** Owning a filter, not holding it, is the real precondition (§3, revised) — reusable
     *  equipment sits in inventory the same way a wire coil or wrench does between uses.
     *  {@code Inventory.getItem(int)} already walks the 36 main slots plus every equipment slot
     *  (offhand included) uniformly, so one indexed loop is everywhere a filter could actually be. */
    private static boolean ownsFilter(ServerPlayer player, SpectralLine line) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof FilterTokenItem token && token.line() == line) {
                return true;
            }
        }
        return false;
    }

    private static ObservationTarget parseTarget(String objectId) {
        try {
            return ObservationTarget.valueOf(objectId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notAnObject) {
            return null;
        }
    }

    private static void fail(ServerPlayer player, String reasonKey) {
        player.sendSystemMessage(Component.translatable("astronima.atlas.decode.no_match",
                        Component.translatable(reasonKey))
                .withStyle(ChatFormatting.YELLOW));
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO,
                SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
