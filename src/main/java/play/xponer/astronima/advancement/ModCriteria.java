package play.xponer.astronima.advancement;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import play.xponer.astronima.Astronima;

/** Triggers the advancement tree listens for. */
public final class ModCriteria {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Astronima.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, HabitatTrigger> HABITAT =
            TRIGGERS.register("habitat", HabitatTrigger::new);

    public static final DeferredHolder<CriterionTrigger<?>, AirlockTrigger> AIRLOCK =
            TRIGGERS.register("airlock", AirlockTrigger::new);

    private ModCriteria() {}
}
