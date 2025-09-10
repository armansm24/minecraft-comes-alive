package net.mca.forge;

import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles respawning as child in hardcore mode for MCA.
 * When a player dies in hardcore mode and has living children,
 * allows them to continue playing as one of their children.
 */
@Mod.EventBusSubscriber(modid = MCA.MOD_ID)
public class HardcoreChildRespawnHandler {

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        // Only handle player deaths
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();
        
        // Check if the feature is enabled
        if (!Config.getInstance().enableHardcoreChildRespawn) {
            return;
        }
        
        // Only handle hardcore mode
        if (!world.getLevelProperties().isHardcore()) {
            return;
        }

        // Get the player's family data
        FamilyTree familyTree = FamilyTree.get(world);
        Optional<FamilyTreeNode> playerNode = familyTree.getOrEmpty(player.getUuid());
        
        if (playerNode.isEmpty()) {
            return;
        }

        // Find living children
        List<VillagerEntityMCA> livingChildren = findLivingChildren(world, playerNode.get());
        
        if (livingChildren.isEmpty()) {
            // No living children - proceed with normal hardcore death
            MCA.LOGGER.info("Player {} died in hardcore mode with no living children", player.getName().getString());
            player.sendMessage(Text.translatable("mca.hardcore.no_children").formatted(Formatting.RED), false);
            return;
        }

        MCA.LOGGER.info("Player {} died in hardcore mode, respawning as child. Found {} living children", 
                player.getName().getString(), livingChildren.size());

        // Cancel the death event to prevent hardcore game over
        event.setCanceled(true);
        
        // Heal the player to prevent immediate re-death
        player.setHealth(player.getMaxHealth());
        
        // Find the chosen child based on configuration
        VillagerEntityMCA chosenChild = selectChild(livingChildren);
        
        // Transfer player control to the child
        transferToChild(player, chosenChild);
    }

    /**
     * Selects which child to respawn as based on the configuration.
     */
    private static VillagerEntityMCA selectChild(List<VillagerEntityMCA> children) {
        String mode = Config.getInstance().hardcoreChildRespawnMode.toLowerCase();
        
        return switch (mode) {
            case "youngest" -> children.stream()
                    .min((a, b) -> Integer.compare(a.getBreedingAge(), b.getBreedingAge()))
                    .orElse(children.get(0));
            case "random" -> children.get((int) (Math.random() * children.size()));
            default -> children.stream() // "oldest" or any other value defaults to oldest
                    .max((a, b) -> Integer.compare(a.getBreedingAge(), b.getBreedingAge()))
                    .orElse(children.get(0));
        };
    }

    /**
     * Finds all living children of the given player in the world.
     */
    private static List<VillagerEntityMCA> findLivingChildren(ServerWorld world, FamilyTreeNode playerNode) {
        return playerNode.streamChildren()
                .map(world::getEntity)
                .filter(entity -> entity instanceof VillagerEntityMCA)
                .map(entity -> (VillagerEntityMCA) entity)
                .filter(child -> !child.isRemoved() && child.isAlive() && !child.isBaby())
                .toList();
    }

    /**
     * Transfers the player's control to the specified child villager.
     */
    private static void transferToChild(ServerPlayerEntity player, VillagerEntityMCA child) {
        ServerWorld world = (ServerWorld) player.getWorld();
        
        // Store original player data
        String originalName = player.getName().getString();
        UUID originalUUID = player.getUuid();
        
        // Get family tree and player data
        FamilyTree familyTree = FamilyTree.get(world);
        PlayerSaveData playerData = PlayerSaveData.get(player);
        
        // Get the child's family node before we modify anything
        FamilyTreeNode originalChildNode = familyTree.getOrEmpty(child.getUuid()).orElse(null);
        
        // Update the original player's family tree entry to mark them as deceased
        familyTree.getOrEmpty(originalUUID).ifPresent(node -> {
            node.setDeceased(true);
            node.setName(originalName + " (Deceased)");
        });
        
        // Create/update the player's new identity as the child
        String childName = child.getName().getString();
        FamilyTreeNode newPlayerNode = familyTree.getOrCreate(player.getUuid(), childName, child.getGenetics().getGender(), true);
        
        // Copy the child's family relationships to the player
        if (originalChildNode != null) {
            // Set parents for the new player identity by getting the FamilyTreeNode objects
            if (FamilyTreeNode.isValid(originalChildNode.father())) {
                FamilyTreeNode father = familyTree.getOrEmpty(originalChildNode.father()).orElse(null);
                if (father != null) {
                    newPlayerNode.setFather(father);
                }
            }
            if (FamilyTreeNode.isValid(originalChildNode.mother())) {
                FamilyTreeNode mother = familyTree.getOrEmpty(originalChildNode.mother()).orElse(null);
                if (mother != null) {
                    newPlayerNode.setMother(mother);
                }
            }
            
            // Update parent relationships to point to the player instead of the child
            if (FamilyTreeNode.isValid(originalChildNode.father())) {
                FamilyTreeNode father = familyTree.getOrEmpty(originalChildNode.father()).orElse(null);
                if (father != null) {
                    // Remove the old child reference and add player as child
                    father.children().removeIf(childId -> childId.equals(child.getUuid()));
                    father.addChild(player.getUuid());
                    
                    // Update villager relationships if the father is a villager in the world
                    if (world.getEntity(father.id()) instanceof VillagerEntityMCA fatherVillager) {
                        // Set relationship to child with high hearts (100)
                        fatherVillager.getVillagerBrain().getMemoriesForPlayer(player).setHearts(100);
                        // The memory system doesn't have direct child/marriage flags in the current API
                        // But setting high hearts should make the relationship very positive
                    }
                }
            }
            
            if (FamilyTreeNode.isValid(originalChildNode.mother())) {
                FamilyTreeNode mother = familyTree.getOrEmpty(originalChildNode.mother()).orElse(null);
                if (mother != null) {
                    // Remove the old child reference and add player as child
                    mother.children().removeIf(childId -> childId.equals(child.getUuid()));
                    mother.addChild(player.getUuid());
                    
                    // Update villager relationships if the mother is a villager in the world
                    if (world.getEntity(mother.id()) instanceof VillagerEntityMCA motherVillager) {
                        // Set relationship to child with high hearts (100)
                        motherVillager.getVillagerBrain().getMemoriesForPlayer(player).setHearts(100);
                        // The memory system doesn't have direct child/marriage flags in the current API
                        // But setting high hearts should make the relationship very positive
                    }
                }
            }
            
            // Mark the original child as deceased in the family tree
            originalChildNode.setDeceased(true);
            originalChildNode.setName(childName + " (Merged with Player)");
        }
        
        // Teleport player to child's location
        player.teleport(world, child.getX(), child.getY(), child.getZ(), child.getYaw(), child.getPitch());
        
        // Set the player's game mode back to survival (in case it was changed)
        player.changeGameMode(GameMode.SURVIVAL);
        
        // Remove the child entity from the world
        child.discard();
        
        // Notify the player of the transfer
        player.sendMessage(Text.translatable("mca.hardcore.respawned_as_child", childName).formatted(Formatting.GREEN), false);
        player.sendMessage(Text.translatable("mca.hardcore.continue_legacy").formatted(Formatting.YELLOW), false);
        
        // Mark data as dirty to save changes
        playerData.markDirty();
        familyTree.markDirty();
    }
}
