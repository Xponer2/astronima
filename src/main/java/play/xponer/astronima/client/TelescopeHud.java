package play.xponer.astronima.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.hud.HudScale;
import play.xponer.astronima.network.TelescopeCaptureAttemptPayload;
import play.xponer.astronima.sim.optics.NamedSkyObjects;
import play.xponer.astronima.sim.optics.SkyRotation;
import play.xponer.astronima.sim.optics.TelescopeSearch;
import play.xponer.astronima.telescope.TelescopeMountEntity;
import play.xponer.astronima.telescope.TelescopeOcclusion;

import java.util.Set;

/**
 * The eyepiece, design/astra-telescope.md §3 (v3). This draws on top of a camera that has
 * genuinely moved — {@code TelescopeCamera} points {@code Minecraft}'s own camera straight at the
 * {@link TelescopeMountEntity} (PLAN.md rule 79, not the ridden-passenger mechanism v2 tried and
 * found unreliable across three rounds) — so the vignette here is decoration around a real
 * narrowed view, not a substitute for one: {@link #onComputeFov} narrows the *real* field of view,
 * the same mechanism vanilla's own Spyglass already uses, and this class only draws the vignette,
 * reticle and hold-arc on top of that real render (§3.1's "only three things sit above the real
 * render"). The vignette itself is a real dark-to-clear gradient closing in from the screen's own
 * edges (§3, corrected after playtest called a thin dotted ring "not a vignette" and named the
 * real shape instead), not a ring outline.
 *
 * <p>No aim state of its own: "currently scoping" is simply {@link TelescopeCamera#currentMount()},
 * read fresh every frame — nothing here tracks a second copy of that same state.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class TelescopeHud {

    /** Reads {@link TelescopeMountEntity#FOV_DIVISOR} — one number, shared with the sensitivity
     * compensation that same class applies, so the two can never quietly drift apart (rule 46). */
    private static final float FOV_DIVISOR = TelescopeMountEntity.FOV_DIVISOR;

    /** Every vanilla HUD element playtest found still showing through the eyepiece
     * ("видно полный худ") — a modal that claims the whole screen (§3's own "claims space" note)
     * has no business leaving the hotbar, crosshair, survival bars or held-item name floating on
     * top of it. Deliberately not touching CHAT, TAB_LIST, BOSS_OVERLAY, TITLE or SUBTITLE_OVERLAY
     * — those stay legible regardless of what the player is looking through. Third-party overlays
     * (Jade's block tooltip was also reported still showing) are not covered here — this only
     * reaches layers registered through vanilla's own {@code VanillaGuiLayers}, named as a real,
     * open limitation rather than silently left unfixed. */
    private static final Set<Identifier> HIDDEN_WHILE_SCOPED = Set.of(
            VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.CROSSHAIR,
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.AIR_LEVEL,
            VanillaGuiLayers.VEHICLE_HEALTH,
            VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.CONTEXTUAL_INFO_BAR,
            VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND,
            VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.EFFECTS);

    /** How large a circle stays genuinely clear at the centre — this is the "tube bore" itself,
     * not a decorative frame around it (§3, corrected after playtest: "виньетка = чёрное вокруг
     * самой трубы, чем ближе к центру тем более видно становится"). Smaller than v2's old ring
     * radius on purpose: most of the screen is meant to read as the inside of the tube, with only
     * the sky glimpsed through its actual bore. */
    private static final double CLEAR_RADIUS_FRACTION = 0.30;
    private static final int VIGNETTE_BANDS = 40;

    private static int holdTicks = 0;

    /** design/astra-telescope.md §2.1 v3: nothing rides the mount any more, so "currently scoping"
     * is "does {@code TelescopeCamera} currently point the camera at one" — the client-only
     * bookkeeping that mechanism actually needs, not a second copy of it here. */
    private static @Nullable TelescopeMountEntity currentMount() {
        return TelescopeCamera.currentMount();
    }

    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        TelescopeMountEntity mount = currentMount();
        if (mount == null || minecraft.level == null) {
            holdTicks = 0;
            return;
        }

        // The player's own rotation is held at a fixed baseline while observing (TelescopeCamera) —
        // the mount's own rotation is the only honest source for "what is this aimed at" now.
        Vec3 look = mount.getLookAngle();
        SkyRotation.Vec3 direction = new SkyRotation.Vec3(look.x(), look.y(), look.z());
        Vec3 eyepiece = Vec3.atCenterOf(mount.telescopePos()).add(0.0, 0.5, 0.0);
        boolean clear = TelescopeOcclusion.isClear(minecraft.level, eyepiece, look);
        double closeness = clear ? closenessToNearest(direction, minecraft.level.getGameTime()) : -1.0;
        boolean onTarget = clear && TelescopeSearch.isOnTarget(closeness);

        holdTicks = TelescopeSearch.advanceHold(holdTicks, onTarget);
        if (TelescopeSearch.isCaptureComplete(holdTicks)) {
            ClientPacketDistributor.sendToServer(new TelescopeCaptureAttemptPayload(mount.telescopePos()));
            holdTicks = 0;
        }
    }

    private static double closenessToNearest(SkyRotation.Vec3 direction, long gameTime) {
        NamedSkyObjects.Placement nearest = NamedSkyObjects.nearest(direction, gameTime);
        SkyRotation.Vec3 current =
                SkyRotation.currentDirection(nearest.fixedDirection(), gameTime);
        double angle = SkyRotation.angleBetweenDegrees(direction, current);
        return TelescopeSearch.closeness(angle, nearest.angularToleranceDegrees());
    }

    /** The real narrow-angle view (design/astra-telescope.md §0.1's Spyglass precedent) — not a
     * picture standing in for one. */
    @SubscribeEvent
    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (currentMount() != null) {
            event.setFOV(event.getFOV() / FOV_DIVISOR);
        }
    }

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(Astronima.MODID, "telescope"), TelescopeHud::render);
    }

    /** Hides the vanilla HUD elements named in {@link #HIDDEN_WHILE_SCOPED} while scoped —
     * directly answers "видно полный худ" from playtest. */
    @SubscribeEvent
    private static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (HIDDEN_WHILE_SCOPED.contains(event.getName())
                && currentMount() != null) {
            event.setCanceled(true);
        }
    }

    /** Hides the held item in first person while scoped — "если взять газовый анализатор его
     * тоже видно будет" (any held item, not only the analyzer specifically). */
    @SubscribeEvent
    private static void onRenderHand(RenderHandEvent event) {
        if (currentMount() != null) {
            event.setCanceled(true);
        }
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        TelescopeMountEntity mount = currentMount();
        if (mount == null || minecraft.level == null) {
            return;
        }

        Vec3 look = mount.getLookAngle();
        SkyRotation.Vec3 direction = new SkyRotation.Vec3(look.x(), look.y(), look.z());
        Vec3 eyepiece = Vec3.atCenterOf(mount.telescopePos()).add(0.0, 0.5, 0.0);
        boolean clear = TelescopeOcclusion.isClear(minecraft.level, eyepiece, look);
        double closeness = clear ? closenessToNearest(direction, minecraft.level.getGameTime()) : -1.0;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // A modal, not a gauge: the eyepiece claims the whole screen while active, so the layout
        // module has to know that — the same "claims space for something that is not a module
        // panel" contract v1 already established, unchanged by the render underneath it becoming
        // real instead of a vignette.
        play.xponer.astronima.client.hud.HudPanels.reserve(
                "telescope", 0, 0, width, height, System.currentTimeMillis() / 1000.0);
        int cx = width / 2;
        int cy = height / 2;
        int clearRadius = (int) (Math.min(width, height) * CLEAR_RADIUS_FRACTION);

        drawVignette(graphics, width, height, clearRadius);
        drawReticle(graphics, cx, cy, clear, closeness);
        if (holdTicks > 0) {
            drawHoldArc(graphics, cx, cy, clearRadius);
        }
    }

    /** A real vignette: black at the screen's own edges, fading to fully clear at
     * {@code clearRadius} — corrected after playtest called the previous dotted ring "не
     * виньетка" and named exactly this shape instead. {@code GuiGraphicsExtractor} has no radial
     * gradient primitive, so this reuses the same nested-square-band technique
     * {@code BreathHud.drawVeil} already proves out for an analogous "vision narrowing" effect in
     * this exact codebase, at full strength (always fully opaque at the edge, never a fading
     * status effect) and scaled from the real screen size rather than a fixed pixel count, so the
     * fall-off looks the same proportion of the view at any resolution. */
    private static void drawVignette(GuiGraphicsExtractor graphics, int width, int height, int clearRadius) {
        double maxInset = Math.min(width, height) / 2.0 - clearRadius;
        if (maxInset <= 0) {
            return;
        }
        double bandWidth = maxInset / VIGNETTE_BANDS;
        for (int i = 0; i < VIGNETTE_BANDS; i++) {
            int alpha = Math.round(255 * (1.0F - i / (float) VIGNETTE_BANDS));
            if (alpha <= 0) {
                continue;
            }
            int colour = alpha << 24;
            int inset = (int) Math.round(i * bandWidth);
            int thickness = (int) Math.ceil(bandWidth) + 1;
            graphics.fill(0, inset, width, inset + thickness, colour);
            graphics.fill(0, height - inset - thickness, width, height - inset, colour);
            graphics.fill(inset, 0, inset + thickness, height, colour);
            graphics.fill(width - inset - thickness, 0, width - inset, height, colour);
        }
    }

    /** The inner progress arc, drawn as a partial ring of brighter dots — replaces v1's separate
     * bar floating below the viewport with one that actually belongs to the ring it is timing. */
    private static void drawHoldArc(GuiGraphicsExtractor graphics, int cx, int cy, int radius) {
        double fraction = Math.min(1.0, holdTicks / (double) TelescopeSearch.LOCK_TICKS);
        int arcRadius = radius - 6;
        int steps = (int) (90 * fraction);
        for (int i = 0; i <= steps; i++) {
            double angle = -Math.PI / 2.0 + (Math.PI * 2.0 * i) / 90.0;
            int x = cx + (int) (Math.cos(angle) * arcRadius);
            int y = cy + (int) (Math.sin(angle) * arcRadius);
            graphics.fill(x - 1, y - 1, x + 1, y + 1, HudScale.COLOR_GOOD);
        }
    }

    private static final int RETICLE = 10;

    private static void drawReticle(GuiGraphicsExtractor graphics, int cx, int cy,
                                    boolean clear, double closeness) {
        int colour = reticleColour(clear, closeness);
        graphics.fill(cx - RETICLE, cy - 1, cx - 3, cy + 1, colour);
        graphics.fill(cx + 3, cy - 1, cx + RETICLE, cy + 1, colour);
        graphics.fill(cx - 1, cy - RETICLE, cx + 1, cy - 3, colour);
        graphics.fill(cx - 1, cy + 3, cx + 1, cy + RETICLE, colour);
    }

    /** Grey while blocked (nothing to search yet), warming from grey through amber to a locked
     * green as closeness climbs — unchanged behaviour from v1; only the rectangular home this used
     * to live in was the complaint (§3.1). */
    private static int reticleColour(boolean clear, double closeness) {
        if (!clear) {
            return HudScale.COLOR_LABEL;
        }
        if (closeness >= 0.0) {
            return HudScale.COLOR_GOOD;
        }
        if (closeness > -0.6) {
            return HudScale.COLOR_WARN;
        }
        return HudScale.COLOR_LABEL;
    }

    private TelescopeHud() {}
}
