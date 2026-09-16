package play.xponer.astronima.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import play.xponer.astronima.client.TitleLogoLayout;

/**
 * Vanilla anchors the rotated splash-text quote at {@code screenWidth/2 + 123}, a fixed offset
 * tuned to sit right at the edge of its own logo, which is a constant 256px wide regardless of
 * screen size. Astronima's logo (drawn by {@code TitleScreenLogoMixin}) instead scales with the
 * window, so that fixed offset drifts out of sync with it — at some sizes landing well inside the
 * logo (the quote reads as jammed against/behind it) rather than beside it. Replacing the constant
 * with half of {@link TitleLogoLayout}'s own width keeps the quote anchored to the logo's actual
 * right edge at every screen size, the same relationship vanilla had with its own fixed logo.
 */
@Mixin(SplashRenderer.class)
public abstract class SplashAnchorMixin {

    @ModifyConstant(method = "extractRenderState", constant = @Constant(floatValue = 123.0F))
    private float astronima$anchorToLogoEdge(float original, GuiGraphicsExtractor graphics, int screenWidth, Font font, float alpha) {
        return TitleLogoLayout.widthFor(screenWidth) / 2.0F;
    }
}
