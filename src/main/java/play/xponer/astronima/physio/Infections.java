package play.xponer.astronima.physio;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.sim.pathogen.Immunity;
import play.xponer.astronima.sim.pathogen.Infection;
import play.xponer.astronima.sim.pathogen.Pathogens;
import play.xponer.astronima.sim.pathogen.Strain;
import play.xponer.astronima.sim.pathogen.Treatment;
import play.xponer.astronima.sim.physio.Ailment;

import java.util.List;
import java.util.Map;

/**
 * Catching an illness, carrying it, and getting over it.
 *
 * <p>The one place the pathogen model meets a player. Everything about how a disease behaves lives
 * in {@code sim/pathogen}; everything about how you <em>get</em> one lives here, because that is
 * about the world.
 *
 * <p>Takes a {@link Player} rather than a {@code ServerPlayer}: attachments, hunger and effects
 * all live on the base class, and the gametest harness hands out a mock that is not a server
 * player. A signature narrower than the code needs is a signature that shuts the tests out.
 *
 * <h2>Nothing tells you that you caught it</h2>
 * Deliberately, and it is the whole opening move. There is no message, no sound and no icon at the
 * moment of infection — the strain hides for minutes, and by the time you feel anything you have
 * done a hundred other things. Working out which one it was is the game.
 */
public final class Infections {

    /**
     * How long a full course runs, and how long one dose covers.
     *
     * <p>Missing the next dose abandons the course, which is the mistake this whole tier is built
     * around. Real courses are measured in days for the same reason: what kills the last, hardiest
     * organisms is <strong>persistence</strong>, not dose size.
     */
    public static final double COURSE_SECONDS = 120;
    public static final double DOSE_COVERS_SECONDS = 30;

    /**
     * Every treatment the game knows, one per drug the laboratory can test.
     *
     * <p>Built from the antibiotics rather than listed separately, so a drug cannot exist on a
     * plate and be un-makeable in an arm - the sort of gap that turns a diagnosis into a dead end
     * (rule 20).
     */
    public static final List<Treatment> KNOWN = play.xponer.astronima.sim.lab.Antibiotic.all()
            .stream()
            .map(drug -> new Treatment(drug.name(), 0.9, COURSE_SECONDS, targetsOf(drug)))
            .toList();

    /**
     * What each drug is actually good for.
     *
     * <p>Matches the MICs the plate measures: the narrow drug answers the cryophile, the broad one
     * the commensal, and the last resort touches a wreck contaminant. A player who reads the
     * antibiogram gets this for free; one who guesses pays for it.
     *
     * <p><strong>DUST/RECYCLER/SUIT closed a real gap (PLAN.md rule 117).</strong> All three
     * shipped with {@link Strain.Source} and {@link play.xponer.astronima.sim.pathogen.Pathogens}
     * long before any drug's {@code against} set named them — every dose in the game answered
     * only half the sources a player could actually catch. Grouped with the source each most
     * resembles rather than invented a new drug for: dust arrives from outside the same way ice
     * does (narrow, alongside the cryophile), water-loop biofilm and a dirty suit both grow from
     * the crew's own housekeeping the same way the general commensal does (broad, alongside it).
     *
     * <p><strong>LAST_RESORT answers every source, and WRECK gets a real second line too
     * (game-design audit #2, finding F).</strong> design/pathogens.md §3.2 states the whole
     * reason more than one antibiotic exists: "a strain that shrugged off one antimicrobial is
     * still answerable to another." That held for CRYOPHILIC/COMMENSAL (each already had two
     * routes) but not for DUST, RECYCLER, SUIT or WRECK — one wrong guess, or one missed
     * 30-second redose during the 120-second course, permanently marks that drug resistant for
     * the bout (rule: {@code Infection.stop()}), and a single-drug source had nothing else to
     * try. Real pharmacology already gives LAST_RESORT the right shape for most of the fix: a
     * last-resort antimicrobial (vancomycin, colistin) is typically broad-spectrum by nature — it
     * is held back for stewardship, to keep resistance from developing against society's actual
     * last option, not because it cannot reach other organisms — so it now targets everything
     * {@link Strain.Source} has, and all three drugs already share the identical {@code strength}
     * constant below, so a wider target set costs nothing in potency. That alone gave DUST,
     * RECYCLER and SUIT a real second line (they had zero LAST_RESORT coverage before), but WRECK
     * was already inside the old LAST_RESORT set — widening it changed nothing for WRECK
     * specifically, caught by this file's own new
     * {@code everySourceIsAnsweredByAtLeastTwoRealTreatments} the first time it ran. WRECK joins
     * NARROW instead, alongside CRYOPHILIC and DUST — the same "arrives from outside the habitat"
     * grouping those two already share, and mechanically identical to any other pairing since
     * strength does not vary by drug.
     */
    private static java.util.Set<Strain.Source> targetsOf(
            play.xponer.astronima.sim.lab.Antibiotic drug) {
        if (drug == play.xponer.astronima.sim.lab.Antibiotic.NARROW) {
            return java.util.Set.of(Strain.Source.CRYOPHILIC, Strain.Source.DUST,
                    Strain.Source.WRECK);
        }
        if (drug == play.xponer.astronima.sim.lab.Antibiotic.BROAD) {
            return java.util.Set.of(Strain.Source.COMMENSAL, Strain.Source.RECYCLER,
                    Strain.Source.SUIT);
        }
        return java.util.Set.of(Strain.Source.values());
    }

    /**
     * How likely a single exposure is to take hold.
     *
     * <p>Low, because exposure happens constantly and an illness that landed every time would make
     * the source unplayable rather than dangerous. One in forty ice blocks is roughly a bad
     * afternoon of mining.
     */
    private static final double CATCH_FROM_ICE = 0.025;

    /** Mould is a room you have neglected, so it is slower and much more certain. */
    private static final double CATCH_FROM_MOULD = 0.004;

    /**
     * Gives a player an infection, unless they already have one.
     *
     * <p>One at a time on purpose: two illnesses at once is two sets of borrowed symptoms on one
     * body, which is unreadable — and the tier is about reading it.
     *
     * @return true when it took hold
     */
    public static boolean infect(Player player, Strain.Source source) {
        if (player.getData(ModAttachments.INFECTION).isIll()) {
            return false;
        }
        player.setData(ModAttachments.INFECTION.get(),
                CarriedInfection.of(new Infection(Pathogens.of(source))));
        return true;
    }

    /** A chance of catching one, rolled once per exposure. */
    public static void expose(Player player, Strain.Source source, double chance) {
        if (player.getRandom().nextDouble() < chance) {
            infect(player, source);
        }
    }

    public static void exposeToIce(Player player) {
        expose(player, Strain.Source.CRYOPHILIC, CATCH_FROM_ICE);
    }

    public static void exposeToMould(Player player) {
        expose(player, Strain.Source.COMMENSAL, CATCH_FROM_MOULD);
    }

    /**
     * Advances whatever the player is carrying and reports what it is doing to them.
     *
     * <p>Adds to the diagnosis the atmosphere is already building rather than publishing its own,
     * because a symptom is a symptom: the biomonitor must not be able to tell a player that
     * <em>this</em> shortness of breath is the infectious one.
     *
     * @param seconds how much time this step covers
     */
    public static void tick(Player player, double seconds,
                            Map<Ailment, Ailment.Severity> diagnosis) {
        CarriedInfection carried = player.getData(ModAttachments.INFECTION);
        if (!carried.isIll()) {
            clearDesignator(player);
            return;
        }
        Infection infection = carried.revive(KNOWN);
        if (infection == null) {
            player.setData(ModAttachments.INFECTION.get(), CarriedInfection.NONE);
            clearDesignator(player);
            return;
        }
        // A course only runs while the doses keep coming. Let the cover lapse and it is
        // abandoned, which breeds resistance to that drug for ever against this organism.
        Treatment running = carried.runningCourse(KNOWN);
        double coverLeft = carried.coverLeft() - seconds;
        if (running != null) {
            infection.begin(running);
            infection.carry(carried.courseTaken() + seconds);
            if (coverLeft <= 0) {
                infection.stop();
            }
        }
        infection.step(seconds, immunityOf(player));
        player.setData(ModAttachments.INFECTION.get(),
                CarriedInfection.of(infection).withCover(Math.max(0, coverLeft)));
        player.setData(ModAttachments.VISIBLE_DESIGNATOR.get(), infection.strain().designator());

        for (Ailment symptom : infection.symptoms()) {
            diagnosis.merge(symptom, severityFor(infection.stage()),
                    (had, now) -> had.ordinal() >= now.ordinal() ? had : now);
        }
        if (infection.stage() == Strain.Stage.SEVERE) {
            // Something a player feels in their hands, not only on a panel. Short and renewed, so
            // it stops the moment the illness does rather than outliving it.
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, true, false));
        }
    }

    /**
     * How hard this body is pushing back.
     *
     * <p><strong>Hunger only, and the first version was worse than useless.</strong> It read rest
     * off the published vitals — which the infection's own symptoms are written into, so being ill
     * made you tired, being tired made you weaker, and the illness fed on itself. A feedback loop
     * nobody designed is not emergent behaviour, it is a bug that looks like one.
     *
     * <p>Rest is the second input the model wants and the mod does not track it yet; until it does,
     * one honest input beats two where one is a rumour about the other.
     */
    /** Whether the illness has reached the stage that leaves a mark (see design/chronic.md). */
    public static boolean isSevere(Player player) {
        CarriedInfection carried = player.getData(ModAttachments.INFECTION);
        return carried.isIll() && carried.revive(KNOWN).stage() == Strain.Stage.SEVERE;
    }

    public static double immunityOf(Player player) {
        double fed = player.getFoodData().getFoodLevel() / 20.0;
        // Marrow damage multiplies the rate rather than replacing it, so a damaged body that eats
        // well is still better off than a damaged body that does not - the old lever keeps working,
        // it is just worth less (design/chronic.md).
        return Immunity.of(fed, fed) * Chronics.immunityFactor(player);
    }

    /**
     * Takes a dose.
     *
     * <p>Starting a different drug abandons whatever course was running, and abandoning a course
     * breeds resistance - so switching mid-treatment costs you the first drug. That is real, and it
     * is why the plate is worth reading before the vial is opened.
     *
     * @return true when there was an infection for it to act on
     */
    public static boolean dose(Player player, play.xponer.astronima.sim.lab.Antibiotic drug) {
        CarriedInfection carried = player.getData(ModAttachments.INFECTION);
        if (!carried.isIll()) {
            return false;
        }
        Infection infection = carried.revive(KNOWN);
        if (infection == null) {
            return false;
        }
        Treatment treatment = KNOWN.stream()
                .filter(known -> known.name().equals(drug.name())).findFirst().orElse(null);
        if (treatment == null) {
            return false;
        }
        infection.begin(treatment);
        infection.carry(carried.courseTaken());
        player.setData(ModAttachments.INFECTION.get(),
                CarriedInfection.of(infection).dosed(drug.name(), DOSE_COVERS_SECONDS));
        return true;
    }

    /** No active infection means nothing for an analyzer chip to have found (design/
     *  biomonitor-chips.md §7 item 5) — an empty string, not a stale label from a past illness. */
    private static void clearDesignator(Player player) {
        if (!player.getData(ModAttachments.VISIBLE_DESIGNATOR.get()).isEmpty()) {
            player.setData(ModAttachments.VISIBLE_DESIGNATOR.get(), "");
        }
    }

    /** A worse stage hurts more, through the severity the readout already understands. */
    private static Ailment.Severity severityFor(Strain.Stage stage) {
        return switch (stage) {
            case HIDDEN, EARLY -> Ailment.Severity.MILD;
            case ESTABLISHED -> Ailment.Severity.SEVERE;
            case SEVERE -> Ailment.Severity.CRITICAL;
        };
    }

    private Infections() {}
}
