package play.xponer.astronima.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.LadderBlock;

/**
 * Something to catch hold of.
 *
 * <p>Every EVA ever performed is done by handrail. The ISS is covered in them for the reason
 * this asteroid needs them: once you are moving there is nothing else to stop you, and
 * translation along a rail is how astronauts actually get anywhere outside.
 *
 * <p><strong>Built on {@link LadderBlock} rather than beside it</strong>, deliberately. Vanilla
 * already knows how to make a thin thing on a wall that a body clings to, and it also already
 * knows to forgive the fall of anyone clinging — reimplementing either would put this mod's
 * idea of holding on next to Minecraft's and let the two drift apart, which is what rule 2 is
 * about even when the API in question is a block.
 *
 * <p>What it adds is the one thing vanilla has no concept of: <strong>catching someone who is
 * already travelling</strong>. See {@code KineticImpactEvents} — an arrival that touched a rail
 * costs nothing at all, because an astronaut with a hand on a handrail is not partly falling.
 */
public class GrabRailBlock extends LadderBlock {
    /**
     * Declared as {@code MapCodec<LadderBlock>} rather than of this class, because
     * {@code LadderBlock.codec()} is not generic in its subtype and Java will not let an
     * override narrow it. Harmless: the codec still round-trips this block, since
     * {@code simpleCodec} carries the constructor it was given.
     */
    public static final MapCodec<LadderBlock> CODEC = simpleCodec(GrabRailBlock::new);

    public GrabRailBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<LadderBlock> codec() {
        return CODEC;
    }
}
