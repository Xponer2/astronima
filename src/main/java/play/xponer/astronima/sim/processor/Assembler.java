package play.xponer.astronima.sim.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Text in, machine code out — {@code design/processor.md} §5.1's "save assembles." Two passes over
 * the source: the first lays every instruction and directive out at a byte address and records
 * every label's address, the second emits bytes and resolves labels against that table. A label
 * used before it is defined resolves correctly for exactly this reason — the whole table exists
 * before any byte referring to one is written.
 *
 * <h2>A refusal changes nothing (§5.1, §8's second row)</h2>
 * Any error found in either pass empties the result down to just its error list — never a partial
 * program. The caller ({@code WireTicker}, on a chip whose program is being replaced) is expected
 * to leave the previous program running untouched when {@link Result#ok()} is false, which is only
 * possible because a half-assembled attempt is never handed back to be mistaken for a real one.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Assembler {

    public record Result(byte[] bytes, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private record Instruction(int lineNumber, int address, Opcode opcode, String operandText) { }

    private record Literal(int lineNumber, int address, int value) { }

    public static Result assemble(String source) {
        List<String> errors = new ArrayList<>();
        Map<String, Integer> labels = new LinkedHashMap<>();
        List<Instruction> instructions = new ArrayList<>();
        List<Literal> literals = new ArrayList<>();

        String[] rawLines = source.split("\n", -1);
        int cursor = 0;
        for (int i = 0; i < rawLines.length; i++) {
            int lineNumber = i + 1;
            String line = stripComment(rawLines[i]).trim();
            if (line.isEmpty()) {
                continue;
            }

            int colon = line.indexOf(':');
            if (colon >= 0) {
                String label = line.substring(0, colon).trim();
                line = line.substring(colon + 1).trim();
                if (label.isEmpty() || label.indexOf(' ') >= 0) {
                    errors.add("line " + lineNumber + ": not a valid label before ':'");
                } else if (labels.containsKey(label)) {
                    errors.add("line " + lineNumber + ": label '" + label + "' already defined");
                } else {
                    labels.put(label, cursor);
                }
                if (line.isEmpty()) {
                    continue;
                }
            }

            String[] parts = line.split("\\s+", 2);
            String head = parts[0].toUpperCase(Locale.ROOT);
            String operand = parts.length > 1 ? parts[1].trim() : null;

            if (head.equals(".ORG")) {
                OptionalInt value = parseLiteral(operand, labels);
                if (value.isEmpty() || value.getAsInt() < 0 || value.getAsInt() > 255) {
                    errors.add("line " + lineNumber + ": .org needs an address 0-255");
                    continue;
                }
                cursor = value.getAsInt();
                continue;
            }
            if (head.equals(".BYTE")) {
                OptionalInt value = parseLiteral(operand, labels);
                if (value.isEmpty() || value.getAsInt() < 0 || value.getAsInt() > 255) {
                    errors.add("line " + lineNumber + ": .byte needs a value 0-255");
                    continue;
                }
                literals.add(new Literal(lineNumber, cursor, value.getAsInt()));
                cursor += 1;
                continue;
            }

            var opcode = Opcode.byMnemonic(head, operand != null && operand.startsWith("#"));
            if (opcode.isEmpty()) {
                errors.add("line " + lineNumber + ": unknown instruction '" + head + "'");
                continue;
            }
            if (opcode.get().operandBytes() == 1 && operand == null) {
                errors.add("line " + lineNumber + ": " + head + " needs an operand");
                continue;
            }
            if (opcode.get().operandBytes() == 0 && operand != null) {
                errors.add("line " + lineNumber + ": " + head + " takes no operand");
                continue;
            }
            if (cursor + 1 + opcode.get().operandBytes() > 256) {
                errors.add("line " + lineNumber + ": program does not fit in 256 bytes");
                continue;
            }
            instructions.add(new Instruction(lineNumber, cursor, opcode.get(), operand));
            cursor += 1 + opcode.get().operandBytes();
        }

        if (!errors.isEmpty()) {
            return new Result(new byte[0], List.copyOf(errors));
        }

        byte[] bytes = new byte[256];
        for (Literal literal : literals) {
            bytes[literal.address()] = (byte) literal.value();
        }
        for (Instruction instruction : instructions) {
            bytes[instruction.address()] = (byte) instruction.opcode().code();
            if (instruction.opcode().operandBytes() == 0) {
                continue;
            }
            OptionalInt value = parseLiteral(instruction.operandText(), labels);
            if (value.isEmpty()) {
                errors.add("line " + instruction.lineNumber() + ": undefined label '"
                        + stripHash(instruction.operandText()) + "'");
                continue;
            }
            int max = maxOperand(instruction.opcode());
            if (value.getAsInt() < 0 || value.getAsInt() > max) {
                errors.add("line " + instruction.lineNumber() + ": operand out of range (0-" + max
                        + "): " + instruction.operandText());
                continue;
            }
            bytes[instruction.address() + 1] = (byte) value.getAsInt();
        }

        if (!errors.isEmpty()) {
            return new Result(new byte[0], List.copyOf(errors));
        }
        return new Result(bytes, List.of());
    }

    /**
     * {@code OUT}'s operand picks one of two ports and {@code IN}'s the one input port that
     * exists — neither is an ordinary 0-255 byte the way every other operand is, and treating them
     * as one would let {@code OUT 7} assemble into a port that does not exist on the housing.
     */
    private static int maxOperand(Opcode opcode) {
        return switch (opcode) {
            case OUT -> Machine.OUTPUT_PORTS - 1;
            case IN -> 0;
            default -> 255;
        };
    }

    private static String stripComment(String line) {
        int at = line.indexOf(';');
        return at < 0 ? line : line.substring(0, at);
    }

    private static String stripHash(String operand) {
        return operand.startsWith("#") ? operand.substring(1) : operand;
    }

    /** A {@code #}-prefixed or bare number (decimal, {@code 0x} hex, {@code 0b} binary), or a label. */
    private static OptionalInt parseLiteral(String token, Map<String, Integer> labels) {
        if (token == null) {
            return OptionalInt.empty();
        }
        String text = stripHash(token.trim());
        if (text.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            if (text.length() > 2 && text.substring(0, 2).equalsIgnoreCase("0x")) {
                return OptionalInt.of(Integer.parseInt(text.substring(2), 16));
            }
            if (text.length() > 2 && text.substring(0, 2).equalsIgnoreCase("0b")) {
                return OptionalInt.of(Integer.parseInt(text.substring(2), 2));
            }
            return OptionalInt.of(Integer.parseInt(text));
        } catch (NumberFormatException notANumber) {
            Integer address = labels.get(text);
            return address == null ? OptionalInt.empty() : OptionalInt.of(address);
        }
    }

    private Assembler() { }
}
