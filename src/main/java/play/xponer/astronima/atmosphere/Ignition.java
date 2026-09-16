package play.xponer.astronima.atmosphere;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.burn.Flammability;

/**
 * Bridges spark/flame events to the combustion sim: if the room at the ignition
 * point holds an ignitable mixture, the whole room burns (sim updates gases and
 * temperature) and the released chemical energy drives a world explosion.
 */
public final class Ignition {
    /** Joules per unit of vanilla explosion strength³ (tuned: stoich CH4 cabin ≈ TNT). */
    private static final double ENERGY_PER_STRENGTH_CUBED = 3_000_000.0;
    private static final float MIN_STRENGTH = 0.8f;
    private static final float MAX_STRENGTH = 8.0f;

    /**
     * @return true when the atmosphere ignited (callers may want to cancel the
     *         action that struck the spark — it probably no longer exists)
     */
    public static boolean tryIgnite(ServerLevel level, BlockPos pos) {
        RoomState room = Atmosphere.get(level).roomAt(pos);
        if (room == null) {
            return false;
        }
        double energy = Flammability.burn(room);
        if (energy <= 0) {
            return false;
        }
        float strength = (float) Math.min(MAX_STRENGTH,
                Math.max(MIN_STRENGTH, Math.cbrt(energy / ENERGY_PER_STRENGTH_CUBED)));
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                strength, Level.ExplosionInteraction.BLOCK);
        return true;
    }

    private Ignition() {}
}
