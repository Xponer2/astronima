package play.xponer.astronima.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Invisible worldgen marker seeding a cavity with trapped volatiles. The first time
 * the room system materializes the cavity, the marker is consumed and its gas
 * charge injected ({@link play.xponer.astronima.atmosphere.GasPockets}) — mass
 * appears exactly once, deterministically from world seed and position.
 */
public class GasPocketCoreBlock extends Block {
    public GasPocketCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
