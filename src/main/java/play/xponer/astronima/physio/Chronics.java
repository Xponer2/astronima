package play.xponer.astronima.physio;

import net.minecraft.world.entity.player.Player;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.physio.Ailment;
import play.xponer.astronima.sim.physio.Chronic;

/** Accumulating lasting damage, and the two lines it moves. */
public final class Chronics {

    public static Chronic of(Player player) {
        return player.getData(ModAttachments.CHRONIC.get()).revive();
    }

    private static void save(Player player, Chronic body) {
        player.setData(ModAttachments.CHRONIC.get(), CarriedChronic.of(body));
    }

    /**
     * One step of living.
     *
     * <p>Which insults count is deliberately narrow. Spores and low oxygen scar lungs because both
     * are things the player can see coming and can fix; marrow damage has two real causes — a
     * severe infection (the one consequence of ignoring the laboratory that should outlive the
     * illness) and severe accumulated radiation dose (design/radiation.md) — folded into one
     * boolean at the call site rather than two separate strains, since both scar the same
     * condition the same way. Anything wider would make chronic damage a background tax rather
     * than a consequence.
     */
    public static void tick(Player player, double seconds,
                            java.util.Map<Ailment, Ailment.Severity> diagnosis, boolean marrowStress) {
        Chronic body = of(player);
        boolean insulted = false;

        if (diagnosis.containsKey(Ailment.MOLD_SPORES) || diagnosis.containsKey(Ailment.HYPOXIA)
                || diagnosis.containsKey(Ailment.TOXIC_GAS)) {
            body.strain(Chronic.Condition.SCARRED_LUNGS, seconds);
            insulted = true;
        }
        if (marrowStress) {
            body.strain(Chronic.Condition.MARROW_DAMAGE, seconds);
            insulted = true;
        }
        if (!insulted) {
            body.rest(seconds);
        }
        save(player, body);
    }

    /** The oxygen pressure this particular body needs, which is not always the standard one. */
    public static double ppO2FloorKPa(Player player) {
        return of(player).ppO2FloorKPa(
                play.xponer.astronima.sim.HabitatStandard.BREATHABLE_PPO2_KPA);
    }

    /** What is left of the immune rate. */
    public static double immunityFactor(Player player) {
        return of(player).immunityFactor();
    }

    private Chronics() {}
}
