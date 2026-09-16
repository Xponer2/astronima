package play.xponer.astronima.client.codex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every guide that loaded, arranged the way the book shows them.
 *
 * <p>Sections come from the pages themselves rather than from a table somebody maintains: drop a
 * file in with {@code section: Electrical} and the section appears. A book whose contents page has
 * to be edited separately is a book with a contents page that is wrong (rule 20).
 *
 * <p>Minecraft-free, so the ordering, the sectioning and the search are all things a test can check
 * without a screen.
 */
public record CodexBook(List<Section> sections, Map<String, CodexPage> byId) {

    public record Section(String name, int order, List<CodexPage> pages) { }

    public static final CodexBook EMPTY = new CodexBook(List.of(), Map.of());

    /**
     * Groups and orders loaded pages.
     *
     * <p>A section's position is the lowest {@code order} of any page in it, so moving one page to
     * the front moves its section with it and nothing has to declare a section order twice.
     */
    public static CodexBook of(List<CodexPage> pages) {
        Map<String, List<CodexPage>> grouped = new LinkedHashMap<>();
        Map<String, CodexPage> byId = new LinkedHashMap<>();
        for (CodexPage page : pages) {
            grouped.computeIfAbsent(page.section(), key -> new ArrayList<>()).add(page);
            byId.put(page.id(), page);
        }
        List<Section> sections = new ArrayList<>();
        grouped.forEach((name, inSection) -> {
            inSection.sort(Comparator.comparingInt(CodexPage::order).thenComparing(CodexPage::title));
            sections.add(new Section(name,
                    inSection.stream().mapToInt(CodexPage::order).min().orElse(0),
                    List.copyOf(inSection)));
        });
        sections.sort(Comparator.comparingInt(Section::order).thenComparing(Section::name));
        return new CodexBook(List.copyOf(sections), Map.copyOf(byId));
    }

    public CodexPage page(String id) {
        return byId.get(id);
    }

    /**
     * The page whose front matter names {@code atlasKey} (e.g. {@code "object/orion_nebula"}) as
     * its own atlas node — design/astra-atlas-s2-entry.md §3.2, the door
     * {@code AtlasContentCoverageTest} already guards from the other direction (every node has
     * exactly one page; every key names something real). Null when nothing claims it, which is a
     * real, expected state for a node the writing hasn't reached yet — the caller decides how to
     * degrade, this method does not guess.
     *
     * <p>A linear scan, not an index: the book is small and this is never a hot path, the same
     * reasoning {@code AtlasUi.placementFor} already gives for its own lookup.
     */
    public CodexPage pageByAtlasKey(String atlasKey) {
        for (CodexPage page : byId.values()) {
            if (page.atlas().equals(atlasKey)) {
                return page;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    /** The first page of the first section — where the book opens when nothing else is chosen. */
    public CodexPage first() {
        for (Section section : sections) {
            if (!section.pages().isEmpty()) {
                return section.pages().get(0);
            }
        }
        return null;
    }

    /**
     * Pages matching a query, title first.
     *
     * <p>Title matches before body matches, because somebody typing "fuse" wants the page about
     * fuses rather than the nine pages that mention one.
     */
    public List<CodexPage> search(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.of();
        }
        List<CodexPage> titles = new ArrayList<>();
        List<CodexPage> bodies = new ArrayList<>();
        for (Section section : sections) {
            for (CodexPage page : section.pages()) {
                if (page.title().toLowerCase(Locale.ROOT).contains(needle)) {
                    titles.add(page);
                } else if (page.searchText().contains(needle)) {
                    bodies.add(page);
                }
            }
        }
        titles.addAll(bodies);
        return List.copyOf(titles);
    }

    /**
     * Links that point at a page that is not there.
     *
     * <p>A dead link in a guide is the same failure as a dead end in the crafting tree: the book
     * tells the player to go somewhere and there is nothing there. Checked rather than trusted,
     * because a guide is edited by hand and hand-edited cross-references rot.
     */
    public List<String> brokenLinks() {
        List<String> broken = new ArrayList<>();
        for (Section section : sections) {
            for (CodexPage page : section.pages()) {
                for (String target : page.links()) {
                    if (!byId.containsKey(target)) {
                        broken.add(page.id() + " links to " + target + ", which is not a page");
                    }
                }
            }
        }
        return List.copyOf(broken);
    }
}
