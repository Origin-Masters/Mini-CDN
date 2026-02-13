package de.htwsaar.minicdn.cli;

import java.io.PrintWriter;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.*;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.shell.jline3.PicocliCommands;

@Command(
        name = "",
        description = "Mini-CDN Interactive Shell",
        subcommands = {
            AdminCommand.class,
            UserCommand.class,
            CommandLine.HelpCommand.class // Add a built-in help command
        })
public class MiniCdnCli implements Runnable {

    public static void main(String[] args) {
        try {
            // Create terminal
            Terminal terminal = TerminalBuilder.builder().system(true).build();

            // Create a CommandLine instance
            MiniCdnCli app = new MiniCdnCli();
            CommandLine cmd = new CommandLine(app);
            PicocliCommands picocli = new PicocliCommands(cmd);

            // Configure LineReader with auto-completion
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(picocli.compileCompleters())
                    .parser(new DefaultParser())
                    .build();

            PrintWriter out = terminal.writer();

            // Welcome message
            out.println("=================================");
            out.println("  Mini-CDN Interactive Shell");
            out.println("  Type 'help' for available commands");
            out.println("  Type 'exit' to quit");
            out.println("=================================");
            out.flush();

            // Main loop
            while (true) {
                String line;
                try {
                    String prompt = new AttributedStringBuilder()
                            .style(AttributedStyle.BOLD.foreground(AttributedStyle.GREEN))
                            .append("mini cdn >> ")
                            .toAnsi();

                    line = reader.readLine(prompt);
                } catch (UserInterruptException e) {
                    // Ctrl+C
                    continue;
                } catch (EndOfFileException e) {
                    // Ctrl+D
                    break;
                }

                line = line.trim();

                // Exit command
                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    out.println("Goodbye!");
                    break;
                }

                // Clear screen command
                if (line.equalsIgnoreCase("clear") || line.equalsIgnoreCase("cls")) {
                    terminal.puts(InfoCmp.Capability.clear_screen);
                    terminal.flush();
                    continue;
                }

                // Execute command
                String[] cmdArgs = line.split("\\s+");
                int exitCode = cmd.execute(cmdArgs);

                // Optional: display execution result
                if (exitCode != 0) {
                    out.println("Command failed with exit code: " + exitCode);
                }
            }

        } catch (Exception e) {
            System.err.println("Error starting shell: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void run() {
        // Executed when the user presses enter without any command
        System.out.println("Please enter a command. Use 'help' to see available commands.");
    }
}
