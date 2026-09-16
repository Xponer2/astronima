package play.xponer.astronima.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModBlocks;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.gravity.LocomotionAids;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * The two answers to a body that will not stay put.
 *
 * <p>Boots stop you <em>leaving</em>; rails stop you <em>travelling</em>. They are separate
 * classes of problem with separate answers, which is why there are two aids rather than one
 * with a bigger number — see {@code design/microgravity.md} §M4.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class LocomotionAidEvents {

    private static final Identifier GRIP_GRAVITY_ID =
            Identifier.fromNamespaceAndPath(Astronima.MODID, "boot_grip");
    private static final Identifier GRIP_SPEED_ID =
            Identifier.fromNamespaceAndPath(Astronima.MODID, "boot_drag");
    /** Same reasoning as {@code AsteroidGravity}'s own — see that class. */
    private static final double VALUE_EPSILON = 1.0e-3;

    /** And it costs what magnetic soles cost in reality: every step is peeling a magnet off. */
    private static final AttributeModifier GRIP_DRAG = new AttributeModifier(
            GRIP_SPEED_ID, LocomotionAids.BOOT_SPEED_FACTOR - 1.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /**
     * How far below a magnetic surface still counts as "close enough to hold you," in blocks —
     * sized to bridge an ordinary one-block step, not to any real property of a magnet. Found
     * live: an exact "touching" requirement (the original {@code standingOnMetal}) meant a player
     * in magnetic boots lost their grip for the whole real-time duration of a routine step, which
     * under this dimension's own weak gravity is well over half a second (see
     * {@code GroundedTracker}'s own note on why coyote time alone was not enough) — reported as
     * still being yanked into zero-g on every ordinary step even wearing the one item whose entire
     * point is staying attached to the deck.
     */
    public static final int MAGNETIC_RANGE = 6;

    @SubscribeEvent
    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean gripping = LocomotionAids.bootsGrip(bootsWorn(player), magneticSurfaceNearby(player));
        applyGrip(player, gripping);
        apply(player.getAttribute(Attributes.MOVEMENT_SPEED), GRIP_DRAG, GRIP_SPEED_ID, gripping);
    }

    /**
     * Whether a ferrous surface is within {@link #MAGNETIC_RANGE} blocks straight down — not
     * "touching." A real magnetic boot's whole point is that it does not let go the instant
     * contact breaks for a single tick; {@code GroundedTracker} reads this same fact to keep a
     * booted player out of zero-g through an ordinary step, and this method's own grip logic
     * reads it to keep restoring full local gravity (rather than this dimension's own weak one)
     * through that same step — the "pulled toward the deck a little harder" a magnet should
     * actually feel like, reusing {@link #applyGrip}'s already-correct cancellation rather than
     * inventing a second downward force alongside it.
     */
    public static boolean magneticSurfaceNearby(Player player) {
        Level level = player.level();
        BlockPos base = player.blockPosition();
        for (int dy = 1; dy <= MAGNETIC_RANGE; dy++) {
            if (isFerrous(level.getBlockState(base.below(dy)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether any of the asteroid's own native rock is within {@link #MAGNETIC_RANGE} blocks
     * straight down — broader than {@link #isFerrous}/{@link #magneticSurfaceNearby} on direct
     * request: this body's whole fiction is iron-nickel throughout (the ore chain's own raw
     * material, not only its worked output), so a booted player should not fall out of that grip
     * onto bare asteroid rock the way falling off a worked hull plate onto raw ore would otherwise
     * read as. Deliberately used only by {@code GroundedTracker}'s own zero-g gate, not by this
     * class's own grip-speed mechanic above: the walking speed penalty was balanced against worked,
     * flat plating specifically, and broadening what counts as "gripped" for that purpose was never
     * asked for and would quietly change survival balance on every natural surface in the
     * dimension.
     */
    public static boolean isAsteroidRock(BlockState state) {
        return state.is(ModBlocks.ASTEROID_ROCK.get())
                || state.is(ModBlocks.REGOLITH.get())
                || state.is(ModBlocks.CHLORATE_ORE.get())
                || state.is(ModBlocks.METAL_RICH_ORE.get())
                || state.is(ModBlocks.HEMATITE_ORE.get())
                || state.is(ModBlocks.ILMENITE_ORE.get())
                || isFerrous(state);
    }

    /** As {@link #magneticSurfaceNearby}, but counting any of the asteroid's own native rock
     *  ({@link #isAsteroidRock}), not only worked plating. */
    public static boolean asteroidRockNearby(Player player) {
        Level level = player.level();
        BlockPos base = player.blockPosition();
        for (int dy = 1; dy <= MAGNETIC_RANGE; dy++) {
            if (isAsteroidRock(level.getBlockState(base.below(dy)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * While the soles are clamped, the deck holds you the way a floor is supposed to.
     *
     * <p>Restores the gravity {@code AsteroidGravity} takes away rather than adding a force of
     * its own: the point of standing on a magnetic deck is that it feels ordinary, and a second
     * downward push layered on a reduced one would feel like neither.
     *
     * <p><strong>Position-dependent, deliberately built here rather than as a constant.</strong>
     * {@code AsteroidGravity} no longer reduces gravity by one fixed fraction everywhere — see
     * {@link Microgravity#gravityMultiplierAt} — so cancelling it exactly requires reading that
     * same function at this player's own position, in this same tick
     * ({@code design/asteroid-body-b2.md} §1.2's cancellation identity). A grip modifier built
     * from the old flat {@code GRAVITY_MULTIPLIER} would only be exactly right at the centre and
     * would under- or over-correct everywhere else.
     */
    private static void applyGrip(ServerPlayer player, boolean wanted) {
        AttributeInstance attribute = player.getAttribute(Attributes.GRAVITY);
        if (attribute == null) {
            return;
        }
        AttributeModifier applied = attribute.getModifier(GRIP_GRAVITY_ID);
        if (!wanted) {
            if (applied != null) {
                attribute.removeModifier(GRIP_GRAVITY_ID);
            }
            return;
        }
        double r = Math.hypot(player.getX(), player.getZ());
        double localGravity = play.xponer.astronima.sim.gravity.Microgravity.gravityMultiplierAt(r);
        double amount = 1.0 / localGravity - 1.0;
        if (applied != null && Math.abs(applied.amount() - amount) < VALUE_EPSILON) {
            return; // already close enough - no resync needed
        }
        if (applied != null) {
            attribute.removeModifier(GRIP_GRAVITY_ID);
        }
        attribute.addTransientModifier(new AttributeModifier(
                GRIP_GRAVITY_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /**
     * Adds or removes a modifier to match the state, and does nothing when it already matches.
     *
     * <p>Checked every tick rather than on the events that change the state, for the reason
     * {@code AsteroidGravity} gives: a modifier applied by an event a player can miss is a
     * modifier that eventually goes missing, and the symptom is a player who is mysteriously
     * slow in a world where nobody else is.
     */
    private static void apply(AttributeInstance attribute, AttributeModifier modifier,
                              Identifier id, boolean wanted) {
        if (attribute == null) {
            return;
        }
        boolean present = attribute.getModifier(id) != null;
        if (wanted && !present) {
            attribute.addTransientModifier(modifier);
        } else if (!wanted && present) {
            attribute.removeModifier(id);
        }
    }

    private static boolean bootsWorn(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.isEquipped(ModItems.MAGNETIC_BOOTS.get()))
                .orElse(false);
    }

    /** The worked-metal blocks a magnet has anything to hold on to. */
    public static boolean isFerrous(BlockState state) {
        return state.is(ModBlocks.HULL_PLATE.get())
                || state.is(ModBlocks.INSULATED_HULL_PLATE.get())
                || state.is(ModBlocks.GRAB_RAIL.get());
    }

    /** True when this player has magnetic boots fitted, for the grip indicator. */
    public static boolean hasBoots(Player player) {
        return bootsWorn(player);
    }

    /** True when a stack is a pair of magnetic boots — for anything iterating a loadout. */
    public static boolean isBoots(ItemStack stack) {
        return stack.is(ModItems.MAGNETIC_BOOTS.get());
    }

    private LocomotionAidEvents() {}
}
