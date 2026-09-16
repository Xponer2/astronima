package play.xponer.astronima.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.client.WireReadout;
import play.xponer.astronima.item.WireCoilItem;
import play.xponer.astronima.item.WireCutterItem;
import play.xponer.astronima.wire.Terminal;
import play.xponer.astronima.wire.Terminated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The terminals near the player, found once and shared.
 *
 * <p>Two renderers wanted the same answer — where the studs are, and which of them are driving —
 * and each was walking its own cube of blockstates on its own timer. Two scans of the same blocks
 * is twice the work for one picture, and worse, two chances for the studs and the charge
 * animation to disagree about what is there.
 *
 * <p><strong>And the scan only runs when the player is actually wiring.</strong> Studs drawn
 * permanently turned every machine in a base into a thing covered in dots — a readout nobody
 * asked for, on all the time, which is the opposite of the instrument it is meant to be. With a
 * coil, cutters or the goggles in play they appear; otherwise a machine looks like a machine.
 */
public final class TerminalScan {

    /**
     * How far out to look, in blocks.
     *
     * <p>Twelve is a cube of 15 625 blockstates. The first version used 24 — 117 649 — and walked
     * it every frame, which at sixty frames a second is seven million lookups a second to draw a
     * handful of studs.
     */
    public static final int RANGE = 12;

    /** How often the list is rebuilt. Terminals move only when blocks do. */
    private static final long REBUILD_MS = 400;

    private static final List<Found> CACHE = new ArrayList<>();
    private static long rebuiltAt;
    private static BlockPos rebuiltAround;

    /** One stud, resolved. */
    public record Found(BlockPos block, BlockState state, Terminal terminal) { }

    /**
     * Whether terminals are worth drawing at all right now.
     *
     * <p>Holding a wiring tool, or wearing the meter. Both are the player saying <em>I am working
     * on the wiring</em>, which is the only time a wall of studs is information rather than
     * clutter.
     */
    public static boolean wanted(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }
        return holdingTool(player) || WireReadout.wearingGoggles(minecraft);
    }

    private static boolean holdingTool(LocalPlayer player) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var item = player.getItemInHand(hand).getItem();
            if (item instanceof WireCoilItem || item instanceof WireCutterItem) {
                return true;
            }
        }
        return false;
    }

    /** Every terminal in range, rebuilt when it has gone stale. */
    public static List<Found> around(ClientLevel level, BlockPos around) {
        long now = System.currentTimeMillis();
        if (rebuiltAround != null && around.distSqr(rebuiltAround) < 4
                && now - rebuiltAt < REBUILD_MS) {
            return Collections.unmodifiableList(CACHE);
        }
        rebuiltAt = now;
        rebuiltAround = around;
        CACHE.clear();
        // Scanned rather than indexed: a terminal is a property of a blockstate, so an index
        // would have to be invalidated by every block change in range to stay honest. Scanning
        // on a timer is the cheap way to be right often enough.
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-RANGE, -RANGE, -RANGE),
                around.offset(RANGE, RANGE, RANGE))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof Terminated terminated)) {
                continue;
            }
            BlockPos block = pos.immutable();
            for (Terminal terminal : terminated.terminals(state)) {
                CACHE.add(new Found(block, state, terminal));
            }
        }
        return Collections.unmodifiableList(CACHE);
    }

    private TerminalScan() {}
}
