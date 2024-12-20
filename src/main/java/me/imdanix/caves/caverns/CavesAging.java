package me.imdanix.caves.caverns;

import io.papermc.lib.PaperLib;
import me.imdanix.caves.configuration.Configurable;
import me.imdanix.caves.ticks.TickLevel;
import me.imdanix.caves.ticks.Tickable;
import me.imdanix.caves.util.Locations;
import me.imdanix.caves.util.Materials;
import me.imdanix.caves.util.Utils;
import me.imdanix.caves.util.bound.Bound;
import me.imdanix.caves.util.random.Rng;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class CavesAging implements Tickable, Configurable {
    private static final Set<Material> AGING_MATERIALS = new Materials.Builder(
            Material.COBBLESTONE, Material.STONE_BUTTON,
            Material.ANDESITE, Material.COBBLESTONE_WALL,
            Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.VINE
    ).build(true);

    private final Plugin plugin;
    private final Map<String, Set<Bound>> skippedChunks; // TODO Make part of RegionManager instead
    private final Set<String> worlds;

    private boolean disabled;

    private Set<Material> replaceBlocks;
    private int lightLevel;
    private int radius;
    private int yMax;
    private double chance;
    private double agingChance;
    private int schedule;
    private boolean forceLoad;

    private double torchRemove;

    private double percentage;
    private Predicate<Block> lightLevelCheck;

    private boolean withVines;
    private boolean withRocks;
    private boolean withMushrooms;
    private boolean withReplace;

    public CavesAging(Plugin plugin) {
        this.plugin = plugin;
        skippedChunks = new HashMap<>();
        worlds = new HashSet<>();
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        radius = Math.max(cfg.getInt("radius", 3), 1);
        yMax = cfg.getInt("y-max", 80);
        chance = cfg.getDouble("chance", 50) / 100;
        agingChance = cfg.getDouble("change-chance", 2.5) / 100;
        lightLevel = cfg.getInt("max-light-level", -1);
        if (lightLevel >= 0) {
            lightLevelCheck = (b) -> {
                for (BlockFace face : Locations.HORIZONTAL_FACES)
                    if (b.getRelative(face).getLightFromBlocks() >= lightLevel) return false;
                return true;
            };
        } else {
            lightLevelCheck = (b) -> true;
        }
        schedule = Math.max(cfg.getInt("schedule-timer", 4), 1);
        forceLoad = cfg.getBoolean("force-load", true);
        torchRemove = cfg.getDouble("torch-remove-chance", 40) / 100;
        Utils.fillWorlds(cfg.getStringList("worlds"), worlds);
        skippedChunks.clear();
        ConfigurationSection boundsCfg = cfg.getConfigurationSection("skip-chunks");
        if (boundsCfg != null) {
            for (String worldStr : boundsCfg.getKeys(false)) {
                Set<Bound> worldBounds = new HashSet<>();
                for (String str : boundsCfg.getStringList(worldStr)) {
                    Bound bound = Bound.fromString(str);
                    if (bound != null) worldBounds.add(bound);
                }
                skippedChunks.put(worldStr, worldBounds);
            }
        }

        replaceBlocks = Materials.getSet(cfg.getStringList("replace-blocks"));
        percentage = cfg.getDouble("percentage", 30) / 100;

        withReplace = cfg.getBoolean("age-types.replace", true);
        withRocks = cfg.getBoolean("age-types.rocks", true);
        withMushrooms = cfg.getBoolean("age-types.mushrooms", true);
        withVines = cfg.getBoolean("age-types.vines", true);

        disabled = !cfg.getBoolean("enabled", true);
    }

    @Override
    public void tick() {
        if (disabled) return;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            for (String worldName : worlds) {
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                Set<QueuedChunk> chunks = new HashSet<>();
                for (Player player : world.getPlayers()) {
                    if (!player.isOnline() || player.getWorld() != world) continue;
                    Location startLoc = player.getLocation();
                    int xCenter = startLoc.getBlockX() >> 4;
                    int zCenter = startLoc.getBlockZ() >> 4;
                    for (int x = xCenter - radius, xMax = xCenter + radius; x <= xMax; x++) {
                        for (int z = zCenter - radius, zMax = zCenter + radius; z <= zMax; z++) {
                            if (isAllowed(world, x, z)) {
                                chunks.add(new QueuedChunk(x, z));
                            }
                        }
                    }
                }
                if (chunks.isEmpty()) continue;
                Utils.runIteratingTask(plugin, chunks, (queuedChunk) -> {
                    Chunk chunk = queuedChunk.getChunk(world);
                    if (chunk == null || !chunk.isLoaded()) {
                        if (forceLoad) {
                            PaperLib.getChunkAtAsync(world, queuedChunk.x, queuedChunk.z).thenAccept(
                                    this::proceedChunk
                            );
                        }
                    } else proceedChunk(chunk);
                }, schedule);
            }
        });
    }

    private boolean isAllowed(World world, int x, int z) {
        Set<Bound> worldBounds = skippedChunks.get(world.getName());
        if (worldBounds == null) return true;
        for (Bound bound : worldBounds)
            if (bound.isInside(x, z)) return false;
        return true;
    }

    private void proceedChunk(Chunk chunk) {
        Location edge = chunk.getBlock(0, chunk.getWorld().getMinHeight(), 0).getLocation();
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        int minHeight = chunk.getWorld().getMinHeight();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!Rng.chance(chance)) return;
            List<DelayedChange> changes = calculateChanges(edge, snapshot, minHeight);
            if (changes.isEmpty()) return;

            plugin.getServer().getScheduler().runTask(plugin, () -> changes.forEach(change -> change.perform(chunk)));
        });
    }

    private List<DelayedChange> calculateChanges(Location edge, ChunkSnapshot snapshot, int minHeight) {
        List<DelayedChange> changes = new ArrayList<>();

        int affectedCount = 0;
        int totalCount = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = minHeight + 2; y <= yMax; y++) { // TODO Configurable aging script
            Material type = snapshot.getBlockType(x, y, z);
            if (type.isAir()) continue;

            totalCount++;

            if (AGING_MATERIALS.contains(type)) {
                affectedCount++;
            }

            if (snapshot.getBlockSkyLight(x, y, z) > 0) {
                break; // We're breaking Y for-loop, as there's no reason to check further
            }

            if (lightLevel >= 0 && (
                    snapshot.getBlockEmittedLight(x, y, z) >= lightLevel ||
                    snapshot.getBlockEmittedLight(x, y+1, z) >= lightLevel ||
                    snapshot.getBlockEmittedLight(x, y-1, z) >= lightLevel
            )) continue;

            if (type == Material.TORCH) {
                if (torchRemove > 0 && Rng.chance(torchRemove)) {
                    changes.add(new DelayedChange(x, y, z, ChangeType.TORCH_AIR));
                }
            } else if (replaceBlocks.contains(type) && Rng.chance(agingChance)) {
                if (withReplace) {
                    switch (Rng.nextInt(6)) {
                        case 0:
                            changes.add(new DelayedChange(x, y, z, ChangeType.ANDESITE));
                            break;

                        case 1:
                            changes.add(new DelayedChange(x, y, z, ChangeType.COBBLESTONE));
                            break;

                        case 2:
                            if (snapshot.getBlockType(x, y-1, z).isAir() && Rng.nextBoolean())
                                changes.add(new DelayedChange(x, y-1, z, ChangeType.STALAGMITE));
                            break;
                    }
                }

                if (withVines && Rng.chance(0.125)) {
                    changes.add(new DelayedChange(x, y, z, ChangeType.VINE));
                }

                if (snapshot.getBlockType(x, y+1, z).isAir()){
                    if (withMushrooms && Rng.chance(0.111)) {
                        changes.add(new DelayedChange(x, y+1, z, Rng.nextBoolean() ? ChangeType.RED_MUSHROOM : ChangeType.BROWN_MUSHROOM));
                    } else if (withRocks && Rng.chance(0.167)) {
                        changes.add(new DelayedChange(x, y+1, z, ChangeType.ROCK));
                    }
                }
            }
        }

        return affectedCount / (double) ++totalCount > percentage ? Collections.emptyList() : changes;
    }

    @Override
    public TickLevel getTickLevel() {
        return TickLevel.WORLD;
    }

    @Override
    public String getConfigPath() {
        return "caverns.aging";
    }

    private class DelayedChange {
        private final int x;
        private final int y;
        private final int z;
        private final ChangeType changeType;

        public DelayedChange(int x, int y, int z, ChangeType changeType) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.changeType = changeType;
        }
    
        public void perform(Chunk chunk) {
            Block block = chunk.getBlock(x, y, z);
            Material type = block.getType();
            if (!lightLevelCheck.test(block)) return;
            switch (changeType) {
                case VINE -> {
                    if (!replaceBlocks.contains(type)) return;
                    for (BlockFace face : Locations.HORIZONTAL_FACES) {
                        Block relBlock = block.getRelative(face);
                        if (relBlock.getType().isAir()) {
                            relBlock.setType(Material.VINE, false);
                            Materials.rotate(relBlock, face.getOppositeFace());
                        }
                    }
                }
                case RED_MUSHROOM -> {
                    if (!type.isAir() || !Materials.isCave(block.getRelative(BlockFace.DOWN).getType())) return;
                    if (block.getLightLevel() > 12) return;
                    block.setType(Material.RED_MUSHROOM, false);
                }
                case BROWN_MUSHROOM -> {
                    if (!type.isAir() || !Materials.isCave(block.getRelative(BlockFace.DOWN).getType())) return;
                    if (block.getLightLevel() > 12) return;
                    block.setType(Material.BROWN_MUSHROOM, false);
                }
                case ROCK -> {
                    if (!type.isAir() || !Materials.isCave(block.getRelative(BlockFace.DOWN).getType()))
                        return;
                    block.setType(Material.STONE_BUTTON, false);
                    Materials.rotate(block, BlockFace.UP);
                }
                case STALAGMITE -> {
                    if (!type.isAir()) return;
                    block.setType(Material.COBBLESTONE_WALL, false);
                }
                case COBBLESTONE -> {
                    if (!replaceBlocks.contains(type)) return;
                    block.setType(type == Material.DEEPSLATE ? Material.COBBLED_DEEPSLATE : Material.COBBLESTONE, false);
                }
                case ANDESITE -> {
                    if (!replaceBlocks.contains(type)) return;
                    block.setType(Material.ANDESITE, false);
                }
                case TORCH_AIR -> {
                    if (type != Material.TORCH) return;
                    block.setType(Material.AIR, false);
                }
            }
        }
    }
    
    private enum ChangeType {
        VINE, RED_MUSHROOM, BROWN_MUSHROOM, ROCK, STALAGMITE, COBBLESTONE, ANDESITE, TORCH_AIR
    }

    private record QueuedChunk(int x, int z) {
        public Chunk getChunk(World world) {
            return world.isChunkLoaded(x, z) ? world.getChunkAt(x, z) : null;
        }
    }
}
