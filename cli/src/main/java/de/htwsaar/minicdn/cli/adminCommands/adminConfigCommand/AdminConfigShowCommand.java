package de.htwsaar.minicdn.cli.adminCommands.adminConfigCommand;

import picocli.CommandLine.Command;

@Command(name = "show", description = "Show global configuration")
public class AdminConfigShowCommand implements Runnable {

    @Override
    public void run() {
        // TODO: ConfigService.show()
        System.out.println("[ADMIN] Show global config");
    }
}