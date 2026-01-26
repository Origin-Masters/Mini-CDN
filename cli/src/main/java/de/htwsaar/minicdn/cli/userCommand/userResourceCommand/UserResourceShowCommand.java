package de.htwsaar.minicdn.cli.userCommand.userResourceCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "show", description = "Show details for one of my resources")
public class UserResourceShowCommand implements Runnable {

    @Option(names = "--id", required = true, description = "Resource ID")
    long id;

    @Override
    public void run() {
        // TODO: ResourceService.showForCurrentUser(id)
        System.out.printf("[USER] Show my resource %d%n", id);
    }
}