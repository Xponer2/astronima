package play.xponer.astronima.astra;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.ore.Mineral;
import play.xponer.astronima.sim.ore.OreBody;

import java.util.Map;

/**
 * The real column a crust sounding reads — design/astra-crust-sounding.md §2.1: the real,
 * already-placed blocks beneath a position, never a re-rolled approximation of worldgen's own
 * noise (astra-systems.md §12's own "reports those minerals and nothing else"). Shared by
 * {@code item.AstraSounderItem} (the real instrument) and the debug command's own honest first
 * consumer, so the two can never quietly read the world two different ways (rule 46).
 */
public final class CrustColumn {

    /** "The column under you" (astra-content.md §5, branch 3) — a real, bounded local read, not
     *  the whole path to the core. First-pass, not measured (rule 41). */
    public static final int SOUNDING_DEPTH_BLOCKS = 32;

    private CrustColumn() {}

    /** The real, aggregate {@link OreBody} of every recognised ore block in the
     *  {@link #SOUNDING_DEPTH_BLOCKS} blocks directly beneath {@code feet} — unrecognised blocks
     *  (plain stone, air, anything else) contribute nothing, exactly as design/
     *  astra-crust-sounding.md §2.1 states. */
    public static OreBody scanBelow(ServerLevel level, BlockPos feet) {
        OreBody column = OreBody.empty();
        for (int i = 1; i <= SOUNDING_DEPTH_BLOCKS; i++) {
            var state = level.getBlockState(feet.below(i));
            if (state.is(ModBlocks.ASTEROID_ROCK.get())) {
                column = column.plus(OreBody.chondrite(4000.0));
            } else if (state.is(ModBlocks.METAL_RICH_ORE.get())) {
                column = column.plus(OreBody.metalRich(4000.0));
            } else if (state.is(ModBlocks.ILMENITE_ORE.get())) {
                column = column.plus(OreBody.of(4000.0, Map.of(Mineral.ILMENITE, 1.0)));
            }
        }
        return column;
    }
}
