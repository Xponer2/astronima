package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.codex.CodexBook;
import play.xponer.astronima.client.codex.CodexPage;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The rail (search + section list) and the status strip (header/footer). Split out of
 * {@code CodexUi} (design/codex-rework.md §9 / C7) — everything here is "the book's own frame",
 * as distinct from {@link CodexBlocks}, which draws what is written on a given page.
 */
final class CodexNav {

    static void rebuildRail(CodexUi.Session session, String query) {
        session.railScroll.clearAllScrollViewChildren();
        fillRail(session, query == null ? "" : query);
    }

    static void fillRail(CodexUi.Session session, String query) {
        List<CodexPage> matches = query.isBlank() ? null : session.book.search(query);
        for (CodexBook.Section section : session.book.sections()) {
            boolean any = false;
            for (CodexPage page : section.pages()) {
                if (matches != null && !matches.contains(page)) {
                    continue;
                }
                if (!any) {
                    Label label = new Label().setValue(Component.literal(section.name().toUpperCase(Locale.ROOT)));
                    label.addClass("codex_section");
                    label.layout(l -> l.widthPercent(100));
                    session.railScroll.addScrollViewChild(label);
                    any = true;
                }
                boolean active = page.id().equals(session.current);
                UIElement entry = CodexBlocks.clickableRow(page.title(), active ? 0xFF6FB0EE : 0xFFC9D2DA,
                        page.icon(), active ? "codex_page_btn_active" : "codex_page_btn", "codex_page_btn_hover",
                        () -> CodexUi.navigate(session, page.id()));
                entry.layout(l -> l.widthPercent(100).heightAuto().marginLeft(page.isChild() ? 8 : 0));
                session.railScroll.addScrollViewChild(entry);
            }
        }
    }

    /**
     * design/codex-rework.md §8 — "a terminal aboard the station, not a document about it".
     * Two thin bars, published state only (rules 25, 26: never reaches into a block entity -
     * {@code player}/{@code player.level()} are the client's own already-synced copies), driven
     * live off {@code UIEvents.TICK} rather than a snapshot taken once at open. Deliberately
     * missing a "sector" reading the original spec asked for: nothing in this mod publishes a
     * sector today, and rule 3 of that same section - "no reading, no zero" - reads as "a value
     * that can be unavailable says so", not "invent the concept so the strip has one more field".
     */
    static UIElement statusHeader(Player player) {
        UIElement strip = new UIElement().addClass("codex_strip");
        strip.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).paddingAll(4).gapAll(8));

        Label title = new Label().setValue(Component.translatable("astronima.codex.title"));
        title.addClass("codex_dim");
        title.textStyle(style -> style.fontSize(6));
        strip.addChild(title);

        strip.addChild(spacer());
        strip.addChild(liveReadout(() -> positionReading(player)));
        return strip;
    }

    static UIElement statusFooter(Player player) {
        UIElement strip = new UIElement().addClass("codex_strip");
        strip.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).paddingAll(4).gapAll(8));

        Label version = new Label().setValue(Component.literal(bookVersion()));
        version.addClass("codex_dim");
        version.textStyle(style -> style.fontSize(6));
        strip.addChild(version);

        strip.addChild(spacer());
        strip.addChild(liveReadout(() -> timeReading(player)));
        return strip;
    }

    /** A label that redraws its own text from {@code reading} every screen tick. */
    private static Label liveReadout(Supplier<String> reading) {
        Label label = new Label().setValue(Component.literal(reading.get()));
        label.addClass("codex_dim");
        label.textStyle(style -> style.fontSize(6));
        label.addEventListener(UIEvents.TICK, event -> label.setValue(Component.literal(reading.get())));
        return label;
    }

    static UIElement spacer() {
        UIElement spacer = new UIElement();
        spacer.layout(l -> l.flex(1).height(1));
        return spacer;
    }

    private static String positionReading(Player player) {
        var pos = player.blockPosition();
        String dimension = player.level().dimension().identifier().getPath().toUpperCase(Locale.ROOT);
        return dimension + "  X " + pos.getX() + " Y " + pos.getY() + " Z " + pos.getZ();
    }

    private static String timeReading(Player player) {
        // getDefaultClockTime(), not getDayTime() (removed in this MC line) or
        // getOverworldClockTime() (hardcoded to the overworld's own clock regardless of which
        // dimension is actually asking) - dimensionType().defaultClock() is this level's own
        // clock, confirmed against the decompiled 26.1 source (Level.java) rather than assumed
        // from older-version memory. WorldClock itself carries no per-clock day length (an empty
        // record, just a registry identity), so the standard 24000-tick day still applies to the
        // tick count it returns.
        long dayTime = player.level().getDefaultClockTime();
        long day = dayTime / 24000L + 1;
        long timeOfDay = dayTime % 24000L;
        long hour = (timeOfDay / 1000 + 6) % 24;
        long minute = timeOfDay % 1000 * 60 / 1000;
        return String.format(Locale.ROOT, "DAY %d  %02d:%02d", day, hour, minute);
    }

    private static String bookVersion() {
        return "ASTRONIMA CODEX " + ModList.get().getModContainerById(Astronima.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("?");
    }

    private CodexNav() {}
}
