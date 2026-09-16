package play.xponer.astronima;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import play.xponer.astronima.client.hud.HudConfig;
import play.xponer.astronima.gametest.ModTestFunctions;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.advancement.ModCriteria;
import play.xponer.astronima.gametest.GasInvariants;
import play.xponer.astronima.gametest.JeiRecipeInvariants;
import play.xponer.astronima.gametest.PlayerScenarios;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModRecipeSerializers;
import play.xponer.astronima.registry.ModCreativeTabs;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.registry.ModMenus;
import play.xponer.astronima.registry.ModParticles;
import play.xponer.astronima.registry.ModEntityTypes;
import play.xponer.astronima.registry.ModSounds;

/**
 * Astronima — science-based survival inside a C-type asteroid.
 *
 * <p>The mod entry point stays thin: content registration lives in the {@code registry}
 * package, and all atmosphere/thermodynamics logic lives in the MC-agnostic {@code sim}
 * package so it can be unit-tested without booting the game.
 */
@Mod(Astronima.MODID)
public final class Astronima {
    public static final String MODID = "astronima";

    public Astronima(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModDimensions.CHUNK_GENERATORS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModRecipeSerializers.SERIALIZERS.register(modBus);
        ModCriteria.TRIGGERS.register(modBus);
        ModTestFunctions.TEST_FUNCTIONS.register(modBus);
        PlayerScenarios.SCENARIOS.register(modBus);
        play.xponer.astronima.gametest.ResearchStageScenarios.init();
        ModEntityTypes.ENTITY_TYPES.register(modBus);
        modBus.addListener(ModEntityTypes::registerAttributes);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLE_TYPES.register(modBus);
        play.xponer.astronima.gametest.WireScenarios.init();
        GasInvariants.INVARIANTS.register(modBus);
        JeiRecipeInvariants.INVARIANTS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Panel placement is a personal preference and varies with GUI scale, so it
        // lives client-side rather than travelling with the world.
        container.registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC);
    }
}
