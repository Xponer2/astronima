package play.xponer.astronima.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.model.SuitModels;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws one piece of worn equipment on its wearer.
 *
 * <p>Equipment that exists only in an inventory screen is not equipment, it is a
 * number. Seeing the tank on someone's back is what makes the loadout the visible,
 * finite thing the slot model was chosen for in the first place — you can look at
 * another player, or at yourself in third person, and know what they are carrying and
 * what they are missing.
 *
 * <p>The model is built lazily on first use rather than in the constructor: Curios
 * constructs renderers during layer setup, and the entity model set is not necessarily
 * ready to bake from at that moment.
 */
public class WornCurioRenderer implements ICurioRenderer.HumanoidRender {
    private final ModelLayerLocation layer;
    private final Identifier texture;

    private EntityModel<HumanoidRenderState> model;

    public WornCurioRenderer(ModelLayerLocation layer, String texturePath) {
        this.layer = layer;
        this.texture = Identifier.fromNamespaceAndPath(Astronima.MODID,
                "textures/entity/" + texturePath + ".png");
    }

    @Override
    public EntityModel<HumanoidRenderState> getModel(ItemStack stack, SlotContext slotContext) {
        if (model == null) {
            model = new SuitModels.Worn(
                    Minecraft.getInstance().getEntityModels().bakeLayer(layer));
        }
        return model;
    }

    @Override
    public Identifier getModelTexture(ItemStack stack, SlotContext slotContext) {
        return texture;
    }

    /**
     * Draws nothing in first person, deliberately.
     *
     * <p>The interface's default implementation submits the <em>whole</em> humanoid
     * model at the arm's transform — head, torso, both arms and both legs, crammed
     * into the space of a hand. On a full-body suit that is the spare limb you see
     * floating beside your own.
     *
     * <p>Showing the suit glove properly in first person needs an arm-only model with
     * its own pivot, which is a separate piece of work rather than a parameter on this
     * one. Until that exists, drawing nothing is correct: the third-person model
     * already covers the arm, and no limb is better than a wrong one.
     */
    @Override
    public void renderFirstPersonHand(ItemStack stack, SlotContext slotContext,
                                      HumanoidArm arm, PoseStack poseStack,
                                      SubmitNodeCollector submitNodeCollector,
                                      AvatarRenderState avatarRenderState,
                                      AbstractClientPlayer clientPlayer, int packedLight) {
        // Intentionally empty; see above.
    }
}
