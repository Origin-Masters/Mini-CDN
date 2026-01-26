package de.htwsaar.minicdn.cli.userCommand.userResourceCommand;

import picocli.CommandLine.Command;

@Command(name = "list", description = "List resources owned by the current user")
public class UserResourceListCommand implements Runnable {

    @Override
    public void run() {
        // TODO: ResourceService.listByCurrentUser()
        System.out.println("[USER] List my resources");
    }
}
