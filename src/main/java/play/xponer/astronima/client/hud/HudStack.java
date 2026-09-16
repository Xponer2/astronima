package play.xponer.astronima.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the floating readouts go, so they sit under each other instead of on each other.
 *
 * <h2>Why they were fighting</h2>
 * Reported as <em>"when you are wearing the goggles and looking at wires, or if there are wires
 * near a machine, the panel with the data closes"</em>. It did: the wire readout drew, returned
 * <em>I drew</em>, and the block readout stood down. The comment in the code said showing both was
 * noise — but they answer different questions. <em>What is this machine doing</em> and <em>what is
 * this wire carrying</em> are two things a player wants at once when they are stood in front of a
 * machine with a run on it, and the answer to the second was eating the first.
 *
 * <p>So neither suppresses the other. They stack, and the stack knows the screen it is on: it
 * starts under the crosshair, keeps a gap, and <strong>slides up as a whole</strong> if the last
 * panel would fall off the bottom. Nothing is ever pushed off, and nothing lands over the
 * crosshair, which is the one part of the screen a player is actually aiming with.
 *
 * <p>Minecraft-free (rule 25).
 */
public final class HudStack {

    /** Gap between stacked panels. */
    public static final int GAP = 3;

    /** How far under the crosshair the first panel starts. */
    public static final int BELOW_CROSSHAIR = 12;

    /** Room left at the bottom of the screen for the hotbar and its trimmings. */
    public static final int BOTTOM_MARGIN = 48;

    /**
     * The top of each panel, in order, for a stack of these heights.
     *
     * <p>Returned as a list rather than positioned one at a time on purpose: whether the stack has
     * to move up depends on <em>all</em> of it, and a caller placing panels one by one cannot know
     * that until it is too late to move the first one.
     */
    public static List<Integer> tops(int screenHeight, List<Integer> heights) {
        int start = screenHeight / 2 + BELOW_CROSSHAIR;
        int room = screenHeight - BOTTOM_MARGIN - start;

        // Panels that do not fit are not drawn. The first version tried to slide the whole stack
        // up to make room, and a test found the case where that cannot work: on a short screen
        // three panels do not fit below the crosshair at all, so sliding up either covers the
        // crosshair or spills under the hotbar. Both are worse than showing one fewer readout.
        // The last panel is the one dropped, and callers put the least important last.
        List<Integer> tops = new ArrayList<>();
        int y = start;
        for (int height : heights) {
            if (y + height > start + room && !tops.isEmpty()) {
                break;
            }
            tops.add(y);
            y += height + GAP;
        }
        return tops;
    }

    /**
     * How many of these panels there is room for.
     *
     * <p>Separate from {@link #tops} so a caller can decide <em>what</em> to show before it decides
     * where — a panel that is going to be dropped should not have its contents computed.
     */
    public static int roomFor(int screenHeight, List<Integer> heights) {
        return tops(screenHeight, heights).size();
    }

    /** Centred horizontally, and never off either edge on a narrow screen. */
    public static int left(int screenWidth, int width) {
        return Math.max(2, (screenWidth - width) / 2);
    }

    private HudStack() {}
}
