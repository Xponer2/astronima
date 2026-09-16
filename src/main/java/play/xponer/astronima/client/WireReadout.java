package play.xponer.astronima.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.client.render.WireHighlight;
import play.xponer.astronima.item.WireCoilItem;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.circuit.Cooling;
import play.xponer.astronima.sim.circuit.Sag;
import play.xponer.astronima.sim.circuit.WireGauge;
import play.xponer.astronima.sim.circuit.Conductor;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.circuit.Delivery;
import play.xponer.astronima.sim.thermal.HeatBalance;
import play.xponer.astronima.sim.wire.FaceBasis;
import play.xponer.astronima.sim.wire.PixelGeometry;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WireTrace;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What a wire says about itself — to somebody wearing the goggles.
 *
 * <p>Rule 9 asks that a block a player acts on show its own state, and prefers a gauge on the
 * block. <strong>A trace has no block.</strong> One pixel of conductor cannot carry a dial, and
 * the numbers that matter about it — what it is drawn from, how long the circuit really is, what
 * fraction of a machine's draw it would waste — are invisible by construction. So the instrument
 * goes on the engineer rather than on the wire, which is also where a real multimeter lives.
 *
 * <p><strong>Gated on the goggles deliberately.</strong> Without them a wire is a coloured line
 * and the player has to reason about their own layout; with them the layout is measurable. That
 * is a real progression — the difference between wiring by eye and wiring with an instrument —
 * and it makes the goggles worth crafting rather than a convenience toggle.
 */
public final class WireReadout {
    /** Room a line of text has in the goggles panel. */
    private static final int TEXT_WIDTH = 150;


    /** How far a circuit is followed for the readout. Bounded; this runs every frame. */
    private static final int LIMIT = 4_096;

    private static final double REACH = 6.0;

    private static WirePixel lastTarget;
    private static Panel lastPanel;

    /** What the goggles found, cached against the pixel that produced it. */
    private record Panel(DyeColor colour, ConductorMaterial metal, WireGauge gauge, int pixels,
                         double ohms, double hottestK, boolean truncated) {

        double metres() {
            return pixels / (double) FaceBasis.GRID;
        }
    }

    /**
     * What the goggles' readout is worth, and where it likes to stand.
     *
     * <p>Lower priority than the block readout on purpose: when a player is stood in front of a
     * machine with a run on it, <em>what is this machine doing</em> is the more urgent question.
     * Its second choice is nearly as cheap as its first, which is what lets it slide aside rather
     * than disappear — the behaviour that was asked for.
     */
    /** Asks the module where this panel goes, or null when it is on another page. */
    private static play.xponer.astronima.client.hud.HudPanels.Seat seat(
            GuiGraphicsExtractor graphics, String id, int width, int height) {
        return play.xponer.astronima.client.hud.HudPanels.place(id, width, height,
                play.xponer.astronima.client.hud.HudCosts.GOGGLES_PRIORITY,
                play.xponer.astronima.client.hud.HudCosts.GOGGLES,
                graphics.guiWidth(), graphics.guiHeight(), System.currentTimeMillis() / 1000.0);
    }

    /** True when the player is wearing the goggles in their curio slot. */
    public static boolean wearingGoggles(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }
        return CuriosApi.getCuriosInventory(minecraft.player)
                .map(inventory -> inventory.isEquipped(ModItems.DIAGNOSTIC_GOGGLES.get()))
                .orElse(false);
    }

    /**
     * Draws the panel, or nothing.
     *
     * <p>Returns whether it drew, so {@code LookAtReadout} can stand down: a wire and the block
     * behind it are two answers to one question, and showing both would be noise.
     */
    public static boolean render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }
        // The coil answers first, and without the goggles: what a leg will cost is the tool
        // talking about itself, not an instrument reading the world. Finding out you were three
        // rods short *after* clicking is the kind of thing that makes a tool feel hostile.
        if (drawPendingLeg(graphics, minecraft)) {
            return true;
        }
        if (!wearingGoggles(minecraft)) {
            return false;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        // A part first, because a part is the thing under the crosshair — the wire merely arrives
        // at it. Pointing at a gate and being told about the trace running past it was the
        // instrument answering a question nobody asked.
        if (drawPart(graphics, minecraft, hit)) {
            return true;
        }
        // Contamination before terminals: a dirty surface is a thing you must not touch, and a
        // terminal listing is a thing you can read at your leisure afterwards.
        if (drawContamination(graphics, minecraft, hit)) {
            return true;
        }
        // The block's own terminals, if it declares any, before the generic wire panel — a
        // terminal that happens to have a wire landed on it used to hide behind the wire's own
        // material/gauge reading, which is backwards from the question a player pointing at a
        // machine actually has ("did I wire this correctly", not "what is this wire made of").
        // The terminal listing already reports each pad's own wired/live status, which subsumes
        // "is there a wire here" — strictly more specific than the wire panel for this exact spot.
        if (drawTerminals(graphics, minecraft, hit)) {
            return true;
        }
        // No terminals on this block — a plain wire run through open space, or one crossing a
        // block's face without landing on a pad. Report the wire itself.
        var aim = play.xponer.astronima.wire.WireAim.at(minecraft.level, hit, true);
        Panel panel = panelFor(minecraft.level, aim.pixel());
        if (panel != null) {
            WireHighlight.lookingAt(aim.pixel());
            draw(graphics, minecraft, panel);
            return true;
        }
        return false;
    }

    /**
     * The part under the crosshair, named, with every pad and what is on it.
     *
     * <p><strong>An unpaid promise from {@code design/wire-parts.md} §4</strong> — <em>"the goggles
     * name it: AND gate — A live, B dead, out dead"</em>. Until now they reported the terminals of
     * <em>blocks</em> and knew nothing about the parts that replaced them, so pointing at a gate,
     * a switch or a fuse got you either nothing or a lecture about a trace running past it.
     *
     * <p>It matters most for the two questions a player cannot answer by looking. <em>Which pad did
     * I actually wire?</em> — a run one pixel off a pad draws as a continuous line. And <em>has this
     * fuse gone?</em> — which is the whole reason a fuse has a window in it.
     *
     * <p><strong>Everything here is read off state the server already published.</strong> The
     * client has no {@code ServerLevel} and cannot ask a part what it is doing; what it has is the
     * part's own synced record and {@code WireLive}, which already knows which circuits are
     * carrying. That constraint is the reason a part stores its outputs at all.
     */
    private static boolean drawPart(GuiGraphicsExtractor graphics, Minecraft minecraft,
                                    BlockHitResult hit) {
        var part = play.xponer.astronima.wire.PartInteraction.under(minecraft.level, hit);
        if (part == null) {
            return false;
        }
        var live = play.xponer.astronima.client.render.WireLive.current(minecraft.level);
        List<String> lines = new java.util.ArrayList<>();
        lines.add(new net.minecraft.world.item.ItemStack(
                play.xponer.astronima.registry.ModItems.part(part.type()))
                .getHoverName().getString());
        lines.add(state(part));

        for (var pad : part.type().pads()) {
            var at = part.padPixel(pad);
            boolean wired = play.xponer.astronima.wire.WireAim.hasAnyWire(minecraft.level, at);
            String what;
            if (!wired) {
                what = "no wire";
            } else if (pad.drives()) {
                what = part.driving(pad) ? "wired, DRIVING" : "wired, idle";
            } else {
                // What is actually arriving, which is the half a player cannot see: a line that
                // reaches the pad and carries nothing looks exactly like one that carries.
                what = live.isCarrying(at) ? "wired, LIVE" : "wired, dead";
            }
            lines.add(String.format(Locale.ROOT, "  %-5s %-4s %s", pad.label(),
                    pad.drives() ? "out" : part.type().isBridge() ? "line" : "in", what));
        }
        drawLines(graphics, minecraft, lines);
        return true;
    }

    /** The one line that says what this part is doing, in its own terms. */
    private static String state(play.xponer.astronima.wire.WirePart part) {
        return switch (part.type()) {
            case SWITCH -> part.held() ? "closed" : "open";
            case BUTTON -> part.held() ? "pressed" : "released";
            case CLOCK -> part.held() ? "high" : "low";
            case RAM -> "addressed cell reads " + (part.outputs() & 0xF);
            case FRAMEBUFFER -> "addressed cell reads " + (part.outputs() & 0xF)
                    + ", " + Long.bitCount(part.memory()) + " of 64 pixels lit";
            case PROCESSOR -> processorState(part);
            case FUSE -> part.held()
                    ? String.format(Locale.ROOT, "BLOWN - replace it (rated %.1f A here)",
                            rating(part))
                    : String.format(Locale.ROOT, "intact, rated %.1f A on this run", rating(part));
            // "off" without saying whether a hand or an overload did it, on purpose: a breaker
            // that is off is off, and the difference is a property of the circuit rather than of
            // the lever. If the fault is still there, throwing it on answers the question in one
            // tick — which is a better instrument than a label that might be stale.
            case BREAKER -> part.held()
                    ? String.format(Locale.ROOT, "OFF - nothing downstream (trips at %.1f A here)",
                            rating(part))
                    : String.format(Locale.ROOT, "on, trips at %.1f A on this run", rating(part));
            case PLATE -> expressionOf(part);
            default -> part.drivingAnything() ? "driving" : "not driving";
        };
    }

    /**
     * The one line a processor's whole running state fits in: the registers, the flags, whether
     * it is executing at all, and — reading {@link play.xponer.astronima.sim.processor.Opcode}
     * back off the byte {@code PC} points at — the mnemonic it is about to run next. Unpacking
     * {@link play.xponer.astronima.sim.processor.Machine} here is fine where it would not be
     * inside {@code PartRenderer}'s own per-pixel loop: this runs once, on demand, when a player
     * is actually looking at the readout, not once per housing pixel every frame.
     */
    private static String processorState(play.xponer.astronima.wire.WirePart part) {
        if (part.program().isEmpty()) {
            return "unprogrammed";
        }
        var machine = play.xponer.astronima.sim.processor.Machine.unpack(part.machine());
        String flags = (machine.zero() ? "Z" : "-") + (machine.carry() ? "C" : "-");
        String next = play.xponer.astronima.sim.processor.Opcode.byCode(machine.byteAtPc())
                .map(play.xponer.astronima.sim.processor.Opcode::mnemonic)
                .orElse(String.format(Locale.ROOT, "0x%02X?", machine.byteAtPc()));
        String running = switch (machine.haltCause()) {
            case RUNNING -> "running";
            case INSTRUCTION_HLT -> "halted (HLT)";
            case UNDEFINED_OPCODE -> String.format(Locale.ROOT,
                    "halted (bad opcode 0x%02X)", machine.byteAtPc());
        };
        return String.format(Locale.ROOT, "A=0x%02X PC=0x%02X [%s] %s, next %s",
                machine.a(), machine.pc(), flags, running, next);
    }

    /**
     * What this protection gives up at, given the conductor it is spliced into.
     *
     * <p>Not a constant any more: a fuse is a deliberately weak length of the same wire, so its
     * rating follows the run's thinnest gauge and worst metal. Read off the trace the part is lying
     * on, because that is all the client has and all it needs.
     */
    private static double rating(play.xponer.astronima.wire.WirePart part) {
        Minecraft minecraft = Minecraft.getInstance();
        var gauge = WireGauge.DEFAULT;
        var metal = ConductorMaterial.IRON;
        if (minecraft.level != null && !part.type().pads().isEmpty()) {
            Panel panel = panelFor(minecraft.level, part.padPixel(part.type().pads().get(0)));
            if (panel != null) {
                gauge = panel.gauge();
                metal = panel.metal();
            }
        }
        return play.xponer.astronima.sim.circuit.Protection.ratingAmps(gauge, metal);
    }

    /** A plate reads back as what it computes, the same sentence its tooltip carries. */
    private static String expressionOf(play.xponer.astronima.wire.WirePart part) {
        var circuit = part.circuit();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < play.xponer.astronima.sim.logic.Circuit.OUTPUTS; i++) {
            var source = circuit.outputs().get(i);
            if (source.isOff()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("  ");
            }
            out.append((char) ('W' + i)).append('=').append(
                    play.xponer.astronima.item.LogicPartItem.describe(circuit, source));
        }
        return out.length() == 0 ? "blank - nothing wired inside" : out.toString();
    }

    /**
     * What the leg you are about to lay would cost, while you are still deciding.
     *
     * <p>Runs the same router the click will run, so the number is the real one rather than a
     * straight-line guess — a route that goes the long way round an obstacle costs what it
     * actually costs, and you can see that before paying for it.
     */
    private static boolean drawPendingLeg(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        var player = minecraft.player;
        var held = player.getMainHandItem();
        if (!(held.getItem() instanceof play.xponer.astronima.item.WireCoilItem)) {
            held = player.getOffhandItem();
        }
        if (!(held.getItem() instanceof play.xponer.astronima.item.WireCoilItem)) {
            return false;
        }
        var anchor = play.xponer.astronima.item.WireCoilItem.anchorOf(held);
        if (anchor == null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        var coil = play.xponer.astronima.item.WireCoilItem.settingOf(held);
        var target = play.xponer.astronima.wire.WireAim.at(minecraft.level, hit, false).pixel();
        var route = play.xponer.astronima.sim.wire.WireRouter.route(
                play.xponer.astronima.item.WireCoilItem.pixelOf(anchor), target,
                play.xponer.astronima.item.WireCoilItem.spaceIn(minecraft.level), coil.mode(),
                play.xponer.astronima.item.WireCoilItem.MAX_LEG_PIXELS).orElse(null);

        List<String> lines = new java.util.ArrayList<>();
        if (route == null) {
            lines.add(coil.mode() == play.xponer.astronima.sim.wire.WireRouter.Mode.STRAIGHT
                    ? "no straight run gets there" : "no route from the end you are holding");
        } else {
            int fresh = 0;
            for (var point : route) {
                if (!play.xponer.astronima.wire.WireAim.hasAnyWire(minecraft.level, point)) {
                    fresh++;
                }
            }
            // Priced at the gauge on the coil: a busbar leg costs ten times what the same
            // route costs in signal wire, and finding that out *after* clicking is what makes a
            // tool feel hostile.
            int rods = play.xponer.astronima.wire.WireStock.itemsFor(fresh, coil.gauge());
            int carried = play.xponer.astronima.wire.WireStock.carried(player, coil.material());
            lines.add(String.format(Locale.ROOT, "%.2f m of %s %s (%s)",
                    route.size() / (double) FaceBasis.GRID,
                    coil.gauge().id(),
                    coil.material().name().toLowerCase(Locale.ROOT),
                    coil.colour().getSerializedName()));
            lines.add(carried >= rods
                    ? String.format(Locale.ROOT, "costs %d - you have %d", rods,
                            carried == Integer.MAX_VALUE ? rods : carried)
                    : String.format(Locale.ROOT, "costs %d - you have %d, SHORT", rods, carried));
        }
        drawLines(graphics, minecraft, lines);
        return true;
    }

    /**
     * Every terminal on the block being looked at: what it is, whether a wire is on it, and
     * whether that wire is live.
     *
     * <p><strong>This is the answer to the last audit.</strong> "I wired it all correctly and it
     * still does not work" was unanswerable, because nothing in the game could be asked. Three
     * facts per stud make every failure legible: a stud with no wire is a miss, a wired stud that
     * is dark is a circuit that is not being driven, and a lit input on a machine that is idle is
     * a problem somewhere else entirely.
     */
    private static boolean drawTerminals(GuiGraphicsExtractor graphics, Minecraft minecraft,
                                         BlockHitResult hit) {
        BlockPos block = hit.getBlockPos();
        var state = minecraft.level.getBlockState(block);
        if (!(state.getBlock() instanceof play.xponer.astronima.wire.Terminated terminated)) {
            return false;
        }
        List<String> lines = new java.util.ArrayList<>();
        lines.add(state.getBlock().getName().getString());
        for (var terminal : terminated.terminals(state)) {
            boolean wired = play.xponer.astronima.wire.WireAim.hasAnyWire(minecraft.level,
                    terminal.pixel(block));
            String kind = switch (terminal.kind()) {
                case POWER -> "pwr";
                case SIGNAL_IN -> "in ";
                case SIGNAL_OUT -> "out";
            };
            String wiring = !wired ? "no wire"
                    : terminal.kind() == play.xponer.astronima.wire.Terminal.Kind.SIGNAL_OUT
                            ? (drivingClient(state) ? "wired, DRIVING" : "wired, idle")
                            : "wired";
            // The label is what makes two amber studs on one machine tellable apart — "working"
            // and "held" are the same colour and want opposite responses.
            lines.add(String.format(Locale.ROOT, "  %s %s %-8s %s",
                    terminal.outward().getName().substring(0, 1).toUpperCase(Locale.ROOT),
                    kind, terminal.label(), wiring));
        }
        drawLines(graphics, minecraft, lines);
        return true;
    }

    private static boolean drivingClient(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof play.xponer.astronima.wire.SignalView view
                && view.isDrivingClient(state);
    }

    private static Panel panelFor(Level level, WirePixel target) {
        if (target.equals(lastTarget)) {
            return lastPanel;
        }
        lastTarget = target;
        lastPanel = measure(level, target);
        return lastPanel;
    }

    /** Walks the circuit under the crosshair and works out what it is. */
    private static Panel measure(Level level, WirePixel target) {
        Optional<WireTrace> here = traceAt(level, target);
        if (here.isEmpty()) {
            return null;
        }
        DyeColor colour = here.get().colour();
        Set<WirePixel> seen = new HashSet<>();
        Deque<WirePixel> frontier = new ArrayDeque<>();
        ConductorMaterial worst = here.get().material();
        WireGauge thinnest = here.get().gauge();
        double hottest = here.get().temperatureK();
        frontier.add(target);
        seen.add(target);
        boolean truncated = false;

        while (!frontier.isEmpty()) {
            if (seen.size() >= LIMIT) {
                truncated = true;
                break;
            }
            WirePixel current = frontier.poll();
            Optional<WireTrace> trace = traceOf(level, current, colour);
            if (trace.isPresent()) {
                if (trace.get().material().resistivity20C() > worst.resistivity20C()) {
                    worst = trace.get().material();
                }
                // A chain is its weakest link in both axes: one spliced pixel of signal wire
                // makes the whole run a signal run, and the meter has to say so rather than
                // quote the gauge of whatever the player happened to point at.
                thinnest = thinnest.thinnerOf(trace.get().gauge());
                // The hottest point, not the one under the crosshair: a run is in trouble where
                // its cooling is worst, and that is rarely where the player is standing.
                hottest = Math.max(hottest, trace.get().temperatureK());
            }
            for (WirePixel candidate : PixelGeometry.neighbours(current)) {
                if (seen.contains(candidate) || traceOf(level, candidate, colour).isEmpty()) {
                    continue;
                }
                seen.add(candidate);
                frontier.add(candidate);
            }
        }
        double metres = seen.size() / (double) FaceBasis.GRID;
        double ohms = thinnest.over(worst, metres).resistance(ConductorMaterial.REFERENCE_K);
        return new Panel(colour, worst, thinnest, seen.size(), ohms, hottest, truncated);
    }

    private static Optional<WireTrace> traceAt(Level level, WirePixel pixel) {
        BlockPos cell = new BlockPos(pixel.x(), pixel.y(), pixel.z());
        ChunkAccess chunk = chunkAt(level, cell);
        if (chunk == null) {
            return Optional.empty();
        }
        return chunk.getData(ModAttachments.WIRES.get()).allOn(cell, Faces.of(pixel.face()))
                .stream().filter(trace -> trace.has(pixel.u(), pixel.v())).findFirst();
    }

    private static Optional<WireTrace> traceOf(Level level, WirePixel pixel, DyeColor colour) {
        BlockPos cell = new BlockPos(pixel.x(), pixel.y(), pixel.z());
        ChunkAccess chunk = chunkAt(level, cell);
        if (chunk == null) {
            return Optional.empty();
        }
        return chunk.getData(ModAttachments.WIRES.get())
                .trace(cell, Faces.of(pixel.face()), colour)
                .filter(trace -> trace.has(pixel.u(), pixel.v()));
    }

    private static ChunkAccess chunkAt(Level level, BlockPos cell) {
        return level.getChunk(SectionPos.blockToSectionCoord(cell.getX()),
                SectionPos.blockToSectionCoord(cell.getZ()), ChunkStatus.FULL, false);
    }

    /** The panel itself, in the same steel chrome every other instrument in the mod uses. */
    /**
     * What is on the surface under the crosshair.
     *
     * <p><strong>Rule 7: a hazard needs an instrument, and this is the most invisible one in the
     * mod.</strong> A contaminated bench looks exactly like a clean bench forever. Without a way
     * to see it, the only feedback a player gets is an illness some hours later with no way to
     * work out which surface did it — which is the dice roll {@code design/transmission.md} exists
     * to refuse, arriving through the back door.
     *
     * <p>Real decon training uses a fluorescent tracer and a lamp for exactly this reason: people
     * do not believe how far contamination spreads until they can see their own handprints on the
     * door they swore they never touched. These goggles are that lamp.
     */
    private static boolean drawContamination(GuiGraphicsExtractor graphics, Minecraft minecraft,
                                             BlockHitResult hit) {
        if (minecraft.level == null) {
            return false;
        }
        var load = play.xponer.astronima.physio.Surfaces.at(minecraft.level, hit.getBlockPos());
        if (load.isClean()) {
            return false;
        }
        var font = minecraft.font;
        int width = 150;
        int height = 40;
        var seat = seat(graphics, "goggles", width, height);
        if (seat == null) {
            return true; // on another page this frame, and it has still answered for the wire
        }
        int x = seat.x();
        int y = seat.y();

        // Opens from its middle, then fills - the same gesture the block readout makes, because
        // two panels arriving differently on one screen reads as two mods rather than one.
        int inset = play.xponer.astronima.client.hud.Reveal.inset(width, seat.fade());
        double contents = play.xponer.astronima.client.hud.Reveal.contentFade(seat.fade());
        graphics.fill(x + inset, y, x + width - inset, y + height,
                play.xponer.astronima.client.hud.Reveal.fade(0xE00C1015, seat.fade()));
        if (contents <= 0) {
            return true;
        }
        int accent = load.isInfectious() ? play.xponer.astronima.client.hud.HudScale.COLOR_BAD : play.xponer.astronima.client.hud.HudScale.COLOR_WARN;
        graphics.fill(x, y, x + width, y + 1, accent);

        MachineFrame.overlay(graphics, font, net.minecraft.network.chat.Component.translatable(
                "astronima.goggles.contaminated"), x + 6, y + 6, TEXT_WIDTH, accent);

        // The dose line drawn across the bar, so "how bad is this" is a distance rather than a
        // number needing conversion - the same axis the biomonitor uses, on purpose.
        int barX = x + 6;
        int barW = width - 12;
        int barY = y + 20;
        graphics.fill(barX, barY, barX + barW, barY + 6, 0xFF1A1F24);
        graphics.fill(barX, barY, barX + (int) (barW * Math.clamp(load.load(), 0, 1)), barY + 6,
                accent);
        int dose = (int) (barW * play.xponer.astronima.sim.pathogen
                .Contamination.INFECTIOUS_DOSE);
        graphics.fill(barX + dose, barY - 2, barX + dose + 1, barY + 8, play.xponer.astronima.client.hud.HudScale.COLOR_WARN);

        MachineFrame.overlay(graphics, font, net.minecraft.network.chat.Component.translatable(
                        load.isInfectious() ? "astronima.goggles.do_not_touch"
                                : "astronima.goggles.traces"),
                x + 6, y + 29, TEXT_WIDTH, play.xponer.astronima.client.hud.HudScale.COLOR_LABEL);
        return true;
    }

    private static void draw(GuiGraphicsExtractor graphics, Minecraft minecraft, Panel panel) {
        var font = minecraft.font;
        double load = HeatBalance.WORKED_MACHINE_W;
        // What a machine at the far end would ACTUALLY see. Not what it would like: the bus has a
        // fixed voltage, the line and the machine divide it between them, and half the voltage is
        // a quarter of the work. Reading this off a full-power assumption - which is what it did -
        // meant the meter said a hundred metres of signal wire delivered ninety per cent, and the
        // machine on the end of it ran at eleven (design/electrical.md §5.1).
        double amps = Sag.amps(panel.ohms(), load, Delivery.BUS_VOLTS);
        double atTheEnd = Sag.voltsAt(panel.ohms(), load, Delivery.BUS_VOLTS);
        double speed = Sag.fraction(panel.ohms(), load, Delivery.BUS_VOLTS);
        Conductor metre = panel.gauge().metre(panel.metal());
        // Where the meter is standing, because that is what decides the rating. A wire sheds
        // heat by radiating and — only if there is any air — by convection, so the identical run
        // is a different component indoors and out (design/electrical.md §2.1). Quoting a vacuum
        // figure inside a habitat, which is what this did, is not a simplification: it is wrong by
        // two thirds, and in the direction that makes a run look worse than it is.
        var air = play.xponer.astronima.client.AnalyzerHud.here();
        double pressureKPa = air == null ? 0 : air.pressureKPa();
        double ambientK = air == null ? Cooling.VACUUM_AMBIENT_K : air.temperatureK();
        Cooling.Conditions here = Cooling.of(metre, amps, pressureKPa, ambientK);

        List<String> lines = new java.util.ArrayList<>(java.util.List.of(
                String.format(Locale.ROOT, "%s %s wire, %s",
                        panel.gauge().id(),
                        panel.metal().name().toLowerCase(Locale.ROOT),
                        panel.colour().getSerializedName()),
                String.format(Locale.ROOT, "%.2f m circuit    %.4f ohm", panel.metres(),
                        panel.ohms()),
                String.format(Locale.ROOT, "a %.0f W machine: %.2f A, %.1f V of %.0f", load, amps,
                        atTheEnd, Delivery.BUS_VOLTS),
                // Always, not only when it is bad: an instrument that appears when something is
                // wrong cannot tell "fine" apart from "not measured" (rule 7).
                String.format(Locale.ROOT, "it would run at %.0f%% speed", speed * 100),
                // What protection on THIS run gives up at. Shown beside the conductor's own limit
                // so the two can be read against each other: a fuse must be under the wire, and
                // on a run whose thinnest pixel is signal wire that is 2.3 A whatever the rest of
                // it is made of.
                String.format(Locale.ROOT, "protection here trips at %.1f A",
                        play.xponer.astronima.sim.circuit.Protection
                                .ratingAmps(panel.gauge(), panel.metal())),
                String.format(Locale.ROOT, "rated %.1f A %s    wire %.0f K",
                        here.ratingAmps(),
                        here.airFraction() > 0.05 ? "in this air" : "in vacuum",
                        panel.hottestK())));
        // Rule 7: the hazard has to be legible before it can bite, so the band between "warm" and
        // "gone" is said out loud rather than left for the player to infer from a number.
        // Warned on what the wire IS, not on what it would settle at: a run that has been
        // cooking for a minute and one that has just been switched on read very differently, and
        // the first is the one worth shouting about.
        if (play.xponer.astronima.sim.circuit.Scorch.hasFailed(panel.hottestK())) {
            lines.add("BURNING - the insulation is going");
        } else if (play.xponer.astronima.sim.circuit.Scorch.isWorthWarning(panel.hottestK(),
                play.xponer.astronima.wire.WireTrace.REST_K)) {
            lines.add(String.format(Locale.ROOT, "COOKING - %.0f%% of the way to failure",
                    play.xponer.astronima.sim.circuit.Scorch.of(panel.hottestK(),
                            play.xponer.astronima.wire.WireTrace.REST_K) * 100));
        } else if (here.isHot()) {
            lines.add("hot - close to its rating here");
        }

        drawLines(graphics, minecraft, panel.truncated()
                ? append(lines, "circuit larger than the meter follows") : lines);
    }

    private static List<String> append(List<String> lines, String extra) {
        List<String> all = new java.util.ArrayList<>(lines);
        all.add(extra);
        return all;
    }

    /** The panel, in the same steel chrome every other instrument in the mod uses. */
    /** Whether any trace <em>ends</em> on this pixel, rather than running across it. */
    private static boolean landedOn(net.minecraft.world.level.Level level,
                                    play.xponer.astronima.sim.wire.WirePixel pixel) {
        for (var trace : play.xponer.astronima.wire.Wires.bundleOn(level,
                play.xponer.astronima.wire.Wires.cellOf(pixel),
                play.xponer.astronima.wire.Faces.of(pixel.face()))) {
            if (trace.endsAt(pixel.u(), pixel.v())) {
                return true;
            }
        }
        return false;
    }

    private static void drawLines(GuiGraphicsExtractor graphics, Minecraft minecraft,
                                  List<String> lines) {
        var font = minecraft.font;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int boxWidth = width + 12;
        int boxHeight = lines.size() * 10 + 8;
        var seat = seat(graphics, "goggles", boxWidth, boxHeight);
        if (seat == null) {
            return;
        }
        int x = seat.x();
        int y = seat.y();

        MachineFrame.panel(graphics, x, y, boxWidth, boxHeight);
        int row = y + 5;
        boolean first = true;
        for (String line : lines) {
            MachineFrame.overlay(graphics, font, line, x + 6, row, TEXT_WIDTH,
                    first ? MachineFrame.TEXT : MachineFrame.TEXT_DIM);
            row += 10;
            first = false;
        }
    }

    private WireReadout() {}
}
