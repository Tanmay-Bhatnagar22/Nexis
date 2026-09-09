package com.nexis.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Tests for CLI presentation layer, startup screen, and help presentation.
 */
class CliUITest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("1. Launching without arguments does not crash, exits 0, and displays startup screen")
    void launchWithoutArgumentsDisplaysStartupScreen() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        Path workspace = tempDir.resolve("workspace");
        NexisCLI app = new NexisCLI(workspace);
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute();

        assertEquals(0, exitCode, "Exit code should be 0 when launched without arguments");
        String output = out.toString();

        // Required assertions from spec
        assertTrue(output.contains("Nexis"), "Startup output must contain 'Nexis'");
        assertTrue(output.contains("version"), "Startup output must contain 'version'");
        assertTrue(output.contains("File Integrity & Host Security Monitor"),
            "Startup output must contain 'File Integrity & Host Security Monitor'");
        assertTrue(output.contains("System ready"), "Startup output must contain 'System ready'");

        // Engine and getting started guidance
        assertTrue(output.contains("Integrity engine loaded"), "Startup output should confirm integrity engine");
        assertTrue(output.contains("Monitoring engine loaded"), "Startup output should confirm monitoring engine");
        assertTrue(output.contains("Type 'nexis help' to get started."),
            "Startup output should guide user to 'nexis help'");

        // Verify no files were created merely by launching without a command
        assertFalse(Files.exists(workspace.resolve("data").resolve("baseline.json")),
            "Launching without arguments must not create baseline file");
        assertFalse(Files.exists(workspace.resolve("data").resolve("events.json")),
            "Launching without arguments must not create events file");
        assertFalse(Files.exists(workspace.resolve("logs").resolve("nexis.log")),
            "Launching without arguments must not create log file");
    }

    @Test
    @DisplayName("2. '--help' and '-h' display clean Nexis help screen")
    void helpFlagDisplaysCleanHelp() {
        for (String flag : new String[]{"--help", "-h"}) {
            StringWriter out = new StringWriter();
            StringWriter err = new StringWriter();

            NexisCLI app = new NexisCLI(tempDir.resolve("workspace"));
            CommandLine cmd = new CommandLine(app);
            cmd.setOut(new PrintWriter(out));
            cmd.setErr(new PrintWriter(err));

            int exitCode = cmd.execute(flag);

            assertEquals(0, exitCode, "Help flag should exit with code 0");
            String output = out.toString();

            assertTrue(output.contains("Nexis - File Integrity & Host Security Monitor"),
                "Help should contain title header");
            assertTrue(output.contains("Usage:"), "Help should contain Usage heading");
            assertTrue(output.contains("nexis <command> [options]"), "Help should show synopsis");
            assertTrue(output.contains("Commands:"), "Help should contain Commands heading");
            assertTrue(output.contains("baseline"), "Help should list baseline command");
            assertTrue(output.contains("scan"), "Help should list scan command");
            assertTrue(output.contains("watch"), "Help should list watch command");
            assertTrue(output.contains("report"), "Help should list report command");
            assertTrue(output.contains("Also show:"), "Help should contain 'Also show:' footer");
            assertTrue(output.contains("nexis --help"), "Help should mention nexis --help");
            assertTrue(output.contains("nexis --version"), "Help should mention nexis --version");
        }
    }

    @Test
    @DisplayName("3. 'help' subcommand displays clean Nexis help screen")
    void helpSubcommandDisplaysCleanHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI(tempDir.resolve("workspace"));
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("help");

        assertEquals(0, exitCode, "help subcommand should exit with code 0");
        String output = out.toString();

        assertTrue(output.contains("Nexis - File Integrity & Host Security Monitor"),
            "Help should contain title header");
        assertTrue(output.contains("Usage:"), "Help should contain Usage heading");
        assertTrue(output.contains("Commands:"), "Help should contain Commands heading");
        assertTrue(output.contains("baseline"), "Help should list baseline command");
        assertTrue(output.contains("scan"), "Help should list scan command");
        assertTrue(output.contains("watch"), "Help should list watch command");
        assertTrue(output.contains("report"), "Help should list report command");
    }

    @Test
    @DisplayName("4. 'help <subcommand>' displays specific subcommand help")
    void helpSubcommandWithArgumentDisplaysSubcommandHelp() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI(tempDir.resolve("workspace"));
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("help", "scan");

        assertEquals(0, exitCode, "help scan should exit with code 0");
        String output = out.toString();
        assertTrue(output.contains("scan"), "Output should contain scan usage");
    }

    @Test
    @DisplayName("5. 'help <unknown>' reports error and exits with code 1")
    void helpUnknownCommandReportsError() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        NexisCLI app = new NexisCLI(tempDir.resolve("workspace"));
        CommandLine cmd = new CommandLine(app);
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        int exitCode = cmd.execute("help", "foobar");

        assertEquals(1, exitCode, "help with unknown command should exit with code 1");
        assertTrue(err.toString().contains("Unknown command 'foobar'"),
            "Error output should mention unknown command");
    }

    @Test
    @DisplayName("6. '--version' and '-V' print version information")
    void versionFlagsPrintVersion() {
        for (String flag : new String[]{"--version", "-V"}) {
            StringWriter out = new StringWriter();
            StringWriter err = new StringWriter();

            NexisCLI app = new NexisCLI(tempDir.resolve("workspace"));
            CommandLine cmd = new CommandLine(app);
            cmd.setOut(new PrintWriter(out));
            cmd.setErr(new PrintWriter(err));

            int exitCode = cmd.execute(flag);

            assertEquals(0, exitCode, "Version flag should exit with code 0");
            assertTrue(out.toString().contains("1.0.0"), "Version output should contain 1.0.0");
        }
    }

    @Test
    @DisplayName("7. Visual markers and status formatters generate expected symbols")
    void visualMarkersGenerateExpectedSymbols() {
        assertEquals("✓", CliUI.SYMBOL_SUCCESS);
        assertEquals("!", CliUI.SYMBOL_WARNING);
        assertEquals("✗", CliUI.SYMBOL_ERROR);
        assertEquals("→", CliUI.SYMBOL_INFO);

        assertTrue(CliUI.success("test").contains("✓ test"));
        assertTrue(CliUI.warning("test").contains("! test"));
        assertTrue(CliUI.error("test").contains("✗ test"));
        assertTrue(CliUI.info("test").contains("→ test"));
    }
}

