package play.xponer.astronima.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import play.xponer.astronima.sim.MoldBranching;

import java.util.List;

/**
 * One colony's live shape for one frame: which way is "away from the surface" ({@code away}, so
 * the renderer can turn {@link MoldBranching}'s own floor-relative local space to point the right
 * way on a wall or ceiling) and the actual twig list itself, read from the block entity's own
 * cache rather than recomputed here (design/mold-growth.md §3c).
 */
public class MoldRenderState extends BlockEntityRenderState {
    public Direction away = Direction.UP;
    public List<MoldBranching.Segment> segments = List.of();
}
