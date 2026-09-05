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

## Architecture

```
com.nexis
├── Main.java                          # Entry point
├── cli/
│   ├── NexisCLI.java                  # Root picocli command
│   ├── BaselineCommand.java           # 'baseline' subcommand
│   ├── ScanCommand.java               # 'scan' subcommand
│   └── ResultFormatter.java           # CLI output formatter
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
└── scanner/
    └── FileScanner.java               # Recursive file discovery
```

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
