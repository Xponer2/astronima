package play.xponer.astronima.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.crafting.CraftingTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes JEI the mod's manual. Crafting/smelting recipes appear in JEI automatically;
 * this plugin adds the part JEI can't infer, straight from {@link CraftingTree}:
 * where world-source materials come from, and how the non-recipe machines are used.
 * Look anything up in JEI and you get its complete story.
 */
@JeiPlugin
public class AstronimaJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(Astronima.MODID, "jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    /**
     * Categories, catalysts and recipes, all three walked off {@link MachinePages#all()}.
     *
     * <p>They used to be nine hand-written registrations across three methods, and two
     * machines were added to the game with none of theirs — see {@code MachinePages}. Rule 20:
     * publish the set once, iterate it everywhere, and let a test assert it is complete.
     */
    @Override
    public void registerCategories(mezz.jei.api.registration.IRecipeCategoryRegistration registration) {
        mezz.jei.api.helpers.IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        for (MachinePages page : MachinePages.all()) {
            registration.addRecipeCategories(new ProcessingCategory(
                    helper, page.type(), page.title(), page.machine().get()));
        }
    }

    @Override
    public void registerRecipeCatalysts(mezz.jei.api.registration.IRecipeCatalystRegistration registration) {
        // Clicking the machine in JEI opens its page, which is how a player finds it.
        for (MachinePages page : MachinePages.all()) {
            registration.addRecipeCatalyst(
                    new net.minecraft.world.item.ItemStack(page.machine().get()), page.type());
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (MachinePages page : MachinePages.all()) {
            registration.addRecipes(page.type(), page.pages().get());
        }

        Map<String, List<Component>> infoByItem = new LinkedHashMap<>();
        for (CraftingTree.Source source : CraftingTree.sources()) {
            if (source instanceof CraftingTree.WorldSource world) {
                infoByItem.computeIfAbsent(world.id(), k -> new ArrayList<>())
                        .add(Component.translatable("astronima.jei.source." + path(world.id())));
            }
        }
        CraftingTree.usageNotes().keySet().forEach(id ->
                infoByItem.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(Component.translatable("astronima.jei.usage." + path(id))));

        infoByItem.forEach((id, components) -> {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            if (item != Items.AIR) {
                registration.addIngredientInfo(item, components.toArray(Component[]::new));
            }
        });
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }
}
