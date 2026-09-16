package play.xponer.astronima.client.particle;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModParticles;

/** Wires {@link ModParticles}' types to the client classes that actually draw them. */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class ModParticleProviders {
    @SubscribeEvent
    private static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.INCANDESCENCE.get(), IncandescenceParticle.Provider::new);
        event.registerSpriteSet(ModParticles.REGOLITH.get(), RegolithParticle.Provider::new);
    }

    private ModParticleProviders() {}
}
