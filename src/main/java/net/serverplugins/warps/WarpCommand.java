package net.serverplugins.warps;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class WarpCommand implements CommandExecutor, TabCompleter {
    private final PlayerWarpsPlugin plugin;

    WarpCommand(PlayerWarpsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            feedback(sender, "Only players can use player warps.");
            return true;
        }
        WarpService service = plugin.service();
        if (service == null) {
            feedback(player, "Player warps are unavailable. Ask staff to check /pwarpadmin status.");
            return true;
        }
        try {
            switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "setwarp" -> {
                    exact(args, 1);
                    service.create(player, args[0]);
                    feedback(player, "Player warp created.");
                }
                case "delwarp" -> {
                    exact(args, 1);
                    service.delete(player, args[0]);
                    feedback(player, "Player warp deleted.");
                }
                case "warps" -> showList(player, service, false, page(args, 0));
                default -> handle(player, service, args);
            }
        } catch (Exception exception) {
            feedback(player, exception instanceof java.io.IOException
                    ? "Warp storage is unavailable. Ask staff to check /pwarpadmin status." : exception.getMessage());
        }
        return true;
    }

    private void handle(Player player, WarpService service, String[] args) throws Exception {
        if (args.length == 0) {
            showList(player, service, false, 1);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set", "create" -> {
                exact(args, 2);
                service.create(player, args[1]);
                feedback(player, "Player warp created.");
            }
            case "delete", "remove" -> {
                exact(args, 2);
                service.delete(player, args[1]);
                feedback(player, "Player warp deleted.");
            }
            case "move" -> {
                exact(args, 2);
                service.move(player, args[1]);
                feedback(player, "Player warp moved to your location.");
            }
            case "rename" -> {
                exact(args, 3);
                service.rename(player, args[1], args[2]);
                feedback(player, "Player warp renamed.");
            }
            case "list" -> showList(player, service, false, page(args, 1));
            case "mine" -> showList(player, service, true, page(args, 1));
            case "status" -> {
                exact(args, 1);
                int limit = service.limit(player);
                reply(player, "Rank: " + service.settings().rank(player::hasPermission).name()
                        + " | Owned warps: " + service.owned(player) + " | Limit: " + (limit < 0 ? "unlimited" : limit));
            }
            case "help" -> {
                exact(args, 1);
                reply(player, "/pwarp <name> | set <name> | delete <name> | move <name> | rename <name> <new-name> | list [page] | mine [page] | status");
            }
            default -> {
                exact(args, 1);
                var destination = service.destination(player, args[0], plugin.getServer());
                plugin.teleporter().teleport(player, destination).whenComplete((success, exception) -> {
                    Runnable response = () -> {
                        if (player.isOnline()) {
                            if (exception != null) {
                                feedback(player, "Teleportation failed or is restricted.");
                            } else if (Boolean.TRUE.equals(success)) {
                                feedback(player, "Teleported to " + WarpSettings.normalize(args[0]) + ".");
                            }
                        }
                    };
                    if (Bukkit.isPrimaryThread()) {
                        response.run();
                    } else if (plugin.isEnabled()) {
                        plugin.getServer().getScheduler().runTask(plugin, response);
                    }
                });
            }
        }
    }

    private void showList(Player player, WarpService service, boolean ownOnly, int requestedPage) {
        if (!player.hasPermission("playerwarps.use")) {
            throw new IllegalArgumentException("You do not have permission to list player warps.");
        }
        List<PlayerWarp> warps = service.list(player, ownOnly);
        int pageSize = service.settings().pageSize();
        int pages = Math.max(1, (warps.size() + pageSize - 1) / pageSize);
        int page = Math.min(requestedPage, pages);
        int start = (page - 1) * pageSize;
        List<String> names = warps.subList(start, Math.min(start + pageSize, warps.size())).stream()
                .map(warp -> warp.name() + " (" + warp.ownerName() + ")").toList();
        reply(player, (ownOnly ? "Your warps" : "Player warps") + " | Page " + page + "/" + pages
                + ": " + (names.isEmpty() ? "none" : String.join(", ", names)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || plugin.service() == null || args.length == 0) {
            return List.of();
        }
        WarpService service = plugin.service();
        List<String> options = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("setwarp") || name.equals("warps")) {
            return List.of();
        }
        if (name.equals("delwarp")) {
            if (args.length == 1 && player.hasPermission("playerwarps.manage")) {
                service.list(player, true).forEach(warp -> options.add(warp.name()));
            }
        } else if (args.length == 1) {
            options.add("help");
            options.add("status");
            if (player.hasPermission("playerwarps.use")) {
                options.addAll(List.of("list", "mine"));
                service.list(player, false).forEach(warp -> options.add(warp.name()));
            }
            if (player.hasPermission("playerwarps.create")) {
                options.add("set");
            }
            if (player.hasPermission("playerwarps.manage")) {
                options.addAll(List.of("delete", "move", "rename"));
            }
        } else if (args.length == 2 && player.hasPermission("playerwarps.manage")
                && List.of("delete", "remove", "move", "rename").contains(args[0].toLowerCase(Locale.ROOT))) {
            service.list(player, true).forEach(warp -> options.add(warp.name()));
        }
        String prefix = WarpSettings.normalize(args[args.length - 1]);
        return options.stream().filter(option -> option.startsWith(prefix)).distinct().sorted().limit(50).toList();
    }

    private void feedback(CommandSender sender, String message) {
        if (plugin.feedback() && message != null) {
            reply(sender, message);
        }
    }

    private static void reply(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }

    private static int page(String[] args, int offset) {
        if (args.length == offset) {
            return 1;
        }
        if (args.length != offset + 1) {
            throw new IllegalArgumentException("Specify one page number.");
        }
        int page;
        try {
            page = Integer.parseInt(args[offset]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Page must be a positive number.");
        }
        if (page < 1) {
            throw new IllegalArgumentException("Page must be a positive number.");
        }
        return page;
    }

    private static void exact(String[] args, int count) {
        if (args.length != count) {
            throw new IllegalArgumentException("Use /pwarp help for command syntax.");
        }
    }
}
