package play.xponer.astronima.wire;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.wire.PixelMask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every trace in one chunk.
 *
 * <p>Attached to the chunk rather than kept in a level-wide table, and that choice is the reason
 * wiring behaves: a chunk already knows how to save itself, how to be sent to a client, and — the
 * part that matters — how to <em>go away</em>. Wire in unloaded terrain unloads with it, instead
 * of accumulating in a map that only ever grows.
 *
 * <p>{@code Atmosphere} is level-wide {@code SavedData} because a room is not confined to a
 * chunk. A trace is: it lies on a surface at a known position, so the chunk is exactly the right
 * granularity.
 *
 * <p>Replaced on write rather than mutated, so the renderer reading on the client thread can
 * never see a half-applied edit.
 */
public final class WireChunk {

    private final List<WireTrace> traces;
    private final List<WirePart> parts;

    public WireChunk() {
        this(List.of(), List.of());
    }

    public WireChunk(List<WireTrace> traces) {
        this(traces, List.of());
    }

    public WireChunk(List<WireTrace> traces, List<WirePart> parts) {
        this.traces = new ArrayList<>(traces);
        this.parts = new ArrayList<>(parts);
    }

    /**
     * Parts are optional in the save, so every world written before they existed still loads.
     *
     * <p>They live in the same attachment as the traces on purpose: a part and the wire it is
     * soldered to are one installation, and splitting them across two stores would be two things
     * to save, two to sync, and two chances for a chunk to come back with half of a circuit.
     */
    public static final MapCodec<WireChunk> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    WireTrace.CODEC.listOf().optionalFieldOf("traces", List.of())
                            .forGetter(WireChunk::traces),
                    WirePart.CODEC.listOf().optionalFieldOf("parts", List.of())
                            .forGetter(WireChunk::parts)
            ).apply(instance, WireChunk::new));

    /**
     * Hand-written rather than derived, for the reason {@code AirlockTerminalPayload} gives: a
     * stream codec assembled out of guessed-at constants is a runtime failure, and this one is
     * spelled out so every field's wire form is visibly matched at both ends.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, WireChunk> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, chunk) -> {
                        buffer.writeVarInt(chunk.traces.size());
                        for (WireTrace trace : chunk.traces) {
                            buffer.writeBlockPos(trace.cell());
                            buffer.writeVarInt(trace.face().ordinal());
                            buffer.writeVarInt(trace.colour().ordinal());
                            buffer.writeVarInt(trace.material().ordinal());
                            buffer.writeVarInt(trace.gauge().ordinal());
                            // Quantised, and that is deliberate: a wire warming through a
                            // hundred kelvin would otherwise resync its chunk on every tick for
                            // a colour nobody can tell apart. Ten-kelvin steps are finer than
                            // the eye and a hundredth of the traffic.
                            buffer.writeVarInt((int) Math.round(trace.temperatureK() / 10.0));
                            for (long word : trace.mask().words()) {
                                buffer.writeLong(word);
                            }
                        }
                        buffer.writeVarInt(chunk.parts.size());
                        for (WirePart part : chunk.parts) {
                            buffer.writeBlockPos(part.cell());
                            buffer.writeVarInt(part.face().ordinal());
                            buffer.writeVarInt(part.u());
                            buffer.writeVarInt(part.v());
                            buffer.writeVarInt(part.rotation());
                            buffer.writeVarInt(part.type().ordinal());
                            buffer.writeVarInt(part.outputs());
                            buffer.writeBoolean(part.held());
                            play.xponer.astronima.item.CircuitPlate.STREAM_CODEC
                                    .encode(buffer, part.circuit());
                            buffer.writeVarInt(part.gateState().size());
                            for (long word : part.gateState()) {
                                buffer.writeLong(word);
                            }
                            buffer.writeUtf(part.name());
                            buffer.writeLong(part.memory());
                            buffer.writeUtf(part.program());
                            buffer.writeVarInt(part.machine().size());
                            for (long word : part.machine()) {
                                buffer.writeLong(word);
                            }
                        }
                    },
                    buffer -> {
                        int count = buffer.readVarInt();
                        List<WireTrace> read = new ArrayList<>(count);
                        Direction[] faces = Direction.values();
                        DyeColor[] colours = DyeColor.values();
                        ConductorMaterial[] materials = ConductorMaterial.values();
                        play.xponer.astronima.sim.circuit.WireGauge[] gauges =
                                play.xponer.astronima.sim.circuit.WireGauge.values();
                        for (int i = 0; i < count; i++) {
                            BlockPos cell = buffer.readBlockPos();
                            // Ordinals off the wire are untrusted input; a malformed one must
                            // not take down the chunk it arrived with.
                            Direction face = faces[Math.floorMod(buffer.readVarInt(), faces.length)];
                            DyeColor colour =
                                    colours[Math.floorMod(buffer.readVarInt(), colours.length)];
                            ConductorMaterial material =
                                    materials[Math.floorMod(buffer.readVarInt(), materials.length)];
                            play.xponer.astronima.sim.circuit.WireGauge gauge = gauges[
                                    Math.floorMod(buffer.readVarInt(), gauges.length)];
                            double temperature = buffer.readVarInt() * 10.0;
                            long[] words = new long[PixelMask.WORDS];
                            for (int word = 0; word < PixelMask.WORDS; word++) {
                                words[word] = buffer.readLong();
                            }
                            read.add(new WireTrace(cell, face, colour, material, gauge,
                                    temperature, PixelMask.of(words)));
                        }
                        // The release deadline is deliberately not sent: it is a server clock
                        // reading, and a client that knew it would be the only thing in the mod
                        // counting down a tick timer it cannot verify. `held` is what gets drawn.
                        int partCount = buffer.readVarInt();
                        List<WirePart> readParts = new ArrayList<>(partCount);
                        play.xponer.astronima.sim.logic.PartType[] types =
                                play.xponer.astronima.sim.logic.PartType.values();
                        for (int i = 0; i < partCount; i++) {
                            BlockPos cell = buffer.readBlockPos();
                            Direction face = faces[Math.floorMod(buffer.readVarInt(),
                                    faces.length)];
                            int u = buffer.readVarInt();
                            int v = buffer.readVarInt();
                            int rotation = buffer.readVarInt();
                            var type = types[Math.floorMod(buffer.readVarInt(), types.length)];
                            int outputs = buffer.readVarInt();
                            boolean held = buffer.readBoolean();
                            var circuit = play.xponer.astronima.item.CircuitPlate.STREAM_CODEC
                                    .decode(buffer);
                            int gateWordCount = buffer.readVarInt();
                            List<Long> gateState = new ArrayList<>(gateWordCount);
                            for (int word = 0; word < gateWordCount; word++) {
                                gateState.add(buffer.readLong());
                            }
                            String name = buffer.readUtf();
                            long memory = buffer.readLong();
                            String program = buffer.readUtf();
                            int machineWordCount = buffer.readVarInt();
                            List<Long> machine = new ArrayList<>(machineWordCount);
                            for (int word = 0; word < machineWordCount; word++) {
                                machine.add(buffer.readLong());
                            }
                            readParts.add(new WirePart(cell, face, u, v, rotation, type,
                                    outputs, held, 0L, circuit, gateState, name, memory, program, machine));
                        }
                        return new WireChunk(read, readParts);
                    });

    public List<WireTrace> traces() {
        return Collections.unmodifiableList(traces);
    }

    public List<WirePart> parts() {
        return Collections.unmodifiableList(parts);
    }

    public boolean isEmpty() {
        return traces.isEmpty() && parts.isEmpty();
    }

    public boolean hasParts() {
        return !parts.isEmpty();
    }

    /** The trace of that colour on that surface, if there is one. */
    public Optional<WireTrace> trace(BlockPos cell, Direction face, DyeColor colour) {
        for (WireTrace trace : traces) {
            if (trace.sameTraceAs(cell, face, colour)) {
                return Optional.of(trace);
            }
        }
        return Optional.empty();
    }

    /** Every trace on that surface, whatever colour — the bundle. */
    public List<WireTrace> allOn(BlockPos cell, Direction face) {
        List<WireTrace> found = new ArrayList<>();
        for (WireTrace trace : traces) {
            if (trace.cell().equals(cell) && trace.face() == face) {
                found.add(trace);
            }
        }
        return found;
    }

    /**
     * Every trace in the chunk, bundled by the surface it sits on — one pass over the whole
     * list rather than one {@link #allOn} scan per trace.
     *
     * <p><strong>Exists specifically for a caller that needs every trace's own bundle</strong>,
     * not just one — {@code WireRenderer} used to call {@link #allOn} once per trace while
     * walking every trace in the chunk, which is <em>T</em> scans of up to <em>T</em> traces
     * each: quadratic in the chunk's own trace count. A base with a few dozen traces on a face
     * never noticed; one with hundreds did, which is exactly the shape "когда много проводов
     * начинает нереально сильно лагать" (once there's a lot of wire, it starts lagging
     * unbelievably badly) describes. This does the identical grouping once, in one pass, and a
     * caller then looks its own trace's bundle up in the result rather than rescanning for it.
     */
    public Map<WirePower.Face, List<WireTrace>> groupedBySurface() {
        Map<WirePower.Face, List<WireTrace>> grouped = new HashMap<>();
        for (WireTrace trace : traces) {
            grouped.computeIfAbsent(new WirePower.Face(trace.cell(), trace.face()), key -> new ArrayList<>())
                    .add(trace);
        }
        return grouped;
    }

    /**
     * Puts a trace in, replacing the one it supersedes.
     *
     * <p>An empty trace is dropped rather than stored: a face nobody has wire on should cost
     * nothing, and keeping empty records would make a chunk grow every time a player laid and
     * pulled a run.
     */
    public WireChunk with(WireTrace trace) {
        List<WireTrace> next = new ArrayList<>(traces.size() + 1);
        for (WireTrace existing : traces) {
            if (!existing.sameTraceAs(trace.cell(), trace.face(), trace.colour())) {
                next.add(existing);
            }
        }
        if (!trace.isEmpty()) {
            next.add(trace);
        }
        return new WireChunk(next, parts);
    }

    /**
     * Strips one surface of <em>conductor</em> — every colour on it.
     *
     * <p>Parts stay. Cutting wire off a wall and unbolting the components from it are two
     * different jobs, and a cutter that took the gates with the wire would make trimming a corner
     * a way to lose a circuit.
     */
    public WireChunk withoutAll(BlockPos cell, Direction face) {
        List<WireTrace> next = new ArrayList<>(traces.size());
        for (WireTrace existing : traces) {
            if (!(existing.cell().equals(cell) && existing.face() == face)) {
                next.add(existing);
            }
        }
        return new WireChunk(next, parts);
    }

    // ---- parts -------------------------------------------------------------------

    /** Every part on that surface. */
    public List<WirePart> partsOn(BlockPos cell, Direction face) {
        List<WirePart> found = new ArrayList<>();
        for (WirePart part : parts) {
            if (part.isOn(cell, face)) {
                found.add(part);
            }
        }
        return found;
    }

    /** Whichever part covers that pixel, if any. */
    public Optional<WirePart> partAt(BlockPos cell, Direction face, int u, int v) {
        for (WirePart part : parts) {
            if (part.isOn(cell, face) && part.covers(u, v)) {
                return Optional.of(part);
            }
        }
        return Optional.empty();
    }

    /** Puts a part in, replacing whatever occupied that exact slot. */
    public WireChunk with(WirePart part) {
        List<WirePart> next = new ArrayList<>(parts.size() + 1);
        for (WirePart existing : parts) {
            if (!existing.sameSlotAs(part)) {
                next.add(existing);
            }
        }
        next.add(part);
        return new WireChunk(traces, next);
    }

    public WireChunk without(WirePart part) {
        List<WirePart> next = new ArrayList<>(parts.size());
        for (WirePart existing : parts) {
            if (!existing.sameSlotAs(part)) {
                next.add(existing);
            }
        }
        return new WireChunk(traces, next);
    }

    @Override
    public String toString() {
        return "WireChunk[" + traces.size() + " traces, " + parts.size() + " parts]";
    }
}
