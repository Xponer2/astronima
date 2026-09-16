package play.xponer.astronima.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.block.GasPipeBlock;
import play.xponer.astronima.block.GasPortBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasTankBlock;
import play.xponer.astronima.block.GasValveBlock;
import play.xponer.astronima.block.entity.GasTankBlockEntity;
import play.xponer.astronima.sim.pipe.Valve;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.pipe.Conduit;
import play.xponer.astronima.sim.pipe.GasNetwork;
import play.xponer.astronima.sim.pipe.TickMemo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the plumbing the player actually built and hands it to the simulation.
 *
 * <p>Topology comes from a flood fill over connected pipe blocks, exactly as room
 * detection reads air. The player builds the shape and the game reads it; there is no
 * menu in which to declare what is joined to what, because a pipe you can see is a
 * better interface than a list you have to maintain.
 *
 * <p>Networks are resolved on demand rather than cached persistently, because the
 * alternative is a cache that has to be invalidated on every block change in the world
 * and gets it subtly wrong (design/plumbing.md &sect;2.7 sketches what that would take).
 * A run of pipe is small and the fill is cheap; correctness is worth more than the
 * microseconds — <strong>per walk.</strong> {@link #resolveForLeaderElection} exists
 * because that stopped being the whole story once a run could carry several ports: every
 * port on a run asks the identical question on the identical interval, and without
 * sharing the answer a run with P ports paid for the walk P times over, not once. See its
 * own doc and {@link TickMemo}'s.
 */
public final class PipeNetworks {

    /** A resolved network: the rooms it joins and how it joins them. */
    public record Resolved(GasNetwork network, List<BlockPos> pipes, List<BlockPos> ports,
                           List<BlockPos> tanks, Map<BlockPos, Integer> nodeOfPort,
                           int tightestValve) {

        /**
         * How far open the tightest valve on this run is, 0..1.
         *
         * <p>A pump has to ask this. Moving gas past a part-closed valve is still
         * restricted by it — a pump does not get to ignore the plumbing it is pumping
         * through, and pretending otherwise made valves decorative.
         */
        public double openFraction() {
            return Valve.openFraction(tightestValve);
        }

        public int roomCount() {
            return network.nodes().size();
        }
    }

    /**
     * Resolves a run that may open into only one volume.
     *
     * <p>A pump asks this about each of its faces, where a run with a single tank on
     * the end is exactly the normal case rather than a degenerate one — the pump is the
     * second end.
     */
    public static @Nullable Resolved resolveSide(ServerLevel level, BlockPos start) {
        return walk(level, start, 1);
    }

    /**
     * Walks the run of pipe reachable from {@code start} and builds its network.
     *
     * @return null when there is no pipe there, or when the run has fewer than two
     *         ports and so cannot move anything anywhere
     */
    public static @Nullable Resolved resolve(ServerLevel level, BlockPos start) {
        return walk(level, start, 2);
    }

    private static @Nullable Resolved walk(ServerLevel level, BlockPos start,
                                           int minimumVolumes) {
        Walked walked = floodFill(level, start);
        return walked == null ? null : buildNetwork(level, walked, minimumVolumes);
    }

    /** What one flood fill actually found, before any of it is judged. */
    private record Walked(List<BlockPos> pipes, List<BlockPos> ports, List<BlockPos> tanks,
                          int valveSetting) {}

    /**
     * The pipe/port/valve/tank blocks reachable from {@code start}, and the tightest
     * valve found along the way — or null when {@code start} is not plumbing at all.
     *
     * <p>Split out from network-building so {@link #resolveForLeaderElection} can see
     * every port a walk actually found even on the branch where the run turns out too
     * small to be a network — which is exactly the branch a too-small run's other ports
     * would otherwise each pay to rediscover for themselves.
     */
    private static @Nullable Walked floodFill(ServerLevel level, BlockPos start) {
        BlockState startState = level.getBlockState(start);
        if (!isNetworkPart(startState)) {
            return null;
        }

        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> pipes = new ArrayList<>();
        List<BlockPos> ports = new ArrayList<>();
        List<BlockPos> tanks = new ArrayList<>();
        // The tightest valve on a run sets its conductance: in series, the narrowest
        // restriction is what everything else has to pass through.
        int valveSetting = Valve.SETTINGS - 1;
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(start.immutable());
        visited.add(start.immutable());

        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof GasPortBlock) {
                ports.add(pos);
                // Walked through rather than stopped at. A port with pipe on two sides
                // is a tee — gas passes through its body just as it would a fitting —
                // and stopping here would mean a fill that starts at a port finds
                // nothing at all, which is precisely what it used to do.
            } else if (state.getBlock() instanceof GasTankBlock) {
                tanks.add(pos);
                // A tank is an end of the run in the same way a port is: gas arriving
                // here has arrived. It is still walked through so a fill that starts at
                // one finds the rest of the run.
            } else if (state.getBlock() instanceof GasValveBlock) {
                pipes.add(pos);
                int setting = state.getValue(GasValveBlock.SETTING);
                if (setting <= 0) {
                    // Shut. Not walked through, so everything beyond it is genuinely
                    // out of reach - which is the entire reason to fit a valve. It used
                    // to be walked through and merely recorded, so a shut valve
                    // throttled the passive edges and did nothing at all to a pump.
                    continue;
                }
                valveSetting = Math.min(valveSetting, setting);
            } else if (state.getBlock() instanceof GasPipeBlock) {
                pipes.add(pos);
            } else {
                continue;
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighbour = pos.relative(direction);
                if (!visited.add(neighbour)) {
                    continue;
                }
                BlockState neighbourState = level.getBlockState(neighbour);
                if (conducts(state, neighbourState, direction)) {
                    frontier.add(neighbour);
                } else {
                    visited.remove(neighbour); // not ours; let another path re-test it
                }
            }
        }

        return new Walked(pipes, ports, tanks, valveSetting);
    }

    /** Judges a flood fill's findings against {@code minimumVolumes} and builds its network. */
    private static @Nullable Resolved buildNetwork(ServerLevel level, Walked walked,
                                                    int minimumVolumes) {
        Atmosphere atmosphere = Atmosphere.get(level);
        GasNetwork network = new GasNetwork();
        Map<BlockPos, Integer> nodeOfPort = new HashMap<>();
        Map<Long, Integer> nodeOfRoom = new HashMap<>();

        for (BlockPos port : walked.ports()) {
            // The room whose interior contains the cell the port opens into — roomAt, not
            // roomTouching. Touching scanned every face AROUND that cell, so a port whose
            // mouth was buried in a one-block wall quietly reached the room on the far
            // side, which is a hole the player never drilled. Found by the
            // port-into-solid-rock scenario the moment it was written.
            RoomState room = atmosphere.roomAt(
                    GasPortBlock.roomSide(level.getBlockState(port), port));
            if (room == null) {
                continue; // a port opening into solid rock is simply not connected
            }
            // Two ports into the same room must not make that room two nodes, or gas
            // would flow from a room to itself and the network would invent pressure.
            int node = nodeOfRoom.computeIfAbsent(room.id(), id -> network.addNode(room));
            nodeOfPort.put(port, node);
        }

        for (BlockPos tank : walked.tanks()) {
            if (level.getBlockEntity(tank) instanceof GasTankBlockEntity vessel) {
                network.addNode(vessel.contents());
            }
        }

        if (network.nodes().size() < minimumVolumes) {
            return null;
        }

        // Every pair of distinct rooms on this run is joined through it. The run's
        // conductance is its length in series, which is why a long line is a slow line.
        int length = Math.max(1, walked.pipes().size());
        double conductance = Valve.conductance(Conduit.ofBlocks(length), walked.valveSetting());
        int count = network.nodes().size();
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                network.connect(i, j, conductance);
            }
        }
        return new Resolved(network, walked.pipes(), walked.ports(), walked.tanks(),
                nodeOfPort, walked.valveSetting());
    }

    /** One {@link TickMemo} per level, so no level's cache can be invalidated by another's tick. */
    private static final Map<ServerLevel, TickMemo<BlockPos, Resolved>> LEADER_CACHE =
            new HashMap<>();

    /**
     * {@link #resolve}, shared by every port on the same run within one server tick.
     *
     * <p>Every port on a run gates on the identical {@code gameTime % INTERVAL_TICKS}
     * (see {@code GasPortBlockEntity}), so all of them ask this in the same tick. Without
     * sharing the answer, a run with P ports paid for the full flood fill P times per
     * interval merely so they could compare positions and agree who leads — the class doc
     * above used to claim cost scaled with openings alone, and this was exactly the part
     * of that claim that was not true; cost scaled with openings <em>times</em> the run's
     * own length.
     *
     * <p>The first port to ask in a tick does the one real walk. Every other port the
     * walk actually found — including when the run turns out too small to be a network at
     * all, so a too-small run is not re-walked by each of its ports either — is answered
     * from the cache instead of walking again.
     */
    public static @Nullable Resolved resolveForLeaderElection(ServerLevel level, BlockPos start,
                                                               long gameTime) {
        TickMemo<BlockPos, Resolved> cache =
                LEADER_CACHE.computeIfAbsent(level, ignored -> new TickMemo<>());
        BlockPos key = start.immutable();
        return cache.get(gameTime, key, ignored -> {
            Walked walked = floodFill(level, start);
            Resolved resolved = walked == null ? null : buildNetwork(level, walked, 2);
            if (walked != null) {
                for (BlockPos port : walked.ports()) {
                    BlockPos portKey = port.immutable();
                    if (!portKey.equals(key)) {
                        cache.supply(gameTime, portKey, resolved);
                    }
                }
            }
            return resolved;
        });
    }

    /** How many distinct volumes (rooms or tanks) {@code side} reaches. */
    public static int volumesOnSide(ServerLevel level, BlockPos side) {
        return volumeCount(resolveSide(level, side));
    }

    /**
     * How many volumes a side's own resolved network holds, null-safe.
     *
     * <p>The one implementation of "how many volumes does a resolved side have" — it was
     * being restated as the same ternary in more than one file (PLAN.md, "duplicated
     * pump-connectivity predicates"), which is exactly the shape of bug that stops
     * agreeing with itself the day one copy is touched and the other is not.
     */
    public static int volumeCount(@Nullable Resolved resolved) {
        return resolved == null ? 0 : resolved.network().nodes().size();
    }

    /**
     * True when gas can pass between two adjacent blocks moving {@code dir} from one to the
     * other. A valve is the only directional case: an inline fitting conducts only along its
     * axis, at either end, so a run cannot sneak through its side — the functional half of
     * {@link GasPipeBlock#connectsToward}, which stops the model drawing an arm there.
     */
    private static boolean conducts(BlockState from, BlockState to, Direction dir) {
        if (from.getBlock() instanceof GasValveBlock
                && from.getValue(GasValveBlock.AXIS) != dir.getAxis()) {
            return false;
        }
        if (to.getBlock() instanceof GasValveBlock
                && to.getValue(GasValveBlock.AXIS) != dir.getAxis()) {
            return false;
        }
        return GasPipeBlock.connectsTo(to);
    }

    /** True for anything that is part of a run rather than merely next to one. */
    public static boolean isNetworkPart(BlockState state) {
        return state.getBlock() instanceof GasPipeBlock
                || state.getBlock() instanceof GasPortBlock
                || state.getBlock() instanceof GasValveBlock
                || state.getBlock() instanceof GasTankBlock;
    }

    private PipeNetworks() {}
}
