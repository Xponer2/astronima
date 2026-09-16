package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.atmosphere.Atmosphere;
import play.xponer.astronima.registry.ModBlockEntities;
import play.xponer.astronima.sim.Gas;
import play.xponer.astronima.sim.RoomState;
import play.xponer.astronima.sim.Scrubber;

/**
 * Absorption model: LiOH binds CO2 at a rate proportional to the CO2 partial pressure
 * around it (first-order in concentration, like a real packed bed), so the scrubber
 * naturally settles the room at an equilibrium where absorption matches production.
 * One cartridge (~2 kg LiOH) binds ~40 mol of CO2, then must be replaced.
 */
public class ScrubberBlockEntity extends ReadableBlockEntity {
    /**
     * CO2 bound per cartridge, mol. Kept as an alias of the model's own constant so
     * existing callers still read, but {@link Scrubber} is the authority — the gauge reads
     * it too, and two copies of a threshold is how a readout starts disagreeing with the
     * machine it describes.
     */
    public static final double CARTRIDGE_CAPACITY_MOL = Scrubber.CARTRIDGE_CAPACITY_MOL;

    private double capacityLeftMol;

    public ScrubberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCRUBBER.get(), pos, state);
    }

    public void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)
                || capacityLeftMol <= 0
                || level.getGameTime() % Atmosphere.TICK_INTERVAL != 0) {
            return;
        }
        RoomState room = Atmosphere.get(serverLevel).roomTouching(pos);
        if (room == null) {
            return;
        }
        double ppCO2 = room.partialPressureKPa(Gas.CARBON_DIOXIDE);
        double want = Scrubber.absorbedMol(ppCO2, Atmosphere.TICK_INTERVAL / 20.0);
        double absorbed = room.removeGas(Gas.CARBON_DIOXIDE, Math.min(want, capacityLeftMol));
        if (absorbed > 0) {
            capacityLeftMol -= absorbed;
            setChanged();
        }
    }

    /**
     * Absorbent remaining, 0..1 — what the gauge on the front reads.
     *
     * <p>It said "would read" for months and nothing drew it: the scrubber's only readout
     * was a chat line you had to click for, which rule 9 does not accept and which scrolls
     * away the moment anything else happens. It is a real gauge now, on the block and under
     * the crosshair, and the block no longer answers a click at all — there is nothing on a
     * scrubber to set.
     */
    public float chargeFraction() {
        return (float) Scrubber.chargeFraction(capacityLeftMol);
    }

    public void preloadCartridge() {
        capacityLeftMol = CARTRIDGE_CAPACITY_MOL;
        setChanged();
    }

    /** Loads a fresh cartridge if the current one is (nearly) spent. */
    /**
     * Loads a cartridge, honouring how much of it is left. A cartridge half spent in
     * a suit is worth half a bay here — otherwise the scrubber would launder used
     * cartridges back to full and the suit's consumable would cost nothing.
     *
     * @param remainingFraction how much absorbent the cartridge still holds, 0 to 1
     */
    public boolean tryLoadCartridge(Player player, double remainingFraction) {
        if (!Scrubber.needsSwap(capacityLeftMol)) {
            say(player, "astronima.scrubber.cartridge_still_good");
            return false;
        }
        capacityLeftMol = CARTRIDGE_CAPACITY_MOL * Math.clamp(remainingFraction, 0.0, 1.0);
        setChanged();
        say(player, "astronima.scrubber.cartridge_loaded");
        return true;
    }

    /**
     * Action bar, not chat: a one-off answer to a click (PLAN §0.5.0).
     *
     * <p>How much absorbent is left is <em>not</em> said here — that is the gauge's job and
     * it is on the block and under the crosshair. This says only what the click just did.
     */
    private static void say(Player player, String key) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable(key), true);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("capacity_left_mol", capacityLeftMol);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        capacityLeftMol = input.getDoubleOr("capacity_left_mol", 0.0);
    }
}
