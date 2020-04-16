/*
 * Dangerous Caves 2 | Make your caves scary
 * Copyright (C) 2020  imDaniX
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.imdanix.caves.mobs.defaults;

import me.imdanix.caves.configuration.Configurable;
import me.imdanix.caves.mobs.CustomMob;
import me.imdanix.caves.util.Utils;
import me.imdanix.caves.util.random.Rnd;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@Configurable.Path("mobs.tnt-creeper")
public class TNTCreeper extends CustomMob implements Configurable, Listener {
    private static final PotionEffect INCREASE_DAMAGE = new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 0, false, true);
    private int weight;
    private String name;
    private int tntAmount;
    private double explosionChance;

    public TNTCreeper() {
        super(EntityType.CREEPER, "tnt-creeper");
    }

    @Override
    public void reload(ConfigurationSection cfg) {
        weight = cfg.getInt("priority", 1);
        name = Utils.clr(cfg.getString("name", "&4TNT Creeper"));
        tntAmount = cfg.getInt("tnt-amount", 2);
        explosionChance = cfg.getDouble("explosion-chance", 33.33) / 100;
    }

    @Override
    public void setup(LivingEntity entity) {
        if(!name.isEmpty()) entity.setCustomName(name);
        entity.addPotionEffect(INCREASE_DAMAGE);
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        if(!isThis(event.getEntity())) return;
        LivingEntity entity = (LivingEntity) event.getEntity();
        entity.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        Location loc = entity.getLocation();
        for(int i = 0; i < tntAmount; i++) {
            Entity tnt = entity.getWorld().spawnEntity(loc, EntityType.PRIMED_TNT);
            tnt.setVelocity(new Vector(Rnd.nextDouble(2) - 1, 0.3, Rnd.nextDouble(2) - 1));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if(explosionChance > 0 && isThis(event.getEntity()) && Rnd.chance(explosionChance))
            event.getDamager().getWorld().createExplosion(event.getDamager().getLocation(), 0.01f);
    }

    @Override
    public int getWeight() {
        return weight;
    }
}
