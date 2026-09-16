package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.animation.StyleAnimation;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import play.xponer.astronima.client.codex.CodexBook;
import play.xponer.astronima.client.codex.CodexLoader;
import play.xponer.astronima.client.codex.CodexMarkup;
import play.xponer.astronima.client.codex.CodexPage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The codex, rebuilt on LDLib2 — the science-book counterpart to {@link AtlasUi}'s magic-book
 * look, per the user's own split: engineering interfaces read as instruments, magic interfaces
 * read as magic. Cool blue-on-slate, a real search field, a real scrolling rail, real item slots
 * for recipes — the same content {@code CodexBook}/{@code CodexMarkup} always held, drawn through
 * a framework built for it instead of hand-rolled {@code fill()} calls.
 *
 * <h2>What did not change</h2>
 * {@link CodexBook}, {@link CodexLoader}, {@link CodexMarkup} and {@link CodexPage} are untouched —
 * they are the Minecraft-free parsing layer (rule 25) and this class reads them exactly as
 * the old {@code MachineFrame}-based screen did before it was deleted with this one landing.
 * Only the drawing changed.
 *
 * <h2>The scroller bug, and what it actually was</h2>
 * Both panes were built by assembling one tall {@code UIElement} column by hand and handing that
 * single wrapper to {@code ScrollerView.addScrollViewChild(...)}. {@code ScrollerView}'s own
 * documented usage (and its own {@code getContainerHeight()}, which walks {@code viewContainer}'s
 * <em>direct</em> children and sums their measured extents) expects the opposite: every item is
 * its own call to {@code addScrollViewChild}, with no wrapper at all. Handing it one wrapper meant
 * the scroller's whole "how much content is there" measurement depended on that one element
 * correctly self-reporting its own aggregate height — which is exactly the thing that kept coming
 * back wrong, however it was coaxed. Rebuilt on the documented shape, this stopped being a
 * question.
 *
 * <h2>Named simplifications (rule 8) — not hidden</h2>
 * <ul>
 *   <li><b>Links are not inline-clickable.</b> A link span renders in the link colour inside its
 *   paragraph, but the click target is a row of pills listing every outgoing link at the bottom
 *   of the page instead — reachable, just not positioned exactly where the prose reads it. Inline
 *   click regions inside one wrapped text run are a real LDLib2 feature this pass did not reach.</li>
 *   <li><b>Inline item icons render as bracketed names</b> (<code>[Gas Analyzer]</code>) rather
 *   than a real icon glyph mid-sentence, for the same reason.</li>
 *   <li><b>No back-history stack yet</b> — following a link replaces the page; there is no "back"
 *   button. The old screen's history list did not port in this pass.</li>
 *   <li><b>No {@code Button}.</b> Three separate, real bugs traced back to it — its own box did
 *   not grow to the height wrapped text needed even with {@code adaptiveHeight} set; its text drew
 *   at full natural width regardless of {@code maxWidth}, bleeding into the next sibling; and
 *   nothing about the width or height it reported back could be trusted for a parent measuring
 *   it. Every clickable row here is a plain {@link UIElement} holding a {@link Label} instead —
 *   the same combination every paragraph on the page already used correctly — with hover and
 *   click wired by hand through {@code MOUSE_ENTER}/{@code MOUSE_LEAVE}/{@code CLICK} events.</li>
 * </ul>
 *
 * <h2>Split across five files (design/codex-rework.md §9 / C7)</h2>
 * This file is now just the entry point and the whole-screen orchestration: {@link #screen},
 * {@link Session}, {@link #navigate}. {@link CodexStyle} holds the LSS. {@link CodexNav} draws
 * the rail and the status strip. {@link CodexBlocks} draws a page's own content, one method per
 * {@code CodexMarkup.Block} kind. {@link CodexLinkPreview} is one block kind's hover animation,
 * on its own because it earned it. Was one 1030-line file before the split.
 */
public final class CodexUi {

    private static final int RAIL_WIDTH = 190;

    /** What a single open of the book is doing right now — rebuilt in place on navigation rather
     *  than reopening the screen, so the rail's scroll position survives following a link. Holds
     *  the two {@code ScrollerView}s directly: items are added straight to them (rule 8's own
     *  header explains why), so there is no wrapper element to keep a reference to instead.
     *  Package-private, not private: {@link CodexNav}, {@link CodexBlocks} and
     *  {@link CodexLinkPreview} all read and mutate one, and a session is exactly as much "this
     *  class's own business" as any of theirs. */
    static final class Session {
        final CodexBook book;
        final ScrollerView contentScroll;
        final ScrollerView railScroll;
        String current;
        // Set once, right after ModularUI itself is built - StyleAnimation needs a real
        // ModularUI to animate against, and that does not exist until the whole element tree
        // below it does, which is well after this Session is first created.
        ModularUI modularUI;
        // A calculator panel's inputs, remembered per page for this session only (parent design
        // codex-calculator.md §7) - client-side, never persisted. Keyed pageId+"#"+calcId rather
        // than just the calc id, since the same calculator can sit on more than one page and each
        // page's "what were you last asking" is its own question.
        final Map<String, List<Double>> calcInputs = new HashMap<>();

        Session(CodexBook book, ScrollerView contentScroll, ScrollerView railScroll, String current) {
            this.book = book;
            this.contentScroll = contentScroll;
            this.railScroll = railScroll;
            this.current = current;
        }
    }

    public static ModularUIScreen screen(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        CodexBook book = CodexLoader.book();

        UIElement root = new UIElement().addClass("codex_root");
        root.layout(l -> l.flexDirection(FlexDirection.ROW)
                .justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER)
                .alignItems(AlignItems.CENTER));

        UIElement bookFrame = new UIElement().addClass("codex_book");
        // SCISSOR clip on the book: without it a child that is still sized or positioned wrong
        // (long text, an oversized figure) bleeds past its own box instead of being cut at it -
        // visible, but contained, rather than spilling across the interface.
        bookFrame.style(s -> s.opacity(0f).clip(com.lowdragmc.lowdraglib2.gui.ui.data.Clip.SCISSOR));
        bookFrame.layout(l -> l
                // A fixed 560x340 overflowed any window smaller than that and left dead space
                // on any window bigger, since the root now truly fills the screen
                // (DynamicSizeProvider, the same fix AtlasUi needed) and nothing here tracked
                // it. A percentage of that real size does, capped so a huge monitor does not
                // get a book stretched wider than it is comfortable to read.
                .widthPercent(90).maxWidth(760)
                .heightPercent(88).maxHeight(460)
                // COLUMN, not ROW - the status strip (design/codex-rework.md §8: "a terminal
                // aboard the station, not a document about it") sits above and below the rail+
                // content row as its own two bars, so the book's own top-level direction has to
                // stack those three, and the rail/content split moves one level down into its
                // own ROW child instead of living directly on bookFrame.
                .flexDirection(FlexDirection.COLUMN));

        UIElement contentRow = new UIElement();
        contentRow.layout(l -> l.widthPercent(100).flex(1).flexDirection(FlexDirection.ROW));

        UIElement rail = new UIElement().addClass("codex_rail");
        rail.layout(l -> l.width(RAIL_WIDTH).heightPercent(100).flexDirection(FlexDirection.COLUMN));

        String openId = book.isEmpty() ? null
                : (book.first() == null ? null : book.first().id());

        ScrollerView contentScroll = new ScrollerView();
        // flex(1), not heightStretch() - the documented ScrollerView example
        // (LDLib2's own HistoryView) sizes its scroller this exact way, and heightStretch()
        // is a different property that does not carry the same guarantee of a real resolved
        // size. bookFrame is ROW-direction, so the scroller's main axis is width: flex(1)
        // takes the row's remaining width after the rail's fixed RAIL_WIDTH, heightPercent(100)
        // covers the cross axis.
        contentScroll.layout(l -> l.flex(1).heightPercent(100));
        scrollable(contentScroll);
        contentScroll.viewContainer.layout(l -> l.paddingAll(12).gapAll(5));

        ScrollerView railScroll = new ScrollerView();
        // widthPercent(100).flex(1) - this exact pair, in this exact order, is what LDLib2's
        // own HistoryView uses for a ScrollerView inside a COLUMN parent (rail is COLUMN too):
        // width is the cross axis here, flex(1) takes the column's remaining height after
        // search's own height. heightStretch() was not that, and the scroller's own size never
        // reliably resolving is consistent with the bar not showing at all - updateScrollers()
        // bails out early whenever its own measured size is not yet positive.
        railScroll.layout(l -> l.widthPercent(100).flex(1));
        scrollable(railScroll);
        railScroll.viewContainer.layout(l -> l.gapAll(2));

        Session session = new Session(book, contentScroll, railScroll, openId);

        TextField search = new TextField();
        search.addClass("codex_search");
        search.layout(l -> l.widthPercent(100));
        // Filters as the player types - KEY_UP fires after the field's own value has already
        // updated for the keystroke that triggered it, so the filter reads what is actually on
        // screen. The separate "search" button this replaced was redundant with this and is gone.
        search.addEventListener(UIEvents.KEY_UP, event -> CodexNav.rebuildRail(session, search.getValue()));

        rail.addChildren(search, railScroll);

        CodexNav.fillRail(session, "");
        if (openId != null) {
            CodexBlocks.renderPage(session, openId);
        } else {
            contentScroll.addScrollViewChild(new Label().setValue(Component.translatable("astronima.codex.empty")));
        }

        contentRow.addChildren(rail, contentScroll);
        bookFrame.addChildren(CodexNav.statusHeader(player), contentRow, CodexNav.statusFooter(player));
        root.addChild(bookFrame);

        UI ui = UI.of(root, List.of(Stylesheet.parse(CodexStyle.STYLE)), size -> size);
        ModularUI modularUI = ModularUI.of(ui, player).shouldCloseOnEsc(true);
        session.modularUI = modularUI;

        // The book opens, not appears - a fade needs a ModularUI to animate against, which
        // does not exist until the element tree above it is already built, so this is the
        // earliest point it can start.
        StyleAnimation.of(modularUI)
                .select(bookFrame)
                .style(PropertyRegistry.OPACITY, 1.0f)
                .duration(0.25f)
                .start();

        return new ModularUIScreen(modularUI, Component.translatable("astronima.codex.title"));
    }

    private static void scrollable(ScrollerView scroller) {
        scroller.getScrollerViewStyle()
                .mode(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode.VERTICAL)
                .verticalScrollDisplay(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay.AUTO);
        // ScrollerView's own default viewport carries a border-sprite background (its own
        // source sets one) - fine for its own editor UI, a mismatch against this book's plain
        // dark panels, so it is cleared the same way LDLib2's own HistoryView clears it.
        scroller.viewPort.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
    }

    /** Package-private: {@link CodexNav#fillRail} and {@link CodexBlocks#renderPage} both wire a
     *  clickable row's {@code onClick} straight to this. */
    static void navigate(Session session, String pageId) {
        CodexPage page = session.book.page(pageId);
        if (page == null || session.modularUI == null) {
            return;
        }
        session.current = pageId;
        // A flat instant swap read as the page slamming shut and a new one slamming open - a
        // quick fade out, the real rebuild while nothing is visible to see it happen, then a
        // fade in. Short on both ends on purpose: this is a reference book turning a page, not
        // a cutscene between them.
        StyleAnimation.of(session.modularUI)
                .select(session.contentScroll)
                .style(PropertyRegistry.OPACITY, 0.0f)
                .duration(0.08f)
                .onFinished(el -> {
                    CodexBlocks.renderPage(session, pageId);
                    CodexNav.rebuildRail(session, "");
                    StyleAnimation.of(session.modularUI)
                            .select(session.contentScroll)
                            .style(PropertyRegistry.OPACITY, 1.0f)
                            .duration(0.12f)
                            .start();
                })
                .start();
    }

    private CodexUi() {}
}
