package play.xponer.astronima.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import play.xponer.astronima.Astronima;

/**
 * Replaces the "MOJANG STUDIOS" wordmark on the boot splash with Astronima's own logo.
 *
 * <p>Vanilla draws that wordmark as two side-by-side halves cropped from one 120x120 sprite sheet
 * (a skewed-seam trick baked into its specific texture layout), so simply overriding the vanilla
 * texture path would just slice our unrelated artwork into the same two pieces. Instead, the first
 * half's draw call is redirected to paint our whole logo across the combined box (reusing vanilla's
 * own position, size and fade-alpha math so the splash's background flash, timing and progress bar
 * are untouched), and the second half's draw call is dropped since there is nothing left to draw.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayLogoMixin {

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(Astronima.MODID, "textures/gui/title/logo.png");
    private static final int LOGO_TEXTURE_WIDTH = 2026;
    private static final int LOGO_TEXTURE_HEIGHT = 289;

    @Redirect(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V",
            ordinal = 0
        )
    )
    private void astronima$drawFullLogo(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y,
                                         float u, float v, int width, int height, int srcWidth, int srcHeight,
                                         int textureWidth, int textureHeight, int color) {
        // `width`/`height`/`x`/`y` here describe vanilla's left half box: x is already the left edge
        // of the *combined* logo (the left half starts right at it), so doubling width recovers the
        // full box, and re-deriving height from our own aspect ratio (instead of vanilla's 4:1) avoids
        // stretching this wider wordmark to fit vanilla's proportions.
        int fullWidth = width * 2;
        int fullHeight = Math.round(fullWidth * (float) LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH);
        int centerY = y + height / 2;
        graphics.blit(
            pipeline, LOGO, x, centerY - fullHeight / 2, 0.0F, 0.0F,
            fullWidth, fullHeight, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, color
        );
    }

    @Redirect(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V",
            ordinal = 1
        )
    )
    private void astronima$skipSecondHalf(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier texture, int x, int y,
                                           float u, float v, int width, int height, int srcWidth, int srcHeight,
                                           int textureWidth, int textureHeight, int color) {
        // Intentionally does nothing: astronima$drawFullLogo already painted the whole logo.
    }
}
