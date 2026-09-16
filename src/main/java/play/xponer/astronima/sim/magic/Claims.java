package play.xponer.astronima.sim.magic;

import java.util.List;
import java.util.Set;

/**
 * The tier's first real claim (design/astra-research-m4a.md) — {@code sim/magic/Research} proved
 * the mechanism against synthetic test claims; this is the first one a player actually resolves.
 *
 * <p><strong>Substitution, named:</strong> astra-research.md §3.3 evidences "the same elements
 * are everywhere" with the Sun and an emission nebula. The Sun has no
 * {@code NamedSkyObjects.Placement} — unlike the four deep-sky objects, it is not a fixed
 * sky-space direction a static chart can draw — so this claim is evidenced by two nebulae
 * instead: {@link ObservationTarget#ORION_NEBULA} and {@link ObservationTarget#HELIX_NEBULA} both
 * carry a real {@link SpectralLine#H_ALPHA}, and the claim's actual point — the same line, from
 * two unrelated objects, is how we learned the universe has one chemistry — holds exactly as well
 * for two nebulae as for a star and a nebula.
 */
public final class Claims {

    /**
     * Stage tables (design/astra-atlas-s3c-completion.md §3, revised once here from a fact that
     * doc did not check): three stages each, same skeleton, different lesson — observe (the
     * historical resolve), instrument (own the filters this branch's lines are read through),
     * commit (hand in the exposures the claim spends).
     *
     * <p><strong>Revision, recorded:</strong> the design's stage-3 "Owns celestial_atlas" was an
     * unobtainable gate — {@code JeiCoverageTest.DELIBERATELY_BARE} carries that item precisely
     * because nothing can produce it until the ritual system exists, and gating a claim on it is
     * the exact rule-6 betrayal S3d's own mutation list forbids. It is replaced by the plate
     * hand-in (crafted, real cost, and the one place in this tier where an item genuinely is
     * ammunition). The grant moved with it: the unlock lands at the commitment stage, the payoff.
     *
     * <p>same_elements instruments with the two filters whose shared peaks the claim rests on
     * (H-alpha is shared but is also Andromeda's, so the metal branch's iron filter stays its
     * own single key); metal_assay instruments with the iron filter that names a metal without
     * breaking the rock. Filters are {@code Owns}, never {@code HandsIn}: reusable equipment by
     * their own item doc's rule.
     *
     * <p>Grants use the {@code research:<claim>} convention, one currency with
     * {@code CodexMarkup.Block.Locked} (S3d-1); ResearchCoverageTest walks them.
     */
    private static List<ResearchStage> staged(String id, String[] ownFilters,
                                              ObservationTarget a, ObservationTarget b) {
        String base = "astronima.research." + id;
        List<Requirement> instruments = new java.util.ArrayList<>(ownFilters.length);
        for (String filter : ownFilters) {
            instruments.add(new Requirement.Owns(filter, 1));
        }
        return List.of(
                ResearchStage.of(0,
                        List.of(new Requirement.Identified(Claims.objectId(a)),
                                new Requirement.Identified(Claims.objectId(b))),
                        base + ".stage1"),
                ResearchStage.of(1, instruments, base + ".stage2"),
                new ResearchStage(2,
                        List.of(new Requirement.HandsIn("astronima:spectral_plate", 2)),
                        base + ".stage3",
                        Set.of("research:" + id)));
    }

    public static final Research.Claim SAME_ELEMENTS = Research.Claim.staged("same_elements",
            staged("same_elements",
                    new String[]{"astronima:filter_helium_i", "astronima:filter_forbidden_oiii"},
                    ObservationTarget.ORION_NEBULA, ObservationTarget.HELIX_NEBULA));

    public static final Research.Claim METAL_ASSAY = Research.Claim.staged("metal_assay",
            staged("metal_assay",
                    new String[]{"astronima:filter_iron_i"},
                    ObservationTarget.ANDROMEDA_GALAXY, ObservationTarget.M32));

    /**
     * astra-research.md §3.3's second claim — "a line names a metal without breaking the rock."
     * §3.3.1 records why the literal wording (a heated lab sample's Fe I forest, matched to the
     * catalogue) could not be built with the roster that existed at the time: {@link
     * ObservationTarget#SUN} carries {@link SpectralLine#IRON_I} but has no
     * {@link play.xponer.astronima.sim.optics.NamedSkyObjects.Placement} and cannot be captured
     * at all, and of the four then-capturable objects only {@link ObservationTarget#ANDROMEDA_GALAXY}
     * carried {@link SpectralLine#IRON_I} — no second object to pair it with.
     *
     * <p>Substitution, in the same spirit {@link #SAME_ELEMENTS} already uses: {@link
     * ObservationTarget#M32}, Andromeda's own real compact-elliptical companion, was added
     * carrying the identical old-star absorption suite (design/astra-research.md §3.3.1's own
     * "the honest candidate is a globular cluster or a second elliptical-bulge-type object").
     * The claim's actual point survives the substitution exactly: matching Fe I between two
     * unrelated old stellar populations — a spiral's bulge and a wholly separate dwarf galaxy —
     * identifies the element without touching either one, precisely what a heated sample and a
     * catalogue match would have demonstrated in a lab.
     */
    /**
     * design/astra-atlas-scope.md's own headline proof: astra's subject joins the same claim
     * engine the sky already uses, as data, not a second mechanism (§4 — "one claim/evidence/
     * resolve engine, generalised, not two"). The claim itself teaches astra-core.md §2.4/§4.1's
     * two real facts together — the field is a real gradient (denser toward the core, not a flat
     * ambient number) and a real, finite resource (it depletes with use, per {@code AstraField}) —
     * the same "a real comparison proves a real pattern" shape {@link #SAME_ELEMENTS} already
     * uses, applied to one continuous field instead of two discrete objects.
     *
     * <p>Evidenced by {@link AstraEvidence}'s two tokens rather than an {@code ObservationTarget}:
     * astra has no discrete named target to decode, only a real position's own real reading, so
     * {@code item.AstraFieldMeterItem} identifies a token directly the moment a real reading lands
     * at either end of the gradient — the same verb, applied to a continuous subject instead of a
     * catalogued one.
     */
    public static final Research.Claim ASTRA_GRADIENT = Research.Claim.staged("astra_gradient",
            List.of(
                    ResearchStage.of(0,
                            List.of(new Requirement.Identified(AstraEvidence.SHALLOW_READING),
                                    new Requirement.Identified(AstraEvidence.DEEP_READING)),
                            "astronima.research.astra_gradient.stage1"),
                    ResearchStage.of(1,
                            List.of(new Requirement.Owns("astronima:astra_field_meter", 1),
                                    new Requirement.Owns("astronima:astra_collector", 1)),
                            "astronima.research.astra_gradient.stage2"),
                    new ResearchStage(2,
                            List.of(new Requirement.HandsIn("astronima:asterium_grains", 2)),
                            "astronima.research.astra_gradient.stage3",
                            Set.of("research:astra_gradient"))));

    public static final List<Research.Claim> ALL = List.of(SAME_ELEMENTS, METAL_ASSAY, ASTRA_GRADIENT);

    /** The id an object is known by everywhere a claim, a marker, or the research command names
     *  one — one spelling, so a typo in a second copy can never quietly stop matching. */
    public static String objectId(ObservationTarget target) {
        return target.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The evidence a set of captured plates offers <em>toward one specific claim</em>: the
     * distinct objects among them that the claim actually asks for.
     *
     * <p>Filtered by the claim on purpose, not just collected — {@code Research.resolve} wants
     * <em>exactly</em> a claim's required evidence (§4: "a claim wants exactly its own evidence,
     * not a superset"), which a real player's whole inventory practically never is once they are
     * carrying more than one exposure. Filtering to what this claim asks for first is what lets
     * "resolve from whatever you're holding" (this leaf's stand-in for the real drag-to-vertex
     * UI) coexist with that already-proven exact-match rule, rather than making every resolve
     * with an unrelated plate in a pocket fail for a reason a player laying evidence by hand
     * would never hit.
     */
    public static Set<String> evidenceFor(Research.Claim claim, List<CapturedSpectrum> plates) {
        Set<String> evidence = new java.util.HashSet<>();
        for (CapturedSpectrum plate : plates) {
            String id = objectId(plate.target());
            if (claim.requiredEvidence().contains(id)) {
                evidence.add(id);
            }
        }
        return evidence;
    }

    /**
     * Same filtering as {@link #evidenceFor(Research.Claim, List)}, against a plain set of
     * object ids directly. {@code ClaimResolvePayload} calls this with
     * {@code ResearchState.identifiedObjects()} (design/astra-research-m4b.md §3) — a captured
     * object is not usable evidence on its own any more; only one a player has actually decoded
     * counts.
     */
    public static Set<String> evidenceFor(Research.Claim claim, Set<String> objectIds) {
        Set<String> evidence = new java.util.HashSet<>(objectIds);
        evidence.retainAll(claim.requiredEvidence());
        return evidence;
    }

    private Claims() {}
}
