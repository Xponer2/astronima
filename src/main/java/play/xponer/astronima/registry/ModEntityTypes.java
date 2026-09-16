package play.xponer.astronima.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.telescope.TelescopeMountEntity;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Astronima.MODID);

    /** design/astra-telescope.md §2.1 — the seat a player rides while looking through a
     * telescope. Zero-sized (no hitbox of its own; the block it belongs to is what a player
     * actually clicks) and ephemeral by design (§9 open question 4) via {@code
     * TelescopeMountEntity#shouldBeSaved()} — see PLAN.md rule 75 for why that is a per-instance
     * override and not a builder flag here. Renderer: {@code ModCurioRenderers} registers
     * vanilla's own {@code NoopRenderer} — this entity has no visual and must still have a
     * renderer registered, or any client that ever sees one tracked crashes outright (rule 76).
     *
     * <p>{@code eyeHeight(0.0F)} — v4 (design/astra-telescope.md §2.3.1): the mount's own tracked
     * position *is* the eye position now, recomputed exactly every frame
     * ({@code TelescopeMountEntity#snapEyeToCurrentAim}), so {@code Camera#alignWithEntity}'s own
     * {@code position.y + eyeHeight} needs nothing added on top. A non-zero eye height here would
     * mean the eye settles toward the *wrong* target: {@code Camera}'s own eye-height smoothing
     * (0.5 per tick, carried over from whatever the previous camera entity was — the player's 1.62)
     * would visibly ease toward this value for several ticks after every {@code setCameraEntity}
     * call, which is desired (reads as leaning down into the eyepiece) only when the value it eases
     * toward is the real one. */
    public static final DeferredHolder<EntityType<?>, EntityType<TelescopeMountEntity>>
            TELESCOPE_MOUNT = ENTITY_TYPES.register("telescope_mount", () ->
                    EntityType.Builder.<TelescopeMountEntity>of(TelescopeMountEntity::new, MobCategory.MISC)
                            .noLootTable()
                            .sized(0.0F, 0.0F)
                            .eyeHeight(0.0F)
                            // NOT .noSave() — confirmed the hard way (a failing gametest): the
                            // builder's noSave() sets EntityType#canSerialize() false, and
                            // Entity#startRiding refuses outright on the server whenever
                            // !entityToRide.type.canSerialize() — riding a "no save" entity type
                            // is not just unsaved, it is un-rideable. Ephemeral (design
                            // astra-telescope.md §9 open question 4) is achieved correctly by
                            // TelescopeMountEntity#shouldBeSaved() instead, a per-instance
                            // override with no such side effect.
                            .clientTrackingRange(10)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                    Identifier.fromNamespaceAndPath(Astronima.MODID, "telescope_mount"))));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
    }

    private ModEntityTypes() {}
}
