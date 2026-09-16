package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import play.xponer.astronima.atmosphere.SkyExposure;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.power.CableNetworks;
import play.xponer.astronima.wire.WirePower;
import play.xponer.astronima.block.entity.PowerCellBlockEntity;
import play.xponer.astronima.power.PowerRun;
import play.xponer.astronima.sim.power.PowerBalance;

/**
 * One square metre of photovoltaic, and it makes about as much as a bright lamp.
 *
 * <p><strong>Thirty-seven watts in full sun</strong> — sunlight at 2.7 AU is a seventh of
 * Earth's, and twenty per cent of that is what an ordinary panel converts. A hand-cranked
 * machine is 250 W, so it takes <em>seven of these to replace one person on a handle</em>.
 * That is not a nerf; it is the belt, and it is why every real outer-system probe carries an
 * RTG instead.
 *
 * <p>Its placement problem is the retort's, on purpose: both need open sky, both are asking
 * {@link SkyExposure} the same question, and a player who roofs one over should not be
 * surprised by the other. Making it a second, differently-shaped rule would be inventing a
 * distinction the physics does not have.
 */
public class SolarArrayBlockEntity extends ReadableBlockEntity {

    /** Cached for the indicator, so the block can be read without asking the light engine. */
    private float lastSunlight;

    public SolarArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_ARRAY.get(), pos, state);
    }

    /** How much sun the panel had on it at the last tick, 0..1 — for the block's readout. */
    public float sunlight() {
        return lastSunlight;
    }

    /** What it is making right now, W. */
    public double watts() {
        return PowerBalance.panelWatts(lastSunlight);
    }

    /**
     * Makes a tick's worth of energy and pushes it into a cell it is touching.
     *
     * <p><strong>Energy that has nowhere to go is not made.</strong> There is no internal
     * buffer and no loss to account for: a panel with no cell beside it is an unwired panel,
     * and pretending it banked something would leave the player wondering where it went the
     * moment they connected one.
     */
    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        float sun = (float) SkyExposure.sunlightAt(serverLevel, pos);
        if (sun != lastSunlight) {
            lastSunlight = sun;
            setChanged();
        }
        if (sun <= 0) {
            return;
        }
        double made = PowerBalance.joules(PowerBalance.panelWatts(sun), 1.0 / 20.0);

        // A routed trace first: if the player wired this array up themselves, the run's real
        // length and metal decide what arrives, and the loss warms the corridor it runs through.
        WirePower.Run traced = WirePower.runFrom(serverLevel, pos);
        if (traced != null && !traced.isEmpty()) {
            double delivered = WirePower.send(serverLevel, traced, made, 1.0 / 20.0);
            for (BlockPos terminal : traced.terminals()) {
                if (delivered <= 0) {
                    break;
                }
                if (serverLevel.getBlockEntity(terminal) instanceof PowerCellBlockEntity cell) {
                    delivered = cell.charge(delivered);
                }
            }
            return;
        }

        CableNetworks.Resolved run = CableNetworks.resolveFrom(serverLevel, pos);
        if (run == null || run.isEmpty()) {
            return;
        }
        // What the run keeps is heat, and it is deposited *where the cable is* rather than at
        // either end - that is the whole difference between this and a pipe, and depositing it
        // at the array would put the warmth outdoors where nobody wants it. One call does both,
        // so delivering without shedding is not a thing this can be written to do.
        double arrived = PowerRun.sendAlong(serverLevel, run, made, 1.0 / 20.0);

        // Spread across the cells on the run, so a second cell is a second night's storage
        // rather than a race between them.
        double each = arrived / run.cells().size();
        for (PowerCellBlockEntity cell : run.cells()) {
            cell.charge(each);
        }
    }
}
