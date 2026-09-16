package play.xponer.astronima.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import play.xponer.astronima.Astronima;

/** Keys for the damage types defined in {@code data/astronima/damage_type/}. */
public final class ModDamageTypes {
    /** Death by oxygen starvation (vacuum or depleted room). */
    public static final ResourceKey<DamageType> SUFFOCATION = key("suffocation");

    /** Death by CO2 poisoning — distinct because it happens with oxygen still present. */
    public static final ResourceKey<DamageType> HYPERCAPNIA = key("hypercapnia");
    public static final ResourceKey<DamageType> THERMAL = key("thermal");

    /** Carbon monoxide: the accumulated-dose killer (sim.tox.GasToxicity). */
    public static final ResourceKey<DamageType> CARBON_MONOXIDE = key("carbon_monoxide");

    /** Acute toxic gas exposure (H2S, SO2, NH3). */
    public static final ResourceKey<DamageType> TOXIC_GAS = key("toxic_gas");

    /** Decompression sickness — nitrogen bubbling out of supersaturated tissue. */
    public static final ResourceKey<DamageType> DECOMPRESSION = key("decompression");

    /** Lungs torn by the surroundings falling faster than they can vent. */
    public static final ResourceKey<DamageType> BAROTRAUMA = key("barotrauma");

    /**
     * Arriving somewhere too fast, in any direction.
     *
     * <p>Its own type rather than vanilla's fall damage, because it is not a fall: on a body
     * with no air and a twentieth of a gravity, the energy that hurts you is as likely to be
     * horizontal as vertical.
     */
    public static final ResourceKey<DamageType> IMPACT = key("impact");

    /** Acute radiation syndrome — the accumulated-gamma-dose killer (sim.rad.RadiationDose). */
    public static final ResourceKey<DamageType> RADIATION = key("radiation");

    /** Systemic fluoride poisoning from real HF contact — ignores worn armor by design
     *  (sim.physio.ChemicalBurn, design/halogens.md §22-23, Part C2). */
    public static final ResourceKey<DamageType> CHEMICAL_BURN = key("chemical_burn");

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Astronima.MODID, path));
    }

    private ModDamageTypes() {}
}
