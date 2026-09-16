package play.xponer.astronima.registry;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.menu.ProcessingUiHolder;

/** Screens for the machines. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Astronima.MODID);

    /**
     * Every processing machine — sixteen kinds, one menu type. {@code ProcessingUiHolder} is
     * both the {@code IContainerUIHolder} that builds the screen and, from the network buffer,
     * the object that knows which kind it is — the same reason the sixteen registrations this
     * replaced could already all point at one {@code ProcessingMenu::new} factory (see
     * {@code design/ui-ldlib2-machines.md} §4).
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> PROCESSING =
            MENUS.register("processing", () -> IMenuTypeExtension.create((id, inventory, buffer) -> {
                ProcessingUiHolder holder = new ProcessingUiHolder(id, inventory, buffer);
                return new play.xponer.astronima.menu.ProcessingUiMenu(
                        ModMenus.PROCESSING.get(), id, inventory, holder);
            }));

    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> SYNTHESISER =
            MENUS.register("synthesiser", () -> IMenuTypeExtension.create((id, inventory, buffer) -> {
                var holder = new play.xponer.astronima.menu.SynthesiserUiHolder(id, inventory, buffer);
                return new play.xponer.astronima.menu.SynthesiserMenu(
                        ModMenus.SYNTHESISER.get(), id, inventory, holder);
            }));

    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> MICROSCOPE =
            MENUS.register("microscope", () -> IMenuTypeExtension.create((id, inventory, buffer) -> {
                var holder = new play.xponer.astronima.menu.MicroscopeUiHolder(id, inventory, buffer);
                return new ModularUIContainerMenu(ModMenus.MICROSCOPE.get(), id, inventory, holder);
            }));

    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> INCUBATOR =
            MENUS.register("incubator", () -> IMenuTypeExtension.create((id, inventory, buffer) -> {
                var holder = new play.xponer.astronima.menu.IncubatorUiHolder(id, inventory, buffer);
                return new play.xponer.astronima.menu.IncubatorMenu(
                        ModMenus.INCUBATOR.get(), id, inventory, holder);
            }));

    /** The airlock panel: a schematic, not a slot grid, and it holds no items at all — LDLib2
     *  now, the same {@code IContainerUIHolder} pattern {@link #PROCESSING} uses. */
    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> AIRLOCK =
            MENUS.register("airlock", () -> IMenuTypeExtension.create((id, inventory, buffer) -> {
                var holder = new play.xponer.astronima.menu.AirlockUiHolder(id, inventory, buffer);
                return new play.xponer.astronima.menu.AirlockMenu(
                        ModMenus.AIRLOCK.get(), id, inventory, holder);
            }));

    /**
     * The drive's own screen — see {@code design/data-cells.md} §14. Used to be a plain vanilla
     * {@code ChestMenu}/{@code MenuType.GENERIC_9x6}, which never drew any of this mod's own
     * chrome at all and rendered as flat, textureless grey slots; now the same LDLib2 pattern
     * every other machine in the mod uses.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> STORAGE_DRIVE =
            MENUS.register("storage_drive", () -> IMenuTypeExtension.create((id, inventory, buffer) -> {
                var holder = new play.xponer.astronima.menu.StorageDriveUiHolder(id, inventory, buffer);
                return new ModularUIContainerMenu(ModMenus.STORAGE_DRIVE.get(), id, inventory, holder);
            }));

    /**
     * The storage terminal's own screen — see {@code design/data-cells.md} §14/§18. Used to be a
     * plain vanilla {@code AbstractContainerMenu}, on the reasoning that no existing LDLib2
     * sync-value consumer existed to verify against and this environment cannot run a real
     * client to check one visually; reported back directly as flat, textureless grey with no way
     * to place an item at all. LDLib2 now, the same pattern {@link #STORAGE_DRIVE} uses — but via
     * {@code StorageTerminalMenu}, a thin {@code ModularUIContainerMenu} subclass rather than the
     * bare class {@link #STORAGE_DRIVE} and {@link #MICROSCOPE} use, since the scrollbar's own
     * {@code ContainerData} needs {@code addDataSlots} (protected, unreachable from the plain
     * holder — §18). {@link #PROCESSING}, {@link #SYNTHESISER}, {@link #INCUBATOR}, and
     * {@link #AIRLOCK} all construct the same kind of thin subclass for the same reason
     * (PLAN.md rule 136) — {@link #STORAGE_DRIVE} and {@link #MICROSCOPE} are the only two
     * entries left with no {@code ContainerData} to sync at all.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> STORAGE_TERMINAL =
            MENUS.register("storage_terminal", () -> IMenuTypeExtension.create((id, inventory, buffer) -> {
                var holder = new play.xponer.astronima.menu.StorageTerminalUiHolder(id, inventory, buffer);
                return new play.xponer.astronima.menu.StorageTerminalMenu(
                        ModMenus.STORAGE_TERMINAL.get(), id, inventory, holder);
            }));

    private ModMenus() {}
}
