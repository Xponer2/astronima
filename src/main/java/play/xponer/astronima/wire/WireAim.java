package play.xponer.astronima.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.FaceBasis;
import play.xponer.astronima.sim.wire.WirePixel;

/**
 * What the player is <em>actually</em> aiming at — one answer, shared by every tool.
 *
 * <p><strong>This exists because the first version was unusable, and the reason is arithmetic.</strong>
 * A block face is sixteen pixels across; at three metres, one pixel subtends about a fifth of a
 * degree. Asking a player to put a crosshair on a specific sixteenth of a face is asking them to
 * aim ten times finer than the game asks anywhere else — so wiring "didn't work" constantly, and
 * worse, <em>looked</em> like it should have. Reported as <em>"непонятно, подключено ли точно к
 * блоку"</em> and <em>"резак не всегда находит провода"</em>.
 *
 * <p>So nothing is aimed at pixel-precisely any more. A tool asks this, and it answers with the
 * nearest thing worth hitting: <strong>a terminal first, then existing wire, then the bare
 * pixel</strong>. Terminals win because they are the thing you are almost always trying to reach
 * and the thing it is most expensive to miss — a run that lands one pixel off a stud looks
 * connected and is not.
 *
 * <p>Used by the coil, the cutters and the goggles, so what the ghost shows, what a click does
 * and what the meter reads are the same point. Three separate answers to "what am I pointing at"
 * would be three chances to disagree, and disagreement here is invisible.
 */
public final class WireAim {

    /**
     * How far the snap reaches, in pixels.
     *
     * <p>Four — a quarter of a face. Wide enough that ordinary aim lands on the stud you meant,
     * narrow enough that two studs on one strip (four pixels apart) are still separately
     * reachable, because that is exactly the case where snapping to the wrong one would be worse
     * than not snapping at all.
     */
    public static final int SNAP = 4;

    /**
     * What the crosshair resolved to, and whether that is a real connection point.
     *
     * @param part the wire-layer part whose pad this is, when it is one
     * @param pad  which pad of that part
     */
    public record Aim(WirePixel pixel, @Nullable BlockPos block, @Nullable Terminal terminal,
                      @Nullable WirePart part, PartType.@Nullable Pad pad) {

        public Aim(WirePixel pixel, @Nullable BlockPos block, @Nullable Terminal terminal) {
            this(pixel, block, terminal, null, null);
        }

        /** True when this is a published connection point rather than bare surface. */
        public boolean onTerminal() {
            return terminal != null || pad != null;
        }
    }

    /** Whether snapping to nearby pads/terminals/wires is enabled. Toggleable via keybind. */
    public static boolean snapEnabled = true;

    /**
     * Resolves a look at a block face into the point a tool should act on.
     *
     * <p><strong>A connection point beats loose wire, and a part's pad is a connection
     * point.</strong> That was missing and it was reported straight back: <em>"провода постоянно
     * снапятся на ближайшие провода при подключении, а не к мелкому коннекту"</em>. A part is five
     * pixels across with its pads on the edges, so anybody wiring one is aiming somewhere on the
     * housing — and snapping to whatever trace happened to run past instead of to the pad is the
     * one failure that looks exactly like success.
     *
     * @param preferWire when the caller is looking for conductor rather than somewhere to
     *                   connect — the cutters, which should reach the wire under the stud rather
     *                   than the stud
     */
    public static Aim at(Level level, BlockHitResult hit, boolean preferWire) {
        BlockPos cell = hit.getBlockPos().relative(hit.getDirection());
        Direction face = hit.getDirection().getOpposite();
        WirePixel aimed = exact(hit, cell, face);

        if (!snapEnabled) {
            return new Aim(aimed, null, null);
        }

        Aim wire = preferWire ? nearestWire(level, aimed) : null;
        if (wire != null) {
            return wire;
        }
        Aim pad = nearestPad(level, aimed, cell, face);
        if (pad != null) {
            return pad;
        }
        Aim terminal = nearestTerminal(level, aimed, cell, face);
        if (terminal != null) {
            return terminal;
        }
        Aim anyWire = preferWire ? null : nearestWire(level, aimed);
        return anyWire != null ? anyWire : new Aim(aimed, null, null);
    }

    /**
     * The closest pad of a part lying on this same face.
     *
     * <p>Searched before block terminals because a part is <em>on</em> the surface being aimed at
     * while a block's stud belongs to the block behind it — so when both are in reach, the part is
     * the thing under the crosshair.
     */
    private static @Nullable Aim nearestPad(Level level, WirePixel aimed, BlockPos cell,
                                            Direction face) {
        WirePart bestPart = null;
        PartType.Pad best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (WirePart part : Wires.partsOn(level, cell, face)) {
            for (PartType.Pad pad : part.type().pads()) {
                int distance = Math.abs(part.padU(pad) - aimed.u())
                        + Math.abs(part.padV(pad) - aimed.v());
                if (distance <= SNAP && distance < bestDistance) {
                    bestPart = part;
                    best = pad;
                    bestDistance = distance;
                }
            }
        }
        return best == null ? null
                : new Aim(bestPart.padPixel(best), null, null, bestPart, best);
    }

    /** The pixel the crosshair literally landed on, before any snapping. */
    public static WirePixel exact(BlockHitResult hit, BlockPos cell, Direction face) {
        var wireFace = Faces.of(face);
        double localX = hit.getLocation().x - cell.getX();
        double localY = hit.getLocation().y - cell.getY();
        double localZ = hit.getLocation().z - cell.getZ();
        return new WirePixel(cell.getX(), cell.getY(), cell.getZ(), wireFace,
                pixelAlong(FaceBasis.uAxis(wireFace), localX, localY, localZ),
                pixelAlong(FaceBasis.vAxis(wireFace), localX, localY, localZ));
    }

    private static int pixelAlong(play.xponer.astronima.sim.wire.Face axis,
                                  double localX, double localY, double localZ) {
        double along = axis.dx() != 0 ? localX : axis.dy() != 0 ? localY : localZ;
        return Math.clamp((int) Math.floor(along * FaceBasis.GRID), 0, FaceBasis.GRID - 1);
    }

    /** The closest published terminal on the block this face belongs to. */
    private static @Nullable Aim nearestTerminal(Level level, WirePixel aimed, BlockPos cell,
                                                 Direction face) {
        BlockPos block = cell.relative(face);
        BlockState state = level.getBlockState(block);
        if (!(state.getBlock() instanceof Terminated terminated)) {
            return null;
        }
        Terminal best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Terminal terminal : terminated.terminals(state)) {
            if (terminal.wireFace() != face) {
                continue;
            }
            int distance = Math.abs(terminal.u() - aimed.u()) + Math.abs(terminal.v() - aimed.v());
            if (distance <= SNAP && distance < bestDistance) {
                best = terminal;
                bestDistance = distance;
            }
        }
        return best == null ? null : new Aim(best.pixel(block), block, best);
    }

    /** The closest conductor on this same face, for tools that want the wire itself. */
    private static @Nullable Aim nearestWire(Level level, WirePixel aimed) {
        for (int ring = 0; ring <= SNAP; ring++) {
            for (int du = -ring; du <= ring; du++) {
                for (int dv = -ring; dv <= ring; dv++) {
                    if (Math.max(Math.abs(du), Math.abs(dv)) != ring) {
                        continue; // only this ring's edge; the inside was already searched
                    }
                    int u = aimed.u() + du;
                    int v = aimed.v() + dv;
                    if (!FaceBasis.onGrid(u) || !FaceBasis.onGrid(v)) {
                        continue;
                    }
                    WirePixel candidate = new WirePixel(aimed.x(), aimed.y(), aimed.z(),
                            aimed.face(), u, v);
                    if (hasAnyWire(level, candidate)) {
                        return new Aim(candidate, null, null);
                    }
                }
            }
        }
        return null;
    }

    /** Whether any colour of trace occupies that pixel. */
    public static boolean hasAnyWire(Level level, WirePixel pixel) {
        BlockPos cell = new BlockPos(pixel.x(), pixel.y(), pixel.z());
        ChunkAccess chunk = level.getChunk(SectionPos.blockToSectionCoord(cell.getX()),
                SectionPos.blockToSectionCoord(cell.getZ()), ChunkStatus.FULL, false);
        if (chunk == null) {
            return false;
        }
        for (WireTrace trace : chunk.getData(
                play.xponer.astronima.registry.ModAttachments.WIRES.get())
                .allOn(cell, Faces.of(pixel.face()))) {
            if (trace.has(pixel.u(), pixel.v())) {
                return true;
            }
        }
        return false;
    }

    private WireAim() {}
}
