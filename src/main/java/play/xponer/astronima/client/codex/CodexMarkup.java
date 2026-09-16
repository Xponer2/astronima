package play.xponer.astronima.client.codex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a guide file into blocks a screen can draw.
 *
 * <h2>Why a file, and why this format</h2>
 * Reported as <em>"there is a whole book written for every machine and it is hard to follow — I want
 * a codex I can write guides into myself"</em>. Both halves matter. The long-form explanation had
 * nowhere to live except the machine's JEI page, so the page carried an essay and the player got a
 * wall of text where they wanted a recipe; and nothing could be written without recompiling.
 *
 * <p>So a guide is a <strong>file</strong> under {@code assets/astronima/codex/}, in a format a
 * person can type without looking anything up: Markdown as far as it goes, plus two things Markdown
 * has no spelling for — a link to another page and an item drawn inline.
 *
 * <pre>
 *   ---
 *   title: Sizing a conductor
 *   section: Electrical
 *   order: 20
 *   icon: astronima:power_fuse
 *   ---
 *
 *   Plain paragraphs, wrapped by the screen rather than by you.
 *
 *   ## A heading
 *
 *   - a bullet
 *   - **bold** inside one
 *
 *   &gt; A callout, for the one sentence that matters.
 *
 *   See [[electrical/protection]] or [[electrical/protection|what trips]].
 *   A fuse {item:astronima:power_fuse} costs a quarter of a plate.
 * </pre>
 *
 * <h2>Minecraft-free, deliberately</h2>
 * Parsing is a decision — where a heading ends, what a link points at, whether a line is a bullet —
 * and rule 25 puts decisions where a unit test can reach them. The screen receives blocks and paints
 * them; it does not read a single character of the source.
 */
public final class CodexMarkup {

    /** One run of text with one meaning. */
    public sealed interface Span {

        record Text(String value, boolean bold) implements Span { }

        /** A jump to another page: {@code [[id]]} or {@code [[id|what to call it]]}. */
        record Link(String page, String label) implements Span { }

        /** An item drawn where the words are: {@code {item:astronima:power_fuse}}. */
        record Icon(String id) implements Span { }
    }

    /** A picture: {@code ![what it shows](astronima:textures/codex/x.png 160x90)}. */
    public record Figure(String texture, int width, int height, String caption) { }

    /** One thing on the page, in the order it was written. */
    public sealed interface Block {

        record Heading(int level, String text) implements Block { }

        record Paragraph(List<Span> spans) implements Block { }

        record Bullet(List<Span> spans) implements Block { }

        /** A pulled-out line, for the sentence that would otherwise be missed. */
        record Callout(List<Span> spans) implements Block { }

        record Rule() implements Block { }

        /** A drawn figure, sized by the author and centred in the column. */
        record Image(Figure figure) implements Block { }

        /**
         * A markdown table: {@code | a | b |} rows, with the standard {@code |---|---|} rule
         * row between the header and the body consumed rather than kept — it is punctuation for
         * the parser, not content for the reader. Each cell runs through the same span parser
         * prose does, so a cell can hold bold text, a link or an inline item like any other line.
         */
        record Table(List<List<Span>> header, List<List<List<Span>>> rows) implements Block { }

        /**
         * The actual crafting grid for an item, drawn from the recipe the game uses.
         *
         * <p>{@code {recipe:astronima:power_fuse}}. A picture that cannot go stale, because it is
         * not a picture — it is the recipe, read out of {@code CraftingTree} at draw time.
         */
        record Recipe(String item) implements Block { }

        /**
         * An interactive calculator: real inputs a player drags, real outputs computed from
         * {@code sim/codex/Calculators} at draw time. {@code {calc:ore/comminution}}. See
         * {@code design/codex-calculator-c4a.md}.
         */
        record Calc(String id) implements Block { }

        /**
         * A real multiblock's own footprint, drawn top-down: {@code {structure:astra_altar}}.
         * The same "read out of the real model at draw time, never a picture that can go stale"
         * shape {@link Recipe} already uses, applied to a built structure instead of a crafting
         * grid — {@code client.screen.CodexBlocks} draws the actual shape a real placement would
         * check against, not an artist's guess at what the block entity accepts.
         */
        record Structure(String id) implements Block { }

        /**
         * A run of blocks the reader has not earned yet: {@code :::locked crafted:astronima:cold_forge}
         * ... {@code :::}. See {@code design/codex-disclosure.md} §4 — a fence around a run of
         * blocks, not a tag on one, because "leave the general idea, lock the precise part" is
         * naturally a paragraph or a table, not a clause inside a sentence.
         *
         * <p>Nesting is refused rather than supported: a {@code :::locked} found while already
         * inside one is flattened into this one at parse time ({@link #flattenNestedLocks}) —
         * two gates on the same words is a content mistake worth catching during review, not a
         * feature.
         */
        record Locked(String unlockId, List<Block> inner) implements Block { }
    }

    /** A parsed guide: its header fields, and its body. */
    public record Parsed(Map<String, String> header, List<Block> blocks) {

        public String field(String name, String fallback) {
            String found = header.get(name);
            return found == null || found.isBlank() ? fallback : found;
        }

        public int number(String name, int fallback) {
            try {
                return Integer.parseInt(field(name, "").trim());
            } catch (NumberFormatException notANumber) {
                return fallback;
            }
        }
    }

    /**
     * Reads a guide.
     *
     * <p>Nothing here throws. A guide is <em>content</em>, and content is edited by hand between
     * play sessions — a typo that crashed the book would make the book the most dangerous thing in
     * the mod. A malformed line becomes an ordinary paragraph, which is visible, harmless, and
     * obviously wrong to whoever wrote it.
     */
    public static Parsed parse(String source) {
        List<String> lines = new ArrayList<>(List.of(source.replace("\r\n", "\n").split("\n", -1)));
        Map<String, String> header = readHeader(lines);
        return new Parsed(header, List.copyOf(parseBlocks(lines)));
    }

    /**
     * The block-level loop, pulled out of {@link #parse} so a {@code :::locked} fence's own
     * contents can be parsed by the same rules as a top-level page — a locked run of blocks is
     * not a different kind of content, only a gated one.
     */
    private static List<Block> parseBlocks(List<String> lines) {
        List<Block> blocks = new ArrayList<>();
        List<String> paragraph = new ArrayList<>();
        List<String> table = new ArrayList<>();
        List<String> locked = null;
        String lockedId = null;
        int fenceDepth = 0;

        for (String raw : lines) {
            String line = raw.strip();
            if (locked != null) {
                boolean opensNested = line.startsWith(":::locked ") || line.equals(":::locked");
                if (line.equals(":::") && fenceDepth == 0) {
                    blocks.add(new Block.Locked(lockedId, flattenNestedLocks(parseBlocks(locked))));
                    locked = null;
                    lockedId = null;
                } else {
                    // A depth counter, not a second state machine: a nested :::locked...:::
                    // pair has to stay balanced inside the raw buffer so the recursive parse
                    // below sees a complete, well-formed fence to flatten - closing on the
                    // first ":::" regardless of depth would close on the *inner* fence's own
                    // end instead of the outer's.
                    if (opensNested) {
                        fenceDepth++;
                    } else if (line.equals(":::")) {
                        fenceDepth--;
                    }
                    locked.add(raw);
                }
                continue;
            }
            if (line.startsWith(":::locked ") || line.equals(":::locked")) {
                flush(paragraph, blocks);
                flushTable(table, blocks);
                lockedId = line.length() > ":::locked".length()
                        ? line.substring(":::locked".length()).strip() : "";
                locked = new ArrayList<>();
                fenceDepth = 0;
                continue;
            }
            if (line.isEmpty()) {
                flush(paragraph, blocks);
                flushTable(table, blocks);
                continue;
            }
            if (line.startsWith("|") && line.endsWith("|") && line.length() > 1) {
                flush(paragraph, blocks);
                table.add(line);
                continue;
            }
            flushTable(table, blocks);
            if (line.startsWith("#")) {
                flush(paragraph, blocks);
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                blocks.add(new Block.Heading(Math.min(level, 3), line.substring(level).strip()));
            } else if (line.equals("---") || line.equals("***")) {
                flush(paragraph, blocks);
                blocks.add(new Block.Rule());
            } else if (line.startsWith("![") && line.contains("](") && line.endsWith(")")) {
                flush(paragraph, blocks);
                blocks.add(new Block.Image(figure(line)));
            } else if (line.startsWith("{recipe:") && line.endsWith("}")) {
                flush(paragraph, blocks);
                blocks.add(new Block.Recipe(line.substring(8, line.length() - 1).strip()));
            } else if (line.startsWith("{calc:") && line.endsWith("}")) {
                flush(paragraph, blocks);
                blocks.add(new Block.Calc(line.substring(6, line.length() - 1).strip()));
            } else if (line.startsWith("{structure:") && line.endsWith("}")) {
                flush(paragraph, blocks);
                blocks.add(new Block.Structure(line.substring(11, line.length() - 1).strip()));
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                flush(paragraph, blocks);
                blocks.add(new Block.Bullet(spans(line.substring(2).strip())));
            } else if (line.startsWith("> ")) {
                flush(paragraph, blocks);
                blocks.add(new Block.Callout(spans(line.substring(2).strip())));
            } else {
                // Joined rather than kept as written: the screen wraps to its own width, so a hard
                // line break in the source would put a ragged edge wherever the author's editor
                // happened to be.
                paragraph.add(line);
            }
        }
        flush(paragraph, blocks);
        flushTable(table, blocks);
        // An unclosed :::locked degrades to visible content rather than vanishing - "nothing
        // here throws" (the class doc's own rule) extends to "nothing here disappears" too.
        if (locked != null) {
            blocks.addAll(flattenNestedLocks(parseBlocks(locked)));
        }
        return blocks;
    }

    /** Unwraps any nested {@code Block.Locked} into its parent's own inner list — see the field's doc. */
    private static List<Block> flattenNestedLocks(List<Block> blocks) {
        List<Block> flat = new ArrayList<>();
        for (Block block : blocks) {
            if (block instanceof Block.Locked nested) {
                flat.addAll(flattenNestedLocks(nested.inner()));
            } else {
                flat.add(block);
            }
        }
        return flat;
    }

    /**
     * Reads {@code ![caption](texture 160x90)}.
     *
     * <p>The size is the author's, because only they know what the picture is of: a wiring diagram
     * wants the full column and an icon wants sixteen pixels. Left off, it gets a sensible default
     * rather than a broken page.
     */
    private static Figure figure(String line) {
        String caption = line.substring(2, line.indexOf("]("));
        String body = line.substring(line.indexOf("](") + 2, line.length() - 1).strip();
        int space = body.lastIndexOf(' ');
        int width = 128;
        int height = 64;
        String texture = body;
        if (space > 0 && body.substring(space + 1).matches("\\d+x\\d+")) {
            String[] size = body.substring(space + 1).split("x");
            width = Integer.parseInt(size[0]);
            height = Integer.parseInt(size[1]);
            texture = body.substring(0, space).strip();
        }
        return new Figure(texture, width, height, caption);
    }

    /** {@code key: value} lines between two {@code ---} fences, if the file opens with one. */
    private static Map<String, String> readHeader(List<String> lines) {
        Map<String, String> header = new LinkedHashMap<>();
        if (lines.isEmpty() || !lines.get(0).strip().equals("---")) {
            return header;
        }
        lines.remove(0);
        while (!lines.isEmpty()) {
            String line = lines.remove(0).strip();
            if (line.equals("---")) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                header.put(line.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).strip());
            }
        }
        return header;
    }

    private static void flush(List<String> paragraph, List<Block> blocks) {
        if (paragraph.isEmpty()) {
            return;
        }
        blocks.add(new Block.Paragraph(spans(String.join(" ", paragraph))));
        paragraph.clear();
    }

    /**
     * Turns the raw {@code |a|b|} lines collected while a table was being read into a real
     * {@link Block.Table}. The first row is the header; the second is the {@code |---|---|}
     * rule and is thrown away rather than kept as an empty row; everything after is body.
     *
     * <p>A table of one line (no separator, so nothing after the header) is not a table — it is
     * a line that happened to start and end with {@code |}, and is filed as an ordinary
     * paragraph instead of a one-row table nobody meant to write.
     */
    private static void flushTable(List<String> table, List<Block> blocks) {
        if (table.isEmpty()) {
            return;
        }
        if (table.size() < 2 || !isRuleRow(table.get(1))) {
            blocks.add(new Block.Paragraph(spans(String.join(" ", table))));
            table.clear();
            return;
        }
        List<List<Span>> header = cells(table.get(0));
        List<List<List<Span>>> rows = new ArrayList<>();
        for (int i = 2; i < table.size(); i++) {
            rows.add(cells(table.get(i)));
        }
        blocks.add(new Block.Table(header, List.copyOf(rows)));
        table.clear();
    }

    /** {@code |---|:--:|---|} and its spacing variants - nothing but pipes, dashes and colons. */
    private static boolean isRuleRow(String line) {
        return line.chars().allMatch(c -> c == '|' || c == '-' || c == ':' || c == ' ');
    }

    /** Splits {@code | a | b |} into cells, dropping the empty strings the leading and trailing
     *  {@code |} produce, and running each cell through the same span parser prose uses. */
    private static List<List<Span>> cells(String row) {
        String inner = row.substring(1, row.length() - 1);
        List<List<Span>> parsed = new ArrayList<>();
        for (String cell : inner.split("\\|", -1)) {
            parsed.add(spans(cell.strip()));
        }
        return List.copyOf(parsed);
    }

    /**
     * Splits a line into runs: plain, bold, links and item icons.
     *
     * <p>One pass, left to right, because the alternative — a regular expression per kind, run over
     * the whole line — loses the order they appeared in, and order is the only thing a sentence
     * has.
     */
    public static List<Span> spans(String line) {
        List<Span> spans = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        boolean bold = false;
        int at = 0;
        while (at < line.length()) {
            if (line.startsWith("**", at)) {
                spill(spans, plain, bold);
                bold = !bold;
                at += 2;
            } else if (line.startsWith("[[", at) && line.indexOf("]]", at) > 0) {
                spill(spans, plain, bold);
                String body = line.substring(at + 2, line.indexOf("]]", at));
                int bar = body.indexOf('|');
                spans.add(bar < 0
                        ? new Span.Link(body.strip(), body.strip())
                        : new Span.Link(body.substring(0, bar).strip(),
                                body.substring(bar + 1).strip()));
                at = line.indexOf("]]", at) + 2;
            } else if (line.startsWith("{item:", at) && line.indexOf('}', at) > 0) {
                spill(spans, plain, bold);
                spans.add(new Span.Icon(line.substring(at + 6, line.indexOf('}', at)).strip()));
                at = line.indexOf('}', at) + 1;
            } else if (line.charAt(at) == '`') {
                // Inline code has no styling of its own yet - `enable` is stud names in the
                // terminals table, and the honest choice right now is to read the word rather
                // than draw the backticks as literal punctuation around it. A real monospace/
                // highlighted treatment is a Span kind this has not grown yet.
                at++;
            } else {
                plain.append(line.charAt(at));
                at++;
            }
        }
        spill(spans, plain, bold);
        return List.copyOf(spans);
    }

    private static void spill(List<Span> spans, StringBuilder plain, boolean bold) {
        if (!plain.isEmpty()) {
            spans.add(new Span.Text(plain.toString(), bold));
            plain.setLength(0);
        }
    }

    /** Everything a page says, with the markup taken out — what a search box matches against. */
    public static String plainText(Parsed parsed) {
        StringBuilder out = new StringBuilder();
        for (Block block : parsed.blocks()) {
            switch (block) {
                case Block.Heading heading -> out.append(heading.text()).append(' ');
                case Block.Paragraph paragraph -> append(out, paragraph.spans());
                case Block.Bullet bullet -> append(out, bullet.spans());
                case Block.Callout callout -> append(out, callout.spans());
                case Block.Image image -> out.append(image.figure().caption()).append(' ');
                case Block.Recipe recipe -> out.append(recipe.item()).append(' ');
                case Block.Structure structure -> out.append(structure.id()).append(' ');
                case Block.Calc calc -> out.append(calc.id()).append(' ');
                case Block.Table table -> {
                    table.header().forEach(cell -> append(out, cell));
                    table.rows().forEach(row -> row.forEach(cell -> append(out, cell)));
                }
                case Block.Rule ignored -> { }
                // Deliberately silent, not recursed into: a search index built once at parse
                // time cannot be per-player, so including a locked block's real words here
                // would let searching for a hidden term surface the page early - the same door
                // design/codex-disclosure.md §7.2 worries JEI could leak through, generalised
                // to the book's own search box.
                case Block.Locked ignored -> { }
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder out, List<Span> spans) {
        for (Span span : spans) {
            switch (span) {
                case Span.Text text -> out.append(text.value());
                case Span.Link link -> out.append(link.label());
                case Span.Icon icon -> out.append(icon.id());
            }
            out.append(' ');
        }
    }

    private CodexMarkup() {}
}
