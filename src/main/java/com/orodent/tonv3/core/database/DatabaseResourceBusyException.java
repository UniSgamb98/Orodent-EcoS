package com.orodent.tonv3.core.database;

/** Only resource conflicts trigger the retry as a client. */
public final class DatabaseResourceBusyException extends Exception {
    public DatabaseResourceBusyException(String message, Throwable cause) { super(message, cause); }
}
