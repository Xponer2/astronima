package play.xponer.astronima.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.wire.SignalSource;
import play.xponer.astronima.wire.SignalView;
import play.xponer.astronima.wire.Terminal;
import play.xponer.astronima.wire.Terminated;

import java.util.List;

/**
 * Alarms: the three things about a room that a habitat has to be able to act on.
 *
 * <p>Control wiring had a presence mat, a switch and a button — three ways of saying <em>a person
 * did something</em>, and nothing at all about the environment those people are trying to survive
 * in. That is backwards for this mod: the whole simulation underneath is pressure, oxygen and
 * temperature, and none of it could reach a wire. These are the sensors that make the physics
 * <em>wirable</em>.
 *
 * <h2>They all drive on the hazard, not on the good state</h2>
 * Vacuum, not "pressurised". Starving, not "breathable". Freezing, not "warm". Three reasons:
 * an alarm that is silent when it is happy is the convention every real panel uses; a chain of
 * alarms into one OR gate is then the master alarm, with no inversions anywhere; and the good
 * state is one NOT gate away for the cases that want it.
 *
 * <p>One class, three registrations — because they differ only in which number they read, and
 * three near-identical classes would be three places to fix the same bug.
 */
public class EnvironmentSensorBlock extends Block
        implements SignalSource, Terminated, SignalView {

    /** What this sensor watches. */
    public enum Watches {
        /**
         * Room pressure below the point a person can work in.
         *
         * <p>Which is what makes an airlock interlock buildable: the chamber says for itself
         * that it is at vacuum, rather than the controller having to be asked.
         */
        VACUUM("vacuum", 30.0),

        /**
         * Oxygen partial pressure below the breathing floor.
         *
         * <p>The one that kills quietly. A room can be at full pressure and still be
         * unbreathable, which is exactly the failure the analyzer exists for and exactly the one
         * nobody notices in time.
         */
        STARVING("starving", 16.0),

        /** Room temperature below the point work becomes dangerous (v0.4's cold). */
        FREEZING("freezing", 263.15);

        private final String label;
        private final double threshold;

        Watches(String label, double threshold) {
            this.label = label;
            this.threshold = threshold;
        }

        public String label() {
            return label;
        }

        public double threshold() {
            return threshold;
        }

        /** True when the room is in the state this sensor is watching for. */
        public boolean alarmed(RoomState room) {
            return switch (this) {
                case VACUUM -> room.pressureKPa() < threshold;
                case STARVING -> room.partialPressureKPa(Gas.OXYGEN) < threshold;
                case FREEZING -> room.temperatureK() < threshold;
            };
        }
    }

    /** Lit while the alarm is up — so a sensor can be read without a wire on it. */
    public static final BooleanProperty ALARM = BlockStateProperties.LIT;

    /** How often it re-reads the room. Half a second: an alarm need not be instant, but nearly. */
    public static final int POLL_TICKS = 10;

    private final Watches watches;

    public EnvironmentSensorBlock(Properties properties, Watches watches) {
        super(properties);
        this.watches = watches;
        registerDefaultState(stateDefinition.any().setValue(ALARM, false));
    }

    public Watches watches() {
        return watches;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ALARM);
    }

    @Override
    public List<Terminal> terminals(BlockState state) {
        List<Terminal> found = new java.util.ArrayList<>(4);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            found.add(Terminal.centre(side, Terminal.Kind.SIGNAL_OUT, watches.label()));
        }
        return found;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old,
                           boolean moving) {
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean now = alarmed(level, pos);
        if (now != state.getValue(ALARM)) {
            level.setBlock(pos, state.setValue(ALARM, now), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, POLL_TICKS);
    }

    /**
     * Reads the room this sensor is sitting in.
     *
     * <p>No room at all — solid rock, or open sky — reads as <strong>alarmed</strong> for vacuum
     * and starving, because that is what it is, and as clear for cold, because empty space has no
     * temperature the sensor can claim to have measured. Silence would be the dangerous answer
     * for the first two: an alarm that goes quiet when its room stops existing is worse than one
     * that never worked.
     */
    private boolean alarmed(ServerLevel level, BlockPos pos) {
        RoomState room = Atmosphere.get(level).roomAt(pos);
        if (room == null) {
            return watches != Watches.FREEZING;
        }
        return watches.alarmed(room);
    }

    @Override
    public boolean isDriving(ServerLevel level, BlockPos pos, Terminal terminal) {
        return terminal.kind() == Terminal.Kind.SIGNAL_OUT && alarmed(level, pos);
    }

    @Override
    public boolean isDrivingClient(BlockState state) {
        return state.getValue(ALARM);
    }
}
