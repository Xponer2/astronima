package play.xponer.astronima.sim.airlock;

import java.util.HashMap;
import java.util.Map;

/**
 * Things one machine is holding, each of which lets go if it stops being renewed.
 *
 * <p>The pattern the airlock already relies on twice and which is worth naming: a machine
 * that takes control of something the player also owns — a pump's gate, a door's latch —
 * must not be able to keep it after the machine is gone. Breaking the controller mid-cycle,
 * the chunk unloading, the server being killed: all of them end the same way, because none
 * of them is handled. They simply stop the renewals, and a hold nobody renews lapses.
 *
 * <p><strong>This is the softlock invariant, mechanised.</strong> A latch that outlived its
 * controller would seal a player into a two-block chamber with no way out, and the failure
 * mode of "release it in the right destructor" is that one of the ways a machine can vanish
 * is always missed.
 *
 * <p>Minecraft-free (rule 1): it is arithmetic on a clock, so it is provable without a
 * world, including the case that matters most — the clock going backwards, which is what a
 * restart looks like from in here.
 */
public final class LeaseRegister {

    private final Map<Long, Long> renewedAt = new HashMap<>();
    private final long leaseTicks;

    /**
     * @param leaseTicks how long after the last renewal a hold survives. Long enough that a
     *                   machine renewing on its normal schedule never drops one, short
     *                   enough that a player standing at a broken controller does not
     *                   believe the door is stuck for good
     */
    public LeaseRegister(long leaseTicks) {
        this.leaseTicks = leaseTicks;
    }

    public long leaseTicks() {
        return leaseTicks;
    }

    /** Takes or extends the hold on {@code key}. */
    public void renew(long key, long now) {
        renewedAt.put(key, now);
    }

    /**
     * Whether the hold on {@code key} is still live.
     *
     * <p>A renewal in the <em>future</em> counts as live and one from before the register
     * existed does not, which is the same thing said twice: a world that reloaded has an
     * empty register and a game time that carries on, so every hold taken before the reload
     * is correctly gone. A world whose clock was wound back cannot leave a hold stuck on
     * either, because a hold is only ever live within a window of its own renewal.
     */
    public boolean isLive(long key, long now) {
        Long renewed = renewedAt.get(key);
        return renewed != null && now - renewed <= leaseTicks && now >= renewed - leaseTicks;
    }

    /** Drops the hold outright — the machine letting go on purpose. */
    public void release(long key) {
        renewedAt.remove(key);
    }

    /**
     * Forgets holds nobody has renewed for a long time.
     *
     * <p>Without this the register grows for every door any airlock ever latched. Called
     * when something is checked rather than on a timer, so it costs nothing on a world with
     * no airlocks in it.
     */
    public void prune(long now) {
        renewedAt.entrySet().removeIf(entry -> now - entry.getValue() > leaseTicks * 4);
    }

    /** How many holds are on the books — for tests, and for the debug command. */
    public int size() {
        return renewedAt.size();
    }
}
