package play.xponer.astronima.item;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import play.xponer.astronima.sim.logic.Circuit;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.CRC32;

/**
 * A circuit as a string a player can copy, paste into chat with a friend, or keep in a text
 * file — {@link CircuitPlate#CODEC} read through {@link JsonOps} rather than NBT or the
 * network, since a clipboard is neither.
 *
 * <p><strong>A garbled paste is refused, not silently blanked.</strong> {@link
 * CircuitPlate#STREAM_CODEC}'s own "malformed reads as empty" rule is right for a network
 * packet — an attacker gets nothing worth attacking over — but a player pasting a string they
 * typed, half-copied, or received corrupted over a chat client that mangled it deserves an
 * honest "that didn't work," not a blank plate that looks like their own circuit was erased.
 * A short prefix and a checksum catch that before the real decode even runs.
 */
public final class CircuitClipboard {

    /** Bumped if the wire shape ever changes, so an old paste fails loudly rather than lying. */
    private static final String PREFIX = "astronima-circuit-v1";

    private static final Gson GSON = new Gson();

    /** Always succeeds: a {@link Circuit} on a plate in this mod is already valid by construction. */
    public static String encode(Circuit circuit) {
        JsonElement json = CircuitPlate.CODEC.encodeStart(JsonOps.INSTANCE, circuit)
                .getOrThrow();
        String body = GSON.toJson(json);
        return PREFIX + ":" + Integer.toHexString(checksum(body)) + ":" + body;
    }

    /** Empty for anything that is not a real, intact paste of this format — never a guess. */
    public static Optional<Circuit> decode(String text) {
        String[] parts = text.strip().split(":", 3);
        if (parts.length != 3 || !parts[0].equals(PREFIX)) {
            return Optional.empty();
        }
        int expectedChecksum;
        try {
            expectedChecksum = Integer.parseUnsignedInt(parts[1], 16);
        } catch (NumberFormatException notHex) {
            return Optional.empty();
        }
        String body = parts[2];
        if (checksum(body) != expectedChecksum) {
            return Optional.empty();
        }
        try {
            JsonElement json = JsonParser.parseString(body);
            return CircuitPlate.CODEC.parse(JsonOps.INSTANCE, json).result();
        } catch (JsonSyntaxException malformed) {
            return Optional.empty();
        }
    }

    private static int checksum(String body) {
        CRC32 crc = new CRC32();
        crc.update(body.getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    private CircuitClipboard() {}
}
