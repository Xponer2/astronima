package play.xponer.astronima.sim.airlock;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Locale;

/**
 * Which device the player put on which terminal.
 *
 * <p>A machine that needs several other blocks to work together used to <em>search</em> for
 * them, and every fault it could report described that search rather than the build: a
 * chamber with both doors in one wall was told it needed two doors, a shared pump was "too
 * many pumps", a tank one block past the end of the walk was "no tank". None of those are
 * things the player did wrong. The player is an engineer and knows which door is the outer
 * one; asking is more honest than guessing, and it cannot be wrong about intent (PLAN rule
 * 17).
 *
 * <p>So this is the commissioning record: a role holds one device, a device is an opaque
 * long, and nothing here knows what a door or a pump <em>is</em>. Minecraft-free on purpose
 * (rule 1) — the rules about roles are the part worth proving, and they are provable without
 * a world.
 *
 * <p><strong>What this does not decide.</strong> Whether a bound device can actually do its
 * job is validation, and validation needs the world, so it lives in
 * {@code airlock/AirlockCommissioning}. This holds intent; that holds truth. In particular a
 * device <em>may</em> be stored on two roles at once — the player is allowed to make that
 * mistake, and the panel names it on the terminal they filled ("INNER DOOR — same door as
 * the outer") rather than the model silently refusing the click and leaving them wondering
 * why nothing happened.
 */
public final class DeviceBinding {

    /**
     * The terminals of an airlock panel. Fixed per machine — an airlock has exactly these
     * four, and there are no user-defined roles (design/commissioning.md §4).
     */
    public enum Role {
        INNER_DOOR,
        OUTER_DOOR,
        PUMP,
        TANK;

        /** {@code INNER_DOOR → INNER DOOR} — what the terminal is captioned. */
        public String label() {
            return name().replace('_', ' ');
        }

        /** The same, in the lower case a sentence wants: "same door as the outer door". */
        public String words() {
            return label().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What is wrong with the device on one terminal.
     *
     * <p>Here rather than beside the checks that produce it, because the panel, the block's
     * own gauge and the debug command all have to name a problem, and every one of those is
     * tested without a world. The <em>checking</em> needs the level and lives in
     * {@code airlock/AirlockCommissioning}; the vocabulary does not.
     */
    public enum Problem {
        /** Nothing wrong. */
        NONE,
        /** The terminal is empty — the player has not commissioned it yet. */
        UNBOUND,
        /** Something was bound here and the block is gone. */
        MISSING,
        /** The block bound here cannot fill this role at all — a tank is not a door. */
        WRONG_KIND,
        /** A door that is not on the chamber's boundary seals nothing the airlock owns. */
        NOT_ON_CHAMBER,
        /** The same door on both door terminals: a good door, but it cannot be both ends. */
        SAME_DOOR,
        /** Another controller holds this pump; two sequencers fighting over one is undefined. */
        IN_USE;

        /** The fault, said out loud, to go under the terminal it belongs to. */
        public String words() {
            return switch (this) {
                case NONE -> "ok";
                case UNBOUND -> "not bound";
                case MISSING -> "missing";
                case WRONG_KIND -> "wrong kind of block";
                case NOT_ON_CHAMBER -> "not on the chamber";
                case SAME_DOOR -> "same door as the other";
                case IN_USE -> "in use by another controller";
            };
        }
    }

    /** Nothing is bound to this terminal. Never a legal device, because a role is a slot. */
    public static final long NONE = Long.MIN_VALUE;

    private final EnumMap<Role, Long> devices = new EnumMap<>(Role.class);

    /**
     * Lands a device on a terminal, replacing whatever was there.
     *
     * <p>Replacing rather than refusing: rebinding a filled role is a thing players do
     * constantly — they mis-click, or they move the pump — and a terminal that had to be
     * cleared before it could be refilled is a second step for no gain.
     */
    public void bind(Role role, long device) {
        if (device == NONE) {
            clear(role);
            return;
        }
        devices.put(role, device);
    }

    /** Empties exactly one terminal, and only that one. */
    public void clear(Role role) {
        devices.remove(role);
    }

    /** Empties every terminal — what a fresh controller looks like. */
    public void clearAll() {
        devices.clear();
    }

    /** The device on this terminal, or {@link #NONE}. */
    public long device(Role role) {
        return devices.getOrDefault(role, NONE);
    }

    public boolean isBound(Role role) {
        return devices.containsKey(role);
    }

    /** The terminals still waiting for a device, in panel order. */
    public EnumSet<Role> unbound() {
        EnumSet<Role> empty = EnumSet.noneOf(Role.class);
        for (Role role : Role.values()) {
            if (!isBound(role)) {
                empty.add(role);
            }
        }
        return empty;
    }

    /** True when every terminal holds a device. Says nothing about whether they work. */
    public boolean complete() {
        return devices.size() == Role.values().length;
    }

    /**
     * True when some other terminal holds the same device as this one.
     *
     * <p>The one binding mistake that cannot be caught by looking at the block itself: the
     * same door is a perfectly good door, it just cannot be both ends of an airlock. Named
     * here so the panel can say so on the terminal the player filled.
     */
    public boolean isShared(Role role) {
        long device = device(role);
        if (device == NONE) {
            return false;
        }
        for (Role other : Role.values()) {
            if (other != role && device(other) == device) {
                return true;
            }
        }
        return false;
    }

    /** Every terminal holding this device — empty when the block is not commissioned. */
    public EnumSet<Role> rolesHolding(long device) {
        EnumSet<Role> holding = EnumSet.noneOf(Role.class);
        if (device == NONE) {
            return holding;
        }
        for (Role role : Role.values()) {
            if (device(role) == device) {
                holding.add(role);
            }
        }
        return holding;
    }

    /** A read-only view, for saving. */
    public Map<Role, Long> all() {
        return Map.copyOf(devices);
    }

    /** True when no terminal has ever been filled — the state a migration looks for. */
    public boolean empty() {
        return devices.isEmpty();
    }

    @Override
    public String toString() {
        return devices.toString();
    }
}
