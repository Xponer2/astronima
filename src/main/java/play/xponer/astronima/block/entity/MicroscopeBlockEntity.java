package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.item.PetriDishItem;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.lab.Culture;
import play.xponer.astronima.sim.lab.Organism;

/**
 * A thousand times, with oil, and a knob you have to find the plane with.
 *
 * <h2>The one operation that is genuinely physical</h2>
 * Everything else on the bench is chemistry — reagents, times, temperatures. Focusing is a hand on
 * a wheel, and at this magnification the depth of field is under a micron: you hunt for the plane
 * rather than point at it.
 *
 * <p>So the machine holds the slide and the reading is written <strong>only when the field is
 * actually sharp</strong>. A blurred field still shows shapes, and shapes read at the wrong plane
 * are how somebody records chains as clusters.
 */
public class MicroscopeBlockEntity extends ReadableBlockEntity {

    public static final int SLOT_DISH = 0;

    private final NonNullList<ItemStack> slots = NonNullList.withSize(1, ItemStack.EMPTY);

    private final SimpleContainer stage = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.is(ModItems.PETRI_DISH.get());
        }
    };

    public MicroscopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MICROSCOPE.get(), pos, state);
    }

    public Container container() {
        return stage;
    }

    /** What is on the slide, or a blank plate. */
    public Culture culture() {
        ItemStack stack = stage.getItem(SLOT_DISH);
        return stack.isEmpty() ? Culture.BLANK : PetriDishItem.cultureOf(stack);
    }

    /**
     * Writes down what is in the field, and refuses when it is not sharp enough to be sure.
     *
     * <p>Checked here rather than on the client, because "was it in focus" decides what goes into
     * the player's notes — and a client that could answer that could also lie about it.
     *
     * @return true when something was recorded
     */
    public boolean record(double knob) {
        if (!play.xponer.astronima.sim.lab.Focus.isReadable(knob)) {
            return false;
        }
        ItemStack stack = stage.getItem(SLOT_DISH);
        Culture culture = culture();
        if (stack.isEmpty() || !culture.hasColonies()) {
            return false;
        }
        Organism organism = culture.growing();
        if (organism == null) {
            return false;
        }
        PetriDishItem.setCulture(stack,
                culture.seen(organism.shape(), organism.arrangement()));
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        slots.set(0, stage.getItem(SLOT_DISH));
        net.minecraft.world.ContainerHelper.saveAllItems(output, slots);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stage.clearContent();
        slots.set(0, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(input, slots);
        stage.setItem(SLOT_DISH, slots.get(0));
    }
}
