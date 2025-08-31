package net.mca;

import net.mca.server.world.data.PlayerSaveData;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.level.LevelProperties;
import net.fabricmc.fabric.api.event.player.PlayerDeathCallback;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class HardcoreRespawnHandler {
    public static void register() {
        // This uses Fabric's event system for demonstration; adapt for Forge/Quilt as needed
        PlayerDeathCallback.EVENT.register((player, damageSource) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
            ServerWorld world = serverPlayer.getServerWorld();
            LevelProperties props = world.getLevelProperties();
            if (!props.isHardcore()) return;

            PlayerSaveData data = PlayerSaveData.get(serverPlayer);
            Set<UUID> children = data.getFamilyEntry().children();
            if (children.isEmpty()) return;

            // Pick the first child (could randomize or let user choose)
            UUID childId = children.iterator().next();
            Optional<PlayerSaveData> childDataOpt = PlayerSaveData.getIfPresent(world, childId);
            if (childDataOpt.isEmpty()) return;
            PlayerSaveData childData = childDataOpt.get();

            // Remove the dead player
            serverPlayer.networkHandler.disconnect(Text.literal("You have died, but your legacy continues as your child."));

            // Respawn as child (simplified: teleport, set name, etc.)
            ServerPlayerEntity childPlayer = world.getServer().getPlayerManager().getPlayer(childId);
            if (childPlayer != null) {
                childPlayer.setGameMode(GameMode.SURVIVAL);
                childPlayer.teleport(world, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYaw(), serverPlayer.getPitch());
                childPlayer.sendMessage(Text.literal("You are now playing as your child."), Util.NIL_UUID);
            }
        });
    }
}
