package play.xponer.astronima.airlock;

import org.jspecify.annotations.Nullable;
import play.xponer.astronima.sim.airlock.AirlockCycle;
import play.xponer.astronima.sim.airlock.DeviceBinding.Problem;
import play.xponer.astronima.sim.airlock.DeviceBinding.Role;

import java.util.EnumMap;
import java.util.Map;

/**
 * Everything the airlock panel draws, in one value that survives the trip to the client.
 *
 * <p>The panel's whole job is to be trusted — it is the only place a player can see what
 * they commissioned — so the crossing has to be exact. Menu data travels as an int array,
 * and hand-packing that at one end and unpacking it at the other is precisely where a panel
 * starts quietly lying: one index inserted in the middle and the tank gauge shows the
 * habitat's pressure with nothing failing anywhere. Encoding lives here, beside the decode,
 * with no Minecraft types, so a unit test can round-trip every field.
 *
 * <p>Since commissioning (PLAN rule 17) the panel is not a report of what we found — it is
 * the record of what the player <em>put</em> on each terminal, and what is wrong with it.
 * So each of the four terminals carries two things: the problem with the device on it, and
 * where that device is relative to the panel. The offset is not decoration: two bulkhead
 * doors look identical on a diagram, and <em>"OUTER DOOR ⌂ 2E 1U"</em> is the only way to
 * tell which of them a fault is about without walking round the chamber.
 *
 * <p>Pressures are carried in tenths of a kPa: an int array is all a container has, and a
 * gauge that jumped in whole kPa would visibly stair-step while the chamber pumps down.
 */
public record AirlockStatus(
        AirlockCycle.Phase phase,
        AirlockResolver.Fault chamberFault,
        AirlockCycle.Fault cycleFault,
        double chamberKPa,
        double tankKPa,
        double habitatKPa,
        int chamberVolume,
        boolean innerShut,
        boolean outerShut,
        Map<Role, Terminal> terminals) {

    /** One terminal of the panel: what is on it, and where that is. */
    public record Terminal(Problem problem, int offset) {
        public boolean bound() {
            return problem != Problem.UNBOUND;
        }

        public boolean ok() {
            return problem == Problem.NONE;
        }
    }

    /** Slots in the synced array. */
    public static final int I_PHASE = 0;
    public static final int I_CHAMBER_FAULT = 1;
    public static final int I_CYCLE_FAULT = 2;
    public static final int I_CHAMBER_DKPA = 3;
    public static final int I_TANK_DKPA = 4;
    public static final int I_HABITAT_DKPA = 5;
    public static final int I_VOLUME = 6;
    public static final int I_FLAGS = 7;
    /** All four terminals' problems, a nibble apiece, in {@link Role} order. */
    public static final int I_PROBLEMS = 8;
    /** One packed offset per terminal, in {@link Role} order. */
    public static final int I_OFFSETS = 9;
    public static final int SIZE = I_OFFSETS + 4;

    private static final int FLAG_INNER_SHUT = 1;
    private static final int FLAG_OUTER_SHUT = 2;

    // ---- offsets --------------------------------------------------------------

    /** An empty terminal has no offset at all, which is how the panel tells them apart. */
    public static final int OFFSET_UNBOUND = 0;
    private static final int OFF_BOUND = 1 << 20;
    /** Bound, but too far away to write as a short offset — say "far" rather than lie. */
    private static final int OFF_FAR = 1 << 21;
    private static final int OFF_BIAS = 32;
    private static final int OFF_LIMIT = 31;

    /**
     * A device's position relative to the panel, packed small enough to ride a container
     * slot. Beyond ±31 blocks it packs as "far": a wrapped-around offset would point the
     * player at the wrong side of the room, which is worse than admitting the distance.
     */
    public static int packOffset(int dx, int dy, int dz) {
        if (Math.abs(dx) > OFF_LIMIT || Math.abs(dy) > OFF_LIMIT || Math.abs(dz) > OFF_LIMIT) {
            return OFF_BOUND | OFF_FAR;
        }
        return OFF_BOUND
                | ((dx + OFF_BIAS) << 12)
                | ((dy + OFF_BIAS) << 6)
                | (dz + OFF_BIAS);
    }

    /**
     * The offset as a player reads a compass: {@code "2E 1U"}, or {@code "far"}, or empty
     * when the terminal is not commissioned yet.
     */
    public static String offsetLabel(int packed) {
        if ((packed & OFF_BOUND) == 0) {
            return "";
        }
        if ((packed & OFF_FAR) != 0) {
            return "far";
        }
        int dx = ((packed >> 12) & 63) - OFF_BIAS;
        int dy = ((packed >> 6) & 63) - OFF_BIAS;
        int dz = (packed & 63) - OFF_BIAS;
        StringBuilder text = new StringBuilder();
        append(text, dx, "E", "W");
        append(text, dy, "U", "D");
        append(text, dz, "S", "N");
        return text.isEmpty() ? "here" : text.toString();
    }

    private static void append(StringBuilder text, int delta, String positive, String negative) {
        if (delta == 0) {
            return;
        }
        if (!text.isEmpty()) {
            text.append(' ');
        }
        text.append(Math.abs(delta)).append(delta > 0 ? positive : negative);
    }

    // ---- the wire -------------------------------------------------------------

    /** Tenths of a kPa, clamped to something an int can hold and a gauge can show. */
    private static int deci(double kPa) {
        return (int) Math.round(Math.clamp(kPa, 0.0, 1_000_000.0) * 10.0);
    }

    public Terminal terminal(Role role) {
        return terminals.getOrDefault(role, new Terminal(Problem.UNBOUND, OFFSET_UNBOUND));
    }

    public Problem problem(Role role) {
        return terminal(role).problem();
    }

    public int encode(int index) {
        if (index >= I_OFFSETS && index < I_OFFSETS + Role.values().length) {
            return terminal(Role.values()[index - I_OFFSETS]).offset();
        }
        return switch (index) {
            case I_PHASE -> phase.ordinal();
            case I_CHAMBER_FAULT -> chamberFault.ordinal();
            case I_CYCLE_FAULT -> cycleFault.ordinal();
            case I_CHAMBER_DKPA -> deci(chamberKPa);
            case I_TANK_DKPA -> deci(tankKPa);
            case I_HABITAT_DKPA -> deci(habitatKPa);
            case I_VOLUME -> chamberVolume;
            case I_FLAGS -> (innerShut ? FLAG_INNER_SHUT : 0) | (outerShut ? FLAG_OUTER_SHUT : 0);
            case I_PROBLEMS -> packProblems();
            default -> 0;
        };
    }

    private int packProblems() {
        int packed = 0;
        Role[] roles = Role.values();
        for (int i = 0; i < roles.length; i++) {
            packed |= (problem(roles[i]).ordinal() & 0xF) << (i * 4);
        }
        return packed;
    }

    /** Rebuilds the status the panel draws from the array the server sent. */
    public static AirlockStatus decode(java.util.function.IntUnaryOperator data) {
        int flags = data.applyAsInt(I_FLAGS);
        int problems = data.applyAsInt(I_PROBLEMS);
        Map<Role, Terminal> terminals = new EnumMap<>(Role.class);
        Role[] roles = Role.values();
        for (int i = 0; i < roles.length; i++) {
            Problem problem = value(Problem.values(), (problems >> (i * 4)) & 0xF,
                    Problem.UNBOUND);
            terminals.put(roles[i], new Terminal(problem, data.applyAsInt(I_OFFSETS + i)));
        }
        return new AirlockStatus(
                value(AirlockCycle.Phase.values(), data.applyAsInt(I_PHASE),
                        AirlockCycle.Phase.SEALED),
                value(AirlockResolver.Fault.values(), data.applyAsInt(I_CHAMBER_FAULT),
                        AirlockResolver.Fault.NONE),
                value(AirlockCycle.Fault.values(), data.applyAsInt(I_CYCLE_FAULT),
                        AirlockCycle.Fault.NONE),
                data.applyAsInt(I_CHAMBER_DKPA) / 10.0,
                data.applyAsInt(I_TANK_DKPA) / 10.0,
                data.applyAsInt(I_HABITAT_DKPA) / 10.0,
                data.applyAsInt(I_VOLUME),
                (flags & FLAG_INNER_SHUT) != 0,
                (flags & FLAG_OUTER_SHUT) != 0,
                Map.copyOf(terminals));
    }

    /** An ordinal from the wire is untrusted input; an out-of-range one falls back. */
    private static <T> T value(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    // ---- what the panel asks it ----------------------------------------------

    /** True when the chamber reads and every terminal is filled and clean. */
    public boolean commissioned() {
        if (chamberFault != AirlockResolver.Fault.NONE) {
            return false;
        }
        for (Role role : Role.values()) {
            if (problem(role) != Problem.NONE) {
                return false;
            }
        }
        return true;
    }

    /** True when the player has not commissioned anything yet — a brand new panel. */
    public boolean blank() {
        for (Role role : Role.values()) {
            if (terminal(role).bound()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The first terminal with something wrong, in panel order, or null.
     *
     * <p>One at a time: a panel listing four faults at once is a wall of text, and the
     * player can only walk to one block anyway.
     */
    public @Nullable Role firstFault() {
        for (Role role : Role.values()) {
            if (problem(role) != Problem.NONE) {
                return role;
            }
        }
        return null;
    }

    /**
     * The stage of the process line the current fault belongs to, so the diagram lights the
     * piece that is wrong instead of printing a word somewhere else.
     */
    public Stage faultyStage() {
        if (chamberFault != AirlockResolver.Fault.NONE) {
            return Stage.CHAMBER;
        }
        Role role = firstFault();
        if (role == null) {
            return Stage.NONE;
        }
        return switch (role) {
            case INNER_DOOR, OUTER_DOOR -> Stage.DOORS;
            case PUMP -> Stage.PUMP;
            case TANK -> Stage.TANK;
        };
    }

    /** The process line, in the order gas actually moves through it. */
    public enum Stage { NONE, CHAMBER, DOORS, PUMP, TANK }
}
