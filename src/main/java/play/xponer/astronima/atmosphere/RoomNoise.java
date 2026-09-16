package play.xponer.astronima.atmosphere;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import play.xponer.astronima.block.AlarmBlock;
import play.xponer.astronima.block.OxygenCandleBlock;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.Acoustics;

import java.util.ArrayList;
import java.util.List;

/**
 * Sound-source survey of a room: finds running machinery in and bordering the room,
 * then works out what a listener actually hears using real distance attenuation — a
 * point source loses 6 dB per doubling of distance, so a compressor across the
 * hangar is not the same as one beside your bunk.
 *
 * <p>Gathering the source list walks the room, so callers cache it on the room-scan
 * cadence; mixing it for a given listener position is cheap.
 */
final class RoomNoise {
    /** A machine and its rated level at the reference distance. */
    record Source(long packedPos, double db) {}

    static List<Source> survey(ServerLevel level, Room room) {
        // A wall machine borders several open cells; count each source position once.
        LongOpenHashSet counted = new LongOpenHashSet();
        List<Source> sources = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (LongIterator it = room.cells.iterator(); it.hasNext(); ) {
            cursor.set(it.nextLong());
            if (counted.add(cursor.asLong())) {
                addIfAudible(level, cursor, sources);
            }
            for (Direction direction : Direction.values()) {
                neighbor.setWithOffset(cursor, direction);
                if (!room.cells.contains(neighbor.asLong()) && counted.add(neighbor.asLong())) {
                    addIfAudible(level, neighbor, sources);
                }
            }
        }
        return sources;
    }

    /** Combined sound level at {@code listener} from all {@code sources}, in dB. */
    static double mixAt(List<Source> sources, BlockPos listener) {
        double linearSum = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Source source : sources) {
            cursor.set(source.packedPos());
            double heardDb = Acoustics.attenuatedDb(source.db(), Math.sqrt(cursor.distSqr(listener)));
            if (heardDb > 0) {
                linearSum += Math.pow(10, heardDb / 10.0);
            }
        }
        return linearSum <= 0 ? 0 : 10.0 * Math.log10(linearSum);
    }

    private static void addIfAudible(ServerLevel level, BlockPos pos, List<Source> sources) {
        double db = ratedDb(level.getBlockState(pos));
        if (db > 0) {
            sources.add(new Source(pos.asLong(), db));
        }
    }

    /** dB ratings of running machinery at one metre, anchored to real appliance levels. */
    private static double ratedDb(BlockState state) {
        if (state.is(ModBlocks.SCRUBBER.get())) {
            return 55; // fan bed
        }
        if (state.is(ModBlocks.DEHUMIDIFIER.get())) {
            return 62; // compressor
        }
        if (state.is(ModBlocks.ALARM.get()) && state.getValue(AlarmBlock.LIT)) {
            return 85; // klaxon
        }
        if (state.is(ModBlocks.OXYGEN_CANDLE.get()) && state.getValue(OxygenCandleBlock.LIT)) {
            return 45; // burn hiss
        }
        if (state.is(Blocks.FURNACE) && state.getValue(BlockStateProperties.LIT)) {
            return 50;
        }
        return 0;
    }

    private RoomNoise() {}
}
