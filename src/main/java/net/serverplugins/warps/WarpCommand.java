package net.serverplugins.warps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
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
            reply(sender, "player-only");
            return true;
        }
        WarpService service = plugin.service();
        if (service == null) {
            reply(player, "unavailable");
            return true;
        }
        try {
            switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "setwarp" -> {
                    exact(args, 1);
                    service.create(player, args[0]);
                    feedback(player, "created", "warp", WarpSettings.normalize(args[0]));
                }
                case "delwarp" -> {
                    exact(args, 1);
                    service.delete(player, args[0]);
                    feedback(player, "deleted", "warp", WarpSettings.normalize(args[0]));
                }
                case "warps" -> showList(player, service, false, page(args, 0));
                default -> handle(player, service, args);
            }
        } catch (WarpProblem problem) {
            reply(player, problem.key(), problem.placeholders());
        } catch (IOException exception) {
            reply(player, "storage-error");
        } catch (Exception exception) {
            reply(player, "command-error");
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
                feedback(player, "created", "warp", WarpSettings.normalize(args[1]));
            }
            case "delete", "remove" -> {
                exact(args, 2);
                service.delete(player, args[1]);
                feedback(player, "deleted", "warp", WarpSettings.normalize(args[1]));
            }
            case "move" -> {
                exact(args, 2);
                service.move(player, args[1]);
                feedback(player, "moved", "warp", WarpSettings.normalize(args[1]));
            }
            case "rename" -> {
                exact(args, 3);
                service.rename(player, args[1], args[2]);
                feedback(player, "renamed", "old", WarpSettings.normalize(args[1]),
                        "new", WarpSettings.normalize(args[2]));
            }
            case "list" -> showList(player, service, false, page(args, 1));
            case "mine" -> showList(player, service, true, page(args, 1));
            case "status" -> {
                exact(args, 1);
                int limit = service.limit(player);
                reply(player, "status", "rank", service.settings().rank(player::hasPermission).name(),
                        "owned", service.owned(player), "limit", limit < 0 ? plugin.messages().format("unlimited") : limit);
            }
            case "help" -> {
                exact(args, 1);
                reply(player, "help");
            }
            default -> {
                exact(args, 1);
                var destination = service.destination(player, args[0], plugin.getServer());
                plugin.teleporter().teleport(player, destination).whenComplete((success, exception) -> {
                    Runnable response = () -> {
                        if (plugin.isEnabled() && player.isOnline()) {
                            if (exception != null) {
                                Throwable cause = exception;
                                while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                                        && cause.getCause() != null) {
                                    cause = cause.getCause();
                                }
                                if (cause instanceof WarpProblem problem) {
                                    reply(player, problem.key(), problem.placeholders());
                                } else {
                                    reply(player, "teleport-failed", "warp", WarpSettings.normalize(args[0]));
                                }
                            } else if (Boolean.TRUE.equals(success)) {
                                feedback(player, "teleported", "warp", WarpSettings.normalize(args[0]));
                            } else {
                                reply(player, "teleport-cancelled", "warp", WarpSettings.normalize(args[0]));
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
            throw new WarpProblem("no-permission", "permission", "playerwarps.use");
        }
        List<PlayerWarp> warps = service.list(player, ownOnly);
        if (warps.isEmpty()) {
            reply(player, ownOnly ? "mine-empty" : "list-empty");
            return;
        }
        int pageSize = service.settings().pageSize();
        int pages = Math.max(1, (warps.size() + pageSize - 1) / pageSize);
        int page = Math.min(requestedPage, pages);
        int start = (page - 1) * pageSize;
        List<String> names = warps.subList(start, Math.min(start + pageSize, warps.size())).stream()
                .map(warp -> plugin.messages().format("list-entry", "warp", warp.name(), "owner", warp.ownerName())).toList();
        reply(player, ownOnly ? "list-mine" : "list-public", "page", page, "pages", pages,
                "total", warps.size(), "warps", String.join(plugin.messages().format("list-separator"), names));
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

    private void feedback(CommandSender sender, String key, Object... placeholders) {
        if (plugin.feedback()) {
            reply(sender, key, placeholders);
        }
    }

    private void reply(CommandSender sender, String key, Object... placeholders) {
        plugin.messages().send(sender, key, placeholders);
    }

    private static int page(String[] args, int offset) {
        if (args.length == offset) {
            return 1;
        }
        if (args.length != offset + 1) {
            throw new WarpProblem("invalid-page-arguments");
        }
        int page;
        try {
            page = Integer.parseInt(args[offset]);
        } catch (NumberFormatException exception) {
            throw new WarpProblem("invalid-page");
        }
        if (page < 1) {
            throw new WarpProblem("invalid-page");
        }
        return page;
    }

    private static void exact(String[] args, int count) {
        if (args.length != count) {
            throw new WarpProblem("usage");
        }
    }
}
