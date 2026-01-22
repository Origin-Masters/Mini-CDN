package de.htwsaar.minicdn.cli;

import de.htwsaar.minicdn.cli.adminCommands.AdminUserMgmtCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "admin",
        description = "mini-Cdn Administration",
        subcommands = {AdminUserMgmtCommand.class})
public class AdminCommand {}
