package play.xponer.astronima.client.screen;

import com.mojang.blaze3d.platform.Window;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.animation.StyleAnimation;
import com.lowdragmc.lowdraglib2.gui.ui.style.values.TextureValue;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;
import play.xponer.astronima.client.codex.CodexBook;
import play.xponer.astronima.client.codex.CodexLoader;
import play.xponer.astronima.client.codex.CodexMarkup;
import play.xponer.astronima.client.codex.CodexPage;
import play.xponer.astronima.item.FilterTokenItem;
import play.xponer.astronima.network.ClaimResolvePayload;
import play.xponer.astronima.network.ClaimStageCompletePayload;
import play.xponer.astronima.network.SpectrumDecodeAttemptPayload;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.magic.ClaimProgress;
import play.xponer.astronima.sim.magic.Claims;
import play.xponer.astronima.sim.magic.ObservationTarget;
import play.xponer.astronima.sim.magic.Research;
import play.xponer.astronima.sim.magic.ResearchStage;
import play.xponer.astronima.sim.magic.ResearchState;
import play.xponer.astronima.sim.magic.SpectralLine;
import play.xponer.astronima.sim.magic.Spectrum;
import play.xponer.astronima.sim.magic.WavelengthColor;
import play.xponer.astronima.sim.optics.NamedSkyObjects;
import play.xponer.astronima.sim.optics.SkyProjection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The atlas — design/astra-research.md's interface for the research layer, rebuilt on LDLib2
 * (Taffy layout + LSS styling + SDF/sprite rendering) rather than the mod's plain-rectangle
 * {@code MachineFrame} kit. The user's own instruction: engineering interfaces stay scientific,
 * magic interfaces get a magic look — this is the magic look's first real surface.
 *
 * <h2>What is here, and what is not</h2>
 * design/astra-research.md §5 designs a three-pane screen: branches, the sky chart, a node pane.
 * design/astra-research-m2a.md builds the first slice of the centre pane only — the four known
 * deep-sky objects ({@link NamedSkyObjects#ALL}), projected onto the panel with
 * {@link SkyProjection} and coloured by whether the viewing player has identified them
 * ({@link ResearchState}). The branch rail, the node pane, the plate_archive block, and the real
 * procedural starfield/galactic-band texture (§6.1 — the static generated art below stands in)
 * are all still open, named rather than silently built as if this were the whole thing.
 *
 * <h2>design/astra-atlas-redesign.md — the "grey dots and a line" rewrite</h2>
 * The first cut (§6a.0 of that document) drew every object as a flat {@code sdf(colour, radius)}
 * dot and every claim as one static rotated rectangle — reported back in play as "серые точки и
 * линия какая-то". Phases A1/A2/A3/A4/A6 of that document are built here: kind-shaped icons
 * (outlined when unresolved, lit when identified — {@link #kindIconFrame}), a 3-layer
 * halo/core/pulse claim stroke segmented into 12 wandering pieces ({@link #claimStrokeFrame}),
 * idle motion per {@link NamedSkyObjects.Kind} and per claim state, an assembling opening sequence
 * and hover feedback, an identification payoff (bloom, ripple, claim charge, strip flash), and a
 * claim figure that now appears once both evidence objects are merely {@code captured} rather than
 * fully {@code identified} (§7.1). See that document's own Status line for what has and has not
 * been visually verified — this class cannot be screenshotted by the agent that wrote it.
 *
 * <p><strong>§1.1's rule, load-bearing throughout this file:</strong> the dynamic layer is
 * rebuilt only when {@link ResearchState} actually changes (unchanged from the original build —
 * see the {@code UIEvents.TICK} listener in {@link #screen}). Every animation below instead comes
 * from a {@code DynamicTexture} supplier that reads {@code System.nanoTime()} (via
 * {@link #nowSeconds()}) or a small per-object payoff timestamp — never from rebuilding the
 * element tree. {@code AtlasUiRebuildGuardTest} guards this directly.
 *
 * <h2>The first pass was worse than nothing</h2>
 * A flat {@code sdf(...)} fill with no full-screen backdrop rendered as a tiny floating tooltip
 * over an undimmed world — visible proof that "compiles and uses a real framework" is not the
 * same claim as "looks right," and the reason this build was checked against a real screenshot
 * before being called done rather than after. Two fixes, both structural: a full-screen root
 * dims the world and centres the card (the pattern every modal dialog uses); the card's
 * background is {@code textures/gui/atlas/panel.png} — a real generated starfield and nebula
 * ({@code tools/textures.py}'s {@code build_atlas_panel}), not a solid colour.
 *
 * <p>Layout is Taffy's, not hand-reserved boxes (rule 27's old shape) — LDLib2 owns that
 * arithmetic now and carries its own test suite for it. Nothing here duplicates it.
 */
public final class AtlasUi {

    /**
     * The baked backdrop's own pixel size — {@code tools/textures.py}'s
     * {@code GUI_ART["atlas/panel"]}.
     *
     * <p><strong>Grown from 340x210, found live: two real objects were already overlapping —
     * then found live a second time: the first fix (680x420) did not fit the actual screen.</strong>
     * {@code NamedSkyObjects.M32} sits deliberately close to {@code ANDROMEDA_GALAXY} — real M31
     * and M32 really do sit close enough in the sky to share a telescope field, per that class's
     * own comment — projecting to 13.5px apart at the old panel size. At the old {@code ICON_SIZE}
     * (14, radius 7) their icons already touched with negative clearance; any bump to a size that
     * could actually show S1's baked detail would have made this claim's own two objects merge
     * into one blob, exactly backwards from {@code astra-atlas-s1c-per-object.md}'s own stated
     * acceptance case ("if M32 still looks like a small Andromeda, nothing here worked").
     *
     * <p>The first fix doubled every dimension (680x420) and compared only the *card* against
     * {@code CodexUi}'s own proven ceiling ({@code maxWidth(760).maxHeight(460)}) — missing that
     * the atlas also carries a rail *beside* the card, so the real total footprint was
     * {@code RAIL_WIDTH + PANEL_WIDTH} = 840px wide, already past that proven-safe ceiling before
     * the rail's own width was even added. Reported live: the screen did not fit. This size
     * instead keeps real headroom under the *combined* footprint against that same proven ceiling
     * — checked by direct calculation, not reasserted by feel — while still clearing the M32/
     * Andromeda collision with margin (24.1px separation at this size, an 8.1px clearance for a
     * 16px icon, comfortably more than the 5px this leaf shipped believing was itself safe). The
     * row is also given a real {@code maxWidth}/{@code maxHeight} of its own (see {@code screen()}),
     * so a screen too small even for this stays a graceful shrink rather than a second silent
     * overflow.
     */
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 380;
    /** design/astra-atlas-redesign-v2.md §4: the left rail's own width, beside the chart rather
     *  than inside it — the chart's baked backdrop stays exactly {@link #PANEL_WIDTH}. Part of
     *  the combined footprint {@link #PANEL_WIDTH}'s own doc checks against {@code CodexUi}'s
     *  proven ceiling — this is not sized independently of that check. */
    private static final int RAIL_WIDTH = 110;
    private static final int RAIL_ROW_HEIGHT = 40;
    /** Matches the card's own {@code padding-all}, so a projected {@code (u,v)} of exactly 0 or 1
     *  lands on the backdrop's own edge rather than under the card's padding. */
    private static final int PANEL_INSET = 21;
    /** design/astra-atlas-redesign.md §3/§6a.2: a real kind-shaped icon, not a dot. Sized against
     *  {@link #PANEL_WIDTH} above, not chosen independently — see that constant's own doc for the
     *  collision check this number had to clear. */
    private static final float ICON_SIZE = 16f;
    private static final float ICON_RADIUS = ICON_SIZE / 2f;
    /**
     * §3.1's "unresolved smudge": colourless and faint, not simply invisible — the load-bearing
     * state, per astra-research.md's own words, has to actually be seen to do its job.
     *
     * <p>8 hex digits here are {@code #AARRGGBB} (alpha first), not the {@code #RRGGBBAA} the
     * stylesheet's own {@code background: #00000090} reads as — verified directly in {@code
     * ColorUtils.parseColor} (LDLib2) rather than assumed from that one other usage, which goes
     * through a different parser (the LSS stylesheet engine, not {@link TextureValue}). ~31%
     * alpha, mid-grey.
     */
    private static final String UNRESOLVED_COLOR = "#50808080";
    private static final int UNRESOLVED_ARGB = parseArgb(UNRESOLVED_COLOR);
    // design/astra-atlas-redesign-v2.md §4.1's rail state colours, adapted from Thaumcraft's own
    // violet/amber/teal reading (§1): a claim's relationship to the player, not its category.
    private static final int RAIL_PARTIAL_ARGB = parseArgb("#FF9060C0");
    private static final int RAIL_READY_ARGB = parseArgb("#FFE0A030");
    private static final int RAIL_HELD_ARGB = parseArgb("#FF4FBF8F");
    /**
     * Held/refuted/outlined were originally three strengths of the *same* red-orange hue (real
     * H-alpha, matching the marker colour) — reported from play as impossible to tell apart at a
     * glance, "lit up red" with no idea whether that meant success. Split into three genuinely
     * different colours instead: the line is a game-state readout, not a second physical light
     * source, so it does not owe the markers a shared hue.
     */
    private static final String HELD_LINE_COLOR = "#FF2ECC71";
    /** A refuted attempt: dim, not gone — the mark stays visible (§3.2), but unmistakably "no". */
    private static final String REFUTED_LINE_COLOR = "#D0E74C3C";
    private static final String OUTLINED_LINE_COLOR = "#90CCCCCC";

    // ---- design/astra-atlas-redesign.md §4: the 3-layer claim stroke's own geometry. Segment
    // count is no longer a constant here - AtlasGeometry.curledStrokePoints decides it from its
    // own curl/crossing geometry, not a fixed number every claim shares.
    private static final float STROKE_HALO_HEIGHT = 9f;
    // How often the braid itself re-jitters into a freshly random shape (round 4: "быстро
    // меняться как-будто молния", matching the reviewed artifact's own mulberry32 reseed
    // cadence) - a coarse time bucket claimStrokeFrame reseeds AtlasGeometry.braidedStrokeStrands
    // from, not a per-frame reroll.
    private static final double RESHUFFLE_INTERVAL_S = 0.22;
    // Real pixel thickness a curled segment's own core renders at, regardless of the
    // container's now-much-bigger bounding box - the old STROKE_CORE_HEIGHT_FRACTION was a
    // fraction of the fixed 9px halo strip specifically so it always came out to exactly 2px;
    // this is that same 2px, named directly since the container it is a fraction *of* now varies
    // per claim (a curl's own bounding box, not a fixed strip).
    private static final float CURL_SEGMENT_CORE_HEIGHT_PX = 2f;
    // How much a segment's own rendered length overshoots its true geometric length, so
    // consecutive segments' rounded ends visibly overlap rather than leaving hairline gaps at
    // the joints - higher than the old straight stroke's 1.05 because a curled path's segments
    // meet at a real angle to each other, not dead straight.
    private static final float CURL_SEGMENT_WIDTH_COVERAGE = 1.15f;

    // ---- §3's per-kind idle motion table. First-pass playtest numbers (rule 41), taken directly
    // from the design document's own suggested periods/amplitudes.
    // --- Animated sheets (design/astra-atlas-s1-depiction.md S1b): the identified state
    // plays a 4x4 grid of 64px frames from the sheet tools/textures.py bakes from the sky's
    // own shader constants. Playback is a SpriteTexture sub-rect, not an element rebuild;
    // the phase is per object, so two same-kind objects never pulse in lockstep.
    private static final int SHEET_GRID = 4;
    private static final int SHEET_FRAMES = SHEET_GRID * SHEET_GRID;
    // 0.3, not 1/12: kept equal to tools/textures.py's own SHEET_SECONDS_PER_FRAME
    // (ShaderPortStructureTest.theJavaPlaysTheGeometryTheGeneratorBakes holds the two
    // together) - see that file's comment for why the original 1.33s loop was too short a
    // slice of the shaders' own drift/shear constants to read as motion at all.
    private static final double SHEET_SECONDS_PER_FRAME = 0.3;
    // How much of the object's own representative colour multiplies into the sheet's already-
    // real colour (see sheetFrame's own comment) - 0 would be pure white (no per-object
    // distinguishing cast at all), 1 is the old full-strength tint that crushed a complementary
    // hue toward black. First-pass number (rule 41), chosen so even the worst real case in this
    // roster (Helix Nebula's H-alpha red against the planetary shader's own teal) stays clearly
    // visible - verified arithmetically (tools/textures.py's ColorUtils.mulColor port), not by
    // eye.
    private static final float SHEET_TINT_STRENGTH = 0.35f;

    private static final double EMISSION_BREATH_PERIOD_S = 4.0;
    private static final double EMISSION_BREATH_AMPLITUDE = 0.15;
    /** design/astra-atlas-s5-presentation.md §S5b: "available now — pulse between dim and full,
     *  ~twice a second — the single most important state on the field." A claim whose stage can
     *  be completed right now is the one thing on the whole rail worth interrupting a glance for,
     *  so this swings far harder than the nebula's own subtle breathing above. */
    private static final double RAIL_READY_PULSE_PERIOD_S = 0.5;
    private static final double RAIL_READY_PULSE_AMPLITUDE = 0.5;
    private static final double REFLECTION_FLECK_COUNT = 2;
    private static final double PLANETARY_RING_PERIOD_S = 20.0;
    private static final double GALAXY_SHEAR_PERIOD_S = 30.0;
    private static final double GALAXY_SHEAR_MAX_DEGREES = 4.0;
    private static final double GALAXY_TILT_DEGREES = 25.0;

    // ---- §4's three-state claim motion table.
    private static final double HELD_WANDER_RATE = 1.4;
    private static final double HELD_WANDER_AMPLITUDE = 1.1;
    private static final double OUTLINED_WANDER_RATE = 0.6;
    private static final double OUTLINED_WANDER_AMPLITUDE = 0.5;
    private static final double WANDER_PHASE_PER_SEGMENT = 0.55;
    private static final double REFUTED_SHUDDER_PERIOD_S = 6.0;
    private static final double REFUTED_SHUDDER_DURATION_S = 0.5;
    private static final double REFUTED_SHUDDER_AMPLITUDE = 1.5;

    // ---- §5.1 opening assembly. First-pass playtest numbers, taken directly from the table.
    private static final float OPENING_FADE_DURATION_S = 0.25f;
    private static final float OPENING_NODES_START_S = 0.30f;
    private static final float OPENING_NODE_STAGGER_S = 0.06f;
    private static final float OPENING_NODE_DURATION_S = 0.25f;
    private static final float OPENING_STROKES_START_S = 0.55f;
    private static final float OPENING_STROKE_DURATION_S = 0.4f;

    // ---- §5.3 hover feedback.
    private static final float HOVER_DURATION_S = 0.12f;
    private static final float HOVER_LIFT_PX = 1.5f;

    // Pane-switch transition (round-4 feedback, 2026-08-28: "мало движения в целом" - opening
    // a node/claim pane, or switching between them, used to be an instant swap with no
    // animation at all, unlike the rail's own node/stroke reveal). Not gated on
    // animateOpening like that reveal is: this fires on every rebuildDynamicLayer call that
    // shows one of these three panes, since each such call already only happens when the
    // player just clicked into or switched panes - the call itself is the trigger. Faster
    // than OPENING_NODE_DURATION_S: unlike that one-time reveal, this repeats on every click.
    private static final float PANE_SWITCH_DURATION_S = 0.18f;
    private static final float PANE_SWITCH_START_SCALE = 0.97f;

    // ---- §5.4 payoff. First-pass playtest numbers.
    private static final double BLOOM_DURATION_S = 0.5;
    private static final double BLOOM_OVERSHOOT_SCALE = 1.25;
    private static final double RIPPLE_DURATION_S = 0.6;
    private static final double CHARGE_DURATION_S = 0.6;
    private static final double STRIP_FLASH_DURATION_S = 0.5;

    private static final String STYLE = """
            .atlas_root {
              background: #00000090;
            }
            .atlas_card {
              background: sprite(astronima:textures/gui/atlas/panel.png);
              padding-all: 14;
            }
            .atlas_rail {
              background: #14101eE0;
              padding-all: 6;
            }
            .atlas_title {
              text-color: #eddcae;
              font-size: 10;
            }
            .atlas_rail_label {
              text-color: #cabfae;
              font-size: 9;
            }
            .atlas_rail_row {
              background: #00000000;
              padding-all: 2;
            }
            .atlas_rail_row_hover {
              background: sdf(#2a2240, 2, 1, #8a7a54);
            }
            .atlas_link_btn {
              background: sdf(#1c1830a0, 2, 1, #6a5c3e);
              padding-all: 2;
            }
            .atlas_link_btn_hover {
              background: sdf(#3a2f1c, 2, 1, #eddcae);
            }
            .atlas_action_btn {
              background: sdf(#2a2010, 2, 1, #e0a030);
              padding-all: 3;
            }
            .atlas_action_btn_hover {
              background: sdf(#3a2c14, 2, 1, #ffcf70);
            }
            .atlas_action_btn_not_ready {
              background: sdf(#1c1c1c, 2, 1, #5a5a5a);
              padding-all: 3;
            }
            .atlas_action_btn_not_ready_hover {
              background: sdf(#282828, 2, 1, #8a8a8a);
            }
            """;

    public static ModularUIScreen screen(Player player) {
        UIElement root = new UIElement().setId("atlas_root").addClass("atlas_root");
        root.layout(layout -> layout
                .justifyContent(AlignContent.CENTER)
                .alignItems(AlignItems.CENTER));
        // §5.1 t=0.00: "root dim fades in" - the world-darkening overlay assembles in rather than
        // snapping straight to its full #00000090.
        root.style(s -> s.opacity(0f));

        // design/astra-atlas-redesign-v2.md §4: the rail sits beside the chart, not inside it -
        // the chart's own baked backdrop is a fixed 340x210 image and stays exactly that size;
        // the rail is a plain panel with no baked art of its own, since its own markers are
        // simple shapes (§4.1), the same kind of primitive Thaumcraft's own circle/square/hexagon
        // nodes are, not something that needs painted art.
        UIElement row = new UIElement().setId("atlas_row");
        // Found live, four times now — the first three rounds are kept short here; the full
        // account is in git history and in the (now superseded) reasoning that used to sit in
        // this comment. What is true today, verified by reading LDLib2's own source
        // (Transform2D.java: "Does not affect Yoga layout; only rendering and hit-testing" —
        // and UIElementRenderer.drawContents calls enableScissor using the element's own
        // content box, which the currently-pushed transform DOES carry through, since the
        // transform is pushed onto the pose stack before drawContents runs): a single
        // Transform2D.scale on row is sufficient and correct. It shrinks everything painted as
        // row's descendants, including the scissor clip cut for row's own Clip.SCISSOR, and
        // hit-testing (UIElement#hitTest inverse-transforms the pointer the same way). The one
        // real regression this session (round 4, "и справа и снизу всё обрезается") came from
        // a *different* attempt that moved the transform onto rail/card individually and forgot
        // that card's own opening pop-in animates its transform to a hardcoded 1.0f — reverted.
        //
        // fitScale: 1.0 (no-op) whenever the real screen already fits RAIL_WIDTH+PANEL_WIDTH x
        // PANEL_HEIGHT; otherwise the ratio that makes it fit, computed once from the real
        // window at screen-open time. Confirmed live at 1920x1080 / GUI scale 3
        // (guiScaledWidth/Height 640x360, only 20px of margin under this panel's own 380): the
        // *0.97 is deliberate slack so an exact-fit ratio doesn't leave a knife-edge zero-margin
        // case for float rounding to eat.
        Window window = Minecraft.getInstance().getWindow();
        float fitScale = Math.min(1f, Math.min(
                (float) window.getGuiScaledWidth() / (RAIL_WIDTH + PANEL_WIDTH),
                (float) window.getGuiScaledHeight() / PANEL_HEIGHT) * 0.97f);
        row.layout(layout -> layout.flexDirection(FlexDirection.ROW).alignItems(AlignItems.FLEX_START)
                .width(RAIL_WIDTH + PANEL_WIDTH).height(PANEL_HEIGHT));
        row.style(s -> s.clip(com.lowdragmc.lowdraglib2.gui.ui.data.Clip.SCISSOR)
                .transform2D(new Transform2D().scale(fitScale)));

        UIElement rail = new UIElement().setId("atlas_rail").addClass("atlas_rail");
        rail.style(s -> s.opacity(0f));
        rail.layout(layout -> layout
                .width(RAIL_WIDTH)
                .height(PANEL_HEIGHT)
                .flexDirection(FlexDirection.COLUMN));
        row.addChild(rail);

        UIElement card = new UIElement().setId("atlas_card").addClass("atlas_card");
        card.style(s -> s.opacity(0f).transform2D(new Transform2D().scale(0.94f)));
        card.layout(layout -> layout
                .width(PANEL_WIDTH)
                .height(PANEL_HEIGHT)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.FLEX_START));
        row.addChild(card);

        Label title = new Label().setValue(Component.translatable("astronima.atlas.title"));
        title.addClass("atlas_title");
        card.addChild(title);

        // A dedicated layer for the state-driven content (the line, the markers), so a live
        // update can clear and rebuild exactly this and nothing else — the title above stays put.
        UIElement dynamicLayer = new UIElement().setId("atlas_dynamic");
        dynamicLayer.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0f).top(0f)
                .width(PANEL_WIDTH).height(PANEL_HEIGHT));
        card.addChild(dynamicLayer);

        // Live refresh: the attachment is synced (M2a), so the client's copy of ResearchState
        // already updates the instant the server resolves a claim — what was missing was this
        // screen ever looking again after it was first built. Checked once a tick (cheap: a
        // record equals over a handful of small sets) and only rebuilt on an actual change, not
        // redrawn unconditionally every frame (design/astra-atlas-redesign.md §1.1's rule).
        //
        // openObjectId (design/astra-research-m4b.md §1/§3): which captured object's spectrum
        // strip, if any, is currently swapped in for the chart. armedFilter: which owned filter
        // is currently selected in that strip's own palette. payoffStartNanos
        // (design/astra-atlas-redesign.md §5.4): the wall-clock moment, per object id, that this
        // client last saw it become newly identified — read every frame by the icon/claim/strip
        // suppliers below to drive the one-shot bloom/ripple/charge/flash sequence, written
        // exactly once per identification (inside the guarded state-changed branch below, never
        // per tick). All of this is purely client-local UI state — never synced, never read by
        // the server — so a self-referencing "rebuild myself" runnable is the simplest way for a
        // marker's click, a palette click, and a tick-driven state refresh to share one rebuild
        // path without this method needing parallel copies of it.
        var state = new Object() {
            ResearchState research = player.getData(ModAttachments.RESEARCH.get());
            String openObjectId = null;
            String openClaimId = null;
            SpectralLine armedFilter = null;
            final Map<String, Long> payoffStartNanos = new HashMap<>();
            // Found live ("каждый раз когда нажимаю на фильтр вкладка как-будто бы
            // перезагружается"): arming a filter is an internal change within the SAME strip,
            // not a navigation to a different pane, but rebuildDynamicLayer tears down and
            // rebuilds its child unconditionally on every refresh - including one triggered by
            // onArmFilter - and every pane's own construction unconditionally plays its "just
            // opened" fade-in. This key is what tells the difference: whichever pane was showing
            // last time, compared against what should show now.
            String openPaneKey = null;
        };
        Runnable[] refresh = new Runnable[1];
        boolean[] openingPlayed = {false};
        Consumer<String> onSelect = objectId -> {
            state.openObjectId = Objects.equals(state.openObjectId, objectId) ? null : objectId;
            state.openClaimId = null; // opening an object's own pane leaves any open claim pane behind
            state.armedFilter = null; // a filter armed for one object's strip means nothing on another's
            playAtlasSound(SoundEvents.BOOK_PAGE_TURN, 1.0f);
            refresh[0].run();
        };
        Consumer<SpectralLine> onArmFilter = line -> {
            state.armedFilter = Objects.equals(state.armedFilter, line) ? null : line;
            refresh[0].run();
        };
        Consumer<String> onSelectClaim = claimId -> {
            state.openClaimId = Objects.equals(state.openClaimId, claimId) ? null : claimId;
            state.openObjectId = null; // a claim's own pane and an object's own pane are exclusive
            playAtlasSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            refresh[0].run();
        };
        ModularUI[] muiRef = new ModularUI[1];
        refresh[0] = () -> {
            boolean animateOpening = !openingPlayed[0] && muiRef[0] != null;
            String newPaneKey = paneKey(state.openClaimId, state.openObjectId, state.research);
            boolean samePane = Objects.equals(state.openPaneKey, newPaneKey);
            rebuildDynamicLayer(dynamicLayer, player, state.research, state.openObjectId, state.openClaimId,
                    state.armedFilter, onSelect, onSelectClaim, onArmFilter, muiRef[0], state.payoffStartNanos,
                    animateOpening, samePane);
            state.openPaneKey = newPaneKey;
            rebuildRail(rail, state.research, state.openClaimId, onSelectClaim);
            if (animateOpening) {
                openingPlayed[0] = true;
            }
        };
        card.addEventListener(UIEvents.TICK, event -> {
            ResearchState current = player.getData(ModAttachments.RESEARCH.get());
            if (!current.equals(state.research)) {
                // design/astra-atlas-redesign.md §5.4: the payoff holder is written exactly once,
                // right here, at the moment the diff is detected — never per tick thereafter. The
                // suppliers below read this timestamp every frame to compute elapsed time on
                // their own; nothing here re-runs once the object settles.
                for (String objectId : current.identifiedObjects()) {
                    if (!state.research.identified(objectId)) {
                        state.payoffStartNanos.put(objectId, System.nanoTime());
                    }
                }
                state.research = current;
                refresh[0].run();
            }
        });

        root.addChild(row);

        UI ui = UI.of(root, java.util.List.of(Stylesheet.parse(STYLE)), size -> size);
        ModularUI modularUI = ModularUI.of(ui, player).shouldCloseOnEsc(true);
        muiRef[0] = modularUI;

        // The real first build, now that a ModularUI exists to drive the opening StyleAnimations
        // the marker/claim builders schedule when animateOpening is true.
        refresh[0].run();
        playAtlasSound(SoundEvents.BOOK_PUT, 1.0f);

        StyleAnimation.of(modularUI).select(root)
                .style(PropertyRegistry.OPACITY, 1.0f)
                .duration(OPENING_FADE_DURATION_S)
                .start();
        StyleAnimation.of(modularUI).select(card)
                .style(PropertyRegistry.OPACITY, 1.0f)
                .duration(OPENING_FADE_DURATION_S)
                .start();
        StyleAnimation.of(modularUI).select(card)
                .style(PropertyRegistry.TRANSFORM_2D, new Transform2D().scale(1.0f))
                .duration(OPENING_FADE_DURATION_S)
                .start();
        StyleAnimation.of(modularUI).select(rail)
                .style(PropertyRegistry.OPACITY, 1.0f)
                .duration(OPENING_FADE_DURATION_S)
                .start();

        return new ModularUIScreen(modularUI, Component.translatable("astronima.atlas.title"));
    }

    private static double nowSeconds() {
        return System.nanoTime() / 1.0e9;
    }

    /**
     * The atlas's own interface sound roster (design/astra-atlas-s5-presentation.md §S5c) —
     * found live, this session: grepping {@code SoundEvents.} across the whole {@code client/}
     * package returned nothing at all. Every sound this mod plays is a *world* sound, fired
     * server-side, audible to bystanders (claim resolve, stage complete) — real feedback for a
     * real action, but never for the book itself opening or turning a page, the exact thing
     * Thaumcraft's own book plays {@code page}/{@code write}/{@code clack} for. Client-side only,
     * {@code SoundSource.UI} (via {@link SimpleSoundInstance#forUI}), so this is never heard by
     * anyone but the player looking at their own screen — the same reason a page turn is not a
     * world event.
     */
    private static void playAtlasSound(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    /** Redraws every state-driven element fresh from {@code research} — either the chart (the
     *  claim strokes, the four object markers), while {@code openObjectId} names a captured
     *  object, that object's spectrum strip in place of the chart entirely
     *  (design/astra-research-m4b.md §1: a node pane sub-view, not a second screen), or, while
     *  {@code openClaimId} names a claim (design/astra-atlas-redesign-v2.md §4.1, the left rail's
     *  own click), that claim's own reading pane. */
    /** Which sub-pane a given state names, or {@code null} for the main chart - the identity
     *  {@code refresh[0]} compares before and after a rebuild to tell "navigated to a different
     *  pane" (play the opening animation) from "the same pane rebuilt over an internal change
     *  like arming a filter" (do not - found live, "вкладка как-будто бы перезагружается"). */
    private static String paneKey(String openClaimId, String openObjectId, ResearchState research) {
        if (openClaimId != null) {
            return "claim:" + openClaimId;
        }
        if (openObjectId != null && research.captured(openObjectId)) {
            return "strip:" + openObjectId;
        }
        if (openObjectId != null) {
            return "object:" + openObjectId;
        }
        return null;
    }

    private static void rebuildDynamicLayer(UIElement dynamicLayer, Player player, ResearchState research,
                                            String openObjectId, String openClaimId, SpectralLine armedFilter,
                                            Consumer<String> onSelect, Consumer<String> onSelectClaim,
                                            Consumer<SpectralLine> onArmFilter,
                                            ModularUI modularUI, Map<String, Long> payoffStartNanos,
                                            boolean animateOpening, boolean samePane) {
        dynamicLayer.clearAllChildren();
        if (openClaimId != null) {
            dynamicLayer.addChild(claimInfoPane(openClaimId, research, player, onSelect, onSelectClaim,
                    modularUI, samePane));
            return;
        }
        if (openObjectId != null && research.captured(openObjectId)) {
            ObservationTarget target = ObservationTarget.valueOf(openObjectId.toUpperCase(Locale.ROOT));
            dynamicLayer.addChild(spectrumStrip(target, openObjectId, research, player,
                    armedFilter, onSelect, onSelectClaim, onArmFilter, payoffStartNanos, modularUI, samePane));
            return;
        }
        if (openObjectId != null) {
            // design/astra-atlas-redesign-v2.md §2: every node opens *something* when clicked,
            // captured or not - the fix for "a button you can't press". Not the full reading-pane
            // content (that document's own B4, deliberately not this pass's job), just enough
            // that the click was never a dead one.
            dynamicLayer.addChild(objectInfoPane(openObjectId, onSelect, onSelectClaim, modularUI, samePane));
            return;
        }
        // design/astra-atlas-redesign.md §7.1: a claim's figure now appears once both evidence
        // objects are merely *captured* (both ends observed, even if not yet identified) rather
        // than fully identified. Note for the record: the code found here before this change had
        // NO gate at all — the line was added unconditionally, which is worse than what
        // astra-atlas-redesign.md §7.1 believed ("Today the code requires identified") and a real
        // violation of astra-research.md §6a.1's own rule ("nothing is ever a link to a
        // mystery"). This fixes that too, not only builds the newly-designed condition.
        if (Claims.SAME_ELEMENTS.requiredEvidence().stream().allMatch(research::captured)) {
            dynamicLayer.addChild(claimLine(Claims.SAME_ELEMENTS, NamedSkyObjects.ORION_NEBULA,
                    NamedSkyObjects.HELIX_NEBULA, research, modularUI, payoffStartNanos, animateOpening));
        }
        if (Claims.METAL_ASSAY.requiredEvidence().stream().allMatch(research::captured)) {
            dynamicLayer.addChild(claimLine(Claims.METAL_ASSAY, NamedSkyObjects.ANDROMEDA_GALAXY,
                    NamedSkyObjects.M32, research, modularUI, payoffStartNanos, animateOpening));
        }
        List<NamedSkyObjects.Placement> placements = NamedSkyObjects.ALL;
        for (int i = 0; i < placements.size(); i++) {
            dynamicLayer.addChild(objectMarker(placements.get(i), research, onSelect, modularUI,
                    payoffStartNanos, i, animateOpening));
        }
    }

    /** Centre pixel of a placement's marker within the card, per {@link SkyProjection}. */
    private static Vector2f pixelCenter(NamedSkyObjects.Placement placement) {
        SkyProjection.Point point = SkyProjection.project(placement.fixedDirection());
        AtlasGeometry.Pixel pixel =
                AtlasGeometry.projectedPixel(point.u(), point.v(), PANEL_WIDTH, PANEL_HEIGHT, PANEL_INSET);
        return new Vector2f((float) pixel.x(), (float) pixel.y());
    }

    /**
     * A claim's own figure (design/astra-research-m4a.md §3, redrawn per design/astra-atlas-
     * redesign.md §4): a 3-layer halo/core/pulse stroke joining the two evidence objects' real
     * positions, anchored at each icon's own rim ({@link AtlasGeometry#strokeSegments}). Clicking
     * it sends {@link ClaimResolvePayload} — the server decides hold or refute from whatever
     * exposed plates the player is actually carrying, never the client.
     */
    private static UIElement claimLine(Research.Claim claim, NamedSkyObjects.Placement a,
                                       NamedSkyObjects.Placement b, ResearchState research,
                                       ModularUI modularUI, Map<String, Long> payoffStartNanos,
                                       boolean animateOpening) {
        Vector2f start = pixelCenter(a);
        Vector2f end = pixelCenter(b);
        String objectIdA = Claims.objectId(a.target());
        String objectIdB = Claims.objectId(b.target());

        // Found live, round 4 - three corrections in a row on the same feature, all against the
        // reviewed artifact directly: "оставить только линию... не добавлять вихри" (no spiral
        // curls), "посмотри как эта линия сделана в артефакте... сделай так же" (the artifact's
        // own crossing braided several overlapping jagged strands, not one), and finally
        // "вместо того чтобы быстро меняться как-будто молния оно просто изгибается" (it should
        // keep re-jittering like real lightning, not settle into one static shape) - the mockup's
        // own mulberry32-reseeded-every-220ms was the thing to copy after all, not avoid, and an
        // earlier version of this comment had that backwards. AtlasGeometry.braidedStrokeStrands
        // is reseeded on a coarse time bucket inside claimStrokeFrame below (RESHUFFLE_INTERVAL_S
        // apart, matching the artifact's own 220ms) rather than once here, so the shape itself
        // visibly re-jitters instead of only its wander offset moving within one fixed shape.
        long seed = claim.id().hashCode();

        // The container's own layout box is still sized once, here, from the resting (bucket 0)
        // shape - re-laying out the element every reshuffle would be the exact "rebuild the tree
        // to animate" §1.1 forbids. GENEROUS_PAD is real slack, not a guess: the braid's own
        // spread is bounded by a geometric series (CROSS_SPREAD_FRACTION_OF_DISTANCE *
        // 1/(1-CROSS_SPREAD_DECAY), AtlasGeometry's own constants), so every possible reseed's
        // own extent is bounded too - this container is sized comfortably past that bound rather
        // than sampled from one instance and hoped past.
        List<List<AtlasGeometry.CurlPoint>> reference = AtlasGeometry.braidedStrokeStrands(
                start.x, start.y, end.x, end.y, ICON_RADIUS, seed);
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (List<AtlasGeometry.CurlPoint> strand : reference) {
            for (AtlasGeometry.CurlPoint p : strand) {
                minX = Math.min(minX, p.x());
                maxX = Math.max(maxX, p.x());
                minY = Math.min(minY, p.y());
                maxY = Math.max(maxY, p.y());
            }
        }
        double refWidth = maxX - minX;
        double refHeight = maxY - minY;
        double refCenterX = (minX + maxX) / 2.0;
        double refCenterY = (minY + maxY) / 2.0;
        // Padding so the halo's own real thickness never clips against the container's own
        // edge, plus real slack for a reshuffled shape reaching a bit further than this one
        // reference sample did.
        float pad = STROKE_HALO_HEIGHT + (float) Math.max(refWidth, refHeight) * 0.3f;
        float boxLeft = (float) (refCenterX - refWidth / 2.0 - pad);
        float boxTop = (float) (refCenterY - refHeight / 2.0 - pad);
        float boxWidthPx = (float) refWidth + pad * 2f;
        float boxHeightPx = (float) refHeight + pad * 2f;
        float centerX = boxLeft + boxWidthPx / 2f;
        float centerY = boxTop + boxHeightPx / 2f;

        boolean held = research.holds(claim.id());
        // Not a check against this claim's own *correct* evidence — that combination can only
        // ever hold, never refute, by resolve()'s own definition, so that check would never be
        // true. "Refuted" here means "any wrong attempt at this claim is on record", regardless
        // of which wrong evidence it was — refutedCombinations keys each distinct wrong attempt
        // separately (Research.combinationKey), so this is a prefix scan across all of them.
        boolean refuted = !held && research.refutedCombinations().stream()
                .anyMatch(combination -> combination.startsWith(claim.id() + "|"));

        UIElement line = new UIElement().setId("atlas_claim_" + claim.id());
        line.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(boxLeft).top(boxTop)
                .width(boxWidthPx).height(boxHeightPx));
        // A curled path has no single angle to pivot a scale-x-from-zero reveal on the way the
        // old straight box did (§5.1's own "draws itself end to end" trick only makes sense for
        // a straight run) - simplified here to a plain opacity fade-in, one style property
        // instead of two, combined into the same call the background is set in rather than a
        // second .style() (found this session: a second call risks silently replacing the
        // first rather than merging with it).
        float startOpacity = animateOpening ? 0f : 1f;
        line.style(s -> s.background(DynamicTexture.of(() ->
                        claimStrokeFrame(held, refuted, start, end, seed, centerX, centerY, boxWidthPx, boxHeightPx,
                                objectIdA, objectIdB, payoffStartNanos)))
                .opacity(startOpacity));
        line.addEventListener(UIEvents.CLICK, event ->
                ClientPacketDistributor.sendToServer(new ClaimResolvePayload(claim.id())));

        if (animateOpening) {
            StyleAnimation.of(modularUI).select(line)
                    .style(PropertyRegistry.OPACITY, 1.0f)
                    .duration(OPENING_STROKE_DURATION_S)
                    .delay(OPENING_STROKES_START_S)
                    .start();
        }
        return line;
    }

    /**
     * One frame of a claim's stroke: every raw segment of every braided strand
     * ({@link AtlasGeometry#braidedStrokeStrands}), each independently positioned and rotated to
     * its own two points, with a wide faint halo pass and a narrow bright core pass — the halo
     * is per-segment now rather than one strip the whole old straight box shared, since a
     * braided shape has no single strip that could cover it. Each core segment still wanders
     * perpendicular to its own local direction ({@link AtlasMotion#segmentWanderOffset}) — the
     * "переливается" ask, preserved exactly, just measured against the segment's own direction
     * instead of a shared box's, and each strand's own wander is phase-offset from the others'
     * so overlapping strands do not visibly breathe in lockstep. A travelling pulse when the
     * claim holds, placed by the widest strand's own arc-length fraction (one pulse for the
     * whole braid, not one per strand — a pulse racing several parallel paths at once would
     * read as noise, not a signal). While a payoff is charging in from either end (§5.4 step
     * 4), the affected segments on every strand flare bright white regardless of the claim's own
     * state, same as before.
     *
     * <p>Called fresh every frame from a {@code DynamicTexture} supplier — this never touches the
     * element tree (design/astra-atlas-redesign.md §1.1).
     */
    private static IGuiTexture claimStrokeFrame(boolean held, boolean refuted,
                                                Vector2f start, Vector2f end, long seed,
                                                float centerX, float centerY,
                                                float boxWidthPx, float boxHeightPx,
                                                String objectIdA, String objectIdB,
                                                Map<String, Long> payoffStartNanos) {
        double t = nowSeconds();
        // Reseeded on a coarse time bucket, not every frame - the shape itself changes roughly
        // every RESHUFFLE_INTERVAL_S seconds (matching the reviewed artifact's own 220ms
        // reseed), while frames *within* one bucket render the same computed strands, so the
        // real geometry work (four strands of real midpoint displacement) runs a few times a
        // second, not sixty.
        long timeBucket = (long) Math.floor(t / RESHUFFLE_INTERVAL_S);
        List<List<AtlasGeometry.CurlPoint>> strands = AtlasGeometry.braidedStrokeStrands(
                start.x, start.y, end.x, end.y, ICON_RADIUS, seed + timeBucket);
        int baseColor = parseArgb(held ? HELD_LINE_COLOR : refuted ? REFUTED_LINE_COLOR : OUTLINED_LINE_COLOR);

        double chargeFromA = chargeFractionFor(objectIdA, payoffStartNanos);
        double chargeFromB = chargeFractionFor(objectIdB, payoffStartNanos);

        List<IGuiTexture> children = new ArrayList<>();
        for (int strandIndex = 0; strandIndex < strands.size(); strandIndex++) {
            List<AtlasGeometry.CurlPoint> path = strands.get(strandIndex);
            int segmentCount = path.size() - 1;
            int litFromA = (int) Math.floor(chargeFromA * segmentCount);
            int litFromB = (int) Math.floor(chargeFromB * segmentCount);
            double strandPhase = strandIndex * WANDER_PHASE_PER_SEGMENT * 3.7;

            for (int i = 0; i < segmentCount; i++) {
                AtlasGeometry.CurlPoint p0 = path.get(i);
                AtlasGeometry.CurlPoint p1 = path.get(i + 1);
                boolean charging = i < litFromA || i >= segmentCount - litFromB;
                if (!held && !refuted && !charging && i % 2 != 0) {
                    // §4's outlined-state table: "pale, dotted" - every other segment skipped.
                    continue;
                }

                double segDx = p1.x() - p0.x();
                double segDy = p1.y() - p0.y();
                double segLen = Math.max(Math.hypot(segDx, segDy), 0.01);
                float angleDeg = (float) Math.toDegrees(Math.atan2(segDy, segDx));
                double normalX = -segDy / segLen;
                double normalY = segDx / segLen;

                double wander = refuted
                        ? AtlasMotion.refutedShudderOffset(t + strandPhase, REFUTED_SHUDDER_PERIOD_S, REFUTED_SHUDDER_DURATION_S, REFUTED_SHUDDER_AMPLITUDE)
                        : AtlasMotion.segmentWanderOffset(t + strandPhase, i, held ? HELD_WANDER_RATE : OUTLINED_WANDER_RATE,
                                WANDER_PHASE_PER_SEGMENT, held ? HELD_WANDER_AMPLITUDE : OUTLINED_WANDER_AMPLITUDE);
                float localX = (float) ((p0.x() + p1.x()) / 2.0 - centerX + normalX * wander);
                float localY = (float) ((p0.y() + p1.y()) / 2.0 - centerY + normalY * wander);
                float widthFraction = (float) (segLen * CURL_SEGMENT_WIDTH_COVERAGE / boxWidthPx);

                SDFRectTexture halo = new SDFRectTexture();
                halo.setRadius(1f);
                halo.setColor(withAlpha(baseColor, held ? 28 : refuted ? 14 : 10));
                halo.rotate(angleDeg);
                halo.scale(widthFraction, STROKE_HALO_HEIGHT / boxHeightPx);
                halo.transform(localX, localY);
                children.add(halo);

                int segColor = charging ? withAlpha(0xFFFFFFFF, 235) : withAlpha(baseColor, held ? 220 : refuted ? 160 : 150);
                SDFRectTexture segment = new SDFRectTexture();
                segment.setRadius(1f);
                segment.setColor(segColor);
                segment.rotate(angleDeg);
                segment.scale(widthFraction, CURL_SEGMENT_CORE_HEIGHT_PX / boxHeightPx);
                segment.transform(localX, localY);
                children.add(segment);
            }
        }

        // Found live, round 4 ("эта конченая белая полоска проходит через всё убери её"): the
        // travelling white pulse a held claim used to draw along its own widest strand is gone -
        // asked for directly, not a bug fix. held claims read as held through their own colour
        // and full-segment density alone (see the outlined-state skip above, which a held claim
        // never triggers), same as before this leaf.

        return GuiTextureGroup.of(children.toArray(IGuiTexture[]::new));
    }

    /** How far a claim's charge-in-from-this-end payoff has progressed right now, {@code 0} if
     *  none is playing for this object at all — design/astra-atlas-redesign.md §5.4 step 4. */
    private static double chargeFractionFor(String objectId, Map<String, Long> payoffStartNanos) {
        Long startNanos = payoffStartNanos.get(objectId);
        if (startNanos == null) {
            return 0.0;
        }
        double elapsed = (System.nanoTime() - startNanos) / 1.0e9;
        return AtlasMotion.stillPlaying(elapsed, CHARGE_DURATION_S)
                ? AtlasMotion.chargeFraction(elapsed, CHARGE_DURATION_S) : 0.0;
    }

    /**
     * One object node (design/astra-research-m2a.md §2, redrawn per design/astra-atlas-
     * redesign.md §3/§6a.2): a real kind-shaped icon — outlined until identified, lit after —
     * with idle motion appropriate to what kind of object it actually is, plus the identification
     * payoff's bloom-and-ripple when one just fired. Position comes from {@link SkyProjection},
     * projecting the object's real fixed sky direction — never a layout choice.
     *
     * <p><strong>Always clickable, captured or not</strong> (design/astra-atlas-redesign-v2.md §2,
     * §0's own complaint quoted verbatim: "если на кнопку нельзя нажать тогда зачем вообще её
     * делать"). A prior version attached the listener only once captured, reasoning that there was
     * "nothing to read yet" — which left an icon that looked exactly as pressable as every other
     * one on the screen and silently did nothing, worse than rule 18's own failure mode rather than
     * an answer to it. What changes with state is the *destination* of the click
     * ({@link #objectInfoPane} before capture, the spectrum strip after), never whether it has one.
     */
    /**
     * A back/close link, bordered and hoverable like the codex's own {@code clickableRow}
     * (design/astra-atlas-redesign-v2.md §2's affordance rule, applied here for the first time —
     * found live in play: every back link in this file used to be a bare {@code Label} with the
     * identical {@code atlas_title} class every static line of text uses, so nothing on screen
     * told a player it was pressable). Label style is set directly rather than through an LSS
     * descendant selector, the same fix {@code CodexBlocks.clickableRow}'s own comment already
     * found necessary for exactly this reason (a descendant selector into a row's own label did
     * not reliably match in this LDLib2 version).
     */
    private static UIElement atlasLinkButton(String id, String text, float top, float width,
                                             Runnable onClick) {
        UIElement link = new UIElement().setId(id).addClass("atlas_link_btn");
        link.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left((float) PANEL_INSET).top(top)
                .width(width).height(14f));
        Label label = new Label().setValue(Component.literal(text));
        label.textStyle(style -> style.textColor(0xFFEDDCAE).fontSize(9));
        link.addChild(label);
        // Found live ("хитбокс маленький" - the hover highlight glitching in and out over the
        // text itself): CodexBlocks.clickableRow's own comment already names this exact trap -
        // mouse-enter/leave do not bubble from a child to its parent, so a listener on link
        // alone only ever fired over the padding strip around the label, never over the label's
        // own area, which is most of the button. Every real hit target - link AND its label -
        // needs its own copy of all three listeners, exactly clickableRow's own fix.
        for (UIElement target : List.of(link, label)) {
            target.addEventListener(UIEvents.CLICK, event -> {
                event.stopPropagation();
                onClick.run();
            });
            target.addEventListener(UIEvents.MOUSE_ENTER, event -> link.addClass("atlas_link_btn_hover"));
            target.addEventListener(UIEvents.MOUSE_LEAVE, event -> link.removeClass("atlas_link_btn_hover"));
        }
        return link;
    }

    private static UIElement objectMarker(NamedSkyObjects.Placement placement, ResearchState research,
                                          Consumer<String> onSelect, ModularUI modularUI,
                                          Map<String, Long> payoffStartNanos, int nodeIndex,
                                          boolean animateOpening) {
        String objectId = Claims.objectId(placement.target());
        boolean identified = research.identified(objectId);
        int litColor = identified ? representativeColorArgb(placement.target()) : UNRESOLVED_ARGB;
        NamedSkyObjects.Kind kind = placement.kind();

        Vector2f center = pixelCenter(placement);
        float left = center.x - ICON_RADIUS;
        float top = center.y - ICON_RADIUS;

        UIElement marker = new UIElement().setId("atlas_node_" + objectId);
        marker.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left).top(top)
                .width(ICON_SIZE).height(ICON_SIZE));
        marker.style(s -> s.background(DynamicTexture.of(() -> {
            Long startNanos = payoffStartNanos.get(objectId);
            Double elapsed = startNanos == null ? null : (System.nanoTime() - startNanos) / 1.0e9;
            return kindIconFrame(kind, identified, litColor, nowSeconds(), elapsed, objectId);
        })));
        marker.addEventListener(UIEvents.CLICK, event -> onSelect.accept(objectId));

        // §5.3: hover lift + halo brighten on MOUSE_ENTER/LEAVE - the same event pair
        // CodexBlocks' own clickableRow already uses for its hover state.
        Transform2D resting = new Transform2D();
        Transform2D lifted = new Transform2D().translate(0f, -HOVER_LIFT_PX);
        marker.addEventListener(UIEvents.MOUSE_ENTER, event ->
                StyleAnimation.of(modularUI).select(marker)
                        .style(PropertyRegistry.TRANSFORM_2D, lifted)
                        .duration(HOVER_DURATION_S)
                        .start());
        marker.addEventListener(UIEvents.MOUSE_LEAVE, event ->
                StyleAnimation.of(modularUI).select(marker)
                        .style(PropertyRegistry.TRANSFORM_2D, resting)
                        .duration(HOVER_DURATION_S)
                        .start());

        if (animateOpening) {
            // §5.1 t=0.30: nodes appear staggered, ~60ms apart, each scaling 0.6 -> 1.0.
            marker.style(s -> s.opacity(0f).transform2D(new Transform2D().scale(0.6f)));
            double delay = OPENING_NODES_START_S + AtlasMotion.staggerDelaySeconds(nodeIndex, OPENING_NODE_STAGGER_S);
            StyleAnimation.of(modularUI).select(marker)
                    .style(PropertyRegistry.OPACITY, 1.0f)
                    .duration(OPENING_NODE_DURATION_S)
                    .delay((float) delay)
                    .start();
            StyleAnimation.of(modularUI).select(marker)
                    .style(PropertyRegistry.TRANSFORM_2D, new Transform2D().scale(1.0f))
                    .duration(OPENING_NODE_DURATION_S)
                    .delay((float) delay)
                    .start();
        } else {
            marker.style(s -> s.opacity(1f).transform2D(resting));
        }
        return marker;
    }

    /**
     * design/astra-atlas-redesign-v2.md §4.1: one row per {@link Claims#ALL} claim, laid out
     * top-to-bottom — the real dependency tree astra-research.md §5 always promised and the sky
     * chart, pinned to real astronomical position, structurally cannot show (§4.2). Shape and
     * colour both encode {@link ClaimProgress}, the same "state, not category" reading §1 of this
     * document takes from Thaumcraft's own teal/amber/violet/dark scheme.
     */
    private static void rebuildRail(UIElement rail, ResearchState research, String openClaimId,
                                    Consumer<String> onSelectClaim) {
        rail.clearAllChildren();
        for (Research.Claim claim : Claims.ALL) {
            rail.addChild(railRow(claim, ClaimProgress.of(claim, research),
                    claim.id().equals(openClaimId), onSelectClaim));
        }
    }

    private static UIElement railRow(Research.Claim claim, ClaimProgress progress, boolean open,
                                     Consumer<String> onSelectClaim) {
        UIElement row = new UIElement().setId("atlas_rail_row_" + claim.id())
                .addClass("atlas_rail_row");
        row.layout(layout -> layout
                .width(RAIL_WIDTH - 8f)
                .height((float) RAIL_ROW_HEIGHT)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .paddingAll(2));
        UIElement markerBox = new UIElement().setId("atlas_rail_marker_" + claim.id());
        markerBox.layout(layout -> layout.width(14f).height(14f));
        markerBox.style(s -> s.background(DynamicTexture.of(() -> railMarker(progress, open, nowSeconds()))));
        row.addChild(markerBox);

        UIElement labelBox = new UIElement().setId("atlas_rail_label_" + claim.id());
        labelBox.layout(layout -> layout.width(RAIL_WIDTH - 26f).height(28f));
        Label label = new Label().setValue(Component.literal(claim.id().replace('_', ' ')));
        label.addClass("atlas_rail_label");
        labelBox.addChild(label);
        row.addChild(labelBox);

        // The diamond marker already carries real state (shape/colour); the row itself gets the
        // same darken-on-hover box every other clickable text row in this file now uses, so the
        // label reads as pressable too, not only the icon beside it. Every nested child gets its
        // own copy of all three listeners, not just row - mouse-enter/leave do not bubble from a
        // child to its parent (CodexBlocks.clickableRow's own documented trap), so a listener on
        // row alone left the marker and label areas - most of the row's real clickable surface -
        // reading as hover-dead ("хитбокс маленький").
        for (UIElement target : List.of(row, markerBox, labelBox, label)) {
            // Found live ("то нажимаются то не нажимаются" - sound plays, pane doesn't open,
            // depending on exactly where the pointer landed): unlike mouse-enter/leave, CLICK
            // does bubble here, so a click on label fired this row's own non-idempotent toggle
            // once from label, again from labelBox, again from row - three fires (net: works),
            // while a click on markerBox alone fired twice (net: cancels back to nothing). Every
            // click handler in this loop must stop propagation at the first (deepest) element
            // that catches it, exactly like atlasLinkButton's own click handler already does.
            target.addEventListener(UIEvents.CLICK, event -> {
                event.stopPropagation();
                onSelectClaim.accept(claim.id());
            });
            target.addEventListener(UIEvents.MOUSE_ENTER, event -> row.addClass("atlas_rail_row_hover"));
            target.addEventListener(UIEvents.MOUSE_LEAVE, event -> row.removeClass("atlas_rail_row_hover"));
        }

        return row;
    }

    /** A diamond, rotated from the same square primitive every other shape here already uses —
     *  Thaumcraft's own shape-is-a-different-primitive-per-mechanism idea (§1), kept simple
     *  because a claim has exactly one real mechanism (resolve), unlike an object's four kinds. */
    private static IGuiTexture railMarker(ClaimProgress progress, boolean open, double timeSeconds) {
        int color = switch (progress) {
            case UNSTARTED -> UNRESOLVED_ARGB;
            case PARTIAL -> RAIL_PARTIAL_ARGB;
            case READY -> RAIL_READY_ARGB;
            case HELD -> RAIL_HELD_ARGB;
        };
        // READY pulses; every other state is flat, matching S5b's own brightness table exactly —
        // "available now" is the one state the field asks the eye to be pulled toward.
        if (progress == ClaimProgress.READY) {
            double breathe = AtlasMotion.breathingBrightnessFraction(
                    timeSeconds, RAIL_READY_PULSE_PERIOD_S, RAIL_READY_PULSE_AMPLITUDE);
            color = scaleBrightness(color, (float) breathe);
        }
        SDFRectTexture diamond = new SDFRectTexture();
        diamond.setRadius(1f);
        boolean filled = progress == ClaimProgress.READY || progress == ClaimProgress.HELD;
        diamond.setColor(filled ? color : 0);
        diamond.setBorderColor(color);
        diamond.setStroke(filled ? 0f : 1.5f);
        diamond.scale(0.5f);
        diamond.getTransform2D().rotation(45f);
        if (!open) {
            return diamond;
        }
        // The currently-open row gets a faint halo, the same "you are here" cue the object
        // markers' own hover lift gives - a rail row has no hover animation of its own (a static
        // list reads fine without one), so this is its one piece of state feedback.
        SDFRectTexture halo = new SDFRectTexture();
        halo.setRadius(1f);
        halo.setColor(0);
        halo.setBorderColor(withAlpha(0xFFFFFFFF, 90));
        halo.setStroke(1f);
        halo.scale(0.75f);
        halo.getTransform2D().rotation(45f);
        return GuiTextureGroup.of(halo, diamond);
    }

    /**
     * A claim's own reading pane (design/astra-research.md §5's "right pane, for a claim" spec,
     * finally with a real trigger — design/astra-atlas-redesign-v2.md §4.1): which objects it
     * needs, which of those are already captured/identified, and whether it already holds. Once
     * held, also names the real reward: the {@code research:<id>} grant lands as a
     * {@code :::locked} section on a codex page (see {@code codex/spectroscopy/lines.md}), and
     * nothing here used to point at it — found live as "что оно открывает", a claim that visibly
     * did nothing once completed.
     */
    private static UIElement claimInfoPane(String claimId, ResearchState research, Player player,
                                           Consumer<String> onSelect, Consumer<String> onSelectClaim,
                                           ModularUI modularUI, boolean samePane) {
        Research.Claim claim = Claims.ALL.stream()
                .filter(c -> c.id().equals(claimId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no claim with id " + claimId));
        ClaimProgress progress = ClaimProgress.of(claim, research);
        CodexBook book = CodexLoader.book();
        CodexPage page = book.pageByAtlasKey("claim/" + claimId);

        UIElement pane = new UIElement().setId("atlas_claim_pane");
        pane.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0f).top(0f)
                .width((float) PANEL_WIDTH).height((float) PANEL_HEIGHT));

        String backTitle = page != null ? page.title() : claimId.replace('_', ' ');
        UIElement back = atlasLinkButton("atlas_claim_back",
                "< " + backTitle + " (" + progress.toString().toLowerCase(Locale.ROOT) + ")",
                24f, (float) (PANEL_WIDTH - 2 * PANEL_INSET), () -> onSelectClaim.accept(null));
        pane.addChild(back);

        // The claim's own written history (design/astra-atlas-s2c-writing.md §4.2) — real prose,
        // not the id-and-status line this pane used to be limited to. Height re-verified against
        // the smaller card (design/astra-atlas-s1-depiction.md's own resize): the worst real case
        // — a stage with two Owns rows (same_elements's own stage 2) — now leaves 49px of real
        // margin above the card's own bottom inset, computed below, not assumed.
        UIElement prose = atlasProseScroller("atlas_claim_prose_" + claimId, book, "claim/" + claimId,
                claimId.replace('_', ' ') + " — no entry written for this claim yet.",
                onSelect, onSelectClaim);
        prose.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left((float) PANEL_INSET).top(42f)
                .width((float) (PANEL_WIDTH - 2 * PANEL_INSET)).height(110f));
        pane.addChild(prose);

        int currentStage = research.stageOf(claim.id());
        int total = claim.totalStages();
        boolean held = currentStage >= total;

        // The stage's own live text (astronima.research.<claim>.stage<n>, S2c-2's per-stage
        // prose): the one thing that makes this pane change what it says as the player actually
        // progresses, not a static write-up read once and never again. Held claims have no
        // "current" stage left to narrate, so this block is skipped rather than shown empty.
        float techTop = 160f;
        if (!held) {
            ResearchStage currentStageModel = claim.stage(currentStage);
            UIElement stageText = new UIElement().setId("atlas_claim_stage_text_" + claimId);
            stageText.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left((float) PANEL_INSET).top(160f)
                    .width((float) (PANEL_WIDTH - 2 * PANEL_INSET)).height(44f));
            Label stageLabel = new Label().setValue(
                    Component.translatable(currentStageModel.textKey()));
            stageLabel.textStyle(style -> style.textColor(0xFFE0A030).fontSize(9));
            CodexBlocks.wrapped(stageLabel);
            stageText.addChild(stageLabel);
            pane.addChild(stageText);
            techTop = 212f;
        }

        int rowIndex = 0;
        for (String objectId : claim.requiredEvidence()) {
            Component status = research.identified(objectId)
                    ? Component.translatable("astronima.atlas.status.identified")
                    : research.captured(objectId)
                    ? Component.translatable("astronima.atlas.status.captured")
                    : Component.translatable("astronima.atlas.status.not_captured");
            float top = techTop + rowIndex * 16f;
            UIElement line = new UIElement().setId("atlas_claim_evidence_" + objectId);
            line.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left((float) PANEL_INSET).top(top)
                    .width((float) (PANEL_WIDTH - 2 * PANEL_INSET)).height(14f));
            Label lineLabel = new Label().setValue(Component.literal(objectId.replace('_', ' ') + " - ")
                    .append(status));
            lineLabel.addClass("atlas_title");
            line.addChild(lineLabel);
            pane.addChild(line);
            rowIndex++;
        }

        // --- Stage rows (design/astra-atlas-s3-progression.md; generated from the model, never
        // authored - S2's split). The current stage's requirement rows with have/wanted, and a
        // Complete control only on the claim's own current stage. The screen draws what it
        // believes; the server re-checks and decides (rule 25).
        final int rowIndexStart = Math.max(rowIndex, 3);
        rowIndex = rowIndexStart;
        final float techTopFinal = techTop;
        if (held) {
            UIElement heldEl = new UIElement().setId("atlas_claim_held_" + claim.id());
            heldEl.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left((float) PANEL_INSET).top(techTopFinal + rowIndexStart * 16f)
                    .width((float) (PANEL_WIDTH - 2 * PANEL_INSET)).height(14f));
            Label heldLabel = new Label().setValue(Component.translatable(
                    "astronima.atlas.claim.held"));
            heldLabel.addClass("atlas_title");
            heldEl.addChild(heldLabel);
            pane.addChild(heldEl);

            // Found live ("что оно открывает" - a claim's own real reward, research:<id>'s
            // Unlocks grant, lands as a :::locked section on a codex page and nothing in this
            // pane ever pointed at it, so completing a claim looked like it did nothing at all).
            // Named here, once the reward is real, rather than left for the player to
            // rediscover by chance.
            String rewardTitle = rewardCodexPageTitle(book, "research:" + claim.id());
            if (rewardTitle != null) {
                UIElement rewardEl = new UIElement().setId("atlas_claim_reward_" + claim.id());
                rewardEl.layout(layout -> layout
                        .positionType(TaffyPosition.ABSOLUTE)
                        .left((float) PANEL_INSET).top(techTopFinal + (rowIndexStart + 1) * 16f + 6f)
                        .width((float) (PANEL_WIDTH - 2 * PANEL_INSET)).height(28f));
                Label rewardLabel = new Label().setValue(Component.translatable(
                        "astronima.atlas.claim.reward", rewardTitle));
                rewardLabel.textStyle(style -> style.textColor(0xFF9FD0A3).fontSize(9));
                CodexBlocks.wrapped(rewardLabel);
                rewardEl.addChild(rewardLabel);
                pane.addChild(rewardEl);
            }
        } else {
            ResearchStage stage = claim.stage(currentStage);
            var view = play.xponer.astronima.sim.magic.StageEvaluation.of(stage, research,
                    inventoryOf(player));

            for (play.xponer.astronima.sim.magic.StageEvaluation.Row row : view.rows()) {
                final var rowF = row;
                final int rowIndexF = rowIndex;
                UIElement rowEl = new UIElement().setId("atlas_claim_stage_row_" + rowIndexF);
                rowEl.layout(layout -> layout
                        .positionType(TaffyPosition.ABSOLUTE)
                        .left((float) PANEL_INSET).top(techTopFinal + rowIndexF * 16f)
                        .width((float) (PANEL_WIDTH - 2 * PANEL_INSET)).height(14f));
                Label rowLabel = new Label().setValue(describeRow(rowF));
                rowLabel.addClass("atlas_title");
                rowEl.addChild(rowLabel);
                pane.addChild(rowEl);
                rowIndex++;
            }
            float top = techTopFinal + rowIndex * 16f + 4f;
            // Always clickable regardless of view.allMet() — design/astra-atlas-redesign-v2.md
            // §2's own rule, applied to a second control: the destination (a real refusal that
            // names what's missing, ClaimStageCompletePayload's own `refuse()`) changes with
            // state, never whether the click does anything at all. What DOES change with state
            // is which real button style this reads as, so "not ready yet" is legible before the
            // player even presses it, not only after.
            boolean ready = view.allMet();
            UIElement complete = new UIElement().setId("atlas_claim_complete_" + claim.id())
                    .addClass(ready ? "atlas_action_btn" : "atlas_action_btn_not_ready");
            complete.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left((float) PANEL_INSET).top(top)
                    .width(96f).height(14f));
            Label completeLabel = new Label().setValue(Component.translatable(
                    ready ? "astronima.atlas.claim.complete"
                          : "astronima.atlas.claim.complete_not_ready"));
            completeLabel.textStyle(style -> style.textColor(
                    ready ? 0xFFFFF3D6 : 0xFFAAAAAA).fontSize(9));
            complete.addChild(completeLabel);
            String hoverClass = ready ? "atlas_action_btn_hover" : "atlas_action_btn_not_ready_hover";
            // Every hit target, not just complete - mouse-enter/leave do not bubble from a child
            // to its parent (CodexBlocks.clickableRow's own documented trap), so a listener on
            // complete alone left the label's own area hover-dead ("хитбокс маленький").
            for (UIElement target : List.of(complete, completeLabel)) {
                target.addEventListener(UIEvents.CLICK, event -> {
                    event.stopPropagation();
                    ClientPacketDistributor.sendToServer(
                            new ClaimStageCompletePayload(claim.id(), currentStage));
                });
                target.addEventListener(UIEvents.MOUSE_ENTER, event -> complete.addClass(hoverClass));
                target.addEventListener(UIEvents.MOUSE_LEAVE, event -> complete.removeClass(hoverClass));
            }
            pane.addChild(complete);
        }
        applyPaneSwitchAnimation(pane, modularUI, samePane);
        return pane;
    }

    /**
     * The codex page whose own content names {@code unlockId} in a {@code :::locked} fence, or
     * {@code null} if nothing does — the reverse lookup {@code claimInfoPane}'s held branch needs
     * to name the claim's real reward. Deliberately a linear scan over every page's own top-level
     * blocks rather than an index {@code CodexBook} maintains: this runs once per pane build, not
     * once per tick, and a claim's own grant only ever names one page today (rule 41 - build the
     * lookup an actual caller needs, not the one a future second caller might).
     */
    private static String rewardCodexPageTitle(CodexBook book, String unlockId) {
        for (CodexPage candidate : book.byId().values()) {
            for (CodexMarkup.Block block : candidate.blocks()) {
                if (block instanceof CodexMarkup.Block.Locked locked && locked.unlockId().equals(unlockId)) {
                    return candidate.title();
                }
            }
        }
        return null;
    }

    /** One requirement row, in the player's language: what it wants and how much is there. */
    // Found live ("почему нету ру перевода в атласе"): every branch below used to build a raw
    // Java String, which a caller then wrapped in Component.literal - meaning none of this text
    // had a translation key at all, in either language, for anyone to translate. Component.
    // translatable with %s args is the same mechanism astronima.coherence_meter.total already
    // uses; ru_ru.json only needed writing because a key finally exists to write it against.
    private static Component describeRow(play.xponer.astronima.sim.magic.StageEvaluation.Row row) {
        String mark = row.met() ? "[x] " : "[ ] ";
        if (row.requirement() instanceof play.xponer.astronima.sim.magic.Requirement.Identified id) {
            return Component.literal(mark).append(Component.translatable(
                    "astronima.atlas.requirement.identified", id.objectId().replace('_', ' ')));
        }
        if (row.requirement() instanceof play.xponer.astronima.sim.magic.Requirement.Holds holds) {
            return Component.literal(mark).append(Component.translatable(
                    "astronima.atlas.requirement.holds", holds.claimId().replace('_', ' ')));
        }
        if (row.requirement() instanceof play.xponer.astronima.sim.magic.Requirement.Owns owns) {
            return Component.literal(mark).append(Component.translatable("astronima.atlas.requirement.owns",
                    owns.count(), shortName(owns.itemId()), row.have()));
        }
        if (row.requirement() instanceof play.xponer.astronima.sim.magic.Requirement.HandsIn handsIn) {
            return Component.literal(mark).append(Component.translatable("astronima.atlas.requirement.hands_in",
                    handsIn.count(), shortName(handsIn.itemId()), row.have()));
        }
        return Component.translatable("astronima.atlas.requirement.unmet");
    }

    private static String shortName(String itemId) {
        String bare = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        return bare.replace('_', ' ');
    }

    /** The seam the stage rows read inventory through - the player looking at the screen. */
    private static play.xponer.astronima.sim.magic.InventoryView inventoryOf(Player player) {
        var inventory = player.getInventory();
        return itemId -> {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getOptional(net.minecraft.resources.Identifier.parse(itemId))
                    .orElse(null);
            if (item == null) {
                return 0;
            }
            int count = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.getItem() == item) {
                    count += stack.getCount();
                }
            }
            return count;
        };
    }

    /**
     * What a not-yet-captured node opens (design/astra-atlas-redesign-v2.md §2): real, honest
     * information rather than a locked door with nothing behind it — the object's real kind
     * (already visible in its icon's own shape once astra-atlas-redesign-v2.md §3 lands; naming it
     * here is not a spoiler) and what to actually do about it. Deliberately not the full B4 reading
     * pane astra-research.md §5 designs for an identified object — this exists only so the click
     * that used to have no listener at all now goes somewhere real.
     */
    // ---------------------------------------------------------------------------------------
    // design/astra-atlas-s2-entry.md §3: the real, authored content just written
    // (design/astra-atlas-s2c-writing.md) is reachable through the codex today, but nothing in
    // the atlas itself has ever rendered it — every pane still showed only the generated
    // technical rows (evidence status, requirement ticks). This is the first real slice of S2
    // wired into the atlas screen: a compact reading pane, sourced from the same codex page a
    // player could otherwise only find by opening the *other* book.
    //
    // Deliberately not the full S2a/S2b spec (no page-turn spread, no `:::page` pagination, no
    // shared session-free renderer extracted out of CodexBlocks): that is real, larger work, and
    // this method draws a small, self-contained subset — heading/paragraph/bullet/callout text
    // with bold spans and cross-navigation links — rather than touch CodexBlocks at all. Reusing
    // CodexBlocks directly would mean stripping its CodexUi.Session dependency first (S2a's own
    // job), a real refactor of a system this session was told is "almost in perfect condition"
    // and is not worth risking for a compact reading pane that needs a fraction of its features.
    // CodexMarkup.parse — the actual Minecraft-free parsing layer both books already share — is
    // reused as-is; only the drawing is duplicated, and only the small part of it this needs.

    /**
     * A captured/claim reading pane's real prose, sourced from the codex page carrying
     * {@code atlasKey} in its own front matter ({@link CodexBook#pageByAtlasKey}). Falls back to
     * {@code fallbackText} when no page claims that key yet — an honest degrade, not a blank
     * area, matching this tier's own "always something real to show" rule.
     *
     * <p>A real {@link ScrollerView}, not an absolute box with a fixed height guessed at: prose
     * length varies per entry and per language (Russian consistently runs longer than English —
     * design/astra-atlas-s2-entry.md §4.1 names this directly), so this pane scrolls rather than
     * clips or overflows into whatever draws beneath it. The caller still owns its own outer
     * position and size via {@code .layout(...)} on the returned element, the same additive
     * pattern {@code CodexBlocks.renderPage}'s own pill sizing already uses.
     */
    private static UIElement atlasProseScroller(String id, CodexBook book, String atlasKey,
                                                String fallbackText, Consumer<String> onSelect,
                                                Consumer<String> onSelectClaim) {
        ScrollerView scroller = new ScrollerView();
        scroller.getScrollerViewStyle()
                .mode(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode.VERTICAL)
                .verticalScrollDisplay(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay.AUTO);
        // ScrollerView's own default viewport carries a border-sprite background - a mismatch
        // against this card's own baked panel, the identical clear CodexUi's own scrollable()
        // already applies for the same reason.
        scroller.viewPort.style(s -> s.backgroundTexture(IGuiTexture.EMPTY));
        scroller.viewContainer.layout(l -> l.paddingAll(2).gapAll(6));

        CodexPage page = book.pageByAtlasKey(atlasKey);
        if (page == null) {
            Label label = new Label().setValue(Component.literal(fallbackText));
            label.textStyle(style -> style.textColor(0xFFCABFAE).fontSize(9));
            CodexBlocks.wrapped(label);
            scroller.addScrollViewChild(label);
            return scroller;
        }
        for (CodexMarkup.Block block : page.blocks()) {
            UIElement drawn = atlasProseBlock(book, block, onSelect, onSelectClaim);
            if (drawn != null) {
                scroller.addScrollViewChild(drawn);
            }
        }
        return scroller;
    }

    /**
     * One block of real prose. Exhaustive over {@link CodexMarkup.Block} on purpose (rule 29's
     * own standing pattern, matching {@code CodexBlocks.renderBlock}'s identical comment) — a
     * table, recipe, calculator, image or locked fence written into a future atlas page fails to
     * compile here rather than silently vanishing from the pane. None of the content written so
     * far uses any of them: the atlas's own generated sections already cover what a recipe
     * widget or a requirement table would otherwise show.
     */
    private static UIElement atlasProseBlock(CodexBook book, CodexMarkup.Block block,
                                             Consumer<String> onSelect, Consumer<String> onSelectClaim) {
        return switch (block) {
            case CodexMarkup.Block.Heading heading -> atlasProseHeading(heading);
            case CodexMarkup.Block.Paragraph paragraph ->
                    atlasProseParagraph(book, paragraph.spans(), onSelect, onSelectClaim);
            case CodexMarkup.Block.Bullet bullet ->
                    atlasProseParagraph(book, bullet.spans(), onSelect, onSelectClaim);
            case CodexMarkup.Block.Callout callout ->
                    atlasProseParagraph(book, callout.spans(), onSelect, onSelectClaim);
            case CodexMarkup.Block.Rule ignored -> null;
            case CodexMarkup.Block.Image ignored -> null;
            case CodexMarkup.Block.Table ignored -> null;
            case CodexMarkup.Block.Recipe ignored -> null;
            case CodexMarkup.Block.Structure ignored -> null;
            case CodexMarkup.Block.Calc ignored -> null;
            case CodexMarkup.Block.Locked ignored -> null;
        };
    }

    private static UIElement atlasProseHeading(CodexMarkup.Block.Heading heading) {
        Label label = new Label().setValue(Component.literal(heading.text()));
        label.textStyle(style -> style.textColor(0xFFE0A030).fontSize(heading.level() <= 1 ? 11 : 10));
        CodexBlocks.wrapped(label);
        return label;
    }

    /**
     * A flowing run of text — bold spans stay bold, a {@code [[link]]} to another atlas node
     * becomes a real, clickable cross-reference that opens straight to that node's own pane
     * (never leaving the atlas to do it); a link to anything else (a spectral-line entry, which
     * has no pane of its own here yet, or a plain codex-only page) renders styled but inert
     * rather than pretending to go somewhere. The same word-per-{@link Label} flow-wrap technique
     * {@code CodexBlocks.richText} already uses, written fresh here rather than reused directly —
     * that method is {@code private} to a class this pass deliberately does not touch (see this
     * section's own header comment).
     */
    private static UIElement atlasProseParagraph(CodexBook book, List<CodexMarkup.Span> spans,
                                                  Consumer<String> onSelect, Consumer<String> onSelectClaim) {
        UIElement flow = new UIElement();
        flow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .flexWrap(FlexWrap.WRAP).alignItems(AlignItems.CENTER).gapAll(3));
        for (CodexMarkup.Span span : spans) {
            switch (span) {
                case CodexMarkup.Span.Text text ->
                        atlasAppendWords(flow, text.value(), text.bold(), 0xFFCABFAE, null);
                case CodexMarkup.Span.Link link -> {
                    Runnable navigate = atlasLinkTarget(book, link.page(), onSelect, onSelectClaim);
                    atlasAppendWords(flow, link.label(), false, 0xFFE0A030, navigate);
                }
                case CodexMarkup.Span.Icon icon -> flow.addChild(atlasInlineIcon(icon.id()));
            }
        }
        return flow;
    }

    /** A small real item texture sitting inline in atlas prose, the same
     *  {@code {item:astronima:x}} markup the codex already parses (found live: "почему в атласе
     *  если просит предмет не отрисовывается сама картинка предмета" - this switch used to have a
     *  case for {@link CodexMarkup.Span.Icon} that deliberately drew nothing). Mirrors {@code
     *  CodexBlocks.inlineIcon}'s own shape exactly rather than reusing it - that method is {@code
     *  private} to a class this pass deliberately does not touch. */
    private static UIElement atlasInlineIcon(String itemId) {
        ItemSlot slot = new ItemSlot();
        Identifier at = Identifier.tryParse(itemId);
        var item = at == null ? null : BuiltInRegistries.ITEM.getOptional(at).orElse(null);
        slot.setItem(item == null ? ItemStack.EMPTY : new ItemStack(item));
        slot.layout(l -> l.width(10).height(10));
        return slot;
    }

    /** What clicking a {@code [[page]]} link inside atlas prose should do — real navigation when
     *  the target is itself a reachable atlas node, {@code null} (inert text) otherwise. Checked
     *  against the loaded book, never guessed at: a link to a page that turns out not to carry an
     *  {@code atlas:} key of its own degrades to plain styled text rather than a dead click. */
    private static Runnable atlasLinkTarget(CodexBook book, String pageId, Consumer<String> onSelect,
                                            Consumer<String> onSelectClaim) {
        CodexPage target = book.page(pageId);
        if (target == null || target.atlas().isEmpty()) {
            return null;
        }
        if (target.atlas().startsWith("object/")) {
            String objectId = target.atlas().substring("object/".length());
            return () -> onSelect.accept(objectId);
        }
        if (target.atlas().startsWith("claim/")) {
            String claimId = target.atlas().substring("claim/".length());
            return () -> onSelectClaim.accept(claimId);
        }
        // line/<id>: no pane type for a spectral line exists in the atlas yet - a real, named
        // gap (design/astra-atlas-s2-entry.md's own open scope), not guessed at here.
        return null;
    }

    /** One word per {@link Label}, matching {@code CodexBlocks.appendWords}'s own proven shape —
     *  the flow container's {@code gapAll} stands in for the space between words. Wired with a
     *  click handler only when {@code onClick} is non-null, so plain and linked runs share one
     *  code path instead of two near-duplicates. */
    private static void atlasAppendWords(UIElement flow, String text, boolean bold, int colorArgb,
                                         Runnable onClick) {
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            Component value = bold
                    ? Component.literal(word).withStyle(ChatFormatting.BOLD)
                    : Component.literal(word);
            Label label = new Label().setValue(value);
            label.textStyle(style -> style.adaptiveWidth(true).adaptiveHeight(true)
                    .textColor(colorArgb).fontSize(9));
            label.layout(l -> l.heightAuto());
            if (onClick != null) {
                label.addEventListener(UIEvents.CLICK, event -> {
                    event.stopPropagation();
                    onClick.run();
                });
            }
            flow.addChild(label);
        }
    }

    private static UIElement objectInfoPane(String objectId, Consumer<String> onSelect,
                                            Consumer<String> onSelectClaim, ModularUI modularUI,
                                            boolean samePane) {
        NamedSkyObjects.Placement placement = placementFor(objectId);
        CodexBook book = CodexLoader.book();
        CodexPage page = book.pageByAtlasKey("object/" + objectId);

        UIElement pane = new UIElement().setId("atlas_info_pane");
        pane.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0f).top(0f)
                .width((float) PANEL_WIDTH).height((float) PANEL_HEIGHT));

        String backTitle = page != null ? page.title() : objectId.replace('_', ' ');
        UIElement back = atlasLinkButton("atlas_info_back", "< " + backTitle,
                24f, (float) (PANEL_WIDTH - 2 * PANEL_INSET), () -> onSelect.accept(null));
        pane.addChild(back);

        UIElement kindRow = new UIElement().setId("atlas_info_kind");
        kindRow.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left((float) PANEL_INSET).top(44f)
                .width((float) (PANEL_WIDTH - 2 * PANEL_INSET)).height(12f));
        Label kindLabel = new Label().setValue(kindDisplayName(placement.kind()));
        kindLabel.addClass("atlas_title");
        kindRow.addChild(kindLabel);
        pane.addChild(kindRow);

        UIElement prose = atlasProseScroller("atlas_info_prose", book, "object/" + objectId,
                "Not yet captured. Point your telescope this way and take a reading.",
                onSelect, onSelectClaim);
        prose.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left((float) PANEL_INSET).top(62f)
                .width((float) (PANEL_WIDTH - 2 * PANEL_INSET))
                .height((float) (PANEL_HEIGHT - 62 - PANEL_INSET)));
        pane.addChild(prose);

        applyPaneSwitchAnimation(pane, modularUI, samePane);
        return pane;
    }

    /** The one plain-language name a kind's icon already implies by its own shape — not a spoiler,
     *  a real astronomical category (design/astra-atlas-redesign-v2.md §2's "always something real
     *  to show" rule). */
    private static Component kindDisplayName(NamedSkyObjects.Kind kind) {
        return switch (kind) {
            case EMISSION -> Component.translatable("astronima.atlas.kind.emission");
            case REFLECTION -> Component.translatable("astronima.atlas.kind.reflection");
            case PLANETARY -> Component.translatable("astronima.atlas.kind.planetary");
            case GALAXY -> Component.translatable("astronima.atlas.kind.galaxy");
        };
    }

    /** The one placement backing this object id — {@link NamedSkyObjects#ALL} is small and this is
     *  never on a hot path, so a linear scan is the honest implementation rather than a cached map
     *  that could itself drift from {@code ALL} (rule 20). */
    private static NamedSkyObjects.Placement placementFor(String objectId) {
        for (NamedSkyObjects.Placement placement : NamedSkyObjects.ALL) {
            if (Claims.objectId(placement.target()).equals(objectId)) {
                return placement;
            }
        }
        throw new IllegalArgumentException("no placement for object id " + objectId);
    }

    /**
     * One frame of a node's icon (design/astra-atlas-redesign.md §3, §6a.2): dispatches to the
     * shape each {@link NamedSkyObjects.Kind} actually is, then layers the identification payoff
     * (bloom scale, ripple ring) on top while one is playing. Called fresh every frame from a
     * {@code DynamicTexture} supplier — never touches the element tree.
     *
     * <p><strong>Real baked art</strong> (design/astra-atlas-redesign-v2.md §3), not the
     * procedural {@code SDFRectTexture} rounded-rectangle this replaced: four real shapes — a
     * glow, a diffuse dust glow with independently-twinkling flecks, a ring, a tilted ellipse —
     * generated by {@code tools/textures.py} into {@code textures/gui/atlas/icon_<kind>_<state>.png}
     * and loaded here via {@link SpriteTexture}, tinted at runtime by the object's own real
     * wavelength colour (the same shape serves every object of a kind; the colour does not).
     */
    /**
     * Which sheet cell this object shows now. The phase is derived from the object's own id
     * (stable across clients and reloads - the S1c rule for per-object timing), the frame
     * from the screen's own clock, so two same-kind objects play the same 16-frame loop out
     * of step instead of pulsing in lockstep, which reads as a UI effect rather than two
     * objects.
     */
    private static int sheetFrameFor(String objectId, double timeSeconds) {
        double phase = (objectId.hashCode() % 1000) / 1000.0 * SHEET_FRAMES;
        double position = timeSeconds / SHEET_SECONDS_PER_FRAME + phase;
        return Math.floorMod((int) Math.floor(position), SHEET_FRAMES);
    }

    /**
     * The identified state's look: the kind's own animated sheet cell (the same generative
     * process the sky shader runs - S1a's port), tinted by the object's real representative
     * colour. The sheet is painted in the shader's own colours, so the tint modulates
     * rather than replaces them - scaleBrightness keeps the shader's hue structure while
     * carrying the per-object colour that distinguishes, say, M32 from Andromeda.
     */
    private static IGuiTexture sheetFrame(NamedSkyObjects.Kind kind, String objectId,
                                          int litColor, double timeSeconds) {
        int frame = sheetFrameFor(objectId, timeSeconds);
        SpriteTexture cell = SpriteTexture.of("astronima:textures/gui/atlas/sheet_"
                + kind.name().toLowerCase(Locale.ROOT) + ".png");
        cell.setSprite((frame % SHEET_GRID) * 64, (frame / SHEET_GRID) * 64, 64, 64);
        // Found live ("объекты в атласе выглядят очень плохо") and confirmed against LDLib2's
        // own source: SpriteTexture#setColor multiplies per channel (ColorUtils.mulColor), and
        // the sheet is already baked in its own real colour straight from the shader's own
        // constants (verified: emission renders red-orange, planetary renders teal, from tools/
        // textures.py's own port). Tinting that with litColor at full strength - as this line did
        // - is a second, independent colour on top of a texture that already has one: for the
        // Helix Nebula specifically, litColor is H-alpha red (its own reddest line, §6a.2's rule)
        // multiplied against the planetary shader's own teal, and red times teal crushes every
        // channel toward zero - the near-invisible dark ring this was found from. Blending
        // litColor mostly toward white before it multiplies keeps the sheet's own real hue
        // dominant (never crushable) while still giving same-kind objects - M32 vs Andromeda -
        // a real, distinguishing cast, which is what this tint is actually for.
        cell.setColor(lerpColor(0xFFFFFFFF, litColor, SHEET_TINT_STRENGTH));
        return cell;
    }

    private static IGuiTexture kindIconFrame(NamedSkyObjects.Kind kind, boolean identified, int litColor,
                                             double timeSeconds, Double payoffElapsedSeconds,
                                             String objectId) {
        IGuiTexture shape;
        if (identified) {
            // The identified state is the object itself, moving the way the sky's shader
            // moves it (S1b). The procedural overlays below stay for the unresolved state
            // only - their twinkle was standing in for the real motion the sheet now plays.
            shape = sheetFrame(kind, objectId, litColor, timeSeconds);
        } else {
            shape = switch (kind) {
                case EMISSION -> cloudShape(litColor, identified, false, timeSeconds);
                case REFLECTION -> cloudShape(litColor, identified, true, timeSeconds);
                case PLANETARY -> ringShape(litColor, identified, timeSeconds);
                case GALAXY -> galaxyShape(litColor, identified, timeSeconds);
            };
        }
        if (identified && payoffElapsedSeconds != null) {
            double elapsed = payoffElapsedSeconds;
            if (AtlasMotion.stillPlaying(elapsed, BLOOM_DURATION_S)) {
                shape.scale((float) AtlasMotion.bloomScale(elapsed, BLOOM_DURATION_S, BLOOM_OVERSHOOT_SCALE));
            }
            if (AtlasMotion.stillPlaying(elapsed, RIPPLE_DURATION_S)) {
                double progress = AtlasMotion.rippleProgress(elapsed, RIPPLE_DURATION_S);
                return GuiTextureGroup.of(shape, rippleRing(litColor, progress));
            }
        }
        return shape;
    }

    /** §5.4 step 3: a ring that expands outward from the icon and fades as {@code progress}
     *  goes 0 -> 1. */
    private static IGuiTexture rippleRing(int color, double progress) {
        SDFRectTexture ring = new SDFRectTexture();
        ring.setRadius(ICON_RADIUS);
        ring.setColor(0);
        ring.setBorderColor(withAlpha(color, (int) Math.round(220 * (1.0 - progress))));
        ring.setStroke(1.5f);
        ring.scale((float) (1.0 + progress * 1.2));
        return ring;
    }

    /** The one baked shape for a kind and state, tinted at runtime — real per-object colour on a
     *  shape shared by every object of that {@link NamedSkyObjects.Kind} (design/astra-atlas-
     *  redesign-v2.md §3). */
    private static SpriteTexture atlasIcon(String kindName, boolean filled, int tint) {
        SpriteTexture icon = SpriteTexture.of("astronima:textures/gui/atlas/icon_" + kindName
                + (filled ? "_filled" : "_outline") + ".png");
        icon.setColor(tint);
        return icon;
    }

    /** {@code EMISSION}/{@code REFLECTION} icons: the real baked glow (§3.1), tinted by the
     *  object's own representative real line when identified, muted when not.
     *  {@code EMISSION}'s core breathes (§3's own row in the old document, mirroring
     *  {@code astronima_nebula.fsh}'s real breathing pulse at GUI scale); {@code REFLECTION}
     *  instead gets independently-twinkling star flecks layered on top of the baked dust glow —
     *  it is starlight scattered off dust, not its own emission, so its own core stays calm, and
     *  a static PNG cannot animate a fleck's own brightness, so the flecks stay procedural. */
    private static GuiTextureGroup cloudShape(int litColor, boolean identified, boolean starFlecked, double timeSeconds) {
        int tint;
        if (identified) {
            float breathe = starFlecked ? 1f
                    : (float) AtlasMotion.breathingBrightnessFraction(timeSeconds, EMISSION_BREATH_PERIOD_S, EMISSION_BREATH_AMPLITUDE);
            tint = scaleBrightness(litColor, breathe);
        } else {
            tint = UNRESOLVED_ARGB;
        }
        SpriteTexture shape = atlasIcon(starFlecked ? "reflection" : "emission", identified, tint);
        List<IGuiTexture> children = new ArrayList<>(List.of(shape));

        if (starFlecked) {
            double[][] offsets = {{-2.5, 2.2}, {2.2, 3.0}};
            double[] periods = {1.2, 1.6};
            double[] phases = {0.0, 2.4};
            for (int i = 0; i < REFLECTION_FLECK_COUNT; i++) {
                double twinkle = AtlasMotion.twinkleBrightnessFraction(timeSeconds, periods[i], phases[i]);
                int alpha = identified ? (int) Math.round(120 + 135 * twinkle) : (int) Math.round(50 * twinkle);
                SDFRectTexture fleck = new SDFRectTexture();
                fleck.setRadius(0.8f);
                fleck.setColor(withAlpha(0xFFFFFFFF, alpha));
                fleck.scale(0.12f);
                fleck.getTransform2D().translate((float) offsets[i][0], (float) offsets[i][1]);
                children.add(fleck);
            }
        }
        return GuiTextureGroup.of(children.toArray(IGuiTexture[]::new));
    }

    /**
     * {@code PLANETARY} icon: the real baked ring (§3.1) — a shell, not a solid disc, the same
     * physically honest shape even once identified (a planetary nebula does not fill in, it lights
     * up). A small bright marker orbits the rim so the ring's own rotation (§3's table in the old
     * document) is visible even though the ring shape itself is symmetric.
     */
    private static GuiTextureGroup ringShape(int litColor, boolean identified, double timeSeconds) {
        int tint = identified ? litColor : UNRESOLVED_ARGB;
        SpriteTexture ring = atlasIcon("planetary", identified, tint);

        double angle = AtlasMotion.ringRotationDegrees(timeSeconds, PLANETARY_RING_PERIOD_S);
        double rad = Math.toRadians(angle);
        float markerRadius = ICON_RADIUS - 1.5f;
        float markerX = (float) (Math.cos(rad) * markerRadius);
        float markerY = (float) (Math.sin(rad) * markerRadius);
        SDFRectTexture marker = new SDFRectTexture();
        marker.setRadius(0.8f);
        marker.setColor(identified ? withAlpha(0xFFFFFFFF, 220) : 0);
        marker.scale(0.1f);
        marker.getTransform2D().translate(markerX, markerY);

        return GuiTextureGroup.of(ring, marker);
    }

    /**
     * {@code GALAXY} icon: the real baked, tilted ellipse (§3.1) — a disc seen at an angle, with a
     * brighter central bulge already baked in — plus a slow shear (§3's table: "differential
     * rotation is the one real motion a disc galaxy has") applied as a rotation on top of the
     * baked shape's own real tilt. Deliberately the calmest shape here, as in the sky.
     */
    private static GuiTextureGroup galaxyShape(int litColor, boolean identified, double timeSeconds) {
        int tint = identified ? litColor : UNRESOLVED_ARGB;
        double shear = AtlasMotion.galaxyShearDegrees(timeSeconds, GALAXY_SHEAR_PERIOD_S, GALAXY_SHEAR_MAX_DEGREES);

        SpriteTexture disc = atlasIcon("galaxy", identified, tint);
        disc.getTransform2D().rotation((float) (GALAXY_TILT_DEGREES + shear));

        return GuiTextureGroup.of(disc);
    }

    private static final int STRIP_LEFT = PANEL_INSET;
    // 40, not 34: leaves a real 2px gap below the back link's own bottom edge (top 24 + its
    // fixed 14px height = 38) instead of touching it exactly - the touching gap was original
    // headroom this session's title-clearance bump ate into everywhere else in this panel.
    private static final int STRIP_TOP = 40;
    private static final int STRIP_WIDTH = PANEL_WIDTH - 2 * PANEL_INSET;
    private static final int STRIP_HEIGHT = 100;
    private static final int STRIP_SAMPLE_COUNT = STRIP_WIDTH / 4; // 4px per bar
    private static final String STRIP_BACKGROUND_COLOR = "#40000000";
    private static final int PALETTE_TOP = STRIP_TOP + STRIP_HEIGHT + 8;
    private static final int PALETTE_SWATCH_SIZE = 14;
    private static final int PALETTE_GAP = 4;
    private static final String ACCENT_HIGHLIGHT_COLOR = "#FFFFFFFF";

    /**
     * A captured object's spectrum strip (design/astra-research.md §4a.2, design/astra-research-
     * m4b.md §3): the real, continuous rainbow a prism spreads light into — every bar coloured by
     * its own sample wavelength via {@link WavelengthColor}, always, identified or not — with a
     * live curve of height standing for brightness drawn over it: a low floor, a few broad decoy
     * bumps, and this object's own real lines as narrow spikes shifted by its own Doppler motion.
     * Clicking the curve with a filter armed in the palette below it sends a
     * {@link SpectrumDecodeAttemptPayload}; the server, never this screen, decides whether it
     * actually matches there.
     *
     * <p><strong>design/astra-atlas-redesign.md §5.4 step 1:</strong> while this object's
     * identification payoff is still playing, its locked line's own bar flares white and settles
     * back to its real wavelength colour ({@link AtlasMotion#stripFlashFraction}) instead of
     * drawing as a flat static colour the instant the strip reopens.
     *
     * <p><strong>Revised after the first cut, in play:</strong> that first cut coloured every bar
     * a flat neutral grey until identified, on the theory that colour should be the reward for
     * identifying a line rather than given away up front — reported back as unreadable as a
     * spectrum at all ("все полосочки тупо белые, как понять спектр непонятно"). §4a.2 step 1 was
     * always clear that the strip *is* the rainbow; only the curve's *height* (decoy vs. real
     * peak) is the thing to actually read, never the colour, which the sample's own wavelength
     * already fixes honestly. Identification instead marks its bar with a small bright tick above
     * it, a locked-in flag rather than a colour change nothing was ever hiding.
     *
     * <p>Also revised: the first cut asked the player to hold a {@link FilterTokenItem} in a
     * hand to apply it, silently failing when tried in the off hand and, worse, giving a screen no
     * way to show which filter was "selected". A player owns filters the same way they own a wire
     * coil or a wrench between uses — this palette lets them arm one straight from what they are
     * carrying, and the server checks ownership, not hand placement.
     */
    private static UIElement spectrumStrip(ObservationTarget target, String objectId,
                                           ResearchState research, Player player, SpectralLine armedFilter,
                                           Consumer<String> onSelect, Consumer<String> onSelectClaim,
                                           Consumer<SpectralLine> onArmFilter,
                                           Map<String, Long> payoffStartNanos, ModularUI modularUI,
                                           boolean samePane) {
        boolean identified = research.identified(objectId);
        CodexBook book = CodexLoader.book();
        UIElement strip = new UIElement().setId("atlas_strip_" + objectId);
        strip.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0f).top(0f)
                .width(PANEL_WIDTH).height(PANEL_HEIGHT));
        strip.addEventListener(UIEvents.CLICK, event -> {
            Vector2f local = strip.worldToLocalLayoutOffset(new Vector2f(event.x, event.y));
            if (local.x < STRIP_LEFT || local.x > STRIP_LEFT + STRIP_WIDTH
                    || local.y < STRIP_TOP || local.y > STRIP_TOP + STRIP_HEIGHT) {
                return; // clicked the header/palette/hint area, not the curve itself
            }
            if (armedFilter == null) {
                net.minecraft.client.Minecraft.getInstance().gui.setOverlayMessage(
                        Component.translatable("astronima.atlas.filter.arm_first")
                                .withStyle(ChatFormatting.YELLOW), false);
                return;
            }
            // Found live ("визуально высота в одном месте а нажать надо на 1 полосу от неё"):
            // snapped to the SAME bar-centre formula the drawing loop below uses, not a raw
            // continuous local.x. A bar is ~3nm wide and the server's own match tolerance
            // (Spectrum.DOPPLER_MATCH_TOLERANCE_NM) is only 1.5nm either side - clicking near a
            // bar's edge under the old continuous mapping could land outside that window despite
            // landing inside the visually correct bar's own rectangle. Snapping first means every
            // click anywhere in bar i always reports exactly bar i's own centre wavelength - the
            // one number its own height was computed from - so "click the tall bar" and "click
            // the bar whose centre is in tolerance" are the same click again.
            int barIndex = Math.clamp((int) ((local.x - STRIP_LEFT) / (STRIP_WIDTH / (double) STRIP_SAMPLE_COUNT)),
                    0, STRIP_SAMPLE_COUNT - 1);
            double candidateNm = Spectrum.VISIBLE_MIN_NM
                    + (barIndex + 0.5) / STRIP_SAMPLE_COUNT * (Spectrum.VISIBLE_MAX_NM - Spectrum.VISIBLE_MIN_NM);
            ClientPacketDistributor.sendToServer(
                    new SpectrumDecodeAttemptPayload(objectId, candidateNm, armedFilter));
        });

        UIElement back = atlasLinkButton("atlas_strip_back",
                "< " + objectId.replace('_', ' ') + (identified ? " (identified)" : ""),
                24f, (float) STRIP_WIDTH, () -> onSelect.accept(null));
        strip.addChild(back);

        UIElement background = new UIElement().setId("atlas_strip_bg");
        background.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left((float) STRIP_LEFT).top((float) STRIP_TOP)
                .width((float) STRIP_WIDTH).height((float) STRIP_HEIGHT));
        background.style(s -> s.background(new TextureValue("rect(" + STRIP_BACKGROUND_COLOR + ")").compute()));
        strip.addChild(background);

        // Real lines' nearest sample gets a locked tick once identified — nearest-sample rather
        // than a fixed wavelength radius, so the actual tallest bar for a line always gets picked
        // regardless of exactly where it falls between two sample centres.
        java.util.Set<Integer> lockedSamples = new java.util.HashSet<>();
        if (identified) {
            for (SpectralLine line : Spectrum.capture(target.temperatureK(), target.lines(), 0.0)) {
                double shiftedNm = line.wavelengthNm() + target.dopplerShiftNm();
                int nearestSample = (int) Math.round(
                        (shiftedNm - Spectrum.VISIBLE_MIN_NM)
                                / (Spectrum.VISIBLE_MAX_NM - Spectrum.VISIBLE_MIN_NM) * STRIP_SAMPLE_COUNT);
                lockedSamples.add(Math.clamp(nearestSample, 0, STRIP_SAMPLE_COUNT - 1));
            }
        }

        float barWidth = (float) STRIP_WIDTH / STRIP_SAMPLE_COUNT;
        for (int i = 0; i < STRIP_SAMPLE_COUNT; i++) {
            double wavelengthNm = Spectrum.VISIBLE_MIN_NM
                    + (i + 0.5) / STRIP_SAMPLE_COUNT * (Spectrum.VISIBLE_MAX_NM - Spectrum.VISIBLE_MIN_NM);
            double intensity = Spectrum.intensity(target, wavelengthNm);
            float barHeight = Math.max(1f, (float) (intensity * STRIP_HEIGHT));
            int realColor = withAlpha(WavelengthColor.rgb(wavelengthNm), 255);
            float barLeft = STRIP_LEFT + i * barWidth;
            float barTop = STRIP_TOP + STRIP_HEIGHT - barHeight;

            UIElement bar = new UIElement().setId("atlas_strip_bar_" + objectId + "_" + i);
            bar.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(barLeft)
                    .top(barTop)
                    .width(Math.max(1f, barWidth - 1f))
                    .height(barHeight));
            if (lockedSamples.contains(i)) {
                // §5.4 step 1: this bar flares white then settles to its real colour while this
                // object's payoff is playing - a DynamicTexture only for the bar(s) actually
                // affected, every other bar stays the cheap static TextureValue below.
                bar.style(s -> s.background(DynamicTexture.of(() -> {
                    Long startNanos = payoffStartNanos.get(objectId);
                    if (startNanos == null) {
                        return colorTexture(realColor);
                    }
                    double elapsed = (System.nanoTime() - startNanos) / 1.0e9;
                    if (!AtlasMotion.stillPlaying(elapsed, STRIP_FLASH_DURATION_S)) {
                        return colorTexture(realColor);
                    }
                    double flash = AtlasMotion.stripFlashFraction(elapsed, STRIP_FLASH_DURATION_S);
                    return colorTexture(lerpColor(realColor, 0xFFFFFFFF, flash));
                })));
            } else {
                bar.style(s -> s.background(colorTexture(realColor)));
            }
            strip.addChild(bar);

            if (lockedSamples.contains(i)) {
                UIElement tick = new UIElement().setId("atlas_strip_tick_" + objectId + "_" + i);
                tick.layout(layout -> layout
                        .positionType(TaffyPosition.ABSOLUTE)
                        .left(barLeft - 1f).top(barTop - 4f)
                        .width(Math.max(1f, barWidth) + 1f).height(3f));
                tick.style(s -> s.background(new TextureValue("rect(" + ACCENT_HIGHLIGHT_COLOR + ")").compute()));
                strip.addChild(tick);
            }
        }

        strip.addChild(filterPalette(player, armedFilter, onArmFilter));

        UIElement hint = new UIElement().setId("atlas_strip_hint");
        hint.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left((float) PANEL_INSET).top((float) (PALETTE_TOP + PALETTE_SWATCH_SIZE + 6))
                .width((float) STRIP_WIDTH).height(24f));
        Label hintLabel = new Label().setValue(Component.translatable(identified
                ? "astronima.atlas.strip.hint_identified"
                : "astronima.atlas.strip.hint_unidentified"));
        hintLabel.addClass("atlas_title");
        hint.addChild(hintLabel);
        strip.addChild(hint);

        // The object's own real write-up (design/astra-atlas-s2c-writing.md §4.1), sharing the
        // doubled card's own new headroom below the decode UI above — this is the pane a player
        // actually spends the most time in, and until now it carried no prose at all, identified
        // or not.
        UIElement prose = atlasProseScroller("atlas_strip_prose_" + objectId, book, "object/" + objectId,
                objectId.replace('_', ' ') + " — no entry written for this object yet.",
                onSelect, onSelectClaim);
        prose.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left((float) PANEL_INSET).top(196f)
                .width((float) STRIP_WIDTH)
                .height((float) (PANEL_HEIGHT - 196 - PANEL_INSET)));
        strip.addChild(prose);

        applyPaneSwitchAnimation(strip, modularUI, samePane);
        return strip;
    }

    /** The "just opened" pop-in every dynamic-layer sub-pane plays - skipped when {@code
     *  samePane} says this is the same pane rebuilding over an internal change (arming a filter,
     *  for instance), not a real navigation, so it does not look like the whole tab reloaded
     *  (found live: "вкладка как-будто бы перезагружается"). Opacity is still forced to 1 in
     *  that case - the element was just freshly built and defaults to fully opaque, but saying so
     *  explicitly here is cheaper than a reader wondering whether it was left implicit on purpose. */
    private static void applyPaneSwitchAnimation(UIElement pane, ModularUI modularUI, boolean samePane) {
        if (samePane) {
            pane.style(s -> s.opacity(1f));
            return;
        }
        pane.style(s -> s.opacity(0f).transform2D(new Transform2D().scale(PANE_SWITCH_START_SCALE)));
        StyleAnimation.of(modularUI).select(pane)
                .style(PropertyRegistry.OPACITY, 1.0f)
                .duration(PANE_SWITCH_DURATION_S)
                .start();
        StyleAnimation.of(modularUI).select(pane)
                .style(PropertyRegistry.TRANSFORM_2D, new Transform2D().scale(1.0f))
                .duration(PANE_SWITCH_DURATION_S)
                .start();
    }

    /**
     * The row of filters this player actually owns (design/astra-research-m4b.md §3, revised) —
     * click one to arm it, click the armed one again to disarm. Reading the local player's own
     * inventory client-side is a display choice only; the server re-checks real ownership before
     * ever acting on a decode attempt.
     */
    private static UIElement filterPalette(Player player, SpectralLine armedFilter,
                                           Consumer<SpectralLine> onArmFilter) {
        UIElement row = new UIElement().setId("atlas_strip_palette");
        row.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left((float) STRIP_LEFT).top((float) PALETTE_TOP)
                .width((float) STRIP_WIDTH).height((float) PALETTE_SWATCH_SIZE));

        List<SpectralLine> owned = ownedFilters(player);
        if (owned.isEmpty()) {
            Label none = new Label().setValue(Component.translatable("astronima.atlas.filter.none_owned"));
            none.addClass("atlas_title");
            row.addChild(none);
            return row;
        }

        int i = 0;
        for (SpectralLine line : owned) {
            float left = i * (PALETTE_SWATCH_SIZE + PALETTE_GAP);
            boolean armed = line == armedFilter;

            if (armed) {
                UIElement halo = new UIElement().setId("atlas_strip_palette_halo_" + line.id());
                halo.layout(layout -> layout
                        .positionType(TaffyPosition.ABSOLUTE)
                        .left(left - 1f).top(-1f)
                        .width((float) PALETTE_SWATCH_SIZE + 2f).height((float) PALETTE_SWATCH_SIZE + 2f));
                halo.style(s -> s.background(new TextureValue("rect(" + ACCENT_HIGHLIGHT_COLOR + ")").compute()));
                row.addChild(halo);
            }

            UIElement swatch = new UIElement().setId("atlas_strip_palette_" + line.id());
            swatch.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(left).top(0f)
                    .width((float) PALETTE_SWATCH_SIZE).height((float) PALETTE_SWATCH_SIZE));
            String hex = String.format(Locale.ROOT, "#%06X", WavelengthColor.rgb(line.wavelengthNm()));
            swatch.style(s -> s.background(new TextureValue("rect(" + hex + ")").compute()));
            swatch.addEventListener(UIEvents.CLICK, event -> {
                event.stopPropagation();
                onArmFilter.accept(line);
            });
            row.addChild(swatch);
            i++;
        }
        return row;
    }

    /** {@code Inventory.getItem(int)} walks the 36 main slots plus every equipment slot (offhand
     *  included) uniformly — the same reason {@code SpectrumDecodeAttemptPayload}'s own server-side
     *  ownership check uses one indexed loop rather than separate per-compartment fields. */
    private static List<SpectralLine> ownedFilters(Player player) {
        EnumSet<SpectralLine> owned = EnumSet.noneOf(SpectralLine.class);
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof FilterTokenItem token) {
                owned.add(token.line());
            }
        }
        return List.copyOf(owned);
    }

    /**
     * A single representative colour for an object with more than one real line: the reddest
     * (longest-wavelength) one, since that is usually the line a real photograph of it is named
     * for (H-alpha for every emission/planetary nebula in this catalogue). Real physics feeding
     * the art, not an art-directed guess (rule 46; {@link WavelengthColor}'s own reason to exist).
     */
    private static String representativeColorHex(ObservationTarget target) {
        SpectralLine reddest = target.lines().stream()
                .max(Comparator.comparingDouble(SpectralLine::wavelengthNm))
                .orElse(null);
        int rgb = reddest == null ? 0xFFFFFF : WavelengthColor.rgb(reddest.wavelengthNm());
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    /** {@link #representativeColorHex} as an opaque {@code 0xAARRGGBB} int, for the icon builders
     *  below — kept as a real physics-derived colour throughout, per rule 46, including for
     *  {@code REFLECTION} objects: design/astra-research.md §6a.2's table names "filled blue" for
     *  that kind specifically, but this project's own standing rule is that a colour is always
     *  derived from real spectral data, never chosen by fiat — so {@code PLEIADES} gets whatever
     *  colour its own real {@code HELIUM_I} line actually is, the same as every other kind, rather
     *  than a hardcoded blue that would not always be true for every possible reflection-nebula
     *  entry this catalogue might grow. */
    private static int representativeColorArgb(ObservationTarget target) {
        String hex = representativeColorHex(target);
        return parseArgb("#FF" + hex.substring(1));
    }

    /** Parses an {@code #AARRGGBB} (8 hex digits) or {@code #RRGGBB} (6, alpha forced opaque)
     *  string into a raw int, without pulling in LDLib2's own {@code ColorUtils} just for this —
     *  keeps this file's colour handling in one place rather than mixing two parsers. */
    private static int parseArgb(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        long value = Long.parseLong(digits.length() == 6 ? "FF" + digits : digits, 16);
        return (int) value;
    }

    private static int withAlpha(int argb, int alpha) {
        int clampedAlpha = Math.clamp(alpha, 0, 255);
        return (clampedAlpha << 24) | (argb & 0x00FFFFFF);
    }

    private static int scaleBrightness(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.clamp(Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        int g = Math.clamp(Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        int b = Math.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpColor(int fromArgb, int toArgb, double t) {
        double clampedT = Math.clamp(t, 0.0, 1.0);
        int a = lerpChannel((fromArgb >>> 24) & 0xFF, (toArgb >>> 24) & 0xFF, clampedT);
        int r = lerpChannel((fromArgb >> 16) & 0xFF, (toArgb >> 16) & 0xFF, clampedT);
        int g = lerpChannel((fromArgb >> 8) & 0xFF, (toArgb >> 8) & 0xFF, clampedT);
        int b = lerpChannel(fromArgb & 0xFF, toArgb & 0xFF, clampedT);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, double t) {
        // Math.round(double) returns long, and Math.clamp(long, int, int) returns long too -
        // an explicit narrowing cast back to int, not an oversight.
        return (int) Math.clamp(Math.round(from + (to - from) * t), 0, 255);
    }

    private static IGuiTexture colorTexture(int argb) {
        return new TextureValue("rect(" + String.format(Locale.ROOT, "#%08X", argb) + ")").compute();
    }

    private AtlasUi() {}
}
