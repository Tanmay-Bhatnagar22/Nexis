package com.nexis.cli;

import java.io.PrintWriter;
import java.util.Objects;

import picocli.CommandLine;

/**
 * Dedicated presentation and UI/UX component for Nexis CLI.
 *
 * <p>Encapsulates visual styling, ASCII markers, ASCII separators, ANSI color
 * formatting, startup banner, and help screen rendering, keeping presentation logic
 * strictly decoupled from core security and integrity business logic.
 *
 * <p>All output is strictly 7-bit US-ASCII compliant to guarantee clean rendering
 * without mojibake across Windows PowerShell, Windows CMD, VS Code integrated terminal,
 * and standard Java console environments.
 */
public final class CliUI {

    // ASCII Status Markers
    public static final String SYMBOL_SUCCESS  = "[OK]";
    public static final String SYMBOL_INFO     = "[INFO]";
    public static final String SYMBOL_WARNING  = "[WARN]";
    public static final String SYMBOL_ERROR    = "[ERROR]";
    public static final String SYMBOL_CRITICAL = "[CRITICAL]";

    // Standard ASCII Separators
    public static final String SEPARATOR_DOUBLE = "===============================================================";
    public static final String SEPARATOR_SINGLE = "---------------------------------------------------------------";
    public static final String SEPARATOR_DOT    = "...............................................................";

    // Standard ANSI Escape Codes (optional visual styling)
    private static final String RESET       = "\u001B[0m";
    private static final String BOLD        = "\u001B[1m";
    private static final String RED         = "\u001B[31m";
    private static final String GREEN       = "\u001B[32m";
    private static final String YELLOW      = "\u001B[33m";
    private static final String CYAN        = "\u001B[36m";

    public static final String BANNER = """
+--------------------------------------------------------------+
|                                                              |
|                         NEXIS                                |
|                                                              |
|          File Integrity & Host Security Monitor              |
|                    Version 1.0.0                             |
|                                                              |
+--------------------------------------------------------------+""";

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

    public static String infoMarker() {
        return isAnsi() ? CYAN + SYMBOL_INFO + RESET : SYMBOL_INFO;
    }

    public static String warningMarker() {
        return isAnsi() ? YELLOW + SYMBOL_WARNING + RESET : SYMBOL_WARNING;
    }

    public static String errorMarker() {
        return isAnsi() ? RED + SYMBOL_ERROR + RESET : SYMBOL_ERROR;
    }

    public static String criticalMarker() {
        return isAnsi() ? RED + BOLD + SYMBOL_CRITICAL + RESET : SYMBOL_CRITICAL;
    }

    public static String success(String message) {
        return successMarker() + " " + message;
    }

    public static String info(String message) {
        return infoMarker() + " " + message;
    }

    public static String warning(String message) {
        return warningMarker() + " " + message;
    }

    public static String error(String message) {
        return errorMarker() + " " + message;
    }

    public static String critical(String message) {
        return criticalMarker() + " " + message;
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
     * Renders the stylized ASCII startup screen when Nexis is launched without arguments.
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
     * Renders the clean, security-oriented custom help screen.
     *
     * @param out output writer
     */
    public static void printHelp(PrintWriter out) {
        Objects.requireNonNull(out, "PrintWriter cannot be null");
        out.println("NEXIS - File Integrity & Host Security Monitor");
        out.println("Version 1.0.0");
        out.println();
        out.println("USAGE");
        out.println("  nexis <command> [options]");
        out.println();
        out.println("COMMANDS");
        out.println();
        out.println("  baseline    Create or update the integrity baseline");
        out.println("  scan        Scan files against the stored baseline");
        out.println("  watch       Monitor a directory for real-time changes");
        out.println("  report      View and manage security events");
        out.println();
        out.println("OPTIONS");
        out.println();
        out.println("  --help      Show this help message");
        out.println("  --version   Show Nexis version");
        out.println();
        out.println("EXAMPLES");
        out.println();
        out.println("  nexis baseline C:\\Users\\Tanmay\\Documents");
        out.println("  nexis scan C:\\Users\\Tanmay\\Documents");
        out.println("  nexis watch C:\\Users\\Tanmay\\Documents");
        out.println("  nexis report");
        out.println();
        out.flush();
    }
}
