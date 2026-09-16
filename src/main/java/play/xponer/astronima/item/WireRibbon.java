package play.xponer.astronima.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import play.xponer.astronima.sim.wire.WireRouter;

import java.util.Locale;

/**
 * What a ribbon is set to: how many lanes, and how it routes. {@code design/bus.md} is the design
 * this follows.
 *
 * <p><strong>No colour, no metal, no gauge — unlike {@link WireCoil}, and deliberately.</strong>
 * Every lane's colour is fixed by its position (lane 0 is always the marked stripe; the rest follow
 * a fixed order — §2.3), because a bus that let the player repaint individual lanes could produce
 * two ribbons that are not interchangeable, which is exactly the ambiguity a real ribbon cable's
 * red-edge convention exists to remove. The conductor is fixed too (iron, signal gauge) — a bus is
 * control and address lines, never a power run, so there is nothing here worth a panel over.
 */
public record WireRibbon(WireRouter.Mode mode, int width) {

    /** Four or eight — the only two widths any part in the set has pads to receive (§1.2). */
    public static final int NARROW = 4;
    public static final int WIDE = 8;

    public static final WireRibbon DEFAULT = new WireRibbon(WireRouter.Mode.PATHFIND, WIDE);

    private static final Codec<WireRouter.Mode> MODE_CODEC = Codec.STRING.xmap(
            name -> WireRouter.Mode.valueOf(name.toUpperCase(Locale.ROOT)),
            mode -> mode.name().toLowerCase(Locale.ROOT));

    public static final Codec<WireRibbon> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    MODE_CODEC.fieldOf("mode").forGetter(WireRibbon::mode),
                    Codec.INT.fieldOf("width").forGetter(WireRibbon::width)
            ).apply(instance, WireRibbon::new));

    public static final StreamCodec<ByteBuf, WireRibbon> STREAM_CODEC = StreamCodec.of(
            (buffer, ribbon) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, ribbon.mode.ordinal());
                ByteBufCodecs.VAR_INT.encode(buffer, ribbon.width);
            },
            buffer -> new WireRibbon(
                    value(WireRouter.Mode.values(), ByteBufCodecs.VAR_INT.decode(buffer)),
                    ByteBufCodecs.VAR_INT.decode(buffer) == NARROW ? NARROW : WIDE));

    private static <T> T value(T[] values, int ordinal) {
        return values[Math.floorMod(ordinal, values.length)];
    }

    public WireRibbon toggleMode() {
        return new WireRibbon(mode == WireRouter.Mode.PATHFIND
                ? WireRouter.Mode.STRAIGHT : WireRouter.Mode.PATHFIND, width);
    }

    public WireRibbon toggleWidth() {
        return new WireRibbon(mode, width == NARROW ? WIDE : NARROW);
    }
}
