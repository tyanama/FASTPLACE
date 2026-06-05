package com.fastplace;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class FastPlacePlugin extends JavaPlugin {

    private FastPlaceListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        listener = new FastPlaceListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        getLogger().info("FastPlace v" + getDescription().getVersion() + " が有効になりました！");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            listener.clearAllSessions();
        }
        getLogger().info("FastPlace が無効になりました。");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("fastplace")) return false;

        if (args.length == 0) {
            sender.sendMessage("§6[FastPlace] §f使い方: /fastplace <reload|toggle|info>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("fastplace.admin")) {
                    sender.sendMessage("§c権限がありません。");
                    return true;
                }
                reloadConfig();
                listener.reloadConfig();
                sender.sendMessage("§6[FastPlace] §a設定をリロードしました。");
            }
            case "toggle" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cプレイヤーのみ使用できます。");
                    return true;
                }
                boolean newState = listener.togglePlayer(player);
                player.sendMessage("§6[FastPlace] §f連続設置: " + (newState ? "§aON" : "§cOFF"));
            }
            case "info" -> {
                sender.sendMessage("§6[FastPlace] §fバージョン: §a" + getDescription().getVersion());
                sender.sendMessage("§6[FastPlace] §f最大設置数: §a" + getConfig().getInt("max-blocks-per-drag"));
                sender.sendMessage("§6[FastPlace] §fブロック消費: §a" + (getConfig().getBoolean("consume-blocks") ? "有効" : "無効"));
            }
            default -> sender.sendMessage("§6[FastPlace] §f使い方: /fastplace <reload|toggle|info>");
        }
        return true;
    }
}
