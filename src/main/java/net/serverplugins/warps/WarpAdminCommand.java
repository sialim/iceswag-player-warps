package net.serverplugins.warps;

import java.util.List;
import java.util.Locale;
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
            reply(sender, "no-permission", "permission", "playerwarps.admin");
            return true;
        }
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadWarps();
                reply(sender, "admin-reloaded");
            } else if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
                WarpService service = plugin.service();
                if (service == null) {
                    reply(sender, "admin-unavailable", "error", plugin.error());
                } else {
                    reply(sender, "admin-status", "count", service.size(), "public", label(service.settings().publicWarps()),
                            "claims", label(service.settings().claimCommands()),
                            "essentials", label(plugin.getServer().getPluginManager().isPluginEnabled("Essentials")));
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
                if (plugin.service() == null) {
                    reply(sender, "storage-error");
                    return true;
                }
                plugin.service().delete(args[1]);
                reply(sender, "admin-deleted", "warp", WarpSettings.normalize(args[1]));
            } else {
                reply(sender, "admin-usage");
            }
        } catch (WarpProblem problem) {
            reply(sender, problem.key(), problem.placeholders());
        } catch (Exception exception) {
            reply(sender, "admin-error", "error", exception.getMessage());
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

    private String label(boolean enabled) {
        return plugin.messages().format(enabled ? "enabled-label" : "disabled-label");
    }

    private void reply(CommandSender sender, String key, Object... placeholders) {
        plugin.messages().send(sender, key, placeholders);
    }
}
