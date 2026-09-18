package play.xponer.astronima.physio;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.Config;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.physio.MacroProfiles;
import play.xponer.astronima.sim.physio.Macronutrition;

/**
 * The player's end of {@code design/macronutrients.md}: what real food actually feeds, and what
 * real time actually drains. Every entry point here is a verb the player already had — eating and
 * time passing — the same "no new key" discipline {@link Contaminations} already names.
 */
public final class Nutrition {

    /** Below this, a reserve's own real symptom starts (design/macronutrients.md §3f). */
    private static final double DEFICIENT_THRESHOLD = 0.3;
    /** Above this, a reserve stops earning its symptom - a small band so it does not flicker
     *  right at the boundary. */
    private static final double RECOVERED_THRESHOLD = 0.4;
    /** 6 real seconds, refreshed every real tick this stays deficient - the same short,
     *  self-renewing shape {@code Infections.tick}'s own severe-stage Weakness already uses. */
    private static final int EFFECT_DURATION_TICKS = 120;

    public static Macronutrition.State of(Player player) {
        return player.getData(ModAttachments.MACRONUTRITION.get()).asState();
    }

    private static void set(Player player, Macronutrition.State state) {
        player.setData(ModAttachments.MACRONUTRITION.get(), CarriedMacronutrition.of(state));
    }

    /** Real time passing: each reserve drains at its own real rate (design/macronutrients.md §2). */
    public static void tick(Player player, double seconds) {
        Macronutrition.State state =
                Macronutrition.drained(of(player), seconds, Config.METABOLISM_SCALE.get());
        set(player, state);
        // A severe fat deficiency must never mask an equally-real protein or carbohydrate one
        // (design/macronutrients.md §3f) - each reserve earns its own real symptom on its own
        // terms, not just whichever is worst overall. Fat gets none: its real consequence is
        // entirely through overallSufficiency()'s own min() into immunityOf (§5).
        applyIfDeficient(player, MobEffects.WEAKNESS, state.protein());
        applyIfDeficient(player, MobEffects.MINING_FATIGUE, state.carbohydrate());
    }

    private static void applyIfDeficient(Player player, Holder<MobEffect> effect, double sufficiency) {
        boolean active = player.hasEffect(effect);
        if (sufficiency < DEFICIENT_THRESHOLD || (active && sufficiency < RECOVERED_THRESHOLD)) {
            player.addEffect(new MobEffectInstance(effect, EFFECT_DURATION_TICKS, 0, true, false));
        }
    }

    /**
     * Finishing a real food item — real composition in, real reserves up. A food with no real
     * {@link MacroProfiles} entry contributes nothing (design/macronutrients.md §2, §6): real
     * calories this design has no modelled macro content for, not a guess.
     */
    public static void consumed(Player player, ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Macronutrition.MacroProfile profile = MacroProfiles.of(id).orElse(Macronutrition.MacroProfile.NONE);
        set(player, Macronutrition.fed(of(player), profile, food.nutrition()));
    }

    /** How well fed this body really is — the one real number {@code immunityOf} now reads
     *  (design/macronutrients.md §3g): the scarcest of the three real reserves, not their average. */
    public static double overallSufficiency(Player player) {
        return of(player).overallSufficiency();
    }

    public static Macronutrition.Macro mostDeficient(Player player) {
        return of(player).mostDeficient();
    }

    private Nutrition() {}
}
