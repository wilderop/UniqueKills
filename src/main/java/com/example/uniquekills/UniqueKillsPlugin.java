package com.example.uniquekills;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class UniqueKillsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private Map<UUID, Set<UUID>> uniqueKills = new HashMap<>();
    private File dataFile;

    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("topkiller").setExecutor(this);
        dataFile = new File(getDataFolder(), "data.yml");
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        loadData();
    }

    public void onDisable() {
        saveData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection killsSection = config.getConfigurationSection("kills");
        if (killsSection == null) {
            return;
        }
        for (String killerKey : killsSection.getKeys(false)) {
            UUID killerUUID = UUID.fromString(killerKey);
            List<String> victimStrings = killsSection.getStringList(killerKey);
            HashSet<UUID> victims = new HashSet<>();
            for (String victimStr : victimStrings) {
                victims.add(UUID.fromString(victimStr));
            }
            uniqueKills.put(killerUUID, victims);
        }
    }

    private void saveData() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> entry : uniqueKills.entrySet()) {
            ArrayList<String> victimStrings = new ArrayList<>();
            for (UUID victim : entry.getValue()) {
                victimStrings.add(victim.toString());
            }
            config.set("kills." + entry.getKey(), victimStrings);
        }
        try {
            config.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data file: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim.getKiller() instanceof Player) {
            Player killer = victim.getKiller();
            UUID killerUUID = killer.getUniqueId();
            UUID victimUUID = victim.getUniqueId();
            Set<UUID> victims = uniqueKills.computeIfAbsent(killerUUID, k -> new HashSet<>());
            if (victims.add(victimUUID)) {
                killer.sendMessage("You have claimed your first kill on " + victim.getName() + "!");
                getServer().broadcastMessage(killer.getName() + " has achieved a new unique kill: " + victim.getName() + "! Total unique kills: " + victims.size());
                saveData();
            }
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("topkiller")) {
            return false;
        }
        if (args.length == 0) {
            ArrayList<Map.Entry<UUID, Set<UUID>>> sortedKillers = new ArrayList<>(uniqueKills.entrySet());
            sortedKillers.sort(Comparator.comparingInt(entry -> entry.getValue().size()).reversed());
            sender.sendMessage("Top 10 Unique Killers:");
            for (int i = 0; i < Math.min(10, sortedKillers.size()); i++) {
                Map.Entry<UUID, Set<UUID>> entry = sortedKillers.get(i);
                OfflinePlayer killerPlayer = Bukkit.getOfflinePlayer(entry.getKey());
                String name = killerPlayer.getName();
                int count = entry.getValue().size();
                sender.sendMessage((i + 1) + ". " + name + " - " + count + " unique kills");
            }
        } else if (args.length == 1) {
            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target.hasPlayedBefore() || target.isOnline()) {
                Set<UUID> victims = uniqueKills.getOrDefault(target.getUniqueId(), new HashSet<>());
                sender.sendMessage(targetName + "'s Unique Kills (" + victims.size() + "):");
                for (UUID victimUUID : victims) {
                    OfflinePlayer victimPlayer = Bukkit.getOfflinePlayer(victimUUID);
                    sender.sendMessage("- " + victimPlayer.getName());
                }
            } else {
                sender.sendMessage("Player '" + targetName + "' not found or has never played on this server.");
            }
        } else {
            sender.sendMessage("Usage: /topkiller [player]");
        }
        return true;
    }
}
