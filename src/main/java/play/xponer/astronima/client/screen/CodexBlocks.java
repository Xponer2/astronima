package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Slider;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.values.TextureValue;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.WireElement;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.progression.Unlocks;
import org.joml.Vector2f;
import play.xponer.astronima.client.codex.CodexBook;
import play.xponer.astronima.client.codex.CodexMarkup;
import play.xponer.astronima.client.codex.CodexPage;
import play.xponer.astronima.crafting.CraftingTree;
import play.xponer.astronima.sim.codex.Calculator;
import play.xponer.astronima.sim.codex.Calculators;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.DoubleConsumer;

/**
 * One page's worth of drawing: the block dispatch, one method per {@link CodexMarkup.Block} kind,
 * and the small shared widgets (clickable rows, item slots) they all lean on. Split out of
 * {@code CodexUi} (design/codex-rework.md §9 / C7) — this is "what a page's content looks like",
 * as distinct from {@link CodexNav} (the book's own frame) and {@link CodexLinkPreview} (one
 * block kind's hover animation, big enough to earn its own file).
 *
 * <p>Rule 29 applies to {@link #renderPage}'s block switch: exhaustive over the sealed {@code
 * Block} interface, so a new block kind that nobody drew fails to compile rather than silently
 * vanishing.
 */
final class CodexBlocks {

    // Fixed pill width (see clickableRow's related-link callers) is 100; three of them plus
    // gaps fits comfortably inside the narrowest content column the book ever resolves to
    // (CodexUi.RAIL_WIDTH already claims the left third), without needing to read a live
    // measured width back out of Taffy just to decide how many belong on one row.
    private static final int PILLS_PER_ROW = 3;

    static void renderPage(CodexUi.Session session, String pageId) {
        CodexPage page = session.book.page(pageId);
        ScrollerView contentScroll = session.contentScroll;
        contentScroll.clearAllScrollViewChildren();
        if (page == null) {
            return;
        }

        Label title = new Label().setValue(Component.literal(page.title()));
        title.addClass("codex_title");
        wrapped(title);
        contentScroll.addScrollViewChild(title);

        for (CodexMarkup.Block block : page.blocks()) {
            UIElement drawn = renderBlock(session, pageId, block);
            if (drawn != null) {
                contentScroll.addScrollViewChild(drawn);
            }
        }

        List<String> links = page.links();
        if (!links.isEmpty()) {
            List<UIElement> pills = new ArrayList<>();
            for (String target : links) {
                CodexPage targetPage = session.book.page(target);
                if (targetPage == null) {
                    continue;
                }
                String shortTitle = shortened(targetPage.title(), 16);
                UIElement pill = clickableRow(shortTitle, 0xFF6FB0EE,
                        "codex_related_btn", "codex_related_btn_hover",
                        () -> CodexUi.navigate(session, target));
                // A real width, not just a maxWidth cap - a cap alone gives the layout no
                // target to wrap the label's text against, and the pill collapsed to its
                // narrowest possible content (one character per line, stretched tall) instead
                // of the compact single- or two-line pill a fixed width actually produces.
                pill.layout(l -> l.width(100));
                CodexLinkPreview.wireExpandPreview(session, pill, shortTitle, targetPage.title());
                pills.add(pill);
            }
            // Chunked into fixed-size rows in Java rather than left to FlexWrap.WRAP on one
            // row holding every pill: that row was a single direct child of contentScroll, and
            // ScrollerView's own container sizing (getContainerWidth/Height, walking
            // viewContainer's direct children) measures each direct child's natural size before
            // the book's actual width is known to wrap against - the row never wrapped, it just
            // reported its full unwrapped width and got clipped at the book's edge. Fixed rows
            // of PILLS_PER_ROW sidestep needing Taffy to wrap inside that measurement pass at
            // all: each row is its own direct child with a bounded, known set of pills.
            int perRow = PILLS_PER_ROW;
            for (int i = 0; i < pills.size(); i += perRow) {
                UIElement row = new UIElement();
                row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4));
                for (UIElement pill : pills.subList(i, Math.min(i + perRow, pills.size()))) {
                    row.addChild(pill);
                }
                contentScroll.addScrollViewChild(row);
            }
        }
    }

    /**
     * One block, dispatched. Pulled out of {@link #renderPage}'s loop so {@link #locked} can
     * draw an unlocked {@code Locked} block's real inner content through the exact same path a
     * top-level page uses, rather than a second copy of the switch.
     *
     * <p>Exhaustive over the sealed {@code Block} interface (rule 29's own comment on this
     * class): a new block kind that nobody drew fails to compile here rather than vanishing.
     */
    private static UIElement renderBlock(CodexUi.Session session, String pageId, CodexMarkup.Block block) {
        return switch (block) {
            case CodexMarkup.Block.Heading heading -> heading(heading);
            case CodexMarkup.Block.Paragraph paragraph -> paragraph(session.book, paragraph.spans(), "codex_body");
            case CodexMarkup.Block.Bullet bullet -> bullet(session.book, bullet);
            case CodexMarkup.Block.Callout callout -> callout(session.book, callout);
            case CodexMarkup.Block.Rule ignored -> rule();
            case CodexMarkup.Block.Image image -> image(image.figure());
            case CodexMarkup.Block.Recipe recipe -> recipe(recipe.item());
            case CodexMarkup.Block.Structure structure -> structure(structure.id());
            case CodexMarkup.Block.Table table -> table(session.book, table);
            case CodexMarkup.Block.Calc calc -> calc(session, pageId, calc.id());
            case CodexMarkup.Block.Locked locked -> locked(session, pageId, locked);
        };
    }

    /**
     * A run of blocks the reader has not earned yet — design/codex-disclosure.md §5.
     *
     * <p><strong>Text blocks are fully live.</strong> Heading, paragraph, bullet and callout
     * each get a {@code UIEvents.TICK} listener that re-checks {@link Unlocks#has} every tick
     * and swaps the label's {@link Component} between the real text and a
     * {@link ChatFormatting#OBFUSCATED} version built <em>from</em> that same text — so the
     * jitter is exactly as long as what it hides, and earning the id resolves it without
     * reopening the page. The same live-state pattern {@code CodexNav#liveReadout} already
     * uses, not a new one.
     *
     * <p><strong>Structural blocks are the one honest simplification here, named rather than
     * hidden.</strong> A table, recipe, calculator or figure that is locked draws as one opaque
     * plate at page-open time rather than rebuilding a whole widget tree every tick to watch for
     * an unlock that almost never fires mid-view — real cost for a transition nobody asked for.
     * The user's own request was specifically about text that visibly changes; this is where
     * that request stops and a simpler, honest cut begins.
     */
    private static UIElement locked(CodexUi.Session session, String pageId, CodexMarkup.Block.Locked locked) {
        UIElement wrapper = new UIElement();
        wrapper.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));
        String unlockId = locked.unlockId();
        boolean unlockedNow = hasUnlock(unlockId);
        for (CodexMarkup.Block inner : locked.inner()) {
            UIElement drawn = switch (inner) {
                case CodexMarkup.Block.Heading heading -> lockedLabel(unlockId, heading.text(), "codex_heading");
                case CodexMarkup.Block.Paragraph paragraph ->
                        lockedLabel(unlockId, spansToComponent(session.book, paragraph.spans()).getString(), "codex_body");
                case CodexMarkup.Block.Bullet bullet ->
                        lockedLabel(unlockId, "- " + spansToComponent(session.book, bullet.spans()).getString(), "codex_bullet");
                case CodexMarkup.Block.Callout callout -> {
                    UIElement box = new UIElement().addClass("codex_callout");
                    box.layout(l -> l.widthPercent(100));
                    box.addChild(lockedLabel(unlockId, spansToComponent(session.book, callout.spans()).getString(), "codex_body"));
                    yield box;
                }
                default -> unlockedNow ? renderBlock(session, pageId, inner) : lockedPlate();
            };
            if (drawn != null) {
                wrapper.addChild(drawn);
            }
        }
        return wrapper;
    }

    /** One opaque plate standing in for a locked table, recipe, calculator or figure. */
    private static UIElement lockedPlate() {
        UIElement plate = new UIElement().addClass("codex_locked_plate");
        plate.layout(l -> l.widthPercent(100).height(48));
        return plate;
    }

    /**
     * A label that reads real or obfuscated from {@code unlockId} every tick — the whole
     * mechanism {@link #locked} needs, once per text kind.
     */
    private static UIElement lockedLabel(String unlockId, String realText, String styleClass) {
        Label label = new Label();
        label.addClass(styleClass);
        wrapped(label);
        label.setValue(displayFor(unlockId, realText));
        label.addEventListener(UIEvents.TICK, event -> label.setValue(displayFor(unlockId, realText)));
        return label;
    }

    private static Component displayFor(String unlockId, String realText) {
        return hasUnlock(unlockId) ? Component.literal(realText)
                : Component.literal(realText).withStyle(ChatFormatting.OBFUSCATED);
    }

    /** Whether the local player has earned this id — {@code Unlocks.NONE} if nobody is in yet. */
    private static boolean hasUnlock(String unlockId) {
        Player player = Minecraft.getInstance().player;
        Unlocks unlocks = player == null ? Unlocks.NONE : player.getData(ModAttachments.UNLOCKS.get());
        return unlocks.has(unlockId);
    }

    private static UIElement heading(CodexMarkup.Block.Heading heading) {
        Label label = new Label().setValue(Component.literal(heading.text()));
        label.addClass("codex_heading");
        wrapped(label);
        return label;
    }

    /** {@code .codex_body}/{@code .codex_bullet}'s own {@code text-color}, restated here because
     *  neither is a color a descendant {@link Label} inherits from its ancestor's CSS class the
     *  way a browser would — this library's styles apply only to the exact element that carries
     *  the class, and {@link #richText} carries that class on a plain, textless flow container,
     *  not on any one of the {@link Label}s actually drawing glyphs inside it. */
    private static final int BODY_TEXT_COLOR = 0xFFC9D2DA;

    private static UIElement paragraph(CodexBook book,
                                       List<CodexMarkup.Span> spans, String styleClass) {
        return richText(book, spans, styleClass, "");
    }

    private static UIElement bullet(CodexBook book,
                                    CodexMarkup.Block.Bullet bullet) {
        return richText(book, bullet.spans(), "codex_bullet", "-");
    }

    /**
     * A wrapped run of text that can carry a real inline item icon, not one big {@link Label}
     * built from a flattened {@link Component}. {@code Label} draws pure glyphs — confirmed
     * against LDLib2's own {@code TextElementRenderer}, which only ever calls {@code
     * LDFonts.drawText} and never touches an item texture — so a real icon needs a real {@link
     * ItemSlot} sitting as its own element, which one {@code Label} cannot host mid-string. Each
     * word becomes its own {@code adaptiveWidth} label and each {@code {item:...}} becomes its
     * own small slot, all packed as siblings into one {@link FlexWrap#WRAP} row; Taffy wraps them
     * exactly like word-wrap would; this file never measures text by hand.
     */
    private static UIElement richText(CodexBook book, List<CodexMarkup.Span> spans, String styleClass,
                                      String prefix) {
        UIElement flow = new UIElement().addClass(styleClass);
        flow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .flexWrap(FlexWrap.WRAP).alignItems(AlignItems.CENTER).gapAll(3));
        if (!prefix.isEmpty()) {
            appendWords(flow, prefix, false, BODY_TEXT_COLOR);
        }
        for (CodexMarkup.Span span : spans) {
            switch (span) {
                case CodexMarkup.Span.Text text -> appendWords(flow, text.value(), text.bold(), BODY_TEXT_COLOR);
                case CodexMarkup.Span.Link link -> appendWords(flow, linkLabel(book, link), false, 0xFF6FB0EE);
                case CodexMarkup.Span.Icon icon -> flow.addChild(inlineIcon(icon.id()));
            }
        }
        return flow;
    }

    /** One word per {@link Label}, sized to its own content — the flow container's own {@code
     *  gapAll} stands in for the space between words, so a leading/trailing blank from splitting
     *  on {@code " "} is skipped rather than drawn as an empty, gap-doubling label. */
    private static void appendWords(UIElement flow, String text, boolean bold, int colorArgb) {
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            Component value = bold
                    ? Component.literal(word).withStyle(ChatFormatting.BOLD)
                    : Component.literal(word);
            Label label = new Label().setValue(value);
            label.textStyle(style -> style.adaptiveWidth(true).adaptiveHeight(true).textColor(colorArgb));
            label.layout(l -> l.heightAuto());
            flow.addChild(label);
        }
    }

    /** A small real item texture sitting inline in a line of text, in place of the bracketed
     *  "[Item name]" placeholder that used to stand in for one. Hovering it still shows the
     *  item's real name and tooltip, the same as every other slot in this book. */
    private static UIElement inlineIcon(String itemId) {
        ItemSlot slot = new ItemSlot();
        slot.setItem(stackOf(itemId));
        slot.layout(l -> l.width(10).height(10));
        return slot;
    }

    /**
     * Every wrapping label in this book needs both of these, not just {@code textWrap} - a
     * label given a fixed one-line height by default, however many lines its wrapped text
     * actually took, is why every block after the first drew at a position computed from a
     * height that was wrong. {@code adaptiveHeight} is what makes the label report back the
     * height its wrapped text really needs, which is what a flex column needs to stack the
     * next sibling below it instead of on top of it.
     */
    static void wrapped(Label label) {
        label.textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true));
        label.layout(l -> l.widthPercent(100).heightAuto());
    }

    /**
     * A clickable row: a plain {@link UIElement} holding a {@link Label}, not a {@code Button} —
     * see {@code CodexUi}'s own header for why. Hover and click are wired by hand:
     * {@code Button}'s base/hover/pressed textures are a property of {@code Button} specifically,
     * not of any element, so this swaps a CSS class on {@code MOUSE_ENTER}/{@code MOUSE_LEAVE}
     * instead of declaring one in LSS.
     */
    static UIElement clickableRow(String text, int textColorArgb, String baseClass,
                                  String hoverClass, Runnable onClick) {
        return clickableRow(text, textColorArgb, "", baseClass, hoverClass, onClick);
    }

    /**
     * As above, with an item drawn before the label when {@code iconId} names one — the rail's
     * own use, where every page already carries an {@code icon:} frontmatter field that nothing
     * drew ({@code CodexPage#icon}, parsed for 64 pages and rendered by none of them until now).
     */
    static UIElement clickableRow(String text, int textColorArgb, String iconId, String baseClass,
                                  String hoverClass, Runnable onClick) {
        UIElement row = new UIElement().addClass(baseClass);
        row.layout(l -> l.flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        List<UIElement> hitTargets = new java.util.ArrayList<>();
        if (!iconId.isBlank()) {
            ItemStack stack = stackOf(iconId);
            if (!stack.isEmpty()) {
                ItemSlot icon = slotOf(stack);
                icon.setAllowHitTest(false);
                row.addChild(icon);
            }
        }
        Label label = new Label().setValue(Component.literal(text));
        wrapped(label);
        // Set directly, not through an LSS selector reaching into the label as a descendant of
        // the row - the table cells' own invisible-text bug was exactly a guess at an internal
        // selector name that did not match anything, and this is the same call that fixed it.
        label.textStyle(style -> style.textColor(textColorArgb).fontSize(7));
        row.addChild(label);
        hitTargets.add(row);
        hitTargets.add(label);
        // On both row and label, not just row: whichever of the two the pointer actually lands
        // on is the one that gets the event - text does not automatically hand mouse-enter/
        // leave up to its own parent. Listening on row alone only caught the padding strip
        // above and below the text, which is exactly "highlights top and bottom but not the
        // middle": the middle is where the label itself, not the row, was the real hit target.
        // The icon itself stays out of hit-testing (allowHitTest(false) above) rather than
        // joining this list, so it decorates the row without becoming a second, smaller click
        // target next to it - the exact hitTest-tests-children-first trap CodexLinkPreview's own
        // preview already had to work around.
        for (UIElement target : hitTargets) {
            target.addEventListener(UIEvents.CLICK, event -> onClick.run());
            target.addEventListener(UIEvents.MOUSE_ENTER, event -> row.addClass(hoverClass));
            target.addEventListener(UIEvents.MOUSE_LEAVE, event -> row.removeClass(hoverClass));
        }
        return row;
    }

    private static UIElement callout(CodexBook book, CodexMarkup.Block.Callout callout) {
        UIElement box = new UIElement().addClass("codex_callout");
        box.layout(l -> l.widthPercent(100));
        box.addChild(paragraph(book, callout.spans(), "codex_body"));
        return box;
    }

    private static UIElement rule() {
        UIElement line = new UIElement().addClass("codex_rule");
        line.layout(l -> l.widthPercent(100));
        return line;
    }

    /**
     * The page's answer to a table that used to be one slab of joined text with the pipes and
     * dashes still in it - real rows and columns, each cell run through the same span parser
     * prose uses so a cell can hold bold text, a link or an inline item like any other line.
     */
    private static UIElement table(CodexBook book, CodexMarkup.Block.Table table) {
        UIElement grid = new UIElement().addClass("codex_table");
        grid.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN));
        grid.addChild(tableRow(book, table.header(), true));
        for (List<List<CodexMarkup.Span>> row : table.rows()) {
            grid.addChild(rule());
            grid.addChild(tableRow(book, row, false));
        }
        return grid;
    }

    private static UIElement tableRow(CodexBook book, List<List<CodexMarkup.Span>> cells, boolean isHeader) {
        UIElement row = new UIElement().addClass(isHeader ? "codex_table_header" : "codex_table_row");
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW));
        for (List<CodexMarkup.Span> cellSpans : cells) {
            Label cell = new Label().setValue(spansToComponent(book, cellSpans));
            cell.textStyle(style -> style
                    .textWrap(TextWrap.WRAP)
                    .adaptiveHeight(true)
                    // 0xAARRGGBB, not 0xRRGGBB - the alpha byte matters here and did not exist
                    // on the first try, which made every table cell's text fully transparent:
                    // the rows, dividers and header background all drew exactly as designed,
                    // and every word of the table's actual content was invisible on top of them.
                    .textColor(isHeader ? 0xFF6FB0EE : 0xFFC9D2DA));
            // flex(1), not widthPercent - every column shares the row equally regardless of how
            // many columns this particular table has, which a fixed percentage would need to
            // know in advance and a table's own author never states.
            cell.layout(l -> l.flex(1).heightAuto());
            row.addChild(cell);
        }
        return row;
    }

    private static UIElement image(CodexMarkup.Figure figure) {
        UIElement box = new UIElement();
        // A fixed pixel width was the actual cause of "the panel abruptly cuts off on the
        // right, only on pages with a diagram": every other block wraps or stretches to the
        // real column width, which changes with the window, but this one never did - on a
        // narrower window (or a wider figure than the old 260 guess) it was simply wider than
        // its own column, and that forced the whole row past the book's edge. widthPercent(100)
        // plus a real aspect ratio makes the figure a citizen of the same flexible column
        // everything else already is, at up to the author's own width and never past it.
        float aspect = figure.width() / (float) Math.max(1, figure.height());
        box.layout(l -> l.widthPercent(100).maxWidth(figure.width()).aspectRatio(aspect));
        Identifier texture = Identifier.tryParse(figure.texture());
        if (texture != null) {
            box.style(s -> s.background(new TextureValue("sprite(" + texture + ")").compute()));
        }
        return box;
    }

    private static UIElement recipe(String itemId) {
        CraftingTree.Shaped shaped = CraftingTree.sources().stream()
                .filter(CraftingTree.Shaped.class::isInstance)
                .map(CraftingTree.Shaped.class::cast)
                .filter(source -> source.result().equals(itemId))
                .findFirst().orElse(null);
        CraftingTree.Shapeless shapeless = shaped != null ? null : CraftingTree.sources().stream()
                .filter(CraftingTree.Shapeless.class::isInstance)
                .map(CraftingTree.Shapeless.class::cast)
                .filter(source -> source.result().equals(itemId))
                .findFirst().orElse(null);
        if (shaped == null && shapeless == null) {
            Label label = new Label().setValue(Component.translatable("astronima.codex.no_recipe"));
            label.addClass("codex_dim");
            return label;
        }

        // No widthPercent(100) here - a recipe is a small, fixed-shape diagram (a 3x3 grid and a
        // result, or an ingredient list and a result), not a banner that ought to fill whatever
        // column width the page happens to have. Sized to its own content, like every recipeNode
        // and gridCell inside it already are.
        RecipeGraph graph = new RecipeGraph();
        graph.addClass("codex_recipe");
        graph.layout(l -> l.flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(8));

        int resultCount = 1;
        if (shaped != null) {
            // The actual pattern, not a bag of icons in key order - orderedKeys() lost which
            // slot each ingredient sat in, so a diagonal or hollow-square recipe read exactly
            // like a solid block of the same ingredients. craftingGrid draws the real rows,
            // columns and blank cells the crafting table itself matches against.
            UIElement grid = craftingGrid(shaped);
            graph.addChild(grid);
            graph.trackIngredient(grid);
            resultCount = shaped.count();
        } else {
            UIElement ingredientColumn = new UIElement();
            ingredientColumn.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(6));
            for (var input : new TreeMap<>(shapeless.inputs()).entrySet()) {
                ItemStack stack = stackOf(input.getKey());
                stack.setCount(Math.max(1, input.getValue()));
                UIElement node = recipeNode(stack, false);
                ingredientColumn.addChild(node);
                graph.trackIngredient(node);
            }
            graph.addChild(ingredientColumn);
        }

        ItemStack made = stackOf(itemId);
        made.setCount(resultCount);
        UIElement result = recipeNode(made, true);
        graph.addChild(result);
        graph.trackResult(result);
        return graph;
    }

    /**
     * The recipe's actual pattern, not just its ingredient set - the same rows, columns and
     * blank cells the crafting table itself matches against, not a bag of icons that could just
     * as well have been arranged wrong. Each {@code Shaped#pattern} row becomes one grid row;
     * a blank in the pattern becomes a real, visibly empty cell rather than a skipped one, so a
     * diagonal or hollow-square recipe still reads as the shape it actually is.
     */
    private static UIElement craftingGrid(CraftingTree.Shaped shaped) {
        UIElement grid = new UIElement().addClass("codex_recipe_grid");
        grid.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(2));
        for (String patternRow : shaped.pattern()) {
            UIElement row = new UIElement();
            row.layout(l -> l.flexDirection(FlexDirection.ROW).gapAll(2));
            for (int i = 0; i < patternRow.length(); i++) {
                char symbol = patternRow.charAt(i);
                row.addChild(gridCell(symbol == ' ' ? null : shaped.keys().get(symbol)));
            }
            grid.addChild(row);
        }
        return grid;
    }

    private static UIElement gridCell(String itemId) {
        UIElement cell = new UIElement().addClass(itemId == null ? "codex_recipe_cell_empty" : "codex_recipe_cell");
        cell.layout(l -> l.width(20).height(20));
        if (itemId != null) {
            cell.addChild(slotOf(stackOf(itemId)));
        }
        return cell;
    }

    /**
     * {@code {structure:id}} - a real multiblock's own footprint, top-down. Only one real
     * structure exists in this mod today ({@code astra_altar}); a second one is a real "add a
     * branch" decision, not a lookup table built ahead of having anything to put in it (rule 8).
     */
    private static UIElement structure(String id) {
        if (!id.equals("astra_altar")) {
            Label label = new Label().setValue(Component.translatable("astronima.codex.no_structure", id));
            label.addClass("codex_dim");
            return label;
        }
        return astraAltarDiagram();
    }

    /**
     * design/astra-ritual-grammar.md §1's vocabulary, drawn top-down: the smallest real figure
     * {@code AstraAltarBlockEntity} actually accepts — two arms ({@code MIN_ARMS}), each three
     * blocks long, terminated in a real {@code asterium_block} anchor, inside a closed boundary
     * ring one block past the longest arm — the exact figure
     * {@code astraAltarActivatesAndCompletesARealRitual} proves end to end, not a bigger or
     * smaller one an artist might have guessed at. Arm length and boundary radius are stated here
     * once, not read from {@code AstraAltarBlockEntity} directly: that class lives in
     * {@code block.entity} and pulls in a page of Minecraft imports a client-only codex renderer
     * has no reason to depend on — the same deliberately-duplicated-constant shape this project
     * already uses for {@code FLARE_SEED}, not an oversight. Must stay a real, acceptable figure
     * per that class's own {@code MIN_ARMS}/{@code MAX_ARM_LENGTH} if either ever changes.
     */
    private static UIElement astraAltarDiagram() {
        final int armLength = 3;
        final int boundaryRadius = armLength + 1;
        UIElement grid = new UIElement().addClass("codex_recipe_grid");
        grid.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(2));
        for (int dz = -boundaryRadius; dz <= boundaryRadius; dz++) {
            UIElement row = new UIElement();
            row.layout(l -> l.flexDirection(FlexDirection.ROW).gapAll(2));
            for (int dx = -boundaryRadius; dx <= boundaryRadius; dx++) {
                row.addChild(gridCell(astraAltarCellItem(dx, dz, armLength, boundaryRadius)));
            }
            grid.addChild(row);
        }
        return grid;
    }

    /** One cell of {@link #astraAltarDiagram()} — the focus at the centre, an anchor at each
     *  arm's own tip, plain solid block (any block really works, per {@code detectArms}'s own
     *  "any other solid block: the arm's own body") for the rest of the two arms and the whole
     *  boundary ring, and nothing everywhere else. */
    private static String astraAltarCellItem(int dx, int dz, int armLength, int boundaryRadius) {
        if (dx == 0 && dz == 0) {
            return "astronima:astra_altar";
        }
        boolean onNorthArm = dx == 0 && dz < 0 && dz >= -armLength;
        boolean onEastArm = dz == 0 && dx > 0 && dx <= armLength;
        if (onNorthArm) {
            return dz == -armLength ? "astronima:asterium_block" : "minecraft:stone";
        }
        if (onEastArm) {
            return dx == armLength ? "astronima:asterium_block" : "minecraft:stone";
        }
        if (Math.abs(dx) == boundaryRadius || Math.abs(dz) == boundaryRadius) {
            return "minecraft:stone";
        }
        return null;
    }

    /**
     * {@code {calc:id}} - a small live instrument: real inputs a player drags, real outputs read
     * off {@link Calculator#compute} at every change. See {@code design/codex-calculator-c4a.md}.
     */
    private static UIElement calc(CodexUi.Session session, String pageId, String id) {
        Calculator calculator = Calculators.get(id);
        if (calculator == null) {
            // Rule 18: the loudest path, naming the id, never a silently dropped block.
            Label error = new Label().setValue(Component.translatable("astronima.codex.no_calculator", id));
            error.addClass("codex_calc_error");
            wrapped(error);
            return error;
        }

        UIElement panel = new UIElement().addClass("codex_calc");
        panel.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN));

        List<Calculator.Input> inputs = calculator.inputs();
        // Keyed by page, not just by calculator id - the same calculator can sit on more than
        // one page (design/codex-calculator.md open question 3) and "what were you last asking"
        // is a per-page question, not a per-calculator one.
        String cacheKey = pageId + "#" + id;
        List<Double> remembered = session.calcInputs.get(cacheKey);
        double[] values = new double[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            values[i] = remembered != null && i < remembered.size()
                    ? remembered.get(i) : inputs.get(i).preset();
        }

        UIElement outputColumn = new UIElement();
        outputColumn.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            List<Double> boxed = new ArrayList<>(values.length);
            for (double value : values) {
                boxed.add(value);
            }
            session.calcInputs.put(cacheKey, boxed);
            outputColumn.clearAllChildren();
            for (Calculator.Output output : calculator.compute(boxed)) {
                outputColumn.addChild(outputRow(output));
            }
        };

        for (int i = 0; i < inputs.size(); i++) {
            int index = i;
            panel.addChild(inputRow(inputs.get(i), values[i], newValue -> {
                values[index] = newValue;
                refresh[0].run();
            }));
        }
        panel.addChild(outputColumn);
        refresh[0].run();
        return panel;
    }

    private static UIElement inputRow(Calculator.Input input, double initial, DoubleConsumer onChange) {
        UIElement row = new UIElement().addClass("codex_calc_input_row");
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));

        Label label = new Label().setValue(Component.translatable(input.labelKey()));
        label.addClass("codex_dim");
        label.textStyle(style -> style.fontSize(7));
        label.layout(l -> l.width(90).heightAuto());
        row.addChild(label);

        // Plain string mode, not NUMBER_DOUBLE - a number-mode TextField also claims the mouse
        // wheel (its own docs: "Number fields support: Mouse wheel to increment/decrement"),
        // which is exactly the conflict NoWheelSlider below exists to avoid. String mode plus
        // Calculator.parseTyped handles ru_ru's comma decimal in the one place it needs handling
        // instead of two.
        TextField field = new TextField();
        field.setText(Calculator.format("%.3f", initial));
        field.layout(l -> l.width(42));

        NoWheelSlider slider = new NoWheelSlider();
        slider.layout(l -> l.flex(1).height(7));
        slider.setRange((float) input.min(), (float) input.max());
        slider.setValue((float) initial);
        slider.setOnValueChanged(value -> {
            onChange.accept(value);
            field.setText(Calculator.format("%.3f", (double) value));
        });
        row.addChild(slider);

        field.addEventListener(UIEvents.KEY_UP, event -> {
            Double parsed = Calculator.parseTyped(field.getValue());
            if (parsed != null) {
                double clamped = Math.clamp(parsed, input.min(), input.max());
                slider.setValue((float) clamped);
                onChange.accept(clamped);
            }
        });
        row.addChild(field);

        return row;
    }

    private static UIElement outputRow(Calculator.Output output) {
        UIElement row = new UIElement().addClass(verdictClass(output.verdict()));
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER));

        Label label = new Label().setValue(Component.translatable(output.labelKey()));
        label.addClass("codex_dim");
        label.textStyle(style -> style.fontSize(7));
        label.layout(l -> l.flex(1).heightAuto());
        row.addChild(label);

        String shown = output.unit().isEmpty() ? output.shown() : output.shown() + " " + output.unit();
        Label value = new Label().setValue(Component.literal(shown));
        value.addClass("codex_body");
        value.textStyle(style -> style.fontSize(7));
        row.addChild(value);

        if (output.verdict() != Calculator.Verdict.IMPOSSIBLE || output.noteKey().isEmpty()) {
            return row;
        }
        UIElement column = new UIElement();
        column.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN));
        Label note = new Label().setValue(Component.translatable(output.noteKey()));
        note.addClass("codex_dim");
        wrapped(note);
        column.addChildren(row, note);
        return column;
    }

    private static String verdictClass(Calculator.Verdict verdict) {
        return switch (verdict) {
            case GOOD -> "codex_calc_good";
            case MARGINAL -> "codex_calc_marginal";
            case BAD -> "codex_calc_bad";
            case IMPOSSIBLE -> "codex_calc_impossible";
            case INFO -> "codex_calc_info";
        };
    }

    /**
     * A horizontal slider that never claims the mouse wheel.
     *
     * <p>{@code Slider.Horizontal.onScrollWheel} changes the value and does not call {@code
     * event.stopPropagation()} (confirmed in LDLib2 source) - inside the codex's scrolling page
     * that means rolling the wheel to keep reading would silently drag whichever input the
     * cursor happened to be resting over, a control the player never touched changing the answer
     * they are reading. Overriding the hook to a no-op leaves the wheel event unconsumed, so it
     * bubbles up to the page's own {@code ScrollerView} instead - the same class of conflict that
     * ruled the node-graph toolkit's {@code GraphView} out of the recipe diagram.
     */
    private static final class NoWheelSlider extends Slider.Horizontal {
        @Override
        protected void onScrollWheel(UIEvent event) {
        }
    }

    /**
     * Just the slot, no name label underneath - hovering the slot already shows the item's real
     * name (and count, and tooltip lore), so a second copy of the same string in tiny text under
     * every node was pure duplication that also made the result's own card read as oversized next
     * to the plain 3x3 grid cells beside it.
     */
    private static UIElement recipeNode(ItemStack stack, boolean isResult) {
        UIElement card = new UIElement().addClass(isResult ? "codex_recipe_node_result" : "codex_recipe_node");
        card.layout(l -> l.flexDirection(FlexDirection.COLUMN).alignItems(AlignItems.CENTER));
        card.addChild(slotOf(stack));
        return card;
    }

    /**
     * A recipe drawn as a small many-to-one graph - every ingredient node's own line converges
     * on the result node, instead of the flat "icon icon icon -&gt; icon" row this replaced.
     *
     * <p>The lines are hand-drawn in {@link #drawBackgroundAdditional} from each node's own
     * resolved screen position ({@code getPositionX/Y}, real numbers any {@code UIElement} can
     * report once laid out) rather than built on LDLib2's node-graph editor toolkit
     * ({@code nodegraphtookit}'s {@code GraphView}/{@code WireElement}): that toolkit is model-,
     * command- and port-driven, built for an editable graph a player drags nodes around in, and
     * far heavier than a static, read-only diagram needs - it also pans and zooms on the mouse
     * wheel by design, which would fight this element's own parent {@code ScrollerView} for the
     * same gesture. {@link DrawerHelperClient#drawLines} is the exact primitive that toolkit's
     * own {@code WireElement} ultimately calls to put pixels on screen, and {@link
     * WireElement#roundCorners} is reused as-is for the same rounded elbow it draws with,
     * without pulling in the model/command machinery around it.
     */
    private static final class RecipeGraph extends UIElement {
        private final List<UIElement> ingredientNodes = new ArrayList<>();
        private UIElement resultNode;

        void trackIngredient(UIElement node) {
            ingredientNodes.add(node);
        }

        void trackResult(UIElement node) {
            resultNode = node;
        }

        @Override
        protected void drawBackgroundAdditional(IGUIContext context) {
            super.drawBackgroundAdditional(context);
            if (!(context instanceof GUIContext guiContext) || resultNode == null) {
                return;
            }
            float toX = resultNode.getPositionX();
            float toY = resultNode.getPositionY() + resultNode.getSizeHeight() / 2f;
            for (UIElement ingredient : ingredientNodes) {
                float fromX = ingredient.getPositionX() + ingredient.getSizeWidth();
                float fromY = ingredient.getPositionY() + ingredient.getSizeHeight() / 2f;
                float midX = (fromX + toX) / 2f;
                List<Vector2f> points = WireElement.roundCorners(List.of(
                        new Vector2f(fromX, fromY),
                        new Vector2f(midX, fromY),
                        new Vector2f(midX, toY),
                        new Vector2f(toX, toY)
                ), 4f, 6);
                DrawerHelperClient.drawLines(guiContext, points, 0xFF6FB0EE, 0xFF6FB0EE, 1.5f);
            }
        }
    }

    private static ItemSlot slotOf(ItemStack stack) {
        ItemSlot slot = new ItemSlot();
        slot.setItem(stack);
        slot.layout(l -> l.width(18).height(18));
        return slot;
    }

    /** Cuts a string to a length a pill button can hold, with an honest ellipsis rather than a
     *  silently truncated word - the reader should be able to tell the title was cut short. */
    private static String shortened(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars - 1).stripTrailing() + "…";
    }

    private static ItemStack stackOf(String id) {
        Identifier at = Identifier.tryParse(id);
        var item = at == null ? null : BuiltInRegistries.ITEM.getOptional(at).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static Component spansToComponent(CodexBook book, List<CodexMarkup.Span> spans) {
        net.minecraft.network.chat.MutableComponent built = Component.empty();
        for (CodexMarkup.Span span : spans) {
            built = switch (span) {
                case CodexMarkup.Span.Text text -> built.append(text.bold()
                        ? Component.literal(text.value()).withStyle(net.minecraft.ChatFormatting.BOLD)
                        : Component.literal(text.value()));
                case CodexMarkup.Span.Link link -> built.append(Component.literal(linkLabel(book, link))
                        .withStyle(style -> style.withColor(0x6FB0EE)));
                case CodexMarkup.Span.Icon icon -> {
                    ItemStack stack = stackOf(icon.id());
                    yield built.append(Component.literal("[" + stack.getHoverName().getString() + "]")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            };
        }
        return built;
    }

    /**
     * What an inline {@code [[id]]}/{@code [[id|label]]} shows. An explicit {@code |label} is
     * always the author's own words and wins outright. A bare {@code [[id]]} has no author-given
     * label at all — {@link CodexMarkup}'s parser fills {@code label} in with the raw {@code id}
     * itself just so the record has something non-null in it — so this resolves it against the
     * real target page's title instead of ever showing that raw path to the reader. A target that
     * does not exist falls back to the raw id on purpose: a dead link should look wrong, not be
     * quietly polished into something that reads fine while pointing nowhere.
     */
    private static String linkLabel(CodexBook book, CodexMarkup.Span.Link link) {
        if (!link.label().equals(link.page())) {
            return link.label();
        }
        CodexPage target = book.page(link.page());
        return target == null ? link.page() : target.title();
    }

    private CodexBlocks() {}
}
