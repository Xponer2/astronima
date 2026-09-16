package play.xponer.astronima.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import play.xponer.astronima.sim.wire.FaceBasis;
import play.xponer.astronima.sim.wire.WirePixel;

/**
 * A named point on a block where a wire may actually land.
 *
 * <p><strong>This replaces "a wire touching the block anywhere is wired to it", which was the
 * thing that made the whole system read as a toy.</strong> A trace dragged across the front of a
 * crusher powered the crusher; a switch was operated by right-clicking it and had no electrical
 * relationship with the wire at all. There was nothing to <em>connect</em>, so there was nothing
 * to get right — and an engineer's system where nothing can be got wrong is a construction toy.
 *
 * <p>Now every block that is on a circuit publishes its terminals: <em>what</em> each one is and
 * <em>where</em> it is, to the pixel. A run has to reach that pixel. The renderer draws every
 * terminal whether or not anything is attached, so wiring a machine is aiming at a stud you can
 * see rather than waving a coil at a wall.
 *
 * @param outward which way the terminal faces out of its block — the wire lands in the cell that
 *                way and fastens back against it
 * @param u       across that face, in the wire's own pixel grid
 * @param v       the other way across it
 */
public record Terminal(Direction outward, int u, int v, Kind kind, String label) {

    /**
     * An unnamed terminal.
     *
     * <p>Kept for the plain cases — a power stud is a power stud — but naming is the norm for
     * anything a player has to choose between. A machine with two amber studs and no labels is a
     * machine whose outputs can only be told apart by experiment.
     */
    public Terminal(Direction outward, int u, int v, Kind kind) {
        this(outward, u, v, kind, kind.defaultLabel());
    }

    /**
     * What a terminal is for.
     *
     * <p><strong>Power is one kind and signals are two</strong>, and that asymmetry is physical
     * rather than tidy: a DC bus really is bidirectional — a cell both takes and gives on the
     * same pair — while a logic output drives and a logic input listens, and a gate whose output
     * could be driven backwards by its own load is not a gate.
     */
    public enum Kind {
        /** Carries watts, both ways. */
        POWER,
        /** Listens to a control line. */
        SIGNAL_IN,
        /** Drives a control line. */
        SIGNAL_OUT;

        public boolean isSignal() {
            return this != POWER;
        }

        String defaultLabel() {
            return switch (this) {
                case POWER -> "power";
                case SIGNAL_IN -> "in";
                case SIGNAL_OUT -> "out";
            };
        }
    }

    public Terminal {
        if (!FaceBasis.onGrid(u) || !FaceBasis.onGrid(v)) {
            throw new IllegalArgumentException("terminal at " + u + "," + v + " is off the face");
        }
    }

    /** A terminal in the middle of a face — the ordinary case. */
    public static Terminal centre(Direction outward, Kind kind) {
        return new Terminal(outward, 8, 8, kind);
    }

    /** The same, named. */
    public static Terminal centre(Direction outward, Kind kind, String label) {
        return new Terminal(outward, 8, 8, kind, label);
    }

    /** The cell a wire has to be in to reach this terminal. */
    public BlockPos cell(BlockPos block) {
        return block.relative(outward);
    }

    /** The face that wire is fastened to — back toward the block. */
    public Direction wireFace() {
        return outward.getOpposite();
    }

    /** The exact pixel a run must occupy to be landed on this terminal. */
    public WirePixel pixel(BlockPos block) {
        BlockPos cell = cell(block);
        return new WirePixel(cell.getX(), cell.getY(), cell.getZ(),
                Faces.of(wireFace()), u, v);
    }

    /** True when this pixel is exactly this terminal. */
    public boolean isAt(BlockPos block, WirePixel pixel) {
        return pixel.equals(pixel(block));
    }
}
