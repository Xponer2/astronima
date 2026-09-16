package play.xponer.astronima.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.crafting.ForgedToolRecipe;

/** Recipe serializers for crafting that has to carry data through it. */
public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Astronima.MODID);

    /**
     * Shaped crafting that carries a head's forged quality into the finished tool,
     * without which the cold forge would be theatre.
     */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ForgedToolRecipe>>
            FORGED_TOOL = SERIALIZERS.register("forged_tool",
                    () -> new RecipeSerializer<>(ForgedToolRecipe.MAP_CODEC,
                            ForgedToolRecipe.STREAM_CODEC));

    private ModRecipeSerializers() {}
}
