# Nexis

**File Integrity & Host Intrusion Detection Monitor**

Nexis is an academic cybersecurity/DFIR (Digital Forensics and Incident Response) project that monitors file system integrity to detect unauthorized modifications, additions, and deletions.

---

## File Integrity Monitoring

Nexis provides a file integrity monitoring engine that:

- **Recursively scans** a target directory to discover all regular files
- **Calculates SHA-256 cryptographic hashes** for each discovered file using streaming I/O
- **Establishes a trusted baseline** mapping file paths to their expected hashes
- **Compares** the current filesystem state against the stored baseline
- **Detects four integrity states:**

| Status | Meaning |
|---|---|
| `UNCHANGED` | File exists and its SHA-256 hash matches the baseline |
| `MODIFIED` | File exists but its SHA-256 hash differs from the baseline |
| `NEW` | File exists on disk but has no baseline entry |
| `DELETED` | Baseline entry exists but the file is no longer on disk |

### Security

- Uses **SHA-256** via Java's standard `java.security.MessageDigest` API
- Does **not** follow symbolic links (prevents traversal attacks)
- Does **not** use weak hashes (MD5, SHA-1)
- Files are hashed using buffered streaming — file contents are never loaded entirely into memory
- Inaccessible files are reported as errors, never silently skipped

---

## Getting Started

### Prerequisites

- Java 26+
- Maven 3.9+

### Build

```bash
mvn clean package
```

### Run Tests

```bash
mvn clean test
```

---

## Usage

### 1. Create a Baseline

Scan a directory and save the trusted baseline:

```bash
nexis baseline <directory>
```

Example:

```
> nexis baseline C:\Users\User\Documents

  NEXIS BASELINE CREATED
  Target:    C:\Users\User\Documents
  Files:     47
  Saved to:  C:\...\data\baseline.json
```

The baseline is saved to `data/baseline.json` in the project's working directory.

### 2. Run an Integrity Scan

Compare the current state of a directory against the saved baseline:

```bash
nexis scan <directory>
```

Example:

```
> nexis scan C:\Users\User\Documents

  NEXIS FILE INTEGRITY SCAN
  Target: C:\Users\User\Documents

  Integrity Results
  ────────────────────────────────────
  UNCHANGED      44
  MODIFIED       1
  NEW            1
  DELETED        1
  ────────────────────────────────────
  Total          47

  Modified: ⚠ report.pdf
  New:      + suspicious.exe
  Deleted:  ✗ old_config.txt

  Status: DIFFERENCES DETECTED — Integrity violations found.
```

- Exit code `0` = CLEAN (no differences)
- Exit code `1` = DIFFERENCES DETECTED or error

---

## Real-Time Alerts & Security Logging

Nexis includes a modular alert and logging subsystem that processes events from both the real-time monitor (`watch`) and the integrity scanner (`scan`).

### Security Event Types

| Event Type | Severity | Trigger |
|---|---|---|
| `FILE_CREATED` | `INFO` | A new file appears in the monitored directory |
| `FILE_MODIFIED` | `WARNING` | An existing monitored file is changed |
| `FILE_DELETED` | `WARNING` | A monitored file is removed |
| `INTEGRITY_VIOLATION` | `CRITICAL` | A file's SHA-256 hash differs from its baseline |
| `MONITORING_ERROR` | `ERROR` | An unexpected error in the real-time watch loop |
| `SYSTEM_ERROR` | `ERROR` | An unexpected error during a scan operation |

### CLI Alert Format

```
   [2026-09-06 14:32:18] [INFO]     FILE_CREATED        Path: C:\watch\report.txt  Details: File creation detected.
   [2026-09-06 14:33:01] [WARNING]  FILE_MODIFIED       Path: C:\watch\config.xml  Details: File modification detected.
!! [2026-09-06 14:35:42] [CRITICAL] INTEGRITY_VIOLATION Path: C:\watch\config.xml  Details: SHA-256 hash differs from baseline.
```

`CRITICAL` and `ERROR` events are prefixed with `!!` for visual prominence.

### Persistent Log File

All events are appended to `logs/nexis.log` in the working directory:

```
2026-09-06 14:32:18 | INFO     | FILE_CREATED         | C:\watch\report.txt | File creation detected.
2026-09-06 14:35:42 | CRITICAL | INTEGRITY_VIOLATION  | C:\watch\config.xml | SHA-256 hash differs from baseline.
```

- Log directory and file are created automatically on first write.
- Events are appended — existing log entries are never overwritten.
- Logging failures are reported to `stderr` and never crash the monitoring engine.
- `logs/nexis.log` is excluded from version control (`.gitignore`).

---

## Security Event Reporting & Investigation

Nexis provides structured reporting and filtering for forensic investigation across detected security events:

```bash
# View complete security report
nexis report

# Filter by severity
nexis report --severity CRITICAL

# Filter by event type
nexis report --type INTEGRITY_VIOLATION

# Filter by affected file path
nexis report --path C:\watch\config.xml

# Clear stored events
nexis report --clear
```

### Sample Report Output

```
NEXIS SECURITY REPORT
────────────────────────────────────

Total Events       : 3
Critical Events    : 1
Warnings           : 1
Errors             : 0
Informational      : 1

EVENT BREAKDOWN
────────────────────────────────────
FILE_CREATED          1
FILE_DELETED          1
INTEGRITY_VIOLATION   1

CRITICAL EVENTS
────────────────────────────────────
[21:23:53] INTEGRITY_VIOLATION
Path: C:\watch\config.xml
Details: SHA-256 hash differs from baseline — possible tampering detected.
```

---

## Architecture

```
com.nexis
├── Main.java                          # Entry point
├── cli/
│   ├── NexisCLI.java                  # Root picocli command
│   ├── BaselineCommand.java           # 'baseline' subcommand
│   ├── ScanCommand.java               # 'scan' subcommand + event dispatch
│   ├── WatchCommand.java              # 'watch' subcommand + event dispatch
│   ├── ReportCommand.java             # 'report' subcommand (investigation)
│   └── ResultFormatter.java           # CLI scan formatter
├── report/
│   ├── EventRepository.java           # In-memory structured event store & query
│   ├── EventStorage.java              # JSON persistence (data/events.json)
│   └── ReportGenerator.java           # Structured security report renderer
├── alert/
│   ├── EventType.java                 # FILE_CREATED/MODIFIED/DELETED/INTEGRITY_VIOLATION/...
│   ├── Severity.java                  # INFO/WARNING/CRITICAL/ERROR
│   ├── SecurityEvent.java             # Immutable event value object
│   ├── AlertManager.java              # CLI alert formatter/printer
│   └── SecurityLogger.java            # Append-only log file writer
├── baseline/
│   ├── BaselineEntry.java             # Path → SHA-256 record
│   ├── BaselineManager.java           # In-memory baseline management
│   ├── BaselineStorage.java           # JSON persistence
│   └── BaselineStorageException.java  # Storage error handling
├── integrity/
│   ├── HashCalculator.java            # SHA-256 streaming hasher
│   ├── ComparisonEngine.java          # Baseline vs. current comparison
│   ├── ComparisonEntry.java           # Per-file comparison result
│   ├── ComparisonResult.java          # Structured result container
│   └── ComparisonStatus.java          # NEW/MODIFIED/DELETED/UNCHANGED enum
├── monitor/
│   ├── DirectoryMonitor.java          # WatchService real-time monitor
│   ├── MonitorEvent.java              # Raw FS event (path + type)
│   └── MonitorEventType.java          # CREATED/MODIFIED/DELETED
└── scanner/
    └── FileScanner.java               # Recursive file discovery
```

**Event flow:**
```
WatchService / ScanEngine -> SecurityEvent
                                 ├── AlertManager   → CLI alerts
                                 ├── SecurityLogger → logs/nexis.log
                                 └── EventRepository → ReportGenerator → CLI report
```

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
