package play.xponer.astronima.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.codex.CodexLoader;
import play.xponer.astronima.client.codex.CodexReveal;
import play.xponer.astronima.client.hud.HudPanels;
import play.xponer.astronima.client.hud.HudScale;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.progression.Unlocks;
import play.xponer.astronima.registry.ModAttachments;

import java.util.Set;

/**
 * "Which article, and what became readable" — design/codex-disclosure.md §6, achievement-styled
 * in this mod's own instrument language rather than vanilla's advancement toast, the same call
 * {@code HudPageHint} already made against reusing a vanilla screen for the codex itself.
 *
 * <p><strong>Cause and effect are the same lookup, not two.</strong> This never decides for
 * itself what a page's title or hidden text is — it detects a new id in the synced {@link
 * Unlocks} and asks {@link CodexReveal#find} the exact question a locked block on the page would
 * answer about itself, off the same loaded book. There is nothing here to disagree with the page.
 *
 * <p>Detects "new" by diffing the synced state against what was seen last client tick — the
 * server-side grant ({@code progression.UnlockTriggers}) has no notion of "this is a client
 * event," it only ever writes the truth, so the client is what notices the truth changed.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class UnlockToast {

    private static final int SHOW_TICKS = 100; // five seconds
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 38;
    private static final int TOP_MARGIN = 24;

    private static Set<String> lastSeen = Set.of();
    /** False right after (re)join: the next tick must adopt the server's synced {@link Unlocks} as
     *  the baseline rather than diff against it, or every already-earned id looks newly earned and
     *  re-toasts on every world entry (the bug this field fixes — see its own commit). */
    private static boolean primed = false;
    private static long tickCounter = 0;
    private static long hideAtTick = -1;
    private static String activeTitle = "";
    private static String activeSnippet = "";

    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        tickCounter++;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            primed = false;
            return;
        }
        Unlocks unlocks = minecraft.player.getData(ModAttachments.UNLOCKS.get());
        if (!primed) {
            // Baseline only: whatever is already earned on join was not just unlocked, so it must
            // not queue a toast — only ids earned AFTER this tick are "new".
            lastSeen = unlocks.earned();
            primed = true;
            return;
        }
        if (unlocks.earned().equals(lastSeen)) {
            return;
        }
        for (String id : unlocks.earned()) {
            if (!lastSeen.contains(id)) {
                queue(id);
            }
        }
        lastSeen = unlocks.earned();
    }

    private static void queue(String unlockId) {
        CodexReveal.find(CodexLoader.book(), unlockId).ifPresent(reveal -> {
            activeTitle = reveal.pageTitle();
            activeSnippet = reveal.snippet();
            hideAtTick = tickCounter + SHOW_TICKS;
        });
    }

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(Astronima.MODID, "unlock_toast"),
                UnlockToast::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (tickCounter >= hideAtTick || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        int x = (graphics.guiWidth() - PANEL_WIDTH) / 2;
        int y = TOP_MARGIN;
        // A fixed spot, not a bid for one - furniture like HudPageHint's own bar, not a readout
        // that follows the crosshair. Reserved anyway, because GuiTextTest's own rule is "every
        // overlay either asks for a place or holds the one it takes": this holds one.
        HudPanels.reserve("unlock toast", x, y, PANEL_WIDTH, PANEL_HEIGHT, System.currentTimeMillis() / 1000.0);

        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xC0121619);
        graphics.fill(x, y, x + PANEL_WIDTH, y + 1, HudScale.COLOR_INFO);
        int innerWidth = PANEL_WIDTH - 12;
        MachineFrame.overlay(graphics, minecraft.font, "CODEX UPDATED", x + 6, y + 4,
                innerWidth, HudScale.COLOR_INFO);
        MachineFrame.overlay(graphics, minecraft.font, activeTitle, x + 6, y + 15,
                innerWidth, 0xFFFFFFFF);
        MachineFrame.overlay(graphics, minecraft.font, activeSnippet, x + 6, y + 26,
                innerWidth, HudScale.COLOR_LABEL);
    }

    private UnlockToast() {}
}
