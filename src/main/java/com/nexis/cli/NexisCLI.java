package com.nexis.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "nexis",
    version = "1.0.0",
    header = {
        "NEXIS - File Integrity & Host Security Monitor",
        "Version 1.0.0"
    },
    synopsisHeading = "%nUSAGE%n",
    customSynopsis = { "  nexis <command> [options]" },
    descriptionHeading = "",
    description = {},
    commandListHeading = "%nCOMMANDS%n%n",
    footerHeading = "%nOPTIONS%n%n",
    footer = {
        "  --help      Show this help message",
        "  --version   Show Nexis version",
        "",
        "EXAMPLES",
        "",
        "  nexis baseline C:\\Users\\Tanmay\\Documents",
        "  nexis scan C:\\Users\\Tanmay\\Documents",
        "  nexis watch C:\\Users\\Tanmay\\Documents",
        "  nexis report"
    },
    subcommands = {
        BaselineCommand.class,
        ScanCommand.class,
        WatchCommand.class,
        ReportCommand.class,
        NexisCLI.HelpSubcommand.class
    }
)
public class NexisCLI implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, hidden = true, description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(names = {"-V", "--version"}, versionHelp = true, hidden = true, description = "Print version information and exit.")
    private boolean versionRequested;

    private final Path baselinePath;
    private final Path logPath;
    private final Path eventsPath;

    public NexisCLI() {
        this(null, null, null);
    }

    public NexisCLI(Path workspaceDir) {
        this(
            workspaceDir != null ? workspaceDir.resolve("data").resolve("baseline.json") : null,
            workspaceDir != null ? workspaceDir.resolve("logs").resolve("nexis.log") : null,
            workspaceDir != null ? workspaceDir.resolve("data").resolve("events.json") : null
        );
    }

    public NexisCLI(Path baselinePath, Path logPath, Path eventsPath) {
        this.baselinePath = baselinePath;
        this.logPath = logPath;
        this.eventsPath = eventsPath;
    }

    public Path getBaselinePath() {
        return baselinePath;
    }

    public Path getLogPath() {
        return logPath;
    }

    public Path getEventsPath() {
        return eventsPath;
    }

    public CommandSpec getSpec() {
        return spec;
    }

    @Override
    public Integer call() {
        PrintWriter out = getOut() != null ? getOut() : new PrintWriter(System.out, true);
        CliUI.printStartupScreen(out);
        return 0;
    }

    /**
     * Returns the standard output writer from the picocli command spec.
     *
     * @return PrintWriter for standard output, or null if spec is unavailable
     */
    public PrintWriter getOut() {
        return spec != null ? spec.commandLine().getOut() : null;
    }

    /**
     * Returns the standard error writer from the picocli command spec.
     *
     * @return PrintWriter for standard error, or null if spec is unavailable
     */
    public PrintWriter getErr() {
        return spec != null ? spec.commandLine().getErr() : null;
    }

    @Command(
        name = "help",
        description = "Display help information about nexis",
        hidden = true
    )
    public static class HelpSubcommand implements Callable<Integer> {

        @ParentCommand
        private NexisCLI parent;

        @Parameters(index = "0", arity = "0..1", description = "Command to show help for", defaultValue = "")
        private String subcommand = "";

        @Override
        public Integer call() {
            PrintWriter out = parent != null && parent.getOut() != null
                ? parent.getOut()
                : new PrintWriter(System.out, true);

            if (subcommand != null && !subcommand.isBlank() && parent != null && parent.getSpec() != null) {
                CommandLine root = parent.getSpec().commandLine();
                CommandLine sub = root.getSubcommands().get(subcommand);
                if (sub != null) {
                    sub.usage(out);
                    return 0;
                } else {
                    PrintWriter err = parent.getErr() != null
                        ? parent.getErr()
                        : new PrintWriter(System.err, true);
                    err.println(CliUI.error("Unknown command '" + subcommand + "'. Type 'nexis help' to see available commands."));
                    return 1;
                }
            }

            CliUI.printHelp(out);
            return 0;
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new NexisCLI()).execute(args);
        System.exit(exitCode);
    }
}
