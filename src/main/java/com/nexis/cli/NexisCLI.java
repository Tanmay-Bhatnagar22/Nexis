package com.nexis.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    name = "nexis",
    version = "1.0.0",
    description = "File Integrity & Host Monitor",
    mixinStandardHelpOptions = true,
    subcommands = { BaselineCommand.class, ScanCommand.class, WatchCommand.class, ReportCommand.class }
)
public class NexisCLI implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

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

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new NexisCLI()).execute(args);
        System.exit(exitCode);
    }
}
