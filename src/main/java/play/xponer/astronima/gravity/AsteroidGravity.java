package play.xponer.astronima.gravity;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;

/**
 * Makes the asteroid weigh what an asteroid weighs — or as close to it as is playable.
 *
 * <p>Applied as an attribute modifier rather than by touching movement code, for the reason
 * rule 2 exists: vanilla already has a syncable {@code GRAVITY} attribute, it is what every
 * other part of the engine consults, and reimplementing the fall would put this mod's idea of
 * physics next to Minecraft's and let the two drift.
 *
 * <p><strong>Applied to every living thing, not only players.</strong> An item that falls at
 * one rate while the player next to it falls at another is the kind of detail that reads as a
 * bug long before anyone works out what it is telling them.
 *
 * <p>The number itself, and why it is not the honest one, is in {@link Microgravity} and
 * {@code design/microgravity.md} §2.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class AsteroidGravity {

    private static final Identifier MODIFIER_ID =
            Identifier.fromNamespaceAndPath(Astronima.MODID, "asteroid_gravity");

    /**
     * Below this, a changed value is not worth resyncing. {@code GRAVITY} is client-synced for
     * movement prediction, so replacing the modifier every tick regardless of whether it moved
     * would be a sync packet per entity per tick even standing still — this keeps the common
     * case (a player who has not walked far since last tick) a no-op, the same way the old
     * presence-only check was a no-op for a constant value.
     */
    private static final double VALUE_EPSILON = 1.0e-3;

    /**
     * Adds or refreshes the modifier in the asteroid and takes it away anywhere else.
     *
     * <p>Checked every tick rather than on dimension change or on movement, deliberately. A
     * modifier that is applied by an event a player can miss — a teleport, a respawn, a chunk
     * load at the wrong moment — is a modifier that eventually goes missing or goes stale, and
     * the symptom is a player who inexplicably weighs the wrong amount in a world where nobody
     * else does. Re-deriving the value fresh every tick makes staleness structurally
     * impossible rather than something to remember to invalidate.
     *
     * <p><strong>The value itself is position-dependent</strong> — see
     * {@link Microgravity#gravityMultiplierAt} and {@code design/asteroid-body-b2.md}. The
     * magnetic boots' grip modifier ({@code LocomotionAidEvents}) cancels exactly this value,
     * evaluated at the same position in the same tick; changing how this method computes {@code
     * r} without changing that one too will break the boots silently rather than loudly.
     */
    @SubscribeEvent
    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        boolean shouldApply = entity.level().dimension() == ModDimensions.ASTEROID_LEVEL;
        AttributeModifier applied = gravity.getModifier(MODIFIER_ID);
        if (!shouldApply) {
            if (applied != null) {
                gravity.removeModifier(MODIFIER_ID);
            }
            return;
        }
        double r = Math.hypot(entity.getX(), entity.getZ());
        double amount = Microgravity.gravityMultiplierAt(r) - 1.0;
        if (applied != null && Math.abs(applied.amount() - amount) < VALUE_EPSILON) {
            return; // already close enough - no resync needed
        }
        if (applied != null) {
            gravity.removeModifier(MODIFIER_ID);
        }
        gravity.addPermanentModifier(new AttributeModifier(
                MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /** Exposed for the wiring guard: which attribute this phase actually touches. */
    public static Holder<Attribute> attribute() {
        return Attributes.GRAVITY;
    }

    private AsteroidGravity() {}
}
