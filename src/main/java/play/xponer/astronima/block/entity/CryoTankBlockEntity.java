package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.cryo.BoilOff;
import play.xponer.astronima.sim.cryo.Bleve;
import play.xponer.astronima.sim.cryo.Cryogen;
import play.xponer.astronima.sim.cryo.CryoVessel;
import play.xponer.astronima.sim.pipe.PressureVessel;

/**
 * A dewar: {@link GasTankBlockEntity}'s vessel (vapor, pressure, network plumbing, client sync,
 * fill port — all inherited for free) plus a real liquid phase (design/cryogenics.md §4).
 *
 * <p>Extending rather than reimplementing means {@code PipeNetworks}' existing
 * {@code instanceof GasTankBlockEntity} flood-fill picks this block up automatically: a player
 * plumbs a valve to vent boil-off exactly the way they already plumb any other vessel, and that
 * plumbing <em>is</em> the counter to the BLEVE hazard (rule 7) — nothing new to teach.
 */
public class CryoTankBlockEntity extends GasTankBlockEntity {

    /** How much of the vessel's real volume is reserved for liquid, the rest kept as headspace —
     * real dewars keep 10-20% ullage even full; this is the honest middle. */
    private static final double MAX_LIQUID_FILL_FRACTION = 0.85;

    private Cryogen cryogen;
    private double liquidMoles;
    private boolean aerogelWrapped;

    /** See {@link FuelCellBlockEntity#sinceRun} — a self-owned counter rather than
     * {@code getGameTime() % interval}, so a hand-driven gametest can actually reach this tick. */
    private int sinceTick;

    public CryoTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_TANK.get(), pos, state);
    }

    /** The species this dewar has committed to, or {@code null} if never filled. */
    public Cryogen cryogen() {
        return cryogen;
    }

    public double liquidMoles() {
        return liquidMoles;
    }

    public boolean aerogelWrapped() {
        return aerogelWrapped;
    }

    /** The maximum liquid a dewar of this size can hold of {@code species}, mol. */
    public static double maxLiquidMoles(Cryogen species) {
        double liquidVolumeM3 = PressureVessel.TANK_VOLUME_M3 * MAX_LIQUID_FILL_FRACTION;
        double massKg = liquidVolumeM3 * species.liquidDensityKgPerM3();
        return massKg / species.gas().molarMassKgPerMol();
    }

    /**
     * Applies the aerogel upgrade — consumed once, permanent, like every other suit/hull upgrade
     * item in this mod. Returns false (nothing consumed) if already wrapped.
     */
    public boolean applyAerogelWrap() {
        if (aerogelWrapped) {
            return false;
        }
        aerogelWrapped = true;
        setChanged();
        return true;
    }

    /**
     * Accepts liquefied gas from a bound cryocooler. A tank commits to whichever species fills
     * it first; a mismatched species is refused outright (design/cryogenics.md §2 — "one dewar,
     * one cryogen") rather than silently mixed.
     *
     * @return moles actually accepted, capped by the dewar's real capacity
     */
    public double receiveLiquid(Cryogen species, double moles) {
        if (moles <= 0) {
            return 0;
        }
        if (cryogen != null && cryogen != species) {
            return 0;
        }
        if (cryogen == null) {
            cryogen = species;
        }
        double room = maxLiquidMoles(species) - liquidMoles;
        double accepted = Math.max(0, Math.min(moles, room));
        liquidMoles += accepted;
        setChanged();
        return accepted;
    }

    /** Forces the liquid content directly, bypassing {@link #receiveLiquid}'s capacity check —
     * the {@code /astronima cryo} debug channel's own hook (rule: every mechanic ships a command
     * to set and read its state), since real boil-off and BLEVE both take real minutes to reach
     * by playing normally. */
    public void debugSetLiquid(Cryogen species, double moles) {
        cryogen = species;
        liquidMoles = Math.clamp(moles, 0.0, maxLiquidMoles(species));
        setChanged();
    }

    public CryoVessel.Condition cryoCondition() {
        return CryoVessel.classify(pressureKPa());
    }

    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (++sinceTick < Atmosphere.TICK_INTERVAL) {
            return;
        }
        sinceTick = 0;
        double dtSeconds = Atmosphere.TICK_INTERVAL / 20.0;

        if (cryogen != null && liquidMoles > 0) {
            Atmosphere atmosphere = Atmosphere.get(serverLevel);
            RoomState room = atmosphere.roomTouching(pos);
            double roomK = room != null ? room.temperatureK() : 293.15;

            double uValue = aerogelWrapped ? BoilOff.AEROGEL_WRAPPED_U_VALUE : BoilOff.DEWAR_U_VALUE;
            double heatLeakWatts = BoilOff.heatLeakWatts(uValue, roomK, cryogen.boilingPointK());
            double boiled = BoilOff.molesBoiledOff(heatLeakWatts, dtSeconds,
                    cryogen.latentHeatVaporizationJPerMol(), liquidMoles);

            if (boiled > 0) {
                liquidMoles -= boiled;
                contents().addGasAt(cryogen.gas(), boiled, cryogen.boilingPointK());
                // The heat that boiled it had to come from somewhere: a dewar is a real, if
                // expensive, air conditioner for the room it stands in (design/cryogenics.md
                // §1.2). Removed in full, not just computed and discarded (rule 11).
                if (room != null) {
                    atmosphere.addHeatJoules(pos, -heatLeakWatts * dtSeconds);
                }
                setChanged();
            }
        }

        if (cryoCondition() == CryoVessel.Condition.BURST) {
            detonate(serverLevel, pos);
        }
    }

    private void detonate(ServerLevel level, BlockPos pos) {
        double energyJ = cryogen == null ? 0.0
                : Bleve.explosionEnergyJ(cryogen, pressureKPa(), liquidMoles);
        float power = Bleve.explosionPower(energyJ);
        level.removeBlock(pos, false);
        if (power > 0.0f) {
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    power, Level.ExplosionInteraction.BLOCK);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("cryogen", cryogen == null ? "" : cryogen.name());
        output.putDouble("liquid_moles", liquidMoles);
        output.putBoolean("aerogel_wrapped", aerogelWrapped);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String name = input.getStringOr("cryogen", "");
        cryogen = name.isEmpty() ? null : Cryogen.valueOf(name);
        liquidMoles = input.getDoubleOr("liquid_moles", 0.0);
        aerogelWrapped = input.getBooleanOr("aerogel_wrapped", false);
    }
}
