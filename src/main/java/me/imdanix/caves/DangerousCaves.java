package me.imdanix.caves;

import me.imdanix.caves.caverns.AmbientSounds;
import me.imdanix.caves.caverns.CaveIns;
import me.imdanix.caves.caverns.CavesAging;
import me.imdanix.caves.caverns.DepthHypoxia;
import me.imdanix.caves.commands.Commander;
import me.imdanix.caves.configuration.Configuration;
import me.imdanix.caves.mobs.MobsManager;
import me.imdanix.caves.placeholders.DCExpansion;
import me.imdanix.caves.ticks.Dynamics;

import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class DangerousCaves extends JavaPlugin {

    private MobsManager mobsManager;
    private Dynamics dynamics;
    private Configuration cfg;
    public static DangerousCaves instance;

    public DangerousCaves() {
    	instance = this;
    }

    public static DangerousCaves getInstance() {
    	return instance;
    }

    @Override
    public void onLoad() {
        if (getDescription().getVersion().contains("SNAPSHOT")) {
            getLogger().warning("Thank you for using dev-build of the plugin! But please note that this version may " +
                    "contain bugs. If you found some - report it to https://github.com/imDaniX/Dangerous-Сaves-2/issues");
        }
    }

    @Override
    public void onEnable() {
        dynamics = new Dynamics(this);
        cfg = new Configuration(this, "config", YamlConfiguration.loadConfiguration(Objects.requireNonNull(getTextResource("plugin.yml"))).getString("config-version", "0"));
        cfg.create(true);
        mobsManager = new MobsManager(this, cfg, dynamics);
        DefaultMobs.registerAll(mobsManager);

        AmbientSounds ambient = new AmbientSounds();
        CaveIns caveIns = new CaveIns();
        CavesAging cavesAging = new CavesAging(this);
        DepthHypoxia hypoxia = new DepthHypoxia(this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            DCExpansion expansion = new DCExpansion(cfg);
            expansion.register(hypoxia.getPlaceholder());
            expansion.register();
        }

        getServer().getPluginManager().registerEvents(mobsManager, this);
        getServer().getPluginManager().registerEvents(caveIns, this);

        dynamics.register(mobsManager);
        dynamics.register(ambient);
        dynamics.register(cavesAging);
        dynamics.register(hypoxia);

        cfg.register(mobsManager);
        cfg.register(ambient);
        cfg.register(cavesAging);
        cfg.register(caveIns);
        cfg.register(hypoxia);

        Objects.requireNonNull(getCommand("dangerouscaves")).setExecutor(new Commander(this));

        cfg.checkVersion(true);

        new Metrics(this, 6824);
    }

    public MobsManager getMobs() {
        return mobsManager;
    }

    public Dynamics getDynamics() {
        return dynamics;
    }

    public Configuration getConfiguration() {
        return cfg;
    }
}
