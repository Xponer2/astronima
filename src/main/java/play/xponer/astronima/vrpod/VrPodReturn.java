package play.xponer.astronima.vrpod;

/**
 * Where a player came from before the VR Simulation Pod sent them to the void, and what to give
 * back when they leave it (design/vr-simulation-pod.md §5) — real dimension, real position, real
 * facing, and the three ability flags the pod itself turned on so they can be turned off again
 * exactly as they were, rather than reset to a default that could be wrong for a player who was
 * already flying (an operator in creative, say) before they ever touched the pod. Kept free of
 * Minecraft classes on purpose (rule 1) so its own round-trip is a plain unit test rather than one
 * more thing this project's gametest harness cannot exercise (see that design doc's §6).
 */
public record VrPodReturn(
        String dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean previousFlying,
        boolean previousMayFly,
        boolean previousInstabuild) {
}
