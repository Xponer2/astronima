package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.wire.SignalSource;
import play.xponer.astronima.wire.Terminal;
import play.xponer.astronima.wire.Terminated;

import java.util.List;

/**
 * Watches one block of space and closes a contact when somebody is in it.
 *
 * <p>The first thing in this mod that gives wire something to <em>say</em>. A run that carries
 * watts is a decision made once; a run that carries <em>somebody is standing in the airlock</em>
 * is a thing the player builds around, and everything interesting about control wiring starts
 * here.
 *
 * <p><strong>It watches exactly one cell — the one directly above it.</strong> Not a radius, not
 * a cone, and not a direction you set: a mat you stand on is something a player can see, step on
 * and reason about without a manual, and its footprint is obvious from where the block is.
 *
 * <p>An earlier draft let it face any way it was placed. That was quietly worse — rule 9 asks
 * that state a player acts on be <em>visible</em>, and a cube with an invisible facing is a
 * component whose behaviour you can only learn by experiment. Put it in the floor of the airlock
 * and it does the one thing its shape promises.
 */
public class PresenceSensorBlock extends Block
        implements SignalSource, Terminated, play.xponer.astronima.wire.SignalView {

    /**
     * Lit while somebody is on it.
     *
     * <p>Not needed by the logic — {@link #isDriving} reads the world directly — and added anyway,
     * because the client cannot call that and a mat with no visible state is the first place a
     * circuit becomes impossible to debug. It is also simply what a real sensor does.
     */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT);
    }

    @Override
    public boolean isDrivingClient(BlockState state) {
        return state.getValue(LIT);
    }

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                           BlockState old, boolean moving) {
        level.scheduleTick(pos, this, 1);
    }

    /** Keeps the lit state in step with who is standing on it. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos,
                        net.minecraft.util.RandomSource random) {
        boolean now = occupied(level, pos);
        if (now != state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, now), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, 5);
    }


    @Override
    public java.util.List<Terminal> terminals(BlockState state) {
        // One output per side, so it can be wired from whichever side the floor is free on.
        return Terminated.aroundSides(Terminal.Kind.SIGNAL_OUT);
    }


    public PresenceSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    /**
     * True while a player is inside the cell this sensor faces.
     *
     * <p>Players only, deliberately. This is a crew sensor: the question a habitat asks is
     * whether a <em>person</em> is in the chamber, and having a dropped item or a wandering mob
     * cycle an airlock would make the mechanic untrustworthy exactly when it matters.
     */
    @Override
    public boolean isDriving(ServerLevel level, BlockPos pos, Terminal terminal) {
        return occupied(level, pos);
    }

    /** Whether anybody is standing in the cell above. */
    private boolean occupied(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos.above());
        List<Player> found = level.getEntitiesOfClass(Player.class, box,
                player -> !player.isSpectator());
        return !found.isEmpty();
    }
}
