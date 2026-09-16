package play.xponer.astronima.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import play.xponer.astronima.sim.circuit.ConductorMaterial;
import play.xponer.astronima.sim.circuit.WireGauge;
import play.xponer.astronima.sim.wire.WireRouter;
import play.xponer.astronima.wire.WireTrace;

import java.util.Locale;

/**
 * What a coil of wire is set to: the metal on it, the colour of its insulation, and how it
 * routes.
 *
 * <p>Lives on the stack, so two coils in a player's inventory are two different settings rather
 * than one global mode — which is what lets somebody keep a spool of red signal wire and a spool
 * of aluminium bus and not have to remember which is selected.
 */
public record WireCoil(ConductorMaterial material, DyeColor colour, WireRouter.Mode mode,
                       WireGauge gauge) {

    /** A spool set up the way one always was, for anything that predates the gauge. */
    public WireCoil(ConductorMaterial material, DyeColor colour, WireRouter.Mode mode) {
        this(material, colour, mode, WireGauge.DEFAULT);
    }

    public static final WireCoil DEFAULT =
            new WireCoil(ConductorMaterial.IRON, DyeColor.WHITE, WireRouter.Mode.PATHFIND);

    private static final Codec<WireRouter.Mode> MODE_CODEC = Codec.STRING.xmap(
            name -> WireRouter.Mode.valueOf(name.toUpperCase(Locale.ROOT)),
            mode -> mode.name().toLowerCase(Locale.ROOT));

    public static final Codec<WireCoil> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    WireTrace.MATERIAL_CODEC.fieldOf("material").forGetter(WireCoil::material),
                    DyeColor.CODEC.fieldOf("colour").forGetter(WireCoil::colour),
                    MODE_CODEC.fieldOf("mode").forGetter(WireCoil::mode),
                    // Optional, so a coil in an existing inventory keeps drawing the gauge every
                    // number in the design document is quoted against.
                    WireTrace.GAUGE_CODEC.optionalFieldOf("gauge", WireGauge.DEFAULT)
                            .forGetter(WireCoil::gauge)
            ).apply(instance, WireCoil::new));

    public static final StreamCodec<ByteBuf, WireCoil> STREAM_CODEC = StreamCodec.of(
            (buffer, coil) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, coil.material.ordinal());
                ByteBufCodecs.VAR_INT.encode(buffer, coil.colour.ordinal());
                ByteBufCodecs.VAR_INT.encode(buffer, coil.mode.ordinal());
                ByteBufCodecs.VAR_INT.encode(buffer, coil.gauge.ordinal());
            },
            buffer -> new WireCoil(
                    value(ConductorMaterial.values(), ByteBufCodecs.VAR_INT.decode(buffer)),
                    value(DyeColor.values(), ByteBufCodecs.VAR_INT.decode(buffer)),
                    value(WireRouter.Mode.values(), ByteBufCodecs.VAR_INT.decode(buffer)),
                    value(WireGauge.values(), ByteBufCodecs.VAR_INT.decode(buffer))));

    /** An ordinal off the wire is untrusted input; out of range falls back rather than throws. */
    private static <T> T value(T[] values, int ordinal) {
        return values[Math.floorMod(ordinal, values.length)];
    }

    public WireCoil withColour(DyeColor next) {
        return new WireCoil(material, next, mode);
    }

    public WireCoil withMode(WireRouter.Mode next) {
        return new WireCoil(material, colour, next);
    }

    /** The next colour round the sixteen, so one button cycles them. */
    public WireCoil nextColour() {
        DyeColor[] colours = DyeColor.values();
        return withColour(colours[(colour.ordinal() + 1) % colours.length]);
    }

    /** Flips between routing round obstacles and refusing to. */
    public WireCoil toggleMode() {
        return withMode(mode == WireRouter.Mode.PATHFIND
                ? WireRouter.Mode.STRAIGHT : WireRouter.Mode.PATHFIND);
    }

    /** Where the loose end of the coil is currently held, if anywhere. */
    public record Anchor(BlockPos cell, Direction face, int u, int v) {

        public static final Codec<Anchor> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        BlockPos.CODEC.fieldOf("cell").forGetter(Anchor::cell),
                        Direction.CODEC.fieldOf("face").forGetter(Anchor::face),
                        Codec.INT.fieldOf("u").forGetter(Anchor::u),
                        Codec.INT.fieldOf("v").forGetter(Anchor::v)
                ).apply(instance, Anchor::new));

        public static final StreamCodec<ByteBuf, Anchor> STREAM_CODEC = StreamCodec.of(
                (buffer, anchor) -> {
                    buffer.writeLong(anchor.cell.asLong());
                    ByteBufCodecs.VAR_INT.encode(buffer, anchor.face.ordinal());
                    ByteBufCodecs.VAR_INT.encode(buffer, anchor.u);
                    ByteBufCodecs.VAR_INT.encode(buffer, anchor.v);
                },
                buffer -> new Anchor(BlockPos.of(buffer.readLong()),
                        value(Direction.values(), ByteBufCodecs.VAR_INT.decode(buffer)),
                        Math.clamp(ByteBufCodecs.VAR_INT.decode(buffer), 0, 15),
                        Math.clamp(ByteBufCodecs.VAR_INT.decode(buffer), 0, 15)));
    }
}
