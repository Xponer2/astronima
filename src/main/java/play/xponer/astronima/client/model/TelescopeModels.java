package play.xponer.astronima.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.sim.optics.TelescopeMount;

/**
 * The telescope's own moving parts, split out of the static block model
 * ({@code models/block/telescope.json}, now only the plinth/cap/post/collar that never move) so
 * {@code TelescopeBlockEntityRenderer} can turn them to the mount's live rotation — a block-model
 * JSON can only ever draw one fixed pose, and a direct request after playtest asked for the
 * opposite: "надо сделать чтобы труба крутилась" (design/astra-telescope.md §6).
 *
 * <p>Two groups, matching the two axes a real alt-azimuth mount actually has: the {@link #yoke()}
 * arms are bolted to the collar and swing with azimuth alone; the {@link #tube()} assembly (cell,
 * trunnion pins, focuser knob, barrel, objective ring, lens) rides the yoke's own trunnions and
 * additionally tips with elevation. Every box below is authored relative to its own group's
 * pivot — the identical coordinates the removed block-model elements used, only shifted so pivot
 * is the origin (rule 1: one set of numbers, not a second guess at a shape already approved).
 *
 * <p>Pivots, both in block-fraction units (a {@code BlockEntityRenderer} translates from the
 * block's own corner): the azimuth pivot sits at {@code (0.5, 11.1/16, 0.5)} — the collar's top
 * centre, where the removed yoke-arm elements met it exactly; the elevation (trunnion) pivot is
 * {@code 1.7} pixels above that, at {@code (0.5, 12.8/16, 0.5)} — the trunnion pins' own height.
 */
public final class TelescopeModels {
    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Astronima.MODID, "textures/entity/telescope_tube.png");
    public static final int TEXTURE_SIZE = 64;

    public static final ModelLayerLocation YOKE = layer("telescope_yoke");
    public static final ModelLayerLocation TUBE = layer("telescope_tube");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(Astronima.MODID, name), "main");
    }

    /**
     * Both yoke arms, relative to the azimuth pivot. The removed block-model elements ran from
     * y=11.1 (the collar top, the pivot itself) to y=14.5, so every box here starts at y=0.
     */
    public static LayerDefinition yoke() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder arms = CubeListBuilder.create()
                // left arm: was [5.6,11.1,6.8]-[6.8,14.5,9.2], centred at x=8,z=8
                .texOffs(0, 0).addBox(-2.4F, 0.0F, -1.2F, 1.2F, 3.4F, 2.4F)
                // right arm: was [9.2,11.1,6.8]-[10.4,14.5,9.2]
                .texOffs(16, 0).addBox(1.2F, 0.0F, -1.2F, 1.2F, 3.4F, 2.4F);
        root.addOrReplaceChild("arms", arms, PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    /**
     * The optical tube assembly, relative to the elevation (trunnion) pivot. Rests here with no
     * elevation rotation applied at all — the identical pose the original static model was built
     * in, matching {@code GROUND_TRIPOD}'s own zero (pointing straight up,
     * design/astra-telescope.md §6.4).
     *
     * <p>Includes a star diagonal at the rear (design/astra-telescope.md §2.3.2) — a housing and an
     * eyepiece barrel emitting along tube-local −Z, ending well short of
     * {@link TelescopeMount#EYE_LOCAL_Z} so the drawn geometry never reaches the eye's own point
     * (real eye relief, not the camera clipping the model). A straight-through eyepiece cannot
     * work here: at the zenith it would put the observer's eye inside the azimuth post, which is
     * exactly what shipped once and read as "вижу текстуру телескопа на весь экран" — every real
     * refractor used anywhere near the zenith has this same part for the same reason.
     */
    public static LayerDefinition tube() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder parts = CubeListBuilder.create()
                // eyepiece cell: was [6.9,11.8,6.9]-[9.1,13.8,9.1], pivot at y=12.8
                .texOffs(32, 0).addBox(-1.1F, -1.0F, -1.1F, 2.2F, 2.0F, 2.2F)
                // trunnion pin, right: was [9.1,12.45,7.65]-[9.65,13.15,8.35]
                .texOffs(0, 10).addBox(1.1F, -0.35F, -0.35F, 0.55F, 0.7F, 0.7F)
                // trunnion pin, left: was [6.35,12.45,7.65]-[6.9,13.15,8.35]
                .texOffs(8, 10).addBox(-1.65F, -0.35F, -0.35F, 0.55F, 0.7F, 0.7F)
                // focuser knob: was [9.1,12.25,7.65]-[10.0,13.35,8.35]
                .texOffs(16, 10).addBox(1.1F, -0.55F, -0.35F, 0.9F, 1.1F, 0.7F)
                // main barrel: was [6.4,13.8,6.4]-[9.6,22.8,9.6]
                .texOffs(0, 32).addBox(-1.6F, 1.0F, -1.6F, 3.2F, 9.0F, 3.2F)
                // objective ring: was [5.9,22.8,5.9]-[10.1,24.1,10.1]
                .texOffs(32, 10).addBox(-2.1F, 10.0F, -2.1F, 4.2F, 1.3F, 4.2F)
                // lens glass: was [6.5,24.08,6.5]-[9.5,24.22,9.5]
                .texOffs(0, 56).addBox(-1.5F, 11.28F, -1.5F, 3.0F, 0.14F, 3.0F)
                // star diagonal housing: bulges off the eyepiece cell's own rear face (y=-1.0),
                // squarely behind it (z=0 to z=-2.0)
                .texOffs(48, 0).addBox(-1.3F, -1.8F, -2.0F, 2.6F, 1.6F, 2.0F)
                // eyepiece barrel: narrower, protrudes further along -Z; EYE_LOCAL_Z=-5.1 stops
                // well past its tip (-3.6), leaving real eye relief rather than clipping the model
                .texOffs(48, 16).addBox(-0.5F, -1.4F, -3.6F, 1.0F, 0.8F, 1.6F);
        root.addOrReplaceChild("tube", parts, PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private TelescopeModels() {}
}
