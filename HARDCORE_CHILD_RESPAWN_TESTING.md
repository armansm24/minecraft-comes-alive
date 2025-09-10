# Hardcore Child Respawn - Usage Example

## How to Test the Feature

### Setup
1. Create a new world in Hardcore mode
2. Enable the MCA mod with the hardcore child respawn feature
3. Ensure `enableHardcoreChildRespawn` is set to `true` in the config

### Steps to Test
1. **Get married to a villager**:
   - Build up relationship hearts to 100+
   - Use a wedding ring to marry a villager

2. **Have children**:
   - Wait for pregnancy and birth
   - Children will grow up over time
   - Make sure at least one child reaches adulthood (not a baby)

3. **Test the respawn**:
   - Put yourself in a dangerous situation (lava, monsters, etc.)
   - Die while in hardcore mode
   - Instead of game over, you should respawn as your child

### Expected Behavior
- **With living adult children**: Player respawns as chosen child
- **With only baby children**: Normal hardcore death (babies can't inherit)
- **With no children**: Normal hardcore death with message about no legacy
- **Feature disabled**: Normal hardcore death regardless of children

### Configuration Testing
Test different `hardcoreChildRespawnMode` values:
- `"oldest"`: Should select the oldest child
- `"youngest"`: Should select the youngest child  
- `"random"`: Should randomly pick a child

### Verification
After respawning as a child:
1. Check that your name changed to the child's name
2. Check that you're at the child's former location
3. Check the family tree - original player should be marked deceased
4. Verify the child villager is no longer in the world

### Troubleshooting
- Check server logs for debug messages about child respawn
- Ensure children are actually spawned as VillagerEntityMCA entities
- Verify hardcore mode is properly detected
- Check that family tree relationships are correctly established
