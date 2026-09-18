package play.xponer.astronima.atmosphere;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.Config;
import play.xponer.astronima.item.OxygenTanks;
import play.xponer.astronima.item.SuitLifeSupport;
import play.xponer.astronima.item.SuitLoadout;
import play.xponer.astronima.sim.suit.SuitCondition;
import play.xponer.astronima.sim.suit.SuitSubsystem;
import play.xponer.astronima.sim.suit.SuitTelemetry;
import play.xponer.astronima.sim.suit.ThermalProtection;
import play.xponer.astronima.network.AtmosphereStatusPayload;
import play.xponer.astronima.network.AstraFieldReadingPayload;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModDamageTypes;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.astra.AstraFieldGeometry;
import play.xponer.astronima.sim.world.AsteroidBody;
import play.xponer.astronima.sim.sky.SkyEventOverride;
import play.xponer.astronima.sim.Acoustics;
import play.xponer.astronima.sim.Humidity;
import play.xponer.astronima.sim.burn.Flammability;
import play.xponer.astronima.sim.physio.Ailment;
import play.xponer.astronima.sim.physio.Hypothermia;
import play.xponer.astronima.sim.physio.Barotrauma;
import play.xponer.astronima.sim.physio.Consciousness;
import play.xponer.astronima.sim.physio.DecompressionModel;
import play.xponer.astronima.sim.physio.VitalSigns;

import java.util.EnumMap;
import java.util.Map;
import play.xponer.astronima.sim.tox.GasToxicity;
import play.xponer.astronima.sim.Breathing;
import play.xponer.astronima.sim.suit.HelmetAtmosphere;
import play.xponer.astronima.sim.Co2Status;
import play.xponer.astronima.advancement.HabitatTrigger;
import play.xponer.astronima.advancement.ModCriteria;
import play.xponer.astronima.sim.HabitatStandard;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.O2Status;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.thermal.HeatBalance;

/**
 * Hooks the atmosphere simulation into the game loop: players breathe every tick,
 * rooms exchange gas on the atmosphere cadence, and any block change invalidates the
 * rooms it touches.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class AtmosphereEvents {
    @SubscribeEvent
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }

        Atmosphere atmosphere = Atmosphere.get(level);
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        Atmosphere.RoomReading reading = atmosphere.readingNear(eyePos);

        // Diagnosis is gathered by the same code that applies the harm, so the
        // monitor can never disagree with what is actually hurting the player.
        Map<Ailment, Ailment.Severity> diagnosis = new EnumMap<>(Ailment.class);
        try {
            tickPhysiology(player, level, atmosphere, reading, eyePos, diagnosis);
            // An illness borrows the same symptom list, so it lands in the same diagnosis the
            // room's hazards do - which is exactly what makes an infection first read as the
            // room doing something to you (design/pathogens.md §1).
            play.xponer.astronima.physio.Infections.tick(player,
                    Atmosphere.TICK_INTERVAL / 20.0, diagnosis);
            // Lasting damage accumulates from what the diagnosis already says, so there is one
            // list of what is wrong with the player rather than two that can disagree.
            play.xponer.astronima.physio.Chronics.tick(player, Atmosphere.TICK_INTERVAL / 20.0,
                    diagnosis, play.xponer.astronima.physio.Infections.isSevere(player)
                            || radiationSevere(player));
            // Real macronutrient reserves drain on the same real clock (design/macronutrients.md
            // §3f) - not gated on diagnosis, since a starving body is starving whether or not the
            // room is doing anything to it.
            play.xponer.astronima.physio.Nutrition.tick(player, Atmosphere.TICK_INTERVAL / 20.0);
        } finally {
            publishVitals(player, diagnosis);
            publishSuitTelemetry(player, reading);
            checkHabitatMilestones(player, reading);
        }
    }

    private static void publishVitals(ServerPlayer player, Map<Ailment, Ailment.Severity> diagnosis) {
        int packed = VitalSigns.pack(diagnosis);
        if (player.getData(ModAttachments.VITALS) != packed) {
            player.setData(ModAttachments.VITALS.get(), packed);
        }
    }

    /**
     * The suit reporting on itself — but only if its status display has been repaired.
     * A dark display publishes nothing rather than zeroes, so the panel can stay
     * absent instead of lying about a tank it cannot read.
     */
    /**
     * Credits the player for a room that actually works.
     *
     * <p>Checked here because this is the one place that already knows a room's seal,
     * volume and full gas composition. Anywhere else would have to ask again and could
     * disagree — and an advancement that disagrees with the biomonitor about whether
     * the air is breathable is worse than no advancement.
     */
    private static void checkHabitatMilestones(ServerPlayer player,
                                               Atmosphere.@Nullable RoomReading reading) {
        if (reading == null || player.tickCount % 40 != 0) {
            return;
        }
        RoomState room = reading.state();
        boolean shelter = HabitatStandard.isSealedShelter(reading.sealed(), room.volumeBlocks());
        if (!shelter) {
            return;
        }
        boolean breathable = HabitatStandard.isBreathableHabitat(
                reading.sealed(), room.volumeBlocks(),
                room.partialPressureKPa(Gas.OXYGEN), room.pressureKPa(),
                room.partialPressureKPa(Gas.CARBON_DIOXIDE));

        ModCriteria.HABITAT.get().trigger(player, breathable
                ? HabitatTrigger.Standard.BREATHABLE : HabitatTrigger.Standard.SHELTER);
    }

    private static void publishSuitTelemetry(ServerPlayer player, Atmosphere.@Nullable RoomReading reading) {
        int packed;
        if (SuitLoadout.suit(player).isEmpty()) {
            packed = SuitTelemetry.ABSENT;
        } else {
            int condition = SuitLoadout.condition(player);
            double ambientPpO2 = reading != null ? reading.state().partialPressureKPa(Gas.OXYGEN) : 0.0;
            packed = SuitTelemetry.pack(
                    SuitTelemetry.levelOf(OxygenTanks.fittedTankFraction(player)),
                    SuitTelemetry.levelOf(OxygenTanks.fittedCartridgeFraction(player)),
                    SuitCondition.isCurrentlySealed(ambientPpO2,
                            SuitCondition.canHoldPressure(condition)),
                    SuitCondition.isWorking(condition, SuitSubsystem.STATUS_DISPLAY));
        }
        if (player.getData(ModAttachments.SUIT_TELEMETRY) != packed) {
            player.setData(ModAttachments.SUIT_TELEMETRY.get(), packed);
        }
    }

    private static void tickPhysiology(ServerPlayer player, ServerLevel level, Atmosphere atmosphere,
                                       Atmosphere.@Nullable RoomReading reading, BlockPos eyePos,
                                       Map<Ailment, Ailment.Severity> diagnosis) {
        // Decompression physiology runs in every environment — vacuum included.
        applyDecompression(player, level, reading, diagnosis);
        applyBarotrauma(player, level, reading, diagnosis);
        // Radiation too (design/radiation.md §4, rule 18): a flare threatens an unsheltered,
        // EVA'd player hardest of all, and must not be silently exempted by an early return
        // that follows it.
        applyRadiation(player, level, eyePos, diagnosis);
        // Real hydrofluoric-acid contact dose (design/halogens.md §22-23, Part C2) — a body burden
        // in the blood, exactly like radiation, so it runs unconditionally here too rather than
        // being silently skipped by the vacuum/room early return just below (rule 18).
        applyChemicalBurn(player, level, diagnosis);

        if (reading != null && reading.openToSpace() && !atmosphere.outsideIsVacuum()) {
            return; // test-world exterior counts as fresh Earth air
        }

        SuitLifeSupport.Status suit = SuitLifeSupport.tick(player, reading, 0.05);
        recordSuitAilments(suit, diagnosis);
        tickReserve(player, level, reading, suit, diagnosis);
        applyThermalExposure(player, level, reading, diagnosis);
        applyHypothermia(player, level, reading, diagnosis);

        if (reading == null) {
            // No enclosure at all: hard vacuum — the suit is the only thing between
            // the player and it.
            if (suitIsSupplying(suit)) {
                stepCoDose(player, 0.0);
                applyHelmetCo2(player, level, suit);
                return;
            }
            // No damage here, deliberately. Vacuum used to kill on the spot; it now
            // drains the reserve, which grades the harm over the ~11 seconds of useful
            // consciousness a person actually gets (design/breathing.md §1). Saying
            // "hypoxia" and doing nothing else is correct: tickReserve already ran, and
            // it is the single authority on what lack of oxygen costs you.
            diagnosis.put(Ailment.HYPOXIA, Ailment.Severity.CRITICAL);
            diagnoseSuit(player, diagnosis);
            return;
        }

        // Sealed or not, you breathe what the space actually holds: a breached
        // pocket's methane in an unsealable tunnel maze is exactly as real as
        // cabin air — and near-vacuum spaces suffocate via their own ppO2.
        RoomState room = reading.state();
        applyMoldSpores(player, room, diagnosis);
        warmTheRoom(player, level, eyePos);

        // When the air can no longer sustain consciousness, the tank takes over
        // before ambient effects apply (its regulator supplies full-pressure O2).
        if (room.partialPressureKPa(Gas.OXYGEN) < 10.0 && suitIsSupplying(suit)) {
            stepCoDose(player, 0.0); // clean bottled O2: the dose clears
            applyHelmetCo2(player, level, suit);
            return;
        }
        breatheFor(player, room, 0.05);
        applyToxicity(player, level, room, diagnosis);
        diagnoseNoise(player, level, eyePos, diagnosis);

        if (room.partialPressureKPa(Gas.OXYGEN) < 10.0) {
            // Unbreathable air and the suit did not take over: say which part of the
            // kit is the reason, because "hypoxia" alone points at the room.
            diagnoseSuit(player, diagnosis);
        }
        O2Status o2 = O2Status.classify(room.partialPressureKPa(Gas.OXYGEN));
        Co2Status co2 = Co2Status.classify(room.partialPressureKPa(Gas.CARBON_DIOXIDE));
        recordBreathingAilments(o2, co2, diagnosis);
        if (o2 == O2Status.NORMAL && co2 == Co2Status.NORMAL) {
            return;
        }
        applyOncePerSecond(player, () -> {
            applyO2Effects(player, level, o2);
            applyCo2Effects(player, level, co2);
        });
    }

    /**
     * Temperature reaching the wearer, because the thermal layer is not there to stop
     * it.
     *
     * <p>This is what the thermal layer subsystem is <em>for</em>, and until now it
     * was repairable with no consequence either way. Vacuum counts as severe: with no
     * medium to conduct to, a body radiates heat away and nothing replaces it, which
     * is the failure mode people find surprising because vacuum "has no temperature".
     *
     * <p>A working layer removes the effect outright rather than reducing it. That is
     * a simplification the full thermal tier will replace with a real heat balance,
     * but it is honest about what it models rather than pretending to a fidelity it
     * does not have.
     */
    private static void applyThermalExposure(ServerPlayer player, ServerLevel level,
                                             Atmosphere.@Nullable RoomReading reading,
                                             Map<Ailment, Ailment.Severity> diagnosis) {
        if (SuitCondition.isWorking(SuitLoadout.condition(player), SuitSubsystem.THERMAL_LAYER)) {
            return;
        }
        double ambientCelsius = SuitLifeSupport.ambientCelsius(reading);
        // Cold is no longer this path's business: {@link Hypothermia} owns it, through a core
        // temperature that falls over minutes rather than a threshold that bites at once.
        // Leaving both in would give one hazard two authorities and two different answers.
        if (ambientCelsius < ThermalProtection.HOT_STRESS_C) {
            return;
        }
        ThermalProtection.Exposure exposure = ThermalProtection.classify(ambientCelsius);
        if (exposure == ThermalProtection.Exposure.NONE) {
            return;
        }
        diagnosis.put(Ailment.THERMAL_STRESS, exposure == ThermalProtection.Exposure.SEVERE
                ? Ailment.Severity.CRITICAL : Ailment.Severity.MILD);
        applyOncePerSecond(player, () -> {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, true, false));
            if (exposure == ThermalProtection.Exposure.SEVERE) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false));
                hurt(player, level, ModDamageTypes.THERMAL, 1.0f);
            }
        });
    }

    /**
     * The body's own heat balance, and what it costs when it loses.
     *
     * <p>Runs whether or not the player is breathing room air: a sealed suit changes where
     * your oxygen comes from, not whether you are cooling. It also runs when there is no room
     * at all — vacuum is the coldest place in the game and the one a player is most likely to
     * be standing in.
     *
     * <p><strong>The harm is graded and slow on purpose.</strong> Mild is a nuisance you can
     * walk out of; severe is the one that kills. Between them are tens of minutes with the
     * reading on screen the whole time, which is what rule 7 asks of a hazard and what the
     * ambient-threshold path it replaces could not offer.
     */
    private static void applyHypothermia(ServerPlayer player, ServerLevel level,
                                         Atmosphere.@Nullable RoomReading reading,
                                         Map<Ailment, Ailment.Severity> diagnosis) {
        boolean layerIntact = SuitCondition.isWorking(
                SuitLoadout.condition(player), SuitSubsystem.THERMAL_LAYER);
        double ambientK = SuitLifeSupport.ambientCelsius(reading) + 273.15;

        float core = (float) Hypothermia.step(player.getData(ModAttachments.CORE_TEMPERATURE_K),
                ambientK, layerIntact, exertionOf(player), 0.05);
        player.setData(ModAttachments.CORE_TEMPERATURE_K.get(), core);

        Hypothermia.Severity severity = Hypothermia.classify(core);
        if (!severity.isHarmful()) {
            return;
        }
        diagnosis.put(Ailment.HYPOTHERMIA, switch (severity) {
            case MILD -> Ailment.Severity.MILD;
            case MODERATE -> Ailment.Severity.SEVERE;
            default -> Ailment.Severity.CRITICAL;
        });
        applyOncePerSecond(player, () -> {
            // Clumsiness first, then the loss of judgement that makes real hypothermia
            // dangerous: by the moderate stage people stop helping themselves.
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60,
                    severity == Hypothermia.Severity.MILD ? 0 : 1, true, false));
            if (severity != Hypothermia.Severity.MILD) {
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 0,
                        true, false));
            }
            if (severity == Hypothermia.Severity.SEVERE) {
                hurt(player, level, ModDamageTypes.THERMAL, 1.0f);
            }
        });
    }

    /**
     * Says why the suit is not helping, when it is not.
     *
     * <p>Only raised while the player is actually in air they cannot breathe, so it
     * never nags someone standing safely in a habitat with their kit in a chest.
     */
    private static void diagnoseSuit(ServerPlayer player, Map<Ailment, Ailment.Severity> diagnosis) {
        boolean suitWorn = !SuitLoadout.suit(player).isEmpty();
        boolean tankFitted = OxygenTanks.hasFilledTank(player);

        if (!suitWorn || !tankFitted) {
            // Carrying the kit is not wearing it, and that distinction has killed
            // players who were looking straight at a full tank in their inventory.
            diagnosis.put(Ailment.EQUIPMENT_NOT_FITTED, Ailment.Severity.SEVERE);
            return;
        }
        if (!SuitCondition.canHoldPressure(SuitLoadout.condition(player))) {
            diagnosis.put(Ailment.SUIT_NOT_SEALING, Ailment.Severity.SEVERE);
        }
    }

    /**
     * Runs the body's own oxygen store, which is what the player actually dies of.
     *
     * <p>Deliberately separate from the room and suit models: those decide what you
     * are breathing, this decides how long you last while breathing it. Keeping them
     * apart is what lets a full tank on a broken mount show a full supply row and a
     * draining reserve — the exact confusion that was reported as a bug.
     *
     * <p>Runs at real physiological rates rather than the gameplay time compression,
     * for the same reason helmet CO2 does: compressing seconds that are already only
     * seconds leaves nothing to react inside.
     */
    private static void tickReserve(ServerPlayer player, ServerLevel level,
                                    Atmosphere.@Nullable RoomReading reading,
                                    SuitLifeSupport.Status suit,
                                    Map<Ailment, Ailment.Severity> diagnosis) {
        double inspired = Consciousness.inspiredPpO2(
                suitIsSupplying(suit), reading != null,
                reading == null ? 0 : reading.state().partialPressureKPa(Gas.OXYGEN));

        float reserve = (float) Consciousness.step(
                player.getData(ModAttachments.O2_RESERVE), inspired, exertionOf(player), 0.05);
        player.setData(ModAttachments.O2_RESERVE.get(), reserve);

        applyConsciousness(player, level, reserve, diagnosis);
    }

    /**
     * How hard the player is working, as the multiplier the physiology takes.
     *
     * <p>One definition for every consumer — lung reserve, room air and the helmet's CO2
     * all have to agree on whether you are exerting yourself, or the same sprint costs air
     * by one reckoning and not by another. This was previously the literal {@code 3.0 : 1.0}
     * in one place and {@link HelmetAtmosphere}'s constants in another, which is two copies
     * of a rule waiting to drift apart.
     */
    /**
     * One breathing step for a player on the room's own air, at their current exertion.
     *
     * <p>Its own method so a scenario can drive the real path. Exertion counts here and it
     * did not used to: the helmet has always burned more air under load, but a player
     * breathing room air got one fixed rate whether asleep or sprinting uphill with ore.
     * That made effort free in exactly the part of the game where the first log tells you
     * to ration it — and a test that called {@code Breathing} directly would have proved
     * the physiology scales while saying nothing about whether the game ever passes the
     * factor, which is how the gap survived in the first place.
     *
     * @return moles of oxygen consumed
     */
    public static double breatheFor(Player player, RoomState room, double dtSeconds) {
        return Breathing.breathe(room, dtSeconds,
                Config.METABOLISM_SCALE.get() * exertionOf(player));
    }

    /**
     * The heat a body puts into the room it is standing in.
     *
     * <p>Placed here, beside the breathing, on purpose: <strong>the oxygen a player burns
     * and the warmth they give off are two consequences of one metabolic rate</strong>, and
     * driving them from two separate numbers is how a game ends up with someone gasping for
     * air in a room their presence is not warming. Same {@code exertionOf} both times.
     *
     * <p>Deliberately <em>before</em> the branch where a sealed suit takes over the
     * breathing. A suit changes where your oxygen comes from; it does not stop you being a
     * hundred watts of warm body in someone's habitat, and a suited player who quietly
     * stopped heating the room would make the counter fail exactly when the room is coldest.
     *
     * <p>And the watts are <em>real</em> watts, not scaled by {@code METABOLISM_SCALE}. That
     * multiplier compresses the oxygen timeline so life support matters within a session; the
     * thermal balance is already on a playable timescale of its own, and running the heat at
     * sixty times life size would let one person hold a habitat against open space.
     */
    private static void warmTheRoom(Player player, ServerLevel level, BlockPos eyePos) {
        Atmosphere.get(level).addHeatJoules(eyePos,
                HeatBalance.metabolicWatts(exertionOf(player)) * 0.05);
    }

    private static double exertionOf(Player player) {
        return player.isSprinting() || player.swinging
                ? HelmetAtmosphere.WORKING_ACTIVITY
                : HelmetAtmosphere.RESTING_ACTIVITY;
    }

    /**
     * The graded consequences of a falling reserve.
     *
     * <p>Unconsciousness is deliberately not death. Someone dragged into air, or a
     * suit that resumes supplying, still recovers — which makes a collapse a situation
     * to fix rather than a run to lose.
     */
    private static void applyConsciousness(ServerPlayer player, ServerLevel level,
                                           float reserve,
                                           Map<Ailment, Ailment.Severity> diagnosis) {
        Consciousness.State state = Consciousness.classify(reserve);
        switch (state) {
            case NORMAL -> { }
            case HYPOXIC -> diagnosis.put(Ailment.HYPOXIA, Ailment.Severity.MILD);
            case GREYOUT -> {
                diagnosis.put(Ailment.HYPOXIA, Ailment.Severity.SEVERE);
                applyOncePerSecond(player, () -> {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, true, false));
                    player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 1, true, false));
                });
            }
            case UNCONSCIOUS -> {
                diagnosis.put(Ailment.HYPOXIA, Ailment.Severity.CRITICAL);
                applyOncePerSecond(player, () -> {
                    // Collapsed: blind, immobile, and losing ground - but alive, and
                    // recoverable by anything that puts oxygen back.
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, true, false));
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 6, true, false));
                    hurt(player, level, ModDamageTypes.SUFFOCATION, 1.0f);
                });
            }
        }
    }

    /** True while the suit is sealed and still has gas to give. */
    private static boolean suitIsSupplying(SuitLifeSupport.Status suit) {
        return suit == SuitLifeSupport.Status.SEALED
                || suit == SuitLifeSupport.Status.SEALED_HYPERCAPNIC;
    }

    /**
     * The suit's own state, told through the monitor the player already reads. A
     * helmet filling with CO2 is not the room's fault, and the diagnosis has to say
     * so — otherwise the player vents a perfectly good cabin trying to fix it.
     */
    private static void recordSuitAilments(SuitLifeSupport.Status suit,
                                           Map<Ailment, Ailment.Severity> diagnosis) {
        if (suit == SuitLifeSupport.Status.SEALED_HYPERCAPNIC) {
            diagnosis.put(Ailment.HYPERCAPNIA, Ailment.Severity.SEVERE);
        }
    }

    /**
     * Rebreathing your own exhaled air. This is the Apollo 13 failure: the tank is
     * still delivering oxygen, so nothing feels wrong until the CO2 does its work.
     */
    private static void applyHelmetCo2(ServerPlayer player, ServerLevel level,
                                       SuitLifeSupport.Status suit) {
        if (suit != SuitLifeSupport.Status.SEALED_HYPERCAPNIC) {
            return;
        }
        applyOncePerSecond(player, () -> {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 1, true, false));
            hurt(player, level, ModDamageTypes.HYPERCAPNIA, 1.0f);
        });
    }

    /**
     * Nitrogen loading and the bends. Tissue equilibrates toward whatever N2 the
     * player is actually breathing — pressurized cabin air loads it, a pure-O2 tank
     * or pre-breathe line washes it out — and a fast pressure drop while loaded
     * bubbles it back out.
     */
    /**
     * Injury when the surroundings fall faster than the lungs can vent.
     *
     * <p>The hazard that makes an airlock <em>necessary</em> rather than convenient: while a
     * door onto vacuum cost nothing, the whole port → pump → tank → controller chain was
     * optional decoration. A cycled airlock bleeds an atmosphere off over tens of seconds,
     * far under the airway's rate, so the safe route stays safe — that is the lesson, and
     * {@code BarotraumaTest} asserts it before anything else.
     *
     * <p>A sealed suit is the mitigation, because the wearer keeps the suit's own pressure
     * and the differential lands on the suit instead of on them.
     */
    /**
     * Real gamma dose: every flare and every RTG within reach (design/radiation.md §1/§4).
     *
     * <p>The expensive part — finding nearby sources and walking a line of sight to each — is
     * throttled to once every {@link Atmosphere#TICK_INTERVAL} ticks, same cadence most other
     * physiology sub-effects already use; the ailment diagnosis is re-read from the stored dose
     * every call so it never flickers between those ticks. Mathematically equivalent to a finer
     * step: the same total dose accumulates either way, just updated in coarser increments.
     */
    private static void applyRadiation(ServerPlayer player, ServerLevel level, BlockPos eyePos,
                                       Map<Ailment, Ailment.Severity> diagnosis) {
        if (player.tickCount % Atmosphere.TICK_INTERVAL == 0) {
            double rateSvPerH = play.xponer.astronima.atmosphere.RadiationSources
                    .totalGammaSvPerH(level, eyePos, player.getEyePosition());
            double dtSeconds = Atmosphere.TICK_INTERVAL / 20.0;
            double old = player.getData(ModAttachments.RADIATION_DOSE);
            double next = play.xponer.astronima.sim.rad.RadiationDose.stepDose(old, rateSvPerH, dtSeconds);
            if (Math.abs(next - old) > 0.0001 || (next == 0.0 && old > 0.0)) {
                player.setData(ModAttachments.RADIATION_DOSE.get(), (float) next);
            }
        }
        double doseSv = player.getData(ModAttachments.RADIATION_DOSE);
        play.xponer.astronima.sim.rad.RadiationDose.Severity severity =
                play.xponer.astronima.sim.rad.RadiationDose.Severity.classify(doseSv);
        recordRadiationAilment(severity, diagnosis);
        if (severity == play.xponer.astronima.sim.rad.RadiationDose.Severity.NONE) {
            return;
        }
        applyOncePerSecond(player, () -> {
            switch (severity) {
                case NONE -> {}
                case MILD -> player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false));
                case SEVERE -> {
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, true, false));
                    hurt(player, level, ModDamageTypes.RADIATION, 1.0f);
                }
                case CRITICAL -> {
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1, true, false));
                    hurt(player, level, ModDamageTypes.RADIATION, 2.5f);
                }
            }
        });
    }

    private static void recordRadiationAilment(play.xponer.astronima.sim.rad.RadiationDose.Severity severity,
                                                Map<Ailment, Ailment.Severity> diagnosis) {
        switch (severity) {
            case NONE -> {}
            case MILD -> diagnosis.put(Ailment.RADIATION_SICKNESS, Ailment.Severity.MILD);
            case SEVERE -> diagnosis.put(Ailment.RADIATION_SICKNESS, Ailment.Severity.SEVERE);
            case CRITICAL -> diagnosis.put(Ailment.RADIATION_SICKNESS, Ailment.Severity.CRITICAL);
        }
    }

    /** True while the player's own accumulated gamma dose sits at SEVERE or worse — the second
     * cause {@code design/chronic.md} §5 names for marrow damage, alongside a severe infection. */
    private static boolean radiationSevere(ServerPlayer player) {
        double doseSv = player.getData(ModAttachments.RADIATION_DOSE);
        play.xponer.astronima.sim.rad.RadiationDose.Severity severity =
                play.xponer.astronima.sim.rad.RadiationDose.Severity.classify(doseSv);
        return severity == play.xponer.astronima.sim.rad.RadiationDose.Severity.SEVERE
                || severity == play.xponer.astronima.sim.rad.RadiationDose.Severity.CRITICAL;
    }

    /**
     * Real hydrofluoric-acid contact dose (design/halogens.md §22-23, Part C2) — accumulated the
     * instant {@code HydrofluoricAcidItem#use} runs, decaying here the same slow way
     * {@code RadiationDose} recovers gamma dose. Deliberately does not consult
     * {@link SuitCondition}, {@link SuitLoadout}, or any barrier at all: the real fact this hazard
     * exists to model is that ordinary suit sealing does not stop it — "contact = deep-tissue
     * damage ignoring armor" (PLAN.md v0.7).
     */
    private static void applyChemicalBurn(ServerPlayer player, ServerLevel level,
                                          Map<Ailment, Ailment.Severity> diagnosis) {
        double doseGrams = player.getData(ModAttachments.CHEMICAL_BURN_DOSE);
        if (player.tickCount % Atmosphere.TICK_INTERVAL == 0) {
            double dtSeconds = Atmosphere.TICK_INTERVAL / 20.0;
            double next = play.xponer.astronima.sim.physio.ChemicalBurn.recover(doseGrams, dtSeconds);
            if (Math.abs(next - doseGrams) > 0.0001 || (next == 0.0 && doseGrams > 0.0)) {
                player.setData(ModAttachments.CHEMICAL_BURN_DOSE.get(), (float) next);
                doseGrams = next;
            }
        }
        play.xponer.astronima.sim.physio.ChemicalBurn.Severity severity =
                play.xponer.astronima.sim.physio.ChemicalBurn.Severity.classify(doseGrams);
        switch (severity) {
            case NONE -> {}
            case MILD -> diagnosis.put(Ailment.CHEMICAL_BURN, Ailment.Severity.MILD);
            case SEVERE -> diagnosis.put(Ailment.CHEMICAL_BURN, Ailment.Severity.SEVERE);
            case CRITICAL -> diagnosis.put(Ailment.CHEMICAL_BURN, Ailment.Severity.CRITICAL);
        }
        if (severity == play.xponer.astronima.sim.physio.ChemicalBurn.Severity.NONE) {
            return;
        }
        applyOncePerSecond(player, () -> {
            switch (severity) {
                case NONE, MILD -> {}
                case SEVERE -> {
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, true, false));
                    hurt(player, level, ModDamageTypes.CHEMICAL_BURN, 1.0f);
                }
                case CRITICAL -> {
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1, true, false));
                    player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 0, true, false));
                    hurt(player, level, ModDamageTypes.CHEMICAL_BURN, 2.5f);
                }
            }
        });
    }

    private static void applyBarotrauma(ServerPlayer player, ServerLevel level,
                                        Atmosphere.@Nullable RoomReading reading,
                                        Map<Ailment, Ailment.Severity> diagnosis) {
        double ambient = reading != null ? reading.state().pressureKPa() : 0.0;
        double ambientPpO2 = reading != null ? reading.state().partialPressureKPa(Gas.OXYGEN) : 0.0;
        float previous = player.getData(ModAttachments.LAST_AMBIENT_KPA);
        player.setData(ModAttachments.LAST_AMBIENT_KPA.get(), (float) ambient);

        float damage = barotraumaDamage(player, previous, ambient, ambientPpO2, 0.05);
        if (damage <= 0) {
            return;
        }
        // Critical past halfway up the curve, derived rather than a bare number, so
        // retuning the damage cannot silently leave the monitor calling everything severe.
        float halfway = Barotrauma.MINIMUM_DAMAGE + Barotrauma.SEVERITY_DAMAGE / 2f;
        diagnosis.put(Ailment.BAROTRAUMA, damage >= halfway
                ? Ailment.Severity.CRITICAL : Ailment.Severity.SEVERE);
        // Immediate rather than once a second: this is a single event, not an exposure,
        // and a player who opened the wrong door should feel it on that tick.
        player.hurtServer(level, level.damageSources().source(ModDamageTypes.BAROTRAUMA), damage);
    }

    /**
     * What this pressure change would do to this player, 0 when it does nothing.
     *
     * <p>Its own method so a scenario can ask the real question — <em>would the game hurt
     * this player</em> — instead of handing the curve two numbers, which would prove the
     * maths and say nothing about whether the curve is even consulted (rule 13).
     */
    public static float barotraumaDamage(Player player, double previousKPa, double ambientKPa,
                                         double ambientPpO2Kpa, double dtSeconds) {
        boolean sealed = SuitCondition.isCurrentlySealed(ambientPpO2Kpa,
                SuitCondition.canHoldPressure(SuitLoadout.condition(player)));
        return Barotrauma.damage(sealed, previousKPa, ambientKPa, dtSeconds);
    }


    private static void applyDecompression(ServerPlayer player, ServerLevel level,
                                           Atmosphere.@Nullable RoomReading reading,
                                           Map<Ailment, Ailment.Severity> diagnosis) {
        double ambientPressure = reading != null ? reading.state().pressureKPa() : 0.0;
        // Breathing bottled oxygen means zero inspired N2, whatever the room holds.
        double inspiredN2 = OxygenTanks.hasFilledTank(player) ? 0.0
                : (reading != null ? reading.state().partialPressureKPa(Gas.NITROGEN) : 0.0);

        float tissue = player.getData(ModAttachments.TISSUE_N2);
        double next = DecompressionModel.step(tissue, inspiredN2, 0.05, Config.METABOLISM_SCALE.get());
        if (Math.abs(next - tissue) > 0.01) {
            player.setData(ModAttachments.TISSUE_N2.get(), (float) next);
        }

        // A suit tank holds the wearer at its own regulator pressure, so an EVA on
        // tank counts as a pressurized environment for bubble formation.
        double effectivePressure = OxygenTanks.hasFilledTank(player)
                ? Math.max(ambientPressure, 40.0) : ambientPressure;
        DecompressionModel.Severity severity = DecompressionModel.severity(next, effectivePressure);
        if (severity == DecompressionModel.Severity.SAFE) {
            return;
        }
        diagnosis.put(Ailment.DECOMPRESSION, severity == DecompressionModel.Severity.SEVERE
                ? Ailment.Severity.CRITICAL : Ailment.Severity.SEVERE);
        applyOncePerSecond(player, () -> {
            switch (severity) {
                case SAFE -> {}
                case PAIN -> {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, true, false));
                    player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 0, true, false));
                }
                case SEVERE -> {
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2, true, false));
                    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false));
                    hurt(player, level, ModDamageTypes.DECOMPRESSION, 1.5f);
                }
            }
        });
    }

    /** How many mould blocks around you count as air you cannot be in at all. */
    private static final int SATURATING_MOULD = 8;

    /** Seconds one contamination step covers - it runs every hundredth tick. */
    private static final double CONTAMINATION_STEP_SECONDS = 5.0;

    /**
     * Spores breathed, loads decayed, and a dose delivered if one has built up.
     *
     * <p><strong>This used to be a dice roll.</strong> Standing near mould rolled a chance to catch
     * something, which is the version {@code design/transmission.md} exists to refuse: it teaches
     * nothing, cannot be avoided deliberately, and makes an illness bad luck rather than a
     * consequence. Now the spores accumulate on the player at a rate set by how much mould is
     * actually there, and the infection happens when the accumulated load crosses a dose the
     * biomonitor has been showing them climb.
     *
     * <p>The mild stamina hit stays, because mould is a maintenance failure and not a death
     * sentence, and because it is the symptom that makes a player look around before the
     * contamination readout means anything to them.
     */
    private static void applyMoldSpores(ServerPlayer player, RoomState room,
                                        Map<Ailment, Ailment.Severity> diagnosis) {
        if (player.tickCount % 100 != 0) {
            return;
        }
        int mould = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos feet = player.blockPosition();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    cursor.setWithOffset(feet, dx, dy, dz);
                    if (player.level().getBlockState(cursor).is(ModBlocks.MOLD.get())) {
                        mould++;
                    }
                }
            }
        }
        boolean growing = Humidity.moldFavourable(room);
        // Damp air keeps the spores up; a dry room lets them settle even where mould is growing.
        double spores = growing
                ? Math.min(1.0, mould / (double) SATURATING_MOULD)
                : Math.min(1.0, mould / (double) SATURATING_MOULD) * 0.25;

        // Runs even at zero, because this is also where a load weathers off. Outside, a mistake
        // cleans itself while you walk back; in a warm habitat it is still on you tomorrow.
        boolean outside = room == null || room.pressureKPa() < 10;
        play.xponer.astronima.physio.Contaminations.tick(player, CONTAMINATION_STEP_SECONDS,
                play.xponer.astronima.sim.pathogen.Strain.Source.COMMENSAL, spores, outside);

        // And the surfaces the player is standing among. Weathered on the same cadence and by the
        // same rule, because a bench that outlasts the hands that dirtied it would be the one
        // place the player could not reason about.
        play.xponer.astronima.physio.Surfaces.tick(
                (net.minecraft.server.level.ServerLevel) player.level(),
                player.level().getChunk(player.blockPosition()), CONTAMINATION_STEP_SECONDS,
                outside ? play.xponer.astronima.sim.pathogen.Contamination.VACUUM_HALF_LIFE
                        : play.xponer.astronima.sim.pathogen.Contamination.HABITAT_HALF_LIFE);

        if (play.xponer.astronima.physio.Contaminations.deliversDose(player)) {
            // A neglected habitat grows its own organisms. Closed environments plus radiation
            // measurably raise virulence - it has been documented on the ISS - and this is the
            // source that arrives because of how you live rather than because of where you went.
            play.xponer.astronima.physio.Infections.infect(player,
                    play.xponer.astronima.physio.Contaminations.of(player).source());
        }
        if (spores > 0 && growing) {
            diagnosis.put(Ailment.MOLD_SPORES, Ailment.Severity.MILD);
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 120, 0, true, false));
            player.causeFoodExhaustion(0.5f);
        }
    }

    /** CO dose bookkeeping plus acute effects of the toxic species (sim.tox). */
    private static void applyToxicity(ServerPlayer player, ServerLevel level, RoomState room,
                                      Map<Ailment, Ailment.Severity> diagnosis) {
        double dose = stepCoDose(player, room.partialPressureKPa(Gas.CARBON_MONOXIDE));
        GasToxicity.CoStatus coStatus = GasToxicity.CoStatus.classify(dose);
        GasToxicity.Severity worstAcute = GasToxicity.Severity.NONE;
        Gas worstGas = null;
        for (Gas gas : Gas.values()) {
            GasToxicity.Severity severity = GasToxicity.acute(gas, room.partialPressureKPa(gas));
            if (severity.ordinal() > worstAcute.ordinal()) {
                worstAcute = severity;
                worstGas = gas;
            }
        }

        if (worstAcute == GasToxicity.Severity.TRACE_WARNING && player.tickCount % 600 == 0) {
            player.sendSystemMessage(Component.translatable("astronima.gas.smell." + worstGas.symbol().toLowerCase())
                    .withStyle(ChatFormatting.YELLOW));
        }
        recordToxicAilments(coStatus, worstAcute, diagnosis);
        if (coStatus == GasToxicity.CoStatus.NONE && worstAcute.ordinal() < GasToxicity.Severity.IRRITATION.ordinal()) {
            return;
        }
        GasToxicity.Severity acute = worstAcute;
        applyOncePerSecond(player, () -> {
            switch (coStatus) {
                case NONE -> {}
                case MILD -> player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false));
                case SEVERE -> {
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, true, false));
                    hurt(player, level, ModDamageTypes.CARBON_MONOXIDE, 1.0f);
                }
                case CRITICAL -> {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false));
                    hurt(player, level, ModDamageTypes.CARBON_MONOXIDE, 2.0f);
                }
            }
            switch (acute) {
                case NONE, TRACE_WARNING -> {}
                case IRRITATION -> player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false));
                case DAMAGE -> {
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, true, false));
                    hurt(player, level, ModDamageTypes.TOXIC_GAS, 1.0f);
                }
                case SEVERE -> {
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, true, false));
                    hurt(player, level, ModDamageTypes.TOXIC_GAS, 2.5f);
                }
            }
        });
    }

    private static void recordToxicAilments(GasToxicity.CoStatus co, GasToxicity.Severity acute,
                                            Map<Ailment, Ailment.Severity> diagnosis) {
        switch (co) {
            case NONE -> {}
            case MILD -> diagnosis.put(Ailment.CARBON_MONOXIDE, Ailment.Severity.MILD);
            case SEVERE -> diagnosis.put(Ailment.CARBON_MONOXIDE, Ailment.Severity.SEVERE);
            case CRITICAL -> diagnosis.put(Ailment.CARBON_MONOXIDE, Ailment.Severity.CRITICAL);
        }
        switch (acute) {
            case NONE, TRACE_WARNING -> {}
            case IRRITATION -> diagnosis.put(Ailment.TOXIC_GAS, Ailment.Severity.MILD);
            case DAMAGE -> diagnosis.put(Ailment.TOXIC_GAS, Ailment.Severity.SEVERE);
            case SEVERE -> diagnosis.put(Ailment.TOXIC_GAS, Ailment.Severity.CRITICAL);
        }
    }

    private static void recordBreathingAilments(O2Status o2, Co2Status co2,
                                                Map<Ailment, Ailment.Severity> diagnosis) {
        switch (o2) {
            case NORMAL -> {}
            case MILD_HYPOXIA -> diagnosis.put(Ailment.HYPOXIA, Ailment.Severity.MILD);
            case SEVERE_HYPOXIA -> diagnosis.put(Ailment.HYPOXIA, Ailment.Severity.SEVERE);
            case SUFFOCATING -> diagnosis.put(Ailment.HYPOXIA, Ailment.Severity.CRITICAL);
            case OXYGEN_TOXICITY -> diagnosis.put(Ailment.OXYGEN_TOXICITY, Ailment.Severity.MILD);
        }
        switch (co2) {
            case NORMAL -> {}
            case ELEVATED -> diagnosis.put(Ailment.HYPERCAPNIA, Ailment.Severity.MILD);
            case HIGH -> diagnosis.put(Ailment.HYPERCAPNIA, Ailment.Severity.SEVERE);
            case TOXIC -> diagnosis.put(Ailment.HYPERCAPNIA, Ailment.Severity.CRITICAL);
        }
    }

    /** Noise does not damage, but it blocks rest — worth naming so it isn't a mystery. */
    private static void diagnoseNoise(ServerPlayer player, ServerLevel level, BlockPos pos,
                                      Map<Ailment, Ailment.Severity> diagnosis) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        if (Acoustics.blocksRest(Atmosphere.get(level).noiseDbAt(pos))) {
            diagnosis.put(Ailment.NOISE_FATIGUE, Ailment.Severity.MILD);
        }
    }

    /** Advances the synced CO dose; writes only on meaningful change to limit sync traffic. */
    private static double stepCoDose(ServerPlayer player, double ppCoKPa) {
        float old = player.getData(ModAttachments.CO_DOSE);
        double next = GasToxicity.stepCoDose(old, ppCoKPa, 0.05);
        if (Math.abs(next - old) > 0.001 || (next == 0.0 && old > 0.0)) {
            player.setData(ModAttachments.CO_DOSE.get(), (float) next);
        }
        return next;
    }

    /**
     * Effects of the room's oxygen level that the reserve does <em>not</em> already own.
     *
     * <p>Too little oxygen is handled entirely by {@link #applyConsciousness}: the room
     * decides what you are breathing, the reserve decides what that does to you. Doing
     * both was double jeopardy, and it is the reason the field report said mining was
     * impossible — a thin room slowed you and hurt you on its own schedule while the
     * reserve was still nearly full.
     *
     * <p>Too <em>much</em> oxygen stays here, because it is a different mechanism
     * entirely: CNS oxygen toxicity is caused by high ppO2, not by an empty reserve, and
     * nothing in the reserve model would ever produce it.
     */
    private static void applyO2Effects(ServerPlayer player, ServerLevel level, O2Status status) {
        if (status == O2Status.OXYGEN_TOXICITY) {
            // Poison models CNS toxicity without inventing a damage type for it.
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, true, false));
        }
    }

    private static void applyCo2Effects(ServerPlayer player, ServerLevel level, Co2Status status) {
        switch (status) {
            case NORMAL -> {}
            case ELEVATED ->
                    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false));
            case HIGH -> {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, true, false));
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 0, true, false));
            }
            case TOXIC -> {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false));
                hurt(player, level, ModDamageTypes.HYPERCAPNIA, 1.0f);
            }
        }
    }

    private static void applyOncePerSecond(ServerPlayer player, Runnable action) {
        if (player.tickCount % 20 == 0) {
            action.run();
        }
    }

    private static void hurt(ServerPlayer player, ServerLevel level, ResourceKey<DamageType> type, float amount) {
        player.hurtServer(level, level.damageSources().source(type), amount);
    }

    /** Streams the analyzer HUD snapshot to any player holding the instrument. */
    @SubscribeEvent
    private static void onHudSync(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || player.tickCount % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }
        if (!player.getMainHandItem().is(ModItems.GAS_ANALYZER.get())
                && !player.getOffhandItem().is(ModItems.GAS_ANALYZER.get())) {
            return;
        }

        Atmosphere atmosphere = Atmosphere.get(level);
        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        Atmosphere.RoomReading reading = atmosphere.readingNear(eyePos);
        float tankFraction = OxygenTanks.fittedTankFraction(player);
        boolean breathesForReal = !player.isCreative() && !player.isSpectator();

        AtmosphereStatusPayload payload;
        if (reading != null && !reading.openToSpace()) {
            // Any enclosed volume reports real numbers, pressurizable or not.
            RoomState room = reading.state();
            float[] partials = new float[Gas.values().length];
            for (Gas gas : Gas.values()) {
                partials[gas.ordinal()] = (float) room.partialPressureKPa(gas);
            }
            float ppO2 = partials[Gas.OXYGEN.ordinal()];
            Atmosphere.ThermalSnapshot thermal = atmosphere.thermalAt(eyePos);
            boolean onTank = breathesForReal && ppO2 < 10.0f && tankFraction > 0;
            payload = new AtmosphereStatusPayload(
                    reading.sealed() ? AtmosphereStatusPayload.ENV_SEALED
                            : AtmosphereStatusPayload.ENV_UNSEALABLE,
                    partials, (float) room.pressureKPa(), (float) room.temperatureK(),
                    room.volumeBlocks(), (float) Humidity.relativeHumidity(room),
                    (float) atmosphere.noiseDbAt(eyePos),
                    (byte) Flammability.assess(room).ordinal(), tankFraction, onTank,
                    (float) thermal.lossWatts(), (float) thermal.supplyWatts(),
                    (float) thermal.skyFraction(), (float) thermal.insulatedFraction());
        } else if (reading != null && !atmosphere.outsideIsVacuum()) {
            payload = AtmosphereStatusPayload.environmentOnly(
                    AtmosphereStatusPayload.ENV_OPEN_AIR, tankFraction, false);
        } else {
            boolean onTank = breathesForReal && tankFraction > 0;
            payload = AtmosphereStatusPayload.environmentOnly(
                    AtmosphereStatusPayload.ENV_VACUUM, tankFraction, onTank);
        }
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** Streams the coherence survey meter's six-bar reading to any player holding one. */
    @SubscribeEvent
    private static void onCoherenceHudSync(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || player.tickCount % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }
        if (!player.getMainHandItem().is(ModItems.COHERENCE_METER.get())
                && !player.getOffhandItem().is(ModItems.COHERENCE_METER.get())) {
            return;
        }

        BlockPos eyePos = BlockPos.containing(player.getEyePosition());
        var breakdown = CoherenceReading.at(level, eyePos, true);
        PacketDistributor.sendToPlayer(player, play.xponer.astronima.network.CoherenceStatusPayload.of(breakdown));
    }

    /**
     * Streams the astra field's own real, depletion-aware density at a player's real position, to
     * every player in the asteroid dimension, on the same cadence the coherence meter already
     * uses — design/astra-field-live.md §2.2: this is the leaf's whole answer to the
     * field-query-budget question, one point (the player) rather than many, so the client never
     * re-derives {@link AstraFieldGeometry} itself. Unlike {@link #onCoherenceHudSync}, this is
     * not gated on holding an item: the glow is ambient and passive, per that document's own §2.1,
     * so every player gets a reading whether or not they own an instrument yet.
     *
     * <p>design/astra-extraction-loop.md §1: reads through {@link AstraFieldStorage} rather than
     * the plain baseline — once ground can be depleted at all, a glow that kept showing the
     * un-depleted baseline would silently contradict astra-phenomena.md §2.1's own promise
     * ("burnt ground is visibly dark"), which is worse than not having built the glow yet.
     */
    @SubscribeEvent
    private static void onAstraFieldSync(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || level.dimension() != ModDimensions.ASTEROID_LEVEL
                || player.tickCount % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }

        var pos = player.position();
        double r = Math.hypot(pos.x, pos.z);
        double shellCoordinate = AsteroidBody.shellCoordinate(r, pos.y);
        double flareIntensity = SkyEventOverride.resolveFlareIntensity(level.getGameTime(), FLARE_SEED);
        double baseline = AstraFieldGeometry.baselineDensity(shellCoordinate, flareIntensity);
        double density = play.xponer.astronima.astra.AstraFieldStorage.get(level)
                .densityAt(player.getBlockX(), player.getBlockY(), player.getBlockZ(), baseline, level.getGameTime());

        PacketDistributor.sendToPlayer(player, new AstraFieldReadingPayload((float) density));
    }

    /** Must match {@code AsteroidSkyRenderer.FLARE_SEED} exactly — one real flare schedule read
     *  from several places, so a forced test flare moves this field's reading exactly as it moves
     *  the rendered sky and every other consumer of the same schedule. */
    private static final long FLARE_SEED = 20260810L;

    /** No sleeping next to a compressor wall: >60 dB blocks rest (ISS acoustic limits). */
    @SubscribeEvent
    private static void onCanSleep(CanPlayerSleepEvent event) {
        if (event.getProblem() != null || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        double noise = Atmosphere.get(level).noiseDbAt(event.getPos());
        if (Acoustics.blocksRest(noise)) {
            event.setProblem(new Player.BedSleepingProblem(
                    Component.translatable("astronima.noise.too_loud", Math.round(noise))));
        }
    }

    /** Chronic noise also stops natural recovery while awake. */
    @SubscribeEvent
    private static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || player.tickCount % 20 != 0) {
            return;
        }
        double noise = Atmosphere.get(level).noiseDbAt(BlockPos.containing(player.getEyePosition()));
        if (Acoustics.blocksRest(noise)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.getGameTime() % Atmosphere.TICK_INTERVAL == 0) {
            Atmosphere.get(level).tick();
        }
    }

    @SubscribeEvent
    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Atmosphere.get(level).invalidate(event.getPos());
            // An open flame placed into a flammable mixture ignites it — and if the
            // air cannot sustain a flame at all, it simply goes out.
            if (event.getPlacedBlock().is(Blocks.TORCH) || event.getPlacedBlock().is(Blocks.WALL_TORCH)
                    || event.getPlacedBlock().is(Blocks.CAMPFIRE) || event.getPlacedBlock().is(Blocks.FIRE)) {
                if (!Ignition.tryIgnite(level, event.getPos())) {
                    CombustionEvents.snuffIfStarved(level, event.getPos(), event.getPlacedBlock());
                }
            }
        }
    }

    @SubscribeEvent
    private static void onBlockBroken(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Atmosphere.get(level).invalidate(event.getPos());
            // Microbes genuinely survive geologic time frozen, and panspermia is a serious
            // hypothesis. Nothing tells the player anything here - no message, no sound - because
            // the strain hides for minutes and working out which block it was is the game.
            if (event.getState().is(ModBlocks.WATER_ICE.get())) {
                play.xponer.astronima.physio.Infections.exposeToIce(event.getPlayer());
            }
        }
    }

    /** Catches state changes that notify neighbors (pistons, fluid flow, most machines). */
    @SubscribeEvent
    private static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Atmosphere.get(level).invalidate(event.getPos());
        }
    }

    /**
     * Vanilla doors, trapdoors, and gates toggle with the don't-notify flag, so no
     * block event fires for them. Any right-click on a block conservatively marks the
     * surrounding rooms stale; the periodic revalidation then picks up the change.
     */
    @SubscribeEvent
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Atmosphere.get(level).invalidate(event.getPos());
            if (event.getItemStack().is(Items.FLINT_AND_STEEL) && event.getFace() != null) {
                Ignition.tryIgnite(level, event.getPos().relative(event.getFace()));
            }
        }
    }

    private AtmosphereEvents() {}
}
