package de.htwsaar.minicdn.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "minicdn",
        mixinStandardHelpOptions = true,
        subcommands = {PingCommand.class})
public class MiniCdnCli implements Runnable {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new MiniCdnCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Bitte einen Befehl angeben:");
    }
}
