package play.xponer.astronima.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.ChargePulse;
import play.xponer.astronima.sim.wire.PixelGeometry;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.SignalView;
import play.xponer.astronima.wire.Terminal;
import play.xponer.astronima.wire.WirePart;
import play.xponer.astronima.wire.WireTrace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Which circuits are carrying, and where the charge is along them.
 *
 * <p><strong>A wire that looks identical whether or not it is doing anything is the single
 * biggest reason this system was hard to debug.</strong> Every failure — a missed stud, a colour
 * collision, a gate wired backwards — has the same symptom: nothing happens. Marching charge
 * turns that into a picture. You follow the pips out from the source and see exactly where they
 * stop, and that place is the fault.
 *
 * <p><strong>Computed entirely from synced blockstates.</strong> The server's
 * {@code SignalSource} needs a level and block entities; the client has neither, so every source
 * also answers {@link SignalView} from its own state. That is a real constraint on the design
 * rather than a workaround, and a good one: a source whose output cannot be read off its own
 * block is a source the player cannot debug, and now it cannot be built either.
 *
 * <p>Rebuilt on a timer, not per frame — a circuit changes when somebody flips something, not
 * sixty times a second.
 */
public final class WireLive {

    /** How far a circuit is followed. Bounded, because this runs on the render thread. */
    private static final int LIMIT = 2_048;

    private static final long REBUILD_MS = 200;

    public record LivePixel(WirePixel pixel, DyeColor colour) {}

    private static final Set<LivePixel> CARRYING = new HashSet<>();
    private static long rebuiltAt;

    private final long millis;

    private WireLive(long millis) {
        this.millis = millis;
    }

    /** The live set for this frame, rebuilt when it has gone stale. */
    public static WireLive current(ClientLevel level) {
        long now = System.currentTimeMillis();
        if (now - rebuiltAt > REBUILD_MS) {
            rebuiltAt = now;
            rebuild(level);
        }
        return new WireLive(now);
    }

    public boolean isCarrying(WirePixel pixel, DyeColor colour) {
        return CARRYING.contains(new LivePixel(pixel, colour));
    }

    public boolean isCarrying(WirePixel pixel) {
        for (DyeColor colour : DyeColor.values()) {
            if (CARRYING.contains(new LivePixel(pixel, colour))) {
                return true;
            }
        }
        return false;
    }

    /**
     * How lit this pixel is by the travelling charge, 0 at rest and 1 at a crest.
     *
     * <p>Replaces a yes/no pip. A binary pip reads as blinking dots, and blinking dots do not say
     * which way the current is going; a crest with a wake behind it does, because the asymmetry
     * <em>is</em> the direction. The shape itself lives in {@code sim/wire/ChargePulse}, where it
     * is tested — a renderer may paint, but it does not decide (rule 25).
     *
     * <p>Distance along the run is stood in for by the pixel's own coordinates. That is cheap and
     * good enough: the eye reads a regular travelling pattern as flow without checking that the
     * spacing follows the conductor's exact arc length, and computing true arc length would mean
     * ordering every circuit every frame for a purely visual gain.
     */
    public double chargeAt(WirePixel pixel) {
        long along = pixel.u() + pixel.v() + (long) (pixel.x() + pixel.y() + pixel.z()) * 16;
        return ChargePulse.intensityAt(along, millis / 1000.0);
    }

    /** The same rhythm, for a part's output pad — so a gate beats in time with what it feeds. */
    public double chargeAt(long along) {
        return ChargePulse.intensityAt(along, millis / 1000.0);
    }

    /** How far out parts are looked for, in chunks — a couple of rooms' worth. */
    private static final int PART_CHUNKS = 2;

    /**
     * Walks out from every driving output in range and marks whatever it feeds.
     *
     * <p><strong>Two kinds of thing drive a line and both have to be asked.</strong> Blocks answer
     * {@link SignalView} from their synced state; wire-layer <em>parts</em> answer from the
     * {@code outputs} bitfield in the synced chunk data, which is the whole reason that field
     * exists. Asking only the first is what shipped, and the symptom was exactly the one this
     * animation was built to end — reported as <em>"нажимаю ПКМ по кнопке, по проводам ничего не
     * течёт"</em>. Pressing a button really did drive the line; nothing drew it, so the player had
     * no way to tell a working circuit from a broken one, which is the state of affairs the pips
     * exist to fix.
     */
    private static void rebuild(ClientLevel level) {
        CARRYING.clear();
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // Shares TerminalScan's list: two walks of the same blocks is twice the work for one
        // picture, and two chances for the studs and the charge to disagree about what is there.
        for (TerminalScan.Found found : TerminalScan.around(level, player.blockPosition())) {
            if (found.terminal().kind() != Terminal.Kind.SIGNAL_OUT) {
                continue;
            }
            if (found.state().getBlock() instanceof SignalView view
                    && view.isDrivingClient(found.state())) {
                // Landed, not crossed. A stud is one of a row, so a run on its way to the power
                // stud passes over the working stud beside it - and lighting that run up would
                // animate current down a wire the server does not consider attached at all. The
                // picture would be showing a circuit that is not there (PLAN rule 43).
                spread(level, found.terminal().pixel(found.block()), true);
            }
        }
        for (WirePart part : PartScan.near(level, player.blockPosition(), PART_CHUNKS)) {
            for (PartType.Pad pad : part.type().outputs()) {
                if (part.driving(pad)) {
                    // A pad is a contact patch on the face the part lies on, so a run crossing it
                    // is genuinely touching it - the same asymmetry WireSignal draws.
                    spread(level, part.padPixel(pad), false);
                }
            }
        }
    }

    /**
     * Flood fill from one driven stud across whatever colour is landed on it.
     *
     * @param landed whether the run must end on the starting pixel rather than pass over it
     */
    private static void spread(ClientLevel level, WirePixel from, boolean landed) {
        DyeColor colour = colourAt(level, from, landed);
        if (colour == null) {
            return;
        }
        LivePixel startKey = new LivePixel(from, colour);
        if (CARRYING.contains(startKey)) {
            return;
        }
        Deque<WirePixel> frontier = new ArrayDeque<>();
        frontier.add(from);
        CARRYING.add(startKey);
        while (!frontier.isEmpty() && CARRYING.size() < LIMIT) {
            WirePixel current = frontier.poll();
            for (WirePixel candidate : PixelGeometry.neighbours(current)) {
                LivePixel key = new LivePixel(candidate, colour);
                if (CARRYING.contains(key) || !has(level, candidate, colour)) {
                    continue;
                }
                CARRYING.add(key);
                frontier.add(candidate);
            }
        }
    }

    private static @Nullable DyeColor colourAt(ClientLevel level, WirePixel pixel,
                                               boolean landed) {
        BlockPos cell = new BlockPos(pixel.x(), pixel.y(), pixel.z());
        ChunkAccess chunk = chunkAt(level, cell);
        if (chunk == null) {
            return null;
        }
        for (WireTrace trace : chunk.getData(ModAttachments.WIRES.get())
                .allOn(cell, Faces.of(pixel.face()))) {
            if (landed ? trace.endsAt(pixel.u(), pixel.v()) : trace.has(pixel.u(), pixel.v())) {
                return trace.colour();
            }
        }
        return null;
    }

    private static boolean has(ClientLevel level, WirePixel pixel, DyeColor colour) {
        BlockPos cell = new BlockPos(pixel.x(), pixel.y(), pixel.z());
        ChunkAccess chunk = chunkAt(level, cell);
        return chunk != null && chunk.getData(ModAttachments.WIRES.get())
                .trace(cell, Faces.of(pixel.face()), colour)
                .filter(trace -> trace.has(pixel.u(), pixel.v()))
                .isPresent();
    }

    private static @Nullable ChunkAccess chunkAt(ClientLevel level, BlockPos cell) {
        return level.getChunk(SectionPos.blockToSectionCoord(cell.getX()),
                SectionPos.blockToSectionCoord(cell.getZ()), ChunkStatus.FULL, false);
    }
}
