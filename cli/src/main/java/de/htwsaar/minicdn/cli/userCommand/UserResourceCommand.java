package de.htwsaar.minicdn.cli.userCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "resource",
        description = "View resources owned by the current user",
        subcommands = {
            UserResourceCommand.UserResourceListCommand.class,
            UserResourceCommand.UserResourceShowCommand.class
        })
public class UserResourceCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "list", description = "List resources owned by the current user")
    public static class UserResourceListCommand implements Runnable {

        @Override
        public void run() {
            // TODO: ResourceService.listByCurrentUser()
            System.out.println("[USER] List my resources");
        }
    }

    @Command(name = "show", description = "Show details for one of my resources")
    public static class UserResourceShowCommand implements Runnable {

        @Option(names = "--id", required = true, description = "Resource ID")
        long id;

        @Override
        public void run() {
            // TODO: ResourceService.showForCurrentUser(id)
            System.out.printf("[USER] Show my resource %d%n", id);
        }
    }
}
