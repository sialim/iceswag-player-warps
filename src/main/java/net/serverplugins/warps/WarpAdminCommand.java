package net.serverplugins.warps;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

final class WarpAdminCommand implements CommandExecutor, TabCompleter {
    private final PlayerWarpsPlugin plugin;

    WarpAdminCommand(PlayerWarpsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playerwarps.admin")) {
            reply(sender, "You do not have permission to manage player warps.");
            return true;
        }
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadWarps();
                reply(sender, "Player warp configuration and data reloaded.");
            } else if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
                WarpService service = plugin.service();
                reply(sender, service == null ? "Unavailable: " + plugin.error()
                        : "Player warps: " + service.size() + " | Public: " + service.settings().publicWarps()
                        + " | Claims /warp: " + service.settings().claimCommands() + " | Essentials: "
                        + plugin.getServer().getPluginManager().isPluginEnabled("Essentials"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
                if (plugin.service() == null) {
                    throw new IllegalStateException("Player warp storage is unavailable.");
                }
                plugin.service().delete(args[1]);
                reply(sender, "Player warp deleted.");
            } else {
                reply(sender, "Usage: /pwarpadmin status | reload | delete <name>");
            }
        } catch (Exception exception) {
            reply(sender, "Unable to complete command: " + exception.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("playerwarps.admin") || args.length != 1) {
            return List.of();
        }
        return List.of("status", "reload", "delete").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }

    private static void reply(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }
}
