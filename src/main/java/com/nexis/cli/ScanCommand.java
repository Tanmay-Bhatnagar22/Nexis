package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.nexis.baseline.BaselineManager;
import com.nexis.baseline.BaselineStorageException;
import com.nexis.integrity.ComparisonEngine;
import com.nexis.integrity.ComparisonResult;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * CLI subcommand that performs an integrity scan against the stored baseline.
 */
@Command(
    name = "scan",
    description = "Scan a directory and compare against the integrity baseline",
    mixinStandardHelpOptions = true
)
public class ScanCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory to scan")
    private Path directory;

    @ParentCommand
    private NexisCLI parent;

    @Override
    public Integer call() {
        PrintWriter out = parent != null && parent.getOut() != null
            ? parent.getOut()
            : new PrintWriter(System.out, true);
        PrintWriter err = parent != null && parent.getErr() != null
            ? parent.getErr()
            : new PrintWriter(System.err, true);

        directory = directory.toAbsolutePath().normalize();

        if (!Files.exists(directory)) {
            err.println("Error: Directory does not exist: " + directory);
            return 1;
        }
        if (!Files.isDirectory(directory)) {
            err.println("Error: Path is not a directory: " + directory);
            return 1;
        }
        if (!Files.isReadable(directory)) {
            err.println("Error: Directory is not accessible: " + directory);
            return 1;
        }

        BaselineManager manager = new BaselineManager();
        try {
            manager.load();
        } catch (BaselineStorageException e) {
            err.println("Error: No baseline found. Run 'nexis baseline <directory>' first.");
            err.println("  Detail: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("Error: Failed to load baseline — " + e.getMessage());
            return 1;
        }

        try {
            ComparisonEngine engine = new ComparisonEngine();
            ComparisonResult result = engine.compare(directory, manager);

            ResultFormatter.format(result, directory, out);

            return result.isClean() ? 0 : 1;

        } catch (IOException e) {
            err.println("Error: Scan failed — " + e.getMessage());
            return 1;
        }
    }
}
