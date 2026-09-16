package play.xponer.astronima.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.item.SuitLoadout;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModDamageTypes;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.suit.SuitCondition;

/**
 * Arriving somewhere too fast, in any direction.
 *
 * <p>Vanilla measures a <em>fall</em>: how many blocks you dropped, vertically, since you were
 * last on the ground. That is a reasonable proxy on a planet with air and normal gravity, and
 * it is the wrong measurement here for two separate reasons.
 *
 * <ul>
 *   <li><strong>There is no air, so there is no terminal velocity.</strong> A shaft keeps
 *       accelerating you for as long as it is deep. Vanilla's distance-based curve flattens;
 *       the physics does not.
 *   <li><strong>Low gravity makes horizontal speed the easy kind to build.</strong> A
 *       sprint-jump across a chasm carries real energy sideways, and vanilla charges nothing
 *       for hitting a wall with it. Being flung into rock is exactly as fast as falling onto
 *       it, and the body cannot tell the difference.
 * </ul>
 *
 * <p>So the measurement is <strong>the speed you were carrying in the tick before you
 * stopped</strong>, whichever way it pointed. {@link Microgravity} turns that into damage.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class KineticImpactEvents {

    /**
     * Vanilla's fall damage is removed outright rather than adjusted.
     *
     * <p>Leaving it in would give one hazard two authorities working from two different
     * measurements — distance and speed — which disagree exactly where it matters, in a deep
     * shaft. The impact tick below is the only thing that harms anyone for moving.
     */
    @SubscribeEvent
    private static void onFall(LivingFallEvent event) {
        if (event.getEntity().level().dimension() == ModDimensions.ASTEROID_LEVEL) {
            event.setCanceled(true);
        }
    }

    /**
     * Watches for the moment a player stops, and charges them for it.
     *
     * <p>Reads the speed <em>remembered from last tick</em> rather than the current one, and
     * that is the whole trick: by the time a collision is visible the velocity has already
     * been zeroed by it, so anything measuring the present tick would find a stationary player
     * and conclude that nothing happened.
     */
    @SubscribeEvent
    private static void onPlayerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || level.dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        float carried = player.getData(ModAttachments.LAST_SPEED);
        double now = speedOf(player);
        player.setData(ModAttachments.LAST_SPEED.get(), (float) now);

        if (!stopped(player) || carried <= Microgravity.FREE_SPEED) {
            return;
        }
        // Caught rather than hit. An astronaut with a hand on a handrail is not partly
        // falling, so this is a cancellation and not a reduction - see LocomotionAids.
        if (touchingRail(player)) {
            return;
        }
        // A stop that is merely slowing down is not an impact. Requiring most of the speed to
        // have gone in one tick is what separates hitting a wall from letting go of the
        // controls, and without it a player who simply stopped sprinting would be hurt for it.
        if (now > carried * 0.5) {
            return;
        }
        float damage = (float) Microgravity.impactDamage(carried, isSuitSealedNow(player, level));
        if (damage > 0) {
            player.hurtServer(level, level.damageSources().source(ModDamageTypes.IMPACT), damage);
        }
    }

    /**
     * Whether the wearer's suit is sealed at the moment of impact — design/microgravity.md §8's
     * "a suited body absorbs a bounded amount of it". Reuses the same instantaneous fact rule 88
     * built for barotrauma and the HUD's seal lamp ({@link SuitCondition#isCurrentlySealed})
     * rather than a third, parallel "is this suit protecting me" computation: a suit worn but not
     * sealed — visor up, or incapable of holding pressure — offers none of this.
     */
    private static boolean isSuitSealedNow(ServerPlayer player, ServerLevel level) {
        Atmosphere.RoomReading reading = Atmosphere.get(level)
                .readingNear(BlockPos.containing(player.getEyePosition()));
        double ambientPpO2 = reading != null ? reading.state().partialPressureKPa(Gas.OXYGEN) : 0.0;
        return SuitCondition.isCurrentlySealed(ambientPpO2,
                SuitCondition.canHoldPressure(SuitLoadout.condition(player)));
    }

    /**
     * Whether the player has hold of a grab rail.
     *
     * <p>Checked at the body and at the head, because a rail catches you whichever part of you
     * reaches it first — and in a shaft you are usually going feet-first past a rail your head
     * has not reached yet.
     */
    private static boolean touchingRail(ServerPlayer player) {
        return player.level().getBlockState(player.blockPosition())
                        .is(ModBlocks.GRAB_RAIL.get())
                || player.level().getBlockState(player.blockPosition().above())
                        .is(ModBlocks.GRAB_RAIL.get());
    }

    /** True when something is in the way this tick — floor, wall or ceiling. */
    private static boolean stopped(ServerPlayer player) {
        return player.onGround() || player.horizontalCollision || player.verticalCollision;
    }

    /**
     * How fast the player is actually going, blocks/tick.
     *
     * <p>Taken from the position delta rather than {@code getDeltaMovement}: a player's motion
     * is client-authoritative and the server's copy of the velocity vector is not reliably the
     * one the client just used. Where they were and where they are now is a fact both agree on.
     */
    private static double speedOf(ServerPlayer player) {
        Vec3 moved = player.position().subtract(player.xOld, player.yOld, player.zOld);
        return moved.length();
    }

    private KineticImpactEvents() {}
}
