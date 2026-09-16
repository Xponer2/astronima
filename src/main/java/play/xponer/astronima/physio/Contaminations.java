package play.xponer.astronima.physio;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import play.xponer.astronima.item.SuitLoadout;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.pathogen.Barrier;
import play.xponer.astronima.sim.pathogen.Contamination;
import play.xponer.astronima.sim.pathogen.Exposure;
import play.xponer.astronima.sim.pathogen.Strain;
import play.xponer.astronima.sim.suit.SuitCondition;
import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * The player's end of {@code design/transmission.md}: what they pick up, and what reaches them.
 *
 * <p>Every entry point here is a verb the player already had. Handling a culture, eating, standing
 * in a mouldy room, washing — no new key, no new slot. That was a constraint of the design and it
 * is worth naming: a hazard the player has to learn a new control to avoid is a hazard they will
 * meet once and then resent.
 */
public final class Contaminations {

    /** Gloves the suit does not have yet are gloves you are not wearing. */
    public static Set<Barrier> barriersOf(Player player) {
        int condition = SuitLoadout.condition(player);
        Set<Barrier> intact = EnumSet.noneOf(Barrier.class);
        if (SuitLoadout.suit(player).isEmpty()) {
            return intact; // no suit is no barriers at all
        }
        if (SuitCondition.isWorking(condition, SuitSubsystem.SEALED_GLOVES)) {
            intact.add(Barrier.GLOVES);
        }
        if (SuitCondition.isWorking(condition, SuitSubsystem.HELMET_SEAL)) {
            intact.add(Barrier.HELMET);
        }
        if (SuitCondition.isWorking(condition, SuitSubsystem.SCRUBBER_BAY)) {
            intact.add(Barrier.FILTER);
        }
        if (SuitCondition.isWorking(condition, SuitSubsystem.THERMAL_LAYER)) {
            intact.add(Barrier.OUTER_LAYER);
        }
        return intact;
    }

    public static CarriedContamination of(Player player) {
        return player.getData(ModAttachments.CONTAMINATION.get());
    }

    private static void set(Player player, CarriedContamination carried) {
        player.setData(ModAttachments.CONTAMINATION.get(), carried);
    }

    /**
     * Picking something contaminated up and working with it.
     *
     * <p>The surface's own load is not tracked on the item yet, so the caller supplies how filthy
     * the thing is. That is honest about the slice: the chain from a surface onto the player is
     * modelled and the surface's own bookkeeping is not.
     */
    public static void handle(Player player, Strain.Source source, double surfaceLoad) {
        CarriedContamination carried = of(player);
        boolean gloves = barriersOf(player).contains(Barrier.GLOVES);
        Exposure.Handled handled = Exposure.handle(new Contamination(source, surfaceLoad),
                carried.onGloves(), carried.onSkin(), gloves);
        set(player, carried.with(handled.glove(), handled.skin()));
    }

    /**
     * Hand to mouth — the face-touch, mapped onto a verb the player already has.
     *
     * <p>There is no natural "scratch your nose" control in Minecraft, and inventing one would put
     * the most important step in the chain behind a key nobody presses. Eating and drinking is the
     * same gesture and the same mistake: whatever is on your glove goes to your mouth.
     */
    public static void handToMouth(Player player) {
        CarriedContamination carried = of(player);
        if (carried.onGloves().isClean()) {
            return;
        }
        Contamination.Handover slip = Exposure.touchFace(carried.onGloves(), carried.onSkin());
        set(player, carried.with(slip.from(), slip.to()));
    }

    /**
     * Time passing: air breathed, and loads decaying wherever the player is standing.
     *
     * @param sporeLoad   airborne concentration in the room, 0..1
     * @param inVacuum    whether the player is outside, where a load does not last
     */
    public static void tick(Player player, double seconds, Strain.Source source, double sporeLoad,
                            boolean inVacuum) {
        CarriedContamination carried = of(player);
        Set<Barrier> intact = barriersOf(player);

        Contamination skin = carried.onSkin();
        if (sporeLoad > 0) {
            skin = Exposure.breathe(skin, source, sporeLoad, seconds, intact);
        }
        double halfLife = inVacuum ? Contamination.VACUUM_HALF_LIFE : Contamination.HABITAT_HALF_LIFE;
        // Only what is on the outside weathers. Once it is on you, standing in vacuum does not
        // help - which is the point of the skin load being its own number.
        Contamination gloves = carried.onGloves().decay(seconds, halfLife);

        CarriedContamination now = carried.with(gloves, skin);
        if (!now.equals(carried)) {
            set(player, now);
        }
    }

    /**
     * Whether what has reached the skin is enough to start an infection, and clears it if so.
     *
     * <p>Consumed rather than left standing: a dose is delivered once. Leaving it would re-infect
     * every tick and turn one mistake into a permanent condition.
     */
    public static boolean deliversDose(Player player) {
        CarriedContamination carried = of(player);
        if (!carried.onSkin().isInfectious()) {
            return false;
        }
        set(player, carried.with(carried.onGloves(), carried.onSkin().cleaned()));
        return true;
    }

    /** Washing: gloves off, hands clean. What a decontamination step does. */
    public static void decontaminate(Player player) {
        CarriedContamination carried = of(player);
        set(player, carried.with(carried.onGloves().cleaned(), carried.onSkin().cleaned()));
    }

    private Contaminations() {}
}
