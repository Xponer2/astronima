package play.xponer.astronima.client.gravity;

import play.xponer.astronima.sim.gravity.Orientation;

/**
 * The local player's own real orientation, held purely client-side — never round-tripped through
 * the network. design/eva-mobility.md §1.2 originally read this back off {@code
 * ModAttachments.ORIENTATION} for the local player too, since that attachment already existed and
 * already held the right value locally. Found live, the second time this was actually flown: it
 * does, but only until the client's own {@code network/OrientationUpdatePayload} teaches the server
 * about it — the attachment being {@code .sync()}-enabled means the server's own write of that same
 * value then rides that sync back down to every observer, <strong>including the client that sent
 * it</strong>, arriving one real round trip later and stomping this tick's fresh input with last
 * tick's (or older) echo. Reported as the camera refusing to turn at all and snapping straight
 * back — a client fighting its own delayed reflection, worse the more real the network path is
 * (multiplayer more than a true single-player world with no listening socket at all, which is
 * exactly why opening to LAN made it *worse*, not better, despite reading like it should be the
 * same code path).
 *
 * <p>The fix is the standard client-prediction shape: the local player is authoritative over their
 * own orientation, full stop — {@code ModAttachments.ORIENTATION} stays the wire format for telling
 * the server (so it can tell everyone else), but the local camera and this player's own
 * third-person model read <em>this</em> instead, which nothing but this player's own input ever
 * writes.
 */
public final class LocalOrientation {

    private static Orientation current = Orientation.IDENTITY;

    public static Orientation get() {
        return current;
    }

    public static void set(Orientation value) {
        current = value;
    }

    private LocalOrientation() {}
}
