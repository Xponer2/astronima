package play.xponer.astronima.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * The tube's own live orientation for one frame — read from whichever source is actually honest
 * for the telescope right now: an occupied mount's own current rotation while someone is aiming
 * it, its saved aim while idle, or its neutral rest angle if it has never been aimed at all
 * (design/astra-telescope.md §6.4/§8a). Two floats, not a reference to the mount entity itself:
 * {@code extractRenderState} runs once per frame and the mount is ephemeral (it may not even
 * exist right now), so the state has to carry the numbers forward on its own.
 */
public class TelescopeRenderState extends BlockEntityRenderState {
    public float yawDegrees;
    public float pitchDegrees;
}
