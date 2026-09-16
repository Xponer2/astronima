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
import play.xponer.astronima.block.entity.IncubatorBlockEntity;
import play.xponer.astronima.client.screen.IncubatorUi;
import play.xponer.astronima.registry.ModMenus;

/**
 * The incubator's real menu — {@code design/ui-ldlib2-machines.md}'s pattern, one feed slot
 * (the same slot serves as both input and output: a dish goes in and the same dish comes back
 * changed, unlike {@link ProcessingMenu}'s feed/product split).
 *
 * <p>Its one {@code ContainerData} slot (the thermostat setpoint) actually reaches the client via
 * {@link IncubatorMenu} (PLAN.md rule 136); before that fix it read a permanently-frozen zero
 * (rule 135).
 */
public final class IncubatorUiHolder implements MenuProvider, IContainerUIHolder {

    private static final int DATA_SIZE = 1;

    private final Container dish;
    private final ContainerData data;
    private final BlockPos pos;

    /** Server-side: a real machine backs every read and write. */
    public IncubatorUiHolder(IncubatorBlockEntity machine, BlockPos pos) {
        this(machine.container(), dataFor(machine), pos);
    }

    /** Client-side: read out of the open-screen packet, filled in by vanilla's own sync. */
    public IncubatorUiHolder(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(new SimpleContainer(1), new SimpleContainerData(DATA_SIZE),
                buffer.readBlockPos());
    }

    private IncubatorUiHolder(Container dish, ContainerData data, BlockPos pos) {
        this.dish = dish;
        this.data = data;
        this.pos = pos;
    }

    /** The one number the screen needs, read straight off the machine. */
    private static ContainerData dataFor(IncubatorBlockEntity incubator) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return (int) Math.round(incubator.setPointK() * 10);
            }

            @Override
            public void set(int index, int value) {
                incubator.setPointK(value / 10.0);
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
        return dish;
    }

    /** What the thermostat is set to, in kelvin. */
    public double setPointK() {
        return data.get(0) / 10.0;
    }

    /** The plate inside, so the screen can draw the real thing rather than an icon. */
    public ItemStack dish() {
        return dish.getItem(IncubatorBlockEntity.SLOT_DISH);
    }

    /** Package-visible for {@link IncubatorMenu}'s own constructor, the one place that actually
     *  registers this for sync. */
    ContainerData data() {
        return data;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.incubator");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new IncubatorMenu(ModMenus.INCUBATOR.get(), id, inventory, this);
    }

    @Override
    public ModularUI createUI(Player player) {
        return IncubatorUi.build(this, player);
    }

    @Override
    public boolean isStillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
