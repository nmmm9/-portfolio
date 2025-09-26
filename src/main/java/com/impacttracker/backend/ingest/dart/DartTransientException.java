// src/main/java/com/impacttracker/backend/ingest/dart/DartTransientException.java
package com.impacttracker.backend.ingest.dart;

public class DartTransientException extends RuntimeException {
    public DartTransientException(String message) { super(message); }
    public DartTransientException(String message, Throwable cause) { super(message, cause); }
}
