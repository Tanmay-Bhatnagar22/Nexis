package com.nexis.cli;

import java.io.PrintWriter;
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
    subcommands = { BaselineCommand.class, ScanCommand.class }
)
public class NexisCLI implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

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
