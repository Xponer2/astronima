package play.xponer.astronima.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;

/**
 * Fires when an airlock the player is standing at completes a leg of its cycle.
 *
 * <p>A custom trigger for the same reason the habitat one is: the accomplishment is a
 * <em>working machine</em>, not a crafted item. Holding a controller teaches nothing; an
 * airlock that actually pumped its chamber down and released the outer door is the whole
 * plumbing chain — port, pipe, pump, tank, doors — proven in one motion.
 *
 * <p>Two moments, because they are the two halves of the lesson: going out costs time and
 * stored air, and coming home on that stored air is the moment the tank the player filled
 * finally pays for itself.
 */
public class AirlockTrigger extends SimpleCriterionTrigger<AirlockTrigger.TriggerInstance> {
    /** Which leg of the cycle completed. */
    public enum Moment {
        /**
         * Every terminal landed and checked out for the first time.
         *
         * <p>Its own moment because commissioning is a job in itself and the one the
         * player is most likely to get stuck halfway through — four terminals, four
         * devices, and until the last one lands the panel does nothing at all. An
         * advancement here says "that part is done" before the cycle is even tried.
         */
        COMMISSIONED,
        /** Pump-down finished and the outer door released: a real exit, air recovered. */
        CYCLED,
        /** The chamber refilled from stored gas and the inner door released: home. */
        CAME_HOME
    }

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Called by the controller when a leg completes, for each player standing near it. */
    public void trigger(ServerPlayer player, Moment moment) {
        trigger(player, instance -> instance.moment() == moment);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Moment moment)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        Codec.STRING.xmap(
                                name -> Moment.valueOf(name.toUpperCase(Locale.ROOT)),
                                moment -> moment.name().toLowerCase(Locale.ROOT))
                                .fieldOf("moment").forGetter(TriggerInstance::moment)
                ).apply(instance, TriggerInstance::new));

        public static Criterion<TriggerInstance> completed(Moment moment) {
            return ModCriteria.AIRLOCK.get().createCriterion(
                    new TriggerInstance(Optional.empty(), moment));
        }
    }
}
