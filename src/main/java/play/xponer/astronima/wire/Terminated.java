package play.xponer.astronima.wire;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A block that publishes where its wires go.
 *
 * <p>Implemented by the <strong>block</strong>, not the block entity — the same rule
 * {@code PowerConnectable} and {@code SignalSource} follow, and for the reason this feature
 * already paid for once: the first power draft asked the block entity, found nothing, and did
 * nothing at all with no error anywhere.
 *
 * <p>Terminals are read from the {@link BlockState} so a machine's ports turn with it. A block
 * whose ports did not follow its facing would be a diagram that lies as soon as you rotate it.
 */
public interface Terminated {

    /** Every point on this block a wire may land on, in this state. */
    List<Terminal> terminals(BlockState state);

    /** One terminal per horizontal side — for a block you wire from whichever side is free. */
    static List<Terminal> aroundSides(Terminal.Kind kind) {
        List<Terminal> found = new ArrayList<>(4);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            found.add(Terminal.centre(side, kind));
        }
        return found;
    }

    /** Where the three studs of a terminal strip sit across a face. */
    int STRIP_IN_U = 4;
    int STRIP_POWER_U = 8;
    int STRIP_OUT_U = 12;

    /**
     * A machine's terminal strip, on every side: enable in, power, status out.
     *
     * <p>Three studs in a row is what a real machine's terminal block looks like, and it is what
     * makes a machine <em>wirable</em> rather than merely powerable. Power alone made every
     * machine a dead end for control: you could feed it and nothing else. With an input it can be
     * held off by logic, and with an output it can drive logic — which is the difference between
     * a base that is powered and a base that is <strong>automated</strong>.
     *
     * <p>On all four sides so a machine in a corner is still wirable, and always in the same
     * order — blue, brass, amber, left to right — so the strip reads the same on every machine in
     * the game.
     */
    static List<Terminal> machineStrip() {
        List<Terminal> found = new ArrayList<>(16);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            found.add(new Terminal(side, STRIP_IN_U, 8, Terminal.Kind.SIGNAL_IN, ENABLE));
            found.add(new Terminal(side, STRIP_POWER_U, 8, Terminal.Kind.POWER, POWER));
            found.add(new Terminal(side, STRIP_OUT_U, 8, Terminal.Kind.SIGNAL_OUT, WORKING));
            // A second row for the fault line. Two outputs is the difference between telling a
            // base that a machine is busy and telling it that a machine is *stuck*, and those
            // want opposite responses — one is patience, the other is a person.
            // Named for what it reports rather than for the word the code used to use. "held" sat
            // one row from a stud that holds a calibration, and two things called held on one
            // strip is a strip nobody can read.
            found.add(new Terminal(side, STRIP_OUT_U, FAULT_V, Terminal.Kind.SIGNAL_OUT, STUCK));
            // The servo, under the enable it sits beneath: a live line here holds the machine's
            // calibration steady while it is drawing power, which is what the electrical tier
            // buys somebody tired of walking to a crusher with a wrench
            // (design/calibration.md §6).
            found.add(new Terminal(side, STRIP_IN_U, FAULT_V, Terminal.Kind.SIGNAL_IN,
                    SERVO));
        }
        return found;
    }

    /** The second row, where a machine's fault line and its servo sit. */
    int FAULT_V = 4;

    /**
     * The names the strip publishes, so a machine and the wire layer agree on which stud is which.
     *
     * <p>Written down rather than typed at each site: two studs of the same kind are told apart by
     * their label now, and a label that is a string literal in three files is a label that will be
     * misspelt in one of them.
     */
    String ENABLE = "enable";
    String SERVO = "servo";
    String POWER = "power";
    String WORKING = "working";
    String STUCK = "stuck";

    /**
     * Left and right of a facing, plus the face it looks at — the shape a gate wants.
     *
     * <p>Inputs on the flanks and the output ahead, so the block reads like a signal-flow diagram
     * from above and a player can tell which end is which without opening anything.
     */
    static List<Terminal> gate(Direction facing) {
        return gate(facing, false);
    }

    /**
     * A gate's terminals.
     *
     * <p><strong>A single-input gate gets one input, on the back.</strong> An inverter with two
     * input studs of which only one is read is a trap: both are blue, both look connectable, and
     * the wrong one fails silently — which a scenario caught its own author doing on the first
     * try. One stud behind and one ahead also reads as a signal passing straight through, which
     * is what an inverter is.
     */
    static List<Terminal> gate(Direction facing, boolean singleInput) {
        if (singleInput) {
            return List.of(
                    Terminal.centre(facing.getOpposite(), Terminal.Kind.SIGNAL_IN, "in"),
                    Terminal.centre(facing, Terminal.Kind.SIGNAL_OUT, "out"));
        }
        return List.of(
                Terminal.centre(facing.getCounterClockWise(), Terminal.Kind.SIGNAL_IN, "A"),
                Terminal.centre(facing.getClockWise(), Terminal.Kind.SIGNAL_IN, "B"),
                Terminal.centre(facing, Terminal.Kind.SIGNAL_OUT, "out"));
    }
}
