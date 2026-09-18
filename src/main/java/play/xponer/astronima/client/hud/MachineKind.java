package play.xponer.astronima.client.hud;

/**
 * Which machine a panel belongs to, without Minecraft attached.
 *
 * <p>{@code ProcessingMenu.Kind} extends nothing but lives beside a Minecraft class, so a layout
 * that wanted to say "the winnowing table needs this much room" could not name it. The result was
 * one panel height for all seven machines, sized by the tallest — the retort — leaving the shorter
 * ones with two hundred pixels of nothing above the inventory.
 *
 * <p>A second enum is a thing that can drift, so {@code MachinePanelTest} refuses it: the two must
 * have the same constants in the same order (rule 16).
 */
public enum MachineKind {
    CRUSHER, SEPARATOR, FORGE, RETORT, WINNOWER, REFINER, FLUIDBED, ELECTROLYSIS, SLS,
    CRACKING_TOWER, POLYMERIZER, WATER_ELECTROLYZER, SABATIER_REACTOR, BOSCH_REACTOR,
    TROILITE_ROASTER, SULFURIC_ACID_PLANT, HEAVY_WATER_CELL, TITANIUM_CELL, INDUCTION_FURNACE,
    IRON_SMELTER, FREEZE_DRYER, DOWNS_CELL, ZONE_REFINER, HF_DIGESTER, ETCH_STATION, GRAPHITIZER,
    ALGAE_BIOREACTOR, ANAEROBIC_DIGESTER;

    /** Mirrors {@code ProcessingMenu.threeSlots} exactly — one feed slot, two stacked product
     * slots at {@code (SLOT_OUT_X, row-10)}/{@code (SLOT_OUT_X, row+18)}, the real geometry
     * {@code ProcessingUi.addMachineSlots} draws for these kinds. A second copy on purpose (rule
     * 16): {@code ProcessingMenu} extends a Minecraft class, so {@link MachinePanel} — which must
     * stay loadable in a plain unit test — cannot call it directly. {@code MachinePanelTest}
     * proves the two classifications agree, the same discipline it already holds the enum
     * constants themselves to. */
    public boolean isThreeSlot() {
        return this == SEPARATOR || this == FLUIDBED || this == ELECTROLYSIS
                || this == WINNOWER || this == REFINER;
    }

    /** Mirrors {@code ProcessingMenu.oneSlot} exactly — no product slot at all, gas vented
     * straight into the room. See {@link #isThreeSlot} for why this is a second, guarded copy. */
    public boolean isOneSlot() {
        return this == CRACKING_TOWER || this == WATER_ELECTROLYZER || this == SABATIER_REACTOR;
    }

    /** Mirrors {@code ProcessingMenu.twoFeedsTwoProducts} exactly — two feed slots stacked at
     * {@code SLOT_IN_X}, two product slots stacked at {@code SLOT_OUT_X}. See {@link #isThreeSlot}
     * for why this is a second, guarded copy. */
    public boolean isTwoFeedsTwoProducts() {
        return this == IRON_SMELTER || this == HF_DIGESTER || this == ETCH_STATION;
    }
}
