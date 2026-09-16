package play.xponer.astronima.client;

import java.util.Set;

/**
 * Which sound categories are part of the world, and therefore muffled by vacuum
 * ({@link PressureSoundAttenuation}) — kept Minecraft-free so the classification is unit
 * testable at all (rule 25). {@code net.minecraft.sounds.SoundSource} is not on the test
 * classpath in this project by design, the same reason every model behind a screen lives
 * outside a Minecraft class; this one keys off {@code SoundSource#getName()} instead.
 *
 * <p><strong>The trade-off, stated rather than hidden (rule 33):</strong> a switch over the
 * real enum would fail to compile the day it grew an eleventh member, which this cannot. What
 * this buys instead is a list {@link PressureSoundAttenuationTest} keeps honest against every
 * name the enum reports today — if a new category appears unclassified, that test's coverage
 * check catches it the next time someone runs the mod-name list past it, not the compiler.
 */
final class DiegeticSound {
    /** Global, UI and jukebox audio is not happening in the room and vacuum cannot muffle it. */
    private static final Set<String> NON_DIEGETIC = Set.of("master", "music", "record", "ui");

    static boolean isDiegetic(String sourceName) {
        return !NON_DIEGETIC.contains(sourceName);
    }

    private DiegeticSound() {}
}
