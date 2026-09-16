package play.xponer.astronima.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the furnace's private burn timer so the oxygen rule can tell a furnace that
 * is genuinely alight from one that merely has fuel in it.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceBurnAccessor {
    @Accessor("litTimeRemaining")
    int astronima$litTimeRemaining();
}
