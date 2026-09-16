package play.xponer.astronima.wire;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether an output is driving, answerable from the blockstate alone.
 *
 * <p>{@link SignalSource} needs a {@code ServerLevel} — it reads block entities and walks
 * circuits — so the client cannot ask it anything. But the client is where the player looks, and
 * <strong>a system whose state you can only learn by its effects is one you cannot debug.</strong>
 * That was the whole of the last audit: <em>"вроде всё подключил как надо, а оно всё равно не
 * работает"</em>.
 *
 * <p>So every source also answers from its synced blockstate. That is a real constraint on the
 * design rather than a convenience: <strong>if a source's output cannot be read off its
 * blockstate, it cannot be shown, and if it cannot be shown it should not exist.</strong> Which
 * is why the sensor gained a lit property it did not strictly need — the mat had no visible state
 * at all, and an input you cannot see is exactly the thing that makes a circuit undebuggable.
 */
public interface SignalView {

    /** True when this state is driving its outputs, as far as the client can tell. */
    boolean isDrivingClient(BlockState state);
}
