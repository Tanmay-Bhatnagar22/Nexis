package com.nexis.monitor;

/**
 * Represents the type of a real-time filesystem event detected by the WatchService.
 */
public enum MonitorEventType {

    /** A new file was created in the monitored directory. */
    CREATED,

    /** An existing file was modified in the monitored directory. */
    MODIFIED,

    /** A file was deleted from the monitored directory. */
    DELETED
}
