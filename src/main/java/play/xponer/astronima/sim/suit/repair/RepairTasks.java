package play.xponer.astronima.sim.suit.repair;

import play.xponer.astronima.sim.suit.SuitSubsystem;

/** Picks the procedure that matches the physical work a subsystem actually needs. */
public final class RepairTasks {
    public static RepairTask forSubsystem(SuitSubsystem subsystem) {
        return switch (subsystem) {
            case HELMET_SEAL -> new SealPressureTest();
            case TANK_MOUNT -> new LatchSequence();
            case REGULATOR -> new RegulatorCalibration();
            case SCRUBBER_BAY -> new CartridgeSeating();
            case THERMAL_LAYER -> new InsulationRoute();
            case STATUS_DISPLAY -> new SolderJoints();
            // Finding the hole is the job; patching it is the easy part.
            case SEALED_GLOVES -> new GloveLeakHunt();
        };
    }

    private RepairTasks() {}
}
