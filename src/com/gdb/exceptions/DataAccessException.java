package com.gdb.exceptions;

/**
 * Unchecked exception wrapping SQL / persistence failures so that callers of the
 * repository layer are not forced to handle {@link java.sql.SQLException}.
 */
public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
