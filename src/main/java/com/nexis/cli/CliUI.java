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

    // Standard ANSI Escape Codes
    private static final String RESET        = "\u001B[0m";
    private static final String BOLD         = "\u001B[1m";
    private static final String RED          = "\u001B[31m";
    private static final String GREEN        = "\u001B[32m";
    private static final String YELLOW       = "\u001B[33m";
    private static final String BLUE         = "\u001B[34m";
    private static final String CYAN         = "\u001B[36m";
    private static final String BRIGHT_RED   = "\u001B[91m";
    private static final String BRIGHT_CYAN  = "\u001B[96m";
    private static final String WHITE        = "\u001B[37m";
    private static final String BRIGHT_WHITE = "\u001B[97m";

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
     * <p>Respects standard conventions:
     * <ul>
     *   <li>{@code -Dpicocli.ansi=true|false} overrides all</li>
     *   <li>{@code NO_COLOR} disables ANSI</li>
     *   <li>{@code CLICOLOR=0} disables ANSI</li>
     *   <li>{@code CLICOLOR_FORCE=1} forces ANSI</li>
     *   <li>Disables ANSI inside test runners (Surefire / JUnit) to protect test assertions</li>
     *   <li>Enables ANSI in Windows 10/11 and modern terminal environments</li>
     * </ul>
     *
     * @return true if ANSI escape sequences are supported and enabled
     */
    public static boolean isAnsi() {
        String ansiProp = System.getProperty("picocli.ansi");
        if ("true".equalsIgnoreCase(ansiProp)) {
            return true;
        }
        if ("false".equalsIgnoreCase(ansiProp)) {
            return false;
        }

        String noColor = System.getenv("NO_COLOR");
        if (noColor != null && !noColor.isBlank()) {
            return false;
        }

        String cliColor = System.getenv("CLICOLOR");
        if ("0".equals(cliColor)) {
            return false;
        }

        String cliColorForce = System.getenv("CLICOLOR_FORCE");
        if (cliColorForce != null && !"0".equals(cliColorForce)) {
            return true;
        }

        // Keep ANSI disabled inside automated test runners to protect string assertions
        if (isTestRunner()) {
            return false;
        }

        if (CommandLine.Help.Ansi.AUTO.enabled()) {
            return true;
        }

        // On Windows 10/11 and VS Code terminals, ANSI VT100 sequences are natively supported
        String os = System.getProperty("os.name");
        if (os != null && os.toLowerCase().contains("win")) {
            return true;
        }

        String term = System.getenv("TERM");
        if (term != null && !term.equals("dumb")) {
            return true;
        }

        String termProgram = System.getenv("TERM_PROGRAM");
        return "vscode".equalsIgnoreCase(termProgram) || System.getenv("WT_SESSION") != null;
    }

    private static boolean isTestRunner() {
        if (System.getProperty("surefire.test.class.path") != null
            || System.getProperty("surefire.real.class.path") != null) {
            return true;
        }
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith("org.junit.") || className.startsWith("org.apache.maven.surefire.")) {
                return true;
            }
        }
        return false;
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
        return isAnsi() ? BRIGHT_RED + BOLD + SYMBOL_CRITICAL + RESET : SYMBOL_CRITICAL;
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

    public static String brightCyan(String text) {
        return isAnsi() ? BRIGHT_CYAN + text + RESET : text;
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

    public static String brightRed(String text) {
        return isAnsi() ? BRIGHT_RED + text + RESET : text;
    }

    public static String blue(String text) {
        return isAnsi() ? BLUE + text + RESET : text;
    }

    public static String white(String text) {
        return isAnsi() ? WHITE + text + RESET : text;
    }

    public static String brightWhite(String text) {
        return isAnsi() ? BRIGHT_WHITE + text + RESET : text;
    }

    /**
     * Renders the stylized ASCII startup screen when Nexis is launched without arguments.
     *
     * @param out output writer
     */
    public static void printStartupScreen(PrintWriter out) {
        Objects.requireNonNull(out, "PrintWriter cannot be null");
        if (isAnsi()) {
            out.println(CYAN + "+--------------------------------------------------------------+" + RESET);
            out.println(CYAN + "|" + RESET + "                                                              " + CYAN + "|" + RESET);
            out.println(CYAN + "|" + RESET + "                         " + BOLD + BRIGHT_CYAN + "NEXIS" + RESET + "                                " + CYAN + "|" + RESET);
            out.println(CYAN + "|" + RESET + "                                                              " + CYAN + "|" + RESET);
            out.println(CYAN + "|" + RESET + "          " + BRIGHT_WHITE + "File Integrity & Host Security Monitor" + RESET + "              " + CYAN + "|" + RESET);
            out.println(CYAN + "|" + RESET + "                    " + CYAN + "Version 1.0.0" + RESET + "                             " + CYAN + "|" + RESET);
            out.println(CYAN + "|" + RESET + "                                                              " + CYAN + "|" + RESET);
            out.println(CYAN + "+--------------------------------------------------------------+" + RESET);
        } else {
            out.println(BANNER);
        }
        out.println();
        out.println("  " + success("System ready"));
        out.println("  " + success("Integrity engine loaded"));
        out.println("  " + success("Monitoring engine loaded"));
        out.println();
        if (isAnsi()) {
            out.println(CYAN + "  Type 'nexis help' to get started." + RESET);
        } else {
            out.println("  Type 'nexis help' to get started.");
        }
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
        if (isAnsi()) {
            out.println(cyan("NEXIS - File Integrity & Host Security Monitor"));
            out.println(cyan("Version 1.0.0"));
            out.println();
            out.println(cyan(SEPARATOR_DOUBLE));
            out.println();
            out.println(cyan("USAGE"));
            out.println();
            out.println("  nexis <command> [options]");
            out.println();
            out.println(cyan("COMMANDS"));
            out.println();
            out.println("  " + bold("baseline") + "  Create or update the integrity baseline");
            out.println("  " + bold("scan") + "      Scan files against the stored baseline");
            out.println("  " + bold("watch") + "     Monitor a directory for real-time changes");
            out.println("  " + bold("report") + "    View and manage security events");
            out.println();
            out.println(cyan("OPTIONS"));
            out.println();
            out.println("  " + bold("--help") + "      Show this help message");
            out.println("  " + bold("--version") + "   Show Nexis version");
            out.println();
            out.println(cyan("EXAMPLES"));
            out.println();
            out.println("  " + cyan("nexis baseline C:\\Users\\Tanmay\\Documents"));
            out.println("  " + cyan("nexis scan C:\\Users\\Tanmay\\Documents"));
            out.println("  " + cyan("nexis watch C:\\Users\\Tanmay\\Documents"));
            out.println("  " + cyan("nexis report"));
            out.println();
        } else {
            out.println("NEXIS - File Integrity & Host Security Monitor");
            out.println("Version 1.0.0");
            out.println();
            out.println(SEPARATOR_DOUBLE);
            out.println();
            out.println("USAGE");
            out.println();
            out.println("  nexis <command> [options]");
            out.println();
            out.println("COMMANDS");
            out.println();
            out.println("  baseline  Create or update the integrity baseline");
            out.println("  scan      Scan files against the stored baseline");
            out.println("  watch     Monitor a directory for real-time changes");
            out.println("  report    View and manage security events");
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
        }
        out.flush();
    }
}
