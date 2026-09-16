package play.xponer.astronima.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import play.xponer.astronima.client.hud.MachineFrame;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.hud.HudPaint;
import play.xponer.astronima.client.indicator.BlockReadings;
import play.xponer.astronima.client.indicator.Reading;

/**
 * What the block you are pointing at says, printed by the crosshair.
 *
 * <p>Replaces the floating world-space gauge as the machine's detailed instrument, for the
 * reason recorded in {@code design/indicators.md} §6: a panel that has to hover over a
 * one-metre block can be big enough to read <em>or</em> small enough not to swallow the
 * machine, never both, so its text was a smear at every useful distance. Reported as
 * <em>"шкалы стали очень маленькими, вообще ничего не видно"</em>, and correctly.
 *
 * <p>Screen space fixes that by construction — the same size at four metres and at
 * fourteen — and pointing solves the clutter problem that made world-space text tempting
 * in the first place: exactly one machine is ever asking for attention, the one you chose.
 *
 * <p>Pure display. Every value comes from {@link BlockReadings}, which is also what the
 * lamp on the block asks, so the two can never disagree.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class LookAtReadout {
    /** Room a line of text has here: the look-at box. */
    private static final int TEXT_WIDTH = 180;

    /** Padding inside the panel. */
    private static final int PAD = 4;

    /** Gap between the crosshair and the top of the panel. */
    private static final int BELOW_CROSSHAIR = 14;

    private static final int BAR_WIDTH = 74;
    private static final int BAR_HEIGHT = 5;

    /** Minimum width, so short readings do not produce a panel the eye keeps re-finding. */
    private static final int MIN_TEXT_WIDTH = 84;

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(Astronima.MODID, "look_at_readout"),
                LookAtReadout::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui
                || minecraft.screen != null) {
            return;
        }
        // Another mod's overlay takes its space first, so the module is deciding on a screen it
        // can see all of. Held per frame and only while Jade is actually drawing.
        JadeSpace.hold(graphics, System.currentTimeMillis() / 1000.0);

        // Nothing is drawn while a menu is open: the menu is a better instrument than this
        // one and two readouts of the same machine at once is noise, not redundancy.
        //
        // The wire readout used to answer first and this one stood down, which is why looking at
        // a machine with a run on it lost the machine's data. They answer different questions -
        // what is this machine doing, and what is this wire carrying - so both draw, and the HUD
        // module decides where each goes (design/hud-module.md).
        WireReadout.render(graphics, deltaTracker);
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        // A machine reads through its block entity; a bulkhead door has none and carries
        // its state in the blockstate instead, so both paths ask BlockReadings and there is
        // still exactly one authority on what a block says.
        Reading reading = blockEntity != null
                ? BlockReadings.shown(blockEntity)
                : BlockReadings.ofState(minecraft.level.getBlockState(pos));

        if (reading == null) {
            return;
        }
        String name = minecraft.level.getBlockState(pos).getBlock()
                .getName().getString().toUpperCase(java.util.Locale.ROOT);
        draw(graphics, minecraft.font, name, reading);
    }

    /**
     * What this readout is worth, and what each place is worth to it.
     *
     * <p>It outranks the wire panel because a machine that is not running is the more urgent
     * question, and it wants the crosshair badly — a machine readout in a corner is one nobody
     * looks at while they are stood in front of the machine.
     */
    private static void draw(GuiGraphicsExtractor graphics, Font font,
                             String name, Reading reading) {
        int textWidth = Math.max(MIN_TEXT_WIDTH,
                Math.max(font.width(name), font.width(reading.label())));
        int width = Math.max(BAR_WIDTH, textWidth) + PAD * 2;
        int height = PAD * 2 + font.lineHeight * 2 + BAR_HEIGHT + 5;

        // Asks the module for a place rather than centring itself. It outranks the goggles and
        // wants the crosshair badly, so it takes that spot and the goggles slide beside it.
        var seat = play.xponer.astronima.client.hud.HudPanels.place("block", width, height,
                play.xponer.astronima.client.hud.HudCosts.BLOCK_PRIORITY,
                play.xponer.astronima.client.hud.HudCosts.BLOCK,
                graphics.guiWidth(), graphics.guiHeight(),
                System.currentTimeMillis() / 1000.0);
        if (seat == null) {
            return;
        }
        int x = seat.x();
        int y = seat.y();

        int accent = play.xponer.astronima.client.hud.Reveal.fade(
                colourOf(reading.band()), seat.fade());

        // A flat plate with a hairline border, matching the machine panels rather than
        // vanilla's tooltip, because this is an instrument and not a description.
        // The plate opens from its own middle before anything is written on it. Text drawn at the
        // same time would be laid over a panel narrower than itself and spill out of both sides.
        int inset = play.xponer.astronima.client.hud.Reveal.inset(width, seat.fade());
        int plateLeft = x + inset;
        int plateRight = x + width - inset;
        double contents = play.xponer.astronima.client.hud.Reveal.contentFade(seat.fade());

        graphics.fill(plateLeft, y, plateRight, y + height,
                play.xponer.astronima.client.hud.Reveal.fade(0xD8121619, seat.fade()));
        graphics.fill(plateLeft, y, plateRight, y + 1, accent);
        graphics.fill(plateLeft, y + height - 1, plateRight, y + height,
                play.xponer.astronima.client.hud.Reveal.fade(0xFF000000, seat.fade()));
        if (contents <= 0) {
            return;
        }

        int textX = x + PAD;
        int textY = y + PAD;
        MachineFrame.overlay(graphics, font, name, textX, textY, TEXT_WIDTH,
                play.xponer.astronima.client.hud.Reveal.fade(0xFF9AA6AC, contents));

        int barY = textY + font.lineHeight + 2;
        int barWidth = width - PAD * 2;
        graphics.fill(textX, barY, textX + barWidth, barY + BAR_HEIGHT, 0xFF0B0E10);
        int filled = Math.round((barWidth - 2) * (float) Math.clamp(reading.fraction(), 0.0, 1.0));
        if (filled > 0) {
            graphics.fill(textX + 1, barY + 1, textX + 1 + filled, barY + BAR_HEIGHT - 1, accent);
            graphics.fill(textX + 1, barY + 1, textX + 1 + filled, barY + 2,
                    HudPaint.shade(accent, 1.3f));
        }
        MachineFrame.overlay(graphics, font, reading.label(), textX, barY + BAR_HEIGHT + 3, TEXT_WIDTH, accent);
    }

    /**
     * Band to colour.
     *
     * <p>Kept here rather than on {@link Reading} so the simulation-facing types stay free
     * of ARGB. The bands themselves are asserted against the sim's own thresholds in
     * {@code PlumbingReadingsTest}, which is what makes a green bar mean safe.
     */
    private static int colourOf(Reading.Band band) {
        return switch (band) {
            case NOMINAL -> 0xFF5FD08A;
            case IDLE -> 0xFF8B979D;
            case WARNING -> 0xFFE0A63C;
            case CRITICAL -> 0xFFE0603C;
        };
    }

    private LookAtReadout() {}
}
