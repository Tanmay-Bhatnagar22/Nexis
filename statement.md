# Project Statement

## Problem Statement

Organizations and individuals face persistent threats to the integrity of their files and systems. Unauthorized modifications to critical files — caused by malware infections, insider threats, misconfigurations, or targeted attacks — often go undetected until significant damage has occurred. Without a mechanism to continuously verify that files remain in their expected state, security breaches can persist unnoticed for extended periods.

**Nexis** addresses this problem by providing a file integrity monitoring and host intrusion detection tool that establishes a trusted cryptographic baseline of a directory's contents and detects any deviations from that baseline. By comparing SHA-256 hashes of current files against their known-good state, Nexis identifies modified, newly created, and deleted files — enabling early detection of unauthorized changes and supporting forensic investigation.

---

## Scope

### In Scope

- **Baseline creation**: Recursively scanning a target directory, computing SHA-256 cryptographic hashes for all regular files, and persisting the baseline to a versioned JSON file.
- **Integrity scanning**: Comparing the current filesystem state against the stored baseline and classifying each file as unchanged, modified, new, or deleted.
- **Real-time monitoring**: Detecting file creation, modification, and deletion events in real time using Java's WatchService API, with live baseline comparison for integrity violation detection.
- **Security event management**: Structured security events with severity levels (INFO, WARNING, CRITICAL, ERROR), CLI alerting, persistent logging to `logs/nexis.log`, and JSON-persisted event storage for reporting.
- **Security reporting**: Generating structured investigation reports with filtering by severity, event type, and file path.
- **CLI interface**: A professional command-line interface built with picocli, featuring startup banners, help screens, and ASCII-safe output.
- **Automated testing**: A comprehensive test suite of 186 unit and integration tests validating all components.

### Out of Scope

- Graphical user interface (GUI).
- Network-based alerting (email, webhooks, SIEM integration).
- File content recovery or rollback.
- File permission or metadata monitoring.
- Baseline encryption or cryptographic signing.
- Scheduled or automated scanning (requires external scheduling tools).
- Cross-platform native packaging or installers.

---

## Target Users

- **Cybersecurity students and educators** studying file integrity monitoring, intrusion detection systems, and digital forensics concepts.
- **System administrators** seeking a lightweight tool to verify file integrity on development or staging environments.
- **Developers** who need to detect unintended file modifications during build processes or deployment pipelines.
- **Digital forensics practitioners** investigating unauthorized file changes on host systems.
- **Security researchers** exploring hash-based integrity verification techniques.

---

## High-Level Features

| Feature | Description |
|---|---|
| **Baseline Creation** | Recursive directory scanning with SHA-256 hashing and JSON persistence |
| **Integrity Scanning** | Baseline comparison detecting UNCHANGED, MODIFIED, NEW, and DELETED files |
| **Real-Time Monitoring** | WatchService-based live directory monitoring with integrity violation detection |
| **Security Alerting** | Structured events with severity classification and CLI display |
| **Persistent Logging** | Append-only pipe-delimited log file (`logs/nexis.log`) |
| **Event Reporting** | Filterable security reports with severity, type, and path-based queries |
| **CLI Interface** | picocli-based commands with ASCII-safe formatting and ANSI color support |
| **Comprehensive Testing** | 186 tests across 20 test classes covering all components |

