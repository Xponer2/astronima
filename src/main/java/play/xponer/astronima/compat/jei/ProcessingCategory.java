package play.xponer.astronima.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.client.hud.WrappedText;

/**
 * A JEI page for one machine, drawn rather than written.
 *
 * <p>The pages exist because a processing step that is not in JEI may as well not
 * exist — for this project JEI is the wiki. They are <em>drawn</em> because what they
 * have to convey is a shape: one control, two consequences moving against each other.
 * Two earlier versions listed the numbers instead, first for a single setting and then
 * as a table, and readers still could not tell what the control was for. A table
 * contains the relationship; a plot shows it.
 *
 * <p>Styled with {@link MachineFrame}, the same kit as the machine screens, so the
 * documentation and the thing it documents look like one piece of equipment.
 */
public class ProcessingCategory implements IRecipeCategory<ProcessingRecipe> {

    private static final int WIDTH = 168;

    /** Room a line of text has on a JEI page, derived from the page rather than typed. */
    private static final int TEXT_WIDTH = WIDTH - 8;
    /**
     * Sized for the longest note block once wrapped, not guessed. The pages
     * carry full sentences and Minecraft's font is variable width, so a height
     * chosen by eye is a height that clips on the one page nobody checked.
     */
    private static final int HEIGHT = 144;

    private static final int NOTE_LINE = 9;

    private static final int CHART_X = 26;
    private static final int CHART_Y = 24;
    private static final int CHART_W = 114;
    private static final int CHART_H = 46;

    /** Vertical gap between the primary input and each extra one below it — clear of the chart,
     *  which starts at {@link #CHART_X} = 26, since the input column ends at x = 21. */
    private static final int EXTRA_INPUT_STEP = 22;

    private static final int TITLE_X = play.xponer.astronima.client.hud.MachinePageLayout.TITLE_X;
    private static final int TITLE_Y = play.xponer.astronima.client.hud.MachinePageLayout.TITLE_Y;

    private final RecipeType<ProcessingRecipe> type;
    private final Component title;
    private final IDrawable icon;

    public ProcessingCategory(IGuiHelper helper, RecipeType<ProcessingRecipe> type,
                              String title, Block iconBlock) {
        this.type = type;
        this.title = Component.literal(title);
        this.icon = helper.createDrawableItemLike(iconBlock);
    }

    @Override
    public RecipeType<ProcessingRecipe> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ProcessingRecipe recipe,
                          IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 3, 4).addItemStack(recipe.input());

        // A real second input slot, not a room reagent named only in prose - the one thing
        // design/iron-smelter.md needed that no existing two-reagent page had. Stacked directly
        // below the primary input, in the column the chart (which starts at x=26) never reaches.
        for (int i = 0; i < recipe.extraInputs().size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 3, 4 + EXTRA_INPUT_STEP * (i + 1))
                    .addItemStack(recipe.extraInputs().get(i));
        }

        // Positions from the page's own plan, not counted out here: the old loop stepped twenty
        // pixels per product with nothing stopping it leaving a 144-pixel page (rule 27).
        for (int i = 0; i < recipe.outputs().size(); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT,
                            play.xponer.astronima.client.hud.MachinePageLayout.outputX(i),
                            play.xponer.astronima.client.hud.MachinePageLayout.outputY(i))
                    .addItemStack(recipe.outputs().get(i));
        }
    }

    /**
     * Draws the page: heading, the plotted curve, and the takeaway underneath.
     *
     * <p>The chart carries the argument and the notes only name it, which is the right
     * division of labour — a caption should say what you are looking at rather than
     * substitute for it.
     */
    @Override
    public void draw(ProcessingRecipe recipe, IRecipeSlotsView slots,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;

        MachineFrame.slot(graphics, 4, 5);
        // Cut to the room it actually has. Drawn full length it ran into the product column, which
        // is the whole class of fault rule 27 exists for.
        MachineFrame.value(graphics, font,
                font.plainSubstrByWidth(recipe.title(),
                        play.xponer.astronima.client.hud.MachinePageLayout
                                .titleWidth(recipe.outputs().size())),
                TITLE_X, TITLE_Y, TEXT_WIDTH,
                MachineFrame.TEXT);

        Chart.draw(graphics, font, CHART_X, CHART_Y, CHART_W, CHART_H,
                recipe.axisLabel(), recipe.series());

        // Wrapped rather than drawn as single lines: these are sentences, the font is
        // variable width, and the page is a fixed size. Trusting them to fit is how
        // they ended up hanging off the right edge.
        int noteY = CHART_Y + CHART_H + 16;
        for (String note : recipe.notes()) {
            if (noteY + NOTE_LINE > HEIGHT) {
                // Off the bottom is not "drawn": it is a sentence the player is never shown while
                // the page pretends to have told them. Say so instead.
                MachineFrame.value(graphics, font, "...", 4, HEIGHT - NOTE_LINE, TEXT_WIDTH,
                        MachineFrame.TEXT_DIM);
                break;
            }
            noteY = WrappedText.draw(graphics, font, note, 4, noteY,
                    play.xponer.astronima.client.hud.MachinePageLayout
                            .noteWidth(recipe.outputs().size()),
                    NOTE_LINE, MachineFrame.TEXT_DIM);
        }
    }
}
