package play.xponer.astronima.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.data.SoundDefinition;
import net.neoforged.neoforge.common.data.SoundDefinitionsProvider;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModSounds;

/**
 * Generates {@code sounds.json} from {@link ModSounds} — datagen owns the JSON (rule 3). The
 * file this replaced was six hand-written entries aliased to vanilla sounds that no
 * {@code DeferredRegister} ever registered (design/astra-incognita.md §1 finding 11); every
 * entry here corresponds to a real, registered {@code SoundEvent}, checked by
 * {@code SoundCoverageTest}.
 */
public final class ModSoundDefinitionsProvider extends SoundDefinitionsProvider {
    public ModSoundDefinitionsProvider(PackOutput output) {
        super(output, Astronima.MODID);
    }

    @Override
    public void registerSounds() {
        track(ModSounds.MUSIC_DEEP_SPACE_DRIFT, "deep_space_drift");
        track(ModSounds.MUSIC_QUIET_SPACE_VOID, "quiet_space_void");
        track(ModSounds.MUSIC_VAST_SILENCE, "vast_silence");
        track(ModSounds.MUSIC_COSMIC_SURVIVAL, "cosmic_survival");
        track(ModSounds.MUSIC_NIGHT_SPACE_AMBIENT, "night_space_ambient");
        track(ModSounds.MUSIC_QUIET_COSMIC_PADS, "quiet_cosmic_pads");
        track(ModSounds.MUSIC_COSMIC_VOID, "cosmic_void");
        track(ModSounds.MUSIC_CRYSTAL_CAVE, "crystal_cave");
    }

    /**
     * Every entry here is a music track: streamed rather than fully buffered (they run minutes
     * long), and each is its own event with one take, not a weighted pool. The subtitle key
     * follows vanilla's {@code subtitles.<path>} convention; {@link ModLanguageProvider} supplies
     * the English text and {@code ru_ru.json} the Russian.
     *
     * <p><strong>The namespace has to be explicit.</strong> {@code sound(String)} parses its
     * argument as an {@link Identifier}, and an id with no colon defaults to {@code minecraft:} —
     * verified the hard way, by every track failing to load with "File
     * minecraft:sounds/music/....ogg does not exist" on first boot. {@link #sound(Identifier)}
     * does not have that failure mode.
     */
    private void track(net.neoforged.neoforge.registries.DeferredHolder<
            net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> event, String name) {
        add(event.get(), definition()
                .subtitle("subtitles.astronima.music." + name)
                .with(sound(Identifier.fromNamespaceAndPath(Astronima.MODID, "music/" + name))
                        .stream()));
    }
}
