package play.xponer.astronima.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import play.xponer.astronima.registry.ModDataComponents;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.registry.ModRecipeSerializers;
import play.xponer.astronima.sim.tool.ToolHead;

/**
 * A shaped recipe that carries the forged quality of its heads into the finished tool.
 *
 * <p>Without this the cold forge is theatre. A plain shaped recipe discards data
 * components, so the work hardening, the optimum, the crack risk — the whole reason
 * {@code sim/metal/ColdWorking} exists — evaporated at the crafting grid and every
 * pickaxe came out identical no matter how well it was made.
 *
 * <p>Quality is the <strong>mean</strong> of the heads that went in, and the tool is
 * cracked if <em>any</em> of them was. Both are the honest physical answer: a tool is
 * as good as the average of its metal and as sound as its worst piece, because a crack
 * in one part of a head is where it will fail.
 *
 * <p>Reuses vanilla's {@link ShapedRecipePattern} for matching and reports an ordinary
 * shaped display, so the recipe book and JEI keep treating it as the plain shaped recipe
 * it looks like. The only thing that differs is what comes out.
 */
public class ForgedToolRecipe extends NormalCraftingRecipe {

    public static final MapCodec<ForgedToolRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
                    ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
            ).apply(instance, ForgedToolRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgedToolRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
                    CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
                    ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
                    ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
                    ForgedToolRecipe::new);

    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;

    public ForgedToolRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
                            ShapedRecipePattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.pattern = pattern;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input);
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(this.pattern.ingredients());
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapedCraftingRecipeDisplay(
                this.pattern.width(),
                this.pattern.height(),
                this.pattern.ingredients().stream()
                        .map(ingredient -> ingredient.map(Ingredient::display)
                                .orElse(SlotDisplay.Empty.INSTANCE))
                        .toList(),
                new SlotDisplay.ItemStackSlotDisplay(this.result),
                new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack tool = this.result.create();

        double qualitySum = 0;
        int heads = 0;
        int grains = 0;
        boolean cracked = false;
        float nickelSum = 0;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.is(ModItems.IRON_NICKEL_GRAINS.get())) {
                grains++;
                nickelSum += stack.getOrDefault(ModDataComponents.METAL_NICKEL.get(), 0.08f);
                continue;
            }
            if (!stack.is(ModItems.TOOL_HEAD.get())) {
                continue;
            }
            heads++;
            qualitySum += stack.getOrDefault(ModDataComponents.METAL_QUALITY.get(), 0.5f);
            nickelSum += stack.getOrDefault(ModDataComponents.METAL_NICKEL.get(), 0.08f);
            // One cracked piece is where the tool will fail, so it makes the whole
            // tool cracked. A tool is as sound as its worst part.
            cracked |= stack.getOrDefault(ModDataComponents.METAL_CRACKED.get(), false);
        }

        if (heads == 0 && grains > 0) {
            // Pressed straight from grains: a green compact, held together at the grain
            // contacts and nothing more. Fixed and low, because pressing loose powder
            // admits of no skill — the way to a better tool is the forge.
            tool.set(ModDataComponents.TOOL_STATE.get(), ToolHead.pressed());
            tool.set(ModDataComponents.METAL_NICKEL.get(), nickelSum / grains);
            return tool;
        }
        if (heads == 0) {
            // Nothing to carry. Shouldn't happen for a matched pattern, but a recipe
            // that silently produced a zero-hardness tool would be worse than one that
            // produces an ordinary middling one.
            return tool;
        }

        double hardness = qualitySum / heads;
        tool.set(ModDataComponents.TOOL_STATE.get(), ToolHead.forged(hardness, cracked));
        tool.set(ModDataComponents.METAL_NICKEL.get(), nickelSum / heads);
        return tool;
    }

    @Override
    public RecipeSerializer<ForgedToolRecipe> getSerializer() {
        return ModRecipeSerializers.FORGED_TOOL.get();
    }
}
