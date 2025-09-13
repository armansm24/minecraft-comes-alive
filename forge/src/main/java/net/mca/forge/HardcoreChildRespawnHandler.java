package net.mca.forge;

import net.mca.Config;
import net.mca.MCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.s2c.PlayerDataMessage;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
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
import java.util.stream.Stream;

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

        // Find living children across all dimensions
        List<VillagerEntityMCA> livingChildren = findLivingChildrenAcrossDimensions(world.getServer(), playerNode.get());
        
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
        
        // Reset health to child default (20 health points = 10 hearts instead of adult health)
        float childDefaultHealth = 20.0F; // Standard Minecraft health for a child
        player.setHealth(childDefaultHealth);
        
        // Clear all status effects (positive and negative)
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            player.removeStatusEffect(effect.getEffectType());
        }
        
        // Extinguish fire if the player is burning
        if (player.isOnFire()) {
            player.extinguish();
        }
        
        // Clear the player's inventory (they start fresh as their child)
        player.getInventory().clear();
        
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
     * Finds all living children of the given player across all dimensions.
     * Only adult children can be respawned as - younger children are not eligible.
     */
    private static List<VillagerEntityMCA> findLivingChildrenAcrossDimensions(MinecraftServer server, FamilyTreeNode playerNode) {
        return playerNode.streamChildren()
                .map(childUUID -> {
                    // Search for the child entity across all dimensions
                    for (ServerWorld dimension : server.getWorlds()) {
                        var entity = dimension.getEntity(childUUID);
                        if (entity instanceof VillagerEntityMCA child && 
                            !child.isRemoved() && child.isAlive() && 
                            child.getAgeState() == net.mca.entity.ai.relationship.AgeState.ADULT) {
                            return child;
                        }
                    }
                    return null; // Child not found in any dimension or not adult
                })
                .filter(child -> child != null)
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
        
        // === CRITICAL: END ALL ROMANTIC RELATIONSHIPS ===
        // Force divorce/breakup from any spouse/partner to prevent relationship conflicts
        // This ensures that family relationship constraints work properly
        PlayerSaveData originalPlayerData = PlayerSaveData.get(player);
        if (originalPlayerData.isMarried() || originalPlayerData.isEngaged()) {
            MCA.LOGGER.info("Player {} was in a relationship, ending it before child respawn", originalName);
            
            // Get the partner information before ending the relationship
            Optional<UUID> partnerUUID = originalPlayerData.getPartnerUUID();
            Optional<String> partnerName = originalPlayerData.getPartnerName().map(text -> text.getString());
            
            // End the player's side of the relationship
            originalPlayerData.endRelationShip(net.mca.entity.ai.relationship.RelationshipState.SINGLE);
            
            // End the partner's side of the relationship if they exist
            partnerUUID.ifPresent(spouseUUID -> {
                // Try to find the partner as a player first
                ServerPlayerEntity spousePlayer = world.getServer().getPlayerManager().getPlayer(spouseUUID);
                if (spousePlayer != null) {
                    PlayerSaveData spouseData = PlayerSaveData.get(spousePlayer);
                    spouseData.endRelationShip(net.mca.entity.ai.relationship.RelationshipState.SINGLE);
                    
                    // Notify the ex-spouse if they're online
                    partnerName.ifPresent(name -> {
                        spousePlayer.sendMessage(Text.translatable("mca.hardcore.spouse_died", originalName)
                                .formatted(Formatting.RED), false);
                    });
                } else {
                    // Try to find the partner as a villager
                    if (world.getEntity(spouseUUID) instanceof VillagerEntityMCA spouseVillager) {
                        spouseVillager.getRelationships().endRelationShip(net.mca.entity.ai.relationship.RelationshipState.SINGLE);
                    }
                }
                
                // Update the family tree entry for the ex-spouse
                familyTree.getOrEmpty(spouseUUID).ifPresent(spouseNode -> {
                    spouseNode.updatePartner(null, net.mca.entity.ai.relationship.RelationshipState.WIDOW);
                });
            });
            
            MCA.LOGGER.info("Successfully ended relationship for player {} before hardcore child respawn", originalName);
        }
        
        // === INHERIT CHILD'S COMPLETE IDENTITY ===
        String childName = child.getName().getString();
        
        // Copy ALL entity data from child to player (includes gender, skin, traits, etc.)
        NbtCompound childEntityData = new NbtCompound();
        ((MobEntity) child).writeCustomDataToNbt(childEntityData);
        playerData.setEntityData(childEntityData);
        playerData.setEntityDataSet(true);
        
        // CRITICAL: Delay sending player data to avoid visual conflicts
        // Send the updated player data after a short delay to ensure proper client synchronization
        world.getServer().execute(() -> {
            // Only send to the respawned player to avoid affecting other players' visuals
            NetworkHandler.sendToPlayer(new PlayerDataMessage(player.getUuid(), childEntityData), player);
            MCA.LOGGER.info("Sent updated player data to {} after hardcore child respawn", childName);
        });
        
        // Create/update the player's new family tree identity
        FamilyTreeNode newPlayerNode = familyTree.getOrCreate(player.getUuid(), childName, child.getGenetics().getGender(), true);
        
        // === ENSURE NEW IDENTITY IS SINGLE ===
        // Critical: Ensure the respawned player starts as single with no romantic relationships
        // This prevents any inheritance of romantic status from the original player or child
        newPlayerNode.updatePartner(null, net.mca.entity.ai.relationship.RelationshipState.SINGLE);
        playerData.endRelationShip(net.mca.entity.ai.relationship.RelationshipState.SINGLE);
        
        MCA.LOGGER.info("Set respawned player {} to single relationship status", childName);
        
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
                    
                    // Update villager memories if the father is a villager in the world
                    if (world.getEntity(father.id()) instanceof VillagerEntityMCA fatherVillager) {
                        // Reset any existing relationship memories and set as beloved child
                        fatherVillager.getVillagerBrain().getMemoriesForPlayer(player).setHearts(100);
                        // Set family relationship state by ensuring the family tree is correctly linked
                        // The IS_PARENT and IS_RELATIVE predicates will now work correctly
                    }
                }
            }
            
            if (FamilyTreeNode.isValid(originalChildNode.mother())) {
                FamilyTreeNode mother = familyTree.getOrEmpty(originalChildNode.mother()).orElse(null);
                if (mother != null) {
                    // Remove the old child reference and add player as child
                    mother.children().removeIf(childId -> childId.equals(child.getUuid()));
                    mother.addChild(player.getUuid());
                    
                    // Update villager memories if the mother is a villager in the world
                    if (world.getEntity(mother.id()) instanceof VillagerEntityMCA motherVillager) {
                        // Reset any existing relationship memories and set as beloved child
                        motherVillager.getVillagerBrain().getMemoriesForPlayer(player).setHearts(100);
                        // Set family relationship state by ensuring the family tree is correctly linked
                        // The IS_PARENT and IS_RELATIVE predicates will now work correctly
                    }
                }
            }
            
            // Mark the original child as deceased in the family tree
            originalChildNode.setDeceased(true);
            originalChildNode.setName(childName + " (Merged with Player)");
        }
        
        // Teleport player to child's location (potentially cross-dimensional)
        ServerWorld childWorld = (ServerWorld) child.getWorld();
        if (childWorld != world) {
            // Child is in a different dimension, teleport across dimensions
            player.teleport(childWorld, child.getX(), child.getY(), child.getZ(), child.getYaw(), child.getPitch());
            MCA.LOGGER.info("Player {} teleported from {} to {} dimension to respawn as child", 
                    player.getName().getString(), 
                    world.getRegistryKey().getValue(), 
                    childWorld.getRegistryKey().getValue());
            
            // Notify player about cross-dimensional travel
            String dimensionName = childWorld.getRegistryKey().getValue().toString();
            player.sendMessage(Text.translatable("mca.hardcore.cross_dimension_respawn", dimensionName)
                    .formatted(Formatting.BLUE), false);
        } else {
            // Child is in same dimension
            player.teleport(world, child.getX(), child.getY(), child.getZ(), child.getYaw(), child.getPitch());
        }
        
        // Set the player's game mode back to survival (in case it was changed)
        player.changeGameMode(GameMode.SURVIVAL);
        
        // Remove the child entity from the world
        child.discard();
        
        // Notify the player of the transfer
        player.sendMessage(Text.translatable("mca.hardcore.respawned_as_child", childName).formatted(Formatting.GREEN), false);
        player.sendMessage(Text.translatable("mca.hardcore.child_status").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.translatable("mca.hardcore.continue_legacy").formatted(Formatting.YELLOW), false);
        
        // === RESET ALL RELATIONSHIP HEARTS AS A CHILD ===
        // Reset relationship hearts with all villagers since the player is now a child
        // This prevents adult relationships and inappropriate interactions
        resetAllRelationshipHearts(world, player);
        
        // === CHECK FOR GENERATIONAL INSURANCE ===
        // Check if the player (now as their child) already has children for future respawn insurance
        if (originalChildNode != null) {
            long grandchildrenCount = originalChildNode.streamChildren().count();
            if (grandchildrenCount > 0) {
                // Count living grandchildren across all dimensions for accurate insurance count
                List<VillagerEntityMCA> livingGrandchildren = findLivingChildrenAcrossDimensions(world.getServer(), originalChildNode);
                int livingCount = livingGrandchildren.size();
                
                if (livingCount > 0) {
                    // Player has insurance! Their new identity already has children for future hardcore respawns
                    player.sendMessage(Text.translatable("mca.hardcore.generational_insurance", livingCount)
                            .formatted(Formatting.AQUA), false);
                    MCA.LOGGER.info("Player {} respawned with {} living grandchildren as insurance", 
                            childName, livingCount);
                } else if (grandchildrenCount > 0) {
                    // Had children but they're not alive/adult yet
                    player.sendMessage(Text.translatable("mca.hardcore.future_insurance")
                            .formatted(Formatting.GRAY), false);
                }
            } else {
                // No insurance yet - encourage family building
                player.sendMessage(Text.translatable("mca.hardcore.build_insurance")
                        .formatted(Formatting.LIGHT_PURPLE), false);
            }
        }
        
        // Trigger the hardcore child respawn achievement!
        CriterionMCA.GENERIC_EVENT_CRITERION.trigger(player, "hardcore_child_respawn");
        
        // === COMPREHENSIVE FAMILY RELATIONSHIP REFRESH ===
        // Force complete refresh of family tree relationships to ensure proper recognition
        familyTree.markDirty();
        
        // CRITICAL: Force complete family tree reconstruction for relationship recognition
        // This ensures that getAllRelatives() and isRelative() work correctly for the player
        if (originalChildNode != null) {
            // Refresh ALL family relationships by forcing the family tree to recalculate
            familyTree.getOrEmpty(player.getUuid()).ifPresent(playerNode -> {
                // Force the family tree to rebuild relationship caches by accessing all relatives
                playerNode.getAllRelatives(9).forEach(relativeId -> {
                    // This forces the family tree to rebuild relationship caches
                    familyTree.getOrEmpty(relativeId);
                });
            });
            
            // Update ALL villagers in the world that are family members
            Stream.of(originalChildNode.father(), originalChildNode.mother())
                .filter(FamilyTreeNode::isValid)
                .forEach(parentId -> {
                    if (world.getEntity(parentId) instanceof VillagerEntityMCA parentVillager) {
                        // CRITICAL: Complete relationship system refresh
                        
                        // 1. Clear ALL relationship memories to force fresh recognition
                        parentVillager.getVillagerBrain().getMemoriesForPlayer(player).setHearts(100);
                        
                        // 2. Completely reinitialize the villager's brain and relationship system
                        parentVillager.reinitializeBrain(world);
                        
                        // 3. Clear specific brain memories that might cache relationship data
                        parentVillager.getBrain().forget(MemoryModuleType.INTERACTION_TARGET);
                        parentVillager.getBrain().forget(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
                        
                        // 4. Force the villager's family tree entry to refresh by accessing it
                        // This ensures IS_RELATIVE and IS_FAMILY predicates work correctly
                        parentVillager.getRelationships().getFamilyEntry().getAllRelatives(9).count();
                    }
                });
            
            // ADDITIONAL: Force all villagers in the world to refresh their family recognition
            // This is necessary because family relationships are bidirectional
            world.iterateEntities().forEach(entity -> {
                if (entity instanceof VillagerEntityMCA villager) {
                    // Check if this villager is related to the player by accessing family tree directly
                    FamilyTreeNode villagerNode = villager.getRelationships().getFamilyEntry();
                    if (villagerNode.isRelative(player.getUuid())) {
                        // Force complete relationship refresh for family members
                        villager.getVillagerBrain().getMemoriesForPlayer(player).setHearts(
                            Math.max(villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts(), 50)
                        );
                        villager.reinitializeBrain(world);
                        
                        // Force family tree relationship recalculation
                        villagerNode.getAllRelatives(9).count();
                    }
                }
            });
        }
        
        // Mark data as dirty to save changes
        playerData.markDirty();
        familyTree.markDirty();
    }
    
    /**
     * Resets all relationship hearts with villagers when player respawns as a child.
     * This ensures the child starts with appropriate relationship levels.
     */
    private static void resetAllRelationshipHearts(ServerWorld world, ServerPlayerEntity player) {
        // Get all villagers across all dimensions in the server
        for (ServerWorld dimension : world.getServer().getWorlds()) {
            // Get all VillagerEntityMCA entities in this dimension
            List<VillagerEntityMCA> villagers = dimension.getEntitiesByClass(
                VillagerEntityMCA.class, 
                new net.minecraft.util.math.Box(
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                ),
                entity -> !entity.isRemoved() && entity.isAlive()
            );
            
            for (VillagerEntityMCA villager : villagers) {
                // Reset relationship hearts to appropriate levels
                var memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
                
                // Family members get high hearts (they love their family child)
                if (villager.getRelationships().getFamilyEntry().isRelative(player.getUuid())) {
                    memory.setHearts(100); // Family love - 100 hearts
                    MCA.LOGGER.debug("Reset family member {} hearts to 100 for respawned player {}", 
                            villager.getName().getString(), player.getName().getString());
                } else {
                    // Non-family members reset to mod's default starting hearts (10)
                    memory.setHearts(10); // Reset to default 10 hearts
                    MCA.LOGGER.debug("Reset non-family villager {} hearts to 10 for respawned player {}", 
                            villager.getName().getString(), player.getName().getString());
                }
                
                // Reset interaction fatigue as well
                memory.setInteractionFatigue(0);
            }
        }
        
        MCA.LOGGER.info("Reset relationship hearts for respawned player {} (family: 100, non-family: 10)", player.getName().getString());
    }
}
