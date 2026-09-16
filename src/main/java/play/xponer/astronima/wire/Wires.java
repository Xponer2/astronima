package play.xponer.astronima.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.circuit.WireGauge;
import play.xponer.astronima.sim.wire.PixelGeometry;
import play.xponer.astronima.sim.wire.WirePixel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reading and writing the wire layer, and walking what it joins.
 *
 * <p>The seam between the world and {@code sim/wire}: where a trace <em>can</em> go is decided by
 * the tested geometry, and what is <em>actually there</em> is decided here. Keeping those apart
 * is what lets the connection rules be proved exhaustively without a world.
 */
public final class Wires {

    /** Is this pixel carrying that colour. */
    public static boolean has(LevelAccessor level, WirePixel pixel, DyeColor colour) {
        return trace(level, pixel, colour).filter(t -> t.has(pixel.u(), pixel.v())).isPresent();
    }

    /** The trace of that colour on the surface this pixel is on. */
    public static Optional<WireTrace> trace(LevelAccessor level, WirePixel pixel, DyeColor colour) {
        BlockPos cell = cellOf(pixel);
        return chunk(level, cell).trace(cell, Faces.of(pixel.face()), colour);
    }

    /** Every trace on the surface this pixel is on, whatever colour. */
    public static List<WireTrace> bundleOn(LevelAccessor level, BlockPos cell, Direction face) {
        return chunk(level, cell).allOn(cell, face);
    }

    /**
     * Whether a trace may be laid here — two questions, and the second one matters more than it
     * looks.
     *
     * <p><strong>Something to fasten it to</strong>, and <strong>somewhere for it to be</strong>.
     * The cell itself must not already be filled by a solid block: you cannot pull a cable
     * through rock. Without that check the router happily threaded runs <em>through</em>
     * obstacles — reported from play as <em>"если поставить блок и начать прокладывать, провода
     * просто пройдут под ним"</em> — which looked like the pathfinder ignoring the world, because
     * it was.
     *
     * <p><strong>And this is not the same as burial, which still works.</strong> Laying a trace
     * needs a free cell; a wall built over one afterwards leaves it exactly where it was. That is
     * the real distinction and the honest one — you can plaster over a cable, you cannot thread
     * one through solid stone — and it means the flagship trick survives the fix intact.
     */
    public static boolean canPlace(LevelAccessor level, BlockPos cell, Direction face) {
        BlockPos support = cell.relative(face);
        if (!level.getBlockState(support).isFaceSturdy(level, support, face.getOpposite())) {
            return false;
        }
        return !level.getBlockState(cell).isCollisionShapeFullBlock(level, cell);
    }

    /**
     * The same question about one pixel, which is one obstacle stricter: <strong>you cannot route
     * a wire under a component.</strong>
     *
     * <p>This is the form the router's {@code Space} asks, so the ghost preview, the click that
     * commits it and the server's re-run of the same search all consult one predicate. Three
     * separate answers to "is this pixel free" would be three chances to disagree, and a
     * disagreement here is invisible: the run simply does not arrive.
     */
    public static boolean canPlace(LevelAccessor level, WirePixel pixel) {
        BlockPos cell = cellOf(pixel);
        Direction face = Faces.of(pixel.face());
        if (!canPlace(level, cell, face)) {
            return false;
        }
        return !blockedByPart(level, cell, face, pixel.u(), pixel.v());
    }

    /**
     * How much a router should dislike a pixel that would touch an unrelated run of its own
     * colour.
     *
     * <p>Six steps' worth. Enough to walk around an obstacle rather than brush past it, small
     * enough that a corridor with room for exactly one lane still gets wired.
     */
    public static final int BRUSH_PENALTY = 6;

    /**
     * Whether laying this pixel would put bare conductor against an existing run of the same
     * colour that it is not already part of.
     *
     * <p>Two same-colour conductors touching <em>are</em> one circuit — that is the rule the whole
     * colour system rests on and it is the physical answer. What was wrong was that the
     * <strong>router</strong> made that connection on the player's behalf, invisibly, while
     * getting from A to B. So this is what the route is charged for and what the ghost warns
     * about.
     *
     * <p>Only the four in-plane neighbours on the same face are checked, and deliberately: that is
     * the case a player actually meets — two runs laid side by side down one wall — and it costs a
     * single trace lookup rather than twelve. A run meeting another across a cell seam is a corner
     * they are looking straight at.
     */
    public static boolean brushes(LevelAccessor level, WirePixel pixel, DyeColor colour) {
        BlockPos cell = cellOf(pixel);
        Direction face = Faces.of(pixel.face());
        Optional<WireTrace> found = chunk(level, cell).trace(cell, face, colour);
        if (found.isEmpty()) {
            return false;
        }
        WireTrace trace = found.get();
        if (trace.has(pixel.u(), pixel.v())) {
            return false; // already ours: retracing a run must be free
        }
        return sideBySide(trace, pixel.u() - 1, pixel.v())
                || sideBySide(trace, pixel.u() + 1, pixel.v())
                || sideBySide(trace, pixel.u(), pixel.v() - 1)
                || sideBySide(trace, pixel.u(), pixel.v() + 1);
    }

    private static boolean sideBySide(WireTrace trace, int u, int v) {
        return play.xponer.astronima.sim.wire.FaceBasis.onGrid(u)
                && play.xponer.astronima.sim.wire.FaceBasis.onGrid(v) && trace.has(u, v);
    }

    /**
     * The existing conductor a proposed route would join up with.
     *
     * <p>What the ghost lights amber, so <em>"this will become one circuit with that"</em> is
     * something the player sees before they click rather than something they discover with a
     * meter afterwards. The router already prefers to keep a gap; when it cannot, this is what
     * says so.
     */
    public static List<WirePixel> wouldJoin(LevelAccessor level, List<WirePixel> route,
                                            DyeColor colour, int limit) {
        Set<WirePixel> laid = new HashSet<>(route);
        List<WirePixel> joined = new ArrayList<>();
        for (WirePixel pixel : route) {
            if (joined.size() >= limit) {
                break;
            }
            for (WirePixel candidate : PixelGeometry.neighbours(pixel)) {
                if (!laid.contains(candidate) && !joined.contains(candidate)
                        && has(level, candidate, colour)) {
                    joined.add(candidate);
                }
            }
        }
        return joined;
    }

    // ---- parts -------------------------------------------------------------------

    /** Whether a part's housing — not its pad — occupies that pixel. */
    public static boolean blockedByPart(LevelAccessor level, BlockPos cell, Direction face,
                                        int u, int v) {
        for (WirePart part : chunk(level, cell).partsOn(cell, face)) {
            if (part.blocks(u, v)) {
                return true;
            }
        }
        return false;
    }

    /** Every part on a surface. */
    public static List<WirePart> partsOn(LevelAccessor level, BlockPos cell, Direction face) {
        return chunk(level, cell).partsOn(cell, face);
    }

    /** Whichever part covers that pixel. */
    public static Optional<WirePart> partAt(LevelAccessor level, BlockPos cell, Direction face,
                                            int u, int v) {
        return chunk(level, cell).partAt(cell, face, u, v);
    }

    /** Where a bridging part carries this pixel to, or null. */
    public static @org.jspecify.annotations.Nullable WirePixel bridgeFrom(LevelAccessor level,
                                                                         WirePixel pixel) {
        BlockPos cell = cellOf(pixel);
        Direction face = Faces.of(pixel.face());
        for (WirePart part : chunk(level, cell).partsOn(cell, face)) {
            WirePixel across = part.bridgePartner(pixel.u(), pixel.v());
            if (across != null) {
                return across;
            }
        }
        return null;
    }

    public static Optional<WirePart> partAt(LevelAccessor level, WirePixel pixel) {
        return partAt(level, cellOf(pixel), Faces.of(pixel.face()), pixel.u(), pixel.v());
    }

    /**
     * Whether a part may be mounted here: something to fasten to, room to be, nothing in the way.
     *
     * <p>The housing needs bare pixels; <strong>a pad landing on existing wire is fine</strong>,
     * because bolting a component down onto a trace that already reaches it is exactly what a
     * player wiring a board does. What is refused is a housing sitting on top of conductor, which
     * would bury a run under a part with no way to see it.
     */
    public static boolean canMount(LevelAccessor level, WirePart part) {
        BlockPos cell = part.cell();
        Direction face = part.face();
        if (!canPlace(level, cell, face)) {
            return false;
        }
        if (!play.xponer.astronima.sim.wire.PartFootprint.fits(part.u(), part.v(),
                part.type().width(), part.type().height(), part.rotation())) {
            return false;
        }
        WireChunk wires = chunk(level, cell);
        for (WirePart existing : wires.parts()) {
            if (part.overlaps(existing)) {
                return false;
            }
        }
        for (int[] pixel : part.body()) {
            if (part.padAt(pixel[0], pixel[1]) != null) {
                continue;
            }
            for (WireTrace trace : wires.allOn(cell, face)) {
                if (trace.has(pixel[0], pixel[1])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Bolts a part down, saves it, and tells the clients watching. */
    public static void mount(LevelAccessor level, WirePart part) {
        ChunkAccess chunk = level.getChunk(part.cell());
        chunk.setData(ModAttachments.WIRES.get(),
                chunk.getData(ModAttachments.WIRES.get()).with(part));
        touched(chunk);
        remember(level, chunk);
    }

    /**
     * Tells the ticker this chunk now holds wire.
     *
     * <p>Wire laid in a chunk that was already loaded raises no chunk event at all, so a
     * load-time index alone would miss it — and that is the common case, not the rare one.
     */
    private static void remember(LevelAccessor level, ChunkAccess chunk) {
        if (level instanceof net.minecraft.world.level.Level world) {
            WireTicker.remember(world, chunk.getPos());
        }
    }

    /**
     * Writes a part back after its state changed, <strong>and only then</strong>.
     *
     * <p>The ticker calls this for every part it re-evaluates, and the great majority of those
     * are unchanged. Saving and syncing a chunk on every tick per gate would put a base's worth of
     * logic on the network continuously for a picture that is not moving — the same mistake the
     * stud renderer made when it walked 117 649 blockstates a frame.
     */
    public static boolean update(LevelAccessor level, WirePart before, WirePart after) {
        if (before.equals(after)) {
            return false;
        }
        mount(level, after);
        return true;
    }

    /**
     * Takes a part off the wall.
     *
     * <p>There is deliberately no "everything hanging on this block" version. A part checks its
     * own support in {@code WireTicker}, because the event a player's pickaxe raises is not the
     * only way a block stops existing (PLAN rule 24) — and one mechanism cannot disagree with
     * itself.
     */
    public static void unmount(LevelAccessor level, WirePart part) {
        ChunkAccess chunk = level.getChunk(part.cell());
        chunk.setData(ModAttachments.WIRES.get(),
                chunk.getData(ModAttachments.WIRES.get()).without(part));
        touched(chunk);
    }

    /** Lays one pixel of conductor, saves it, and tells the clients watching. */
    public static void place(LevelAccessor level, WirePixel pixel, DyeColor colour,
                             ConductorMaterial material, WireGauge gauge) {
        BlockPos cell = cellOf(pixel);
        Direction face = Faces.of(pixel.face());
        ChunkAccess chunk = level.getChunk(cell);
        WireChunk wires = chunk.getData(ModAttachments.WIRES.get());
        WireTrace trace = wires.trace(cell, face, colour)
                .orElseGet(() -> WireTrace.empty(cell, face, colour, material, gauge));
        chunk.setData(ModAttachments.WIRES.get(),
                wires.with(trace.with(pixel.u(), pixel.v())));
        touched(chunk);
        remember(level, chunk);
    }

    /**
     * Lays a whole routed trace at once.
     *
     * <p>One save and one sync for the run rather than one per pixel — a twenty-block route is
     * three hundred pixels, and syncing each would send the same chunk three hundred times.
     */
    public static int placeAll(LevelAccessor level, List<WirePixel> route, DyeColor colour,
                               ConductorMaterial material, WireGauge gauge) {
        Set<ChunkAccess> touched = new HashSet<>();
        int laid = 0;
        for (WirePixel pixel : route) {
            BlockPos cell = cellOf(pixel);
            Direction face = Faces.of(pixel.face());
            ChunkAccess chunk = level.getChunk(cell);
            WireChunk wires = chunk.getData(ModAttachments.WIRES.get());
            WireTrace trace = wires.trace(cell, face, colour)
                    .orElseGet(() -> WireTrace.empty(cell, face, colour, material, gauge));
            if (trace.has(pixel.u(), pixel.v())) {
                continue;
            }
            chunk.setData(ModAttachments.WIRES.get(), wires.with(trace.with(pixel.u(), pixel.v())));
            touched.add(chunk);
            laid++;
        }
        touched.forEach(Wires::touched);
        touched.forEach(chunk -> remember(level, chunk));
        return laid;
    }

    /** Takes one pixel out. */
    public static boolean remove(LevelAccessor level, WirePixel pixel, DyeColor colour) {
        BlockPos cell = cellOf(pixel);
        Direction face = Faces.of(pixel.face());
        ChunkAccess chunk = level.getChunk(cell);
        WireChunk wires = chunk.getData(ModAttachments.WIRES.get());
        Optional<WireTrace> found = wires.trace(cell, face, colour);
        if (found.isEmpty() || !found.get().has(pixel.u(), pixel.v())) {
            return false;
        }
        chunk.setData(ModAttachments.WIRES.get(),
                wires.with(found.get().without(pixel.u(), pixel.v())));
        touched(chunk);
        return true;
    }

    /** Writes one trace back over itself — same slot, new state. */
    public static void replace(LevelAccessor level, WireTrace trace) {
        replace(level, trace, true);
    }

    /**
     * The same, with the choice of whether anybody watching needs to be told.
     *
     * <p><strong>Saving and syncing are separate, and conflating them lost data.</strong> A wire
     * warming a degree a second must keep every degree or it never warms at all — the first
     * version skipped the write when the change was small, so each step read the stored value,
     * added its degree, decided that was not worth writing, and threw it away. The temperature sat
     * still forever while the physics said it should be climbing.
     *
     * <p>But a chunk resynced every tick for a colour nobody can distinguish is real traffic. So
     * the value is always kept and the <em>packet</em> is what waits for a visible change.
     */
    public static void replace(LevelAccessor level, WireTrace trace, boolean sync) {
        ChunkAccess chunk = level.getChunk(trace.cell());
        chunk.setData(ModAttachments.WIRES.get(),
                chunk.getData(ModAttachments.WIRES.get()).with(trace));
        chunk.markUnsaved();
        if (sync) {
            chunk.syncData(ModAttachments.WIRES.get());
        }
    }

    /** Takes one whole trace out — one cell, one face, one colour. */
    public static void removeOne(LevelAccessor level, WireTrace trace) {
        ChunkAccess chunk = level.getChunk(trace.cell());
        chunk.setData(ModAttachments.WIRES.get(),
                chunk.getData(ModAttachments.WIRES.get())
                        .with(trace.at(WireTrace.REST_K).cleared()));
        touched(chunk);
    }

    /** Strips a surface bare, and says how much conductor came off it. */
    public static int removeAll(LevelAccessor level, BlockPos cell, Direction face) {
        ChunkAccess chunk = level.getChunk(cell);
        WireChunk wires = chunk.getData(ModAttachments.WIRES.get());
        int pulled = 0;
        for (WireTrace trace : wires.allOn(cell, face)) {
            pulled += trace.pixelCount();
        }
        if (pulled > 0) {
            chunk.setData(ModAttachments.WIRES.get(), wires.withoutAll(cell, face));
            touched(chunk);
        }
        return pulled;
    }

    // ---- networks --------------------------------------------------------------

    /**
     * Everything electrically joined to this pixel.
     *
     * <p>A flood fill across {@link PixelGeometry#neighbours}, taking only pixels of the
     * <strong>same colour</strong> — which is how two runs cross without meeting.
     *
     * <p><strong>Nothing stores a network id, deliberately.</strong> The tempting design is to
     * label each network and update the labels on change, and that is precisely the shape that
     * once gave two rooms one {@code RoomState}: cutting a network has to name two things, and
     * whatever is left over keeps answering to the old name (PLAN rule 16). A network derived on
     * demand cannot split wrongly, because there is nothing to split.
     */
    public static Network network(LevelAccessor level, WirePixel start, DyeColor colour,
                                  int limit) {
        // Nothing at the start is an empty network, not a network of one.
        //
        // The walk used to seed itself with the starting pixel whatever was there, so asking
        // about a pixel whose conductor had just burned through came back as a one-pixel run —
        // which reads as "still wired" to every caller. Every existing caller happens to check
        // first; the one that did not was a test, and it spent ten minutes driving power down a
        // wire that no longer existed.
        if (!has(level, start, colour)) {
            return new Network(List.of(), List.of(), List.of(), List.of(), false);
        }
        Set<WirePixel> seen = new HashSet<>();
        List<WirePixel> found = new ArrayList<>();
        Deque<WirePixel> frontier = new ArrayDeque<>();
        Set<ConductorMaterial> metals = new HashSet<>();
        Set<WireGauge> gauges = new HashSet<>();
        frontier.add(start);
        seen.add(start);
        boolean truncated = false;

        while (!frontier.isEmpty()) {
            if (found.size() >= limit) {
                truncated = true;
                break;
            }
            WirePixel current = frontier.poll();
            found.add(current);
            trace(level, current, colour).ifPresent(t -> {
                metals.add(t.material());
                gauges.add(t.gauge());
            });

            // Straight through a bridging part, if one joins this pixel to another. A fuse is
            // a length of conductor with a condition on it: while it holds, the two sides are one
            // circuit; when it goes, they are two — which is the whole of what a fuse does and
            // cannot be expressed by adjacency alone, because the housing sits between the pads.
            WirePixel across = bridgeFrom(level, current);
            if (across != null && seen.add(across) && isLoaded(level, cellOf(across))
                    && has(level, across, colour)) {
                frontier.add(across);
            }
            for (WirePixel candidate : PixelGeometry.neighbours(current)) {
                if (!seen.add(candidate)) {
                    continue;
                }
                BlockPos cell = cellOf(candidate);
                // Only look where the world is loaded: a run leaving into unloaded terrain must
                // stall visibly rather than drag chunks in behind it (rule 18).
                if (!isLoaded(level, cell)) {
                    truncated = true;
                    continue;
                }
                if (has(level, candidate, colour)) {
                    frontier.add(candidate);
                }
            }
        }
        return new Network(List.copyOf(found), linksAmong(level, found), List.copyOf(metals),
                List.copyOf(gauges), truncated);
    }

    public static Network network(LevelAccessor level, WirePixel start, DyeColor colour) {
        return network(level, start, colour, DEFAULT_LIMIT);
    }

    /**
     * The shortest run of pixels joining two points of the <strong>same</strong> network, walked
     * order start-to-end and including both ends — or empty when they are not on one network at
     * all, or when they are the same pixel.
     *
     * <p>Same walk as {@link #network}, over the same {@link PixelGeometry#neighbours} and the
     * same bridging-part rule, but tracking one parent per visited pixel instead of collecting
     * everything reachable — reconstructing a path the moment the walk reaches {@code end} rather
     * than exhausting the whole network first. BFS over an unweighted grid already gives the
     * shortest path deterministically; {@code PixelGeometry.neighbours} returns a fixed order and
     * the frontier is a plain queue, so two players cutting the same two points always get the
     * same cut, the same reasoning {@link play.xponer.astronima.sim.logic.Circuit#normalised}
     * already leans on for its own tie-break (rule 19).
     */
    public static List<WirePixel> pathBetween(LevelAccessor level, WirePixel start, WirePixel end,
                                              DyeColor colour, int limit) {
        if (start.equals(end) || !has(level, start, colour) || !has(level, end, colour)) {
            return List.of();
        }
        Map<WirePixel, WirePixel> cameFrom = new HashMap<>();
        Deque<WirePixel> frontier = new ArrayDeque<>();
        frontier.add(start);
        cameFrom.put(start, start);

        while (!frontier.isEmpty()) {
            if (cameFrom.size() >= limit) {
                return List.of();
            }
            WirePixel current = frontier.poll();
            if (current.equals(end)) {
                return reconstruct(cameFrom, start, end);
            }
            WirePixel across = bridgeFrom(level, current);
            if (across != null && !cameFrom.containsKey(across) && isLoaded(level, cellOf(across))
                    && has(level, across, colour)) {
                cameFrom.put(across, current);
                frontier.add(across);
            }
            for (WirePixel candidate : PixelGeometry.neighbours(current)) {
                if (cameFrom.containsKey(candidate)) {
                    continue;
                }
                BlockPos cell = cellOf(candidate);
                if (!isLoaded(level, cell)) {
                    continue;
                }
                if (has(level, candidate, colour)) {
                    cameFrom.put(candidate, current);
                    frontier.add(candidate);
                }
            }
        }
        return List.of();
    }

    private static List<WirePixel> reconstruct(Map<WirePixel, WirePixel> cameFrom, WirePixel start,
                                               WirePixel end) {
        List<WirePixel> path = new ArrayList<>();
        WirePixel step = end;
        while (!step.equals(start)) {
            path.add(step);
            step = cameFrom.get(step);
        }
        path.add(start);
        java.util.Collections.reverse(path);
        return path;
    }

    /**
     * Whether the two ends of {@code path} would still be joined by some other route once every
     * pixel in {@code path} itself is gone — the one case the wire snips has to name rather than
     * guess (design note, Phase B): a run on a loop has more than one way from one point to the
     * other, and cutting the shortest of them leaves the two "sides" the player meant to separate
     * still one circuit, joined the long way round. Nothing about the cut pixels is wrong in that
     * case; the operation is just not what it looked like.
     *
     * <p>Walked from whichever of the path's own neighbours are <em>not</em> part of the path,
     * avoiding every path pixel along the way — the same {@link PixelGeometry#neighbours} and
     * bridging-part walk {@link #pathBetween} already uses, just seeded from both ends at once
     * and never allowed back onto the pixels about to be removed.
     */
    public static boolean wouldStillBeJoinedAfter(LevelAccessor level, List<WirePixel> path,
                                                  DyeColor colour, int limit) {
        if (path.size() < 2) {
            return false;
        }
        Set<WirePixel> cut = Set.copyOf(path);
        List<WirePixel> startSide = neighboursOutside(level, path.get(0), colour, cut);
        List<WirePixel> endSide = neighboursOutside(level, path.get(path.size() - 1), colour, cut);
        if (startSide.isEmpty() || endSide.isEmpty()) {
            return false; // one end is a stub - nothing left to bypass through
        }
        Set<WirePixel> targets = new HashSet<>(endSide);

        Set<WirePixel> seen = new HashSet<>(cut);
        seen.addAll(startSide);
        Deque<WirePixel> frontier = new ArrayDeque<>(startSide);
        while (!frontier.isEmpty()) {
            if (seen.size() >= limit) {
                return false; // truncated: cannot prove a bypass exists, so do not block the cut
            }
            WirePixel current = frontier.poll();
            if (targets.contains(current)) {
                return true;
            }
            WirePixel across = bridgeFrom(level, current);
            if (across != null && seen.add(across) && isLoaded(level, cellOf(across))
                    && has(level, across, colour)) {
                frontier.add(across);
            }
            for (WirePixel candidate : PixelGeometry.neighbours(current)) {
                if (!seen.add(candidate)) {
                    continue;
                }
                if (!isLoaded(level, cellOf(candidate))) {
                    continue;
                }
                if (has(level, candidate, colour)) {
                    frontier.add(candidate);
                }
            }
        }
        return false;
    }

    /** Same-colour neighbours of one pixel, skipping whatever is in {@code exclude}. */
    private static List<WirePixel> neighboursOutside(LevelAccessor level, WirePixel pixel,
                                                      DyeColor colour, Set<WirePixel> exclude) {
        List<WirePixel> found = new ArrayList<>();
        WirePixel across = bridgeFrom(level, pixel);
        if (across != null && !exclude.contains(across) && isLoaded(level, cellOf(across))
                && has(level, across, colour)) {
            found.add(across);
        }
        for (WirePixel candidate : PixelGeometry.neighbours(pixel)) {
            if (exclude.contains(candidate) || !isLoaded(level, cellOf(candidate))) {
                continue;
            }
            if (has(level, candidate, colour)) {
                found.add(candidate);
            }
        }
        return found;
    }

    /**
     * A run and everything it joins.
     *
     * @param truncated true when the walk stopped early — the answer is a floor, not a total
     */
    /**
     * Every join between two pixels of a found run, as index pairs into {@code found}.
     *
     * <p><strong>Derived after the walk, not during it</strong>, and that is the whole reason this
     * method exists: a walk records the edges of its own spanning <em>tree</em> and skips every
     * pixel it has already seen — so the cross-connections, which are exactly the edges that make a
     * loop, are precisely the ones a walk never notices. A graph missing its loops is a graph in
     * which two wires in parallel look like one long one.
     *
     * <p>Sorted by construction: the index order of the walk, then the fixed neighbour order. A
     * mesh that reduced differently depending on hash order would answer differently after a relog
     * (rule 19).
     */
    private static List<int[]> linksAmong(LevelAccessor level, List<WirePixel> found) {
        java.util.Map<WirePixel, Integer> index = new java.util.HashMap<>();
        for (int i = 0; i < found.size(); i++) {
            index.put(found.get(i), i);
        }
        List<int[]> links = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            for (WirePixel neighbour : PixelGeometry.neighbours(found.get(i))) {
                Integer other = index.get(neighbour);
                if (other != null && other > i) {
                    links.add(new int[] {i, other});
                }
            }
            WirePixel across = bridgeFrom(level, found.get(i));
            Integer other = across == null ? null : index.get(across);
            if (other != null && other > i) {
                // Negative marks the far index as a bond rather than a length of conductor: a
                // fuse's element is nothing beside metres of wire, but the two ends are joined.
                links.add(new int[] {i, other, 1});
            }
        }
        return List.copyOf(links);
    }

    /**
     * A run and everything it joins.
     *
     * @param links     which pixels touch which, as index pairs — a third element marks a bond
     *                  through a bridging part rather than plain adjacency
     * @param truncated true when the walk stopped early — the answer is a floor, not a total
     */
    public record Network(List<WirePixel> pixels, List<int[]> links,
                          List<ConductorMaterial> metals,
                          List<WireGauge> gauges, boolean truncated) {

        /** Pixels of conductor, which is sixteen to the metre. */
        public int length() {
            return pixels.size();
        }

        /** The run's length in metres, which is what resistance is charged against. */
        public double metres() {
            return pixels.size() / (double) play.xponer.astronima.sim.wire.FaceBasis.GRID;
        }

        /**
         * The worst conductor anywhere in the run.
         *
         * <p>A chain is its weakest link, and a player who splices one pixel of iron into an
         * aluminium bus has made an iron bus with expensive ends. Reporting the worst is what
         * makes that visible instead of averaging it away.
         */
        public Optional<ConductorMaterial> weakest() {
            return metals.stream().max((a, b) ->
                    Double.compare(a.resistivity20C(), b.resistivity20C()));
        }

        /**
         * The thinnest conductor anywhere in the run.
         *
         * <p>The same rule the metal follows, and for the same reason: a chain is its weakest
         * link. Splice one pixel of signal wire into a busbar and you have installed a fuse
         * nobody meant to fit — which is a real mistake with a real symptom, and the meter says
         * so before the wire does.
         */
        public WireGauge thinnest() {
            WireGauge thinnest = WireGauge.DEFAULT;
            boolean any = false;
            for (WireGauge gauge : gauges) {
                thinnest = any ? thinnest.thinnerOf(gauge) : gauge;
                any = true;
            }
            return thinnest;
        }

        public boolean isEmpty() {
            return pixels.isEmpty();
        }
    }

    /** How far one walk goes before it admits it has not finished. */
    public static final int DEFAULT_LIMIT = 20_000;

    // ---- plumbing --------------------------------------------------------------

    public static BlockPos cellOf(WirePixel pixel) {
        return new BlockPos(pixel.x(), pixel.y(), pixel.z());
    }

    private static WireChunk chunk(LevelAccessor level, BlockPos cell) {
        return level.getChunk(cell).getData(ModAttachments.WIRES.get());
    }

    /**
     * Whether this cell's chunk is loaded, <strong>without loading it</strong>.
     *
     * <p>Spelled out rather than using {@code hasChunkAt}, which vanilla deprecates precisely
     * because it reads as a harmless question. Asking for {@code FULL} with
     * {@code loadOrGenerate = false} says exactly what is meant: look only at what is already
     * here. A network walk that generated terrain by asking about it would turn laying cable
     * into world generation.
     */
    private static boolean isLoaded(LevelAccessor level, BlockPos cell) {
        return level.getChunk(SectionPos.blockToSectionCoord(cell.getX()),
                SectionPos.blockToSectionCoord(cell.getZ()), ChunkStatus.FULL, false) != null;
    }

    /** Save it, and push it to whoever is watching. */
    private static void touched(ChunkAccess chunk) {
        chunk.markUnsaved();
        chunk.syncData(ModAttachments.WIRES.get());
    }

    private Wires() {}
}
