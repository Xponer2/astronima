package play.xponer.astronima.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;

/**
 * Registered sound events — design/presentation.md §4.3's infrastructure, actually built this
 * time. The previous {@code sounds.json} was six hand-written entries registered by nothing
 * (design/astra-incognita.md §1 finding 11); this is the replacement, and datagen owns the JSON
 * from here (rule 3).
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Astronima.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_DEEP_SPACE_DRIFT =
            register("music.deep_space_drift");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_QUIET_SPACE_VOID =
            register("music.quiet_space_void");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_VAST_SILENCE =
            register("music.vast_silence");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_COSMIC_SURVIVAL =
            register("music.cosmic_survival");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_NIGHT_SPACE_AMBIENT =
            register("music.night_space_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_QUIET_COSMIC_PADS =
            register("music.quiet_cosmic_pads");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_COSMIC_VOID =
            register("music.cosmic_void");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_CRYSTAL_CAVE =
            register("music.crystal_cave");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(Astronima.MODID, path)));
    }

    private ModSounds() {}
}
