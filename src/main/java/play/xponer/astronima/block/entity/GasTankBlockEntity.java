package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.GasMixture;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.pipe.PressureVessel;

/**
 * The gas a tank is holding.
 *
 * <p>Stored as a {@link RoomState} so the network can treat it as an ordinary node —
 * the plumbing does not need to know whether the volume on the end of a pipe is a
 * habitat or a bottle, and keeping them the same type is what stops the network
 * growing a special case for every kind of container.
 *
 * <p>Its volume is the vessel's, not a block's: two cubic metres in a one-metre cube,
 * which is what a pressure vessel is <em>for</em>.
 */
public class GasTankBlockEntity extends ReadableBlockEntity {
    private static final double AMBIENT_K = 293.15;

    private final RoomState contents = new RoomState(
            0, 1, new GasMixture(), AMBIENT_K);

    public GasTankBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.GAS_TANK.get(), pos, state);
    }

    /**
     * For a subclass registered under its own {@link BlockEntityType} — see
     * {@link play.xponer.astronima.block.entity.CryoTankBlockEntity}, which is a gas tank plus a
     * liquid phase and needs {@code instanceof GasTankBlockEntity} (how {@code PipeNetworks}
     * flood-fills the pipe network) to keep matching it for free.
     */
    protected GasTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // volumeBlocks is the network's unit; the vessel's real volume is set here so
        // pressure comes out right for a tank rather than for a one-metre room.
        contents.setVolumeBlocks((int) Math.max(1, Math.round(PressureVessel.TANK_VOLUME_M3)));
    }

    public RoomState contents() {
        return contents;
    }

    public double pressureKPa() {
        return contents.pressureKPa();
    }

    public PressureVessel.Condition condition() {
        return PressureVessel.classify(pressureKPa());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // One key per species rather than a nested tag: a tank that gains a gas in a
        // later version then simply reads zero for it instead of failing to load.
        for (Gas gas : Gas.values()) {
            output.putDouble(key(gas), contents.gases().get(gas));
        }
        output.putDouble("temperature", contents.temperatureK());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (Gas gas : Gas.values()) {
            contents.removeGas(gas, contents.gases().get(gas));
        }
        double temperature = input.getDoubleOr("temperature", AMBIENT_K);
        contents.setTemperatureK(temperature);
        for (Gas gas : Gas.values()) {
            double amount = input.getDoubleOr(key(gas), 0.0);
            if (amount > 0) {
                contents.addGasAt(gas, amount, temperature);
            }
        }
    }

    private static String key(Gas gas) {
        return "gas_" + gas.name().toLowerCase(java.util.Locale.ROOT);
    }
}
