package de.htwsaar.minicdn.cli.adminCommands;

import picocli.CommandLine.Command;

@Command(name = "smokeTest", description = "Runs quick smoke tests against the CDN (stub).")
public class SmokeTestCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("[ADMIN] Running smoke tests (stub).");
    }
}
