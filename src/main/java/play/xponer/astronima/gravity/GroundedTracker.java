package play.xponer.astronima.gravity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import play.xponer.astronima.Astronima;

import java.util.HashMap;
import java.util.Map;

/**
 * Coyote time: every consumer of {@code Microgravity.hasAirControl} reads this instead of
 * {@code entity.onGround()} directly.
 *
 * <p>Reported directly in play as "genuinely unplayable": stepping down a single block, or
 * stepping up onto one, while just walking normally, made every zero-g system in this dimension
 * engage instantly and yank the player — because vanilla's own {@code onGround()} genuinely does
 * go false for a tick or two during an ordinary step, not only during a real jump or push-off.
 * Nothing about that momentary flicker is airborne in any sense this mod's own systems should care
 * about; a player crossing a one-block ledge is still walking, not suddenly weightless.
 *
 * <p>Tracks real elapsed time since an entity was last observed grounded, the same
 * {@link System#nanoTime()}-based shape {@code OrientationSmoothing}'s own landing window already
 * uses, rather than a per-tick counter that would need a separate driver to stay correct — any
 * caller that asks the question also updates the answer, so nothing else has to remember to.
 *
 * <p><strong>Still not enough on its own</strong> — found live, a second time: this dimension's
 * own weak gravity ({@code Microgravity.GRAVITY_MULTIPLIER}) means an ordinary one-block step down
 * spends well over half a real second airborne (`d = ½gt²` at this dimension's own gravity puts a
 * one-block fall at roughly 530ms), comfortably longer than any grace window short enough to still
 * make a real jump feel immediate. A player wearing magnetic boots near the asteroid's own native
 * rock — {@link LocomotionAidEvents#asteroidRockNearby}, broader than the grip mechanic's own
 * worked-plating-only check on direct request, since this body's whole fiction is iron-nickel
 * throughout — is also treated as grounded, unconditionally of the timer: the boots' whole
 * fictional point is not letting go for a momentary loss of exact contact.
 *
 * <p><strong>Cleans up after itself.</strong> This map has no cap and no expiry of its own — every
 * entity ID that ever asks the question gets an entry that, without {@link #onEntityLeave}, would
 * sit there forever. Applies to every {@code LivingEntity} (mobs included, matching {@code
 * AirControlMixin}'s own scope), so the cleanup hooks {@link EntityLeaveLevelEvent} rather than a
 * player-only login/logout event — a busy server despawning and respawning entities all day is
 * exactly the situation an unbounded map would otherwise slowly leak into.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class GroundedTracker {

    /** How long a recent landing still counts as grounded, in nanoseconds — long enough to
     *  bridge a one-block step for an unbooted player, short enough that a real jump or push-off
     *  still reads as airborne almost immediately. A rule-41 number, open to retuning once
     *  actually flown. */
    private static final long GRACE_NANOS = 250_000_000L;

    private static final Map<Integer, Long> lastGroundedNanos = new HashMap<>();

    /**
     * Whether {@code entity} should be treated as grounded right now — true while actually
     * touching the ground, for {@link #GRACE_NANOS} afterward, and for a booted player near a
     * magnetic surface regardless of the timer.
     */
    public static boolean isEffectivelyGrounded(Entity entity) {
        long now = System.nanoTime();
        if (entity.onGround()) {
            lastGroundedNanos.put(entity.getId(), now);
            return true;
        }
        if (entity instanceof Player player
                && LocomotionAidEvents.hasBoots(player)
                && LocomotionAidEvents.asteroidRockNearby(player)) {
            return true;
        }
        Long last = lastGroundedNanos.get(entity.getId());
        return last != null && (now - last) < GRACE_NANOS;
    }

    @SubscribeEvent
    private static void onEntityLeave(EntityLeaveLevelEvent event) {
        lastGroundedNanos.remove(event.getEntity().getId());
    }

    private GroundedTracker() {}
}
