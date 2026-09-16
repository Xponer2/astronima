package play.xponer.astronima.compat.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.block.AlarmBlock;
import play.xponer.astronima.block.GasPumpBlock;
import play.xponer.astronima.block.GasTankBlock;
import play.xponer.astronima.block.OxygenCandleBlock;
import play.xponer.astronima.block.ScrubberBlock;

/**
 * Look-at readouts, and a deliberate limit on what they are allowed to say.
 *
 * <p>Rule 7 of the architecture — every hazard ships with the instrument that reveals
 * it — is the reason the gas analyzer exists. A look-at overlay that simply printed
 * oxygen, pressure and temperature on every block would delete that instrument and
 * with it the whole reason to build one, so this plugin does not do that.
 *
 * <p>The line it draws is the honest one: <strong>a machine may report on itself</strong>,
 * because a scrubber with a cartridge in it really does have a gauge on the front and
 * you really can see how far a candle has burned. <strong>The atmosphere may not</strong>,
 * because reading a gas mixture takes an instrument — so those lines appear only when
 * the analyzer is actually in your hand, where Jade becomes a better display for a tool
 * you already earned rather than a replacement for it.
 *
 * <p>Loaded by annotation scanning, so Jade being absent costs nothing: the class is
 * never touched and the mod behaves identically.
 */
@WailaPlugin(Astronima.MODID)
public class AstronimaJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        // Machine internals live in block entities that are not synced to the client,
        // so the server has to hand them over before anything can be drawn.
        registration.registerBlockDataProvider(MachineDataProvider.INSTANCE, ScrubberBlock.class);
        registration.registerBlockDataProvider(MachineDataProvider.INSTANCE, OxygenCandleBlock.class);
        registration.registerBlockDataProvider(MachineDataProvider.INSTANCE, GasPumpBlock.class);
        registration.registerBlockDataProvider(MachineDataProvider.INSTANCE, GasTankBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MachineStateProvider.INSTANCE, ScrubberBlock.class);
        registration.registerBlockComponent(MachineStateProvider.INSTANCE, OxygenCandleBlock.class);
        registration.registerBlockComponent(MachineStateProvider.INSTANCE, AlarmBlock.class);
        registration.registerBlockComponent(MachineStateProvider.INSTANCE, GasPumpBlock.class);
        registration.registerBlockComponent(MachineStateProvider.INSTANCE, GasTankBlock.class);

        // Atmosphere, gated on holding the analyzer — see the class comment.
        registration.registerBlockComponent(AtmosphereProvider.INSTANCE,
                net.minecraft.world.level.block.Block.class);
    }
}
