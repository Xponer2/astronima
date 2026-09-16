package play.xponer.astronima.client.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.client.model.MoldModels;
import play.xponer.astronima.client.model.PlumbingModels;
import play.xponer.astronima.client.model.SuitModels;
import play.xponer.astronima.client.model.TelescopeModels;
import play.xponer.astronima.client.model.WireModels;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.registry.ModEntityTypes;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.registry.ModMenus;
import play.xponer.astronima.telescope.TelescopeMountEntity;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Wires the worn equipment into the entity renderer.
 *
 * <p>Two separate steps, and the order matters: layer definitions are the geometry and
 * have to exist before anything bakes them, while renderers are associated with items
 * during client setup. Registering a renderer for an item whose layer was never
 * declared fails at bake time rather than here, which is why both live in one class —
 * adding a piece of equipment means touching exactly one file.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class ModCurioRenderers {
    /**
     * Real 3D geometry the crosshair readout cannot substitute for: the valve's handwheel,
     * which has to be seen turning, and the retort's own light.
     *
     * <p>Tank, pump, airlock, condenser and scrubber used to register a renderer here too, for
     * no reason beyond drawing the now-removed world-space gauge — {@code LookAtReadout}
     * (screen-space, at the crosshair) carries every one of their readings today, so those
     * five had nothing left to draw and are gone rather than kept as decoration.
     *
     * <p>{@code DisplayBlock}'s own renderer is gone for a different reason: the block itself
     * is removed (design/display.md §7/§8) — {@code PartType.FRAMEBUFFER} is the real,
     * reachable screen now, drawn by {@code PartRenderer} alongside every other wire-layer part.
     */
    @SubscribeEvent
    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.GAS_VALVE.get(),
                PlumbingIndicatorRenderers.Valve::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SOLAR_RETORT.get(),
                PlumbingIndicatorRenderers.Retort::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TELESCOPE.get(),
                TelescopeBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MOLD.get(),
                MoldBlockEntityRenderer::new);
        // TelescopeMountEntity is a camera anchor, never meant to be seen — the crash this
        // registration fixes was not "wrong renderer," it was "no renderer at all":
        // EntityRenderDispatcher NPEs on any tracked entity with none registered. Vanilla's own
        // NoopRenderer exists for exactly this case (design/astra-telescope.md §2.1).
        event.registerEntityRenderer(ModEntityTypes.TELESCOPE_MOUNT.get(),
                net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    @SubscribeEvent
    private static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SuitModels.SUIT, SuitModels::suit);
        event.registerLayerDefinition(SuitModels.TANK, SuitModels::tank);
        event.registerLayerDefinition(SuitModels.CARTRIDGE, SuitModels::cartridge);
        event.registerLayerDefinition(PlumbingModels.VALVE_WHEEL, PlumbingModels::wheel);
        event.registerLayerDefinition(TelescopeModels.YOKE, TelescopeModels::yoke);
        event.registerLayerDefinition(TelescopeModels.TUBE, TelescopeModels::tube);
        // Wire has no block and therefore no block-entity renderer; WireRenderer bakes this
        // once and submits it per segment.
        event.registerLayerDefinition(WireModels.WIRE, WireModels::wire);
        // Same recipe, for MoldBlockEntityRenderer's per-twig segments.
        event.registerLayerDefinition(MoldModels.TWIG, MoldModels::twig);
    }

    /** Machine screens. Vanilla's registry is private; this is the supported hook. */
    @SubscribeEvent
    private static void onRegisterScreens(
            net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(ModMenus.PROCESSING.get(),
                com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen::new);
        event.register(ModMenus.AIRLOCK.get(),
                com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen::new);
        event.register(ModMenus.INCUBATOR.get(),
                com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen::new);
        event.register(ModMenus.MICROSCOPE.get(),
                com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen::new);
        event.register(ModMenus.SYNTHESISER.get(),
                com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen::new);
        event.register(ModMenus.STORAGE_DRIVE.get(),
                com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen::new);
        event.register(ModMenus.STORAGE_TERMINAL.get(),
                com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen::new);
    }

    @SubscribeEvent
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ICurioRenderer.register(ModItems.EVA_SUIT.get(), EvaSuitRenderer::new);
            // Both tank states render: an empty bottle on your back is exactly the
            // thing you want to notice without opening a screen.
            ICurioRenderer.register(ModItems.OXYGEN_TANK.get(),
                    () -> new WornCurioRenderer(SuitModels.TANK, "oxygen_tank"));
            ICurioRenderer.register(ModItems.OXYGEN_TANK_EMPTY.get(),
                    () -> new WornCurioRenderer(SuitModels.TANK, "oxygen_tank_empty"));
            ICurioRenderer.register(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get(),
                    () -> new WornCurioRenderer(SuitModels.CARTRIDGE, "lioh_cartridge"));
        });
    }

    private ModCurioRenderers() {}
}
