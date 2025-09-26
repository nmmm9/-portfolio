// src/main/java/com/impacttracker/backend/ingest/dart/DartQuotaExceededException.java
package com.impacttracker.backend.ingest.dart;

public class DartQuotaExceededException extends RuntimeException {
    public DartQuotaExceededException(String message) { super(message); }
}
