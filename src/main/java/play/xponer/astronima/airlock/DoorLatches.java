package play.xponer.astronima.airlock;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import play.xponer.astronima.sim.airlock.LeaseRegister;

import java.util.HashMap;
import java.util.Map;

/**
 * Which bulkhead doors a controller is currently holding shut.
 *
 * <p>The latch itself lives on the door's blockstate, because it has to be visible and it
 * has to stop redstone. But a blockstate is <em>saved</em>, and a saved latch with nothing
 * left to release it is a player sealed into a two-block chamber for good. So the authority
 * over whether a latch may stay thrown is this — deliberately <strong>not</strong> saved.
 *
 * <p>The consequence is the one that matters: after any restart every register is empty, so
 * the first time a locked door checks itself it finds no holder and releases. A cycle that
 * is genuinely still running renews within a tick, long before that check comes round, so a
 * live airlock never notices. Nothing has to remember to clean up, which is what makes it
 * correct for the cases nobody thought of.
 *
 * <p>Per dimension, because two doors at the same coordinates in the overworld and on the
 * asteroid are two different doors.
 */
public final class DoorLatches {

    /**
     * How long a latch survives without renewal, in ticks.
     *
     * <p>Three seconds. The controller renews every tick, so this is never close for a
     * working airlock; it is entirely about how long a player stands at a door after
     * breaking the controller before deciding the game has trapped them. The pump's claim
     * uses the same figure for the same reason.
     */
    public static final long LEASE_TICKS = 60;

    /** How often a locked door wakes up to ask whether anyone still holds it. */
    public static final int CHECK_INTERVAL_TICKS = 20;

    private static final Map<ResourceKey<Level>, LeaseRegister> BY_DIMENSION = new HashMap<>();

    private static LeaseRegister register(ServerLevel level) {
        return BY_DIMENSION.computeIfAbsent(level.dimension(),
                key -> new LeaseRegister(LEASE_TICKS));
    }

    /** A controller saying "I am still holding this door", once per tick. */
    public static void renew(ServerLevel level, BlockPos doorFoot) {
        register(level).renew(doorFoot.asLong(), level.getGameTime());
    }

    /** Whether anything still claims this door. */
    public static boolean held(ServerLevel level, BlockPos doorFoot) {
        LeaseRegister register = register(level);
        register.prune(level.getGameTime());
        return register.isLive(doorFoot.asLong(), level.getGameTime());
    }

    /** A controller letting go on purpose — releasing a door it no longer locks. */
    public static void release(ServerLevel level, BlockPos doorFoot) {
        register(level).release(doorFoot.asLong());
    }

    /** For the debug command: how many latches are being held in this dimension. */
    public static int heldCount(ServerLevel level) {
        return register(level).size();
    }

    private DoorLatches() {}
}
