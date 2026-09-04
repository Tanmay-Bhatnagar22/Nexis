package com.nexis;

import com.nexis.cli.NexisCLI;

import picocli.CommandLine;

public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new NexisCLI()).execute(args);
        System.exit(exitCode);
    }
}
