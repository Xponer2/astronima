package play.xponer.astronima.client.screen;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.DelegatingUIElementRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import play.xponer.astronima.client.hud.BiomonitorLayout;
import play.xponer.astronima.client.hud.HudScale;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.physio.Ailment;
import play.xponer.astronima.sim.physio.BodyRegion;
import play.xponer.astronima.sim.physio.BodySystem;
import play.xponer.astronima.sim.physio.Diagnosis;
import play.xponer.astronima.sim.physio.VitalSigns;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.EnumMap;
import java.util.Map;

/**
 * The biomonitor's own picture, ported from the old {@code BiomonitorScreen} verbatim —
 * {@code design/ui-ldlib2-machines.md}'s pattern reused: a screen that drew everything by hand
 * needs its drawing code changed only where it read {@code leftPos}/{@code topPos}, never in
 * what it actually draws. Purely visual and read-only, with no slots and no server round trip
 * at all — {@link #font()} can call {@code Minecraft.getInstance()} freely, unlike a machine
 * screen's own equivalent (PLAN.md rule 108), because this element is only ever built from a
 * client keybind ({@code ClientTickHandler.OPEN_BIOMONITOR}), never from
 * {@code IContainerUIHolder#createUI}.
 */
public final class BiomonitorBody extends UIElement {

    private static final int PANEL_WIDTH = BiomonitorLayout.WIDTH;
    private static final int PANEL_HEIGHT = BiomonitorLayout.HEIGHT;
    private static final int ROW_HEIGHT = BiomonitorLayout.ROW_HEIGHT;
    private static final int BODY_WIDTH = BiomonitorLayout.BODY_WIDTH;

    /** Room a line of text has here: a third of the panel, less the gutter — three columns. */
    private static final int TEXT_WIDTH = PANEL_WIDTH / 3 - 14;

    public BiomonitorBody() {
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(PANEL_WIDTH);
        getLayout().height(PANEL_HEIGHT);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int left, int top) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int packed = minecraft.player.getData(ModAttachments.VITALS);
        Map<Ailment, Ailment.Severity> active = VitalSigns.unpack(packed);
        boolean hasChip = hasChip(minecraft, ModItems.BASIC_BIOMONITOR_CHIP.get());
        boolean hasAnalyzer = hasChip(minecraft, ModItems.PATHOGEN_ANALYZER_CHIP.get());
        String designator = minecraft.player.getData(ModAttachments.VISIBLE_DESIGNATOR.get());
        play.xponer.astronima.physio.CarriedInfection infection =
                minecraft.player.getData(ModAttachments.INFECTION);

        frame(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, VitalSigns.worst(packed));

        Font font = font();
        MachineFrame.value(graphics, font, Component.translatable("astronima.biomonitor.title"),
                left + 8, top + 7, TEXT_WIDTH, HudScale.COLOR_VALUE);

        drawSystems(graphics, font, left + 8, top + 24, active, hasChip);
        drawBody(graphics, left + 118, top + 26, active, hasChip);
        drawConditions(graphics, font, left + 190, top + 24, active, left, hasChip,
                hasAnalyzer, designator, infection, minecraft.player);
        drawContamination(graphics, font, left + 8, top + PANEL_HEIGHT - 30,
                minecraft.player.getData(ModAttachments.CONTAMINATION.get()));
        drawChronic(graphics, font, left + 190, top + PANEL_HEIGHT - 30,
                minecraft.player.getData(ModAttachments.CHRONIC.get()).revive());
    }

    /** Whether the player has this chip fitted anywhere in their Curios loadout — the same
     *  read {@code WireReadout#wearingGoggles} already uses for the goggles slot. */
    private static boolean hasChip(Minecraft minecraft, Item chip) {
        if (minecraft.player == null) {
            return false;
        }
        return CuriosApi.getCuriosInventory(minecraft.player)
                .map(inventory -> inventory.isEquipped(chip))
                .orElse(false);
    }

    /**
     * Lasting damage, and how close you are to it.
     *
     * <p><strong>The bar matters more than the label.</strong> A permanent consequence a player
     * could not see coming is a punishment; one they watched climb for ten minutes and ignored is
     * a decision. So the accumulating insult is drawn while it is still reversible, and only turns
     * into a named condition once it is not.
     *
     * <p>A scarred condition draws a full bar in the warning colour and stops moving, which is
     * what permanent looks like on an instrument: not a bar at zero, a bar that no longer
     * responds to anything you do.
     */
    private void drawChronic(GuiGraphicsExtractor graphics, Font font, int x, int y,
                             play.xponer.astronima.sim.physio.Chronic body) {
        MachineFrame.value(graphics, font, Component.translatable("astronima.biomonitor.chronic"),
                x, y, TEXT_WIDTH, HudScale.COLOR_LABEL);
        int row = y + 11;
        for (var condition : play.xponer.astronima.sim.physio.Chronic.Condition.values()) {
            boolean scarred = body.hasScarred(condition);
            double filled = scarred ? 1.0
                    : body.insultOf(condition)
                            / play.xponer.astronima.sim.physio.Chronic.SCARRING_THRESHOLD;
            int colour = scarred ? HudScale.COLOR_BAD
                    : filled > 0.5 ? HudScale.COLOR_WARN : HudScale.COLOR_VALUE;

            MachineFrame.value(graphics, font, Component.translatable(
                            "astronima.chronic." + condition.key()), x, row, TEXT_WIDTH, colour);
            graphics.fill(x, row + 10, x + 96, row + 15, 0xFF1A1F24);
            graphics.fill(x, row + 10, x + (int) (96 * Math.clamp(filled, 0, 1)), row + 15,
                    colour);
            if (scarred) {
                MachineFrame.value(graphics, font, Component.translatable(
                                "astronima.chronic." + condition.key() + ".effect"),
                        x, row + 17, TEXT_WIDTH, HudScale.COLOR_LABEL);
                row += 9;
            }
            row += 25;
        }
    }

    /**
     * What is on your gloves and what has reached your skin, as two bars against the dose.
     *
     * <p>Rule 7: a hazard needs an instrument, and this is the most invisible hazard in the mod —
     * without this panel the first a player knows about it is an unexplained illness, which is the
     * dice roll {@code design/transmission.md} exists to refuse.
     *
     * <p>Two bars rather than one, because the entire system turns on their difference. A dirty
     * glove is not a problem; a dirty <em>hand</em> is. Seeing them apart is what lets somebody
     * notice they are one careless mouthful away instead of finding out afterwards.
     *
     * <p>The dose is drawn as a line across both, not as a full bar, so "how close am I" is a
     * distance you can see rather than a percentage you have to convert.
     */
    private void drawContamination(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                   play.xponer.astronima.physio.CarriedContamination carried) {
        MachineFrame.value(graphics, font, Component.translatable("astronima.biomonitor.contamination"),
                x, y, TEXT_WIDTH, HudScale.COLOR_LABEL);

        int width = 96;
        int mark = (int) (width * play.xponer.astronima.sim.pathogen
                .Contamination.INFECTIOUS_DOSE);
        String[] labels = {"astronima.biomonitor.gloves", "astronima.biomonitor.skin"};
        double[] loads = {carried.glove(), carried.skin()};

        for (int row = 0; row < 2; row++) {
            int barY = y + 11 + row * 9;
            MachineFrame.value(graphics, font, Component.translatable(labels[row]), x, barY - 1, TEXT_WIDTH,
                    HudScale.COLOR_LABEL);
            int barX = x + 44;
            graphics.fill(barX, barY, barX + width, barY + 6, 0xFF1A1F24);
            int filled = (int) (width * Math.clamp(loads[row], 0, 1));
            // The skin bar is the one that can hurt you, so only it turns red at the dose. A
            // glove at the dose is a normal working state and colouring it as a warning would
            // teach the player to ignore the colour.
            boolean bad = row == 1 && loads[row] >= play.xponer.astronima.sim.pathogen
                    .Contamination.INFECTIOUS_DOSE;
            graphics.fill(barX, barY, barX + filled, barY + 6,
                    bad ? HudScale.COLOR_BAD : HudScale.COLOR_VALUE);
            graphics.fill(barX + mark, barY - 1, barX + mark + 1, barY + 7, HudScale.COLOR_WARN);
        }
    }

    /**
     * Left column: one gauge per physiological system, worst condition wins.
     *
     * <p>Grouping by system <em>is</em> the tier-1 reveal (design/biomonitor-chips.md §2) — with
     * no chip fitted a person has no instrument telling them which system is under strain, only
     * that something is, so this whole column is withheld rather than degraded.
     */
    private void drawSystems(GuiGraphicsExtractor graphics, Font font, int x, int y,
                             Map<Ailment, Ailment.Severity> active, boolean hasChip) {
        MachineFrame.value(graphics, font, Component.translatable("astronima.biomonitor.systems"),
                x, y, TEXT_WIDTH, HudScale.COLOR_LABEL);
        int row = y + ROW_HEIGHT + 2;
        if (!hasChip) {
            row = drawWrapped(graphics, font,
                    Component.translatable("astronima.biomonitor.no_implant").getString(),
                    x, row, TEXT_WIDTH);
            return;
        }
        Map<BodySystem, Ailment.Severity> worstBySystem = new EnumMap<>(BodySystem.class);
        active.forEach((ailment, severity) -> worstBySystem.merge(ailment.system(), severity,
                (a, b) -> a.ordinal() >= b.ordinal() ? a : b));
        for (BodySystem system : BodySystem.values()) {
            Ailment.Severity severity = worstBySystem.getOrDefault(system, Ailment.Severity.NONE);
            int colour = severityColour(severity);
            graphics.fill(x, row + 2, x + 3, row + 5, colour);
            MachineFrame.value(graphics, font, Component.translatable("astronima.body.system." + system.key()),
                    x + 7, row, TEXT_WIDTH, severity == Ailment.Severity.NONE ? HudScale.COLOR_LABEL : colour);
            row += ROW_HEIGHT;
        }
    }

    /**
     * Centre: a simple humanoid figure whose regions tint by the worst condition
     * presenting there, so harm has a place on the body.
     *
     * <p>Region placement is instrument-revealed exactly like the systems column (design/
     * biomonitor-chips.md §2) — with no chip the figure is drawn but stays an outline, telling a
     * player nothing about where anything is happening.
     */
    private void drawBody(GuiGraphicsExtractor graphics, int x, int y,
                          Map<Ailment, Ailment.Severity> active, boolean hasChip) {
        Map<BodyRegion, Ailment.Severity> worstByRegion = new EnumMap<>(BodyRegion.class);
        if (hasChip) {
            active.forEach((ailment, severity) -> worstByRegion.merge(ailment.region(), severity,
                    (a, b) -> a.ordinal() >= b.ordinal() ? a : b));
        }
        // A systemic condition colours everything that is not already worse.
        Ailment.Severity systemic = worstByRegion.getOrDefault(BodyRegion.SYSTEMIC, Ailment.Severity.NONE);

        int centre = x + BODY_WIDTH / 2;
        region(graphics, worstByRegion, systemic, BodyRegion.HEAD, centre - 8, y, 16, 16);
        region(graphics, worstByRegion, systemic, BodyRegion.CHEST, centre - 12, y + 18, 24, 20);
        region(graphics, worstByRegion, systemic, BodyRegion.ABDOMEN, centre - 11, y + 40, 22, 16);
        region(graphics, worstByRegion, systemic, BodyRegion.ARMS, centre - 22, y + 18, 8, 32);
        region(graphics, worstByRegion, systemic, BodyRegion.ARMS, centre + 14, y + 18, 8, 32);
        region(graphics, worstByRegion, systemic, BodyRegion.LEGS, centre - 11, y + 58, 9, 34);
        region(graphics, worstByRegion, systemic, BodyRegion.LEGS, centre + 2, y + 58, 9, 34);
    }

    private void region(GuiGraphicsExtractor graphics, Map<BodyRegion, Ailment.Severity> worst,
                        Ailment.Severity systemic, BodyRegion regionKey, int x, int y, int w, int h) {
        Ailment.Severity severity = worst.getOrDefault(regionKey, Ailment.Severity.NONE);
        if (systemic.ordinal() > severity.ordinal()) {
            severity = systemic;
        }
        int fill = severity == Ailment.Severity.NONE ? 0xFF2A3038 : severityColour(severity);
        graphics.fill(x, y, x + w, y + h, fill);
        // A thin outline keeps the silhouette readable when everything is healthy.
        graphics.fill(x, y, x + w, y + 1, HudScale.COLOR_FRAME);
        graphics.fill(x, y + h - 1, x + w, y + h, HudScale.COLOR_FRAME);
        graphics.fill(x, y, x + 1, y + h, HudScale.COLOR_FRAME);
        graphics.fill(x + w - 1, y, x + w, y + h, HudScale.COLOR_FRAME);
    }

    /** Room a remedy has before it would run past the panel's right edge. */
    private int remedyWidth(int x, int panelLeft) {
        int panelRight = panelLeft + PANEL_WIDTH;
        return Math.max(60, panelRight - x - 14);
    }

    /** Wrapping lives in {@link play.xponer.astronima.client.hud.WrappedText}, shared with every
     *  other panel. */
    private int drawWrapped(GuiGraphicsExtractor graphics, Font font, String text,
                            int x, int y, int maxWidth) {
        return play.xponer.astronima.client.hud.WrappedText.draw(graphics, font, text,
                x, y, maxWidth, ROW_HEIGHT - 2, HudScale.COLOR_TEXT_DIM);
    }

    /**
     * Game-design audit #2, finding D: the regulator (the shortest-lived of the suit's seven
     * subsystems, design/progression-economy.md §9.1 — cycle 9, ~11,583 s of real sealed time)
     * wears out with zero proactive warning anywhere in the game — the only way to find out was
     * the on-demand repair screen, which nothing prompts a player to open before it already
     * matters. Rule 7's own requirement ("a hazard ships with an instrument that warns before it
     * kills"), closed the same way {@link #drawAntibioticCourse} closed it for a missed redose:
     * surfaced on the instrument the player is already glancing at, not a new screen.
     *
     * <p>Shows only the <em>worst</em> subsystem — a full seven-row breakdown here would bury the
     * one number that actually matters under six that do not, and a player who wants the rest
     * already has the repair screen for it. No suit worn at all costs no layout space, matching
     * {@link #drawAntibioticCourse}'s own "nothing running, nothing drawn" shape.
     *
     * @return the row the caller's own next line should start at
     */
    private int drawSuitWear(GuiGraphicsExtractor graphics, Font font, int x, int y,
                             net.minecraft.world.entity.player.Player player) {
        net.minecraft.world.item.ItemStack suit =
                play.xponer.astronima.item.SuitLoadout.suit(player);
        if (suit.isEmpty()) {
            return y;
        }
        int packedWear = play.xponer.astronima.item.EvaSuitItem.wearOf(suit);
        play.xponer.astronima.sim.suit.SuitSubsystem worst =
                play.xponer.astronima.sim.suit.SuitWear.worstSubsystem(packedWear);
        float worstFraction = play.xponer.astronima.sim.suit.SuitWear.fraction(packedWear, worst);
        MachineFrame.value(graphics, font,
                Component.translatable("astronima.biomonitor.suit_wear",
                        worst.displayName(), Math.round(worstFraction * 100)),
                x, y, TEXT_WIDTH, HudScale.suitWearColor(worstFraction));
        return y + ROW_HEIGHT;
    }

    /**
     * The one warning this instrument was missing: a running antibiotic course abandons itself,
     * silently and permanently, the instant its cover clock reaches zero
     * ({@code Infections.tick}: "missing the next dose abandons the course, which breeds
     * resistance to that drug for ever against this organism"). Rule 7's own hazard-ships-with-
     * instrument requirement, closed — before this, the only reader of
     * {@code CarriedInfection.coverLeft()} anywhere in the mod was the {@code /astronima} debug
     * command. Not gated behind a chip: the player dosed themselves, so this reports their own
     * action back to them rather than revealing hidden physiology.
     *
     * @return the row the caller's own next line should start at — unchanged when no course is
     *         running, so this costs no layout space the rest of the time
     */
    private int drawAntibioticCourse(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                     play.xponer.astronima.physio.CarriedInfection infection) {
        if (infection.course().isBlank() || infection.coverLeft() <= 0) {
            return y;
        }
        double coverLeft = infection.coverLeft();
        int colour = coverLeft <= 5.0 ? HudScale.COLOR_BAD
                : coverLeft <= play.xponer.astronima.physio.Infections.DOSE_COVERS_SECONDS / 2.0
                        ? HudScale.COLOR_WARN : HudScale.COLOR_GOOD;
        MachineFrame.value(graphics, font,
                Component.translatable("astronima.biomonitor.antibiotic_course",
                        infection.course(), (int) Math.ceil(coverLeft)),
                x, y, TEXT_WIDTH, colour);
        return y + ROW_HEIGHT;
    }

    /**
     * Right column: what is wrong, where, and the remedy when one is known — gated by
     * {@link Diagnosis#detailFor} (design/biomonitor-chips.md), not always the full truth.
     */
    private void drawConditions(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                Map<Ailment, Ailment.Severity> active, int panelLeft,
                                boolean hasChip, boolean hasAnalyzer, String designator,
                                play.xponer.astronima.physio.CarriedInfection infection,
                                net.minecraft.world.entity.player.Player player) {
        MachineFrame.value(graphics, font, Component.translatable("astronima.biomonitor.conditions"),
                x, y, TEXT_WIDTH, HudScale.COLOR_LABEL);
        int row = y + ROW_HEIGHT + 2;
        row = drawSuitWear(graphics, font, x, row, player);
        row = drawAntibioticCourse(graphics, font, x, row, infection);
        if (active.isEmpty()) {
            MachineFrame.value(graphics, font, Component.translatable("astronima.biomonitor.healthy"),
                    x, row, TEXT_WIDTH, HudScale.COLOR_GOOD);
            return;
        }
        for (Map.Entry<Ailment, Ailment.Severity> entry : active.entrySet()) {
            Ailment ailment = entry.getKey();
            Diagnosis.Detail detail = Diagnosis.detailFor(ailment, hasChip);
            int colour = severityColour(entry.getValue());
            graphics.fill(x, row + 2, x + 3, row + 5, colour);

            if (detail == Diagnosis.Detail.RAW_SYMPTOM) {
                // No chip: a felt sensation, not a diagnosis - no name, no region, no remedy.
                row = drawWrapped(graphics, font, ailment.rawSymptom(), x + 7, row,
                        remedyWidth(x, panelLeft)) + 2;
                continue;
            }

            MachineFrame.value(graphics, font,
                    Component.translatable("astronima.ailment." + ailment.key() + ".name"),
                    x + 7, row, TEXT_WIDTH, colour);
            row += ROW_HEIGHT - 2;
            // Location always shown once a chip exists; the rest depends on what it can resolve.
            MachineFrame.value(graphics, font, Component.translatable("astronima.body.region." + ailment.region().key()),
                    x + 7, row, TEXT_WIDTH, HudScale.COLOR_LABEL);
            row += ROW_HEIGHT - 1;
            if (detail == Diagnosis.Detail.SYSTEM_REGION) {
                // An analyzer chip cannot name the organism, but it can give it a label to track
                // across visits - real information (design/biomonitor-chips.md §4), not a name.
                if (hasAnalyzer && !designator.isEmpty()) {
                    MachineFrame.value(graphics, font,
                            Component.translatable("astronima.biomonitor.designator", designator),
                            x + 7, row, TEXT_WIDTH, HudScale.COLOR_TEXT_DIM);
                    row += ROW_HEIGHT - 1;
                }
                row += 2;
                continue;
            }
            // Remedies are full sentences and the panel is a fixed width, so they have
            // to wrap. Drawing them as one line ran them off the right edge and,
            // beyond the screen, off the window entirely.
            row = drawWrapped(graphics, font,
                    Component.translatable("astronima.ailment." + ailment.key() + ".remedy")
                            .getString(),
                    x + 7, row, remedyWidth(x, panelLeft));
        }
    }

    private static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                              Ailment.Severity worst) {
        int accent = worst == Ailment.Severity.NONE ? HudScale.COLOR_GOOD : severityColour(worst);
        play.xponer.astronima.client.hud.Instruments.panel(graphics, x, y, w, h, accent);
        graphics.fill(x + 8, y + 18, x + w - 8, y + 19, HudScale.COLOR_FRAME);
    }

    private static int severityColour(Ailment.Severity severity) {
        return switch (severity) {
            case NONE -> HudScale.COLOR_GOOD;
            case MILD -> HudScale.COLOR_WARN;
            case SEVERE -> 0xFFDD8833;
            case CRITICAL -> HudScale.COLOR_BAD;
        };
    }

    @LDLRegisterClient(name = "biomonitor_body", registry = "ldlib2:ui_element_renderer")
    public static final class BiomonitorBodyRenderer
            extends DelegatingUIElementRenderer<BiomonitorBody, BiomonitorBodyRenderer> {
        @Override
        public Class<BiomonitorBody> type() {
            return BiomonitorBody.class;
        }

        @Override
        public void drawBackgroundAdditional(BiomonitorBody element, IGUIContext context) {
            if (!(context instanceof GUIContext guiContext)) {
                drawParentBackgroundAdditional(element, context);
                return;
            }
            element.drawPanel(guiContext.graphics, (int) element.getContentX(),
                    (int) element.getContentY());
        }
    }
}
