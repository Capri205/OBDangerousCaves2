package me.imdanix.caves.mobs.defaults;

import me.imdanix.caves.mobs.CustomMob;
import me.imdanix.caves.mobs.MobBase;
import me.imdanix.caves.mobs.MobsManager;
import me.imdanix.caves.util.Locations;
import me.imdanix.caves.util.Materials;
import me.imdanix.caves.util.TagHelper;
import me.imdanix.caves.util.Utils;
import me.imdanix.caves.util.random.Rng;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

// TODO: Other blocks like furnace?
// TODO: Requires refactoring after uncompatibility
public class Mimic extends MobBase implements CustomMob.Ticking, Listener {
    private static final PotionEffect BLINDNESS = new PotionEffect(PotionEffectType.BLINDNESS, 60, 1);
    private static final ItemStack CHEST = new ItemStack(Material.CHEST);
    private static final ItemStack CHESTPLATE = Materials.getColored(EquipmentSlot.CHEST, 194, 105, 18);
    private static final ItemStack LEGGINGS = Materials.getColored(EquipmentSlot.LEGS, 194, 105, 18);
    private static final ItemStack BOOTS = Materials.getColored(EquipmentSlot.FEET, 194, 105, 18);
    private static final ItemStack PLANKS = new ItemStack(Material.SPRUCE_PLANKS);

    private final MobsManager mobsManager;
    private final NamespacedKey chunkKey;
    private List<Material> items;
    private boolean clean;
    private Listener unloadListener;

    public Mimic(MobsManager mobsManager) {
        super(EntityType.WITHER_SKELETON, "mimic", 0, 30d);
        this.mobsManager = mobsManager;
        items = new ArrayList<>();
        chunkKey = new NamespacedKey(mobsManager.getPlugin(), "mimic-count");
    }

    @Override
    protected void configure(ConfigurationSection cfg) {
        items = new ArrayList<>(Materials.getSet(cfg.getStringList("drop-items")));
        boolean skipPersistence = cfg.getBoolean("skip-persistence-check", false);
        clean = cfg.getBoolean("remove-on-unload", false);
        if (clean) {
            if (unloadListener == null) {
                Bukkit.getPluginManager().registerEvents(unloadListener = new Listener() {
                    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
                    public void onUnload(ChunkUnloadEvent event) {
                        if (!skipPersistence) {
                            PersistentDataContainer container = event.getChunk().getPersistentDataContainer();
                            if (!container.has(chunkKey, PersistentDataType.INTEGER)) return;
                            int amount = container.get(chunkKey, PersistentDataType.INTEGER);
                            for (BlockState tile : event.getChunk().getTileEntities()) {
                                if (!(tile instanceof Chest)) continue;
                                String tag = TagHelper.getTag(tile);
                                if (tag == null || !tag.startsWith("mimic")) continue;
                                tile.setType(Material.AIR);
                                if (--amount <= 0) break;
                            }
                            container.remove(chunkKey);
                        } else for (BlockState tile : event.getChunk().getTileEntities()) {
                            if (!(tile instanceof Chest)) continue;
                            String tag = TagHelper.getTag(tile);
                            if (tag == null || !tag.startsWith("mimic")) continue;
                            tile.setType(Material.AIR);
                        }
                    }
                }, mobsManager.getPlugin());
            }
        } else if (unloadListener != null) {
            HandlerList.unregisterAll(unloadListener);
            unloadListener = null;
        }
    }

    @Override
    public void prepare(LivingEntity entity) {
        entity.setSilent(true);
        entity.setCanPickupItems(false);
        EntityEquipment equipment = entity.getEquipment();
        equipment.setHelmet(CHEST);
        equipment.setItemInMainHand(PLANKS);
        equipment.setItemInOffHand(PLANKS);
        equipment.setChestplate(CHESTPLATE);    equipment.setChestplateDropChance(0);
        equipment.setLeggings(LEGGINGS);        equipment.setLeggingsDropChance(0);
        equipment.setBoots(BOOTS);              equipment.setBootsDropChance(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;
        Player player = event.getPlayer();
        for (BlockFace face : Locations.HORIZONTAL_FACES) {
            Block rel = block.getRelative(face);
            if (rel.getType() == Material.CHEST && openMimic(rel,player)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() == Material.CHEST && openMimic(block, event.getPlayer())) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    private boolean openMimic(Block block, Player player) {
        String tag = TagHelper.getTag(block.getState());
        if (tag == null || !tag.startsWith("mimic")) return false;
        if (!block.getRelative(BlockFace.UP).isPassable()) return true;
        block.setType(Material.AIR);
        double health = Utils.getDouble(tag.substring(6), this.health); // Safe because will be defined anyway
        if (health <= 0) health = 1;
        Location loc = block.getLocation();
        LivingEntity entity = mobsManager.spawn(this, loc.add(0.5, 0, 0.5));
        Utils.setMaxHealth(entity, this.health);
        entity.setHealth(Math.min(health, this.health));
        Locations.playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.5f);
        player.addPotionEffect(BLINDNESS);
        ((Monster) entity).setTarget(player);

        PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
        if (!container.has(chunkKey, PersistentDataType.INTEGER)) return true;
        int amount = container.get(chunkKey, PersistentDataType.INTEGER);
        if (amount == 1) {
            container.remove(chunkKey);
        } else {
            container.set(chunkKey, PersistentDataType.INTEGER, amount - 1);
        }
        return true;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (isThis(entity)) Locations.playSound(entity.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1f, 0.2f);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (isThis(event.getEntity())) {
            Locations.playSound(event.getEntity().getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, SoundCategory.HOSTILE, 1f, 0.2f);
            List<ItemStack> drops = event.getDrops();
            drops.clear();
            drops.add(CHEST);
            if (!items.isEmpty()) drops.add(new ItemStack(Rng.randomElement(items)));
        }
    }

    @Override
    public void tick(LivingEntity entity) {
        Block block = entity.getLocation().getBlock();
        if (((Monster)entity).getTarget() == null && block.getType().isAir()) {
            for (BlockFace face : Locations.HORIZONTAL_FACES)
                if (block.getRelative(face).getType() == Material.CHEST) return;
            block.setType(Material.CHEST, false);
            Materials.rotate(block, Rng.randomElement(Locations.HORIZONTAL_FACES));
            TagHelper.setTag(block.getState(), "mimic-" + entity.getHealth());
            entity.remove();

            if (clean) {
                PersistentDataContainer container = block.getChunk().getPersistentDataContainer();
                container.set(chunkKey, PersistentDataType.INTEGER, container.getOrDefault(chunkKey, PersistentDataType.INTEGER, 0) + 1);
            }
        }
    }
}