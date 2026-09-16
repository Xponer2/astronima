package play.xponer.astronima.sim.processor;

import java.util.Optional;

/**
 * The whole of {@code design/processor.md} §3's instruction table, as one enum — byte value,
 * operand shape, and which of the two forms a shared mnemonic ({@code LDA}/{@code ADD}/{@code SUB})
 * means. Nothing here executes anything; {@link Machine} does that, reading this table's own
 * {@link #code()} back off a program byte — so an opcode's meaning is defined exactly once, in one
 * place, for both the {@link Assembler} that writes it and the {@link Machine} that reads it.
 *
 * <p>Minecraft-free (rule 1).
 */
public enum Opcode {

    NOP("NOP", 0x00, 0, false),
    LDA_IMM("LDA", 0x01, 1, true),
    LDA_MEM("LDA", 0x02, 1, false),
    STA("STA", 0x03, 1, false),
    ADD_IMM("ADD", 0x04, 1, true),
    ADD_MEM("ADD", 0x05, 1, false),
    SUB_IMM("SUB", 0x06, 1, true),
    SUB_MEM("SUB", 0x07, 1, false),
    AND("AND", 0x08, 1, false),
    OR("OR", 0x09, 1, false),
    XOR("XOR", 0x0A, 1, false),
    NOT("NOT", 0x0B, 0, false),
    INC("INC", 0x0C, 0, false),
    DEC("DEC", 0x0D, 0, false),
    SHL("SHL", 0x0E, 0, false),
    SHR("SHR", 0x0F, 0, false),
    JMP("JMP", 0x10, 1, false),
    JZ("JZ", 0x11, 1, false),
    JNZ("JNZ", 0x12, 1, false),
    JC("JC", 0x13, 1, false),
    JNC("JNC", 0x14, 1, false),
    OUT("OUT", 0x15, 1, false),
    IN("IN", 0x16, 1, false),
    STROBE("STROBE", 0x17, 0, false),
    HLT("HLT", 0xFF, 0, false);

    private final String mnemonic;
    private final int code;
    private final int operandBytes;
    private final boolean immediate;

    Opcode(String mnemonic, int code, int operandBytes, boolean immediate) {
        this.mnemonic = mnemonic;
        this.code = code;
        this.operandBytes = operandBytes;
        this.immediate = immediate;
    }

    /** The written mnemonic — shared by {@code LDA_IMM}/{@code LDA_MEM} and the two other pairs. */
    public String mnemonic() {
        return mnemonic;
    }

    /** The one byte a program stores for this instruction. */
    public int code() {
        return code;
    }

    /** How many operand bytes follow the opcode byte: zero or one, never more (§3). */
    public int operandBytes() {
        return operandBytes;
    }

    /** True for the {@code #n} form of {@code LDA}/{@code ADD}/{@code SUB}; meaningless elsewhere. */
    public boolean immediate() {
        return immediate;
    }

    /** The opcode a program byte decodes to, or empty for a byte none of the twenty-four claim. */
    public static Optional<Opcode> byCode(int code) {
        for (Opcode opcode : values()) {
            if (opcode.code == code) {
                return Optional.of(opcode);
            }
        }
        return Optional.empty();
    }

    /**
     * The opcode a written mnemonic means, disambiguated by whether its operand carried a
     * {@code #} — the same distinction {@code LDA #5} vs {@code LDA 5} makes on paper. Mnemonics
     * with only one form ({@code JMP}, {@code OUT}, and so on) answer to either flag identically,
     * so a caller does not have to know in advance which mnemonics are the ambiguous ones.
     */
    public static Optional<Opcode> byMnemonic(String mnemonic, boolean immediateOperand) {
        Opcode fallback = null;
        for (Opcode opcode : values()) {
            if (!opcode.mnemonic.equals(mnemonic)) {
                continue;
            }
            if (opcode.immediate == immediateOperand) {
                return Optional.of(opcode);
            }
            fallback = opcode;
        }
        // A mnemonic with only one form (say OUT) never sets `immediate` either way; the operand's
        // own `#` is meaningless for it rather than a real ambiguity, so the single form answers.
        return Optional.ofNullable(fallback);
    }
}
