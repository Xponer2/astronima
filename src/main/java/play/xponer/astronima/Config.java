package play.xponer.astronima;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Tuning knobs for the atmosphere simulation. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Real human physiology (~26 mol O2/day) would take days to deplete a room, which is
     * safe but boring. This multiplier compresses that timeline so life support matters
     * within a play session while the underlying model stays physically correct.
     */
    public static final ModConfigSpec.DoubleValue METABOLISM_SCALE = BUILDER
            .comment("Multiplier applied to real human O2 consumption / CO2 production (1.0 = real-time physiology).")
            .defineInRange("metabolismScale", 60.0, 1.0, 1000.0);

    public static final ModConfigSpec.IntValue MAX_ROOM_VOLUME = BUILDER
            .comment("Largest volume in blocks that can count as one sealed room; bigger spaces read as unsealable.")
            .defineInRange("maxRoomVolume", 4096, 64, 65536);

    /**
     * On the asteroid, outside is always hard vacuum. Until the asteroid dimension
     * ships, this keeps ordinary test worlds playable: unsealed space counts as
     * breathable Earth air while sealed rooms still simulate fully.
     */
    public static final ModConfigSpec.BooleanValue BREATHABLE_OUTSIDE = BUILDER
            .comment("If true, unsealed space is breathable Earth air (for testing in vanilla-style worlds).",
                    "If false, everything outside a sealed room is vacuum.")
            .define("breathableOutside", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
