package play.xponer.astronima.physio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import play.xponer.astronima.sim.physio.Macronutrition;

/**
 * The saved/synced shape of a player's three real macronutrient reserves
 * ({@code sim.physio.Macronutrition.State}), the same thin wrapper
 * {@link CarriedContamination} already is over its own sim record.
 */
public record CarriedMacronutrition(double protein, double carbohydrate, double fat) {

    public static final CarriedMacronutrition FULL =
            new CarriedMacronutrition(1.0, 1.0, 1.0);

    public static final Codec<CarriedMacronutrition> CODEC = RecordCodecBuilder.create(it -> it.group(
            Codec.DOUBLE.fieldOf("protein").forGetter(CarriedMacronutrition::protein),
            Codec.DOUBLE.fieldOf("carbohydrate").forGetter(CarriedMacronutrition::carbohydrate),
            Codec.DOUBLE.fieldOf("fat").forGetter(CarriedMacronutrition::fat))
            .apply(it, CarriedMacronutrition::new));

    public Macronutrition.State asState() {
        return new Macronutrition.State(protein, carbohydrate, fat);
    }

    public static CarriedMacronutrition of(Macronutrition.State state) {
        return new CarriedMacronutrition(state.protein(), state.carbohydrate(), state.fat());
    }
}
