package play.xponer.astronima.menu;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.block.entity.SynthesiserBlockEntity;
import play.xponer.astronima.client.screen.SynthesiserUi;
import play.xponer.astronima.registry.ModMenus;
import play.xponer.astronima.sim.lab.Antibiotic;

/** The synthesiser's real menu — {@code design/ui-ldlib2-machines.md}'s pattern, one feed slot
 *  and one selected-index {@link ContainerData} slot.
 *
 *  <p>That slot actually reaches the client via {@link SynthesiserMenu} (PLAN.md rule 136);
 *  before that fix it read a permanently-frozen zero (rule 135). */
public final class SynthesiserUiHolder implements MenuProvider, IContainerUIHolder {

    private static final int DATA_SIZE = 1;

    private final Container vial;
    private final ContainerData data;
    private final BlockPos pos;

    /** Server-side: a real machine backs every read and write. */
    public SynthesiserUiHolder(SynthesiserBlockEntity machine, BlockPos pos) {
        this(machine.container(), dataFor(machine), pos);
    }

    /** Client-side: read out of the open-screen packet, filled in by vanilla's own sync. */
    public SynthesiserUiHolder(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(new SimpleContainer(1), new SimpleContainerData(DATA_SIZE), buffer.readBlockPos());
    }

    private SynthesiserUiHolder(Container vial, ContainerData data, BlockPos pos) {
        this.vial = vial;
        this.data = data;
        this.pos = pos;
    }

    private static ContainerData dataFor(SynthesiserBlockEntity machine) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return Antibiotic.all().indexOf(machine.chosen());
            }

            @Override
            public void set(int index, int value) {
                machine.setFromDial(value / (double) Math.max(1, Antibiotic.all().size() - 1));
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    public BlockPos pos() {
        return pos;
    }

    public Container container() {
        return vial;
    }

    public int chosenIndex() {
        return Math.clamp(data.get(0), 0, Antibiotic.all().size() - 1);
    }

    public ItemStack vial() {
        return vial.getItem(SynthesiserBlockEntity.SLOT_VIAL);
    }

    /** Package-visible for {@link SynthesiserMenu}'s own constructor, the one place that
     *  actually registers this for sync. */
    ContainerData data() {
        return data;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.synthesiser");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SynthesiserMenu(ModMenus.SYNTHESISER.get(), id, inventory, this);
    }

    @Override
    public ModularUI createUI(Player player) {
        return SynthesiserUi.build(this, player);
    }

    @Override
    public boolean isStillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
