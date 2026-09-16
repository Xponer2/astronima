package play.xponer.astronima.atmosphere;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.Config;
import play.xponer.astronima.block.MoldBlock;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.sim.Humidity;
import play.xponer.astronima.sim.ThermalRelaxation;
import play.xponer.astronima.sim.room.Shell;
import play.xponer.astronima.sim.thermal.HeatBalance;
import play.xponer.astronima.sim.burn.Flammability;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.GasFlow;
import play.xponer.astronima.sim.GasMixture;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.room.BlockKind;
import play.xponer.astronima.sim.room.CellPos;
import play.xponer.astronima.sim.room.RoomScanner;
import play.xponer.astronima.sim.room.ScanResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Per-dimension room registry and atmosphere ticker.
 *
 * <p>Rooms are found lazily: the first breath, machine tick, or analyzer reading in an
 * enclosed space triggers a scan. When geometry changes, affected rooms are only marked
 * dirty; the next access re-scans and the new room <em>inherits gas from whatever rooms
 * previously owned its cells, proportional to the volume taken</em> — so sealing a wall
 * splits air between the halves, and knocking one out merges it, without creating or
 * destroying a single mole.
 */
public final class Atmosphere extends SavedData {
    /** Deep C-type asteroid rock equilibrium temperature (~−60 °C). */
    public static final double AMBIENT_ROCK_TEMP_K = 213.0;

    /** How fast an unsealed room bleeds into space (fraction of imbalance per second). */
    private static final double BREACH_CONDUCTANCE = 3.0;

    /** Leakage through a closed non-airtight door, per leak block. */
    private static final double LEAK_CONDUCTANCE = 0.02;

    /** Atmosphere physics advances every 10 game ticks. */
    public static final int TICK_INTERVAL = 10;
    private static final double TICK_SECONDS = TICK_INTERVAL / 20.0;

    /**
     * A room older than this is re-scanned on next access even without an
     * invalidation event — the safety net for state changes no event covers
     * (mob griefing, other mods' machines, commands).
     */
    private static final int REVALIDATE_AFTER_TICKS = 40;

    /** Per atmosphere tick chance that a damp room sprouts mold (≈ once per 5 min). */
    private static final float MOLD_SEED_CHANCE = 0.002f;

    /** Cells checked per tick for flames a starved room can no longer support. */
    private static final int FLAME_SAMPLES_PER_TICK = 4;

    public static final SavedDataType<Atmosphere> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Astronima.MODID, "atmosphere"),
            level -> new Atmosphere(Objects.requireNonNull(level, "Atmosphere requires a level")),
            level -> codec(Objects.requireNonNull(level, "Atmosphere requires a level")));

    private final ServerLevel level;
    private final Long2ObjectMap<Room> rooms = new Long2ObjectOpenHashMap<>();
    private final Long2LongMap cellToRoom = new Long2LongOpenHashMap();
    private long nextRoomId;

    private Atmosphere(ServerLevel level) {
        this.level = level;
        this.cellToRoom.defaultReturnValue(-1);
    }

    public static Atmosphere get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * The room whose interior contains {@code pos}, scanning or re-scanning as needed.
     * Returns null when the position is inside a solid block or no enclosure exists.
     */
    public @Nullable RoomState roomAt(BlockPos pos) {
        Room room = freshRoomAt(pos);
        return room == null ? null : room.state;
    }

    /** Like {@link #roomAt} but reports sealing, for UI/analyzer use. */
    public @Nullable RoomReading readingAt(BlockPos pos) {
        Room room = freshRoomAt(pos);
        return room == null ? null : new RoomReading(room.state, room.sealed, room.openToSpace);
    }

    /**
     * The air a person at {@code pos} actually breathes. A head inside a non-open cell
     * (a doorway panel, a slab gap) breathes the adjacent air, preferring the sealed
     * neighbor with the most oxygen — the pressurized side of the doorway, not the
     * void on the other side.
     */
    public @Nullable RoomReading readingNear(BlockPos pos) {
        RoomReading direct = readingAt(pos);
        if (direct != null) {
            return direct;
        }
        RoomReading bestSealed = null;
        RoomReading anyUnsealed = null;
        for (Direction dir : Direction.values()) {
            RoomReading neighbor = readingAt(pos.relative(dir));
            if (neighbor == null) {
                continue;
            }
            if (neighbor.sealed()) {
                if (bestSealed == null || neighbor.state().partialPressureKPa(Gas.OXYGEN)
                        > bestSealed.state().partialPressureKPa(Gas.OXYGEN)) {
                    bestSealed = neighbor;
                }
            } else if (anyUnsealed == null) {
                anyUnsealed = neighbor;
            }
        }
        return bestSealed != null ? bestSealed : anyUnsealed;
    }

    /** Full diagnostic view of the room at {@code pos}, for the analyzer's debug mode. */
    public @Nullable DebugReading debugReadingAt(BlockPos pos) {
        Room room = freshRoomAt(pos);
        if (room == null) {
            return null;
        }
        return new DebugReading(room.state, room.sealed, room.overCap,
                room.leaks.size(), level.getGameTime() - room.lastScanTime,
                new LongOpenHashSet(room.cells), new LongOpenHashSet(room.leaks));
    }

    private @Nullable Room freshRoomAt(BlockPos pos) {
        Room room = ownerOf(pos.asLong());
        if (room != null && !room.dirty
                && level.getGameTime() - room.lastScanTime <= REVALIDATE_AFTER_TICKS) {
            return room;
        }
        return materialize(pos);
    }

    /** The room touching any face of {@code machinePos} — how full-block machines find their air. */
    public @Nullable RoomState roomTouching(BlockPos machinePos) {
        for (Direction dir : Direction.values()) {
            RoomState state = roomAt(machinePos.relative(dir));
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    /**
     * Distinct blocks bounding the room containing {@code inside} that match {@code test} —
     * the doors and ports that seal a chamber, so the airlock can find the pieces the
     * player built around it. Empty when there is no room there.
     *
     * <p>Walks the room's own cells and collects each solid neighbour that matches, so it
     * reads the boundary the scan already found rather than guessing a bounding box.
     */
    public java.util.Set<BlockPos> boundaryMatching(BlockPos inside,
            java.util.function.Predicate<BlockState> test) {
        java.util.Set<BlockPos> found = new HashSet<>();
        Room room = freshRoomAt(inside);
        if (room == null) {
            return found;
        }
        for (long cell : room.cells) {
            BlockPos here = BlockPos.of(cell);
            for (Direction dir : Direction.values()) {
                BlockPos neighbour = here.relative(dir);
                if (!room.cells.contains(neighbour.asLong())
                        && test.test(level.getBlockState(neighbour))) {
                    found.add(neighbour.immutable());
                }
            }
        }
        return found;
    }

    /** Marks rooms containing or bordering {@code pos} stale after a block change. */
    public void invalidate(BlockPos pos) {
        markDirty(pos.asLong());
        for (Direction dir : Direction.values()) {
            markDirty(pos.relative(dir).asLong());
        }
    }

    /** Cached machine positions per room; surveys are block-walks, mixing is cheap. */
    private final Long2ObjectMap<CachedSources> noiseCache = new Long2ObjectOpenHashMap<>();

    private record CachedSources(List<RoomNoise.Source> sources, long surveyedAt) {}

    /**
     * Combined machinery noise (dB) heard at {@code pos}, with distance attenuation;
     * 0 in silence or vacuum (no medium, no sound — the one upside of a breach).
     */
    public double noiseDbAt(BlockPos pos) {
        // Beds and machines occupy their own (non-open) cell: fall back to the
        // neighboring air the sleeper actually lies in.
        Room room = freshRoomAt(pos);
        if (room == null) {
            for (Direction dir : Direction.values()) {
                room = freshRoomAt(pos.relative(dir));
                if (room != null) {
                    break;
                }
            }
        }
        if (room == null || (room.openToSpace && outsideIsVacuum())) {
            return 0; // vacuum carries no sound
        }
        long now = level.getGameTime();
        CachedSources cached = noiseCache.get(room.id());
        if (cached == null || now - cached.surveyedAt() > REVALIDATE_AFTER_TICKS) {
            cached = new CachedSources(RoomNoise.survey(level, room), now);
            noiseCache.put(room.id(), cached);
        }
        return RoomNoise.mixAt(cached.sources(), pos);
    }

    /** On the asteroid, outside is always hard vacuum; other dimensions follow config. */
    public boolean outsideIsVacuum() {
        return level.dimension().equals(ModDimensions.ASTEROID_LEVEL) || !Config.BREATHABLE_OUTSIDE.get();
    }

    /**
     * Fills the room at {@code pos} with sea-level Earth air at 20 °C — used once, for
     * the crashed crew module the player wakes up in (its reserves vented into the
     * cavity during the crash; see DESIGN.md §2).
     */
    public boolean pressurizeWithEarthAir(BlockPos pos) {
        Room room = ownerOf(pos.asLong());
        if (room == null || room.dirty) {
            room = materialize(pos);
        }
        if (room == null) {
            return false;
        }
        RoomState state = room.state;
        state.gases().extractFraction(1.0);
        state.setTemperatureK(293.0);
        state.gases().addAll(GasMixture.earthAir(state.volumeM3(), GasMixture.EARTH_PRESSURE_KPA, 293.0));
        setDirty();
        return true;
    }

    private void markDirty(long cellKey) {
        Room room = ownerOf(cellKey);
        if (room != null) {
            room.dirty = true;
            setDirty();
        }
    }

    private @Nullable Room ownerOf(long cellKey) {
        long id = cellToRoom.get(cellKey);
        return id < 0 ? null : rooms.get(id);
    }

    /**
     * Scans the enclosure at {@code seedPos} and registers it as a room, pulling gas out
     * of any previous rooms whose cells it overlaps (proportional to the volume taken).
     *
     * <p>Identity is stable: when the scan replaces a previous room outright (the
     * routine revalidation case), the new room keeps that room's id, so readings and
     * debug output refer to "the same room" across rescans. A fresh id is minted only
     * for genuinely new enclosures and for fragments split off a surviving room.
     */
    private @Nullable Room materialize(BlockPos seedPos) {
        ScanResult scan = RoomScanner.scan(new LevelBlockAccess(level),
                new CellPos(seedPos.getX(), seedPos.getY(), seedPos.getZ()),
                Config.MAX_ROOM_VOLUME.get());
        if (scan.cells().isEmpty()) {
            return null;
        }

        LongSet cells = new LongOpenHashSet(scan.cells().size());
        for (CellPos cell : scan.cells()) {
            cells.add(BlockPos.asLong(cell.x(), cell.y(), cell.z()));
        }
        LongSet leaks = new LongOpenHashSet(scan.leaks().size());
        for (CellPos leak : scan.leaks()) {
            leaks.add(BlockPos.asLong(leak.x(), leak.y(), leak.z()));
        }

        Room seedOwner = ownerOf(seedPos.asLong());
        // Measured before inheritance mutates anything: how much of the room that
        // used to be here still is.
        int seedOwnerSizeBefore = seedOwner == null ? 0 : seedOwner.cells.size();
        int seedOwnerRetained = seedOwner == null ? 0 : countOwnedBy(cells, seedOwner);

        RoomState gathered = new RoomState(-1, cells.size(), new GasMixture(), AMBIENT_ROCK_TEMP_K);
        inheritGas(gathered, cells);

        long id = identityFor(seedOwner, seedOwnerSizeBefore, seedOwnerRetained);
        if (seedOwner != null && id == seedOwner.id()) {
            // Taking the id means taking the room. Anything of it this scan did not claim
            // is a different volume now and must not be left pointing here, or it will
            // inherit the same id on its own next scan.
            releaseLeftovers(seedOwner, cells);
        }

        RoomState state = new RoomState(id, cells.size(), gathered.gases(), gathered.temperatureK());
        Room room = new Room(state, cells, leaks, scan.sealed());
        room.shell = scan.shell();
        room.overCap = scan.overCap();
        room.openToSpace = scan.openToSpace();
        room.lastScanTime = level.getGameTime();
        rooms.put(room.id(), room);
        for (LongIterator it = cells.iterator(); it.hasNext(); ) {
            cellToRoom.put(it.nextLong(), room.id());
        }
        consumePocketCores(room);
        setDirty();
        return room;
    }

    /**
     * Decides whether this scan is the <em>same room</em> as the one that was here.
     *
     * <p>Identity used to require the previous room to be consumed down to nothing,
     * which is far too strict: placing a single block inside a room — a door, a
     * machine, a torch — takes one cell out of the enclosure, leaves the old room
     * holding that one orphan cell, and so minted a brand new id for a room the player
     * would say never changed. Opening and closing a door did the same thing every
     * time.
     *
     * <p>The rule that actually matches what a person means by "the same room" is
     * continuity of substance: if most of the old room is still here, this is that
     * room. A genuine split — a wall built across the middle — fails that test on the
     * smaller side, which is correct, because the smaller side really is a new room.
     */
    private long identityFor(@Nullable Room seedOwner, int sizeBefore, int retained) {
        if (seedOwner == null) {
            return nextRoomId++;
        }
        // Fully absorbed: unambiguously the same room continuing.
        if (!rooms.containsKey(seedOwner.id())) {
            return seedOwner.id();
        }
        // Survived as a fragment elsewhere, but the majority of it is right here.
        boolean continuation = sizeBefore > 0 && retained * 2 >= sizeBefore;
        return continuation ? seedOwner.id() : nextRoomId++;
    }

    /**
     * Releases the cells of a room whose id has just been taken over by a smaller scan.
     *
     * <p>Reported from play: two sealed volumes either side of a shut door both reading as
     * <em>room #13</em>, with air draining out of one when the other was pumped down. They
     * were not merely mislabelled — they shared a single {@link RoomState}, so gas taken
     * from either was taken from both, and that one fault produced the drifting habitat
     * pressure, the 0.1 kPa appearing in an evacuated chamber, and the repressurise that
     * stopped at a quarter of an atmosphere.
     *
     * <p>The cause was a continuation claiming an id while leaving the rest of the old
     * room still <em>pointing at</em> that id. When the leftover piece was next scanned it
     * found itself owned by the new room, saw that it made up most of it, and concluded it
     * was a continuation as well. Two volumes, one id.
     *
     * <p>Unmapping is the fix rather than refusing the id: an id has to survive placing a
     * torch or shutting a door inside a room, which is why the majority rule exists in the
     * first place. What must not survive is a <em>second</em> volume inheriting it.
     */
    private void releaseLeftovers(Room previous, LongSet claimed) {
        for (LongIterator it = previous.cells.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            if (!claimed.contains(cell) && cellToRoom.get(cell) == previous.id()) {
                cellToRoom.remove(cell);
            }
        }
    }

    private int countOwnedBy(LongSet cells, Room owner) {
        int count = 0;
        for (LongIterator it = cells.iterator(); it.hasNext(); ) {
            if (cellToRoom.get(it.nextLong()) == owner.id()) {
                count++;
            }
        }
        return count;
    }

    /** First scan of a natural cavity releases its trapped volatiles (worldgen markers). */
    private void consumePocketCores(Room room) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (LongIterator it = room.cells.iterator(); it.hasNext(); ) {
            cursor.set(it.nextLong());
            if (level.getBlockState(cursor).is(ModBlocks.GAS_POCKET_CORE.get())) {
                GasPockets.inject(level.getSeed(), cursor.immutable(), room.state);
                // Flag 2 (client update only): no neighbor events, so this doesn't
                // re-dirty the room we are in the middle of materializing.
                level.setBlock(cursor, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /** Transfers gas from previous owners of {@code cells} into {@code state}, conserving mass. */
    private void inheritGas(RoomState state, LongSet cells) {
        Map<Room, Integer> overlaps = new HashMap<>();
        for (LongIterator it = cells.iterator(); it.hasNext(); ) {
            Room previous = ownerOf(it.nextLong());
            if (previous != null) {
                overlaps.merge(previous, 1, Integer::sum);
            }
        }
        for (Map.Entry<Room, Integer> entry : overlaps.entrySet()) {
            Room previous = entry.getKey();
            double fraction = Math.min(1.0, entry.getValue() / (double) previous.cells.size());
            GasMixture taken = previous.state.gases().extractFraction(fraction);
            state.addMixtureAt(taken, previous.state.temperatureK());

            previous.cells.removeAll(cells);
            // Anything left that is no longer open air is not part of any room — it is
            // the block the player just placed. Keeping such cells left a one-cell
            // ghost room alive, which was enough to break room identity.
            previous.cells.removeIf((long cell) -> !isOpenCell(cell));
            if (previous.cells.isEmpty()) {
                forget(previous);
            } else {
                previous.state.setVolumeBlocks(previous.cells.size());
            }
        }
    }

    /**
     * Puts out flames a room can no longer feed. Sampled rather than exhaustive: a
     * depressurized room goes dark over a few seconds instead of instantly, which
     * reads as flames guttering rather than a switch being thrown.
     */
    private void snuffStarvedFlames(Room room) {
        if (room.state.partialPressureKPa(Gas.OXYGEN) >= Flammability.MIN_O2_KPA || room.cells.isEmpty()) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < FLAME_SAMPLES_PER_TICK; attempt++) {
            int index = level.getRandom().nextInt(room.cells.size());
            LongIterator it = room.cells.iterator();
            for (int i = 0; i < index && it.hasNext(); i++) {
                it.nextLong();
            }
            if (!it.hasNext()) {
                continue;
            }
            cursor.set(it.nextLong());
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                    || state.is(Blocks.FIRE) || state.is(Blocks.CAMPFIRE)) {
                CombustionEvents.snuffIfStarved(level, cursor.immutable(), state);
            }
        }
    }

    /**
     * Adds heat to whatever room holds this position, in joules — {@code joules} may be
     * negative, which removes heat instead (a cryo dewar boiling in a room draws the energy for
     * that from somewhere real; see {@code CryoTankBlockEntity}). Every other caller only ever
     * computes a positive quantity, so this is additive capability, not a changed contract.
     *
     * <p>The way everything that warms a habitat gets into the balance: crew metabolism from
     * the breathing tick, friction from a machine being cranked. Silently ignored when
     * nothing here is a room, because heating vacuum is not a thing that happens and a caller
     * should not have to check.
     *
     * <p><strong>It looks at the neighbours when the position itself is not a room cell,</strong>
     * and that is not a convenience. A machine <em>is</em> a solid block: it occupies a cell
     * the room scan walks past, so a crusher asking to warm the air at its own position asks
     * about a cell no room owns and gets nothing. The first version did exactly that, and the
     * symptom was perfect - the crusher ran, the handle turned, the room cooled at precisely
     * the rate it did with the machine idle. Heat from a block goes into the air touching it,
     * which is both the fix and the physics.
     */
    public void addHeatJoules(BlockPos pos, double joules) {
        if (joules == 0) {
            return;
        }
        Room room = ownerOf(pos.asLong());
        if (room == null || room.dirty) {
            room = null;
            for (Direction side : Direction.values()) {
                Room neighbour = ownerOf(pos.relative(side).asLong());
                if (neighbour != null && !neighbour.dirty) {
                    room = neighbour;
                    break;
                }
            }
        }
        if (room != null) {
            room.pendingHeatJ += joules;
            setDirty();
        }
    }

    /**
     * How far above a room's own cell to read the sky from — {@code AsteroidBody}'s own full
     * span top-to-bottom is 160 blocks ({@code POLAR_RADIUS} either side of {@code CENTRE_Y}),
     * so this clears the body entirely even from a room dug at its deepest point, whatever a
     * player has built between there and the surface.
     */
    private static final int SOLAR_REFERENCE_MARGIN = 200;

    /**
     * Where to read the day/night-and-occultation state for a room at this position, never to
     * ask whether the room itself is covered over — its own {@code Shell.skyFraction()} has
     * already answered that (design/albedo-paint.md §2).
     *
     * <p><strong>Relative to the room's own cell, not a fixed world point or an absolute height
     * derived from {@code AsteroidBody}'s own coordinates.</strong> Both were tried and both are
     * wrong: a fixed world point sits in an unloaded chunk for every room built anywhere else
     * (silently reading as unlit rather than genuinely open, since {@link
     * SkyExposure#hasClearSky} treats an unloaded column no differently from a covered one), and
     * an absolute height keyed to the asteroid body's own geometry is meaningless the moment a
     * room exists anywhere that is not that specific asteroid — a gametest, or the "vanilla
     * overworld" case this mod's own other sky code already names as real. Same (x, z) as the
     * room keeps the chunk guaranteed already loaded (chunks load as full vertical columns, and
     * the room right below is being actively simulated); clamped to the level's own build height
     * so a room dug near the top of an unusually tall world cannot ask about a position past it.
     */
    private BlockPos solarReferenceFor(BlockPos anyRoomCell) {
        int y = Math.min(anyRoomCell.getY() + SOLAR_REFERENCE_MARGIN, level.getMaxY() - 1);
        return new BlockPos(anyRoomCell.getX(), y, anyRoomCell.getZ());
    }

    /**
     * The room's real thermal balance: what it radiates to a 2.7 K sky, what it conducts
     * into the rock, what the sun puts into an exposed shell, and what the people and machines
     * in it put back.
     *
     * <p><strong>This replaces the interim bath</strong> that dragged every sealed room to
     * 293 K regardless of what was happening in it. That model was honest about being a
     * placeholder and correct to hold the line until there was a counter — see
     * {@code design/thermal.md} §3. There is one now: crew and worked machines (T3), and
     * insulated plate to keep it in (T5), so the loss can finally be real.
     *
     * <p>Unsealed volumes keep the old treatment. A vented cavity is a hole in an asteroid
     * at the asteroid's temperature; running a radiative balance on it would be arithmetic
     * about a room that is not holding anything.
     */
    private void relaxTemperature(Room room) {
        if (!room.sealed) {
            room.state.setTemperatureK(ThermalRelaxation.step(room.state.temperatureK(),
                    AMBIENT_ROCK_TEMP_K, TICK_SECONDS, ThermalRelaxation.TIME_CONSTANT_S));
            return;
        }
        double watts = room.pendingHeatJ / TICK_SECONDS;
        room.pendingHeatJ = 0;
        Shell shell = room.shell;
        double area = HeatBalance.hullAreaM2(room.state.volumeBlocks());
        BlockPos reference = solarReferenceFor(BlockPos.of(room.cells.iterator().nextLong()));
        double sunFraction = SkyExposure.sunlightAt(level, reference);
        double solarWatts = HeatBalance.solarWatts(area, shell.skyFraction(),
                HeatBalance.solarAbsorptivity(shell.paintedFraction()), sunFraction);
        watts += solarWatts;
        room.lastSupplyWatts = watts;
        room.state.setTemperatureK(HeatBalance.step(
                room.state.temperatureK(), room.state.volumeBlocks(),
                shell.skyFraction(), shell.buriedFraction(),
                HeatBalance.uValue(shell.insulatedFraction()), AMBIENT_ROCK_TEMP_K,
                watts, TICK_SECONDS));
    }

    /**
     * Everything the thermal instrument needs about the room at a position.
     *
     * @param lossWatts         radiated plus conducted, at the temperature it is at
     * @param supplyWatts       what crew and worked machines put back on the last step
     * @param skyFraction       share of the shell facing vacuum
     * @param insulatedFraction share of the shell built out of insulated plate
     */
    public record ThermalSnapshot(double lossWatts, double supplyWatts, double skyFraction,
                                  double insulatedFraction) {
        public static final ThermalSnapshot NONE = new ThermalSnapshot(0, 0, 0, 0);
    }

    /** The thermal picture of whatever room holds this position. */
    public ThermalSnapshot thermalAt(BlockPos pos) {
        Room room = ownerOf(pos.asLong());
        if (room == null || room.dirty || !room.sealed) {
            return ThermalSnapshot.NONE;
        }
        return new ThermalSnapshot(heatLossWatts(room), room.lastSupplyWatts,
                room.shell.skyFraction(), room.shell.insulatedFraction());
    }

    /** What this room is losing right now, in watts — the number an instrument shows. */
    public double heatLossWatts(Room room) {
        Shell shell = room.shell;
        double area = HeatBalance.hullAreaM2(room.state.volumeBlocks());
        return HeatBalance.radiatedWatts(room.state.temperatureK(), area, shell.skyFraction())
                + HeatBalance.conductedWatts(room.state.temperatureK(), AMBIENT_ROCK_TEMP_K,
                        area, shell.buriedFraction(),
                        HeatBalance.uValue(shell.insulatedFraction()));
    }

    /** True when a cell is still space a room could occupy. */
    private boolean isOpenCell(long cellKey) {
        BlockPos pos = BlockPos.of(cellKey);
        return AirBlockKinds.classify(level.getBlockState(pos), level, pos) == BlockKind.OPEN;
    }

    /** Drops a room and every trace of it, so long sessions don't accumulate junk. */
    private void forget(Room room) {
        rooms.remove(room.id());
        noiseCache.remove(room.id());
        // Its cells may already have been claimed by the replacing room; only clear
        // mappings that still point here.
        for (LongIterator it = room.cells.iterator(); it.hasNext(); ) {
            long cell = it.nextLong();
            if (cellToRoom.get(cell) == room.id()) {
                cellToRoom.remove(cell);
            }
        }
    }

    /** Advances leak exchange and breach venting. Call every {@link #TICK_INTERVAL} game ticks. */
    public void tick() {
        if (rooms.isEmpty()) {
            return;
        }
        Set<RoomPair> exchangedPairs = new HashSet<>();
        for (Room room : new ArrayList<>(rooms.values())) {
            if (room.dirty) {
                continue;
            }
            if (room.openToSpace) {
                // A path to vacuum: the volume empties, whatever its size.
                if (outsideIsVacuum()) {
                    GasFlow.ventToVacuum(room.state, BREACH_CONDUCTANCE, TICK_SECONDS);
                } else {
                    resetToOutsideAir(room.state);
                }
                continue;
            }
            // Enclosed but over-cap (a big tunnel maze) is not pressurizable, yet it
            // still holds whatever is in it — breached pocket gas lingers to be
            // measured, breathed, and ignited. Only leaks move it.
            relaxTemperature(room);
            exchangeThroughLeaks(room, exchangedPairs);
            seedMold(room);
            snuffStarvedFlames(room);
        }
        setDirty();
    }

    /**
     * Damp, warm rooms occasionally sprout a mold colony in a random cell — on whichever of
     * that cell's floor, ceiling or wall faces {@link MoldBlock#trySeed} actually finds solid
     * backing for (design/mold-growth.md: real mold is not a floor-only phenomenon). Rare per
     * tick by design: neglect shows up over hours, not seconds, and a dehumidifier removes the
     * cause entirely.
     */
    private void seedMold(Room room) {
        if (!Humidity.moldFavourable(room.state) || level.getRandom().nextFloat() >= MOLD_SEED_CHANCE) {
            return;
        }
        int index = level.getRandom().nextInt(room.cells.size());
        LongIterator it = room.cells.iterator();
        for (int i = 0; i < index && it.hasNext(); i++) {
            it.nextLong();
        }
        if (it.hasNext()) {
            MoldBlock.trySeed(level, BlockPos.of(it.nextLong()));
        }
    }

    /**
     * A room open to a breathable exterior (pre-dimension testing worlds) tracks
     * outside air: infinite-reservoir exchange collapses to simply resetting it.
     */
    private static void resetToOutsideAir(RoomState state) {
        state.gases().extractFraction(1.0);
        state.setTemperatureK(293.0);
        state.addMixtureAt(GasMixture.earthAir(state.volumeM3(), GasMixture.EARTH_PRESSURE_KPA, 293.0), 293.0);
    }

    private void exchangeThroughLeaks(Room room, Set<RoomPair> exchangedPairs) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (LongIterator it = room.leaks.iterator(); it.hasNext(); ) {
            long leakKey = it.nextLong();
            cursor.set(leakKey);
            for (Direction dir : Direction.values()) {
                Room other = ownerOf(cursor.relative(dir).asLong());
                if (other == null || other == room || other.dirty) {
                    continue;
                }
                if (exchangedPairs.add(RoomPair.of(room.id(), other.id()))) {
                    GasFlow.equalize(room.state, other.state, LEAK_CONDUCTANCE, TICK_SECONDS);
                }
            }
        }
    }

    private record RoomPair(long low, long high) {
        static RoomPair of(long a, long b) {
            return a < b ? new RoomPair(a, b) : new RoomPair(b, a);
        }
    }

    /** A room's physics plus its sealing status, for display. */
    /**
     * A room's physics plus how it is bounded.
     *
     * @param sealed      pressurizable: neither open to space nor oversized
     * @param openToSpace has a path to vacuum, so it cannot hold gas at all — this,
     *                    not {@code !sealed}, is what "vacuum" means to the player
     */
    public record RoomReading(RoomState state, boolean sealed, boolean openToSpace) {
        /** True for an enclosed but unpressurizable volume — a big tunnel network. */
        public boolean unsealableEnclosure() {
            return !sealed && !openToSpace;
        }
    }

    /**
     * Diagnostic snapshot for the analyzer's debug mode.
     *
     * @param cells packed positions of every cell (copy — safe to iterate later)
     * @param leaks packed positions of leaky boundary blocks
     */
    public record DebugReading(RoomState state, boolean sealed, boolean overCap,
                               int leakCount, long ticksSinceScan, LongSet cells, LongSet leaks) {}

    // ---------------------------------------------------------------- persistence

    private record SavedRoom(long id, double temperatureK, boolean sealed, boolean openToSpace,
                             List<Long> cells, List<Long> leaks, Map<String, Double> gases) {
        static final Codec<SavedRoom> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("id").forGetter(SavedRoom::id),
                Codec.DOUBLE.fieldOf("temperature_k").forGetter(SavedRoom::temperatureK),
                Codec.BOOL.fieldOf("sealed").forGetter(SavedRoom::sealed),
                // v1 saves lack this; defaulting to "not open" is safe because the
                // next scan re-derives it before any venting decision is made.
                Codec.BOOL.optionalFieldOf("open_to_space", false).forGetter(SavedRoom::openToSpace),
                Codec.LONG.listOf().fieldOf("cells").forGetter(SavedRoom::cells),
                Codec.LONG.listOf().fieldOf("leaks").forGetter(SavedRoom::leaks),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("gases_mol").forGetter(SavedRoom::gases)
        ).apply(instance, SavedRoom::new));
    }

    /** Save format version (rule 5): bump on structural change, ship an upgrader. */
    private static final int SAVE_VERSION = 2;

    private static Codec<Atmosphere> codec(ServerLevel level) {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("version", 1).forGetter(a -> SAVE_VERSION),
                Codec.LONG.fieldOf("next_room_id").forGetter(a -> a.nextRoomId),
                SavedRoom.CODEC.listOf().fieldOf("rooms").forGetter(Atmosphere::snapshot)
        ).apply(instance, (version, nextId, saved) -> restore(level, nextId, saved)));
    }

    private List<SavedRoom> snapshot() {
        List<SavedRoom> out = new ArrayList<>(rooms.size());
        for (Room room : rooms.values()) {
            Map<String, Double> gases = new HashMap<>();
            for (Gas gas : Gas.values()) {
                double moles = room.state.gases().get(gas);
                if (moles > 0) {
                    gases.put(gas.symbol(), moles);
                }
            }
            out.add(new SavedRoom(room.id(), room.state.temperatureK(), room.sealed, room.openToSpace,
                    boxed(room.cells), boxed(room.leaks), gases));
        }
        return out;
    }

    private static Atmosphere restore(ServerLevel level, long nextId, List<SavedRoom> saved) {
        Atmosphere atmosphere = new Atmosphere(level);
        atmosphere.nextRoomId = nextId;
        for (SavedRoom savedRoom : saved) {
            if (savedRoom.cells().isEmpty()) {
                continue;
            }
            GasMixture mixture = new GasMixture();
            for (Gas gas : Gas.values()) {
                Double moles = savedRoom.gases().get(gas.symbol());
                if (moles != null) {
                    mixture.add(gas, moles);
                }
            }
            RoomState state = new RoomState(savedRoom.id(), savedRoom.cells().size(),
                    mixture, savedRoom.temperatureK());
            Room room = new Room(state, new LongOpenHashSet(savedRoom.cells()),
                    new LongOpenHashSet(savedRoom.leaks()), savedRoom.sealed());
            room.openToSpace = savedRoom.openToSpace();
            // Not persisted: derivable, and the first rescan (within the TTL) sets it.
            room.overCap = !savedRoom.sealed() && !savedRoom.openToSpace();
            atmosphere.rooms.put(room.id(), room);
            for (LongIterator it = room.cells.iterator(); it.hasNext(); ) {
                atmosphere.cellToRoom.put(it.nextLong(), room.id());
            }
        }
        return atmosphere;
    }

    private static List<Long> boxed(LongSet set) {
        return new LongArrayList(set);
    }
}
