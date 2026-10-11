package net.serverplugins.warps;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerWarpsPlugin extends JavaPlugin {
    private WarpService service;
    private WarpTeleporter teleporter;
    private CommandBindings bindings;
    private String error;

    @Override
    public void onEnable() {
        teleporter = new EssentialsTeleportBridge(getServer());
        bindings = new CommandBindings(this);
        WarpCommand executor = new WarpCommand(this);
        for (String name : java.util.List.of("pwarp", "setwarp", "delwarp", "warps")) {
            var command = Objects.requireNonNull(getCommand(name));
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        var admin = Objects.requireNonNull(getCommand("pwarpadmin"));
        WarpAdminCommand administration = new WarpAdminCommand(this);
        admin.setExecutor(administration);
        admin.setTabCompleter(administration);
        try {
            saveDefaultConfig();
            reloadWarps();
        } catch (Exception exception) {
            error = exception.getMessage();
            bindings.apply(true);
        }
    }

    @Override
    public void onDisable() {
        if (bindings != null) {
            bindings.restore();
        }
    }

    WarpService service() {
        return service;
    }

    WarpTeleporter teleporter() {
        return teleporter;
    }

    String error() {
        return error;
    }

    boolean feedback() {
        return service != null && service.settings().commandFeedback();
    }

    void reloadWarps() throws Exception {
        WarpSettings replacement = readSettings();
        WarpStore store = new WarpStore(getDataFolder().toPath().resolve("warps.yml"));
        service = new WarpService(store, replacement);
        error = null;
        bindings.apply(replacement.claimCommands());
    }

    private WarpSettings readSettings() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File(getDataFolder(), "config.yml"));
        try (var input = new InputStreamReader(Objects.requireNonNull(getResource("config.yml")), StandardCharsets.UTF_8)) {
            config.setDefaults(YamlConfiguration.loadConfiguration(input));
            config.options().copyDefaults(true);
        }
        return WarpSettings.load(config);
    }
}
