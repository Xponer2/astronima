package play.xponer.astronima.progression;

import java.util.HashSet;
import java.util.Set;

/**
 * What a player has earned — the one state a locked codex block and, later, a locked Atlas node
 * both read (design/codex-disclosure.md §1's shared-primitive decision).
 *
 * <p>Deliberately a set of plain strings, not a typed registry: an unlock id is authored inside a
 * markdown file — {@code :::locked crafted:astronima:cold_forge}, see {@code CodexMarkup.Block
 * .Locked} — and a codex page is content, edited between play sessions, the same reason
 * {@code CodexMarkup.parse} itself never throws. A typed enum would mean nothing could be locked
 * without a Java change.
 *
 * <p><strong>No {@code Codec} field here, and that is deliberate, not an oversight.</strong> A
 * first draft carried one, and it made this class untestable from a plain JUnit test: {@code
 * com.mojang.serialization}'s own {@code RecordCodecBuilder} pulls in {@code com.mojang
 * .datafixers.kinds.App}, which is not on {@code src/test/java}'s classpath any more than
 * {@code net.minecraft.*} is — checked directly, not assumed, after {@code UnlocksTest} failed
 * with exactly that {@code ClassNotFoundException}. The same limitation already silently applied
 * to {@code CarriedContamination}/{@code CarriedInfection}, which carry the identical pattern and
 * have never had a direct test either. Rather than add the whole codec library to the test
 * classpath for one field, the codec itself lives in {@code ModAttachments} — the file that
 * actually needs {@code com.mojang.serialization} regardless, for every attachment it declares —
 * and this class stays what {@code sim/}-style Minecraft-free classes already are in this project:
 * plain data and behaviour, provably testable.
 */
public record Unlocks(Set<String> earned) {

    public static final Unlocks NONE = new Unlocks(Set.of());

    public Unlocks {
        earned = Set.copyOf(earned);
    }

    public boolean has(String id) {
        return earned.contains(id);
    }

    /** Adds one id, unchanged if it was already earned — granting twice must not be observable. */
    public Unlocks with(String id) {
        if (earned.contains(id)) {
            return this;
        }
        Set<String> next = new HashSet<>(earned);
        next.add(id);
        return new Unlocks(next);
    }
}
