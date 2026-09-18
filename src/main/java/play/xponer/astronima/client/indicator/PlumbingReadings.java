package play.xponer.astronima.client.indicator;

import play.xponer.astronima.airlock.AirlockResolver;
import play.xponer.astronima.sim.Cleanroom;
import play.xponer.astronima.sim.Scrubber;
import play.xponer.astronima.sim.airlock.AirlockCycle;
import play.xponer.astronima.sim.machine.WorkState;
import play.xponer.astronima.sim.airlock.DeviceBinding;
import play.xponer.astronima.sim.pipe.PressureVessel;
import play.xponer.astronima.sim.power.CombustionEngine;
import play.xponer.astronima.sim.power.FuelCell;
import play.xponer.astronima.sim.ore.RetortAdvice;
import play.xponer.astronima.sim.ore.RetortProcess;
import play.xponer.astronima.sim.pipe.Valve;

import java.util.Locale;

/**
 * What each plumbing block's gauge says.
 *
 * <p>Kept together and MC-free so the thresholds can be asserted against the sim's own
 * limits. A gauge that reads "nominal" while the model considers a vessel overpressured
 * is worse than having no gauge at all, because it is trusted.
 */
public final class PlumbingReadings {

    /**
     * A pressure vessel, read against its <em>working</em> pressure.
     *
     * <p>Not against burst: a gauge showing 40 % on a tank that is already at its rating
     * would be telling the player there is room left in something that is full.
     */
    public static Reading tank(double pressureKPa) {
        Reading.Band band = switch (PressureVessel.classify(pressureKPa)) {
            case NOMINAL -> Reading.Band.NOMINAL;
            case OVERPRESSURE -> Reading.Band.WARNING;
            case BURST -> Reading.Band.CRITICAL;
        };
        return new Reading(PressureVessel.gauge(pressureKPa), band,
                String.format("%.0f kPa", pressureKPa));
    }

    /**
     * A retort, read against the window rather than against a maximum.
     *
     * <p>The needle is deliberately <em>not</em> "how hot, as a fraction of hottest": on
     * this machine hotter stops being better partway along and then becomes actively worse,
     * so a bar that filled up would be praising the mistake. It reads against the window
     * instead, and the band says which side of it you are on — which is the same thing the
     * panel's marked track says, from across the room and without opening anything.
     */
    public static Reading retort(double sunlight, double temperatureK, RetortProcess process) {
        RetortAdvice.Verdict verdict = RetortAdvice.read(sunlight, temperatureK, process);
        Reading.Band band = switch (verdict) {
            case IDEAL -> Reading.Band.NOMINAL;
            case DARK, COLD -> Reading.Band.IDLE;
            case PARTIAL, EXCESS -> Reading.Band.WARNING;
            case SPOILING -> Reading.Band.CRITICAL;
        };
        String label = verdict == RetortAdvice.Verdict.DARK
                ? "no sun" : String.format(Locale.ROOT, "%.0f K", temperatureK);
        return new Reading(RetortAdvice.barFraction(temperatureK), band, label);
    }

    /**
     * An oxygen candle, read as how much of it is left.
     *
     * <p>The block already shows lit, unlit and spent through its model, and that is three
     * states out of a hundred: a candle nine-tenths gone looks exactly like one just struck.
     * <strong>After the oxygen tier that is the difference between planning and being
     * surprised</strong> — a candle is a hundred moles and about fifteen blocks of digging, so
     * knowing whether you have a quarter of one left is a decision, not a curiosity.
     *
     * @param lit      whether it is burning now
     * @param spent    whether there is nothing left in it
     * @param fraction how much of the charge remains, 0..1
     */
    public static Reading candle(boolean lit, boolean spent, double fraction) {
        if (spent) {
            return new Reading(0, Reading.Band.IDLE, "spent");
        }
        if (!lit) {
            return new Reading(Math.clamp(fraction, 0.0, 1.0), Reading.Band.IDLE, "unlit");
        }
        Reading.Band band = fraction < 0.15 ? Reading.Band.CRITICAL
                : fraction < 0.4 ? Reading.Band.WARNING : Reading.Band.NOMINAL;
        return new Reading(Math.clamp(fraction, 0.0, 1.0), band,
                Math.round(fraction * 100) + " %");
    }

    /**
     * A solar array, read against what one panel can possibly make.
     *
     * <p>Full scale is one panel in full sun — 37 W — rather than anything the base might draw,
     * because <strong>the point of the reading is that the number is small</strong>. A needle
     * scaled against a machine's 250 W would sit near the bottom forever and teach the player
     * that the array is broken rather than that the belt is dim.
     */
    public static Reading solarArray(double sunlight, double watts) {
        Reading.Band band = sunlight <= 0 ? Reading.Band.IDLE
                : sunlight < 0.5 ? Reading.Band.WARNING : Reading.Band.NOMINAL;
        String label = sunlight <= 0 ? "no sun" : String.format(Locale.ROOT, "%.0f W", watts);
        return new Reading(Math.clamp(sunlight, 0.0, 1.0), band, label);
    }

    /**
     * A combustion generator, read as <em>why it is not running</em>.
     *
     * <p>Out of fuel and out of air look identical from outside the machine and need opposite
     * responses — pipe more methane in, or get more oxygen in. Rule 18 says the error path has
     * to name which, and on a block the only way to name it is the label.
     */
    public static Reading generator(CombustionEngine.Stall stall, double watts) {
        return switch (stall) {
            case NO_FUEL -> new Reading(0, Reading.Band.IDLE, "no fuel");
            case NO_AIR -> new Reading(0, Reading.Band.CRITICAL, "no air");
            // Its own label, because the fix is in a different category: the other two are
            // plumbing and this is a shovel. "Stopped" for all three would send the player to
            // check pipes that are perfectly fine (rule 18).
            case FOULED -> new Reading(1, Reading.Band.WARNING, "choked");
            case RUNNING -> new Reading(
                    Math.clamp(watts / CombustionEngine.RATED_WATTS, 0.0, 1.0),
                    Reading.Band.NOMINAL, String.format(Locale.ROOT, "%.0f W", watts));
        };
    }

    /**
     * A fuel cell, read as <em>which feed is empty</em>.
     *
     * <p>Out of hydrogen and out of oxygen are completely different journeys — one is a trip
     * into the deep rock for a rare pocket, the other is a valve on a tank you already have.
     * Rule 18: a block saying only "stopped" sends the player on the wrong one.
     */
    public static Reading fuelCell(FuelCell.Stall stall, double watts) {
        return switch (stall) {
            case NO_HYDROGEN -> new Reading(0, Reading.Band.IDLE, "no H2");
            case NO_OXYGEN -> new Reading(0, Reading.Band.CRITICAL, "no O2");
            case RUNNING -> new Reading(Math.clamp(watts / FuelCell.RATED_WATTS, 0.0, 1.0),
                    Reading.Band.NOMINAL, String.format(Locale.ROOT, "%.0f W", watts));
        };
    }

    /**
     * A power cell, read as what it is for: how much of the night it covers.
     *
     * <p>Empty is CRITICAL rather than merely idle, and that is the honest severity here — a
     * flat cell at dusk means every machine stops until morning, and the player can do
     * something about it while the sun is still up.
     */
    public static Reading powerCell(double charge, double storedJ) {
        Reading.Band band = charge <= 0.001 ? Reading.Band.CRITICAL
                : charge < 0.2 ? Reading.Band.WARNING : Reading.Band.NOMINAL;
        // Minutes of one machine at its rated draw: the unit a player actually plans in.
        double minutes = storedJ / play.xponer.astronima.sim.thermal.HeatBalance.WORKED_MACHINE_W
                / 60.0;
        return new Reading(Math.clamp(charge, 0.0, 1.0), band,
                String.format(Locale.ROOT, "%.0f min", minutes));
    }

    /**
     * A condenser, showing whether there is water in it worth walking over for.
     *
     * <p>Added because its only readout was a chat line, which rule 9 forbids outright:
     * you had to right-click a machine to discover it had nothing for you. That is
     * indistinguishable from a broken one, and it is the last link of the rock → steam →
     * bottle chain, so being unreadable there wasted the whole chain upstream of it.
     */
    public static Reading condenser(int bottlesReady, double progressToNext) {
        if (bottlesReady > 0) {
            return new Reading(1, Reading.Band.NOMINAL,
                    bottlesReady + (bottlesReady == 1 ? " bottle" : " bottles"));
        }
        return new Reading(progressToNext, Reading.Band.IDLE,
                String.format(Locale.ROOT, "%.0f%%", progressToNext * 100));
    }

    /**
     * A pump, which has only three things it can be.
     *
     * <p>Stalled and unrouted are different states with different fixes — one needs a
     * bigger pump, the other needs plumbing — so they do not share a colour.
     */
    public static Reading pump(boolean running, boolean routed) {
        if (!routed) {
            return new Reading(0, Reading.Band.WARNING, "no route");
        }
        return running
                ? new Reading(1, Reading.Band.NOMINAL, "running")
                : new Reading(0, Reading.Band.IDLE, "stalled");
    }

    /**
     * A valve, showing the bore rather than the setting number.
     *
     * <p>The label carries both bore and flow because they differ by the fourth power:
     * a valve at half bore passes a sixteenth, and showing only "50 %" would be a
     * comfortable lie about what is actually getting through.
     */
    public static Reading valve(int setting) {
        double open = Valve.openFraction(setting);
        if (open <= 0) {
            return new Reading(0, Reading.Band.IDLE, "shut");
        }
        double flow = Valve.flowFraction(open);
        return new Reading(open,
                open >= 1.0 ? Reading.Band.NOMINAL : Reading.Band.WARNING,
                String.format("%.0f%% bore, %.0f%% flow", open * 100, flow * 100));
    }

    /**
     * The airlock controller: the phase and the chamber's pressure, or the reason it will
     * not run.
     *
     * <p>Priority order is the player's: no chamber at all outranks everything, then a
     * terminal whose device cannot do its job — and that one is named <em>on the device the
     * player commissioned</em>, "outer door — not on the chamber", which is the whole gain
     * of rule 17 carried through to the gauge on the block. Then a cycle held on a fault.
     * Otherwise the phase, with the falling or rising chamber pressure as the bar — the
     * readout a real airlock panel gives.
     *
     * <p>{@code faultRole} is only read when {@code faultProblem} is not
     * {@link DeviceBinding.Problem#NONE}; a clean panel has no terminal to name.
     */
    public static Reading airlock(AirlockCycle.Phase phase, AirlockResolver.Fault chamberFault,
                                  AirlockCycle.Fault cycleFault,
                                  DeviceBinding.Role faultRole,
                                  DeviceBinding.Problem faultProblem,
                                  double chamberPressureKPa, double habitatPressureKPa) {
        if (chamberFault != AirlockResolver.Fault.NONE) {
            return new Reading(0, Reading.Band.WARNING, label(chamberFault.name()));
        }
        if (faultProblem != DeviceBinding.Problem.NONE) {
            return new Reading(0, Reading.Band.WARNING,
                    faultRole.words() + " — " + faultProblem.words());
        }
        if (cycleFault != AirlockCycle.Fault.NONE) {
            return new Reading(0, Reading.Band.CRITICAL, label(cycleFault.name()));
        }
        double fraction = habitatPressureKPa > 0
                ? Math.clamp(chamberPressureKPa / habitatPressureKPa, 0.0, 1.0) : 0.0;
        Reading.Band band = switch (phase) {
            case SEALED -> Reading.Band.NOMINAL;
            case VACUUM -> Reading.Band.IDLE;
            case PUMPING, VENTING, REPRESSURIZING -> Reading.Band.WARNING;
        };
        return new Reading(fraction, band, String.format("%s %.0f kPa",
                label(phase.name()), chamberPressureKPa));
    }

    /**
     * The CO2 scrubber: how much absorbent is left in the cartridge.
     *
     * <p>The one number that decides whether the room stays breathable, and until now the
     * only way to see it was to click the block and read a chat line — which scrolls away,
     * cannot be glanced at from the doorway, and is exactly what rule 9 refuses. The
     * threshold is not a display choice: it is {@link Scrubber#SWAP_THRESHOLD_FRACTION},
     * the same constant the machine uses to decide whether a fresh cartridge may be fitted,
     * so the gauge cannot say "fine" about a cartridge the block considers spent.
     *
     * <p>A quarter left is the warning band. Not arbitrary either — a scrubber slows as it
     * fills, so the last quarter is where the room's CO2 starts climbing while the number
     * still looks survivable, and that is precisely when the player needs telling.
     */
    /**
     * A paraffin thermal mass: how much melting capacity it has left to absorb the next heat
     * spike with, not how full it is — the same "headroom, not fullness" framing {@link
     * #scrubber} already uses, since the number a player needs before adding another machine to
     * a room is whether the blocks already there can still take more.
     */
    public static Reading paraffinThermalMass(double meltFraction) {
        double melt = Math.clamp(meltFraction, 0.0, 1.0);
        double headroom = 1.0 - melt;
        if (melt >= 1.0) {
            return new Reading(headroom, Reading.Band.CRITICAL, "fully melted, no headroom left");
        }
        if (melt <= 0.0) {
            return new Reading(headroom, Reading.Band.NOMINAL, "fully frozen");
        }
        Reading.Band band = melt >= 0.75 ? Reading.Band.WARNING : Reading.Band.NOMINAL;
        return new Reading(headroom, band, String.format("%.0f%% melted", melt * 100));
    }

    public static Reading scrubber(double chargeFraction) {
        double charge = Math.clamp(chargeFraction, 0.0, 1.0);
        if (charge <= Scrubber.SWAP_THRESHOLD_FRACTION) {
            return new Reading(charge, Reading.Band.CRITICAL, "cartridge spent");
        }
        Reading.Band band = charge < LOW_ABSORBENT_FRACTION
                ? Reading.Band.WARNING : Reading.Band.NOMINAL;
        return new Reading(charge, band,
                String.format("%.0f%% absorbent", charge * 100));
    }

    /**
     * A cleanroom controller — design/halogens.md §34. The bar's own fraction is the number the
     * player actually came to read (cleanliness); the band and label fold in filter health too,
     * the same "one gauge, one number" reasoning {@link #scrubber} already keeps, since a clean
     * room about to lose its only means of staying clean is not nominal.
     */
    public static Reading cleanroom(double cleanlinessFraction, double filterChargeFraction) {
        double clean = Math.clamp(cleanlinessFraction, 0.0, 1.0);
        double filter = Math.clamp(filterChargeFraction, 0.0, 1.0);
        if (filter <= Cleanroom.SWAP_THRESHOLD_MOL / Cleanroom.HEPA_CAPACITY_MOL) {
            return new Reading(clean, Reading.Band.CRITICAL,
                    String.format("%.0f%% clean, filter spent", clean * 100));
        }
        Reading.Band band = clean < 0.5 ? Reading.Band.WARNING : Reading.Band.NOMINAL;
        return new Reading(clean, band,
                String.format("%.0f%% clean, %.0f%% filter", clean * 100, filter * 100));
    }

    /**
     * The decon booth: what it is doing, how far it has got, and whether it can finish.
     *
     * <p>The gauge is <strong>decades removed</strong>, not a percentage. Every equal dose kills
     * the same fraction of what is left, so a percentage would flatter the first few seconds and
     * then look broken for the rest of the run. Three decades is a thousandfold and is the point
     * at which this booth calls it done.
     */
    public static Reading deconStation(boolean running, boolean rinsing, double decades,
                                       double waterFraction) {
        double gauge = Math.clamp(decades / 3.0, 0.0, 1.0);
        if (!running) {
            return waterFraction < 0.05
                    ? new Reading(waterFraction, Reading.Band.WARNING, "dry - no rinse available")
                    : new Reading(waterFraction, Reading.Band.IDLE,
                            String.format("idle, %.0f%% water", waterFraction * 100));
        }
        return new Reading(gauge, Reading.Band.NOMINAL,
                String.format("%s - %.1f decades off", rinsing ? "rinsing" : "ultraviolet",
                        decades));
    }

    /** Below this the bed is measurably slowing and the room's CO2 begins to climb. */
    private static final double LOW_ABSORBENT_FRACTION = 0.25;

    /**
     * A worked machine — crusher, separator, forge — saying whether it is getting on.
     *
     * <p>The last blind spot, and the one that produced the report this whole state machine
     * was written for: <em>"if you take the item out the progress is kept but stops"</em>.
     * {@link WorkState} answered it — half-crushed rock does not un-crush itself — and then
     * the answer lived only inside the machine's own menu, so a player walking past a stalled
     * crusher still saw exactly what they saw before: a machine, not moving.
     *
     * <p>The band is the operator's, not the machine's: a full product slot and an empty feed
     * are both <em>your</em> doing and both trivially fixed, so they warn rather than alarm; a
     * ruined workpiece is material already lost, so it does not. Progress is the bar, because
     * a stalled machine that is 80 % through a batch is a different thing to walk over to
     * than one that has not started.
     */
    public static Reading machine(WorkState state, double progress) {
        Reading.Band band = switch (state) {
            case CRANKING -> Reading.Band.NOMINAL;
            case CREEPING -> Reading.Band.IDLE;
            case STARVED, BLOCKED, TOO_COLD, UNLIT, UNPRESSURISED, NO_REAGENT, PACKED, NO_ETHYLENE,
                    NO_FEEDSTOCK_GAS, NO_OXYGEN, NO_ACID_FEEDSTOCK, NOT_READY_TO_DRY,
                    NEEDS_OXIDIZER, NO_CARBON_DIOXIDE -> Reading.Band.WARNING;
            // Blowing out and combusting are both advancing, but every step either one advances,
            // something real is leaving — the bed out the exhaust, the charge up as CO2 — so both
            // alarm like a ruined workpiece rather than warn like a passive stall.
            case SPOILED, BACKPRESSURE, BLOWING_OUT, COMBUSTING -> Reading.Band.CRITICAL;
        };
        // The percentage rides along on anything that is stopped mid-batch: "no feed" on a
        // machine holding 80 % of a batch is a different message to "no feed" on an idle one,
        // and it is the half that told the player their progress had not been thrown away.
        String label = !state.isWorking() && progress > 0.01
                ? String.format(Locale.ROOT, "%s - %.0f%% held", state.label(), progress * 100)
                : state.label();
        return new Reading(progress, band, label);
    }

    /**
     * A bulkhead door, saying whether the interlock is holding it.
     *
     * <p>A door is the one block here with no block entity — there can be hundreds of them —
     * so it gets no lamp of its own. The latch is drawn on the door itself (a thrown bolt,
     * a lit lamp), and this is what the crosshair adds: the word for it, so a player who
     * has just been refused by a door knows they were refused rather than that the game
     * missed their click.
     */
    public static Reading bulkheadDoor(boolean locked, boolean open) {
        if (locked) {
            return new Reading(1.0, Reading.Band.CRITICAL, "interlocked - held by an airlock");
        }
        return new Reading(open ? 1.0 : 0.0, Reading.Band.NOMINAL, open ? "open" : "sealed");
    }

    /** Enum constant to a lower-case human label: {@code NEEDS_TWO_DOORS → needs two doors}. */
    private static String label(String constant) {
        return constant.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /**
     * A storage drive: how much of its 54 real slots the player's own built structure has
     * actually unlocked. See {@code design/data-cells.md} §9.
     *
     * <p>Added because a vanilla {@code ChestMenu} cannot say <em>why</em> a slot refuses a
     * cell — the same "unreadable state" rule 9 forbids elsewhere, just moved from a chat line
     * to a locked slot. A bare drive (one usable slot) reads as idle rather than critical: it
     * still works, it is just modest.
     */
    public static Reading storageDrive(int usableSlots, int maxSlots, int connectedFrames) {
        Reading.Band band = connectedFrames <= 0 ? Reading.Band.IDLE : Reading.Band.NOMINAL;
        return new Reading((double) usableSlots / maxSlots, band,
                usableSlots + "/" + maxSlots + " slots (" + connectedFrames + " frames)");
    }

    private PlumbingReadings() {}
}
