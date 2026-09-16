package play.xponer.astronima.wire;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.item.LogicPartItem;
import play.xponer.astronima.item.WireCoilItem;
import play.xponer.astronima.item.WireCutterItem;
import play.xponer.astronima.item.WireRibbonItem;
import play.xponer.astronima.item.WireSnipsItem;
import play.xponer.astronima.item.WrenchItem;
import play.xponer.astronima.registry.ModItems;
import play.xponer.astronima.sim.logic.PartType;

/**
 * How the wire layer gets a right click at all, and what a part comes back as.
 *
 * <p><strong>Losing your support is deliberately not handled here.</strong> Wire is fastened to a
 * block, not floating in a cell, so a trace whose block stops existing has to come down — but
 * this event fires for a player's pickaxe and for nothing else. An explosion, a piston, a
 * {@code /setblock} and another mod's machine all remove blocks silently, and a run left hanging
 * on nothing would keep conducting with no way to take it down. {@code WireTicker} checks each
 * trace's and each part's own support instead, which catches every one of them, and one mechanism
 * cannot disagree with itself (PLAN rules 20 and 24).
 *
 * <p><strong>The distinction that makes the whole design work survives intact:</strong> wire
 * merely <em>passing through</em> a broken block's cell is untouched, because it was never
 * attached to it. So a player can wall over a run and later take the wall down and find their
 * wiring intact, while breaking the surface it is stapled to takes it away. Two very similar
 * player actions, two different outcomes, and both are the ones a real installation would give.
 */
@EventBusSubscriber(modid = Astronima.MODID)
public final class WireEvents {

    /** The part, back in the hand it came from — with a plate's circuit, and its name, still on it. */
    public static ItemStack asItem(WirePart part) {
        ItemStack stack = new ItemStack(ModItems.part(part.type()));
        if ((part.type() == PartType.PLATE || part.type() == PartType.MACRO_PLATE) && !part.circuit().isBlank()) {
            stack.set(play.xponer.astronima.registry.ModDataComponents.CIRCUIT.get(),
                    part.circuit());
        }
        if (!part.name().isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(part.name()));
        }
        return stack;
    }

    /**
     * A wiring tool always wins over the block it is pointed at — and so does a part.
     *
     * <p>Vanilla gives the <em>block</em> the first refusal on a right-click when the player is
     * not sneaking, so pointing a coil at a crusher opened the crusher's menu and the wire was
     * never laid — reported as <em>"на некоторые машины нельзя положить провод, у них есть
     * интерфейс и он тупо включается"</em>. Which is the worst possible case, because those are
     * exactly the blocks you most want to wire.
     *
     * <p>The same problem arrives with parts, one level finer: a switch bolted to a machine's
     * casing is a control the machine would swallow every click of. So the block is denied
     * <strong>only when a part actually covers the pixel being pointed at</strong> — everywhere
     * else on that same face the machine still opens as it always did.
     */
    @SubscribeEvent
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof WireCoilItem
                || event.getItemStack().getItem() instanceof WireCutterItem
                || event.getItemStack().getItem() instanceof WireRibbonItem
                || event.getItemStack().getItem() instanceof WireSnipsItem
                || event.getItemStack().getItem() instanceof LogicPartItem) {
            event.setUseBlock(net.minecraft.util.TriState.FALSE);
            return;
        }
        Level level = event.getLevel();
        BlockHitResult hit = event.getHitVec();
        if (hit == null) {
            return;
        }
        WirePart part = PartInteraction.under(level, hit);
        if (part == null) {
            return;
        }
        event.setUseBlock(net.minecraft.util.TriState.FALSE);
        // Consumed, and that is not a formality. `Minecraft.startUseItem` walks BOTH hands and
        // stops only on an InteractionResult.Success — so cancelling with the default PASS made
        // the client try the off hand and send a *second* packet, the server fired this handler
        // again, and every switch toggled twice per click. Reported as "нажимаю ПКМ по свичу,
        // постоянно пишет open": it really did close, and then immediately reopen.
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return; // the off hand still denies the block, but acts on nothing
        }
        boolean wrench = event.getItemStack().getItem() instanceof WrenchItem;
        if (level instanceof ServerLevel serverLevel) {
            if (wrench) {
                PartInteraction.turn(serverLevel, event.getEntity(), part);
            } else {
                PartInteraction.operate(serverLevel, event.getEntity(), part);
            }
            return;
        }
        // The plate's editor is a screen, so it opens on the client — with the part's address, so
        // the edit lands on the plate on the wall rather than on whatever is in the hand.
        if (!wrench && (part.type() == PartType.PLATE || part.type() == PartType.MACRO_PLATE)) {
            play.xponer.astronima.client.CircuitEditorOpener.open(part.circuit(),
                    new play.xponer.astronima.network.CircuitPlatePayload.At(
                            part.cell(), part.face(), part.u(), part.v()), part.type(), part.name());
        }
        // Likewise the processor's own code editor.
        if (!wrench && part.type() == PartType.PROCESSOR) {
            play.xponer.astronima.client.ProcessorEditorOpener.open(part.program(),
                    new play.xponer.astronima.network.ProcessorProgramPayload.At(
                            part.cell(), part.face(), part.u(), part.v()));
        }
    }

    /**
     * Left click with a coil: let go of the end, and <strong>never break anything</strong>.
     *
     * <p>A coil is held for minutes at a time while pointing at the walls being wired, so the
     * ordinary meaning of left click — chew through the block — is exactly wrong for it. The
     * event is cancelled outright, which also stops the swing animation and the crack overlay,
     * rather than letting the break start and undoing it.
     */
    @SubscribeEvent
    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getItemStack().getItem() instanceof WireCoilItem) {
            event.setCanceled(true);
            if (!event.getLevel().isClientSide()) {
                WireCoilItem.letGoOf(event.getItemStack(), event.getEntity());
            }
            return;
        }
        if (event.getItemStack().getItem() instanceof WireRibbonItem) {
            event.setCanceled(true);
            if (!event.getLevel().isClientSide()) {
                WireRibbonItem.letGoOf(event.getItemStack(), event.getEntity());
            }
            return;
        }
        if (event.getItemStack().getItem() instanceof WireSnipsItem) {
            event.setCanceled(true);
            if (!event.getLevel().isClientSide()) {
                WireSnipsItem.letGoOf(event.getItemStack(), event.getEntity());
            }
        }
    }

    // Swinging at nothing is deliberately not handled: LeftClickEmpty never reaches the
    // server, so it would need a packet of its own to do anything, and a player wiring a wall
    // is by definition pointing at one.

    private WireEvents() {}
}
