package play.xponer.astronima.client.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The live side of the HUD module: what wants to be on screen right now, and where it has got to.
 *
 * <h2>How a readout uses it</h2>
 * A panel measures itself, asks for a place, and draws where it is told:
 *
 * <pre>
 *   var seat = HudPanels.place("goggles", width, height, 10, costs, screenW, screenH, now);
 *   if (seat == null) return;              // it is on another page this frame
 *   draw(seat.x(), seat.y(), seat.fade()); // fade is 0..1 while it is arriving
 * </pre>
 *
 * <p><strong>The layout is solved from what was on screen last frame.</strong> A readout knows its
 * size only once it has decided what to say, so a strictly correct solver would need every panel
 * measured before any is drawn — two passes through code that is otherwise a straight line. One
 * frame of latency on a panel that fades in over eight frames is invisible, and it keeps every
 * readout a single readable method.
 *
 * <p>Panels that stop asking are forgotten, which is what makes a panel leave: it stops calling,
 * its motion runs out, and it is gone.
 */
public final class HudPanels {

    private record Live(HudLayout.Panel panel, double lastAsked, HudPlace.Rect from,
                        HudPlace.Rect at, double since) {}

    /**
     * How long a panel may go without asking before it is forgotten.
     *
     * <p>In seconds, not frames. The first version counted frames at an assumed sixty a second, so
     * on a machine running at twenty a panel asking every single frame was three "frames" late and
     * got forgotten and re-added continuously — which re-solved the layout every frame and left it
     * permanently part-way through arriving. A HUD must not depend on the frame rate.
     */
    private static final double FORGET_AFTER_SECONDS = 0.15;

    private static final Map<String, Live> LIVE = new LinkedHashMap<>();

    /** Space held by things that do not bid: our own fixed furniture, and other mods' overlays. */
    private record Held(HudPlace.Rect rect, double lastAsked) {}

    private static final Map<String, Held> RESERVED = new LinkedHashMap<>();
    private static Map<String, HudLayout.Placement> solved = Map.of();
    private static int page;
    private static int lastWidth;
    private static int lastHeight;

    /**
     * Where a panel is this frame, or null when it has nowhere on this page.
     *
     * <p>Carries the <em>place</em> as well as the pixels, and that is not decoration. Three tests
     * in a row here passed for the wrong reason by comparing two seats' coordinates: they differ
     * by the lift a panel carries while it is still arriving, so an assertion that a panel "moved"
     * held even when it had not moved at all. Comparing places cannot go wrong that way.
     */
    public record Seat(HudPlace place, int x, int y, double fade) {}

    /**
     * Claims space for something that is not a module panel.
     *
     * <p>Called every frame by whatever is holding it, so a gauge that stops drawing stops holding
     * — the same rule the panels themselves obey. That matters most for a foreign overlay: Jade
     * takes the space it is using <em>while it is showing</em>, and gives it back the moment the
     * player looks at nothing.
     */
    public static void reserve(String id, int x, int y, int width, int height, double now) {
        HudPlace.Rect rect = new HudPlace.Rect(x, y, width, height);
        Held before = RESERVED.get(id);
        RESERVED.put(id, new Held(rect, now));
        if (before == null || !before.rect().equals(rect)) {
            solved = Map.of(); // the world changed shape; the next ask re-solves
        }
    }

    /**
     * Asks for a place, and answers where to draw.
     *
     * @param now seconds since the game started, for the arrival curve
     */
    public static Seat place(String id, int width, int height, int priority,
                             Map<HudPlace, Integer> costs, int screenWidth, int screenHeight,
                             double now) {
        boolean sizeChanged = false;
        Live before = LIVE.get(id);
        if (before == null || before.panel().width() != width
                || before.panel().height() != height) {
            sizeChanged = true;
        }
        LIVE.put(id, new Live(new HudLayout.Panel(id, width, height, priority, costs), now,
                before == null ? null : before.from(),
                before == null ? null : before.at(),
                before == null ? now : before.since()));

        forgetPanelsThatStoppedAsking(now);
        forgetSpaceNobodyIsHoldingAnyMore(now);
        if (sizeChanged || screenWidth != lastWidth || screenHeight != lastHeight
                || !solved.keySet().equals(LIVE.keySet())) {
            resolve(screenWidth, screenHeight, now);
        }

        HudLayout.Placement placement = solved.get(id);
        if (placement == null || placement.page() != page || placement.rect() == null) {
            return null;
        }
        Live live = LIVE.get(id);
        double progress = Reveal.progress(now - live.since(), false);
        HudPlace.Rect target = placement.rect();
        HudPlace.Rect from = live.from() == null ? target : live.from();

        // Slides from wherever it was to wherever it is going. A panel that is merely moving does
        // not fade out and back in - restarting it is what made looking from one machine to the
        // next flicker.
        int x = (int) Math.round(from.x() + (target.x() - from.x()) * progress);
        int y = (int) Math.round(from.y() + (target.y() - from.y()) * progress)
                + Reveal.offset(progress);
        return new Seat(placement.place(), x, y, live.from() == null ? progress : 1.0);
    }

    private static void resolve(int screenWidth, int screenHeight, double now) {
        List<HudLayout.Panel> wanted = new ArrayList<>();
        LIVE.values().forEach(live -> wanted.add(live.panel()));
        List<HudPlace.Rect> taken = new ArrayList<>();
        RESERVED.values().forEach(held -> taken.add(held.rect()));
        Map<String, HudLayout.Placement> now_ =
                HudLayout.place(screenWidth, screenHeight, wanted, taken);

        LIVE.replaceAll((id, live) -> {
            HudPlace.Rect target = now_.get(id) == null ? null : now_.get(id).rect();
            HudPlace.Rect was = live.at();
            boolean moved = was != null && target != null && !was.equals(target);
            return new Live(live.panel(), live.lastAsked(),
                    moved ? was : live.from(), target,
                    moved || live.at() == null ? now : live.since());
        });
        solved = now_;
        lastWidth = screenWidth;
        lastHeight = screenHeight;
    }

    private static void forgetPanelsThatStoppedAsking(double now) {
        LIVE.entrySet().removeIf(entry -> now - entry.getValue().lastAsked() > FORGET_AFTER_SECONDS);
    }

    private static void forgetSpaceNobodyIsHoldingAnyMore(double now) {
        boolean gone = RESERVED.entrySet().removeIf(
                entry -> now - entry.getValue().lastAsked() > FORGET_AFTER_SECONDS);
        if (gone) {
            solved = Map.of();
        }
    }

    /** For tests: no panel is showing and nothing is remembered. */
    public static void forgetEverything() {
        RESERVED.clear();
        LIVE.clear();
        solved = Map.of();
        page = 0;
        lastWidth = 0;
        lastHeight = 0;
    }

    /** Whether anything is waiting on a page the player is not looking at. */
    public static boolean hasOtherPages() {
        return HudLayout.hasOverflow(solved);
    }

    /** Flips to the next page, wrapping. The player is told there is one to flip to. */
    public static void nextPage() {
        int highest = 0;
        for (HudLayout.Placement placement : solved.values()) {
            highest = Math.max(highest, placement.page());
        }
        page = highest == 0 ? 0 : (page + 1) % (highest + 1);
    }

    public static int page() {
        return page;
    }

    /** How many pages there are, so the hint can say "2 of 3" rather than just "more". */
    public static int pageCount() {
        int highest = 0;
        for (HudLayout.Placement placement : solved.values()) {
            highest = Math.max(highest, placement.page());
        }
        return highest + 1;
    }

    private HudPanels() {}
}
