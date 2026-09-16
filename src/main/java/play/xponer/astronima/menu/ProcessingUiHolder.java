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
import play.xponer.astronima.block.entity.ProcessingBlockEntity;
import play.xponer.astronima.client.screen.ProcessingUi;
import play.xponer.astronima.menu.ProcessingMenu.Kind;
import play.xponer.astronima.registry.ModMenus;
import play.xponer.astronima.sim.machine.Calibration;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.ore.ElectrolysisSpecies;
import play.xponer.astronima.sim.ore.RetortProcess;

import static play.xponer.astronima.menu.ProcessingMenu.DATA_CALIBRATING;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_D2O_FRACTION;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_CALIBRATION_ERROR;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_DRIFT;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_ELECTROLYSIS_TARGET;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_FEED_MICRONS;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_FLUX_RATIO;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_GRADE;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_PROCESS;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_POWERED;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_PROGRESS;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_REAGENT_A;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_REAGENT_B;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_REAGENT_C;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_RECOVERY;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_ROOM_KPA;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_SETTING;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_SIZE;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_SLS_POWER_W;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_SLS_SPEED_MMS;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_SUNLIGHT;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_TEMPERATURE_K;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_VACUUM;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_WORK_REQUIRED;
import static play.xponer.astronima.menu.ProcessingMenu.DATA_WORK_STATE;
import static play.xponer.astronima.menu.ProcessingMenu.SETTING_SCALE;

/**
 * The real menu behind every processing machine — {@code design/ui-ldlib2-machines.md}.
 *
 * <p>Both a vanilla {@link MenuProvider} (what a block's {@code useWithoutItem} still opens,
 * unchanged in shape) and an LDLib2 {@link IContainerUIHolder} (what actually builds the
 * screen). One class replaces the sixteen near-identical private {@code Provider} records that
 * used to live one per {@code *Block.java}, and replaces {@code ProcessingMenu}'s own instance
 * side — {@code progress()}, {@code setting()}, and the rest — which had nowhere honest to live
 * once {@code ProcessingMenu} stopped being a Minecraft menu.
 *
 * <p><strong>Two constructors, same shape the old {@code ProcessingMenu} had.</strong> The
 * server-side one is built directly from a real {@link ProcessingBlockEntity} — the container
 * <em>is</em> the block entity, and the data view is {@link ProcessingMenu#dataFor}. The
 * client-side one, read out of the open-screen packet, has no block entity to reach: a
 * disconnected {@link SimpleContainer}/{@link SimpleContainerData} that vanilla's own slot-sync
 * and data-sync packets fill in from there, exactly like every other {@code AbstractContainerMenu}
 * always has.
 *
 * <p>The {@link ContainerData} {@link ProcessingMenu#dataFor} builds — progress, setting, every
 * per-kind reading — actually reaches the client via {@link ProcessingUiMenu} (PLAN.md rule 136);
 * before that fix it read a permanently-frozen zero (rule 135), for every one of the ~16
 * processing-machine kinds sharing this one holder.
 */
public final class ProcessingUiHolder implements MenuProvider, IContainerUIHolder {

    private final Kind kind;
    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;

    /** Server-side: a real machine backs every read and write. */
    public ProcessingUiHolder(ProcessingBlockEntity machine, BlockPos pos) {
        this(machine.kind(), machine, ProcessingMenu.dataFor(machine), pos);
    }

    /** Client-side: read out of the open-screen packet, filled in by vanilla's own sync. */
    public ProcessingUiHolder(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(Kind.values()[buffer.readVarInt()], new SimpleContainer(buffer.readVarInt()),
                new SimpleContainerData(DATA_SIZE), buffer.readBlockPos());
    }

    private ProcessingUiHolder(Kind kind, Container container, ContainerData data, BlockPos pos) {
        this.kind = kind;
        this.container = container;
        this.data = data;
        this.pos = pos;
    }

    // --------------------------------------------------------------- MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.astronima." + blockId(kind));
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ProcessingUiMenu(ModMenus.PROCESSING.get(), id, inventory, this);
    }

    /** The registry id every {@code *Block.java} already writes into the open-screen packet. */
    private static String blockId(Kind kind) {
        return switch (kind) {
            case CRUSHER -> "ore_crusher";
            case SEPARATOR -> "magnetic_separator";
            case FORGE -> "cold_forge";
            case RETORT -> "solar_retort";
            case WINNOWER -> "winnowing_table";
            case REFINER -> "carbonyl_refiner";
            case FLUIDBED -> "fluidized_bed";
            case ELECTROLYSIS -> "electrolysis_cell";
            case SLS -> "sls_printer";
            case CRACKING_TOWER -> "cracking_tower";
            case POLYMERIZER -> "polymerizer";
            case WATER_ELECTROLYZER -> "water_electrolyzer";
            case SABATIER_REACTOR -> "sabatier_reactor";
            case BOSCH_REACTOR -> "bosch_reactor";
            case TROILITE_ROASTER -> "troilite_roaster";
            case SULFURIC_ACID_PLANT -> "sulfuric_acid_plant";
            case HEAVY_WATER_CELL -> "heavy_water_cell";
            case TITANIUM_CELL -> "titanium_cell";
            case ZONE_REFINER -> "zone_refiner";
            case HF_DIGESTER -> "hf_digester";
            case INDUCTION_FURNACE -> "induction_furnace";
            case IRON_SMELTER -> "iron_smelter";
            case FREEZE_DRYER -> "freeze_dryer";
            case DOWNS_CELL -> "downs_cell";
            case ETCH_STATION -> "etch_station";
        };
    }

    // --------------------------------------------------------------- IContainerUIHolder

    @Override
    public ModularUI createUI(Player player) {
        return ProcessingUi.build(this, player);
    }

    @Override
    public boolean isStillValid(Player player) {
        return container.stillValid(player);
    }

    // --------------------------------------------------------------- machine reads

    public Kind kind() {
        return kind;
    }

    public BlockPos pos() {
        return pos;
    }

    public Container container() {
        return container;
    }

    /** Package-visible for {@link ProcessingUiMenu}'s own constructor, the one place that
     *  actually registers this for sync. */
    ContainerData data() {
        return data;
    }

    /** The item currently in gui slot {@code index} — feed is always 0. */
    public ItemStack item(int index) {
        return container.getItem(index);
    }

    public float progress() {
        return data.get(DATA_PROGRESS) / (float) SETTING_SCALE;
    }

    /** The machine's operating parameter, 0..1. */
    public double setting() {
        return data.get(DATA_SETTING) / (double) SETTING_SCALE;
    }

    public int workRequired() {
        return data.get(DATA_WORK_REQUIRED);
    }

    public float lastRecovery() {
        return data.get(DATA_RECOVERY) / (float) SETTING_SCALE;
    }

    public float lastGrade() {
        return data.get(DATA_GRADE) / (float) SETTING_SCALE;
    }

    /** How far the forge's chamber is pumped down, 0..1. */
    public float evacuation() {
        return data.get(DATA_VACUUM) / (float) SETTING_SCALE;
    }

    /**
     * What this machine's setting does when nobody is looking, straight from the machine.
     *
     * <p>Defensive about the ordinal for the same reason every other one here is: a desync should
     * make a panel dull, not throw.
     */
    public Calibration.Drift drift() {
        Calibration.Drift[] all = Calibration.Drift.values();
        int ordinal = data.get(DATA_DRIFT);
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : Calibration.Drift.NONE;
    }

    /** Whether this machine has no handle in its window, because a wrench sets it. */
    public boolean isSetByWrench() {
        return drift().isSetByWrench();
    }

    /** Where the operator left it, as against where it has drifted to. */
    public double calibratedTo() {
        return Math.clamp(setting() - drift().direction() * calibrationError(), 0.0, 1.0);
    }

    /** How far the setting has drifted, 0..1. */
    public double calibrationError() {
        return data.get(DATA_CALIBRATION_ERROR) / (double) SETTING_SCALE;
    }

    /** How far through a calibration, 0..1. Zero when nobody has a wrench in it. */
    public double calibrating() {
        return data.get(DATA_CALIBRATING) / (double) SETTING_SCALE;
    }

    public int feedMicrons() {
        return data.get(DATA_FEED_MICRONS);
    }

    public int temperatureK() {
        return data.get(DATA_TEMPERATURE_K);
    }

    /** The air around a winnowing table, kPa — what decides whether it can lift anything. */
    public double roomPressureKPa() {
        return data.get(DATA_ROOM_KPA);
    }

    /** How much sun is on the mirror, 0..1. */
    public float sunlight() {
        return data.get(DATA_SUNLIGHT) / (float) SETTING_SCALE;
    }

    public WorkState workState() {
        WorkState[] all = WorkState.values();
        return all[Math.clamp(data.get(DATA_WORK_STATE), 0, all.length - 1)];
    }

    /** The window the retort's dial is currently hunting. */
    public RetortProcess retortProcess() {
        RetortProcess[] all = RetortProcess.values();
        return all[Math.clamp(data.get(DATA_PROCESS), 0, all.length - 1)];
    }

    /** Which metal the electrolysis cell is currently tuned for, read off the electrode. */
    public ElectrolysisSpecies electrolysisTarget() {
        ElectrolysisSpecies[] all = ElectrolysisSpecies.values();
        return all[Math.clamp(data.get(DATA_ELECTROLYSIS_TARGET), 0, all.length - 1)];
    }

    /** Where the printer's marker sits on the process plane, in watts and mm/s. */
    public double slsPowerW() {
        return data.get(DATA_SLS_POWER_W);
    }

    public double slsSpeedMmS() {
        return data.get(DATA_SLS_SPEED_MMS);
    }

    /** What share of a full electrical draw the machine last actually got, 0..1 — meaningless
     *  while hand-cranked ({@link WorkState#CRANKING}), since the machine never even asks. */
    public double poweredFraction() {
        return data.get(DATA_POWERED) / (double) SETTING_SCALE;
    }

    /** The D2O fraction of whatever water bottle is currently in the feed slot. */
    public double d2oFraction() {
        return data.get(DATA_D2O_FRACTION) / (double) SETTING_SCALE;
    }

    /** How much of the last iron-smelter batch's real stoichiometric flux need was supplied. */
    public double fluxRatio() {
        return data.get(DATA_FLUX_RATIO) / (double) SETTING_SCALE;
    }

    /** Generic room-reagent stock readings, 0..1 — which real gas each channel means depends on
     *  {@link #kind()}, per {@code ProcessingMenu.dataFor}'s own switch (design/machines.md's own
     *  Update section). */
    public double reagentA() {
        return data.get(DATA_REAGENT_A) / (double) SETTING_SCALE;
    }

    public double reagentB() {
        return data.get(DATA_REAGENT_B) / (double) SETTING_SCALE;
    }

    public double reagentC() {
        return data.get(DATA_REAGENT_C) / (double) SETTING_SCALE;
    }
}
