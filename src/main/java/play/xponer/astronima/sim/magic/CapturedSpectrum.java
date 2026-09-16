package play.xponer.astronima.sim.magic;

/**
 * What a spectral plate remembers about its exposure: the target and the vacuum around it at
 * the time. Deliberately <strong>not</strong> the resulting line set — the plate stores the
 * inputs and {@link Spectrum#capture} is called fresh wherever this is read, so a plate can
 * never disagree with the model that produced it (rule 13; rule 46's lesson about ports that
 * drift is exactly the failure storing a snapshot would risk).
 */
public record CapturedSpectrum(ObservationTarget target, double vacuumKPa) {
}
