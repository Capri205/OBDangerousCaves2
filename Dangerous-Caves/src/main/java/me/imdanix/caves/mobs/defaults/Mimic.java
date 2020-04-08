package me.imdanix.caves.mobs.defaults;

import me.imdanix.caves.compatibility.Compatibility;
import me.imdanix.caves.compatibility.VMaterial;
import me.imdanix.caves.compatibility.VSound;
import me.imdanix.caves.configuration.Configurable;
import me.imdanix.caves.mobs.TickableMob;
import me.imdanix.caves.util.Locations;
import me.imdanix.caves.util.Utils;
import me.imdanix.caves.util.random.Rnd;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

@Configurable.Path("mobs.mimic")
public class Mimic extends TickableMob implements Configurable, Listener {
    private static final PotionEffect BLINDNESS = new PotionEffect(PotionEffectType.BLINDNESS, 60, 1);
    private final List<Material> items;
    private int weight;
    private String name;

    private static final ItemStack CHEST;
    private static final ItemStack CHESTPLATE;
    private static final ItemStack BOOTS;
    private static final ItemStack LEGGINGS;
    private static final ItemStack PLANKS;
    static {
        CHEST = new ItemStack(Material.CHEST);
        CHESTPLATE = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) CHESTPLATE.getItemMeta();
        meta.setColor(Color.fromRGB(194, 105, 18));
        CHESTPLATE.setItemMeta(meta);
        BOOTS = new ItemStack(Material.LEATHER_LEGGINGS);
        BOOTS.setItemMeta(meta);
        LEGGINGS = new ItemStack(Material.LEATHER_BOOTS);
        LEGGINGS.setItemMeta(meta);
        PLANKS = new ItemStack(VMaterial.SPRUCE_PLANKS.get());
    }

    public Mimic() {
        super(EntityType.WITHER_SKELETON, "mimic");
        items = new ArrayList<>();
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        weight = cfg.getInt("weight", 0);
        name = Utils.clr(cfg.getString("name", "&4Mimic"));

        items.clear();
        List<String> itemsCfg = cfg.getStringList("drop-items");
        for(String materialStr : itemsCfg) {
            Material material = Material.getMaterial(materialStr.toUpperCase());
            if(material != null) items.add(material);
        }
    }

    @Override
    public void setup(LivingEntity entity) {
        if(!name.isEmpty()) entity.setCustomName(name);
        entity.setSilent(true);
        entity.setCanPickupItems(false);
        EntityEquipment equipment = entity.getEquipment();
        equipment.setItemInMainHand(PLANKS);
        equipment.setItemInOffHand(PLANKS);
        equipment.setChestplate(CHESTPLATE);    equipment.setChestplateDropChance(0);
        equipment.setLeggings(LEGGINGS);        equipment.setLeggingsDropChance(0);
        equipment.setBoots(BOOTS);              equipment.setBootsDropChance(0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if(block.getType() == Material.CHEST) {
            String tag = Compatibility.getTag(block);
            if(tag == null || !tag.startsWith("mimic-")) return;
            double health = Double.parseDouble(tag.substring(6));
            event.setCancelled(true);
            LivingEntity entity = (LivingEntity) block.getLocation().getWorld().spawnEntity(block.getLocation(), EntityType.WITHER_SKELETON);
            setup(entity);
            entity.setHealth(health);
            Player player = event.getPlayer();
            event.getPlayer().playSound(event.getPlayer().getEyeLocation(), VSound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR.get(), 1f, 0.5f);
            event.getPlayer().addPotionEffect(BLINDNESS);
            ((Monster)entity).setTarget(player);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if(isThis(entity)) entity.getLocation().getWorld().playSound(entity.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1f, 0.2f);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if(isThis(event.getEntity())) {
            event.setDeathSound(VSound.BLOCK_ENDER_CHEST_CLOSE.get());
            event.setDeathSoundPitch(0.2f);
            List<ItemStack> items = event.getDrops();
            items.clear();
            items.add(CHEST);
            items.add(new ItemStack(Rnd.randomItem(this.items)));
        }
    }

    @Override
    public void tick(LivingEntity entity) {
        Block block = entity.getLocation().getBlock();
        if(((Monster)entity).getTarget() == null && Compatibility.isAir(block.getType())) {
            block.setType(Material.CHEST, false);
            Compatibility.rotate(block, Locations.HORIZONTAL_FACES[Rnd.nextInt(4)]);
            Compatibility.setTag(block, "mimic-" + entity.getHealth());
            entity.remove();
        }
    }

    @Override
    public int getWeight() {
        return weight;
    }
}