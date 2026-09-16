package play.xponer.astronima.menu;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import play.xponer.astronima.airlock.AirlockStatus;
import play.xponer.astronima.block.entity.AirlockControllerBlockEntity;
import play.xponer.astronima.client.screen.AirlockUi;
import play.xponer.astronima.registry.ModMenus;

/**
 * The airlock panel's real menu — {@code design/ui-ldlib2-machines.md}'s pattern applied to a
 * slot-free screen. Carries no item slots at all (an airlock holds no items), so unlike
 * {@link ProcessingUiHolder} there is no {@code Container} here — just the same
 * {@link ContainerData} view {@code AirlockMenu} always carried, and the {@link BlockPos} every
 * terminal/scan/cycle packet already addresses by.
 *
 * <p>That {@link ContainerData} — every gauge {@link #status()} decodes — actually reaches the
 * client via {@link AirlockMenu} (PLAN.md rule 136); before that fix it read permanently-frozen
 * zeros (rule 135).
 */
public final class AirlockUiHolder implements MenuProvider, IContainerUIHolder {

    private final ContainerData data;
    private final BlockPos pos;

    /** Server-side: a real controller backs every read. */
    public AirlockUiHolder(AirlockControllerBlockEntity controller, BlockPos pos) {
        this(dataFor(controller), pos);
    }

    /** Client-side: read out of the open-screen packet, filled in by vanilla's own sync. */
    public AirlockUiHolder(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(new SimpleContainerData(AirlockStatus.SIZE), buffer.readBlockPos());
    }

    private AirlockUiHolder(ContainerData data, BlockPos pos) {
        this.data = data;
        this.pos = pos;
    }

    /** Reads a controller's live status; every value is derived, so none can go stale. */
    private static ContainerData dataFor(AirlockControllerBlockEntity controller) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return controller.status().encode(index);
            }

            @Override
            public void set(int index, int value) {
                // The server owns all of it. The one thing the player can change — the
                // cycle command — goes through its own packet so it can be validated.
            }

            @Override
            public int getCount() {
                return AirlockStatus.SIZE;
            }
        };
    }

    public BlockPos pos() {
        return pos;
    }

    /** What to draw, decoded from the synced array. */
    public AirlockStatus status() {
        return AirlockStatus.decode(data::get);
    }

    /** Package-visible for {@link AirlockMenu}'s own constructor, the one place that actually
     *  registers this for sync. */
    ContainerData data() {
        return data;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima.airlock_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AirlockMenu(ModMenus.AIRLOCK.get(), id, inventory, this);
    }

    @Override
    public ModularUI createUI(Player player) {
        return AirlockUi.build(this, player);
    }

    @Override
    public boolean isStillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof AirlockControllerBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64;
    }
}
