package de.htwsaar.minicdn.cli.adminCommands.adminConfigCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "set", description = "Set a global configuration value")
public class AdminConfigSetCommand implements Runnable {

    @Option(names = "--key", required = true, description = "Configuration key")
    String key;

    @Option(names = "--value", required = true, description = "Configuration value")
    String value;

    @Override
    public void run() {
        // TODO: ConfigService.set(key, value)
        System.out.printf("[ADMIN] Set config %s=%s%n", key, value);
    }
}