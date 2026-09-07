package com.nexis.report;

import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.nexis.alert.EventType;
import com.nexis.alert.SecurityEvent;
import com.nexis.alert.Severity;

/**
 * Generates structured, human-readable security reports from collections of {@link SecurityEvent} records.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Calculate total event counts, severity breakdowns, and event-type breakdowns</li>
 *   <li>Format critical events and investigation timeline details</li>
 *   <li>Gracefully handle empty reports without displaying misleading statistics</li>
 *   <li>Produce clean, non-bloated CLI reports following Nexis formatting conventions</li>
 * </ul>
 */
public class ReportGenerator {

    private static final String SEPARATOR = "────────────────────────────────────";
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Generates a complete report string for the given collection of security events.
     *
     * @param events collection of security events; must not be null
     * @return formatted report string
     * @throws NullPointerException if events is null
     */
    public String generate(Collection<SecurityEvent> events) {
        Objects.requireNonNull(events, "Events collection cannot be null");

        StringBuilder sb = new StringBuilder();

        sb.append("NEXIS SECURITY REPORT").append(System.lineSeparator());
        sb.append(SEPARATOR).append(System.lineSeparator()).append(System.lineSeparator());

        if (events.isEmpty()) {
            sb.append("No security events recorded.").append(System.lineSeparator());
            return sb.toString();
        }

        int totalCount = events.size();
        int criticalCount = (int) events.stream().filter(e -> e.getSeverity() == Severity.CRITICAL).count();
        int warningCount  = (int) events.stream().filter(e -> e.getSeverity() == Severity.WARNING).count();
        int errorCount    = (int) events.stream().filter(e -> e.getSeverity() == Severity.ERROR).count();
        int infoCount     = (int) events.stream().filter(e -> e.getSeverity() == Severity.INFO).count();

        // 1. Summary section
        sb.append(String.format("%-18s : %d%n", "Total Events", totalCount));
        sb.append(String.format("%-18s : %d%n", "Critical Events", criticalCount));
        sb.append(String.format("%-18s : %d%n", "Warnings", warningCount));
        sb.append(String.format("%-18s : %d%n", "Errors", errorCount));
        sb.append(String.format("%-18s : %d%n", "Informational", infoCount));
        sb.append(System.lineSeparator());

        // 2. Event breakdown section (non-zero types)
        sb.append("EVENT BREAKDOWN").append(System.lineSeparator());
        sb.append(SEPARATOR).append(System.lineSeparator());
        for (EventType type : EventType.values()) {
            long count = events.stream().filter(e -> e.getEventType() == type).count();
            if (count > 0) {
                sb.append(String.format("%-21s %d%n", type.name(), count));
            }
        }
        sb.append(System.lineSeparator());

        // 3. Detail section (Critical events first, or event details if filtered to non-critical)
        List<SecurityEvent> criticalEvents = events.stream()
            .filter(e -> e.getSeverity() == Severity.CRITICAL)
            .toList();

        if (!criticalEvents.isEmpty()) {
            sb.append("CRITICAL EVENTS").append(System.lineSeparator());
            sb.append(SEPARATOR).append(System.lineSeparator());
            for (int i = 0; i < criticalEvents.size(); i++) {
                SecurityEvent event = criticalEvents.get(i);
                formatEventDetail(sb, event);
                if (i < criticalEvents.size() - 1) {
                    sb.append(System.lineSeparator());
                }
            }
        } else if (totalCount > 0) {
            // When filtered to non-critical events, show event details so the user can investigate
            sb.append("EVENT DETAILS").append(System.lineSeparator());
            sb.append(SEPARATOR).append(System.lineSeparator());
            List<SecurityEvent> eventList = List.copyOf(events);
            for (int i = 0; i < eventList.size(); i++) {
                SecurityEvent event = eventList.get(i);
                formatEventDetail(sb, event);
                if (i < eventList.size() - 1) {
                    sb.append(System.lineSeparator());
                }
            }
        }

        return sb.toString();
    }

    /**
     * Generates and writes the report directly to the supplied {@link PrintWriter}.
     *
     * @param events collection of events to report
     * @param out    the destination writer; must not be null
     * @throws NullPointerException if events or out is null
     */
    public void generate(Collection<SecurityEvent> events, PrintWriter out) {
        Objects.requireNonNull(out, "PrintWriter cannot be null");
        out.print(generate(events));
        out.flush();
    }

    private void formatEventDetail(StringBuilder sb, SecurityEvent event) {
        String timestampStr = TIME_FORMATTER.format(event.getTimestamp());
        String pathStr = event.getFilePath() != null ? event.getFilePath().toString() : "N/A";

        sb.append("[").append(timestampStr).append("] ")
          .append(event.getEventType().name())
          .append(System.lineSeparator());
        sb.append("Path: ").append(pathStr).append(System.lineSeparator());
        sb.append("Details: ").append(event.getDetails()).append(System.lineSeparator());
    }
}

