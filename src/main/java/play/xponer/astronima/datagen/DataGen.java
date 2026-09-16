package play.xponer.astronima.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import play.xponer.astronima.Astronima;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = Astronima.MODID)
public final class DataGen {
    @SubscribeEvent
    private static void onGatherClientData(GatherDataEvent.Client event) {
        event.createProvider(ModModelProvider::new);
        event.createProvider(ModLanguageProvider::new);
        event.createProvider(ModBlockTagsProvider::new);
        event.createProvider(ModSoundDefinitionsProvider::new);

        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();
        event.addProvider(new LootTableProvider(
                event.getGenerator().getPackOutput(),
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLoot::new, LootContextParamSets.BLOCK)),
                registries));
        event.addProvider(new ModRecipeProvider.Runner(event.getGenerator().getPackOutput(), registries));
        event.addProvider(new ModCuriosProvider(Astronima.MODID,
                event.getGenerator().getPackOutput(), registries));
        // The advancement tree, which is what tells a player any of this exists.
        event.addProvider(new net.minecraft.data.advancements.AdvancementProvider(
                event.getGenerator().getPackOutput(), registries,
                List.of(new ModAdvancements())));
    }

    private DataGen() {}
}
