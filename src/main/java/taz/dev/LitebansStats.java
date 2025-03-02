package taz.dev;

import litebans.api.Database;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class LitebansStats extends JavaPlugin {
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        if (config.getBoolean("settings.enable-bstats", true)) {
            Metrics metrics = new Metrics(this, 24969);
            metrics.addCustomChart(new SingleLineChart("players", () ->
                    getServer().getOnlinePlayers().size()
            ));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("adminstats")) {
            if (!sender.hasPermission("litebansstats.view")) {
                String noPermMsg = config.getString("messages.no-permission", "&cYou don't have permission to use this command!");
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermMsg));
                return true;
            }

            if (args.length != 1) {
                return false;
            }

            String targetAdmin = args[0];

            CompletableFuture.supplyAsync(() -> getAdminStats(targetAdmin))
                    .thenAccept(stats -> getServer().getScheduler().runTask(this, () -> {
                        String headerFormat = config.getString("messages.stats-header", "&6===== &f{player}'s Admin Stats &6=====");
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                headerFormat.replace("{player}", targetAdmin)));

                        if (config.getBoolean("stats.bans") && stats.containsKey("bans")) {
                            sendStat(sender, "Bans", stats.get("bans"));
                        }
                        if (config.getBoolean("stats.kicks") && stats.containsKey("kicks")) {
                            sendStat(sender, "Kicks", stats.get("kicks"));
                        }
                        if (config.getBoolean("stats.warns") && stats.containsKey("warns")) {
                            sendStat(sender, "Warns", stats.get("warns"));
                        }
                        if (config.getBoolean("stats.mutes") && stats.containsKey("mutes")) {
                            sendStat(sender, "Mutes", stats.get("mutes"));
                        }
                        if (config.getBoolean("stats.unwarns") && stats.containsKey("unwarns")) {
                            sendStat(sender, "Unwarns", stats.get("unwarns"));
                        }
                        if (config.getBoolean("stats.unbans") && stats.containsKey("unbans")) {
                            sendStat(sender, "Unbans", stats.get("unbans"));
                        }

                        String footerFormat = config.getString("messages.stats-footer", "&6==========================");
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', footerFormat));
                    }))
                    .exceptionally(throwable -> {
                        getLogger().severe("Error fetching admin stats: " + throwable.getMessage());
                        sender.sendMessage(ChatColor.RED + "An error occurred while fetching the statistics.");
                        return null;
                    });

            return true;
        }
        return false;
    }

    private void sendStat(CommandSender sender, String statName, int count) {
        String format = config.getString("messages.stats-format", "&7{stat}: &f{count}");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                format.replace("{stat}", statName).replace("{count}", String.valueOf(count))));
    }

    private Map<String, Integer> getAdminStats(String adminName) {
        Map<String, Integer> stats = new HashMap<>();

        try {
            if (config.getBoolean("stats.bans")) {
                stats.put("bans", countActions("{bans}", adminName, false));
            }
            if (config.getBoolean("stats.kicks")) {
                stats.put("kicks", countActions("{kicks}", adminName, false));
            }
            if (config.getBoolean("stats.warns")) {
                stats.put("warns", countActions("{warnings}", adminName, false));
            }
            if (config.getBoolean("stats.mutes")) {
                stats.put("mutes", countActions("{mutes}", adminName, false));
            }
            if (config.getBoolean("stats.unwarns")) {
                stats.put("unwarns", countActions("{warnings}", adminName, true));
            }
            if (config.getBoolean("stats.unbans")) {
                stats.put("unbans", countActions("{bans}", adminName, true));
            }
        } catch (SQLException e) {
            getLogger().severe("Error fetching admin stats: " + e.getMessage());
        }

        return stats;
    }

    private int countActions(String table, String adminName, boolean removed) throws SQLException {
        String query = removed ?
                "SELECT COUNT(*) FROM " + table + " WHERE removed_by_name=?" :
                "SELECT COUNT(*) FROM " + table + " WHERE banned_by_name=?";

        try (PreparedStatement st = Database.get().prepareStatement(query)) {
            st.setString(1, adminName);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}