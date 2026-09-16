package play.xponer.astronima.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import play.xponer.astronima.Astronima;

/**
 * Paints Astronima's own logo above the real Escape menu's heading. The title screen gets the
 * same logo too, but that one is drawn by {@code TitleScreenLogoMixin} instead — vanilla's splash
 * text there is anchored to (and drawn after) the logo slot, so it has to be replaced in place
 * rather than painted over afterward here.
 *
 * <p>Not drawn on {@link PauseScreen} when {@link PauseScreen#showsPauseMenu()} is false — that
 * variant (e.g. the transient overlay shown when the window loses focus) puts its title text at
 * y=10, too close to the top to fit a logo above without also overriding vanilla's widget
 * layout, which this deliberately avoids touching.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class TitleLogoRenderer {

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(Astronima.MODID, "textures/gui/title/logo.png");
    /** The source artwork's own pixel size — every drawn size is scaled from this. */
    private static final int LOGO_TEXTURE_WIDTH = 2026;
    private static final int LOGO_TEXTURE_HEIGHT = 289;

    private static final int PAUSE_SCREEN_WIDTH = 224;
    /** Clears by a few pixels above the "Game menu" heading, which vanilla draws at y=40. */
    private static final int PAUSE_SCREEN_Y = 4;

    @SubscribeEvent
    public static void onRenderScreenPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof PauseScreen pauseScreen && pauseScreen.showsPauseMenu()) {
            draw(event.getGuiGraphics(), screen.width, PAUSE_SCREEN_WIDTH, PAUSE_SCREEN_Y);
        }
    }

    private static void draw(GuiGraphicsExtractor graphics, int screenWidth, int displayWidth, int y) {
        int displayHeight = Math.round(displayWidth * (float) LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH);
        int x = screenWidth / 2 - displayWidth / 2;
        graphics.blit(
            RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0F, 0.0F,
            displayWidth, displayHeight, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT
        );
    }

    private TitleLogoRenderer() {}
}
