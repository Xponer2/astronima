package play.xponer.astronima.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.TitleLogoLayout;

/**
 * Astronima draws its own logo in place of vanilla's "MINECRAFT" wordmark, redirecting the exact
 * call vanilla makes to draw it — scoped to {@link TitleScreen} only, so the rarely-seen onboarding
 * and credits screens that reuse {@link LogoRenderer} keep their normal branding.
 *
 * <p>Drawing happens right here rather than additively afterward (e.g. on
 * {@code ScreenEvent.Render.Post}) because vanilla draws its splash text (the rotating "As seen on
 * TV!!"-style quote) <em>after</em> the logo slot, anchored to it. A later draw pass would paint
 * over that text instead of leaving it on top, which is exactly backwards. The size computed here
 * (via {@link TitleLogoLayout}) is also what {@code SplashAnchorMixin} uses to re-anchor that
 * splash text against this logo's actual edge, so the two must stay in agreement.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenLogoMixin {

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(Astronima.MODID, "textures/gui/title/logo.png");
    /** Vanilla's own logo starts at y=30; buttons don't start until height/4+32, so there's room. */
    private static final int Y = 24;

    @Redirect(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"
        )
    )
    private void astronima$drawLogoInstead(LogoRenderer logoRenderer, GuiGraphicsExtractor graphics, int screenWidth, float alpha) {
        int width = TitleLogoLayout.widthFor(screenWidth);
        int height = TitleLogoLayout.heightFor(width);
        int x = screenWidth / 2 - width / 2;
        graphics.blit(
            RenderPipelines.GUI_TEXTURED, LOGO, x, Y, 0.0F, 0.0F,
            width, height, TitleLogoLayout.LOGO_TEXTURE_WIDTH, TitleLogoLayout.LOGO_TEXTURE_HEIGHT,
            TitleLogoLayout.LOGO_TEXTURE_WIDTH, TitleLogoLayout.LOGO_TEXTURE_HEIGHT, ARGB.white(alpha)
        );
    }
}
