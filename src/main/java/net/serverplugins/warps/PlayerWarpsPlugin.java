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
    private WarpMessages messages;
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
            YamlConfiguration defaults = readDefaults();
            messages = WarpMessages.load(defaults, defaults);
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

    WarpMessages messages() {
        return messages;
    }

    void reloadWarps() throws Exception {
        YamlConfiguration defaults = readDefaults();
        YamlConfiguration config = readConfiguration(defaults);
        WarpSettings replacement = WarpSettings.load(config);
        WarpMessages replacementMessages = WarpMessages.load(config, defaults);
        if (service == null) {
            messages = replacementMessages;
        }
        WarpStore store = new WarpStore(getDataFolder().toPath().resolve("warps.yml"));
        mergeMessages(config, defaults);
        service = new WarpService(store, replacement);
        messages = replacementMessages;
        error = null;
        bindings.apply(replacement.claimCommands());
    }

    private YamlConfiguration readConfiguration(YamlConfiguration defaults) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File(getDataFolder(), "config.yml"));
        if (!config.contains("messages.no-permission", true)) {
            config.set("messages.command-feedback", true);
        }
        config.setDefaults(defaults);
        config.options().copyDefaults(true);
        return config;
    }

    private YamlConfiguration readDefaults() throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        try (var input = new InputStreamReader(Objects.requireNonNull(getResource("config.yml")), StandardCharsets.UTF_8)) {
            defaults.load(input);
        }
        return defaults;
    }

    private void mergeMessages(YamlConfiguration config, YamlConfiguration defaults) throws Exception {
        boolean changed = false;
        var section = Objects.requireNonNull(defaults.getConfigurationSection("messages"));
        for (var entry : section.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String || entry.getValue() instanceof Boolean) {
                String path = "messages." + entry.getKey();
                if (!config.contains(path, true)) {
                    config.set(path, entry.getValue());
                    changed = true;
                }
            }
        }
        if (changed) {
            config.save(new File(getDataFolder(), "config.yml"));
        }
    }
}
