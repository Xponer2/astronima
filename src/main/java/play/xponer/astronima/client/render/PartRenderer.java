package play.xponer.astronima.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.model.WireModels;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.Contrast;
import play.xponer.astronima.sim.wire.WirePixel;
import play.xponer.astronima.wire.Faces;
import play.xponer.astronima.wire.WirePart;
import play.xponer.astronima.wire.WireTrace;

/**
 * Drawing the logic that lives in the wire layer.
 *
 * <p>A part is not a block, so nothing in the ordinary block pipeline draws it. Same path as
 * {@link WireRenderer}: walk the chunks around the camera and submit one pixel cube per pixel,
 * through {@code SubmitCustomGeometryEvent} — the collector this mod's indicators already draw
 * through, and <em>not</em> {@code debugQuads}, which is the pipeline that made a whole tier of
 * gauges invisible while every test on their values passed.
 *
 * <h2>The housing says which gate it is, and it says it the way a schematic does</h2>
 * Five pixels by five is far too small for a texture of the word "NAND", so the six gates are told
 * apart the way they are on paper: <strong>an inversion bubble</strong> at the output, which is
 * exactly what distinguishes NAND from AND, NOR from OR, and is the whole of what a NOT is —
 * plus a body colour per family and a glyph in the middle. A player who has read one schematic
 * already knows this notation, and one who has not learns something true.
 *
 * <p><strong>The state comes off the record, never from a computation here.</strong> The client
 * has no {@code ServerLevel}, so it cannot ask a gate what it is doing; the server writes the
 * answer into the chunk data and this reads exactly that. Every signal source in this mod has had
 * to publish its own answer since the charge animation shipped.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class PartRenderer {

    /** How far out to look for parts, in chunks. Matches the wire, so a run and its gates fade together. */
    private static final int CHUNK_RADIUS = 4;

    /**
     * <strong>A pad sits at exactly the height wire does, and that is not a detail.</strong>
     *
     * <p>The first version stood the whole part a pixel proud of the wire layer, which looked
     * tidy and was reported straight back: <em>"не могу ничего подключить к мелкой логике, она
     * постоянно на один пиксель выше уровня проводов"</em>. A pad is where a trace <em>lands</em>
     * — if it is drawn in a different plane from the trace, the connection reads as not made even
     * when it is, and there is no way for a player to tell which. Connection points are coplanar
     * with the thing that connects to them.
     */
    private static final double PAD_STANDOFF = WireModels.STANDOFF / 16.0;

    /**
     * The housing, on the other hand, is thicker than its pads — which is what a chip looks like.
     *
     * <p>Small enough that nothing can be mistaken for a pad's plane, large enough that the part
     * reads as an object bolted on rather than as a patch of coloured wire.
     */
    private static final double BODY_STANDOFF = (WireModels.STANDOFF + 0.45) / 16.0;

    /**
     * Housing colours, by family - and every one of them is a <em>ramp</em>, not a colour.
     *
     * <p>The mod's own texture rules say it in one line: <em>"ramps, not colours. Two arbitrary
     * greys never sit right together; two steps of one ramp always do."</em> The first version of
     * this renderer filled the whole housing with one flat tone and put a dark ring round it,
     * which is exactly the programmer art those rules were written to end - the part read as a
     * coloured patch rather than as an object bolted to the wall.
     *
     * <p>So the body is shaded per pixel: lit along the top-left, shadowed along the bottom-right,
     * under the same single light source everything else in this mod is drawn by. At five pixels
     * across that is only three tones, and three tones is the difference between a sticker and a
     * moulded package.
     */
    private static final int[] BODY_AND = ramp(0x3C4E6B);
    private static final int[] BODY_OR = ramp(0x4B5C3A);
    private static final int[] BODY_XOR = ramp(0x553B62);
    private static final int[] BODY_PLAIN = ramp(0x4A4E52);

    /**
     * The timing-and-memory family: {@link PartType#CLOCK} and {@link PartType#RAM}, the two parts
     * in the set with state that persists between refreshes rather than a truth table computed
     * fresh each time. A teal, where every gate family sits between blue and violet — the one hue
     * in the ramp set that reads as "sequential" rather than "combinational" at a glance, the same
     * way the gate families are told apart by hue rather than by a label nobody can read at range.
     */
    private static final int[] BODY_SEQ = ramp(0x2B6E68);

    /**
     * The authored-circuit family: {@link PartType#PLATE} and {@link PartType#MACRO_PLATE}. Every
     * other family here is a fixed function a player did not write; a plate is a schematic somebody
     * built and saved, and before this it rendered as the same undecorated grey box as a bare
     * switch housing — the one part in the whole set that most deserves to read as "a chip", and the
     * one that least did. A terracotta/copper tone, distinct in hue from every gate family and from
     * {@link #BODY_SEQ}, and far enough from {@link #PAD_OUT}'s amber that a lit output pad still
     * reads as a separate thing from the housing it sits on.
     */
    private static final int[] BODY_CIRCUIT = ramp(0x7A5238);

    /**
     * A five-step ramp around a base tone: shadow, dark, base, light, highlight.
     *
     * <p>Saturation falls toward the highlight and the shadow is pulled cool, which is what stops
     * a naive lighten/darken from looking like plastic - the same curve
     * {@code tools/textures.py} builds every material in the mod from.
     */
    private static int[] ramp(int base) {
        int[] out = new int[5];
        for (int step = 0; step < 5; step++) {
            double t = (step / 4.0 - 0.5) * 2;
            double factor = 1 + t * 0.45;
            double blend = Math.abs(t) * 0.25;
            int tint = t < 0 ? 0x121622 : 0xFFFAEB;
            out[step] = 0xFF000000
                    | (channel(base, 16, factor, blend, tint) << 16)
                    | (channel(base, 8, factor, blend, tint) << 8)
                    | channel(base, 0, factor, blend, tint);
        }
        return out;
    }

    private static int channel(int base, int shift, double factor, double blend, int tint) {
        double value = ((base >> shift) & 0xFF) * factor;
        double toward = (tint >> shift) & 0xFF;
        return Math.clamp((int) Math.round(value * (1 - blend) + toward * blend), 0, 255);
    }

    /** The glyph and the inversion bubble: pale, so they read against every housing. */
    private static final int GLYPH = 0xFFCFD6DB;
    private static final int GLYPH_SHADE = 0xFF6E767C;
    private static final int BUBBLE = 0xFFF6F8F9;

    /** The same stud colours the rest of the wiring uses - blue listens, amber drives. */
    private static final int PAD_IN = 0xFF3D6183;
    private static final int PAD_IN_LIVE = 0xFF9FD6FF;
    private static final int PAD_OUT = 0xFFA9752E;
    private static final int PAD_OUT_LIVE = 0xFFFFD27A;
    private static final int PAD_RIM = 0xFF14181B;

    /** A fuse's element: bright while it holds, a sooty gap once it has given up. */
    private static final int FILAMENT = 0xFFF2E4A8;
    private static final int FILAMENT_BLOWN = 0xFF1A1210;

    /** A switch's plunger, and a button's: a control has to read as one (rule 23). */
    /**
     * A breaker's lever: pale nickel when it is holding, warning red when it is not.
     *
     * <p>Not the plunger colours. A switch that is down is <em>doing what you asked</em>; a breaker
     * that is down is a <em>fault you have not fixed yet</em>, and those must not look alike across
     * a room. Red is the one colour in this mod reserved for "this is why nothing works".
     */
    private static final int LEVER_ON = 0xFFD8DEE4;
    private static final int LEVER_OFF = 0xFFD24438;

    private static final int PLUNGER_UP = 0xFFC85446;
    private static final int PLUNGER_DOWN = 0xFF6E2A22;

    /**
     * A clock's beacon: the one pixel that shows its own phase without a wire attached at all.
     *
     * <p>{@link PartType#CLOCK} has no lever to throw and no plunger to press — its state is not a
     * player's decision, it is {@code held} recomputed fresh every refresh from the world's own game
     * time (see the type's own javadoc). Its output pad already animates once a wire is attached
     * (the driving-pad branch below), but a freshly placed clock with nothing wired to it would
     * otherwise be a static grey box for a part whose entire purpose is that it changes — so the
     * housing itself carries a second, wire-independent tell, read straight off the same record
     * every other stateful part already publishes through (rule: state comes off the record, never
     * a computation here).
     */
    private static final int BEACON_ON = 0xFF7FEBC4;
    private static final int BEACON_OFF = 0xFF1E3B36;

    /**
     * RAM's die: sixteen pale dots, four by four, standing for its sixteen cells the way the gate
     * glyphs already stand for a truth table — an identity mark, not a live readout. Reading the
     * chip's actual contents stays the debug command's job (rule: every mechanic ships with a
     * command to set and read its state); this only ever says "this is memory," the same as an AND
     * gate's cross glyph only ever says "this is AND" rather than showing today's inputs.
     */
    private static final int DIE_DOT = 0xFFB9C6C3;
    private static final int DIE_DOT_SHADE = 0xFF6C7B77;

    /**
     * {@link PartType#FRAMEBUFFER}'s own pixels: a phosphor green against a near-black panel — an
     * LED matrix's own two colours, not this file's usual body ramp, because this is the one part
     * in the set whose whole job is being looked at rather than read off its housing shape.
     */
    private static final int SCREEN_LIT = 0xFF7CF29A;
    private static final int SCREEN_DARK = 0xFF12201A;

    private static ModelPart pixel;

    @SubscribeEvent
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        if (pixel == null) {
            pixel = minecraft.getEntityModels().bakeLayer(WireModels.WIRE).getChild("pixel");
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        // Which lines are carrying, so a part's *input* pads light too — that is what makes a
        // gate readable at a glance: two live pads in, a dead pad out, and you know what it did.
        WireLive live = WireLive.current(level);

        for (WirePart part : PartScan.near(level, BlockPos.containing(camera), CHUNK_RADIUS)) {
            draw(part, poseStack, collector, camera, live);
        }
    }

    private static void draw(WirePart part, PoseStack poseStack, SubmitNodeCollector collector,
                             Vec3 camera, WireLive live) {
        // How far through its travel this part is, if it is one that moves. A control the
        // player watches move is a control they believe they operated (rule 23); one that changes
        // between frames is one they wonder whether they touched.
        double travel = part.type().isOperable() ? PartMotionState.travelOf(part) : 0;
        for (int[] at : part.body()) {
            boolean pad = part.padAt(at[0], at[1]) != null;
            boolean plunger = part.type().isOperable() && isCentre(part, at[0], at[1]);
            if (plunger) {
                // Sunk into the housing as it is pressed: the motion is the standoff, so the
                // plunger really travels rather than merely changing colour.
                put(part, at[0], at[1], colourOf(part, at[0], at[1], live),
                        BODY_STANDOFF + (1 - travel) * 1.3 / 16.0, 1.06f,
                        poseStack, collector, camera);
                continue;
            }
            if (pad) {
                // A dark rim under the pad, a hair lower and a hair wider: what makes a pad read
                // as inset metal rather than as one more coloured pixel of the housing.
                put(part, at[0], at[1], PAD_RIM, PAD_STANDOFF - 0.6 / 16.0, 1.35f,
                        poseStack, collector, camera);
            }
            put(part, at[0], at[1], colourOf(part, at[0], at[1], live),
                    pad ? PAD_STANDOFF : BODY_STANDOFF, pad ? 1.0f : 1.06f,
                    poseStack, collector, camera);
        }
    }

    /**
     * What one pixel of a part is.
     *
     * <p>Ordered by what has to win: a pad is a connection point and must never be hidden by
     * decoration, then the bubble that names the gate, then the glyph, then the edge, then the
     * body. Getting that order wrong would make a gate that is visibly wired look unwired.
     */
    private static int colourOf(WirePart part, int u, int v, WireLive live) {
        PartType.Pad pad = part.padAt(u, v);
        if (pad != null) {
            if (!pad.drives()) {
                // A listening pad lights when its line is carrying, so a gate shows its *whole*
                // truth table on the wall: which inputs arrived, and what it decided.
                return live.isCarrying(part.padPixel(pad)) ? PAD_IN_LIVE : PAD_IN;
            }
            if (!part.driving(pad)) {
                return PAD_OUT;
            }
            // A driving pad beats in the same rhythm as the wire it feeds, phased by where it
            // physically is — so a gate and its run read as one flowing thing rather than as a
            // lit dot next to a moving line. Same model, same clock: ChargePulse decides, this
            // paints (rule 25).
            long along = part.padU(pad) + part.padV(pad)
                    + (long) (part.cell().getX() + part.cell().getY() + part.cell().getZ()) * 16;
            return Contrast.mix(PAD_OUT, PAD_OUT_LIVE, 0.35 + 0.65 * live.chargeAt(along))
                    | 0xFF000000;
        }
        if (isBubble(part, u, v)) {
            return BUBBLE;
        }
        // Before the plunger, because a breaker is operable *and* a bridge and must read as
        // neither a switch nor a fuse: a lever, with the state that stops the circuit on it.
        if (part.type() == PartType.BREAKER && isCentre(part, u, v)) {
            return part.held() ? LEVER_OFF : LEVER_ON;
        }
        if (part.type().isOperable() && isCentre(part, u, v)) {
            return part.held() ? PLUNGER_DOWN : PLUNGER_UP;
        }
        // A fuse wears its state where anybody can read it: a bright element across the middle
        // while it holds, and a dark gap with soot round it once it has gone. That is what a real
        // cartridge fuse looks like through its window, and it is the one part in the set whose
        // whole job is to be inspected after something went wrong.
        if (part.type().isBridge() && isCentre(part, u, v)) {
            return part.held() ? FILAMENT_BLOWN : FILAMENT;
        }
        if (part.type() == PartType.CLOCK && isCentre(part, u, v)) {
            return part.held() ? BEACON_ON : BEACON_OFF;
        }
        // A processor's own run pad, reusing CLOCK's exact beacon — a chip is either being asked
        // to execute right now or it is not, and that is the one fact cheap enough to show on
        // every one of its pixels every frame. Whether it is *internally* halted needs unpacking
        // the whole Machine, which the readout does on demand (WireReadout.processorState) rather
        // than here, where it would run once per pixel per frame for every processor on screen.
        if (part.type() == PartType.PROCESSOR && isCentre(part, u, v)) {
            return part.held() ? BEACON_ON : BEACON_OFF;
        }
        if (isDieDot(part, u, v)) {
            return isLit(part, u, v) ? DIE_DOT : DIE_DOT_SHADE;
        }
        if (isScreenPixel(part, u, v)) {
            int x = u - part.u() - SCREEN_MARGIN;
            int y = v - part.v() - SCREEN_MARGIN;
            return play.xponer.astronima.wire.MemoryLogic.pixelAt(part.memory(), x, y)
                    ? SCREEN_LIT : SCREEN_DARK;
        }
        if (isOrientationDot(part, u, v)) {
            return isLit(part, u, v) ? GLYPH : GLYPH_SHADE;
        }
        if (isGlyph(part, u, v)) {
            // The glyph carries its own two tones, so it reads as moulded rather than painted on.
            return isLit(part, u, v) ? GLYPH : GLYPH_SHADE;
        }
        int[] body = bodyOf(part.type());
        if (isCorner(part, u, v)) {
            return body[0];                                  // a chamfer: corners are darkest
        }
        if (isEdge(part, u, v)) {
            return body[isLit(part, u, v) ? 4 : 1];
        }
        return body[isLit(part, u, v) ? 3 : 2];
    }

    /**
     * Whether this pixel faces the light.
     *
     * <p>One light source, from the top-left of the face's own axes - the rule everything else in
     * this mod is drawn under, applied to the pixel grid instead of to a texture. A part shaded
     * the other way from the wall it is on would look like a decal.
     */
    private static boolean isLit(WirePart part, int u, int v) {
        return u - part.u() <= v - part.v();
    }

    private static boolean isCorner(WirePart part, int u, int v) {
        boolean acrossEnd = u == part.u() || u == part.u() + part.spanU() - 1;
        boolean downEnd = v == part.v() || v == part.v() + part.spanV() - 1;
        return acrossEnd && downEnd;
    }

    /**
     * The inversion bubble: one pale pixel just inside the output pad.
     *
     * <p>The real notation, and the only thing on a five-pixel housing that could carry the
     * difference between an AND and a NAND. Placed by walking in from the pad rather than by a
     * per-rotation table, so it follows the part when the wrench turns it.
     */
    private static boolean isBubble(WirePart part, int u, int v) {
        if (part.type().gate() == null || !inverting(part.type())) {
            return false;
        }
        PartType.Pad out = part.type().outputs().get(0);
        int padU = part.padU(out);
        int padV = part.padV(out);
        int centreU = part.u() + part.spanU() / 2;
        int centreV = part.v() + part.spanV() / 2;
        return u == padU + Integer.signum(centreU - padU)
                && v == padV + Integer.signum(centreV - padV);
    }

    private static boolean inverting(PartType type) {
        return switch (type) {
            case GATE_NAND, GATE_NOR, GATE_NOT -> true;
            default -> false;
        };
    }

    /**
     * A glyph in the middle of the housing, distinct per family.
     *
     * <p>A cross for the AND family (<em>all of these</em>), a bar for the OR family
     * (<em>any of these</em>), a diagonal for XOR. Three pixels of pattern is not a label and is
     * not trying to be one — it is the second channel behind colour, for the case a player is
     * colour-blind or the room is dark.
     */
    private static boolean isGlyph(WirePart part, int u, int v) {
        if (part.type().gate() == null) {
            return false;
        }
        int du = u - (part.u() + part.spanU() / 2);
        int dv = v - (part.v() + part.spanV() / 2);
        return switch (part.type()) {
            case GATE_AND, GATE_NAND -> (du == 0) != (dv == 0) && Math.abs(du) + Math.abs(dv) == 1;
            case GATE_OR, GATE_NOR -> dv == 0 && Math.abs(du) <= 1;
            case GATE_XOR -> Math.abs(du) == Math.abs(dv) && Math.abs(du) <= 1;
            default -> du == 0 && dv == 0;
        };
    }

    private static boolean isCentre(WirePart part, int u, int v) {
        return u == part.u() + part.spanU() / 2 && v == part.v() + part.spanV() / 2;
    }

    private static boolean isEdge(WirePart part, int u, int v) {
        return u == part.u() || v == part.v()
                || u == part.u() + part.spanU() - 1 || v == part.v() + part.spanV() - 1;
    }

    private static int[] bodyOf(PartType type) {
        return switch (type) {
            case GATE_AND, GATE_NAND -> BODY_AND;
            case GATE_OR, GATE_NOR -> BODY_OR;
            case GATE_XOR -> BODY_XOR;
            case CLOCK, RAM, FRAMEBUFFER, PROCESSOR -> BODY_SEQ;
            case PLATE, MACRO_PLATE -> BODY_CIRCUIT;
            default -> BODY_PLAIN;
        };
    }

    /**
     * The pin-1 dot every real IC package carries near one corner, so its orientation can be read
     * without hunting for the notch. {@link PartType#PLATE} and {@link PartType#MACRO_PLATE} both
     * put input <code>A</code> at their own local (0,0) ({@code platePads()}/{@code
     * macroPlatePads()}), so one pixel inset from that same corner marks it — genuinely useful once
     * a plate has been turned with a wrench and "which edge is the inputs edge" stops being obvious
     * at a glance, not just decoration borrowed from a real chip's silkscreen.
     */
    private static boolean isOrientationDot(WirePart part, int u, int v) {
        if (part.type() != PartType.PLATE && part.type() != PartType.MACRO_PLATE) {
            return false;
        }
        return u == part.u() + 1 && v == part.v() + 1;
    }

    /**
     * Sixteen dots, four by four, centred in the free interior {@link PartType#RAM}'s pad layout
     * already leaves open — every pad sits on the outermost ring (u/v 0 or 11), so u,v 3..9 in steps
     * of two is untouched by any of them. Walked from the part's own corner rather than hardcoded
     * absolute coordinates, the same way {@link #isBubble} tracks a rotated part's pads instead of a
     * fixed offset.
     */
    private static boolean isDieDot(WirePart part, int u, int v) {
        if (part.type() != PartType.RAM) {
            return false;
        }
        int du = u - part.u();
        int dv = v - part.v();
        return du >= 3 && du <= 9 && (du % 2 == 1) && dv >= 3 && dv <= 9 && (dv % 2 == 1);
    }

    /**
     * The one pixel margin between {@link PartType#FRAMEBUFFER}'s eight-by-eight matrix and its
     * pad ring — {@code ramPads()} puts every pad at u/v 0 or 11, the matrix at u,v ∈ [2,9], so
     * this is both "where the matrix starts" and "how wide the safety gap is", the same number
     * doing both jobs rather than two constants that could quietly drift apart.
     */
    private static final int SCREEN_MARGIN = 2;

    /**
     * design/display.md §2.2/§4.3: the framebuffer's whole face inside the pad ring is its own
     * VRAM, drawn — not a static identity glyph like {@link #isDieDot}'s, but a live readout of
     * {@link WirePart#memory()}, read through the exact same {@link
     * play.xponer.astronima.wire.MemoryLogic#pixelAt} the gametest's own per-pixel assertions use,
     * so the housing and the test that checked it cannot disagree about what a pixel means.
     */
    private static boolean isScreenPixel(WirePart part, int u, int v) {
        if (part.type() != PartType.FRAMEBUFFER) {
            return false;
        }
        int du = u - part.u() - SCREEN_MARGIN;
        int dv = v - part.v() - SCREEN_MARGIN;
        return du >= 0 && du < 8 && dv >= 0 && dv < 8;
    }

    private static void put(WirePart part, int u, int v, int colour, double standoff, float scale,
                            PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera) {
        WirePixel at = new WirePixel(part.cell().getX(), part.cell().getY(), part.cell().getZ(),
                Faces.of(part.face()), u, v);
        double[] centre = WireTrace.centreOf(at, standoff);
        poseStack.pushPose();
        // World rendering puts the origin at the camera, so every position is relative to it.
        poseStack.translate(at.x() + centre[0] - camera.x, at.y() + centre[1] - camera.y,
                at.z() + centre[2] - camera.z);
        poseStack.scale(scale, scale, scale);
        collector.submitModelPart(pixel, poseStack,
                RenderTypes.entitySolid(WireModels.WIRE_TEXTURE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, colour, null);
        poseStack.popPose();
    }

    private PartRenderer() {}
}
