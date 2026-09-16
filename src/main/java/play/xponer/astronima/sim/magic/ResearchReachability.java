package play.xponer.astronima.sim.magic;

import play.xponer.astronima.crafting.CraftingTree;
import play.xponer.astronima.sim.optics.NamedSkyObjects;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The joint item+research reachability walk (design/astra-atlas-s3d-unlocks.md §3): from an
 * empty world, which items and which claims become reachable, where a gated item needs its own
 * claim held and a claim can itself need a gated item (a lens tier, chiefly). Neither
 * {@code CraftingTree} nor {@code Claims} can answer this alone — the item graph knows nothing
 * about claims, and a claim's own reachability depends on the item graph — so this is the one
 * class allowed to depend on both, kept out of {@code crafting} so that package's own
 * independence from {@code sim.magic} stays true.
 *
 * <p><strong>Deliberately not {@link CraftingTree#reachableFromBareHands()} plus a claim check
 * bolted on afterward.</strong> That function is blind to {@link CraftingTree#researchGateOf}
 * by design (see its own doc) — reusing its pre-computed result as this walk's own starting
 * point would already contain the gated item, having reached it purely through ingredients
 * with the gate never consulted at all. This class rebuilds the item closure from
 * {@code WorldSource} up, growing items and claims together in one fixed point, so a gated
 * result can only ever enter the item set after its gate claim has entered the claim set.
 */
public final class ResearchReachability {

    public record Result(Set<String> reachableItems, Set<String> reachableClaims) {}

    /** Every item and every claim reachable from an empty world, holding nothing. */
    public static Result compute() {
        List<CraftingTree.Source> sources = CraftingTree.sources();
        Set<String> items = new HashSet<>();
        for (CraftingTree.Source source : sources) {
            if (source instanceof CraftingTree.WorldSource world) {
                items.add(world.id());
            }
        }
        Set<String> claims = new HashSet<>();

        boolean progress = true;
        while (progress) {
            progress = false;
            for (CraftingTree.Source source : sources) {
                String result = resultOf(source);
                if (result == null || items.contains(result)) {
                    continue;
                }
                String gate = CraftingTree.researchGateOf(result);
                if (gate != null && !claims.contains(gate)) {
                    continue;
                }
                if (ingredientsReachable(source, items)) {
                    items.add(result);
                    progress = true;
                }
            }
            for (Research.Claim claim : Claims.ALL) {
                if (!claims.contains(claim.id()) && isClaimReachable(claim, items, claims)) {
                    claims.add(claim.id());
                    progress = true;
                }
            }
        }
        return new Result(Set.copyOf(items), Set.copyOf(claims));
    }

    /** Whether every requirement across every stage of {@code claim} is individually
     *  satisfiable — the same flat, no-ordering-assumed check
     *  {@code ResearchReachabilityTest.everyRequirementItemIsCraftableFromBareHands} already
     *  applies to {@code Owns}/{@code HandsIn}, extended here to {@code Identified} (real lens
     *  tier) and {@code Holds} (another claim, recursively — this method's own caller loop is
     *  what makes the recursion a real fixed point rather than one pass). */
    private static boolean isClaimReachable(Research.Claim claim, Set<String> items, Set<String> claims) {
        for (ResearchStage stage : claim.stages()) {
            for (Requirement requirement : stage.requirements()) {
                boolean met = switch (requirement) {
                    case Requirement.Identified id -> isObjectIdentifiable(id.objectId(), items);
                    case Requirement.Owns owns -> items.contains(owns.itemId());
                    case Requirement.HandsIn handsIn -> items.contains(handsIn.itemId());
                    case Requirement.Holds holds -> claims.contains(holds.claimId());
                };
                if (!met) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether {@code objectId} can be identified at all — real per-object required lens tier
     *  (design/astra-research.md §2b), not merely "is there an instrument" (the instruments
     *  themselves are always reachable today and carry no gate of their own). An object with no
     *  {@link NamedSkyObjects.Placement} (the Sun) is not on the atlas chart yet at all — that is
     *  a real, separate gap this walk does not invent an answer for; treated as identifiable so
     *  it never falsely blocks a claim that does not actually depend on the missing placement. */
    private static boolean isObjectIdentifiable(String objectId, Set<String> items) {
        NamedSkyObjects.Placement placement = placementFor(objectId);
        if (placement == null) {
            return true;
        }
        return isLensTierReachable(placement.requiredLensTier(), items);
    }

    /**
     * Whether a lens of {@code tier} is reachable. Deliberately a small, named mapping rather
     * than a generic "which item grants which tier" registry — there is exactly one lens beyond
     * the base today ({@code astronima:wide_aperture_lens}, tier 2). A real tier 3 needs a real
     * new item and a real decision about what it costs, at which point this method grows one
     * more honest branch rather than a guessed-at general mechanism built for a case that does
     * not exist yet.
     */
    private static boolean isLensTierReachable(int tier, Set<String> items) {
        if (tier <= NamedSkyObjects.BASE_LENS_TIER) {
            return true;
        }
        if (tier == NamedSkyObjects.BASE_LENS_TIER + 1) {
            return items.contains("astronima:wide_aperture_lens");
        }
        return false;
    }

    private static NamedSkyObjects.Placement placementFor(String objectId) {
        for (NamedSkyObjects.Placement placement : NamedSkyObjects.ALL) {
            if (Claims.objectId(placement.target()).equals(objectId)) {
                return placement;
            }
        }
        return null;
    }

    private static String resultOf(CraftingTree.Source source) {
        return switch (source) {
            case CraftingTree.WorldSource ignored -> null;
            case CraftingTree.Shaped shaped -> shaped.result();
            case CraftingTree.Shapeless shapeless -> shapeless.result();
            case CraftingTree.Cooking cooking -> cooking.result();
            case CraftingTree.Transformation transformation -> transformation.result();
        };
    }

    private static boolean ingredientsReachable(CraftingTree.Source source, Set<String> items) {
        return switch (source) {
            case CraftingTree.WorldSource ignored -> true;
            case CraftingTree.Shaped shaped -> items.containsAll(shaped.keys().values());
            case CraftingTree.Shapeless shapeless -> items.containsAll(shapeless.inputs().keySet());
            case CraftingTree.Cooking cooking -> items.contains(cooking.input());
            case CraftingTree.Transformation transformation -> items.contains(transformation.from());
        };
    }

    private ResearchReachability() {}
}
