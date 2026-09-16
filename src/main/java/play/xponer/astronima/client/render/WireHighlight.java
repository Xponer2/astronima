package play.xponer.astronima.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import play.xponer.astronima.item.WireCoilItem;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.wire.PixelGeometry;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WireTrace;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Point at a wire and the whole circuit lights up.
 *
 * <p>The question a player asks constantly once they have more than one run — <em>is this one
 * circuit or two?</em> — and until now the only way to answer it was a command. Wire is one pixel
 * thick and colours repeat; a run that looks continuous may have been cut by a broken block three
 * rooms away, and a run that looks like two may be spliced somewhere out of sight.
 *
 * <p>So the answer is made visual and immediate: whatever trace is under the crosshair, its
 * entire connected network is drawn bright. A gap shows up as the glow simply stopping, which is
 * the exact place to go and look — and finding the cut is otherwise a search rather than a
 * glance.
 *
 * <p><strong>Computed on the client, and only when the target changes.</strong> A network walk is
 * cheap but not free, and the crosshair moves every frame while what it is pointing at usually
 * does not — so the result is cached against the pixel that produced it and recomputed only when
 * that pixel changes. Bounded, because an unbounded walk over a base-spanning bus would be paid
 * for on the render thread.
 */
public final class WireHighlight {

    /** How far a network is followed for the highlight. Generous, and still bounded. */
    private static final int LIMIT = 4_096;

    /** How far the player can be from a wire for it to answer. */
    private static final double REACH = 6.0;

    private static WirePixel lastTarget;
    private static DyeColor lastColour;
    private static Set<WirePixel> lastNetwork = Set.of();

    /**
     * The pixel the meter settled on this frame, if any.
     *
     * <p>Set by {@code WireReadout} because it does the snapping — the crosshair alone cannot hit
     * a one-pixel target, so what the player is <em>actually</em> reading is a pixel a little way
     * off where they pointed. Lighting anything else would highlight one circuit while reporting
     * another.
     */
    private static WirePixel probed;

    public static void lookingAt(WirePixel pixel) {
        probed = pixel;
    }

    /**
     * The circuit under the crosshair, or empty.
     *
     * <p>Only while holding the coil: lighting up a wall of wiring every time a player happens to
     * look at it would be noise, and this is a question they ask <em>while wiring</em>.
     */
    public static Set<WirePixel> current() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return clear();
        }
        boolean holdingCoil = player.getMainHandItem().getItem() instanceof WireCoilItem
                || player.getOffhandItem().getItem() instanceof WireCoilItem;
        // The goggles light a circuit too: reading a wire and seeing where it goes are the same
        // question, and the meter has already worked out which pixel is meant.
        WirePixel probedNow = probed;
        probed = null;
        if (!holdingCoil && probedNow == null) {
            return clear();
        }
        if (!(player.pick(REACH, minecraft.getDeltaTracker()
                .getGameTimeDeltaPartialTick(false), false) instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return clear();
        }

        WirePixel target = probedNow != null ? probedNow
                : WireCoilItem.pixelAt(minecraft.level, hit);
        Optional<DyeColor> colour = colourAt(minecraft.level, target);
        if (colour.isEmpty()) {
            return clear();
        }
        if (target.equals(lastTarget) && colour.get() == lastColour) {
            return lastNetwork;
        }
        lastTarget = target;
        lastColour = colour.get();
        lastNetwork = walk(minecraft.level, target, colour.get());
        return lastNetwork;
    }

    /**
     * Which colour is actually on this pixel.
     *
     * <p>A surface can carry several, and the one being pointed at is whichever is there — so a
     * bundle of four answers about the one under the crosshair rather than about all of them.
     * Lowest colour first, so the answer is stable when two share a pixel.
     */
    private static Optional<DyeColor> colourAt(Level level, WirePixel pixel) {
        BlockPos cell = new BlockPos(pixel.x(), pixel.y(), pixel.z());
        ChunkAccess chunk = chunkAt(level, cell);
        if (chunk == null) {
            return Optional.empty();
        }
        return chunk.getData(ModAttachments.WIRES.get())
                .allOn(cell, Faces.of(pixel.face())).stream()
                .filter(trace -> trace.has(pixel.u(), pixel.v()))
                .map(WireTrace::colour)
                .min((a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
    }

    /** The same flood fill the server does, over the client's copy of the wire layer. */
    private static Set<WirePixel> walk(Level level, WirePixel start, DyeColor colour) {
        Set<WirePixel> seen = new HashSet<>();
        Deque<WirePixel> frontier = new ArrayDeque<>();
        frontier.add(start);
        seen.add(start);

        while (!frontier.isEmpty() && seen.size() < LIMIT) {
            WirePixel current = frontier.poll();
            for (WirePixel candidate : PixelGeometry.neighbours(current)) {
                if (seen.contains(candidate) || !has(level, candidate, colour)) {
                    continue;
                }
                seen.add(candidate);
                frontier.add(candidate);
            }
        }
        return Collections.unmodifiableSet(seen);
    }

    private static boolean has(Level level, WirePixel pixel, DyeColor colour) {
        BlockPos cell = new BlockPos(pixel.x(), pixel.y(), pixel.z());
        ChunkAccess chunk = chunkAt(level, cell);
        return chunk != null && chunk.getData(ModAttachments.WIRES.get())
                .trace(cell, Faces.of(pixel.face()), colour)
                .filter(trace -> trace.has(pixel.u(), pixel.v()))
                .isPresent();
    }

    private static ChunkAccess chunkAt(Level level, BlockPos cell) {
        return level.getChunk(SectionPos.blockToSectionCoord(cell.getX()),
                SectionPos.blockToSectionCoord(cell.getZ()), ChunkStatus.FULL, false);
    }

    /** Lifted toward white, so a lit circuit keeps its own colour instead of becoming a glow. */
    public static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 110);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 110);
        int b = Math.min(255, (argb & 0xFF) + 110);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * The colour a travelling pip is drawn in, against a wire of this colour.
     *
     * <p><strong>Brightening does not work, and white is the proof.</strong> The animation lit a
     * pip by adding 110 to every channel — which on a white run adds nothing at all, so the
     * charge marched invisibly down the one colour every player wires their first circuit in.
     * Reported exactly that way: <em>"по белому проводу ничего не видно, раньше на нём было"</em>.
     *
     * <p>The choice itself lives in {@code sim/wire/Contrast}, where it can be swept over the
     * whole colour cube rather than over the three colours somebody happened to try — which is
     * the difference between this being fixed and this being fixed for red.
     */
    public static int charge(int argb) {
        return 0xFF000000 | play.xponer.astronima.sim.wire.Contrast.pip(argb & 0xFFFFFF);
    }

    /** Pushed toward black, so wiring that is not the circuit in question recedes. */
    public static int fade(int argb) {
        int r = ((argb >> 16) & 0xFF) / 4;
        int g = ((argb >> 8) & 0xFF) / 4;
        int b = (argb & 0xFF) / 4;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static Set<WirePixel> clear() {
        lastTarget = null;
        lastColour = null;
        lastNetwork = Set.of();
        return lastNetwork;
    }

    private WireHighlight() {}
}
