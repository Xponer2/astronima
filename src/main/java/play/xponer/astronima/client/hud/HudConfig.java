package play.xponer.astronima.client.hud;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side placement and sizing for the instrument panel.
 *
 * <p>GUI scale multiplies everything the game draws, so a panel that reads well at
 * scale 2 is overwhelming at scale 4. These let the panel be anchored to any corner,
 * nudged, and scaled independently of the rest of the interface.
 */
public final class HudConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<HudAnchor> PANEL_ANCHOR = BUILDER
            .comment("Screen corner the analyzer panel is attached to.")
            .defineEnum("analyzerPanel.anchor", HudAnchor.TOP_LEFT);

    public static final ModConfigSpec.IntValue PANEL_OFFSET_X = BUILDER
            .comment("Horizontal distance from the anchored corner, in pixels.")
            .defineInRange("analyzerPanel.offsetX", 6, 0, 4096);

    public static final ModConfigSpec.IntValue PANEL_OFFSET_Y = BUILDER
            .comment("Vertical distance from the anchored corner, in pixels.")
            .defineInRange("analyzerPanel.offsetY", 6, 0, 4096);

    public static final ModConfigSpec.DoubleValue PANEL_SCALE = BUILDER
            .comment("Size of the analyzer panel relative to the rest of the GUI.",
                    "Below 1 shrinks it — useful at GUI scale 3 or 4, where it would otherwise dominate.")
            .defineInRange("analyzerPanel.scale", 1.0, 0.25, 2.0);

    public static final ModConfigSpec.EnumValue<HudAnchor> VITALS_ANCHOR = BUILDER
            .comment("Screen corner the body-vitals gauges (CO burden, tissue N2) are attached to.")
            .defineEnum("vitals.anchor", HudAnchor.BOTTOM_LEFT);

    public static final ModConfigSpec.IntValue VITALS_OFFSET_X = BUILDER
            .defineInRange("vitals.offsetX", 4, 0, 4096);

    public static final ModConfigSpec.IntValue VITALS_OFFSET_Y = BUILDER
            .defineInRange("vitals.offsetY", 4, 0, 4096);

    public static final ModConfigSpec.DoubleValue VITALS_SCALE = BUILDER
            .defineInRange("vitals.scale", 1.0, 0.25, 2.0);

    public static final ModConfigSpec.EnumValue<HudAnchor> SUIT_ANCHOR = BUILDER
            .comment("Corner the suit panel is anchored to")
            .defineEnum("suit.anchor", HudAnchor.BOTTOM_RIGHT);
    public static final ModConfigSpec.IntValue SUIT_OFFSET_X = BUILDER
            .comment("Suit panel horizontal offset from its anchor, pixels")
            .defineInRange("suit.offsetX", 4, -1000, 1000);
    public static final ModConfigSpec.IntValue SUIT_OFFSET_Y = BUILDER
            .comment("Suit panel vertical offset from its anchor, pixels")
            .defineInRange("suit.offsetY", 4, -1000, 1000);
    public static final ModConfigSpec.DoubleValue SUIT_SCALE = BUILDER
            .comment("Suit panel scale")
            .defineInRange("suit.scale", 1.0, 0.25, 3.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HudConfig() {}
}
