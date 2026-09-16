package play.xponer.astronima.sim.room;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Finds the enclosed volume containing a seed position by breadth-first flood fill.
 *
 * <p>The fill spreads through {@link BlockKind#OPEN} cells. {@link BlockKind#SEALED}
 * blocks bound it; {@link BlockKind#LEAKY} blocks bound it too but are reported so the
 * atmosphere tick can exchange gas across them. Touching {@link BlockKind#UNBOUNDED}
 * space, or exceeding the volume cap, marks the volume unsealed — but the fill still
 * completes (capped) so callers know the full extent that will vent.
 */
public final class RoomScanner {
    public static ScanResult scan(BlockAccess world, CellPos seed, int maxVolume) {
        if (world.kindAt(seed) != BlockKind.OPEN) {
            return ScanResult.noRoom();
        }

        Set<CellPos> cells = new HashSet<>();
        Set<CellPos> leaks = new HashSet<>();
        boolean touchedUnbounded = false;
        boolean overCap = false;
        Shell shell = Shell.NONE;

        Queue<CellPos> frontier = new ArrayDeque<>();
        cells.add(seed);
        frontier.add(seed);

        while (!frontier.isEmpty()) {
            CellPos current = frontier.remove();
            for (CellPos next : current.neighbors()) {
                if (cells.contains(next)) {
                    continue;
                }
                switch (world.kindAt(next)) {
                    case OPEN -> {
                        if (cells.size() >= maxVolume) {
                            overCap = true;
                        } else {
                            cells.add(next);
                            frontier.add(next);
                        }
                    }
                    // A leaky boundary is still a boundary: it has area, and area is what
                    // radiates. Whether gas also crosses it is a separate question, asked
                    // separately, and conflating the two would make a room with one cracked
                    // seal read as having a smaller hull than the identical intact one.
                    case LEAKY -> {
                        leaks.add(next);
                        shell = shell.plusFace(isBuried(world, current, next),
                                world.insulatedAt(next), world.paintedAt(next));
                    }
                    case UNBOUNDED -> touchedUnbounded = true;
                    case SEALED -> shell = shell.plusFace(isBuried(world, current, next),
                            world.insulatedAt(next), world.paintedAt(next));
                }
            }
        }

        return new ScanResult(Set.copyOf(cells), Set.copyOf(leaks), touchedUnbounded, overCap,
                shell);
    }

    /**
     * Whether this boundary face has something solid behind it, looked at from inside.
     *
     * <p>One step further along the same direction the fill was travelling. That is the
     * whole of the exposed/buried decision - see {@link Shell} for why one step is enough
     * and what it deliberately gets wrong.
     */
    private static boolean isBuried(BlockAccess world, CellPos from, CellPos boundary) {
        CellPos beyond = new CellPos(
                2 * boundary.x() - from.x(),
                2 * boundary.y() - from.y(),
                2 * boundary.z() - from.z());
        return world.kindAt(beyond) == BlockKind.SEALED;
    }

    private RoomScanner() {}
}
