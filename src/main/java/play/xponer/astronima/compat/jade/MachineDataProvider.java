package play.xponer.astronima.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.block.entity.GasPumpBlockEntity;
import play.xponer.astronima.block.entity.GasTankBlockEntity;
import play.xponer.astronima.block.entity.OxygenCandleBlockEntity;
import play.xponer.astronima.block.entity.ScrubberBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Ships machine internals from the server to the client for display.
 *
 * <p>Separate from {@link MachineStateProvider} because Jade requires it: since
 * 1.21.6 a data provider may not also be a component provider. The split is not
 * arbitrary bookkeeping — these genuinely run on different sides, and merging them
 * invites reading server-only state on the client, which works in single-player and
 * silently reports nothing on a real server.
 */
public enum MachineDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    /** Keys shared with the client half. */
    static final String CHARGE = "charge";
    static final String BURN = "burn";
    static final String PUMP_INLET = "pump_inlet";
    static final String PUMP_OUTLET = "pump_outlet";
    /**
     * The pump's own real routing verdict ({@link GasPumpBlockEntity#isRouted()}), sent
     * alongside the two face counts rather than left for the client to reconstruct from
     * them. {@code inlet == 1 && outlet == 1} is not the same claim — a room ported to
     * itself puts one volume on each face and it is the same volume, which only
     * {@link play.xponer.astronima.sim.pipe.PumpRouting#isUsable} (via {@code isRouted})
     * can see (PLAN.md, "duplicated pump-connectivity predicates").
     */
    static final String PUMP_ROUTED = "pump_routed";
    static final String TANK_PRESSURE = "tank_pressure";

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(Astronima.MODID, "machine_data");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ScrubberBlockEntity scrubber) {
            data.putFloat(CHARGE, scrubber.chargeFraction());
        } else if (accessor.getBlockEntity() instanceof OxygenCandleBlockEntity candle) {
            data.putFloat(BURN, candle.burnFraction());
        } else if (accessor.getBlockEntity() instanceof GasPumpBlockEntity pump) {
            // Which of the pump's two faces is plumbed, and to how much. Sent
            // separately rather than as one status string so the tooltip can say
            // exactly which end is the problem — "no route" alone sends a player to
            // check both.
            data.putInt(PUMP_INLET, pump.volumesOnInlet());
            data.putInt(PUMP_OUTLET, pump.volumesOnOutlet());
            data.putBoolean(PUMP_ROUTED, pump.isRouted());
        } else if (accessor.getBlockEntity() instanceof GasTankBlockEntity tank) {
            data.putDouble(TANK_PRESSURE, tank.pressureKPa());
        }
    }
}
