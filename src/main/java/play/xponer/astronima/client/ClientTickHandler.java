package play.xponer.astronima.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import play.xponer.astronima.Astronima;

/** Opens the mod's screens from their key bindings. */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class ClientTickHandler {
    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        while (ModKeybinds.HUD_PAGE.consumeClick()) {
            // Only ever does anything when something did not fit. A readout that is missing
            // without saying so is worse than one a keypress away, and this is the keypress.
            play.xponer.astronima.client.hud.HudPanels.nextPage();
        }
        while (ModKeybinds.OPEN_BIOMONITOR.consumeClick()) {
            minecraft.setScreen(play.xponer.astronima.client.screen.BiomonitorUi.screen(minecraft.player));
        }
        while (ModKeybinds.OPEN_CODEX.consumeClick()) {
            minecraft.setScreen(play.xponer.astronima.client.screen.CodexUi.screen(minecraft.player));
        }
        while (ModKeybinds.ROTATE_PART.consumeClick()) {
            // Only when it would mean something. A key that silently does nothing most of the
            // time is a key the player stops believing in.
            if (holdingPart(minecraft)) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                        new play.xponer.astronima.network.PartRotatePayload());
            }
        }
        while (ModKeybinds.TOGGLE_SNAP.consumeClick()) {
            play.xponer.astronima.wire.WireAim.snapEnabled = !play.xponer.astronima.wire.WireAim.snapEnabled;
            boolean enabled = play.xponer.astronima.wire.WireAim.snapEnabled;
            if (minecraft.player != null) {
                net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component
                        .literal("Wire Snapping: " + (enabled ? "ENABLED [On]" : "DISABLED [Off]"))
                        .withStyle(enabled ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED);
                minecraft.player.sendSystemMessage(msg);
            }
        }
        while (ModKeybinds.PUSH_OFF.consumeClick()) {
            attemptPushOff(minecraft);
        }
        while (ModKeybinds.TETHER.consumeClick()) {
            toggleTether(minecraft);
        }
    }

    /**
     * design/eva-mobility.md §3.2: one key, toggled. Already tethered lets go; not tethered fires
     * at whatever solid point is aimed at within {@link play.xponer.astronima.sim.gravity.Tether#RANGE},
     * or does nothing at all on a miss - the same "no penalty for a wrong attempt" shape push-off
     * already uses.
     *
     * <p><strong>Gated on real, literal {@code onGround()} — never {@code GroundedTracker}'s own
     * grace period</strong> — found live: firing worked far from the asteroid but failed close to
     * it, and still failed with no boots worn at all, which ruled out the magnetic-boots extension
     * on its own. The real cause was {@code GroundedTracker}'s coyote-time window itself: pressing
     * this key within its ~250ms of having last touched ground — exactly the moment right after a
     * jump or push-off, when a player is most likely to want a safety line — read as still
     * grounded and silently refused to fire. A discrete, key-triggered action like this one has no
     * business reading a grace period built to smooth *continuous* systems (camera, WASD, orientation)
     * through a momentary flicker; it only ever needs to know whether the player is standing on
     * something right now.
     */
    private static void toggleTether(Minecraft minecraft) {
        var player = minecraft.player;
        if (player.level().dimension() != play.xponer.astronima.registry.ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        var current = player.getData(play.xponer.astronima.registry.ModAttachments.TETHER.get());
        if (current.active()) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                    new play.xponer.astronima.network.TetherReleasePayload());
            return;
        }
        if (play.xponer.astronima.sim.gravity.Microgravity.hasAirControl(player.onGround())) {
            return;
        }
        net.minecraft.world.phys.HitResult hit = player.pick(
                play.xponer.astronima.sim.gravity.Tether.RANGE, 1.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return;
        }
        // PLAN.md rule 105: a raycast hit point sits exactly on the block's own surface, and
        // deriving a block position from it later would round to the wrong voxel depending on
        // which face was hit. Tether#nudgeIntoBlock is the tested fix - see its own doc.
        net.minecraft.core.Direction face = blockHit.getDirection();
        net.minecraft.world.phys.Vec3 hitPoint = hit.getLocation();
        double[] nudged = play.xponer.astronima.sim.gravity.Tether.nudgeIntoBlock(
                hitPoint.x, hitPoint.y, hitPoint.z,
                face.getStepX(), face.getStepY(), face.getStepZ());
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new play.xponer.astronima.network.TetherFirePayload(nudged[0], nudged[1], nudged[2]));
    }

    /**
     * design/eva-mobility.md §2: "no block in range simply does nothing" - a miss sends nothing
     * and costs nothing, so this only ever does work on a real hit.
     *
     * <p>Gated on real, literal {@code onGround()}, same reasoning as {@link #toggleTether}'s own
     * note: a discrete key press has no business reading {@code GroundedTracker}'s own grace
     * period, which exists to smooth continuous systems through a momentary flicker, not to decide
     * whether a deliberate action is currently allowed.
     */
    private static void attemptPushOff(Minecraft minecraft) {
        var player = minecraft.player;
        if (player.level().dimension() != play.xponer.astronima.registry.ModDimensions.ASTEROID_LEVEL) {
            return;
        }
        if (play.xponer.astronima.sim.gravity.Microgravity.hasAirControl(player.onGround())) {
            return;
        }
        net.minecraft.world.phys.HitResult hit =
                player.pick(player.blockInteractionRange(), 1.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return;
        }
        net.minecraft.core.Direction face = blockHit.getDirection();
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new play.xponer.astronima.network.PushOffPayload(
                        face.getStepX(), face.getStepY(), face.getStepZ()));
    }

    private static boolean holdingPart(Minecraft minecraft) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            if (minecraft.player.getItemInHand(hand).getItem()
                    instanceof play.xponer.astronima.item.LogicPartItem) {
                return true;
            }
        }
        return false;
    }

    private ClientTickHandler() {}
}
