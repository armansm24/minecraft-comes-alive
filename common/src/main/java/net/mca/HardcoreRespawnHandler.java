
package net.mca;

import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.world.GameMode;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class HardcoreRespawnHandler {
    public static void onPlayerDeath(ServerPlayerEntity serverPlayer) {
        ServerWorld world = serverPlayer.getServerWorld();
        if (!world.getLevelProperties().isHardcore()) return;

        PlayerSaveData data = PlayerSaveData.get(serverPlayer);
        Set<UUID> children = data.getFamilyEntry().children();
        if (children.isEmpty()) return;

        UUID childId = children.iterator().next();
        Optional<PlayerSaveData> childDataOpt = PlayerSaveData.getIfPresent(world, childId);
        if (childDataOpt.isEmpty()) return;

        serverPlayer.networkHandler.disconnect(Text.literal("You have died, but your legacy continues as your child."));

        ServerPlayerEntity childPlayer = world.getServer().getPlayerManager().getPlayer(childId);
        if (childPlayer != null) {
            childPlayer.changeGameMode(GameMode.SURVIVAL); // or setGameMode depending on mappings
            childPlayer.teleport(world, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYaw(), serverPlayer.getPitch());
            childPlayer.sendMessage(Text.literal("You are now playing as your child."), false);
        }
    }
}
