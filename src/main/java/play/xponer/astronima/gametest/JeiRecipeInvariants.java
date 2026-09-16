package play.xponer.astronima.gametest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.compat.jei.MachinePages;
import play.xponer.astronima.compat.jei.ProcessingRecipe;
import play.xponer.astronima.crafting.CraftingTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Every JEI processing page, built for real rather than assumed from source text.
 *
 * <p>Reported as <em>"крафт в машине есть, а в jei его нет"</em> — a machine's page can go
 * empty in JEI while every other gate stays green, because {@code MachinePages.all()} lists
 * the machine and {@code JeiCoverageTest} only greps the source text of
 * {@code ProcessingRecipe.java} for item names it was told in advance to expect. A method
 * that starts returning {@code List.of()} — a stale early return, a refactor that drops the
 * last line, a branch that no longer reaches its own builder call — passes every one of those
 * scans, because the text is still there; it is just never executed.
 *
 * <p>This is the one place in the suite that actually calls {@link MachinePages#all()} and
 * then every {@code pages()} supplier in it, exactly as JEI itself does when a player opens a
 * category. It cannot run as a plain unit test: building a page constructs real
 * {@code ItemStack}s off {@code ModItems}/{@code ModBlocks}, which needs the registries a
 * gametest is the only environment in this project to boot (rule 14) — so a broken page is
 * caught here or not at all.
 *
 * <p>Walking the published set rather than a hand-kept list of machines is the point: a
 * thirtieth machine added next month is covered by the walk on the day it is written, with
 * nobody remembering to add a row for it.
 */
public final class JeiRecipeInvariants {
    public static final DeferredRegister<Consumer<GameTestHelper>> INVARIANTS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Astronima.MODID);

    @SuppressWarnings("unused")
    private static final Object JEI_PROCESSING_PAGES_ARE_REAL =
            INVARIANTS.register("jei_processing_pages_are_real",
                    () -> JeiRecipeInvariants::jeiProcessingPagesAreReal);

    private static void jeiProcessingPagesAreReal(GameTestHelper helper) {
        List<MachinePages> published;
        try {
            published = MachinePages.all();
        } catch (RuntimeException trouble) {
            helper.fail("MachinePages.all() threw building the published set: " + trouble);
            return;
        }
        // Rule 11 applied to this test itself: a walk that found nothing would pass for
        // having checked nothing, and the real list has stood at 27 or more since it was
        // written - anything much lower means the set itself failed to build, not that
        // machines were removed on purpose.
        if (published.size() < 20) {
            helper.fail("MachinePages.all() published only " + published.size()
                    + " pages - has the list failed to build, or lost machines?");
            return;
        }

        List<String> broken = new ArrayList<>();
        for (MachinePages page : published) {
            String label = page.title();

            Block machine;
            try {
                machine = page.machine().get();
            } catch (RuntimeException trouble) {
                broken.add(label + ": its own machine block would not resolve - " + trouble);
                continue;
            }
            if (machine == null) {
                broken.add(label + ": its own machine block resolved to null");
                continue;
            }

            List<ProcessingRecipe> pages;
            try {
                pages = page.pages().get();
            } catch (RuntimeException trouble) {
                broken.add(label + ": building its recipes threw - " + trouble);
                continue;
            }
            if (pages == null || pages.isEmpty()) {
                broken.add(label + ": published zero recipes - a player pressing the JEI key"
                        + " on this machine sees an empty tab, exactly as if it had never been"
                        + " added");
                continue;
            }

            for (ProcessingRecipe recipe : pages) {
                String page1 = label + " / \"" + recipe.title() + "\"";
                if (recipe.title() == null || recipe.title().isBlank()) {
                    broken.add(label + ": one of its pages has a blank title");
                }
                // Not "input must be an item": the array, the generator and the fuel cell all
                // draw ItemStack.EMPTY on purpose, because what they take is sunlight or a room's
                // own fuel gas, not something in a slot - their whole page IS the chart. What
                // must never happen is a page with NONE of the three things a page can show: no
                // item went in, nothing comes out, and no curve was even drawn.
                boolean showsAnItem = !isEmpty(recipe.input()) || hasContent(recipe.outputs());
                boolean showsAChart = recipe.series() != null && !recipe.series().isEmpty();
                if (!showsAnItem && !showsAChart) {
                    broken.add(page1 + ": shows no item and no chart - the page is blank");
                }
                if (recipe.notes() == null || recipe.notes().isEmpty()) {
                    broken.add(page1 + ": has no notes - a player gets a chart and nothing"
                            + " telling them what it means");
                }
                for (String note : recipe.notes() == null ? List.<String>of() : recipe.notes()) {
                    if (note == null || note.isBlank()) {
                        broken.add(page1 + ": has a blank note in its list");
                    }
                }
            }
        }

        if (!broken.isEmpty()) {
            helper.fail("these JEI processing pages are broken in a way no source-text scan"
                    + " catches, because building them for real is the only way to find out:\n"
                    + String.join("\n", broken));
            return;
        }
        helper.succeed();
    }

    /**
     * {@code CraftingTree.Transformation} pairs that are real, but not a
     * {@code ProcessingMenu} machine step with its own JEI page — each with the reason, so a
     * stale entry (the pair renamed, or later given a real page) is caught rather than quietly
     * excusing something new.
     *
     * <p>Found by the same audit that found {@code metal_billet}: every {@code Transformation}
     * is a claim that some machine turns one real item into another, and {@code metal_billet}'s
     * own bug — a real transformation with literally zero JEI presence on either end — is a
     * shape worth checking for everywhere at once, not once per report.
     */
    private static final Set<String> EXCUSED_TRANSFORMATIONS = Set.of(
            // A refill, not a process: the same empty tank/canister comes back full. Nothing is
            // turned into anything for JEI to chart.
            "astronima:oxygen_tank<-astronima:oxygen_tank_empty",
            "astronima:ammonia_canister<-astronima:ammonia_canister_empty",
            // A combustion state change (CombustionEvents), not a machine with a screen or a
            // JEI category at all.
            "astronima:unlit_torch<-minecraft:torch",
            // The magic tier (design/astra-*.md) is a wholly separate system with its own
            // presentation, not a ProcessingMenu machine - out of this check's scope the same
            // way celestial_atlas is excused from JeiCoverageTest.
            "astronima:asterium_grains<-astronima:astra_collector",
            // Scraped off a running generator, not processed from it - CraftingTree's own
            // comment calls this "the honest statement of you get this by owning one and
            // running it", not a feed/product relationship a chart could show.
            "astronima:sludge<-astronima:combustion_generator");

    @SuppressWarnings("unused")
    private static final Object JEI_MACHINE_TRANSFORMATIONS_ARE_VISIBLE =
            INVARIANTS.register("jei_machine_transformations_are_visible",
                    () -> JeiRecipeInvariants::jeiMachineTransformationsAreVisible);

    /**
     * Every {@code CraftingTree.Transformation} — "some machine turns this into that" — must
     * name two items a player can actually find on a real JEI page, or the transformation is
     * invisible: reachable, according to the crafting-tree model that gates what a fresh item is
     * allowed to assume exists, but with no page anywhere showing a player it happens.
     *
     * <p>This is exactly the shape of the reported {@code metal_billet} bug, generalised: a
     * machine can do a real step (declared here, so the reachability graph knows about it) and
     * still have zero JEI presence for one end of it, because the JEI page for that machine
     * shows a different, collapsed version of the same process. Walking every declared
     * transformation catches the next one of these without waiting for a screenshot.
     */
    private static void jeiMachineTransformationsAreVisible(GameTestHelper helper) {
        Set<String> shown = new HashSet<>();
        for (MachinePages page : MachinePages.all()) {
            for (ProcessingRecipe recipe : page.pages().get()) {
                addId(shown, recipe.input());
                for (ItemStack stack : recipe.extraInputs()) {
                    addId(shown, stack);
                }
                for (ItemStack stack : recipe.outputs()) {
                    addId(shown, stack);
                }
            }
        }

        List<String> invisible = new ArrayList<>();
        Set<String> staleExcuses = new HashSet<>(EXCUSED_TRANSFORMATIONS);
        for (CraftingTree.Source source : CraftingTree.sources()) {
            if (!(source instanceof CraftingTree.Transformation transformation)) {
                continue;
            }
            String key = transformation.result() + "<-" + transformation.from();
            if (EXCUSED_TRANSFORMATIONS.contains(key)) {
                staleExcuses.remove(key);
                continue;
            }
            List<String> missing = new ArrayList<>();
            if (!shown.contains(transformation.result())) {
                missing.add(transformation.result() + " (the product)");
            }
            if (!shown.contains(transformation.from())) {
                missing.add(transformation.from() + " (the feed)");
            }
            if (!missing.isEmpty()) {
                invisible.add(key + ": " + String.join(" and ", missing)
                        + " never appear on any real JEI page");
            }
        }

        if (!staleExcuses.isEmpty()) {
            helper.fail("EXCUSED_TRANSFORMATIONS names pairs CraftingTree no longer declares - "
                    + "either the transformation was renamed (fix the entry) or it now has a "
                    + "real page (delete the entry): " + new java.util.TreeSet<>(staleExcuses));
            return;
        }
        if (!invisible.isEmpty()) {
            helper.fail("these machine transformations are reachable in the crafting model but "
                    + "invisible in JEI - a player can never see them happen:\n"
                    + String.join("\n", invisible));
            return;
        }
        helper.succeed();
    }

    private static void addId(Set<String> ids, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
    }

    private static boolean isEmpty(net.minecraft.world.item.ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    private static boolean hasContent(List<net.minecraft.world.item.ItemStack> stacks) {
        if (stacks == null) {
            return false;
        }
        for (net.minecraft.world.item.ItemStack stack : stacks) {
            if (!isEmpty(stack)) {
                return true;
            }
        }
        return false;
    }

    private JeiRecipeInvariants() {}
}
