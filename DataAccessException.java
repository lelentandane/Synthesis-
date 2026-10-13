package com.lelentandane.synthesis.db;

/** Wraps SQLException so the rest of the code does not need checked exceptions. */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
