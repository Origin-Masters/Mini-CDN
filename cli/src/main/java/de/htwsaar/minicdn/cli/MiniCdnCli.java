package de.htwsaar.minicdn.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "minicdn",
        mixinStandardHelpOptions = true,
        description = "mini-Cdn CLI",
        subcommands = {AdminCommand.class, UserCommand.class})
public class MiniCdnCli implements Runnable {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new MiniCdnCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Bitte einen Befehl angeben:");
        CommandLine.usage(this, System.out); // show help when only "cdn" called
    }
}
