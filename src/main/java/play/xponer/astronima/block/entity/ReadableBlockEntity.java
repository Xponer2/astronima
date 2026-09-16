package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import play.xponer.astronima.client.indicator.BlockReadings;
import play.xponer.astronima.client.indicator.Reading;

import java.util.Objects;

/**
 * A block whose gauge the player can read — and which therefore has to <em>send</em> that gauge.
 *
 * <h2>Saving is not sending (rule 26)</h2>
 * Reported as <em>"the solar panel does not work at all, it just says no sun"</em>. It worked. It
 * made power the whole time. Its gauge was reading a field that never left the server.
 *
 * <p>A block entity field is server state. {@code setChanged()} marks it for the <strong>save
 * file</strong>; the client holds a <em>different object</em> at those coordinates and is told
 * nothing about it. Vanilla's default {@code getUpdatePacket()} is {@code null} and its default
 * {@code getUpdateTag()} is empty — so every indicator in the mod was drawing a perfectly honest
 * reading computed from <strong>zero</strong>. Nine machines had it. The solar array was only the
 * one whose default has a name a player reads as a fault.
 *
 * <h2>The reading travels, not the fields</h2>
 * Syncing the fields would mean auditing what each machine saves — three of them saved nothing at
 * all — and would leave the client <em>recomputing</em>, which is a second place to disagree with
 * the server. Publishing the answer means the server decides once and the client draws what it was
 * handed, which is what {@code design/indicators.md} §0 was about in the first place.
 *
 * <h2>One mechanism, not nine (rule 20)</h2>
 * Extending this class is the whole of what a readable machine has to do. An architecture guard
 * fails the build if a block entity {@code BlockReadings} knows about does not.
 */
public abstract class ReadableBlockEntity extends BlockEntity {

    /**
     * The last reading published to whoever is watching.
     *
     * <p>On the server this is what the client has been told; on the client it <em>is</em> the
     * gauge. Deliberately the same field on both sides — a second field for "what I computed
     * locally" is the fallback that hid this fault for the whole of the mod's life.
     */
    private @Nullable Reading published;

    protected ReadableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** What this block's gauge says, or null before anything has been published. */
    public @Nullable Reading reading() {
        return published;
    }

    /**
     * Recomputes the gauge and sends it on when it has moved.
     *
     * <p>Hung on {@code setChanged()} rather than on each machine's tick, because
     * {@code setChanged()} already means <em>something about me moved</em> and every machine
     * already calls it. Nine hand-written calls would be a checklist, and the tenth machine would
     * be the one that got missed — which is precisely the shape of the fault being fixed.
     */
    @Override
    public void setChanged() {
        super.setChanged();
        if (refreshReading() && level != null) {
            // What actually puts the packet on the wire: sendBlockUpdated marks the position, and
            // the chunk's broadcast then asks getUpdatePacket() for it. setChanged() alone only
            // ever reached the save file.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The reading, computed fresh on the server.
     *
     * <p>Called from {@link #getUpdateTag} as well, so a chunk arriving at a player brings a
     * current gauge rather than whatever was last saved — a machine that has been sitting idle
     * since it was built has never called {@code setChanged()} and would otherwise arrive blank.
     *
     * @return true when the gauge moved, so it is worth telling anybody
     */
    protected boolean refreshReading() {
        if (!(level instanceof ServerLevel)) {
            return false;      // the client keeps what it was told; it does not get a vote
        }
        Reading now = BlockReadings.of(this);
        if (Objects.equals(now, published)) {
            return false;
        }
        published = now;
        return true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        refreshReading();
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * The gauge rides in the same tag as everything else, so subclasses need do nothing.
     *
     * <p>It goes into the world save too. Redundant, since it is derived — and worth it, because a
     * freshly loaded chunk then has a correct lamp on the first frame instead of on the first tick
     * of whatever the machine does next.
     */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (published == null) {
            return;
        }
        output.putDouble("gauge_fraction", published.fraction());
        output.putString("gauge_band", published.band().name());
        output.putString("gauge_label", published.label());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String band = input.getStringOr("gauge_band", "");
        if (band.isEmpty()) {
            published = null;
            return;
        }
        published = new Reading(input.getDoubleOr("gauge_fraction", 0.0),
                bandOr(band, Reading.Band.IDLE),
                input.getStringOr("gauge_label", ""));
    }

    /** An unknown band costs the gauge, never the chunk it arrived in — a packet is input. */
    private static Reading.Band bandOr(String name, Reading.Band fallback) {
        for (Reading.Band band : Reading.Band.values()) {
            if (band.name().equals(name)) {
                return band;
            }
        }
        return fallback;
    }
}
