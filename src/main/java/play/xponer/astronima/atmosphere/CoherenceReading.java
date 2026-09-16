package play.xponer.astronima.atmosphere;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import play.xponer.astronima.sim.magic.Coherence;

/**
 * The six-term coherence breakdown at a real point in the world.
 *
 * <p>One authority, because the debug command, the survey meter's HUD sync and its GameTest proof
 * all ask exactly the same question — <em>what would {@link Coherence#breakdown} say here, right
 * now</em> — from real {@link Atmosphere} state (the same reading barotrauma trusts) and real
 * {@link Atmosphere#noiseDbAt} (the same number that blocks rest above 60 dB). Two copies of this
 * reading would drift the first time one of them changed, and the symptom would be the meter
 * disagreeing with the command that is supposed to be checking it.
 */
public final class CoherenceReading {

    /** Radiation dose and field exposure report zero honestly: nothing in the game emits either
     *  yet, so those two terms are named but always read as no limit rather than left off. */
    public static Map<Coherence.Term, Double> at(ServerLevel level, BlockPos pos, boolean observerPresent) {
        Atmosphere atmosphere = Atmosphere.get(level);
        var reading = atmosphere.readingNear(pos);
        double temperatureK = reading != null ? reading.state().temperatureK() : 0.0;
        double pressureKPa = reading != null ? reading.state().pressureKPa() : 0.0;
        double noiseDb = atmosphere.noiseDbAt(pos);
        return Coherence.breakdown(temperatureK, noiseDb, 0.0, 0.0, pressureKPa, observerPresent);
    }

    private CoherenceReading() {}
}
