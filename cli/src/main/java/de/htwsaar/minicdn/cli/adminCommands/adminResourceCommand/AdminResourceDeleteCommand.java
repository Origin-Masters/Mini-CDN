package de.htwsaar.minicdn.cli.adminCommands.adminResourceCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "delete", description = "Delete a resource")
public class AdminResourceDeleteCommand implements Runnable {

    @Option(names = "--id", required = true, description = "Resource ID")
    long id;

    @Override
    public void run() {
        // TODO: ResourceService.delete(id)
        System.out.printf("[ADMIN] Delete resource %d%n", id);
    }
}
