package play.xponer.astronima.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import play.xponer.astronima.Astronima;

/**
 * One twig, reused for every segment of every mold colony in the world — {@code
 * MoldBlockEntityRenderer} positions and stretches it per segment rather than baking a shape per
 * colony, the same "one model, transformed per instance" recipe {@link WireModels} already uses
 * for exactly the same reason (a base with a lot of colonies in it should not rebuild geometry
 * every frame for a shape that never actually varies, only where it sits and how big it is).
 *
 * <p>A unit cube with one corner at the local origin, not centred — {@code
 * MoldBlockEntityRenderer} composes it from a segment's own minimum corner via translate, then
 * its extent via scale, so this exact box reproduces any axis-aligned segment {@link
 * play.xponer.astronima.sim.MoldBranching} computes without ever needing a rotation.
 */
public final class MoldModels {

    public static final Identifier TWIG_TEXTURE = WireModels.WIRE_TEXTURE;

    public static final ModelLayerLocation TWIG = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Astronima.MODID, "mold_twig"), "main");

    public static LayerDefinition twig() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("twig", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(0, 0, 0, 1, 1, 1),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    private MoldModels() {}
}
