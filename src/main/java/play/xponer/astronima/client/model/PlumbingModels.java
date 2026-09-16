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
 * Geometry for the valve handwheel — the one plumbing part that turns.
 *
 * <p>It is a real 3D part, not a texture painted to look like one. That distinction is
 * written into the machine-io plan as a mistake already made once: the pump's nozzles
 * shipped as flat squares drawn to resemble a bore and a spout, and at any distance both
 * read as the same dark face. A handwheel is worse as a texture, because it is a
 * <em>control</em> — it has to be seen turning, and a picture of a wheel cannot turn.
 *
 * <p>Everything is authored in the standard 1/16-block "pixel" units and centred on the
 * origin in the axes it spins around, so a renderer only has to translate to the block
 * centre and apply the animation angle. The unlit steel texture is deliberately near
 * uniform, so a face samples metal wherever its UVs land — the parts read by their
 * silhouette turning, not by surface detail that is invisible on a spinning object
 * anyway.
 */
public final class PlumbingModels {
    /** One flat steel sheet, shared by every turning part. */
    public static final Identifier STEEL_TEXTURE =
            Identifier.fromNamespaceAndPath(Astronima.MODID, "textures/entity/gas_valve_wheel.png");
    public static final int TEXTURE_SIZE = 32;

    public static final ModelLayerLocation VALVE_WHEEL = layer("gas_valve_wheel");

    /**
     * The retort's own light: a near-white glow blob tinted by the real temperature.
     *
     * <p>Used to be painted onto a vanilla billboard quad ({@code PlumbingModels.retortGlow()},
     * removed) that a person playing reported as reading like "a flat square always turned to
     * face the camera." {@code RetortPhotonGlow} now paints this same texture onto a genuine 3D
     * sphere (Photon's built-in {@code sphere.obj}, {@code Model} render mode) instead — the
     * texture itself did not need to change, only the geometry it rides on.
     */
    public static final Identifier GLOW_TEXTURE =
            Identifier.fromNamespaceAndPath(Astronima.MODID, "textures/entity/retort_glow.png");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(
                Identifier.fromNamespaceAndPath(Astronima.MODID, name), "main");
    }

    /**
     * The valve handwheel: a spoked wheel on a short stem, standing proud on top of the
     * slim valve body.
     *
     * <p>On top, and about the vertical, because a gate valve carries its handwheel on a
     * stem above the bonnet, and putting it there means the renderer never has to work out
     * which way the run points — it turns about the vertical whatever axis the valve is on.
     * Centred in X and Z so the whole assembly turns about its own axis with a single
     * {@code rotationY}.
     *
     * <p>Y here is measured in the renderer's frame, whose origin is the block <em>centre</em>
     * (the BER translates to {@code 0.5,0.5,0.5} before drawing). The valve body is a
     * pipe-diameter tube whose top is at model-pixel 11, i.e. +3 above centre, so the wheel
     * begins there rather than at the top of a full cube.
     *
     * <p>An octagonal rim rather than a square: four short axis edges and four chamfered
     * corners, so it reads as a round wheel a hand turns rather than a plate — and half the
     * old radius, because the square plate was both too big and too blocky.
     */
    public static LayerDefinition wheel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // +3 in this frame is the top of the slim tube; the wheel sits just above it, its
        // rim in the plane at y≈5.25.
        CubeListBuilder wheel = CubeListBuilder.create()
                // Short stem from the tube up to the hub.
                .texOffs(0, 0).addBox(-0.5F, 3.0F, -0.5F, 1.0F, 2.5F, 1.0F)
                // Small hub.
                .texOffs(0, 4).addBox(-1.5F, 5.0F, -1.5F, 3.0F, 1.5F, 3.0F)
                // Two crossed spokes, radius ~4.5.
                .texOffs(0, 9).addBox(-4.5F, 5.25F, -0.5F, 9.0F, 1.0F, 1.0F)
                .texOffs(0, 11).addBox(-0.5F, 5.25F, -4.5F, 1.0F, 1.0F, 9.0F)
                // Octagonal rim: four short axis edges…
                .texOffs(0, 13).addBox(-2.0F, 5.25F, 3.9F, 4.0F, 1.0F, 1.0F)
                .texOffs(0, 15).addBox(-2.0F, 5.25F, -4.9F, 4.0F, 1.0F, 1.0F)
                .texOffs(0, 17).addBox(3.9F, 5.25F, -2.0F, 1.0F, 1.0F, 4.0F)
                .texOffs(0, 19).addBox(-4.9F, 5.25F, -2.0F, 1.0F, 1.0F, 4.0F)
                // …and four chamfer corners that cut the square into an octagon.
                .texOffs(8, 0).addBox(2.3F, 5.25F, 2.3F, 1.7F, 1.0F, 1.7F)
                .texOffs(8, 2).addBox(-4.0F, 5.25F, 2.3F, 1.7F, 1.0F, 1.7F)
                .texOffs(8, 4).addBox(2.3F, 5.25F, -4.0F, 1.7F, 1.0F, 1.7F)
                .texOffs(8, 6).addBox(-4.0F, 5.25F, -4.0F, 1.7F, 1.0F, 1.7F);
        root.addOrReplaceChild("wheel", wheel, PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private PlumbingModels() {}
}
