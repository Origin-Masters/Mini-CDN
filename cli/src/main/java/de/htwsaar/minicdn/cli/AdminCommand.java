package de.htwsaar.minicdn.cli;

import de.htwsaar.minicdn.cli.adminCommands.*;
import picocli.CommandLine.Command;

@Command(
        name = "admin",
        description = "mini-Cdn Administration",
        subcommands = {
            AdminResourceCommand.class,
            AdminNodeCommand.class,
            AdminUserMgmtCommand.class,
            AdminConfigCommand.class,
            PingCommand.class,
            SmokeTestCommand.class
        })
public class AdminCommand {}
