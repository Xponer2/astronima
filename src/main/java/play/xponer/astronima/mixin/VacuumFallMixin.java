package play.xponer.astronima.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import play.xponer.astronima.registry.ModDimensions;

/**
 * Real vacuum has no terminal velocity: nothing slows a falling body, so a deep shaft keeps
 * accelerating whoever falls down it for as long as it is deep. {@code KineticImpactEvents}'
 * own class doc already says exactly this — but vanilla's movement code disagreed with it.
 *
 * <p>{@code LivingEntity.travelInAir} multiplies vertical speed by a fixed {@code 0.98} every
 * tick, unconditionally — a hardcoded air-resistance term with no attribute behind it, unlike
 * {@code Attributes.GRAVITY} (which is why {@code AsteroidGravity} could stay a plain attribute
 * modifier and this cannot: there is nothing to hang a modifier on). Left alone, that constant
 * converges falling speed to about {@code 50 · g_eff} blocks/tick — roughly 0.88 at this mod's
 * surface gravity — comfortably below {@code Microgravity.FATAL_SPEED} (1.5). No shaft, however
 * deep, could ever kill a faller: rule 71 in PLAN.md records how this was found and why it
 * matters that the fix live here rather than in the damage model.
 *
 * <p>Removed only in the asteroid dimension, and only the <strong>vertical</strong> term —
 * horizontal air resistance ({@code travelInAir}'s separate, unrelated {@code 0.91} constant)
 * is untouched, so ordinary movement still feels like Minecraft rather than like frictionless
 * ice skating. Applied to every living thing, not only players, for the same reason
 * {@code AsteroidGravity} is: an item that decelerates while the player next to it does not is
 * the kind of detail that reads as a bug long before anyone works out why.
 */
@Mixin(LivingEntity.class)
public abstract class VacuumFallMixin {

    @ModifyConstant(method = "travelInAir", constant = @Constant(floatValue = 0.98F))
    private float astronima$noVerticalDragInVacuum(float vanillaDrag) {
        LivingEntity self = (LivingEntity) (Object) this;
        return self.level().dimension() == ModDimensions.ASTEROID_LEVEL ? 1.0F : vanillaDrag;
    }
}
