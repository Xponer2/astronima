package play.xponer.astronima.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.hud.HudPaint;
import play.xponer.astronima.client.hud.HudScale;
import play.xponer.astronima.item.OxygenTanks;
import play.xponer.astronima.gravity.LocomotionAidEvents;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.physio.Hypothermia;
import play.xponer.astronima.sim.physio.Consciousness;

/**
 * Two rows above the hotbar: what your body has left, and what your suit has left.
 *
 * <p>They are separate because they fail separately, and conflating them is exactly
 * the confusion that got reported as a bug — a full tank on a broken mount looks like
 * plenty of oxygen right up until you collapse. Shown side by side, a full supply row
 * over a draining reserve row says "your suit is not feeding you" without a word of
 * text.
 *
 * <p>Drawn as pips rather than a bar because vanilla's air bubbles already taught
 * everyone what an emptying row of round things above the hotbar means. Reusing that
 * vocabulary costs the player nothing to learn.
 *
 * <p>The reserve row is always visible while it is not full, and the supply row only
 * when something is fitted — an empty row would be a permanent nag for players who
 * have not built a suit yet.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class BreathHud {
    private static final int PIPS = 10;
    private static final int PIP = 8;
    private static final int PIP_GAP = 1;

    /** Rows sit above the hotbar, clear of vanilla's own bars. */
    private static final int BOTTOM_OFFSET = 52;
    private static final int ROW_GAP = 10;

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(Astronima.MODID, "breath"), BreathHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        float reserve = minecraft.player.getData(ModAttachments.O2_RESERVE);
        float supply = OxygenTanks.fittedTankFraction(minecraft.player);
        float core = minecraft.player.getData(ModAttachments.CORE_TEMPERATURE_K);

        drawVeil(graphics, reserve);

        boolean showReserve = reserve < 0.999f;
        // Shown from the first tenth of a degree, long before the first symptom. That gap is
        // the entire rule-7 obligation for this hazard: the danger is slow, so the reading
        // has to arrive while every remedy is still reachable rather than alongside the harm.
        boolean showCore = core < Hypothermia.NORMAL_CORE_K - 0.1;
        if (!showReserve && supply < 0 && !showCore) {
            return; // nothing to say
        }

        int x = graphics.guiWidth() / 2 + 10;
        int y = graphics.guiHeight() - BOTTOM_OFFSET;

        // The three rows this strip can show, as one rectangle: a pop-up readout that landed on
        // the oxygen gauge would cover the one number a player checks by reflex.
        int stripWidth = PIPS * (PIP + PIP_GAP);
        play.xponer.astronima.client.hud.HudPanels.reserve("breath", x, y - ROW_GAP - 2,
                stripWidth, ROW_GAP * 2 + PIP + 4, System.currentTimeMillis() / 1000.0);

        if (supply >= 0) {
            drawRow(graphics, x, y - ROW_GAP, supply, HudScale.COLOR_INFO);
        }
        if (showReserve) {
            drawRow(graphics, x, y, reserve, reserveColour(reserve));
        }
        if (showCore) {
            drawRow(graphics, x, y + ROW_GAP, coreFraction(core), coreColour(core));
        }
        drawClosureRate(graphics, x, y);
        drawBootGrip(graphics, x, y);
    }

    /**
     * Whether the boots have anything to hold on to.
     *
     * <p>Rules 9 and 18 together. Magnetic soles do nothing at all on rock, and a player who
     * has fitted them and noticed no difference needs an answer that is not silence — the
     * quiet failure is worse than the loud one, because it looks like a broken item rather
     * than a floor made of the wrong thing.
     *
     * <p>Drawn only for someone who is actually wearing them, so it is a row you opted into
     * rather than clutter, and computed on the client because both halves — what is fitted
     * and what is underfoot — are things it already knows.
     */
    private static void drawBootGrip(GuiGraphicsExtractor graphics, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !LocomotionAidEvents.hasBoots(minecraft.player)) {
            return;
        }
        boolean gripping = minecraft.player.onGround() && LocomotionAidEvents.isFerrous(
                minecraft.player.level().getBlockState(
                        minecraft.player.blockPosition().below()));
        drawRow(graphics, x, y + ROW_GAP * 2, gripping ? 1f : 0.15f,
                gripping ? HudScale.COLOR_GOOD : HudScale.COLOR_LABEL);
    }

    /**
     * How fast you are going, against how fast you can survive arriving.
     *
     * <p>The rule 7 instrument for kinetic impact, and the one reading in this HUD that is
     * <strong>computed on the client rather than sent to it</strong>: your own speed is
     * something the client knows first-hand and a tick sooner than any packet could say it,
     * and a closure-rate indicator that lagged the closure would be worse than none.
     *
     * <p>Hidden while you are moving at speeds that cannot hurt, because a row that is on
     * screen during ordinary walking is a row nobody reads during a fall. Real spacecraft
     * carry closure-rate indicators for exactly this problem.
     */
    private static void drawClosureRate(GuiGraphicsExtractor graphics, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        double speed = minecraft.player.position()
                .subtract(minecraft.player.xOld, minecraft.player.yOld, minecraft.player.zOld)
                .length();
        if (speed < Microgravity.FREE_SPEED * 0.6) {
            return;
        }
        drawRow(graphics, x, y - ROW_GAP * 2, (float) Microgravity.gauge(speed),
                Microgravity.isSurvivableArrival(speed) ? HudScale.COLOR_WARN
                        : HudScale.COLOR_BAD);
    }

    /**
     * How much of the way from severe hypothermia back to normal this core is.
     *
     * <p>Scaled across the band that matters rather than from absolute zero: a row measuring
     * 310 K against 0 K would sit permanently full and move imperceptibly, which is a row that
     * teaches nothing. Empty here means the heart is at risk.
     */
    private static float coreFraction(float coreK) {
        double span = Hypothermia.NORMAL_CORE_K - Hypothermia.SEVERE_K;
        return (float) Math.clamp((coreK - Hypothermia.SEVERE_K) / span, 0.0, 1.0);
    }

    /** The row changes colour where the player's situation changes, as the reserve row does. */
    private static int coreColour(float coreK) {
        return switch (Hypothermia.classify(coreK)) {
            case NONE -> HudScale.COLOR_INFO;
            case MILD -> HudScale.COLOR_WARN;
            default -> HudScale.COLOR_BAD;
        };
    }

    /** One row of pips, partial pip included so small changes are still visible. */
    private static void drawRow(GuiGraphicsExtractor graphics, int x, int y,
                                float fraction, int colour) {
        float filled = Math.clamp(fraction, 0f, 1f) * PIPS;
        for (int i = 0; i < PIPS; i++) {
            int px = x + i * (PIP + PIP_GAP);
            // The empty socket, so the row's length reads as capacity.
            graphics.fill(px, y, px + PIP, y + PIP, 0x90000000);
            graphics.fill(px + 1, y + 1, px + PIP - 1, y + PIP - 1, 0xFF1A1F24);

            float pip = Math.clamp(filled - i, 0f, 1f);
            if (pip <= 0) {
                continue;
            }
            int height = Math.max(1, Math.round((PIP - 2) * pip));
            graphics.fill(px + 1, y + PIP - 1 - height, px + PIP - 1, y + PIP - 1, colour);
            graphics.fill(px + 1, y + PIP - 1 - height, px + PIP - 1,
                    y + PIP - height, HudPaint.shade(colour, 1.4f));
        }
    }

    /**
     * The reserve row changes colour at the points where the player's situation
     * changes, so the row is a warning rather than only a number.
     */
    private static int reserveColour(float reserve) {
        return switch (Consciousness.classify(reserve)) {
            case NORMAL -> HudScale.COLOR_GOOD;
            case HYPOXIC -> HudScale.COLOR_WARN;
            case GREYOUT, UNCONSCIOUS -> HudScale.COLOR_BAD;
        };
    }

    /**
     * Vision narrowing, which is what greyout actually is.
     *
     * <p>A darkening vignette rather than a message, because losing peripheral vision
     * is the symptom itself and describing it in words would be describing something
     * the player could simply be shown.
     */
    private static void drawVeil(GuiGraphicsExtractor graphics, float reserve) {
        float strength = Consciousness.veilStrength(reserve);
        if (strength <= 0) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int bands = 16;
        for (int i = 0; i < bands; i++) {
            // Each band is a slightly smaller frame, so the darkness closes in from the
            // edges rather than dimming the whole screen evenly.
            int alpha = Math.round(255 * strength * (1 - i / (float) bands) * 0.55f);
            if (alpha <= 0) {
                continue;
            }
            int colour = alpha << 24;
            int inset = i * 4;
            graphics.fill(0, inset, width, inset + 4, colour);
            graphics.fill(0, height - inset - 4, width, height - inset, colour);
            graphics.fill(inset, 0, inset + 4, height, colour);
            graphics.fill(width - inset - 4, 0, width - inset, height, colour);
        }
    }

    private BreathHud() {}
}
