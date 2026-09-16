package play.xponer.astronima.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A block that can put a voltage on the line landed on one of its output terminals.
 *
 * <p><strong>A signal here is not a redstone level, it is a closed contact.</strong> An output
 * either drives or it does not — which is what a sensor, a switch and a relay contact all
 * physically are, and it is why nothing in this system needs a 0-15 number or a rule about how
 * far a signal travels before it fades. A conductor does not weaken what it carries; a long run
 * costs voltage through {@code I²R} and that is already modelled.
 *
 * <p>Asked <strong>per terminal</strong>, because a gate has more than one and has to tell them
 * apart. Implemented by the <em>block</em> rather than the block entity, matching
 * {@link Terminated} — the first power draft asked the block entity, found nothing, and did
 * nothing at all with no error anywhere.
 */
public interface SignalSource {

    /** True while this block is driving the line landed on that output terminal. */
    boolean isDriving(ServerLevel level, BlockPos pos, Terminal terminal);
}
