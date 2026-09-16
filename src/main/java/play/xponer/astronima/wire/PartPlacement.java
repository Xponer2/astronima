package play.xponer.astronima.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import play.xponer.astronima.sim.logic.Circuit;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.PartFootprint;
import play.xponer.astronima.sim.wire.WirePixel;

/**
 * Where a part would land if the player clicked now.
 *
 * <p><strong>One answer, asked twice.</strong> The ghost draws it and the click commits it, and
 * they call the same method — because a preview that disagrees with the click is worse than no
 * preview at all: the player aims by the picture and gets something else, with nothing to blame.
 * That is the shape rule 20 keeps insisting on, and the same reason {@link WireAim} exists as one
 * object rather than as three tools each deciding for themselves.
 *
 * <p>The turn comes from where the player is looking, projected into the face's own plane — the
 * rule the gate <em>blocks</em> used, kept because it means the common case needs no adjustment at
 * all. The wrench turns it afterwards.
 */
public final class PartPlacement {

    /**
     * The part the next click would bolt down.
     *
     * <p>Always returns something: whether it can actually go there is {@link Wires#canMount}, and
     * keeping those apart is what lets the ghost draw a <em>refused</em> placement in red rather
     * than simply vanishing. A preview that disappears tells the player nothing about why.
     */
    public static WirePart proposed(Player player, BlockHitResult hit, PartType type,
                                    Circuit circuit) {
        return proposed(player, hit, type, circuit, 0, "");
    }

    /**
     * The same, turned by however many quarters the player has already asked for.
     *
     * @param turns extra quarter-turns from the R key, on top of where they are looking
     */
    public static WirePart proposed(Player player, BlockHitResult hit, PartType type,
                                    Circuit circuit, int turns) {
        return proposed(player, hit, type, circuit, turns, "");
    }

    /**
     * The same, carrying the name the plate in hand was already given — so renaming a plate before
     * placing it is not undone by the act of placing it.
     */
    public static WirePart proposed(Player player, BlockHitResult hit, PartType type,
                                    Circuit circuit, int turns, String name) {
        BlockPos cell = hit.getBlockPos().relative(hit.getDirection());
        Direction face = hit.getDirection().getOpposite();
        WirePixel aimed = WireAim.exact(hit, cell, face);
        Vec3 look = player.getLookAngle();
        int rotation = Math.floorMod(
                PartFootprint.rotationFor(Faces.of(face), look.x, look.y, look.z) + turns,
                PartFootprint.ROTATIONS);

        return WirePart.placed(cell, face,
                PartFootprint.corner(aimed.u(),
                        PartFootprint.spanU(type.width(), type.height(), rotation)),
                PartFootprint.corner(aimed.v(),
                        PartFootprint.spanV(type.width(), type.height(), rotation)),
                rotation, type, circuit, name);
    }

    /** Whether that proposal would be accepted — the ghost's colour, and the click's answer. */
    public static boolean allowed(Level level, WirePart proposed) {
        return Wires.canMount(level, proposed);
    }

    private PartPlacement() {}
}
