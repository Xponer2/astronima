package play.xponer.astronima.power;

/**
 * A block that sits on the electrical network, and that a {@code PowerCableBlock} therefore
 * reaches an arm toward.
 *
 * <p>This is the visual counterpart of the walk in {@link CableNetworks}: the walk resolves a
 * run structurally (cables and the cells they touch), while sources and draws attach to it from
 * their own side by calling {@link CableNetworks#resolveFrom}. A cable cannot see either of
 * those relationships from a neighbour's block state alone, so every block that is on the
 * network — the cable itself, the store, the sources, and the machines that draw — declares it
 * here, and the cable connects toward anything that does.
 *
 * <p>A marker rather than a hard-coded list inside the cable, for the reason {@code CableNetworks}
 * gives about the gas network: there must be exactly one place that decides what "connected"
 * means, and it should live with the block rather than be re-enumerated somewhere the block does
 * not know about. Adding a powered machine is then implementing this interface, next to the
 * sibling machines that already do — not editing the cable.
 */
public interface PowerConnectable {
}
