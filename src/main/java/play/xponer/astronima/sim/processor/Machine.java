package play.xponer.astronima.sim.processor;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code PROCESSOR} chip's own insides: 256 bytes of unified memory, {@code A}, {@code PC},
 * the {@code Z}/{@code C} flags, and the two latched 4-bit output ports — everything
 * {@code design/processor.md} §2.1 puts on the die. {@code design/processor.md} §4 is the design
 * this follows for how a run is sliced.
 *
 * <h2>Immutable, and {@link #run} returns the next state rather than mutating this one</h2>
 * The same shape {@code MemoryLogic.Result} already uses for a much smaller machine: a pure
 * function from "state coming in" to "state going out" is what a gametest can assert against
 * directly, and what {@code WireTicker} can store straight onto a {@code WirePart} without a
 * mutable object living on the side that a save could miss.
 *
 * <h2>Undefined opcodes halt, on purpose (§3)</h2>
 * A program byte none of {@link Opcode}'s twenty-four claim stops the machine with
 * {@link HaltCause#UNDEFINED_OPCODE} rather than being skipped or treated as {@code NOP} — the
 * alternative is a program that silently does something other than what was written, which is
 * exactly the failure shape rule 63's whole history already warned against.
 *
 * <p>Minecraft-free (rule 1).
 */
public final class Machine {

    public static final int MEMORY_SIZE = 256;
    public static final int OUTPUT_PORTS = 2;

    /** Why the machine is not currently executing — {@link #halted()} is {@code cause != RUNNING}. */
    public enum HaltCause { RUNNING, INSTRUCTION_HLT, UNDEFINED_OPCODE }

    private final byte[] memory;
    private final int a;
    private final int pc;
    private final boolean zero;
    private final boolean carry;
    private final HaltCause haltCause;
    private final int port0;
    private final int port1;

    /** A freshly placed, unprogrammed chip — every cell zero, registers zero, not halted. */
    public Machine() {
        this(new byte[MEMORY_SIZE], 0, 0, false, false, HaltCause.RUNNING, 0, 0);
    }

    private Machine(byte[] memory, int a, int pc, boolean zero, boolean carry, HaltCause haltCause,
                    int port0, int port1) {
        if (memory.length != MEMORY_SIZE) {
            throw new IllegalArgumentException("memory must be exactly " + MEMORY_SIZE + " bytes");
        }
        this.memory = memory;
        this.a = a & 0xFF;
        this.pc = pc & 0xFF;
        this.zero = zero;
        this.carry = carry;
        this.haltCause = haltCause;
        this.port0 = port0 & 0xF;
        this.port1 = port1 & 0xF;
    }

    /**
     * A fresh machine with {@code program} loaded starting at address 0 — what saving a new
     * assembly in the code editor does. Registers, flags and any halt all reset, because a new
     * program is not a continuation of whatever the old one was doing.
     */
    public static Machine loaded(byte[] program) {
        byte[] mem = new byte[MEMORY_SIZE];
        System.arraycopy(program, 0, mem, 0, Math.min(program.length, MEMORY_SIZE));
        return new Machine(mem, 0, 0, false, false, HaltCause.RUNNING, 0, 0);
    }

    /**
     * What the {@code run} pad's rising edge does (§2.2): {@code PC} resets to 0 and any halt
     * clears, so the program starts over from the top — but memory, {@code A} and the flags are
     * left exactly as they stood, the same as the spec's own wording draws the line.
     */
    public Machine restarted() {
        return new Machine(memory.clone(), a, 0, zero, carry, HaltCause.RUNNING, port0, port1);
    }

    public int a() {
        return a;
    }

    public int pc() {
        return pc;
    }

    public boolean zero() {
        return zero;
    }

    public boolean carry() {
        return carry;
    }

    public HaltCause haltCause() {
        return haltCause;
    }

    public boolean halted() {
        return haltCause != HaltCause.RUNNING;
    }

    /** The byte {@link #pc()} points at right now — the one an undefined-opcode halt stopped on. */
    public int byteAtPc() {
        return memory[pc] & 0xFF;
    }

    public int outPort(int index) {
        return index == 0 ? port0 : port1;
    }

    public int memoryAt(int address) {
        return memory[address & 0xFF] & 0xFF;
    }

    public byte[] memorySnapshot() {
        return memory.clone();
    }

    /** How many longs {@link #pack()} produces — thirty-two for memory, one for everything else. */
    public static final int PACKED_WORDS = MEMORY_SIZE / Long.BYTES + 1;

    /**
     * The whole machine as {@code design/processor.md} §5.3 says {@code WirePart.machine} stores
     * it: the 256 bytes packed eight to a word, then one more word carrying every register, flag,
     * the halt cause and both ports — the same "pack the array, spend one more word on everything
     * else" shape {@code CompiledCircuit}'s own bitset packing already uses.
     */
    public List<Long> pack() {
        List<Long> words = new ArrayList<>(PACKED_WORDS);
        for (int word = 0; word < MEMORY_SIZE / Long.BYTES; word++) {
            long packed = 0L;
            for (int b = 0; b < Long.BYTES; b++) {
                packed |= (long) (memory[word * Long.BYTES + b] & 0xFF) << (b * 8);
            }
            words.add(packed);
        }
        long registers = (long) a
                | ((long) pc << 8)
                | ((zero ? 1L : 0L) << 16)
                | ((carry ? 1L : 0L) << 17)
                | ((long) haltCause.ordinal() << 18)
                | ((long) port0 << 20)
                | ((long) port1 << 24);
        words.add(registers);
        return List.copyOf(words);
    }

    /**
     * The inverse of {@link #pack()}. Anything other than exactly {@link #PACKED_WORDS} longs is
     * untrusted or pre-{@code PROCESSOR} save data rather than a real machine, and reads as a
     * fresh, unprogrammed chip — the same "malformed reads as blank rather than throwing" rule
     * {@code CircuitPlate.STREAM_CODEC} already keeps for exactly this reason.
     */
    public static Machine unpack(List<Long> packed) {
        if (packed.size() != PACKED_WORDS) {
            return new Machine();
        }
        byte[] mem = new byte[MEMORY_SIZE];
        for (int word = 0; word < MEMORY_SIZE / Long.BYTES; word++) {
            long value = packed.get(word);
            for (int b = 0; b < Long.BYTES; b++) {
                mem[word * Long.BYTES + b] = (byte) ((value >>> (b * 8)) & 0xFF);
            }
        }
        long registers = packed.get(MEMORY_SIZE / Long.BYTES);
        int a = (int) (registers & 0xFF);
        int pc = (int) ((registers >>> 8) & 0xFF);
        boolean zero = ((registers >>> 16) & 1) != 0;
        boolean carry = ((registers >>> 17) & 1) != 0;
        HaltCause[] causes = HaltCause.values();
        HaltCause cause = causes[Math.floorMod((int) (registers >>> 18) & 0x3, causes.length)];
        int port0 = (int) ((registers >>> 20) & 0xF);
        int port1 = (int) ((registers >>> 24) & 0xF);
        return new Machine(mem, a, pc, zero, carry, cause, port0, port1);
    }

    /**
     * @param machine the machine as it stood when this slice ended
     * @param strobed whether {@code STROBE} executed during this slice — {@code WireTicker}'s cue
     *                to publish the port pads and pulse {@code stb} for this refresh
     */
    public record RunResult(Machine machine, boolean strobed) { }

    /**
     * Executes up to {@code maxInstructions} against a private copy of this machine's own memory —
     * this instance is never mutated — stopping early on {@code STROBE}, a halt, or running out of
     * budget. Running out of budget with no {@code STROBE} is not a fault: it is the runaway guard
     * (§4) that keeps an infinite loop with no I/O from spending more than one refresh's worth of
     * the server's own time, ever.
     *
     * @param inputPort what {@code IN 0} reads — the {@code i0..i3} pads, sampled once per slice
     */
    public RunResult run(int maxInstructions, int inputPort) {
        if (halted()) {
            return new RunResult(this, false);
        }
        byte[] mem = memory.clone();
        int curA = a;
        int curPc = pc;
        boolean curZero = zero;
        boolean curCarry = carry;
        int curPort0 = port0;
        int curPort1 = port1;
        HaltCause cause = HaltCause.RUNNING;
        boolean strobed = false;
        int input = inputPort & 0xF;

        for (int step = 0; step < maxInstructions; step++) {
            int opcodeByte = mem[curPc] & 0xFF;
            var found = Opcode.byCode(opcodeByte);
            if (found.isEmpty()) {
                cause = HaltCause.UNDEFINED_OPCODE;
                break;
            }
            Opcode opcode = found.get();
            int operand = opcode.operandBytes() == 1 ? mem[(curPc + 1) & 0xFF] & 0xFF : 0;
            curPc = (curPc + 1 + opcode.operandBytes()) & 0xFF;

            switch (opcode) {
                case NOP -> { }
                case LDA_IMM -> curA = operand;
                case LDA_MEM -> curA = mem[operand] & 0xFF;
                case STA -> mem[operand] = (byte) curA;
                case ADD_IMM, ADD_MEM -> {
                    int value = opcode == Opcode.ADD_IMM ? operand : (mem[operand] & 0xFF);
                    int sum = curA + value;
                    curCarry = sum > 0xFF;
                    curA = sum & 0xFF;
                    curZero = curA == 0;
                }
                case SUB_IMM, SUB_MEM -> {
                    int value = opcode == Opcode.SUB_IMM ? operand : (mem[operand] & 0xFF);
                    curCarry = curA < value;
                    curA = (curA - value) & 0xFF;
                    curZero = curA == 0;
                }
                case AND -> {
                    curA &= (mem[operand] & 0xFF);
                    curZero = curA == 0;
                }
                case OR -> {
                    curA |= (mem[operand] & 0xFF);
                    curZero = curA == 0;
                }
                case XOR -> {
                    curA ^= (mem[operand] & 0xFF);
                    curZero = curA == 0;
                }
                case NOT -> {
                    curA = (~curA) & 0xFF;
                    curZero = curA == 0;
                }
                case INC -> {
                    curA = (curA + 1) & 0xFF;
                    curCarry = curA == 0;
                    curZero = curA == 0;
                }
                case DEC -> {
                    curCarry = curA == 0;
                    curA = (curA - 1) & 0xFF;
                    curZero = curA == 0;
                }
                case SHL -> {
                    curCarry = (curA & 0x80) != 0;
                    curA = (curA << 1) & 0xFF;
                    curZero = curA == 0;
                }
                case SHR -> {
                    curCarry = (curA & 0x01) != 0;
                    curA = curA >>> 1;
                    curZero = curA == 0;
                }
                case JMP -> curPc = operand;
                case JZ -> { if (curZero) { curPc = operand; } }
                case JNZ -> { if (!curZero) { curPc = operand; } }
                case JC -> { if (curCarry) { curPc = operand; } }
                case JNC -> { if (!curCarry) { curPc = operand; } }
                case OUT -> {
                    if (operand == 0) {
                        curPort0 = curA & 0xF;
                    } else {
                        curPort1 = curA & 0xF;
                    }
                }
                case IN -> curA = input;
                case STROBE -> strobed = true;
                case HLT -> cause = HaltCause.INSTRUCTION_HLT;
            }
            if (strobed || cause != HaltCause.RUNNING) {
                break;
            }
        }

        Machine next = new Machine(mem, curA, curPc, curZero, curCarry, cause, curPort0, curPort1);
        return new RunResult(next, strobed);
    }
}
