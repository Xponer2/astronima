package play.xponer.astronima.client.gravity;

import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import play.xponer.astronima.Astronima;
import play.xponer.astronima.registry.ModDimensions;
import play.xponer.astronima.sim.gravity.Microgravity;
import play.xponer.astronima.sim.gravity.Orientation;

/**
 * design/eva-mobility.md §1.4: the model itself rolls, for every observer, not only in the
 * viewer's own imagination. Confirmed against this project's own real, resolved
 * {@code neoforge-26.1.2.82-universal.jar} (rule 2 — a fresh check, not the stale NeoForge cache
 * snapshot an earlier pass through this design mistakenly trusted): 1.26's player rendering is no
 * longer {@code PlayerRenderer}/{@code PlayerRenderState} at all — it is {@code Avatar} /
 * {@code AvatarRenderState}, and rendering carries a render <em>state</em>, never a live entity
 * reference, by design (the submit phase is deliberately decoupled from the entity it describes).
 *
 * <p><strong>Only stashes the fact here — {@code mixin/OrientationModelTiltMixin} is what actually
 * applies it, and that split is deliberate, not incidental:</strong>
 * {@link RegisterRenderStateModifiersEvent#registerAvatarEntityModifier} (mod-bus, fired once at
 * startup) lets NeoForge call this modifier during state <em>extraction</em>, while a live
 * {@code Avatar} is still in hand, to stash arbitrary data onto the state via
 * {@code BaseRenderState#setRenderData} — the actual mechanism {@code IRenderStateExtension} exists
 * for. This class stops there. The first attempt also consumed the value here, via
 * {@code RenderLivingEvent.Pre}/{@code Post} wrapping the <em>entire</em> vanilla render in one more
 * {@code poseStack.mulPose} — found wrong live, reported as pitch input reading as roll on the
 * model and the model facing backwards at ordinary facings: that wrapper sits <em>outside</em>
 * vanilla's own {@code LivingEntityRenderer#setupRotations}, which applies the entity's real body
 * yaw nested <em>inside</em> it, and a world-space wrapper rotation does not commute with a
 * local-space nested one — the two only agreed at the one facing this system's own "yaw stripped"
 * reference happened to coincide with. {@code OrientationModelTiltMixin} now injects the same
 * stashed value directly into {@code setupRotations}, right after vanilla's own body-yaw
 * {@code mulPose}, so it nests correctly inside real yaw instead of wrapping outside it.
 *
 * <p><strong>Also zeroes {@code state.xRot} while airborne</strong> — found live, reported as the
 * head pointing nowhere near this player's real look direction: {@code HumanoidModel#setupAnim}
 * (confirmed against this project's own decompiled source, rule 2) independently sets
 * {@code this.head.xRot = state.xRot * d2r} — vanilla's own separate head-look-up/down animation,
 * completely unaware of {@code OrientationModelTiltMixin}'s own body-level rotation, which already
 * carries this player's real pitch. Left alone, pitch was being applied twice: once to the whole
 * body (this system's own, correct), and again to the head alone (vanilla's own, now redundant).
 * {@code state.yRot} — the head's own small look-left/right offset relative to the body — is left
 * untouched: it is orthogonal to pitch and this system never applies any yaw of its own at the body
 * level (see {@code OrientationTarget#forModel}'s own "yaw stripped" note), so nothing here
 * conflicts with it.
 */
@EventBusSubscriber(modid = Astronima.MODID, value = Dist.CLIENT)
public final class OrientationRenderEvents {

    /** Also read by {@code mixin/OrientationModelTiltMixin} — the extraction-time stash and the
     *  render-time consumer must agree on the exact same key. */
    public static final ContextKey<Orientation> ORIENTATION_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Astronima.MODID, "orientation"));

    @SubscribeEvent
    private static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                if (avatar.level().dimension() != ModDimensions.ASTEROID_LEVEL) {
                    return;
                }
                // Smoothed here, once per frame per avatar (every observer, not only the local
                // player) - the same landing-transition and stepped-roll fix §1.3 already needed,
                // generalised: this render state's own id is real and stable per avatar per frame.
                Orientation target = OrientationTarget.forModel(avatar);
                Orientation smoothed = OrientationSmoothing.smoothedToward(
                        avatar.getId(), OrientationSmoothing.Purpose.MODEL, target);
                state.setRenderData(ORIENTATION_KEY, smoothed);
                if (!Microgravity.hasAirControl(play.xponer.astronima.gravity.GroundedTracker.isEffectivelyGrounded(avatar))) {
                    state.xRot = 0f;
                }
            }
        });
    }

    private OrientationRenderEvents() {}
}
