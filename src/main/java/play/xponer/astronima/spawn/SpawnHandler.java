package play.xponer.astronima.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.entity.ScrubberBlockEntity;
import play.xponer.astronima.item.EvaSuitItem;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.registry.ModItems;

import java.util.Set;

/**
 * The opening beat of the game: the player wakes inside the buried crew module of
 * their wrecked mining vessel — a hull-plate box entombed in asteroid rock, holding
 * the ship's vented air reserve and its salvaged emergency kit (DESIGN.md §2).
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class SpawnHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Interior of the crew module: 7×7 floor, 4 high, centered under the surface. */
    private static final BlockPos MODULE_MIN = new BlockPos(5, 24, 5);
    private static final BlockPos MODULE_MAX = new BlockPos(11, 27, 11);
    private static final BlockPos PLAYER_SPAWN = new BlockPos(8, 24, 8);

    /**
     * The EVA vestibule: a two-cell box bolted to the +X wall, with a bulkhead at each end.
     *
     * <p><strong>This is the way out, and without it the game has none.</strong> The module
     * is entombed in rock, so the only route to the surface is a tunnel — and a tunnel cut
     * straight out of a pressurised room stays connected to it, which means every block of
     * it dilutes the air the player is breathing. Measured: 6.6 mol of oxygen per block at
     * the breathable floor, against 65 blocks of slack in the module and 15 blocks per
     * candle. Digging your own way out is, quite literally, digging your own grave, and it
     * is the first thing any player tries.
     *
     * <p>Two doors and two cubic metres fix it, because that is what an airlock <em>is</em>.
     * Shut the inner door and the drift beyond is a separate room: extend it as far as you
     * like and the habitat never notices. The cost is the vestibule's own air, lost every
     * cycle — about 17 mol — which is the honest price of an airlock with no pump on it,
     * and precisely the pressure that makes the pump worth building.
     */
    private static final BlockPos VESTIBULE_FOOT = new BlockPos(13, 24, 8);

    @SubscribeEvent
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SpawnState state = SpawnState.get(player.level().getServer());
        if (state.isInitialized(player.getUUID())) {
            return;
        }
        ServerLevel asteroid = player.level().getServer().getLevel(ModDimensions.ASTEROID_LEVEL);
        if (asteroid == null) {
            LOGGER.error("Asteroid dimension missing; cannot run crash-site spawn");
            return;
        }

        buildCrewModule(asteroid);
        buildVestibule(asteroid);
        giveEmergencyKit(player);

        player.teleportTo(asteroid,
                PLAYER_SPAWN.getX() + 0.5, PLAYER_SPAWN.getY(), PLAYER_SPAWN.getZ() + 0.5,
                Set.of(), 0.0f, 0.0f, false);
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                new LevelData.RespawnData(GlobalPos.of(ModDimensions.ASTEROID_LEVEL, PLAYER_SPAWN), 0.0f, 0.0f),
                true), false);

        // The module holds the ship's vented air reserve — the only free air on the rock.
        if (!Atmosphere.get(asteroid).pressurizeWithEarthAir(PLAYER_SPAWN)) {
            LOGGER.error("Failed to pressurize the crew module at {}", PLAYER_SPAWN);
        }

        state.markInitialized(player.getUUID());
    }

    private static void buildCrewModule(ServerLevel level) {
        BlockPos shellMin = MODULE_MIN.offset(-1, -1, -1);
        BlockPos shellMax = MODULE_MAX.offset(1, 1, 1);

        // Airtight hull shell with a hollow interior.
        for (BlockPos pos : BlockPos.betweenClosed(shellMin, shellMax)) {
            boolean interior = pos.getX() >= MODULE_MIN.getX() && pos.getX() <= MODULE_MAX.getX()
                    && pos.getY() >= MODULE_MIN.getY() && pos.getY() <= MODULE_MAX.getY()
                    && pos.getZ() >= MODULE_MIN.getZ() && pos.getZ() <= MODULE_MAX.getZ();
            level.setBlockAndUpdate(pos, interior
                    ? Blocks.AIR.defaultBlockState()
                    : ModBlocks.HULL_PLATE.get().defaultBlockState());
        }

        // Salvage: a working scrubber (cartridge already seated) and one unlit candle.
        BlockPos scrubberPos = new BlockPos(MODULE_MIN.getX(), MODULE_MIN.getY(), MODULE_MIN.getZ());
        level.setBlockAndUpdate(scrubberPos, ModBlocks.SCRUBBER.get().defaultBlockState());
        if (level.getBlockEntity(scrubberPos) instanceof ScrubberBlockEntity scrubber) {
            scrubber.preloadCartridge();
        }
        level.setBlockAndUpdate(new BlockPos(MODULE_MAX.getX(), MODULE_MIN.getY(), MODULE_MAX.getZ()),
                ModBlocks.OXYGEN_CANDLE.get().defaultBlockState());

        // The workshop corner: no wood grows here, so the ship's stations must survive.
        level.setBlockAndUpdate(new BlockPos(MODULE_MIN.getX(), MODULE_MIN.getY(), MODULE_MAX.getZ()),
                Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(MODULE_MAX.getX(), MODULE_MIN.getY(), MODULE_MIN.getZ()),
                Blocks.FURNACE.defaultBlockState());
    }

    /**
     * Cuts the vestibule out of the rock against the module's +X wall.
     *
     * <p>Built as a solid hull box that is then hollowed, rather than as a hole in the rock:
     * whatever the worldgen happened to put here — void, ore, a natural cavity — the
     * vestibule has to be airtight on every face or it is not an airlock, it is a leak with
     * a door on it.
     */
    private static void buildVestibule(ServerLevel level) {
        BlockPos inner = VESTIBULE_FOOT.west();   // the module's +X wall
        BlockPos outer = VESTIBULE_FOOT.east();   // the rock face

        for (BlockPos pos : BlockPos.betweenClosed(
                VESTIBULE_FOOT.offset(0, -1, -1), outer.offset(0, 2, 1))) {
            level.setBlockAndUpdate(pos.immutable(),
                    ModBlocks.HULL_PLATE.get().defaultBlockState());
        }
        level.setBlockAndUpdate(VESTIBULE_FOOT, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(VESTIBULE_FOOT.above(), Blocks.AIR.defaultBlockState());

        // Both bulkheads start shut, so the module is sealed the moment the player wakes
        // and the vestibule is the vacuum it is supposed to be.
        placeDoor(level, inner);
        placeDoor(level, outer);
    }

    private static void placeDoor(ServerLevel level, BlockPos foot) {
        // FACING is never left to its class default (NORTH): the vestibule's corridor runs
        // east-west, and a door facing north renders swung across the opening instead of
        // across the wall — every bit as sealed to the sim (AirBlockKinds only reads OPEN),
        // but it looks wrong/broken to the player standing in front of it, and "the door
        // looks open" was reported straight from real play.
        net.minecraft.world.level.block.state.BlockState shut = ModBlocks.BULKHEAD_DOOR.get()
                .defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST)
                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false);
        level.setBlockAndUpdate(foot, shut);
        level.setBlockAndUpdate(foot.above(), shut.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                DoubleBlockHalf.UPPER));
    }

    private static void giveEmergencyKit(ServerPlayer player) {
        // You crawl out of the wreck in a suit that barely works. Repairing it is
        // the first thing worth doing, so one patch comes with it as a starting point.
        player.getInventory().add(EvaSuitItem.wrecked(ModItems.EVA_SUIT.get()));
        // Both parts the suit needs to hold pressure at all. A patch alone leaves the
        // tank mount broken, so the tank on your back feeds you nothing — which reads
        // as an unexplained suffocation rather than a repair you have not done yet.
        player.getInventory().add(new ItemStack(ModItems.SEALANT_PATCH.get()));
        player.getInventory().add(new ItemStack(ModItems.LATCH_SET.get()));
        // A sealed suit with nothing in the tank is not life support. One charged
        // tank, so the first walk outside is possible rather than theoretical.
        player.getInventory().add(new ItemStack(ModItems.OXYGEN_TANK.get()));
        player.getInventory().add(new ItemStack(ModItems.GAS_ANALYZER.get()));
        player.getInventory().add(new ItemStack(ModItems.OXYGEN_CANDLE.get(), 4));
        // Cartridges are fitted one at a time and no longer stack, so the kit carries
        // a spare as a second item rather than a stack of three.
        player.getInventory().add(new ItemStack(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get()));
        player.getInventory().add(new ItemStack(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get()));
        player.getInventory().add(new ItemStack(ModItems.HULL_PLATE.get(), 24));
        player.getInventory().add(new ItemStack(ModItems.BULKHEAD_DOOR.get()));
        player.getInventory().add(new ItemStack(ModItems.PRY_BAR.get()));
        player.getInventory().add(new ItemStack(Items.TORCH, 16));
        player.getInventory().add(new ItemStack(Items.BREAD, 8));
        player.getInventory().add(new ItemStack(ModItems.THOLIN_CLUMP.get(), 8));
        player.getInventory().add(new ItemStack(ModItems.IRON_ROD.get(), 4));
        player.getInventory().add(new ItemStack(ModItems.SEISMIC_PROBE.get()));
    }

    private SpawnHandler() {}
}
