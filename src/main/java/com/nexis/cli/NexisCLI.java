package com.nexis.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "nexis",
    version = "1.0.0",
    description = "File Integrity & Host Monitor",
    mixinStandardHelpOptions = true
)
public class NexisCLI implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new NexisCLI()).execute(args);
        System.exit(exitCode);
    }
}

