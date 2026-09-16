package play.xponer.astronima.sim.pipe;

/**
 * Whether a pump's two faces actually give it somewhere to move gas.
 *
 * <p>A pump needs exactly one volume on each face <em>and</em> those two volumes have to
 * be different ones. A room ported to itself — two gas ports into the same room, a pump
 * between them — puts exactly one volume on each face, so a reading built only from the
 * two counts sees "1 in, 1 out" and would call that routed. It is not: there is nowhere
 * to push into that is not also where the gas was drawn from.
 *
 * <p>Written out as its own named fact because it was already being answered twice.
 * {@code GasPumpBlockEntity}'s real routing decision checks the two volumes are not the
 * same one; a Jade tooltip built only from the two face counts (PLAN.md, "duplicated
 * pump-connectivity predicates") cannot see that distinction and reads "1 in, 1 out" as
 * fully routed regardless. Both are meant to answer the same question. This is the one
 * place that answer lives — a caller with the two counts and whether the volumes are the
 * same one gets the same verdict {@link play.xponer.astronima.block.entity.GasPumpBlockEntity}
 * uses to decide whether to actually pump.
 */
public final class PumpRouting {

    /**
     * @param inletVolumes  how many volumes the inlet face reaches
     * @param outletVolumes how many volumes the outlet face reaches
     * @param sameVolumeOnBothFaces whether the one volume on each face (when there is
     *                              exactly one on each) is the same volume
     * @return true only when there is exactly one volume on each face and they differ
     */
    public static boolean isUsable(int inletVolumes, int outletVolumes,
                                   boolean sameVolumeOnBothFaces) {
        return inletVolumes == 1 && outletVolumes == 1 && !sameVolumeOnBothFaces;
    }

    private PumpRouting() {}
}
