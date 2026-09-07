package com.nexis.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import com.nexis.alert.EventType;
import com.nexis.alert.SecurityEvent;
import com.nexis.alert.Severity;
import com.nexis.report.EventRepository;
import com.nexis.report.ReportGenerator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * CLI subcommand that generates security event investigation reports.
 *
 * <p>Usage:
 * <pre>
 *   nexis report
 *   nexis report --severity CRITICAL
 *   nexis report --type INTEGRITY_VIOLATION
 *   nexis report --path "C:\path\to\file.txt"
 *   nexis report --clear
 * </pre>
 */
@Command(
    name = "report",
    description = "Generate a security event investigation report",
    mixinStandardHelpOptions = true
)
public class ReportCommand implements Callable<Integer> {

    @Option(names = {"--severity", "-s"}, description = "Filter by severity (INFO, WARNING, CRITICAL, ERROR)")
    private String severityStr;

    @Option(names = {"--type", "-t"}, description = "Filter by event type (e.g. INTEGRITY_VIOLATION, FILE_CREATED, FILE_MODIFIED, FILE_DELETED)")
    private String typeStr;

    @Option(names = {"--path", "-p"}, description = "Filter by affected file path")
    private Path pathFilter;

    @Option(names = {"--clear"}, description = "Clear stored security events")
    private boolean clear;

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

        if (clear) {
            try {
                Files.deleteIfExists(EventRepository.DEFAULT_EVENTS_PATH);
                out.println("Security events cleared.");
                out.flush();
                return 0;
            } catch (IOException e) {
                err.println("Error: Failed to clear events — " + e.getMessage());
                return 1;
            }
        }

        Severity severity = null;
        if (severityStr != null && !severityStr.isBlank()) {
            try {
                severity = Severity.valueOf(severityStr.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                err.println("Error: Invalid severity '" + severityStr + "'. Allowed values: "
                    + Arrays.toString(Severity.values()));
                return 1;
            }
        }

        EventType eventType = null;
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                eventType = EventType.valueOf(typeStr.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                err.println("Error: Invalid event type '" + typeStr + "'. Allowed values: "
                    + Arrays.toString(EventType.values()));
                return 1;
            }
        }

        EventRepository repository = EventRepository.loadOrDefault();
        List<SecurityEvent> matchedEvents = repository.find(severity, eventType, pathFilter);

        ReportGenerator generator = new ReportGenerator();
        generator.generate(matchedEvents, out);

        return 0;
    }
}

