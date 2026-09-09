package com.nexis.cli;

import java.io.PrintWriter;
import java.util.Objects;

import picocli.CommandLine;

/**
 * Dedicated presentation and UI/UX component for Nexis CLI.
 *
 * <p>Encapsulates all visual styling, Unicode symbols, ANSI color formatting,
 * startup banner, and help screen rendering, keeping presentation logic strictly
 * decoupled from core security and integrity business logic.
 */
public final class CliUI {

    public static final String SYMBOL_SUCCESS = "✓";
    public static final String SYMBOL_WARNING = "!";
    public static final String SYMBOL_ERROR   = "✗";
    public static final String SYMBOL_INFO    = "→";

    // Standard ANSI Escape Codes
    private static final String RESET       = "\u001B[0m";
    private static final String BOLD        = "\u001B[1m";
    private static final String RED         = "\u001B[31m";
    private static final String GREEN       = "\u001B[32m";
    private static final String YELLOW      = "\u001B[33m";
    private static final String CYAN        = "\u001B[36m";

    public static final String BANNER = """
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│   ███╗   ██╗███████╗██╗  ██╗██╗███████╗                     │
│   ████╗  ██║██╔════╝╚██╗██╔╝██║██╔════╝                     │
│   ██╔██╗ ██║█████╗   ╚███╔╝ ██║███████╗                     │
│   ██║╚██╗██║██╔══╝   ██╔██╗ ██║╚════██║                     │
│   ██║ ╚████║███████╗██╔╝ ██╗██║███████║                     │
│   ╚═╝  ╚═══╝╚══════╝╚═╝  ╚═╝╚═╝╚══════╝                     │
│                                                              │
│          File Integrity & Host Security Monitor             │
│                 Nexis — version 1.0.0                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘""";

    private CliUI() {
        // Prevent instantiation of utility class
    }

    /**
     * Checks if ANSI colors are enabled for the current runtime environment.
     *
     * @return true if ANSI escape sequences are supported and enabled
     */
    public static boolean isAnsi() {
        return CommandLine.Help.Ansi.AUTO.enabled();
    }

    public static String successMarker() {
        return isAnsi() ? GREEN + SYMBOL_SUCCESS + RESET : SYMBOL_SUCCESS;
    }

    public static String warningMarker() {
        return isAnsi() ? YELLOW + SYMBOL_WARNING + RESET : SYMBOL_WARNING;
    }

    public static String errorMarker() {
        return isAnsi() ? RED + SYMBOL_ERROR + RESET : SYMBOL_ERROR;
    }

    public static String infoMarker() {
        return isAnsi() ? CYAN + SYMBOL_INFO + RESET : SYMBOL_INFO;
    }

    public static String success(String message) {
        return successMarker() + " " + message;
    }

    public static String warning(String message) {
        return warningMarker() + " " + message;
    }

    public static String error(String message) {
        return errorMarker() + " " + message;
    }

    public static String info(String message) {
        return infoMarker() + " " + message;
    }

    public static String bold(String text) {
        return isAnsi() ? BOLD + text + RESET : text;
    }

    public static String cyan(String text) {
        return isAnsi() ? CYAN + text + RESET : text;
    }

    public static String green(String text) {
        return isAnsi() ? GREEN + text + RESET : text;
    }

    public static String yellow(String text) {
        return isAnsi() ? YELLOW + text + RESET : text;
    }

    public static String red(String text) {
        return isAnsi() ? RED + text + RESET : text;
    }

    /**
     * Renders the stylized startup screen when Nexis is launched without arguments.
     *
     * @param out output writer
     */
    public static void printStartupScreen(PrintWriter out) {
        Objects.requireNonNull(out, "PrintWriter cannot be null");
        if (isAnsi()) {
            out.println(CYAN + BANNER + RESET);
        } else {
            out.println(BANNER);
        }
        out.println();
        out.println("  " + success("System ready"));
        out.println("  " + success("Integrity engine loaded"));
        out.println("  " + success("Monitoring engine loaded"));
        out.println();
        out.println("  Type 'nexis help' to get started.");
        out.println();
        out.flush();
    }

    /**
     * Renders the clean, security-oriented help screen.
     *
     * @param out output writer
     */
    public static void printHelp(PrintWriter out) {
        Objects.requireNonNull(out, "PrintWriter cannot be null");
        out.println("Nexis - File Integrity & Host Security Monitor");
        out.println();
        out.println("Usage:");
        out.println("  nexis <command> [options]");
        out.println();
        out.println("Commands:");
        out.println();
        out.println("  baseline    Create or update the integrity baseline for a directory");
        out.println("  scan        Scan a directory and compare against the integrity baseline");
        out.println("  watch       Monitor a directory in real-time for file system changes");
        out.println("  report      Generate a security event investigation report");
        out.println();
        out.println("Also show:");
        out.println();
        out.println("  nexis --help");
        out.println("  nexis --version");
        out.println();
        out.flush();
    }
}

