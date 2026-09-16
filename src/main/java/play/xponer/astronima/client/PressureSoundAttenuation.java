package play.xponer.astronima.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.Acoustics;

import java.util.concurrent.CompletableFuture;

/**
 * Vacuum carries no sound (design/presentation.md §4.1). Every sound that is part of the world
 * — footsteps, machinery, mobs, ambience — is scaled by how much air is around the
 * <strong>local player's</strong> head right now; menus, music and records are not, because
 * they are not happening in the room.
 *
 * <h2>One mechanism, not fourteen (rule 20)</h2>
 * This does not touch a single {@code playSound} call site. {@link PlaySoundEvent} fires for
 * every sound the client is about to play, from any source — network-received, locally
 * triggered, vanilla or ours — so wrapping it here covers a phantom's shriek exactly like the
 * mod's own machinery. Per-sound handling is how half a mod ends up loud in vacuum.
 *
 * <h2>Same door, same number (rule 13)</h2>
 * Reads {@link ModAttachments#LAST_AMBIENT_KPA} — the field {@code AtmosphereEvents} already
 * keeps current every tick so barotrauma can see a pressure <em>rate</em>. A second, separately
 * computed "ambient pressure for audio" would be a second thing that could disagree with the
 * one already driving decompression damage.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class PressureSoundAttenuation {

    @SubscribeEvent
    private static void onPlaySound(PlaySoundEvent event) {
        SoundInstance original = event.getSound();
        if (original == null || !DiegeticSound.isDiegetic(original.getSource().getName())) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double factor = Acoustics.pressureVolumeFactor(player.getData(ModAttachments.LAST_AMBIENT_KPA));
        if (factor >= 1.0) {
            return; // full pressure: nothing to attenuate, no wrapper needed
        }
        event.setSound(new Attenuated(original, (float) factor));
    }

    /** Forwards everything but the volume, scaled by how much air is around the listener. */
    private record Attenuated(SoundInstance delegate, float factor) implements SoundInstance {
        @Override
        public Identifier getIdentifier() {
            return delegate.getIdentifier();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return delegate.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return delegate.getSound();
        }

        @Override
        public SoundSource getSource() {
            return delegate.getSource();
        }

        @Override
        public boolean isLooping() {
            return delegate.isLooping();
        }

        @Override
        public boolean isRelative() {
            return delegate.isRelative();
        }

        @Override
        public int getDelay() {
            return delegate.getDelay();
        }

        @Override
        public float getVolume() {
            return delegate.getVolume() * factor;
        }

        @Override
        public float getPitch() {
            return delegate.getPitch();
        }

        @Override
        public double getX() {
            return delegate.getX();
        }

        @Override
        public double getY() {
            return delegate.getY();
        }

        @Override
        public double getZ() {
            return delegate.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return delegate.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return delegate.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return delegate.canPlaySound();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(
                SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
            return delegate.getStream(soundBuffers, sound, looping);
        }
    }

    private PressureSoundAttenuation() {}
}
