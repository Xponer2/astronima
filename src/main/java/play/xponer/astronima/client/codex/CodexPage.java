package play.xponer.astronima.client.codex;

import java.util.ArrayList;
import java.util.List;

/**
 * One guide, loaded from one file.
 *
 * @param id      its path under {@code codex/} without the extension — what {@code [[links]]} name
 * @param title   the heading in the sidebar
 * @param section which group of the contents it belongs to
 * @param order   position within that section; also decides where the section itself sits
 * @param icon    an item id drawn beside the title, or empty
 * @param parent  the page this one sits under, or empty for a top-level page
 * @param atlas   which atlas node this page is the entry for ({@code object/<id>},
 *                {@code claim/<id>}, {@code line/<id>}), or empty for a page with no atlas node
 *                — design/astra-atlas-s2-entry.md §3.2. Front matter already carried this key
 *                (read raw off disk by {@code AtlasContentCoverageTest}); nothing surfaced it
 *                through the loaded book itself until now, which is what a page actually needs
 *                to be found by a player opening the atlas rather than by a build-time scan.
 */
public record CodexPage(String id, String title, String section, int order, String icon,
                        String parent, List<CodexMarkup.Block> blocks, String searchText,
                        String atlas) {

    public static CodexPage of(String id, CodexMarkup.Parsed parsed) {
        // Named from the file when the header does not say, so a page can be written with no
        // header at all and still appear. The cheapest possible guide is a filename and a
        // paragraph, and it should work.
        String fallbackTitle = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
        return new CodexPage(id,
                parsed.field("title", fallbackTitle),
                parsed.field("section", "Field notes"),
                parsed.number("order", 100),
                parsed.field("icon", ""),
                parsed.field("parent", ""),
                parsed.blocks(),
                CodexMarkup.plainText(parsed),
                parsed.field("atlas", ""));
    }

    /**
     * Every page this one points at, so the book can be checked for dead ends.
     *
     * <p>Walks into a {@code Block.Locked}'s own inner blocks too — a link's freshness is a
     * build-time correctness question, unrelated to whether a player has earned the words
     * around it, so a link inside locked content still has to point somewhere real.
     */
    public List<String> links() {
        return List.copyOf(linksIn(blocks));
    }

    private static List<String> linksIn(List<CodexMarkup.Block> blocks) {
        List<String> found = new ArrayList<>();
        for (CodexMarkup.Block block : blocks) {
            if (block instanceof CodexMarkup.Block.Locked locked) {
                found.addAll(linksIn(locked.inner()));
                continue;
            }
            List<List<CodexMarkup.Span>> runs = switch (block) {
                case CodexMarkup.Block.Paragraph paragraph -> List.of(paragraph.spans());
                case CodexMarkup.Block.Bullet bullet -> List.of(bullet.spans());
                case CodexMarkup.Block.Callout callout -> List.of(callout.spans());
                case CodexMarkup.Block.Table table -> {
                    List<List<CodexMarkup.Span>> all = new ArrayList<>(table.header());
                    table.rows().forEach(all::addAll);
                    yield all;
                }
                default -> List.of();
            };
            for (List<CodexMarkup.Span> spans : runs) {
                for (CodexMarkup.Span span : spans) {
                    if (span instanceof CodexMarkup.Span.Link link) {
                        found.add(link.page());
                    }
                }
            }
        }
        return found;
    }

    /** True when this page hangs under another — drawn indented in the contents. */
    public boolean isChild() {
        return !parent.isBlank();
    }
}
