package play.xponer.astronima.physio;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.pathogen.Barrier;
import play.xponer.astronima.sim.pathogen.Contamination;
import play.xponer.astronima.sim.pathogen.Exposure;

/**
 * Surfaces, and the two-way traffic between them and a pair of hands.
 *
 * <p>The half of the chain that was missing. Touching a dirty bench makes your gloves dirty; that
 * much was obvious. The half people forget is that <strong>it goes the other way too</strong> —
 * work at a clean bench with a dirty glove and the bench is now the problem, and it will be there
 * tomorrow when you have forgotten and come back without gloves.
 *
 * <p>That is what makes a habitat something you can contaminate rather than a backdrop, and it is
 * why the decontamination station is worth standing in <em>before</em> you touch anything rather
 * than after.
 */
public final class Surfaces {

    public static SurfaceContamination of(Level level, BlockPos pos) {
        return level.getChunk(pos).getData(ModAttachments.SURFACES.get());
    }

    public static Contamination at(Level level, BlockPos pos) {
        return of(level, pos).at(pos);
    }

    /** Puts a load on a surface and makes sure the chunk is saved and sent. */
    public static void set(Level level, BlockPos pos, Contamination load) {
        SurfaceContamination surfaces = of(level, pos);
        surfaces.set(pos, load);
        markChanged(level, pos);
    }

    /**
     * Working on a surface: what is on it comes off onto you, and what is on you goes onto it.
     *
     * <p>Both directions in one call, because they are one action. Splitting them into "touch" and
     * "soil" would let a caller do half of a thing that has no halves.
     */
    public static void work(Player player, BlockPos pos) {
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }
        CarriedContamination carried = Contaminations.of(player);
        boolean gloves = Contaminations.barriersOf(player).contains(Barrier.GLOVES);

        Exposure.Handled after = of(level, pos).loads().work(pos.asLong(), carried.onGloves(),
                carried.onSkin(), gloves);
        player.setData(ModAttachments.CONTAMINATION.get(),
                carried.with(after.glove(), after.skin()));
        markChanged(level, pos);

        if (Contaminations.deliversDose(player)) {
            Infections.infect(player, Contaminations.of(player).source());
        }
    }

    /** Weathers everything in one chunk. Called on a slow cadence; there is nothing urgent here. */
    public static void tick(ServerLevel level, ChunkAccess chunk, double seconds, double halfLife) {
        SurfaceContamination surfaces = chunk.getData(ModAttachments.SURFACES.get());
        if (!surfaces.isEmpty() && surfaces.decay(seconds, halfLife)) {
            touched(chunk);
        }
    }

    private static void markChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            // Rule 26: saving is not sending. A surface nobody can survey is a surface the
            // player will keep working at.
            touched(level.getChunk(pos));
        }
    }

    /** Save it, and push it to whoever is watching. */
    private static void touched(ChunkAccess chunk) {
        chunk.markUnsaved();
        chunk.syncData(ModAttachments.SURFACES.get());
    }

    private Surfaces() {}
}
