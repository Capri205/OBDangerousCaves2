package me.imdanix.caves.caverns;

import me.imdanix.caves.DangerousCaves;
import me.imdanix.caves.configuration.Configurable;
import me.imdanix.caves.util.Locations;
import me.imdanix.caves.util.Materials;
import me.imdanix.caves.util.Utils;
import me.imdanix.caves.util.random.PseudoRandom;
import me.imdanix.caves.util.random.Rng;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

public class CaveIns implements Listener, Configurable {
	
	static Logger log = Logger.getLogger("Minecraft");

	DangerousCaves plugin;
	
    private static final PotionEffect BLINDNESS = new PotionEffect(PotionEffectType.BLINDNESS, 65, 3);
    private static final RandomGenerator PSEUDO_RANDOM = new PseudoRandom(new int[]{1, 0, -1, 0, 0, 1, 0, 1, 2, 0, 2, -1, 2, -1});

    private boolean disabled;

    private final Set<String> worlds;
    private double chance;
    private int yMax;
    private int radius;
    private boolean cuboid;
    private boolean slowFall;
    private boolean rabbitFoot;

    private boolean blastEffect;
    private boolean blastSound;

    private RandomGenerator noise;
    private int distance;
    private int[][] heightMap;

    public CaveIns() {
    	plugin = DangerousCaves.getInstance();
        worlds = new HashSet<>();
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        chance = cfg.getDouble("chance", 0.25) / 100;
        yMax = cfg.getInt("y-max", 25);
        radius = Math.max(cfg.getInt("radius", 6), 1);
        cuboid = cfg.getBoolean("cuboid", false);
        slowFall = cfg.getBoolean("slow-fall", false);
        rabbitFoot = cfg.getBoolean("rabbit-foot", true);

        blastEffect = cfg.getBoolean("blast-effect", true);
        blastSound = cfg.getBoolean("blast-sound", true);

        Utils.fillWorlds(cfg.getStringList("worlds"), worlds);

        noise = cuboid ? PseudoRandom.ZERO_PSEUDO_RANDOM : PSEUDO_RANDOM;
        distance = radius*2 + 1;
        heightMap = calcMap();

        disabled = !cfg.getBoolean("enabled", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (disabled) return;

        Block initBlock = event.getBlock();
        World world = initBlock.getWorld();
        if (initBlock.getY() > yMax || !worlds.contains(world.getName()) ||
                !Materials.isCave(initBlock.getType())) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || !Locations.isCave(player.getLocation()) ||
                (rabbitFoot && player.getInventory().contains(Material.RABBIT_FOOT))) return;

        if (Rng.chance(chance)) {
            Location blockLoc = initBlock.getLocation();
            if (blastSound) world.playSound(blockLoc, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
            if (blastEffect) player.addPotionEffect(BLINDNESS);

            List<List<Block>> allBlocks = getBlocksInRadius(blockLoc);

            if (slowFall) {
                // process off the main thread
            	doCaveIn(world, blockLoc, allBlocks);
            } else for (List<Block> blocks : allBlocks) {
                if (blocks.isEmpty()) continue;
                Block search = blocks.get(0).getRelative(BlockFace.DOWN);

                if (!search.isPassable()) continue;
                while (search.getY() > 0 && search.isPassable() && !(search = search.getRelative(BlockFace.DOWN)).getType().isSolid());

                for (Block block : blocks) {
                    search = search.getRelative(BlockFace.UP);
                    search.setType(block.getType());
                    block.setType(Material.AIR);
                }
            }
        }
    }
    
    private void doCaveIn(World world, Location blockLoc, List<List<Block>> allBlocks) {
    	new BukkitRunnable() {
    		@Override
    		public void run() {
    			allBlocks.forEach(l -> l.forEach(
    				block -> {
    					new BukkitRunnable() {
    						@Override
    						public void run() {
    							world.spawnFallingBlock(block.getLocation(), block.getBlockData());
    							block.setType(Material.GRAVEL);
    						}
    					}.runTask(plugin);
    				}
    			));
    		}
    	}.runTaskAsynchronously(plugin);
    }

    private List<List<Block>> getBlocksInRadius(Location blockLoc) {
        List<List<Block>> allBlocks = new ArrayList<>(distance*distance);
        World world = Objects.requireNonNull(blockLoc.getWorld());
        
        blockLoc.subtract(radius, 2, radius);
        if (blockLoc.getY() < world.getMinHeight() + 1) blockLoc.setY(world.getMinHeight() + 1);

        int yInit = blockLoc.getBlockY();

        for (int x = 0; x < distance; x++) for (int z = 0; z < distance; z++) {
            int xRel = blockLoc.getBlockX() + x;
            int zRel = blockLoc.getBlockZ() + z;

            List<Block> blocks = new ArrayList<>();

            int heightMax = heightMap[x][z] - noise.nextInt();
            for (int yOffset = 0; yOffset <= heightMax; yOffset++) {
                Block block = world.getBlockAt(xRel, yInit + yOffset, zRel);
                if (block.getType() == Material.BEDROCK) continue;
                if (!Materials.isCave(block.getType())) {
                    if (block.getType().isAir()) continue;
                    break;
                }
                blocks.add(block);
            }

            allBlocks.add(blocks);
        }

        return allBlocks;
    }

    private int[][] calcMap() {
        int[][] heightMap = new int[distance][distance];
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                heightMap[x+radius][z+radius] = cuboid
                        ? distance
                        : (((-x*x) + (-z*z))/radius + radius*2);
            }
        }

        return heightMap;
    }

    @Override
    public String getConfigPath() {
        return "caverns.ins";
    }

}
