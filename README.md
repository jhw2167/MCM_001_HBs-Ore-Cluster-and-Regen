# HB’s Ore Clusters and Regen

## Short Overview

**HB’s Ore Clusters and Regen** is a mod that spawns large clusters of ore in the world in addition to the small vanilla ore veins players are used to. These ore clusters also regenerate (configurable) after several days so the clusters can be harvested again, boosting resource supply and incentivizing the player to set up multiple small bases around the world, and interconnect them.

## Why would you want this?

<ul>
  <li>Finding a large "cluster" of ore is a rewarding discovery that makes exploring caves more fun</li>
  <li>If you think Gregtech Ore Generation style is cool and you also don’t want to lose the traditional vanilla ore veins</li>
  <li>Regenerating clusters incentivize players to create a true secondary mining base, and makes choosing the location of a base more important</li>
</ul>

## Which ores regenerate? How big are the clusters? What if I like normal Minecraft mining?

- This mod does not modify the existing vanilla ore generation at all—it finds an existing ore vein and creates a cluster out of it.
- Vanilla mining is great and should remain a core part of the game!
- All aspects of the clusters are configurable via a JSON config file. See the section below or view my GitHub page.
- In its base configuration:
  - Coal and iron generate in large clusters between `12x12x12` and `16x16x16`
  - Deepslate diamond ore also spawns in smaller clusters deeper underground, and is more rare than coal and iron

## Compatibility with other mods

- This mod works with **Terralith**, **Tectonic**, **Biomes O’ Plenty**, and all world generation mods
- It can form clusters for **any block** naturally generated in the world (including modded ores, beehives, spawner blocks, etc.)
- The mod can add new ores to world gen — BUT the mod MUST be able to find a block of the specified in the world in order to know where to spawn the cluster. For example: you could create an iron_ore cluster, and add custom_mod:custom_nickel as one of the replaceableEmptyBlocks of the cluster.

## Future Updates

Future updates will depend on feedback I receive on [my Discord](https://discord.gg/dp9d4wymNv). Between porting to other versions, adding features, and working on other mods, it's important I prioritize based on feedback from active users.

Planned features include:

- GUI for building ore cluster configurations
- Improved performance
- In-game block “Sacrificial Altar” to reduce regeneration time for clusters as a player progresses in their world
- Compatibility for modifying concentrations of existing ore veins
- Support for biome-specific configurations
- Support for multiple configurations for identical ore types
