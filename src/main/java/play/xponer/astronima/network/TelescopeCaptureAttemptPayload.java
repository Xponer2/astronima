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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import play.xponer.astronima.atmosphere.SkyExposure;
import play.xponer.astronima.block.TelescopeBlock;
import play.xponer.astronima.item.SpectrographItem;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.magic.Claims;
import play.xponer.astronima.sim.magic.ObservationTarget;
import play.xponer.astronima.sim.magic.ResearchState;
import play.xponer.astronima.sim.magic.Spectrum;
import play.xponer.astronima.sim.optics.NamedSkyObjects;
import play.xponer.astronima.sim.optics.SkyRotation;
import play.xponer.astronima.sim.optics.TelescopeMount;
import play.xponer.astronima.telescope.TelescopeMountEntity;
import play.xponer.astronima.telescope.TelescopeOcclusion;

/**
 * "I've held this telescope on target long enough — write it down." The client only reports that
 * its own local hold-timer completed ({@code client.TelescopeHud}); the server independently
 * re-derives everything that made the hold real — occlusion, angle, daylight — before ever
 * touching {@code ResearchState}, the same trust boundary {@code ClaimResolvePayload} already
 * keeps for the atlas (design/astra-telescope.md §4): a client that could report its own capture
 * could report one it never actually earned.
 *
 * <p>design/astra-telescope.md §2.1's v3 mechanism (PLAN.md rule 79's correction away from
 * riding): the aim lives on the server's own {@link TelescopeMountEntity}, kept fresh by
 * {@link TelescopeAimUpdatePayload} — never trusted outright even there, since that handler
 * already re-derives {@link TelescopeMount#clamp} itself before ever writing it. This handler
 * clamps the mount's *current* rotation again regardless, the same "do not trust a value just
 * because it was clamped once already" caution occlusion and the target lookup are already
 * re-derived under.
 */
public record TelescopeCaptureAttemptPayload(BlockPos telescopePos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TelescopeCaptureAttemptPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath("astronima", "telescope_capture"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelescopeCaptureAttemptPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBlockPos(payload.telescopePos),
                    buffer -> new TelescopeCaptureAttemptPayload(buffer.readBlockPos()));

    private static final SkyRotation.Vec3 BASE_UP = new SkyRotation.Vec3(0.0, 1.0, 0.0);

    public static void apply(TelescopeCaptureAttemptPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // A capture is only real while genuinely observing *this* telescope — a stronger check
        // than the old reach radius, and unaffected by dropping the old ridden-passenger mechanism
        // (design/astra-telescope.md §7's registry table): the session itself is still tracked
        // server-side, just via TelescopeMountEntity.isObservedBy rather than a vehicle relation.
        TelescopeMountEntity mount = TelescopeMountEntity.findAt(level, payload.telescopePos());
        if (mount == null || !mount.isObservedBy(player)) {
            return;
        }
        if (!(level.getBlockState(payload.telescopePos()).getBlock() instanceof TelescopeBlock)) {
            return;
        }

        Vec3 mountLook = mount.getLookAngle();
        SkyRotation.Vec3 claimed = new SkyRotation.Vec3(mountLook.x(), mountLook.y(), mountLook.z());
        SkyRotation.Vec3 direction =
                TelescopeMount.clamp(claimed, BASE_UP, TelescopeMount.GROUND_TRIPOD);
        Vec3 look = new Vec3(direction.x(), direction.y(), direction.z());
        Vec3 eyepiece = Vec3.atCenterOf(payload.telescopePos()).add(0.0, 0.5, 0.0);
        if (!TelescopeOcclusion.isClear(level, eyepiece, look)) {
            fail(player, "the sky's blocked from here");
            return;
        }

        ObservationTarget target = resolveTarget(level, payload.telescopePos(), direction);
        if (target == null) {
            fail(player, "nothing in view right now");
            return;
        }

        String objectId = Claims.objectId(target);
        ResearchState before = player.getData(ModAttachments.RESEARCH.get());
        boolean alreadyKnown = before.captured(objectId);
        player.setData(ModAttachments.RESEARCH.get(), before.withCaptured(objectId));
        announce(player, level, target, alreadyKnown);
    }

    /** A named deep-sky object first; failing that, the Sun itself if it is actually up — the same
     * dual path {@code SpectrographItem} used, kept rather than narrowed. */
    private static ObservationTarget resolveTarget(ServerLevel level, BlockPos telescopePos,
                                                    SkyRotation.Vec3 direction) {
        NamedSkyObjects.Placement aimed = NamedSkyObjects.lookedAt(direction, level.getGameTime());
        if (aimed != null) {
            return aimed.target();
        }
        if (SkyExposure.sunlightAt(level, telescopePos) > 0.0) {
            return ObservationTarget.SUN;
        }
        return null;
    }

    private static void fail(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal("Lost the lock — " + reason)
                .withStyle(ChatFormatting.YELLOW));
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO,
                SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    /** Real feedback on every completed lock, per rule 18 — exactly the complaint the old plate
     * flow earned ("clicked everywhere, nothing happens") answered the same way
     * {@code ClaimResolvePayload.announce} already answers it for a claim resolve. */
    private static void announce(ServerPlayer player, ServerLevel level, ObservationTarget target,
                                 boolean alreadyKnown) {
        if (!alreadyKnown) {
            player.sendSystemMessage(Component.literal("Captured: "
                            + target.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '))
                    .withStyle(ChatFormatting.GREEN));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.6f, 1.6f);
        SpectrographItem.spawnDispersionFan(level, player,
                Spectrum.capture(target.temperatureK(), target.lines(), 0.0));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
