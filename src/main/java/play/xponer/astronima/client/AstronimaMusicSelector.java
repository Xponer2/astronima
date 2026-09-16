package play.xponer.astronima.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.Music;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModSounds;

import java.util.EnumMap;
import java.util.Map;

/**
 * Reads the real game state {@link MusicSelection} needs and hands its answer to vanilla's own
 * {@link net.minecraft.client.sounds.MusicManager} through {@link SelectMusicEvent} — the
 * situational-music hook, which fires for the main menu exactly as it does in a world, so one
 * mechanism covers both (rule 20).
 *
 * <h2>Why the pick is stable per bucket, not re-rolled every event</h2>
 * {@code Music}'s own {@code minDelay}/{@code maxDelay} already spaces songs out; picking a
 * different track from a bucket's pool on every firing would fight that spacing and could
 * restart selection before anything ever played a full loop. So each bucket's choice is made
 * once per client session, the first time that bucket is needed, and held — see
 * {@link MusicSelection#pick}.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class AstronimaMusicSelector {

    /** How far to look for a magic vein (a water-ice block) around the player. */
    private static final int VEIN_SCAN_RADIUS = 6;
    /** Below this Y, "no open sky" reads as genuinely deep rather than a shallow, sealed room. */
    private static final int DEEP_UNDERGROUND_MAX_Y = 32;
    /** Block scans are not free; the expensive half of the situation is refreshed on a cadence,
     * not every tick — long enough to be cheap, short enough that arriving somewhere new is
     * still noticed within a couple of seconds. */
    private static final int RECHECK_INTERVAL_TICKS = 40;

    private static final RandomSource SESSION_RANDOM = RandomSource.create();
    private static final Map<MusicSelection.Bucket, Integer> SESSION_PICK =
            new EnumMap<>(MusicSelection.Bucket.class);

    private static boolean cachedNearVein = false;
    private static boolean cachedDeepUnderground = false;
    private static long lastRecheckGameTime = Long.MIN_VALUE;

    @SubscribeEvent
    private static void onSelectMusic(SelectMusicEvent event) {
        MusicSelection.Situation situation = situationOf(Minecraft.getInstance());
        MusicSelection.Bucket bucket = MusicSelection.bucketFor(situation);
        int index = SESSION_PICK.computeIfAbsent(bucket, b -> SESSION_RANDOM.nextInt(1_000_000));
        event.setMusic(musicFor(MusicSelection.pick(bucket, index)));
    }

    private static MusicSelection.Situation situationOf(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return new MusicSelection.Situation(true, false, false, false, false);
        }
        refreshExpensiveChecksIfDue(level, player);
        BlockPos pos = player.blockPosition();
        boolean hasOpenSky = level.canSeeSky(pos);
        boolean isDaytime = level.getSkyDarken() < 4; // the same threshold SkyExposure uses
        return new MusicSelection.Situation(
                false, hasOpenSky, isDaytime, cachedNearVein, cachedDeepUnderground);
    }

    private static void refreshExpensiveChecksIfDue(ClientLevel level, LocalPlayer player) {
        long now = level.getGameTime();
        if (lastRecheckGameTime != Long.MIN_VALUE && now - lastRecheckGameTime < RECHECK_INTERVAL_TICKS) {
            return;
        }
        lastRecheckGameTime = now;
        BlockPos origin = player.blockPosition();
        cachedDeepUnderground = origin.getY() < DEEP_UNDERGROUND_MAX_Y;
        cachedNearVein = false;
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-VEIN_SCAN_RADIUS, -VEIN_SCAN_RADIUS, -VEIN_SCAN_RADIUS),
                origin.offset(VEIN_SCAN_RADIUS, VEIN_SCAN_RADIUS, VEIN_SCAN_RADIUS))) {
            if (level.getBlockState(candidate).is(ModBlocks.WATER_ICE.get())) {
                cachedNearVein = true;
                break;
            }
        }
    }

    // ------------------------------------------------------------------ tuning, per bucket

    private static final int MENU_MIN_DELAY = 200;    // 10 s
    private static final int MENU_MAX_DELAY = 1200;   // 60 s
    private static final int AMBIENT_MIN_DELAY = 6000;  // 5 min
    private static final int AMBIENT_MAX_DELAY = 14000; // ~11.7 min
    private static final int DANGEROUS_MIN_DELAY = 4000; // ~3.3 min
    private static final int DANGEROUS_MAX_DELAY = 9000; // 7.5 min
    private static final int VEIN_MIN_DELAY = 2000; // ~1.7 min
    private static final int VEIN_MAX_DELAY = 6000; // 5 min

    private static Music musicFor(MusicSelection.Track track) {
        return switch (track) {
            case DEEP_SPACE_DRIFT -> new Music(ModSounds.MUSIC_DEEP_SPACE_DRIFT,
                    MENU_MIN_DELAY, MENU_MAX_DELAY, true);
            case QUIET_SPACE_VOID -> new Music(ModSounds.MUSIC_QUIET_SPACE_VOID,
                    MENU_MIN_DELAY, MENU_MAX_DELAY, true);
            case VAST_SILENCE -> new Music(ModSounds.MUSIC_VAST_SILENCE,
                    MENU_MIN_DELAY, MENU_MAX_DELAY, true);
            case COSMIC_SURVIVAL -> new Music(ModSounds.MUSIC_COSMIC_SURVIVAL,
                    AMBIENT_MIN_DELAY, AMBIENT_MAX_DELAY, false);
            case NIGHT_SPACE_AMBIENT -> new Music(ModSounds.MUSIC_NIGHT_SPACE_AMBIENT,
                    AMBIENT_MIN_DELAY, AMBIENT_MAX_DELAY, false);
            case QUIET_COSMIC_PADS -> new Music(ModSounds.MUSIC_QUIET_COSMIC_PADS,
                    AMBIENT_MIN_DELAY, AMBIENT_MAX_DELAY, false);
            // Arriving somewhere notable is worth an immediate change rather than waiting for
            // whatever ambient loop happened to be running (rule 7's shape, applied to mood: the
            // instrument for "you have found something" has to actually announce it).
            case COSMIC_VOID -> new Music(ModSounds.MUSIC_COSMIC_VOID,
                    DANGEROUS_MIN_DELAY, DANGEROUS_MAX_DELAY, true);
            case CRYSTAL_CAVE -> new Music(ModSounds.MUSIC_CRYSTAL_CAVE,
                    VEIN_MIN_DELAY, VEIN_MAX_DELAY, true);
        };
    }

    private AstronimaMusicSelector() {}
}
