package play.xponer.astronima.wire;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import play.xponer.astronima.sim.logic.PartType;
import play.xponer.astronima.sim.wire.WirePixel;

import java.util.Locale;

/**
 * Operating a part that is mounted in the wire layer.
 *
 * <p>A part is not a block, so nothing in the ordinary interaction pipeline reaches it: vanilla
 * asks the block under the crosshair and the block under the crosshair is a wall. So the right
 * click is intercepted (see {@code WireEvents}) and resolved against the wire layer instead.
 *
 * <p><strong>Only when something is really there.</strong> The interception is per-pixel: if no
 * part covers the pixel being pointed at, the block gets its click exactly as before. A machine
 * with a part stuck on its casing still opens its menu everywhere except on the part.
 */
public final class PartInteraction {

    /** How long a button stays down, in ticks — three quarters of a second. */
    public static final int HELD_TICKS = 15;

    /**
     * How far off a part you may aim and still be pointing at it, in pixels.
     *
     * <p>Two, which makes the smallest part in the set — a three-pixel switch — a seven-pixel
     * target, and leaves well over half of every face still belonging to the block behind it. That
     * balance is the whole design of this snap: a switch bolted to a machine has to be clickable
     * <em>and</em> the machine has to still open everywhere else on the same panel.
     */
    public static final int SNAP = 2;

    /** Whichever part the player is pointing at, or null when the click belongs to the block. */
    public static @Nullable WirePart under(net.minecraft.world.level.Level level,
                                           BlockHitResult hit) {
        BlockPos cell = hit.getBlockPos().relative(hit.getDirection());
        Direction face = hit.getDirection().getOpposite();
        WirePixel aimed = WireAim.exact(hit, cell, face);

        // The nearest part, not the one under the exact pixel. Asking for the exact pixel is the
        // mistake WireAim was written to end — see WirePart.distanceTo — and it is why the switch
        // was reported as simply not working: it is three pixels across.
        WirePart best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (WirePart part : Wires.partsOn(level, cell, face)) {
            int distance = part.distanceTo(aimed.u(), aimed.v());
            // Ties broken by position, never by iteration order: two parts equally near must
            // resolve the same way for every player and across every relog (rule 19).
            if (distance <= SNAP && (distance < bestDistance
                    || (distance == bestDistance && earlier(part, best)))) {
                best = part;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A total order on two parts of one face, so an equal-distance tie has one answer. */
    private static boolean earlier(WirePart part, @Nullable WirePart than) {
        return than == null || part.v() < than.v()
                || (part.v() == than.v() && part.u() < than.u());
    }

    /**
     * The server half of a click on a part.
     *
     * @return true when the click was consumed, so the block underneath must not also act
     */
    public static boolean operate(ServerLevel level, Player player, WirePart part) {
        if (!once(level, part)) {
            return true;
        }
        return switch (part.type()) {
            case SWITCH -> {
                boolean closed = !part.held();
                Wires.mount(level, sourceUpdate(part.withHeld(closed, 0L)));
                level.playSound(null, part.cell(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                        0.4f, closed ? 0.9f : 0.6f);
                announce(player, part, closed ? "closed" : "open");
                yield true;
            }
            // A lever, and the only part in the set whose two states a player and the world can
            // both set. Throw it off to work on a circuit; throw it back on when the fault is
            // fixed. Nothing here asks whether it is — the next settlement does that, and trips it
            // again if you were wrong, which is the only way that lesson lands.
            case BREAKER -> {
                boolean off = !part.held();
                Wires.mount(level, part.withHeld(off, 0L));
                level.playSound(null, part.cell(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                        0.5f, off ? 0.6f : 1.1f);
                announce(player, part, off ? "OFF - the circuit is dead" : "on");
                yield true;
            }
            case BUTTON -> {
                if (!part.held()) {
                    WirePart pressed = part.withHeld(true, level.getGameTime() + HELD_TICKS);
                    Wires.mount(level, sourceUpdate(pressed));
                    level.playSound(null, part.cell(), SoundEvents.STONE_BUTTON_CLICK_ON,
                            SoundSource.BLOCKS, 0.4f, 0.9f);
                }
                yield true;
            }
            // The plate's editor opens on the client; the server has nothing to do but keep the
            // wall from opening a menu instead.
            case PLATE -> true;
            // Likewise the processor's code editor.
            case PROCESSOR -> true;
            default -> {
                // A gate has nothing to operate, so the click is used to *say what it is*. A
                // five-pixel housing cannot carry a label, and "what have I actually bolted to
                // this wall" is the question a player asks constantly once there are three of
                // them (rule 9: if the state changes, the block changes — and if it cannot be
                // drawn large enough to read, it has to be askable).
                announce(player, part, part.drivingAnything() ? "driving" : "dead");
                yield true;
            }
        };
    }

    /**
     * A switch or button is a pure source — it reads no pad, so unlike a gate it can never be part
     * of a feedback loop and never needed {@link PartLogic}'s cached-read fix for one. Writing its
     * {@code outputs} bit here, at the moment {@code held} changes, is what makes the click land
     * immediately instead of waiting for {@code WireTicker}'s next five-tick refresh — which is
     * where every other part's cached output legitimately comes from, and still does.
     *
     * <p>Without this a click set {@code held} correctly but left {@code outputs} stale, and
     * {@link PartLogic#isDriving} — deliberately reading the cached bit rather than recursing, so a
     * real feedback loop resolves instead of hitting {@code WireSignal#MAX_DEPTH} — read exactly
     * that stale bit. The switch itself reported closed; everything downstream still saw open.
     */
    public static WirePart sourceUpdate(WirePart part) {
        return part.withOutputs(part.type().evaluate(new boolean[0], part.held(), part.circuit(), part.memory()));
    }

    /**
     * Whether this part has already been acted on this tick.
     *
     * <p><strong>One click, one change.</strong> The interaction event is raised once per hand and
     * the client walks both of them, so a handler that acts every time it is called toggles a
     * switch twice per click and leaves it exactly where it started — which is what shipped, and
     * what a player sees as <em>"нажимаю и постоянно пишет open"</em>. The event side of that is
     * fixed by consuming the interaction; this is the belt to that pair of braces, because the
     * failure is silent and there are more ways to be called twice than there are ways to notice.
     *
     * <p>A human cannot click a thing twice inside one twentieth of a second, so nothing legitimate
     * is refused. One slot and one tick is all that is remembered — a map here would grow for the
     * lifetime of the server to defend against something that lasts one tick.
     */
    private static boolean once(ServerLevel level, WirePart part) {
        long now = level.getGameTime();
        if (lastOperated != null && lastOperatedAt == now && lastOperated.sameSlotAs(part)) {
            return false;
        }
        lastOperated = part;
        lastOperatedAt = now;
        return true;
    }

    private static @Nullable WirePart lastOperated;
    private static long lastOperatedAt = Long.MIN_VALUE;

    /**
     * Turning a part with the wrench.
     *
     * <p>Rule 22 made the wrench the mod's one "turn this" verb, with a named predicate for what
     * it refuses. A wire part is a single component whose orientation is nobody else's business —
     * the exact case that predicate was written to allow — so it turns, and it turns nothing that
     * would not fit afterwards.
     */
    public static boolean turn(ServerLevel level, Player player, WirePart part) {
        if (!once(level, part)) {
            return true;    // twice in one tick is one click arriving twice: 180 degrees, not 90
        }
        WirePart turned = part.turned();
        if (turned.equals(part)) {
            say(player, Component.translatable("astronima.part.no_room"));
            return true;
        }
        Wires.mount(level, turned);
        level.playSound(null, part.cell(), SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                SoundSource.BLOCKS, 0.6f, 1.0f);
        return true;
    }

    /** What a part is and what it is doing, on the action bar. */
    public static void announce(Player player, WirePart part, String state) {
        say(player, Component.literal(name(part.type()) + " - " + state)
                .withStyle(ChatFormatting.AQUA));
    }

    private static String name(PartType type) {
        return type.isGate() ? type.gate().label() + " gate"
                : type.name().toLowerCase(Locale.ROOT);
    }

    private static void say(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }

    private PartInteraction() {}
}
