package play.xponer.astronima.client.hud;

/**
 * Where the rows of a machine panel go.
 *
 * <p>Written after the same bug twice. The panel's contents were laid out by
 * hand-computed y offsets, I checked the crusher, and the separator — which has one
 * extra row — overflowed into the player's inventory label. Fixing that by adjusting a
 * constant would have left the next machine with an extra row to overflow again.
 *
 * <p>So rows are stacked rather than positioned: ask for the next one and it lands
 * below the last. The panel's required height then falls out of how many rows were
 * asked for, and a test can assert that the tallest machine still fits above the
 * inventory. Collisions become arithmetic that is checked rather than eyeballed.
 */
public final class PanelLayout {
    public static final int ROW_HEIGHT = 11;
    public static final int GAUGE_ROW_HEIGHT = 12;

    private final int startY;
    private int cursor;

    public PanelLayout(int startY) {
        this.startY = startY;
        this.cursor = startY;
    }

    /** Reserves a text row and returns its y. */
    public int row() {
        int y = cursor;
        cursor += ROW_HEIGHT;
        return y;
    }

    /** Reserves a row tall enough for a gauge and its label. */
    public int gaugeRow() {
        int y = cursor;
        cursor += GAUGE_ROW_HEIGHT;
        return y;
    }

    /** Adds bare space, for separating groups. */
    public void gap(int pixels) {
        cursor += pixels;
    }

    /** The y just past the last reserved row. */
    public int bottom() {
        return cursor;
    }

    public int height() {
        return cursor - startY;
    }

    /**
     * How many rows each machine's readout actually draws.
     *
     * <p><strong>Declared, not guessed.</strong> The previous version asserted that the separator
     * was the worst case — two text rows and two gauges — and it was wrong: the retort draws
     * <em>four</em> gauges and the forge three. Their last rows went straight through the
     * "Inventory" label and into the player's slots, which is the same fault the comment in
     * {@code ProcessingMenu} says was already fixed once. It was fixed for one machine.
     *
     * <p>A guard reads this table against the screen's own source, so a machine that grows a row
     * fails the build instead of quietly overlapping.
     *
     * @param rows   plain text rows
     * @param gauges rows tall enough for a gauge and its label
     */
    public record Rows(String machine, MachineKind kind, int rows, int gauges, int extraPixels) {
        public Rows(String machine, MachineKind kind, int rows, int gauges) {
            this(machine, kind, rows, gauges, 0);
        }
    }

    public static final java.util.List<Rows> MACHINE_ROWS = java.util.List.of(
            // Every machine now draws its own; the dispatcher only routes.
            new Rows("drawReadouts", null, 0, 0),
            new Rows("drawCrusherReadouts", MachineKind.CRUSHER, 2, 0,
                    CrusherView.HEIGHT - ROW_HEIGHT),
            new Rows("drawSeparatorReadouts", MachineKind.SEPARATOR, 2, 2,
                    SeparatorView.HEIGHT - ROW_HEIGHT),
            new Rows("drawRefinerReadouts", MachineKind.REFINER, 2, 0,
                    RefinerView.HEIGHT - ROW_HEIGHT),
            new Rows("drawFluidBedReadouts", MachineKind.FLUIDBED, 1, 0,
                    FluidBedView.HEIGHT - ROW_HEIGHT),
            new Rows("drawWinnowerReadouts", MachineKind.WINNOWER, 2, 2,
                    WinnowerView.HEIGHT - ROW_HEIGHT),
            new Rows("drawForgeReadouts", MachineKind.FORGE, 2, 0,
                    ForgeView.HEIGHT - ROW_HEIGHT),
            // The retort draws a picture instead of rows, so it books its own height as a gap.
            // Declared in pixels rather than rows because that is what it is.
            new Rows("drawRetortReadouts", MachineKind.RETORT, 2, 4,
                    RetortView.HEIGHT - ROW_HEIGHT),
            new Rows("drawElectrolysisCellReadouts", MachineKind.ELECTROLYSIS, 1, 0,
                    ElectrolysisCellView.HEIGHT - ROW_HEIGHT),
            new Rows("drawSlsPrinterReadouts", MachineKind.SLS, 1, 0,
                    SlsPrinterView.HEIGHT - ROW_HEIGHT),
            // Neither draws anything beyond the shared bar and work-state line: both machines'
            // whole real state is already visible in the feed slot itself (a tholin/sludge item,
            // a water bottle) - inventing a gauge here would be the decorative dial rule 8
            // refuses, not a real reading (design/machines.md's own Update section).
            new Rows("drawReadouts", MachineKind.CRACKING_TOWER, 0, 0),
            new Rows("drawReadouts", MachineKind.WATER_ELECTROLYZER, 0, 0),
            // One real reading each: the room ethylene/O2 this machine actually needs, which is
            // otherwise completely invisible (design/machines.md's own Update section).
            new Rows("drawPolymerizerReadouts", MachineKind.POLYMERIZER, 1, 0),
            new Rows("drawTroiliteRoasterReadouts", MachineKind.TROILITE_ROASTER, 1, 0),
            // Two real readings: both room reagents a Sabatier/Bosch batch actually needs.
            new Rows("drawSabatierReadouts", MachineKind.SABATIER_REACTOR, 2, 0),
            new Rows("drawBoschReadouts", MachineKind.BOSCH_REACTOR, 2, 0),
            // Three real readings: the only machine in the mod reading three room gases at once.
            new Rows("drawSulfuricAcidReadouts", MachineKind.SULFURIC_ACID_PLANT, 3, 0),
            // No dial, but a real two-line readout: the live D2O fraction and what to do next.
            new Rows("drawHeavyWaterReadouts", MachineKind.HEAVY_WATER_CELL, 2, 0),
            // Nothing extra: reacts whatever titania it is fed, same as the seven reagent
            // machines above.
            new Rows("drawReadouts", MachineKind.TITANIUM_CELL, 0, 0),
            // Nothing extra: melts whatever iron powder it is fed, always.
            new Rows("drawReadouts", MachineKind.INDUCTION_FURNACE, 0, 0),
            // No dial, but a real two-line readout: the flux ratio bar and what to do next.
            new Rows("drawIronSmelterReadouts", MachineKind.IRON_SMELTER, 2, 0),
            // Nothing extra: the work-state line already names "needs vacuum and LN2" when it
            // is not ready — no second-order number worth a row of its own.
            new Rows("drawReadouts", MachineKind.FREEZE_DRYER, 0, 0),
            // Nothing extra: electrolyzes whatever rock salt it is fed, always.
            new Rows("drawReadouts", MachineKind.DOWNS_CELL, 0, 0),
            // Nothing extra: purifies whatever silicon it is fed, always.
            new Rows("drawReadouts", MachineKind.ZONE_REFINER, 0, 0),
            // Nothing extra: reacts whatever fluorite and sulfuric acid it is fed, at the real
            // fixed 1:1 stoichiometry - no ratio to report the way iron smelter's flux has one.
            new Rows("drawReadouts", MachineKind.HF_DIGESTER, 0, 0),
            // One real reading: whether the adjacent cleanroom controller currently certifies
            // this room clean enough to run at all - otherwise invisible (design/halogens.md §43).
            new Rows("drawEtchStationReadouts", MachineKind.ETCH_STATION, 1, 0));

    /**
     * How far down one machine's own content reaches.
     *
     * <p>Added so each panel can be its own height. Sizing every machine by the tallest of them
     * left the winnowing table with a screen most of which was empty grey, and the player's
     * inventory two hundred pixels below the last thing worth reading.
     */
    public static int contentBottom(MachineKind kind, int startY) {
        for (Rows machine : MACHINE_ROWS) {
            if (machine.kind() != kind) {
                continue;
            }
            PanelLayout layout = new PanelLayout(startY);
            for (int i = 0; i < machine.rows(); i++) {
                layout.row();
            }
            for (int i = 0; i < machine.gauges(); i++) {
                layout.gaugeRow();
            }
            layout.gap(machine.extraPixels());
            return layout.bottom();
        }
        return tallestMachineContent(startY);
    }

    /**
     * The tallest content any machine panel will produce.
     *
     * <p>Taken over the whole published set rather than from whichever machine somebody remembered
     * (rule 20), so the panel is sized by the machine that actually needs the most room.
     */
    public static int tallestMachineContent(int startY) {
        int tallest = startY;
        for (Rows machine : MACHINE_ROWS) {
            PanelLayout layout = new PanelLayout(startY);
            for (int i = 0; i < machine.rows(); i++) {
                layout.row();
            }
            for (int i = 0; i < machine.gauges(); i++) {
                layout.gaugeRow();
            }
            layout.gap(machine.extraPixels());
            tallest = Math.max(tallest, layout.bottom());
        }
        return tallest;
    }

    private PanelLayout() {
        this(0);
    }
}
