package play.xponer.astronima.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import play.xponer.astronima.registry.ModBlockEntities;

/**
 * Remembers where a telescope was last aimed, per direct request after playtest: "хочу что-бы
 * телескоп сохранял позицию в которой его оставили когда вышли" — reversing this document's own
 * earlier assumption that an unoccupied mount's rest angle needs no memory
 * (design/astra-telescope.md §6.4/§9 open question 4, now answered the other way by the person
 * whose preference that open question exists to capture).
 *
 * <p>Lives on the block, not the ephemeral {@code TelescopeMountEntity} (which still does not
 * survive a save — only this fact about it does): a telescope's last aim is a property of the
 * instrument sitting there, not of whichever transient rider entity most recently existed for it.
 */
public class TelescopeBlockEntity extends BlockEntity {

    private boolean hasSavedAim = false;
    private float savedYaw;
    private float savedPitch;

    public TelescopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELESCOPE.get(), pos, state);
    }

    public boolean hasSavedAim() {
        return hasSavedAim;
    }

    public float savedYaw() {
        return savedYaw;
    }

    public float savedPitch() {
        return savedPitch;
    }

    /** Called when a mount empties (the last rider dismounts) — the aim it ends on is what a
     * future observer finds waiting for them.
     *
     * <p>{@code setChanged()} alone only marks the chunk dirty for the next disk save — it does
     * not, by itself, tell any already-connected client anything changed (confirmed by reading
     * {@code Level#blockEntityChanged}/{@code ChunkHolder#blockChanged} directly, rule 2: only
     * {@code Level#sendBlockUpdated} ever queues a block entity's {@code getUpdatePacket()} for
     * broadcast). Found the hard way: the tube kept showing its neutral rest angle to everyone
     * watching from outside no matter how many times a telescope was actually re-aimed and left,
     * because the only client that had ever heard about a saved aim was the one present when the
     * chunk first loaded (PLAN.md rule 80) — the same shape of bug {@code TelescopeMountEntity}'s
     * own {@code telescopePos} field already turned out to be (rule 77), for a block entity
     * instead of an entity. */
    public void saveAim(float yaw, float pitch) {
        this.hasSavedAim = true;
        this.savedYaw = yaw;
        this.savedPitch = pitch;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Sent on chunk load, and now also on {@link #saveAim} — the same data either way, since both
     * reuse {@link #saveCustomOnly}, not a second, parallel serialization (rule 1). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("has_saved_aim", hasSavedAim);
        if (hasSavedAim) {
            output.putFloat("saved_yaw", savedYaw);
            output.putFloat("saved_pitch", savedPitch);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        hasSavedAim = input.getBooleanOr("has_saved_aim", false);
        savedYaw = input.getFloatOr("saved_yaw", 0.0F);
        savedPitch = input.getFloatOr("saved_pitch", 0.0F);
    }
}
