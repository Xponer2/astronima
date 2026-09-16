package play.xponer.astronima.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Fires when the player is standing in a room that meets a habitat standard.
 *
 * <p>A custom trigger rather than an item check, because the habitat milestones are
 * meant to reward <em>having built something that works</em>. Rewarding possession of a
 * hull plate would be trivial to implement and would teach nothing: you can hold a
 * stack of plates and still be suffocating. Rewarding a room that is actually sealed,
 * actually pressurised and actually breathable is the only version of this that means
 * anything, and the atmosphere tick already computes exactly that every tick.
 *
 * <p>Two levels, because sealing a room and pressurising it are genuinely different
 * accomplishments and most players will manage the first long before the second.
 */
public class HabitatTrigger extends SimpleCriterionTrigger<HabitatTrigger.TriggerInstance> {
    /** How good the room has to be. */
    public enum Standard {
        /** Sealed and big enough to stand in. */
        SHELTER,
        /** Sealed, pressurised, oxygenated and not full of carbon dioxide. */
        BREATHABLE
    }

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Called from the atmosphere tick with what the room actually is. */
    public void trigger(ServerPlayer player, Standard achieved) {
        trigger(player, instance -> instance.matches(achieved));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Standard standard)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        Codec.STRING.xmap(
                                name -> Standard.valueOf(name.toUpperCase(java.util.Locale.ROOT)),
                                standard -> standard.name().toLowerCase(java.util.Locale.ROOT))
                                .fieldOf("standard").forGetter(TriggerInstance::standard)
                ).apply(instance, TriggerInstance::new));

        /**
         * A breathable room also satisfies a shelter criterion.
         *
         * <p>Otherwise a player who built a good habitat first would never be credited
         * with the lesser milestone, and the tree would have a permanent hole in it
         * for doing well.
         */
        public boolean matches(Standard achieved) {
            return achieved == standard
                    || (standard == Standard.SHELTER && achieved == Standard.BREATHABLE);
        }

        public static Criterion<TriggerInstance> reached(Standard standard) {
            return ModCriteria.HABITAT.get().createCriterion(
                    new TriggerInstance(Optional.empty(), standard));
        }
    }
}
