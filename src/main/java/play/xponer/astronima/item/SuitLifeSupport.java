package play.xponer.astronima.item;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModAttachments;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.suit.HelmetAtmosphere;
import play.xponer.astronima.sim.suit.SuitCondition;
import play.xponer.astronima.sim.suit.SuitEndurance;
import play.xponer.astronima.sim.suit.SuitWear;
import play.xponer.astronima.sim.suit.ThermalProtection;
import play.xponer.astronima.sim.suit.SuitSubsystem;

/**
 * Running the suit as a closed life-support loop.
 *
 * <p>While sealed, the wearer is no longer breathing the room: oxygen comes from the
 * fitted tank and exhaled carbon dioxide stays inside the helmet, where only a fitted
 * cartridge can remove it. That is what makes the scrubber bay a subsystem worth
 * repairing — the tank governs how long you can breathe, the cartridge governs how
 * long you stay conscious.
 *
 * <p>The suit seals itself when the surrounding air will not support you and unseals
 * when it will, which is simply what a person would do with a visor.
 */
public final class SuitLifeSupport {
    /** Outcome of one life-support step, for the caller to act on. */
    public enum Status {
        /** Not sealed; the wearer is breathing whatever is around them. */
        OPEN,
        /** Sealed and supplying gas normally. */
        SEALED,
        /** Sealed, but the helmet is accumulating dangerous carbon dioxide. */
        SEALED_HYPERCAPNIC,
        /** Sealed with no gas left; the suit can no longer keep the wearer alive. */
        SUPPLY_EXHAUSTED
    }

    /**
     * Advances the suit for one tick.
     *
     * @param ambient the air around the wearer, or null in hard vacuum
     */
    public static Status tick(ServerPlayer player, Atmosphere.@Nullable RoomReading ambient,
                              double dtSeconds) {
        double ambientPpO2 = ambient == null ? 0
                : ambient.state().partialPressureKPa(Gas.OXYGEN);
        boolean sealed = SuitCondition.isCurrentlySealed(ambientPpO2,
                SuitCondition.canHoldPressure(SuitLoadout.condition(player)));
        boolean thermallyStressed = isThermallyStressed(ambient);

        if (!sealed) {
            tickWear(player, false, false, thermallyStressed, dtSeconds);
            // Visor open, or a suit that cannot seal: the helmet clears to ambient.
            if (player.getData(ModAttachments.HELMET_CO2) != 0f) {
                player.setData(ModAttachments.HELMET_CO2.get(), (float) HelmetAtmosphere.vented());
            }
            return Status.OPEN;
        }

        ItemStack tank = SuitLoadout.tank(player);
        if (!OxygenTanks.isUsable(tank)) {
            tickWear(player, false, false, thermallyStressed, dtSeconds);
            return Status.SUPPLY_EXHAUSTED;
        }
        tickWear(player, true, isScrubbing(player), thermallyStressed, dtSeconds);

        drawFromTank(player, tank, dtSeconds);
        if (tank.getDamageValue() >= tank.getMaxDamage()) {
            // A spent tank stays fitted as an empty shell: it is refillable, and the
            // wearer should see *why* the supply stopped rather than an empty slot.
            SuitLoadout.fit(player, SuitLoadout.SLOT_TANK,
                    new ItemStack(ModItems.OXYGEN_TANK_EMPTY.get()));
        }
        double ppCo2 = scrubAndAccumulate(player, dtSeconds);
        return HelmetAtmosphere.isDangerous(ppCo2) ? Status.SEALED_HYPERCAPNIC : Status.SEALED;
    }

    /**
     * Spends the suit's remaining life on whatever is actually stressing it.
     *
     * <p>Wear is charged to the stress that physically causes it, not to a clock: a
     * seal is tired by holding pressure, a regulator by passing gas, the thermal layer
     * by fighting a temperature. A suit hanging on a hook does not degrade, and a suit
     * you never seal keeps its seal indefinitely, which is both physically right and
     * the reason planning an EVA is worth doing.
     *
     * <p>Whole seconds only: one nibble of life is minutes of stress, so fractional
     * seconds are banked on the player rather than truncated away to nothing.
     */
    private static void tickWear(ServerPlayer player, boolean sealed, boolean scrubbing,
                                 boolean thermallyStressed, double dtSeconds) {
        ItemStack suit = SuitLoadout.suit(player);
        if (suit.isEmpty()) {
            return;
        }
        float carried = player.getData(ModAttachments.SUIT_STRESS_CARRY) + (float) dtSeconds;
        int seconds = (int) carried;
        player.setData(ModAttachments.SUIT_STRESS_CARRY.get(), carried - seconds);
        if (seconds <= 0) {
            return;
        }

        int startingLife = EvaSuitItem.wearOf(suit);
        long startingStress = EvaSuitItem.stressOf(suit);
        SuitWear.Stressed state = new SuitWear.Stressed(startingLife, startingStress);

        if (sealed) {
            // Holding pressure tires the seal, loads the mount, and passes gas.
            state = stress(state, SuitSubsystem.HELMET_SEAL, seconds);
            state = stress(state, SuitSubsystem.TANK_MOUNT, seconds);
            state = stress(state, SuitSubsystem.REGULATOR, seconds);
        }
        if (scrubbing) {
            state = stress(state, SuitSubsystem.SCRUBBER_BAY, seconds);
        }
        if (thermallyStressed) {
            state = stress(state, SuitSubsystem.THERMAL_LAYER, seconds);
        }
        if (SuitCondition.isWorking(EvaSuitItem.repairedMask(suit), SuitSubsystem.STATUS_DISPLAY)) {
            // Electronics simply age once they are powered.
            state = stress(state, SuitSubsystem.STATUS_DISPLAY, seconds);
        }

        if (state.life() != startingLife) {
            EvaSuitItem.setWear(suit, state.life());
        }
        if (state.stress() != startingStress) {
            EvaSuitItem.setStress(suit, state.stress());
        }
    }

    private static SuitWear.Stressed stress(SuitWear.Stressed state, SuitSubsystem subsystem,
                                            int seconds) {
        return SuitWear.applyStress(state.life(), state.stress(), subsystem, seconds);
    }

    /** True while a fitted cartridge in a working bay is actually removing CO2. */
    private static boolean isScrubbing(ServerPlayer player) {
        ItemStack cartridge = SuitLoadout.cartridge(player);
        return SuitCondition.isWorking(SuitLoadout.condition(player), SuitSubsystem.SCRUBBER_BAY)
                && cartridge.is(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get())
                && cartridge.getDamageValue() < cartridge.getMaxDamage();
    }

    /**
     * Whether the surroundings are working hard enough on the wearer that the thermal
     * layer is earning its keep. Vacuum always counts: with nothing to conduct to,
     * the suit is fighting radiative loss unaided.
     */
    public static boolean isThermallyStressed(Atmosphere.@Nullable RoomReading ambient) {
        return ThermalProtection.isStressful(ambientCelsius(ambient));
    }

    /** Ambient temperature for thermal purposes, with vacuum given its equivalent. */
    public static double ambientCelsius(Atmosphere.@Nullable RoomReading ambient) {
        return ambient == null ? ThermalProtection.vacuumEquivalentCelsius()
                : ambient.state().temperatureK() - 273.15;
    }


    /** Spends tank gas, doubled when the regulator is faulty. */
    private static void drawFromTank(ServerPlayer player, ItemStack tank, double dtSeconds) {
        double unitsPerSecond = SuitEndurance.wearPerSecond(tank.getMaxDamage(),
                SuitEndurance.TANK_SECONDS)
                * SuitCondition.tankDrainMultiplier(SuitLoadout.condition(player));
        spendDurability(player, tank, ModAttachments.SUIT_WEAR_CARRY.get(),
                unitsPerSecond * dtSeconds);
    }

    /**
     * Applies fractional wear to an item without losing the remainder: a tenth of a
     * durability point per tick would otherwise round to nothing and the item would
     * never wear at all.
     */
    private static void spendDurability(ServerPlayer player, ItemStack stack,
                                        AttachmentType<Float> carry,
                                        double units) {
        float carried = player.getData(carry) + (float) units;
        int whole = (int) carried;
        player.setData(carry, carried - whole);
        if (whole > 0) {
            stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + whole));
        }
    }

    /**
     * Adds this step's exhaled CO2 to the helmet and lets a fitted cartridge remove
     * what it can, consuming the cartridge as it works.
     *
     * @return helmet CO2 partial pressure after the step
     */
    private static double scrubAndAccumulate(ServerPlayer player, double dtSeconds) {
        ItemStack cartridge = SuitLoadout.cartridge(player);
        boolean bayWorks = SuitCondition.isWorking(SuitLoadout.condition(player),
                SuitSubsystem.SCRUBBER_BAY);
        boolean usable = bayWorks && cartridge.is(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get())
                && cartridge.getDamageValue() < cartridge.getMaxDamage();

        double scrubberFraction = usable ? 1.0 : 0.0;
        double ppCo2 = HelmetAtmosphere.step(player.getData(ModAttachments.HELMET_CO2),
                scrubberFraction, activityOf(player), 305.0, dtSeconds);
        player.setData(ModAttachments.HELMET_CO2.get(), (float) ppCo2);

        if (usable) {
            spendDurability(player, cartridge, ModAttachments.CARTRIDGE_WEAR_CARRY.get(),
                    SuitEndurance.wearPerSecond(cartridge.getMaxDamage(),
                            SuitEndurance.CARTRIDGE_SECONDS) * dtSeconds);
        }
        return ppCo2;
    }

    /** Working hard produces markedly more CO2 than standing still. */
    private static double activityOf(ServerPlayer player) {
        return player.isSprinting() || player.swinging
                ? HelmetAtmosphere.WORKING_ACTIVITY
                : HelmetAtmosphere.RESTING_ACTIVITY;
    }

    private SuitLifeSupport() {}
}
