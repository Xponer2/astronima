package play.xponer.astronima.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.registry.ModParticles;
import play.xponer.astronima.registry.Particles;
import play.xponer.astronima.sim.sky.ImpactSchedule;

/**
 * design/sky.md §3's impact-flash event: real content, and the row's own point stated plainly —
 * <em>no meteor streak</em>. There is no air here to burn an incoming body, so nothing crosses
 * the sky first; the flash is the whole event, silent and without warning, on the ground. Every
 * player expects a shooting star; getting a pinpoint flash instead is the one sky event whose
 * entire job is to teach vacuum in a single look ("the inversion that sells the setting").
 *
 * <p>Server-side, unlike this package's {@code client.sky} sibling: a real position on real
 * terrain near each player, not a camera-relative billboard on the celestial dome the rest of
 * this mod's sky effects live on.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class ImpactFlashEvents {

    private static final long IMPACT_SEED = 20260812L;

    /** Real content: "at the horizon" (design/sky.md), not underfoot — far enough to read as
     * something seen in the distance, close enough to still sit inside ordinary render distance. */
    private static final double MIN_DISTANCE_BLOCKS = 40.0;
    private static final double MAX_DISTANCE_BLOCKS = 90.0;

    @SubscribeEvent
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        if (!ImpactSchedule.firesAt(level.getGameTime(), IMPACT_SEED)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            flashNear(level, player);
        }
    }

    /** The debug-command rule's "trigger it" half: {@code /astronima sky impact now} calls this
     * directly rather than needing a held override the way the flare/occultation do — a real
     * impact is a one-tick pulse, not a continuous value to preview over time, so "force it" only
     * ever means "make one happen right now," which this already does. */
    public static void triggerNear(ServerLevel level, ServerPlayer player) {
        flashNear(level, player);
    }

    private static void flashNear(ServerLevel level, ServerPlayer player) {
        double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
        double distance = MIN_DISTANCE_BLOCKS
                + level.getRandom().nextDouble() * (MAX_DISTANCE_BLOCKS - MIN_DISTANCE_BLOCKS);
        int x = (int) Math.round(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.round(player.getZ() + Math.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos impact = new BlockPos(x, y, z);

        // The flash itself: real content — a genuine kinetic impact converting its own energy to
        // light on contact — momentary and bright, not lingering.
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1.0F, 0.95F, 0.85F),
                impact.getX() + 0.5, impact.getY() + 0.3, impact.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);

        // The regolith plume. This used to be vanilla CLOUD particles, with a comment calling that
        // an honest first-cut simplification - it was not one. Vanilla's own particle physics
        // falls at Earth gravity through Earth air, and this body has neither, so the plume was
        // quietly contradicting the exact lesson the impact event exists to teach (rule 52) while
        // also disagreeing with Microgravity, a real model this mod already trusts for the
        // player's own falls (rule 46). Every grain now flies a real parabola under this body's
        // own gravity with no drag term at all - see RegolithParticle and RegolithBallistics. The
        // arc is slow, high and unhurried, and that is the reading: it is what 0.22g looks like
        // from across a valley without an instrument.
        level.sendParticles(ModParticles.REGOLITH.get(),
                impact.getX() + 0.5, impact.getY() + 0.5, impact.getZ() + 0.5,
                (int) Particles.IMPACT_PLUME_GRAINS, 0.5, 0.1, 0.5, 0.0);
    }

    private ImpactFlashEvents() {}
}
