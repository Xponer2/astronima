package play.xponer.astronima.client.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides where every floating readout stands, from what each one is worth and what each place
 * costs it.
 *
 * <h2>Panels bid for places instead of fighting for them</h2>
 * Before this, each readout positioned itself with literals, and the two that collided settled it
 * by <strong>one of them refusing to draw</strong> — which is why looking at a machine with a wire
 * on it lost the machine's data. Suppressing a readout is not a layout decision; it is the absence
 * of one.
 *
 * <p>Here a panel declares what each place is worth to it and how much it matters. The rule is
 * short: take the cheapest free place; if the cheapest is held by something less important, move
 * that one and take it. The displaced panel obeys the same rule, so the goggles slide to their
 * second choice rather than vanishing, and both stay readable.
 *
 * <p><strong>Cost and priority are different and both are needed.</strong> Priority alone gives
 * "the important one wins and the other disappears", which is what already happens. Cost alone
 * gives "everybody takes their favourite" and they land on each other.
 *
 * <p>Minecraft-free (rule 25). See {@code design/hud-module.md}.
 */
public final class HudLayout {

    /**
     * A readout that wants to be on screen.
     *
     * @param id       what it is, for a caller to match the answer back to
     * @param priority higher wins a contested place
     * @param costs    what each place is worth to this panel — lower is better. A place left out
     *                 is one this panel refuses to stand in, which is a real thing to want: a
     *                 hazard alarm belongs where the eye is or nowhere.
     */
    public record Panel(String id, int width, int height, int priority,
                        Map<HudPlace, Integer> costs) {

        public int costOf(HudPlace place) {
            return costs.getOrDefault(place, REFUSED);
        }

        public boolean willStandIn(HudPlace place) {
            return costs.containsKey(place);
        }
    }

    /** The cost of a place a panel has not offered to stand in. */
    public static final int REFUSED = Integer.MAX_VALUE;

    /** Where one panel ended up. */
    public record Placement(HudPlace place, HudPlace.Rect rect, int page) {}

    /**
     * Places every panel.
     *
     * <p>Deterministic: the same panels on the same screen always give the same answer. A layout
     * that could pick either of two equally good arrangements would flicker between them, which is
     * the single most distracting thing a HUD can do.
     */
    public static Map<String, Placement> place(int screenWidth, int screenHeight,
                                               List<Panel> panels) {
        return place(screenWidth, screenHeight, panels, List.of());
    }

    /**
     * Places every panel around space that is already spoken for.
     *
     * <p>Reservations are rectangles held by things that do not take part in the bidding: this
     * mod's own fixed furniture — the oxygen gauge, the suit strip, the analyzer — and
     * <strong>another mod's overlay</strong>. Jade is the obvious one, and the honest question is
     * not "is Jade installed" but "is Jade on screen right now": with it showing, the space is
     * taken; with nothing under the crosshair, it is free again this frame.
     *
     * <p>Without this the module is confidently correct about a screen it can only see half of.
     */
    public static Map<String, Placement> place(int screenWidth, int screenHeight,
                                               List<Panel> panels,
                                               List<HudPlace.Rect> reserved) {
        List<Panel> byImportance = new ArrayList<>(panels);
        // Priority first, then id, so ties never depend on the order a caller happened to build
        // the list in.
        byImportance.sort(Comparator.comparingInt(Panel::priority).reversed()
                .thenComparing(Panel::id));

        Map<String, Placement> placed = new LinkedHashMap<>();
        List<Panel> waiting = byImportance;

        // Solved page by page. What will not fit on this one is not dropped and not left without
        // coordinates - it is laid out again, from scratch, on the next page. A mutation found
        // that the first version gave a paged panel no rectangle at all, which made page two
        // permanently empty: the readout was recorded as "elsewhere" and there was nowhere for
        // the player to flip to.
        for (int page = 0; page < MAX_PAGES && !waiting.isEmpty(); page++) {
            Map<String, Placement> thisPage = new LinkedHashMap<>();
            Map<HudPlace, String> holder = new LinkedHashMap<>();
            List<Panel> overflow = new ArrayList<>();

            for (Panel panel : waiting) {
                if (!seat(screenWidth, screenHeight, panel, thisPage, holder, reserved)) {
                    overflow.add(panel);
                }
            }
            if (overflow.size() == waiting.size()) {
                // Nothing fitted at all, so another page would be identical. These are panels
                // bigger than the window; a further page would loop forever.
                for (Panel panel : overflow) {
                    placed.put(panel.id(), new Placement(null, null, page));
                }
                return placed;
            }
            int finalPage = page;
            thisPage.forEach((id, seat) ->
                    placed.put(id, new Placement(seat.place(), seat.rect(), finalPage)));
            waiting = overflow;
        }
        return placed;
    }

    /** How many pages the player can ever be asked to flip through. */
    public static final int MAX_PAGES = 4;

    /**
     * Seats one panel in the cheapest place that is genuinely free.
     *
     * <p><strong>There is no displacement step, and there was.</strong> The first version moved a
     * less important panel out of a contested place — and a mutation proved that code could never
     * run, because panels are seated in priority order, so the place a more important panel wants
     * is never already held by a lesser one. Two mutations then masked each other: removing the
     * displacement changed nothing, and reversing the priority sort <em>also</em> changed nothing,
     * because the displacement covered for it.
     *
     * <p>Dead code that makes another line untestable is worse than dead code. The behaviour the
     * design asked for — the goggles move aside rather than vanishing — falls out of seating the
     * machine panel first and letting the goggles take their second choice.
     */
    private static boolean seat(int screenWidth, int screenHeight, Panel panel,
                                Map<String, Placement> placed, Map<HudPlace, String> holder,
                                List<HudPlace.Rect> reserved) {
        for (HudPlace place : cheapestFirst(panel)) {
            HudPlace.Rect rect = place.resolve(screenWidth, screenHeight,
                    panel.width(), panel.height());
            if (!rect.isInside(screenWidth, screenHeight)) {
                continue;
            }
            // Free by name AND clear of every rectangle already down. Two different places can
            // still overlap - a wide panel under the crosshair reaches past it to both sides -
            // and a name check alone would let them land on each other.
            if (holder.containsKey(place) || collides(rect, placed) || hits(rect, reserved)) {
                continue;
            }
            holder.put(place, panel.id());
            placed.put(panel.id(), new Placement(place, rect, 0));
            return true;
        }
        return false;
    }

    private static List<HudPlace> cheapestFirst(Panel panel) {
        List<HudPlace> places = new ArrayList<>();
        for (HudPlace place : HudPlace.values()) {
            if (panel.willStandIn(place)) {
                places.add(place);
            }
        }
        places.sort(Comparator.comparingInt(panel::costOf).thenComparing(Enum::name));
        return places;
    }

    private static boolean hits(HudPlace.Rect rect, List<HudPlace.Rect> reserved) {
        for (HudPlace.Rect taken : reserved) {
            if (rect.overlaps(taken)) {
                return true;
            }
        }
        return false;
    }

    private static boolean collides(HudPlace.Rect rect, Map<String, Placement> placed) {
        for (Placement other : placed.values()) {
            if (other.rect() != null && rect.overlaps(other.rect())) {
                return true;
            }
        }
        return false;
    }

    /** Whether anything ended up on a page the player is not looking at. */
    public static boolean hasOverflow(Map<String, Placement> placed) {
        for (Placement placement : placed.values()) {
            if (placement.page() > 0) {
                return true;
            }
        }
        return false;
    }

    private HudLayout() {}
}
