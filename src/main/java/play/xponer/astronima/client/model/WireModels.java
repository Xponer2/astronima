package play.xponer.astronima.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import play.xponer.astronima.Astronima;

/**
 * The wire itself: one bar, one pixel square, reused for every segment in the world.
 *
 * <p><strong>One model, transformed per segment, rather than geometry built per wire.</strong>
 * A base with a few hundred metres of cable in it would otherwise rebuild a mesh every frame
 * for something that is always the same shape — the shape never varies, only where it sits and
 * which way it points.
 *
 * <p>Drawn through {@code submitModelPart} on an {@code entitySolid} render type, which is the
 * path {@link PlumbingModels} records as <em>known to draw</em>. Its javadoc carries the scar
 * worth not repeating: the first field gauges were submitted as custom geometry on
 * {@code debugQuads}, a pipeline vanilla renders nothing through, so every gauge in the mod was
 * invisible in play while the unit tests on their readings passed happily. A wire nobody can see
 * would be that bug again, and worse — the player would keep laying it.
 *
 * <p>The texture is plain near-white because the renderer multiplies it by the wire's insulation
 * colour; a tinted base would drag every colour toward it.
 */
public final class WireModels {

    /** How thick a wire is, in model pixels. One — it is a wire. */
    public static final float THICKNESS = 1.0F;

    /**
     * A trace is drawn one pixel at a time, so the model is a single pixel cube.
     *
     * <p>Consecutive pixels are exactly one pixel apart, so they <strong>touch by construction</strong>
     * and a run comes out solid with nothing to align. That deletes an entire class of bug the
     * first version had: a hub with arms reaching out of it needs the arms to start exactly at
     * the hub's surface, and getting that wrong put two surfaces in one place at every junction —
     * reported from play as <em>"есть пиксель и их там сразу два... они клипаются друг в друга"</em>.
     * Geometry that cannot overlap cannot z-fight.
     */
    public static final float LENGTH = 1.0F;

    /**
     * How far the wire's centre sits from the surface it is fastened to, in model pixels.
     *
     * <p>Half its own thickness plus a hair, so it lies <em>on</em> the surface rather than half
     * inside it. The hair is what stops it z-fighting with the block it is stapled to, which at
     * one pixel thick would otherwise flicker at every distance.
     */
    public static final float STANDOFF = 0.55F;

    public static final Identifier WIRE_TEXTURE =
            Identifier.fromNamespaceAndPath(Astronima.MODID, "textures/entity/wire.png");

    public static final ModelLayerLocation WIRE = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(Astronima.MODID, "wire"), "main");

    /**
     * One pixel of conductor, centred on the origin so the renderer only has to translate it.
     *
     * <p>No rotation is needed anywhere: a cube looks the same whichever way a trace is running,
     * which is the second thing that makes per-pixel drawing simpler than per-segment rather
     * than more complicated.
     */
    public static LayerDefinition wire() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("pixel", CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-THICKNESS / 2f, -THICKNESS / 2f, -LENGTH / 2f,
                                THICKNESS, THICKNESS, LENGTH),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    private WireModels() {}
}
