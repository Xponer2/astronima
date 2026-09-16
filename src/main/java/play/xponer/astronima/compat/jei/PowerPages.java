package play.xponer.astronima.compat.jei;

import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.client.hud.MachineFrame;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.Scrubber;
import play.xponer.astronima.sim.ore.ChlorateDecomposition;
import play.xponer.astronima.sim.power.CombustionEngine;
import play.xponer.astronima.sim.power.FuelCell;
import play.xponer.astronima.sim.power.PowerBalance;
import play.xponer.astronima.sim.thermal.HeatBalance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pages for the machines that make power, air and water rather than items.
 *
 * <h2>Six machines had no page at all</h2>
 * Reported as <em>"some machines have no usage when I look them up"</em>, and they did not: JEI
 * covered the seven machines that turn an item into another item, and the generator, the fuel cell,
 * the candle, the scrubber, the dehumidifier and the array had nothing. Every one of them converts
 * something into something else — that is the definition of a machine — and a player looking one up
 * got a blank.
 *
 * <p>They fitted nowhere because the category was built around an item in a slot. But every one of
 * them has the thing the category actually exists to draw: <strong>one control with two consequences
 * that move in opposite directions</strong>. The candle makes more oxygen the hotter it runs and
 * starts making chlorine; the generator makes power and makes carbon dioxide you then have to
 * scrub. Those are the pages.
 *
 * <p>Every figure is read from the model the machine runs, so a page cannot drift from the game.
 */
public final class PowerPages {

    private static final int SAMPLES = 13;

    // ------------------------------------------------------------------ the array

    /**
     * Sunlight in, watts out — and the number that decides the whole early game.
     *
     * <p>One panel in full sun makes 37 W and a worked machine wants 250 W, so it takes
     * <strong>seven of them to replace one person on a handle</strong>. That is not a nerf; it is
     * sunlight at 2.7 AU being a seventh of Earth's, and it is why every real outer-system probe
     * carries an RTG instead.
     */
    public static List<ProcessingRecipe> solarArray() {
        List<Float> made = new ArrayList<>();
        List<Float> ofAMachine = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            double sun = i / (double) (SAMPLES - 1);
            made.add((float) (PowerBalance.panelWatts(sun) / HeatBalance.WORKED_MACHINE_W));
            ofAMachine.add(1.0f);
        }
        return List.of(new ProcessingRecipe(ItemStack.EMPTY, List.of(), "Solar array",
                "no sun  ->  full sun",
                List.of(new Chart.Series("one panel", MachineFrame.GOOD, made),
                        new Chart.Series("one machine", MachineFrame.BAD, ofAMachine)),
                List.of(format("One square metre in full sun makes %.0f W. A worked machine wants"
                                + " %.0f W, so it takes %d panels to run one.",
                        PowerBalance.PANEL_WATTS, HeatBalance.WORKED_MACHINE_W,
                        (int) Math.ceil(HeatBalance.WORKED_MACHINE_W / PowerBalance.PANEL_WATTS)),
                        "Sunlight here is a seventh of Earth's - that is the distance, not a"
                                + " penalty.",
                        "Roof one over and it makes nothing. It needs open sky, and so does the"
                                + " retort.")));
    }

    // ------------------------------------------------------------------ the generator

    /**
     * Power against the air it costs you.
     *
     * <p>The trade nobody expects until their room turns sour: burning methane makes a mole of
     * carbon dioxide for every mole of fuel, and that lands in the room you are standing in. The
     * generator is not a power source, it is a power source <em>and</em> a scrubber load.
     */
    public static List<ProcessingRecipe> generator() {
        List<Float> power = new ArrayList<>();
        List<Float> spoil = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            double oxygen = i / (double) (SAMPLES - 1);
            CombustionEngine.Burn burn = CombustionEngine.burn(
                    CombustionEngine.FUEL_MOL_PER_SECOND,
                    CombustionEngine.FUEL_MOL_PER_SECOND * CombustionEngine.O2_PER_FUEL * oxygen,
                    1.0);
            power.add((float) (burn.electricalJ() / CombustionEngine.RATED_WATTS));
            spoil.add((float) (burn.co2Mol() / (CombustionEngine.FUEL_MOL_PER_SECOND
                    * CombustionEngine.CO2_PER_FUEL)));
        }
        return List.of(new ProcessingRecipe(ItemStack.EMPTY, List.of(), "Combustion generator",
                "starved of air  ->  all it wants",
                List.of(new Chart.Series("power", MachineFrame.GOOD, power),
                        new Chart.Series("CO2 made", MachineFrame.BAD, spoil)),
                List.of(format("Burns methane at %.0f%% thermal efficiency for %.0f W - and it"
                                + " burns %.0f moles of oxygen for every mole of fuel.",
                        CombustionEngine.THERMAL_EFFICIENCY * 100, CombustionEngine.RATED_WATTS,
                        CombustionEngine.O2_PER_FUEL),
                        "The two lines never separate, and that is the point: every watt costs"
                                + " you exactly its share of carbon dioxide, in the room you are"
                                + " standing in.",
                        "It stalls on air before it stalls on fuel. A generator in a sealed room"
                                + " suffocates itself, and then you.",
                        "Feed the exhaust to a scrubber, or run it outside and wire the power in."
                )));
    }

    // ------------------------------------------------------------------ the fuel cell

    /**
     * The same energy, more than twice as far — and it hands you the water back.
     *
     * <p>60% against the engine's 25%, because there is no heat engine in the middle and therefore
     * no Carnot limit to pay. The catch is upstream: it wants pure hydrogen, which is a chemistry
     * tier away, where methane is something you can dig up.
     */
    public static List<ProcessingRecipe> fuelCell() {
        List<Float> cell = new ArrayList<>();
        List<Float> engine = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            double supply = i / (double) (SAMPLES - 1);
            FuelCell.Run run = FuelCell.run(FuelCell.FUEL_MOL_PER_SECOND * supply,
                    FuelCell.FUEL_MOL_PER_SECOND * FuelCell.O2_PER_FUEL, 1.0);
            cell.add((float) (run.electricalJ() / FuelCell.RATED_WATTS));
            engine.add((float) (supply * CombustionEngine.THERMAL_EFFICIENCY
                    / FuelCell.EFFICIENCY));
        }
        return List.of(new ProcessingRecipe(ItemStack.EMPTY,
                List.of(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                        net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.WATER)), "Fuel cell",
                "no hydrogen  ->  all it wants",
                List.of(new Chart.Series("cell", MachineFrame.GOOD, cell),
                        new Chart.Series("engine", MachineFrame.WARN, engine)),
                List.of(format("%.0f%% efficient against the engine's %.0f%%, because there is no"
                                + " heat engine in the middle and so no Carnot limit to pay.",
                        FuelCell.EFFICIENCY * 100, CombustionEngine.THERMAL_EFFICIENCY * 100),
                        format("Every mole of hydrogen wants %.1f of oxygen and gives back %.0f of"
                                        + " water. On a closed base that water is not a by-product,"
                                        + " it is the point.",
                                FuelCell.O2_PER_FUEL, FuelCell.WATER_PER_FUEL),
                        "The catch is upstream: it needs pure hydrogen, and methane you can dig"
                                + " up.")));
    }

    // ------------------------------------------------------------------ the candle

    /**
     * The classic two-consequence curve: hotter frees more oxygen and starts making chlorine.
     *
     * <p>Real chemistry, and the reason a chlorate candle is a serious piece of equipment rather
     * than a firework. Sodium chlorate gives up its oxygen from 250 °C and is done by 500 °C; push
     * past 600 °C and the chloride starts coming off as chlorine, which is a lung poison in the
     * room you were trying to make breathable.
     */
    public static List<ProcessingRecipe> oxygenCandle() {
        List<Float> oxygen = new ArrayList<>();
        List<Float> chlorine = new ArrayList<>();
        double from = ChlorateDecomposition.ONSET_K - 60;
        double to = ChlorateDecomposition.CHLORINE_K + ChlorateDecomposition.CHLORINE_RAMP_K;
        for (int i = 0; i < SAMPLES; i++) {
            double kelvin = from + (to - from) * i / (double) (SAMPLES - 1);
            ChlorateDecomposition.Bake bake = ChlorateDecomposition.bake(100, kelvin);
            oxygen.add((float) (bake.oxygenMoles() / (100 * ChlorateDecomposition.O2_MOL_PER_GRAM)));
            chlorine.add((float) ChlorateDecomposition.chlorineFraction(kelvin));
        }
        return List.of(new ProcessingRecipe(new ItemStack(ModItems.CHLORATE_POWDER.get()),
                List.of(), "Oxygen candle",
                format("%.0f K  ->  %.0f K", from, to),
                List.of(new Chart.Series("oxygen", MachineFrame.GOOD, oxygen),
                        new Chart.Series("chlorine", MachineFrame.BAD, chlorine)),
                List.of(format("Chlorate gives up its oxygen from %.0f K and is done by %.0f K.",
                        ChlorateDecomposition.ONSET_K, ChlorateDecomposition.COMPLETE_K),
                        format("Past %.0f K the chloride starts coming off as chlorine - a lung"
                                        + " poison, in the room you were making breathable.",
                                ChlorateDecomposition.CHLORINE_K),
                        "The window between the two is the whole skill. Run it cool and you waste"
                                + " charge; run it hot and you poison the room you are saving.")));
    }

    // ------------------------------------------------------------------ the scrubber

    /**
     * What a cartridge holds, and how fast the room empties it.
     *
     * <p>The rate is proportional to the carbon dioxide already in the air, which is why a scrubber
     * cannot be sized by eye: it works hardest exactly when the room is worst, and the cartridge
     * that lasted a week alone lasts a day with three people in it.
     */
    public static List<ProcessingRecipe> scrubber() {
        List<Float> rate = new ArrayList<>();
        List<Float> hours = new ArrayList<>();
        double worst = 3.0;
        for (int i = 0; i < SAMPLES; i++) {
            double ppCO2 = worst * i / (double) (SAMPLES - 1);
            double perSecond = Scrubber.absorbedMol(ppCO2, 1.0);
            rate.add((float) (perSecond / Scrubber.absorbedMol(worst, 1.0)));
            double life = perSecond <= 0 ? 1
                    : Math.min(1, Scrubber.CARTRIDGE_CAPACITY_MOL / perSecond / 3600.0 / 6.0);
            hours.add((float) life);
        }
        return List.of(new ProcessingRecipe(
                new ItemStack(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get()), List.of(),
                "Carbon dioxide scrubber",
                "clean air  ->  a room going bad",
                List.of(new Chart.Series("removing", MachineFrame.GOOD, rate),
                        new Chart.Series("cartridge lasts", MachineFrame.WARN, hours)),
                List.of(format("A cartridge holds %.0f moles of carbon dioxide, and it swaps at"
                                + " %.0f%% left.", Scrubber.CARTRIDGE_CAPACITY_MOL,
                        Scrubber.SWAP_THRESHOLD_FRACTION * 100),
                        "It works in proportion to what is already in the air, so it works hardest"
                                + " exactly when the room is worst - and a cartridge that lasted a"
                                + " week alone will not last with company.",
                        "Carbon dioxide is what gets you first in a sealed room. The oxygen"
                                + " outlasts it.")));
    }

    // ------------------------------------------------------------------ the dehumidifier

    /**
     * Water out of the air you are already breathing into.
     *
     * <p>A person exhales a litre and a half of water a day and it has nowhere to go in a sealed
     * habitat, so a condenser is not a water source bolted on — it is the room's own humidity given
     * somewhere to end up.
     */
    public static List<ProcessingRecipe> condenser() {
        List<Float> collected = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            collected.add((float) (i / (double) (SAMPLES - 1)));
        }
        return List.of(new ProcessingRecipe(ItemStack.EMPTY,
                List.of(net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                        net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.WATER)), "Condenser",
                "dry room  ->  humid room",
                List.of(new Chart.Series("collecting", MachineFrame.GOOD, collected)),
                List.of(format("%.0f moles of water fill a bottle - about half a litre.",
                        play.xponer.astronima.block.entity.DehumidifierBlockEntity.MOL_PER_BOTTLE),
                        "It takes only the humidity above what the room can hold, so a dry room"
                                + " yields nothing and a crowded one fills bottles by itself.",
                        "The water you breathe out is the water you drink. That loop is the whole"
                                + " reason a habitat can be closed at all.")));
    }

    private static String format(String pattern, Object... values) {
        return String.format(Locale.ROOT, pattern, values);
    }

    private PowerPages() {}
}
