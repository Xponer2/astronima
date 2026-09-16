package play.xponer.astronima.client.codex;

import java.util.List;
import java.util.Optional;

/**
 * What a toast should say when {@code unlockId} is granted — the page it lives on, and a short
 * phrase drawn from the first real line it hides. See design/codex-disclosure.md §6: "the toast
 * fires from the exact call site that grants the unlock and reads the same page/id it just
 * wrote," which is why this is a pure lookup over the already-loaded book rather than a value
 * computed twice in two different places.
 *
 * <p>Minecraft-free, like {@link CodexBook} and {@link CodexMarkup} themselves — the walk over
 * {@code Block.Locked} is exactly the same kind of decision {@code CodexMarkup.parse} already
 * makes, and rule 25 puts decisions where a unit test can reach them.
 */
public record CodexReveal(String pageTitle, String snippet) {

    /** Searches every page in {@code book} for a {@code Locked} block gated on {@code unlockId}. */
    public static Optional<CodexReveal> find(CodexBook book, String unlockId) {
        for (CodexBook.Section section : book.sections()) {
            for (CodexPage page : section.pages()) {
                Optional<CodexMarkup.Block.Locked> match = findIn(page.blocks(), unlockId);
                if (match.isPresent()) {
                    return Optional.of(new CodexReveal(page.title(), snippetOf(match.get())));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<CodexMarkup.Block.Locked> findIn(List<CodexMarkup.Block> blocks, String unlockId) {
        for (CodexMarkup.Block block : blocks) {
            if (block instanceof CodexMarkup.Block.Locked locked) {
                if (locked.unlockId().equals(unlockId)) {
                    return Optional.of(locked);
                }
                // A nested lock is already flattened into its parent at parse time
                // (CodexMarkup's own rule), so this recursion only ever finds another
                // top-level id sitting beside real content inside the same fence — it does
                // not need to handle genuine nesting, because none survives parsing.
                Optional<CodexMarkup.Block.Locked> inInner = findIn(locked.inner(), unlockId);
                if (inInner.isPresent()) {
                    return inInner;
                }
            }
        }
        return Optional.empty();
    }

    /** The first line of real text a resolved block reveals, for the toast's own short phrase. */
    private static String snippetOf(CodexMarkup.Block.Locked locked) {
        for (CodexMarkup.Block block : locked.inner()) {
            String text = switch (block) {
                case CodexMarkup.Block.Heading heading -> heading.text();
                case CodexMarkup.Block.Paragraph paragraph -> plain(paragraph.spans());
                case CodexMarkup.Block.Bullet bullet -> plain(bullet.spans());
                case CodexMarkup.Block.Callout callout -> plain(callout.spans());
                default -> "";
            };
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static String plain(List<CodexMarkup.Span> spans) {
        StringBuilder out = new StringBuilder();
        for (CodexMarkup.Span span : spans) {
            if (span instanceof CodexMarkup.Span.Text text) {
                out.append(text.value());
            }
        }
        return out.toString();
    }
}
