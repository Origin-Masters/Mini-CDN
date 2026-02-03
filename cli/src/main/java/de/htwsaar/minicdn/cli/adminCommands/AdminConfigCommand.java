package de.htwsaar.minicdn.cli.adminCommands;

import de.htwsaar.minicdn.cli.service.AdminConfigService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "config",
        description = "Manage global configuration",
        subcommands = {AdminConfigCommand.AdminConfigSetCommand.class, AdminConfigCommand.AdminConfigShowCommand.class})
public class AdminConfigCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "set", description = "Set a global configuration value")
    public static class AdminConfigSetCommand implements Runnable {

        @Option(names = "--key", required = true, description = "Configuration key")
        String key;

        @Option(names = "--value", required = true, description = "Configuration value")
        String value;

        @Override
        public void run() {
            boolean ok = AdminConfigService.set(key, value);
            if (ok) {
                System.out.printf("[ADMIN] Set config %s=%s%n", key, value);
            } else {
                System.out.printf("[ADMIN] Failed to set config %s%n", key);
            }
        }
    }

    @Command(name = "show", description = "Show global configuration")
    public static class AdminConfigShowCommand implements Runnable {

        @Override
        public void run() {
            AdminConfigService.show();
            System.out.println("[ADMIN] Show global config");
        }
    }
}
