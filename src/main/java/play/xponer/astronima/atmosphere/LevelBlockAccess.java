package play.xponer.astronima.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import play.xponer.astronima.sim.room.BlockAccess;
import play.xponer.astronima.sim.room.BlockKind;
import play.xponer.astronima.sim.room.CellPos;

/**
 * Adapts a {@link ServerLevel} to the sim's world view. Anything outside the build
 * height or in an unloaded chunk is {@link BlockKind#UNBOUNDED} — the scanner treats
 * volumes touching it as unsealed rather than guessing at unseen geometry.
 */
final class LevelBlockAccess implements BlockAccess {
    private final ServerLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    LevelBlockAccess(ServerLevel level) {
        this.level = level;
    }

    @Override
    public BlockKind kindAt(CellPos pos) {
        if (pos.y() < level.getMinY() || pos.y() > level.getMaxY()) {
            return BlockKind.UNBOUNDED;
        }
        cursor.set(pos.x(), pos.y(), pos.z());
        if (!level.isLoaded(cursor)) {
            return BlockKind.UNBOUNDED;
        }
        return AirBlockKinds.classify(level.getBlockState(cursor), level, cursor);
    }

    @Override
    public boolean insulatedAt(CellPos pos) {
        if (pos.y() < level.getMinY() || pos.y() > level.getMaxY()) {
            return false;
        }
        cursor.set(pos.x(), pos.y(), pos.z());
        return level.isLoaded(cursor) && AirBlockKinds.insulates(level.getBlockState(cursor));
    }

    @Override
    public boolean paintedAt(CellPos pos) {
        if (pos.y() < level.getMinY() || pos.y() > level.getMaxY()) {
            return false;
        }
        cursor.set(pos.x(), pos.y(), pos.z());
        return level.isLoaded(cursor) && AirBlockKinds.paints(level.getBlockState(cursor));
    }
}
