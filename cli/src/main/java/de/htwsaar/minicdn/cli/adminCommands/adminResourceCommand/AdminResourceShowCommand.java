package de.htwsaar.minicdn.cli.adminCommands.adminResourceCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "show", description = "Show resource details")
public class AdminResourceShowCommand implements Runnable {

    @Option(names = "--id", required = true, description = "Resource ID")
    long id;

    @Override
    public void run() {
        // TODO: ResourceService.show(id)
        System.out.printf("[ADMIN] Show resource %d%n", id);
    }
}
