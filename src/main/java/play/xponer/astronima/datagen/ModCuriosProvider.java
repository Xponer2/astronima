package play.xponer.astronima.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import play.xponer.astronima.registry.ModItems;
import top.theillusivec4.curios.api.CuriosDataProvider;

import java.util.concurrent.CompletableFuture;

/**
 * Declares the suit's equipment slots.
 *
 * <p>These are dedicated slots rather than inventory space because the loadout has to
 * be both <em>visible</em> and <em>finite</em>: you should be able to see at a glance
 * that no scrubber cartridge is fitted, and carrying six spare tanks to hot-swap mid
 * EVA would remove the tension the whole system exists to create.
 */
public class ModCuriosProvider extends CuriosDataProvider {
    public ModCuriosProvider(String modId, PackOutput output,
                             CompletableFuture<HolderLookup.Provider> registries) {
        super(modId, output, registries);
    }

    @Override
    public void generate(HolderLookup.Provider registries) {
        // The pressure garment. Everything else depends on it, so it exists alone.
        createSlot("suit").size(1).order(0).icon(icon("suit"));
        // Consumables are fitted, not carried: one of each, consumed in place.
        createSlot("tank").size(1).order(1).icon(icon("tank"));
        createSlot("cartridge").size(1).order(2).icon(icon("cartridge"));
        // Implants (medicine tier): deliberately few, so diagnosis competes with
        // quality-of-life rather than stacking with it.
        createSlot("implant").size(2).order(3).icon(icon("implant"));
        // Footwear, and its own slot rather than a flavour of "suit": the boots are the only
        // part of the loadout whose usefulness depends on what you are standing on, so they
        // are taken off and put back on far more often than anything else.
        createSlot("boots").size(1).order(4).icon(icon("boots"));
        // Diagnostic eyewear. Its own slot rather than an implant: it is worn and swapped, and
        // making it compete with a chip would price a *readout* against a body modification.
        createSlot("goggles").size(1).order(5).icon(icon("goggles"));

        createEntities("player").addPlayer()
                .addSlots("suit", "tank", "cartridge", "implant", "boots", "goggles");

        // Which items each slot will accept. Both tank states are equippable so a
        // spent tank can be left fitted and swapped deliberately, rather than
        // silently vanishing at the moment it runs out.
        tag("suit").add(ModItems.EVA_SUIT.get());
        tag("tank").add(ModItems.OXYGEN_TANK.get(), ModItems.OXYGEN_TANK_EMPTY.get());
        tag("cartridge").add(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get());
        tag("boots").add(ModItems.MAGNETIC_BOOTS.get());
        tag("goggles").add(ModItems.DIAGNOSTIC_GOGGLES.get());
        // The chip ladder's own two rungs (design/biomonitor-chips.md) - the slot this comment
        // above already anticipated, still empty until now.
        tag("implant").add(ModItems.BASIC_BIOMONITOR_CHIP.get(), ModItems.PATHOGEN_ANALYZER_CHIP.get());
    }

    /**
     * Slot background icons, as GUI <em>sprite ids</em> — not texture paths.
     *
     * <p>This is the distinction that made the first attempt silently draw nothing.
     * Curios ships an atlas source ({@code assets/minecraft/atlases/gui.json}) with
     * {@code {source: "slot", prefix: "slot/"}}, which stitches every mod's
     * {@code assets/<ns>/textures/slot/*.png} into the GUI atlas under the id
     * {@code <ns>:slot/<file>}. A raw path like {@code astronima:textures/gui/slot/suit.png}
     * is not a sprite in that atlas, so it resolves to nothing at all — and because
     * a missing icon is a legitimate state, nothing is logged.
     */
    private static Identifier icon(String slot) {
        return Identifier.fromNamespaceAndPath("astronima", "slot/" + slot);
    }
}
