package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.item.DoseItem;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.lab.Antibiotic;

/**
 * Fills a blank vial with whichever drug you tell it to.
 *
 * <h2>It does not read the plate, and that is the design</h2>
 * The machine will happily make the wrong thing. Nothing here checks the antibiogram, because the
 * decision the whole tier is built around is <em>which drug to make</em> — and a synthesiser that
 * looked at the plate for you would take it away.
 *
 * <p>The plate tells you. The bench proves it. This just does as it is told.
 */
public class SynthesiserBlockEntity extends ReadableBlockEntity {

    public static final int SLOT_VIAL = 0;

    private final NonNullList<ItemStack> slots = NonNullList.withSize(1, ItemStack.EMPTY);

    private final SimpleContainer vial = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.is(ModItems.DOSE.get());
        }
    };

    private int chosen;

    public SynthesiserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SYNTHESISER.get(), pos, state);
    }

    public Container container() {
        return vial;
    }

    public Antibiotic chosen() {
        return Antibiotic.all().get(Math.clamp(chosen, 0, Antibiotic.all().size() - 1));
    }

    /** Which drug the selector is on, as a dial position — so it rides the usual setting packet. */
    public void setFromDial(double part) {
        chosen = (int) Math.round(Math.clamp(part, 0, 1) * (Antibiotic.all().size() - 1));
        fill();
        setChanged();
    }

    /**
     * Programmes whatever blank vial is in the slot.
     *
     * <p>Immediate rather than timed. The cost of a wrong drug is not the minute it took to make —
     * it is the resistance it breeds, and putting a progress bar in front of that would only make
     * the real penalty feel like the small one.
     */
    private void fill() {
        ItemStack stack = vial.getItem(SLOT_VIAL);
        if (!stack.isEmpty() && DoseItem.drugOf(stack) == null) {
            DoseItem.setDrug(stack, chosen());
        }
    }

    public ItemStack vial() {
        return vial.getItem(SLOT_VIAL);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        fill();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("chosen", chosen);
        slots.set(0, vial.getItem(SLOT_VIAL));
        net.minecraft.world.ContainerHelper.saveAllItems(output, slots);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        chosen = input.getIntOr("chosen", 0);
        vial.clearContent();
        slots.set(0, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(input, slots);
        vial.setItem(SLOT_VIAL, slots.get(0));
    }
}
