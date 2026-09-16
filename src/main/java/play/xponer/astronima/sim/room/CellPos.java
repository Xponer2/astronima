package play.xponer.astronima.sim.room;

import java.util.List;

/** An integer cell position — the sim-side stand-in for a block position. */
public record CellPos(int x, int y, int z) {
    public List<CellPos> neighbors() {
        return List.of(
                new CellPos(x + 1, y, z), new CellPos(x - 1, y, z),
                new CellPos(x, y + 1, z), new CellPos(x, y - 1, z),
                new CellPos(x, y, z + 1), new CellPos(x, y, z - 1));
    }
}
