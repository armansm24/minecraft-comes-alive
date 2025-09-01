
package net.mca;

import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.world.GameMode;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import com.mojang.authlib.GameProfile;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class HardcoreRespawnHandler {
    public static void onPlayerDeath(ServerPlayerEntity serverPlayer) {
        ServerWorld world = serverPlayer.getServerWorld();
        System.out.println("[MCA] onPlayerDeath called for player: " + serverPlayer.getName().getString());
        PlayerSaveData data = PlayerSaveData.get(serverPlayer);
        Set<UUID> children = data.getFamilyEntry().children();
        System.out.println("[MCA] Children count: " + children.size());
        if (children.isEmpty()) return;

        UUID childId = children.iterator().next();
        System.out.println("[MCA] Selected child UUID: " + childId);
        Optional<PlayerSaveData> childDataOpt = PlayerSaveData.getIfPresent(world, childId);
        if (childDataOpt.isPresent()) {
            System.out.println("[MCA] Found PlayerSaveData for child UUID: " + childId + " (player child)");
            // ...existing code for player child respawn (if needed)...
            return;
        }

        // Try to find the NPC child entity in the world
        Entity childEntity = world.getEntity(childId);
        net.mca.entity.VillagerEntityMCA npcChild = null;
        if (childEntity == null) {
            System.out.println("[MCA] No NPC child entity found for UUID: " + childId);
            // Diagnostic logging for why the entity is missing
            boolean inFamilyTree = false;
            PlayerSaveData parentData = PlayerSaveData.get(serverPlayer);
            if (parentData != null) {
                inFamilyTree = parentData.getFamilyEntry().children().contains(childId);
            }
            System.out.println("[MCA] Child UUID " + childId + " is " + (inFamilyTree ? "registered" : "NOT registered") + " in the family tree.");
            boolean foundInLoadedEntities = false;
            for (Entity e : world.iterateEntities()) {
                if (e.getUuid().equals(childId)) {
                    foundInLoadedEntities = true;
                    System.out.println("[MCA] Found child entity in loaded entities: " + e.getName().getString() + " at " + e.getBlockPos());
                    break;
                }
            }
            if (!foundInLoadedEntities) {
                System.out.println("[MCA] Child entity not found in any loaded chunk.");
            }
            System.out.println("[MCA] Current world dimension: " + world.getRegistryKey().getValue());

            // Use saved position from FamilyTreeNode if available
            var childNodeOpt = data.getFamilyTree().getOrEmpty(childId);
            if (childNodeOpt.isPresent() && childNodeOpt.get().hasPosition()) {
                double x = childNodeOpt.get().getPosX();
                double y = childNodeOpt.get().getPosY();
                double z = childNodeOpt.get().getPosZ();
                System.out.println("[MCA] Using saved child position: " + x + ", " + y + ", " + z);
                serverPlayer.teleport(world, x, y, z, serverPlayer.getYaw(), serverPlayer.getPitch());
                serverPlayer.sendMessage(Text.literal("You are now playing as your child (using saved position)."), false);
            } else {
                System.out.println("[MCA] No saved child position available.");
            }
            return;
        } else {
            System.out.println("[MCA] NPC child entity found: " + childEntity.getName().getString() + " (UUID: " + childEntity.getUuid() + ")");
            if (!(childEntity instanceof net.mca.entity.VillagerEntityMCA)) {
                System.out.println("[MCA] Entity is not a VillagerEntityMCA, aborting.");
                return;
            }
            npcChild = (net.mca.entity.VillagerEntityMCA) childEntity;
        }

        // Instead of disconnecting, transfer control to NPC child in-place
        System.out.println("[MCA] Transferring control to NPC child in-place, player remains in game.");

        // Transfer position
        double x = npcChild.getX();
        double y = npcChild.getY();
        double z = npcChild.getZ();
        float yaw = npcChild.getYaw();
        float pitch = npcChild.getPitch();
        System.out.println("[MCA] Teleporting player to NPC child position: " + x + ", " + y + ", " + z);
        serverPlayer.teleport(world, x, y, z, yaw, pitch);

        // Transfer inventory
        System.out.println("[MCA] Copying NPC child inventory to player.");
        serverPlayer.getInventory().clear();
        for (int i = 0; i < npcChild.getInventory().size(); i++) {
            ItemStack stack = npcChild.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                serverPlayer.getInventory().setStack(i, stack.copy());
            }
        }

        // Transfer skin (if applicable)
        System.out.println("[MCA] Attempting to copy skin/custom skin from NPC child.");
        GameProfile childProfile = npcChild.getGameProfile();
        if (childProfile != null) {
            System.out.println("[MCA] NPC child has custom skin: " + childProfile.getName());
            // Copy custom skin, hair, and clothes from NPC child to player
            try {
                if (serverPlayer instanceof net.mca.entity.VillagerLike villagerPlayer) {
                    net.mca.entity.VillagerLike<?> npcVillager = (net.mca.entity.VillagerLike<?>) npcChild;
                    villagerPlayer.setCustomSkin(npcVillager.getTrackedValue(net.mca.entity.VillagerLike.CUSTOM_SKIN));
                    villagerPlayer.setTrackedValue(net.mca.entity.VillagerLike.HAIR, npcVillager.getTrackedValue(net.mca.entity.VillagerLike.HAIR));
                    villagerPlayer.setTrackedValue(net.mca.entity.VillagerLike.CLOTHES, npcVillager.getTrackedValue(net.mca.entity.VillagerLike.CLOTHES));
                    villagerPlayer.updateCustomSkin();
                    System.out.println("[MCA] Applied NPC child's skin, hair, and clothes to player.");
                } else {
                    System.out.println("[MCA] Player entity is not VillagerLike, cannot apply skin.");
                }
            } catch (Exception e) {
                System.out.println("[MCA] Failed to apply NPC child's skin: " + e.getMessage());
            }
        }

        // Transfer name
        String childName = npcChild.getName().getString();
        System.out.println("[MCA] Setting player name to NPC child name: " + childName);
        serverPlayer.setCustomName(Text.literal(childName));

        // Remove NPC child from world
        System.out.println("[MCA] Removing NPC child entity from world.");
        npcChild.discard();

        // Final message
    serverPlayer.sendMessage(Text.literal("You are now playing as your child."), false);
    // Send packet to client to close death screen
    net.mca.cobalt.network.NetworkHandler.sendToPlayer(new net.mca.network.c2s.CloseDeathScreenMessage(), serverPlayer);
    System.out.println("[MCA] Sent CloseDeathScreenMessage to client.");
    System.out.println("[MCA] Respawn as NPC child complete.");
    }
}
