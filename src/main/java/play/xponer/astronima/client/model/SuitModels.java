package play.xponer.astronima.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import play.xponer.astronima.Astronima;

/**
 * Geometry for the equipment you can see on a wearer.
 *
 * <p>Everything here is built on the <em>humanoid skeleton</em> — the same part names
 * and pivots the player model uses — even when the visible geometry is a single box on
 * someone's back. That is not incidental: parenting to the real skeleton is what makes
 * a tank lean when the player leans and swing when they crouch, for free and correctly.
 * A box positioned in world space would need every one of those transforms
 * reimplemented, and would still be wrong the first time an animation changed.
 */
public final class SuitModels {
    public static final ModelLayerLocation SUIT =
            layer("eva_suit");
    public static final ModelLayerLocation TANK =
            layer("oxygen_tank");
    public static final ModelLayerLocation CARTRIDGE =
            layer("lioh_cartridge");

    /** The suit sits just outside the body, the way armour does. */
    private static final CubeDeformation SUIT_PADDING = new CubeDeformation(0.6F);

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(
                Identifier.fromNamespaceAndPath(Astronima.MODID, name), "main");
    }

    /**
     * The pressure garment: a full humanoid shell, textured on the standard 64×64
     * skin layout so it can be re-skinned without touching any code.
     */
    public static LayerDefinition suit() {
        return LayerDefinition.create(HumanoidModel.createMesh(SUIT_PADDING, 0.0F), 64, 64);
    }

    /** The tank, carried high on the back where the mount actually sits. */
    public static LayerDefinition tank() {
        MeshDefinition mesh = skeleton();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        // Bottle.
                        .texOffs(0, 0).addBox(-3.0F, 1.0F, 2.0F, 6.0F, 10.0F, 4.0F)
                        // Regulator block on top, so it reads as plumbing and not a brick.
                        .texOffs(0, 14).addBox(-2.0F, -1.0F, 3.0F, 4.0F, 2.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** The cartridge, clipped to the chest where a wearer could reach it in gloves. */
    public static LayerDefinition cartridge() {
        MeshDefinition mesh = skeleton();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5.5F, 3.0F, -3.0F, 3.0F, 5.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    /**
     * An empty humanoid skeleton with the pivots the player model uses.
     *
     * <p>{@link HumanoidModel} requires every one of these parts to exist, so pieces
     * that only occupy one of them still have to declare the rest. They cost nothing:
     * a part with no cubes renders nothing.
     */
    private static MeshDefinition skeleton() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
        return mesh;
    }

    /** A plain humanoid model over a built root part. */
    public static class Worn extends HumanoidModel<HumanoidRenderState> {
        public Worn(net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }
    }

    private SuitModels() {}
}
