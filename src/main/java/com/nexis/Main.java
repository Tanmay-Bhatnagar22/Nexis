package com.nexis;

import com.nexis.cli.CliUI;
import com.nexis.cli.NexisCLI;

import picocli.CommandLine;

public class Main {

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new NexisCLI());
        if (CliUI.isAnsi()) {
            cmd.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.ON));
        } else {
            cmd.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF));
        }
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}
