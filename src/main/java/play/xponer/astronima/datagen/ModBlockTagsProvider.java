package play.xponer.astronima.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModBlocks;

import java.util.concurrent.CompletableFuture;

/**
 * Tool tags. Without {@code mineable/pickaxe} a pickaxe gives no speed bonus at all —
 * which is why early asteroid rock felt like digging with bare hands.
 */
public class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Astronima.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.ASTEROID_ROCK.get(),
                ModBlocks.WATER_ICE.get(),
                ModBlocks.CHLORATE_ORE.get(),
                ModBlocks.HEMATITE_ORE.get(),
                        ModBlocks.METAL_RICH_ORE.get(),
                        ModBlocks.ORE_CRUSHER.get(),
                        ModBlocks.MAGNETIC_SEPARATOR.get(),
                        ModBlocks.WINNOWING_TABLE.get(),
                        ModBlocks.CARGO_CRATE.get(),
                        ModBlocks.COLD_FORGE.get(),
                        ModBlocks.PACKED_TAILINGS.get(),
                ModBlocks.HULL_PLATE.get(),
                ModBlocks.INSULATED_HULL_PLATE.get(),
                ModBlocks.GRAB_RAIL.get(),
                ModBlocks.PURGE_VALVE.get(),
                ModBlocks.SOLAR_ARRAY.get(),
                ModBlocks.POWER_CELL.get(),
                ModBlocks.POWER_CABLE.get(),
                ModBlocks.COMBUSTION_GENERATOR.get(),
                ModBlocks.FUEL_CELL.get(),
                ModBlocks.CARBONYL_REFINER.get(),
                ModBlocks.FLUIDIZED_BED.get(),
                ModBlocks.ELECTROLYSIS_CELL.get(),
                ModBlocks.SLS_PRINTER.get(),
                ModBlocks.CRACKING_TOWER.get(),
                ModBlocks.POLYMERIZER.get(),
                ModBlocks.WATER_ELECTROLYZER.get(),
                ModBlocks.SABATIER_REACTOR.get(),
                ModBlocks.BOSCH_REACTOR.get(),
                ModBlocks.TROILITE_ROASTER.get(),
                ModBlocks.HALITE_ORE.get(),
                ModBlocks.DOWNS_CELL.get(),
                ModBlocks.SULFURIC_ACID_PLANT.get(),
                ModBlocks.HEAVY_WATER_CELL.get(),
                ModBlocks.TITANIUM_CELL.get(),
                ModBlocks.ZONE_REFINER.get(),
                ModBlocks.FLUORITE_ORE.get(),
                ModBlocks.HF_DIGESTER.get(),
                ModBlocks.STORAGE_FRAME.get(),
                ModBlocks.STORAGE_DRIVE.get(),
                ModBlocks.STORAGE_TERMINAL.get(),
                ModBlocks.INDUCTION_FURNACE.get(),
                ModBlocks.IRON_SMELTER.get(),
                ModBlocks.VR_SIMULATION_POD.get(),
                ModBlocks.AMMONIA_HEAT_PIPE.get(),
                ModBlocks.ILMENITE_ORE.get(),
                ModBlocks.BULKHEAD_DOOR.get(),
                ModBlocks.SCRUBBER.get(),
                ModBlocks.OXYGEN_CANDLE.get(),
                ModBlocks.ALARM.get(),
                ModBlocks.PRE_BREATHE_STATION.get(),
                ModBlocks.DEHUMIDIFIER.get(),
                ModBlocks.ASTERIUM_BLOCK.get(),
                ModBlocks.ASTRA_ALTAR.get(),
                ModBlocks.CLEANROOM_CONTROLLER.get(),
                ModBlocks.ETCH_STATION.get(),
                ModBlocks.GRAPHITIZER.get(),
                ModBlocks.ALGAE_BIOREACTOR.get(),
                ModBlocks.ANAEROBIC_DIGESTER.get());
        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(ModBlocks.REGOLITH.get());

        // The grab rail's entire reason to exist. Reported as "they do nothing", and they did
        // nothing: in this line every scrap of ladder behaviour is tag-driven, not class-driven.
        // NeoForge's own default is `IBlockExtension.isLadder -> state.is(BlockTags.CLIMBABLE)`,
        // LadderBlock does not override it, and LivingEntity.onClimbable routes through
        // CommonHooks.isLivingOnLadder to exactly that call. So `extends LadderBlock` bought the
        // model, the FACING state and the wall placement - and none of the clinging, none of the
        // climbing, and none of vanilla's fall-forgiveness. A rail was a decoration shaped like a
        // ladder. Verified against the decompiled sources rather than recalled (rule 2).
        tag(BlockTags.CLIMBABLE).add(ModBlocks.GRAB_RAIL.get());

        // Deliberately no needs_*_tool tags: the softlock invariant (PLAN §1.0.2)
        // guarantees everything drops bare-handed; tags only ever affect speed.
    }
}
