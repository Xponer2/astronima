package play.xponer.astronima.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import play.xponer.astronima.registry.ModItems;

/**
 * Portable compressed oxygen.
 *
 * <p>Sizing: one tank holds {@value #TANK_O2_MOL} mol of O2 — at the game's 60×
 * metabolism that is about 11 minutes of sealed time, tracked through item damage.
 *
 * <p>Gas is drawn only from the tank <em>fitted to the suit</em>, never from one
 * carried in a pocket. That is a deliberate design constraint rather than an
 * oversight: a loadout you can hot-swap from your inventory mid-EVA has no tension in
 * it, and a real tank is plumbed to a regulator, not held to your face. Spares are
 * still worth carrying — you just have to stop and fit one.
 *
 * @see SuitLifeSupport for the breathing loop itself
 */
public final class OxygenTanks {
    public static final double TANK_O2_MOL = 12.0;

    /**
     * The ppO2 a <em>habitat</em> must be left holding after a fill, kPa.
     *
     * <p>Applies to rooms and to rooms only, and the asymmetry is the point. Draining the
     * air you are standing in to fill a bottle you then breathe is a round trip that gains
     * nothing and can kill you halfway through, so a room refuses before it becomes
     * unbreathable. A pressure vessel has no such floor: a bank exists to be drawn down,
     * and refusing to empty one would be protecting the player from the thing they built it
     * for.
     */
    public static final double MIN_ROOM_PPO2_AFTER_FILL = 12.0;
    public static final int TICKS_PER_DAMAGE_UNIT = 12;
    /** ceil(TANK_O2_MOL / (per-tick O2 draw at 60×) / TICKS_PER_DAMAGE_UNIT). */
    public static final int TANK_MAX_DAMAGE = 1096;

    /** True when a fitted tank still has gas (drives N2 washout, §v0.25). */
    public static boolean hasFilledTank(ServerPlayer player) {
        return isUsable(SuitLoadout.tank(player));
    }

    /** Fraction of gas left in the fitted tank, or -1 when none is fitted. */
    public static float fittedTankFraction(LivingEntity wearer) {
        ItemStack tank = SuitLoadout.tank(wearer);
        if (!tank.is(ModItems.OXYGEN_TANK.get())) {
            return -1f;
        }
        return 1f - (float) tank.getDamageValue() / tank.getMaxDamage();
    }

    /** Fraction of scrubbing left in the fitted cartridge, or -1 when none is fitted. */
    public static float fittedCartridgeFraction(LivingEntity wearer) {
        ItemStack cartridge = SuitLoadout.cartridge(wearer);
        if (!cartridge.is(ModItems.LITHIUM_HYDROXIDE_CARTRIDGE.get())) {
            return -1f;
        }
        return 1f - (float) cartridge.getDamageValue() / cartridge.getMaxDamage();
    }

    static boolean isUsable(ItemStack tank) {
        return tank.is(ModItems.OXYGEN_TANK.get()) && tank.getDamageValue() < tank.getMaxDamage();
    }

    private OxygenTanks() {}
}
