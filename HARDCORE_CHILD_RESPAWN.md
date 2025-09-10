# Hardcore Child Respawn Feature

## Overview
This feature allows players to continue their legacy in Hardcore mode by respawning as one of their children when they die, instead of facing immediate game over.

## How It Works
1. When a player dies in Hardcore mode, the mod checks if they have any living children (villagers they've had through marriage)
2. If living children are found, the player respawns as the chosen child instead of getting a game over screen
3. The original player character is marked as deceased in the family tree
4. The child villager is removed from the world and the player takes their place

## Configuration Options

### `enableHardcoreChildRespawn` (default: `true`)
- Controls whether the hardcore child respawn feature is enabled
- Set to `false` to disable the feature entirely

### `hardcoreChildRespawnMode` (default: `"oldest"`)
- Controls which child to respawn as when multiple children are available
- Options:
  - `"oldest"`: Respawn as the oldest child (highest breeding age)
  - `"youngest"`: Respawn as the youngest child (lowest breeding age)
  - `"random"`: Respawn as a randomly selected child

## Messages
When the feature activates, players will see one of these messages:

- **No living children**: "Game Over! You have no living children to continue your legacy."
- **Successful respawn**: "You have respawned as your child, [Child Name]. Your legacy continues!"
- **Encouragement message**: "Continue the family line and make your ancestors proud."

## Implementation Details
- Only works in worlds with hardcore mode enabled
- Only considers living, non-removed villager children
- Preserves family tree relationships for the new player identity
- Teleports the player to the child's location
- Marks both the original player and child villager as deceased in the family tree
- Maintains the child's name as the new player identity

## Compatibility
This feature is implemented for Minecraft Forge and requires:
- Minecraft Comes Alive (MCA) mod
- Forge mod loader
- Hardcore world

## Technical Notes
- Uses Forge's `LivingDeathEvent` to intercept player deaths
- Integrates with MCA's family tree system
- Cancels the death event when children are available to prevent game over
- Properly manages entity cleanup and world state
