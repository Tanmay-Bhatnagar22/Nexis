package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import com.nexis.baseline.BaselineManager;
import com.nexis.scanner.FileScanner;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * CLI subcommand that creates or updates the integrity baseline for a target directory.
 */
@Command(
    name = "baseline",
    description = "Create or update the integrity baseline for a directory",
    mixinStandardHelpOptions = true
)
public class BaselineCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory to baseline")
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

        try {
            FileScanner scanner = new FileScanner();
            List<Path> files = scanner.scan(directory);

            BaselineManager manager = new BaselineManager();
            manager.addOrUpdateFiles(files);
            manager.save();

            out.println();
            out.println("  NEXIS BASELINE CREATED");
            out.println("  Target:    " + directory.toAbsolutePath().normalize());
            out.println("  Files:     " + files.size());
            out.println("  Saved to:  " + manager.getBaselinePath().toAbsolutePath().normalize());
            out.println();
            return 0;

        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
