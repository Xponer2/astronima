package play.xponer.astronima.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import play.xponer.astronima.atmosphere.SkyExposure;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.magic.CapturedSpectrum;
import play.xponer.astronima.sim.magic.Claims;
import play.xponer.astronima.sim.magic.ObservationTarget;
import play.xponer.astronima.sim.magic.ResearchState;
import play.xponer.astronima.sim.magic.SpectralLine;
import play.xponer.astronima.sim.magic.Spectrum;
import play.xponer.astronima.sim.magic.WavelengthColor;
import play.xponer.astronima.sim.optics.NamedSkyObjects;
import play.xponer.astronima.sim.optics.SkyRotation;

import java.util.Locale;
import java.util.SortedSet;

/**
 * The instrument, design/astra-incognita.md §8.1. Right-click with a blank
 * {@link SpectralPlateItem} in the off hand: aimed within {@link NamedSkyObjects}'s tolerance of
 * a named deep-sky object (design/sky.md L4 — {@link ObservationTarget#ORION_NEBULA} is the
 * first one this mod places), that object is captured regardless of the hour; otherwise, under
 * open sky in daylight, the Sun. An RTG does not exist in the mod yet and no ice-lens block does
 * either, so {@link ObservationTarget#RTG_GLOW} and {@link ObservationTarget#ICE_LENS} stay
 * debug-command-only until they do (§14.5).
 *
 * <p><strong>Simplified from the design, named rather than hidden (rule 8):</strong> a real
 * exposure takes time and can be spoiled by moving the instrument; this captures instantly on a
 * successful attempt. The Sun is bright enough that this is not a large lie, and the timed,
 * spoilable version is exactly the kind of refinement worth doing once a fainter target exists
 * to make the difference matter.
 */
public class SpectrographItem extends Item {

    public SpectrographItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            // Fires once per click, on the hand the item is actually in - not once per hand,
            // which would run the exposure twice for a single press (rule 25's second sighting:
            // the client tries both hands and a handler that answers for the wrong one runs twice).
            return InteractionResult.PASS;
        }
        ItemStack plate = player.getOffhandItem();
        if (!(plate.getItem() instanceof SpectralPlateItem) || SpectralPlateItem.isExposed(plate)) {
            player.sendSystemMessage(Component.translatable("astronima.spectrograph.need_blank_plate")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        // Checked before the daylight gate below: an airless sky has no reason to hide a deep-sky
        // object at any hour (design/sky.md §1's own "stars at noon" point, applied to targeting
        // rather than just the view) - only the Sun's own capture still needs daylight, since
        // that is a real precondition (nothing to disperse from a Sun below the horizon), not an
        // arbitrary one.
        Vec3 look = player.getLookAngle();
        NamedSkyObjects.Placement aimed = NamedSkyObjects.lookedAt(
                new SkyRotation.Vec3(look.x(), look.y(), look.z()), serverLevel.getGameTime());
        if (aimed != null) {
            capture(serverLevel, player, plate, aimed.target());
            return InteractionResult.SUCCESS;
        }

        if (SkyExposure.sunlightAt(serverLevel, player.blockPosition()) <= 0.0) {
            player.sendSystemMessage(Component.translatable("astronima.spectrograph.no_target")
                    .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        capture(serverLevel, player, plate, ObservationTarget.SUN);
        return InteractionResult.SUCCESS;
    }

    /** Real vacuum, honestly, for every target this tier reaches: nothing here is ever behind
     * glass, so 0.0 kPa is the correct ambient pressure at every capture, not a placeholder — the
     * reason {@link ObservationTarget#ORION_NEBULA}'s own forbidden line shows up the first time
     * anyone points at it, with no separate vacuum plumbing needed. */
    private static void capture(ServerLevel serverLevel, Player player, ItemStack plate,
                                ObservationTarget target) {
        CapturedSpectrum captured = new CapturedSpectrum(target, 0.0);
        player.setItemInHand(InteractionHand.OFF_HAND,
                SpectralPlateItem.exposedCopy(plate, captured));

        // Registers the same real fact TelescopeCaptureAttemptPayload's own capture does (rule
        // 46: one fact lives in one place). Without this, the plate looks captured but
        // ResearchState never heard about it, so SpectrumDecodeAttemptPayload's own
        // before.captured(objectId) guard silently refuses every decode - a player using only
        // this instrument made no real progress and was never told why.
        String objectId = Claims.objectId(target);
        ResearchState before = player.getData(ModAttachments.RESEARCH.get());
        boolean alreadyKnown = before.captured(objectId);
        player.setData(ModAttachments.RESEARCH.get(), before.withCaptured(objectId));
        if (!alreadyKnown) {
            player.sendSystemMessage(Component.literal("Captured: "
                            + target.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                    .withStyle(ChatFormatting.GREEN));
        }

        serverLevel.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(),
                SoundSource.PLAYERS, 0.6f, 1.9f);
        spawnDispersionFan(serverLevel, player,
                Spectrum.capture(target.temperatureK(), target.lines(), 0.0));
    }

    /**
     * The one visual this tier ships so far, and it is a gauge wearing particle effects rather
     * than decoration (design/presentation.md §1, design/astra-incognita.md §9.1): one coloured
     * dust particle cluster per captured line, laid out left-to-right by wavelength the way a
     * real dispersed spectrum reads, each coloured by {@link WavelengthColor} — the same physics
     * the plate's tooltip already reports as numbers, this time as light.
     */
    private static final double VISIBLE_MIN_NM = 380.0;
    private static final double VISIBLE_MAX_NM = 780.0;
    private static final double FAN_WIDTH_BLOCKS = 1.6;

    /** Shared with {@code network.TelescopeCaptureAttemptPayload} — one dispersion-fan visual for
     * every real capture in this tier, whichever instrument earned it (rule 46). */
    public static void spawnDispersionFan(
            ServerLevel level, Player player, SortedSet<SpectralLine> lines) {
        if (lines.isEmpty()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z(), 0.0, look.x());
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1.0, 0.0, 0.0); // looking straight up or down: any horizontal will do
        }
        right = right.normalize();

        double eyeY = player.getEyeY() + 0.3;
        for (SpectralLine line : lines) {
            double t = Math.clamp(
                    (line.wavelengthNm() - VISIBLE_MIN_NM) / (VISIBLE_MAX_NM - VISIBLE_MIN_NM),
                    0.0, 1.0);
            double offset = (t - 0.5) * FAN_WIDTH_BLOCKS;
            double x = player.getX() + right.x() * offset;
            double z = player.getZ() + right.z() * offset;

            DustParticleOptions options = new DustParticleOptions(
                    WavelengthColor.rgb(line.wavelengthNm()), 1.2f);
            level.sendParticles(options, x, eyeY, z, 6, 0.04, 0.04, 0.04, 0.0);
        }
    }
}
