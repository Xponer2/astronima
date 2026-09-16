package play.xponer.astronima.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import play.xponer.astronima.Astronima;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Remembers which players have already been through the crash-site intro. */
public final class SpawnState extends SavedData {
    private static final Codec<SpawnState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.listOf().fieldOf("initialized_players")
                    .forGetter(s -> List.copyOf(s.initializedPlayers))
    ).apply(instance, SpawnState::new));

    public static final SavedDataType<SpawnState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Astronima.MODID, "spawn_state"),
            SpawnState::new, CODEC);

    private final Set<UUID> initializedPlayers = new HashSet<>();

    private SpawnState() {}

    private SpawnState(List<UUID> players) {
        initializedPlayers.addAll(players);
    }

    public static SpawnState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean markInitialized(UUID player) {
        boolean added = initializedPlayers.add(player);
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean isInitialized(UUID player) {
        return initializedPlayers.contains(player);
    }
}
