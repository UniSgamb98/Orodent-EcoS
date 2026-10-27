package com.orodent.ecos.database;

/** The failed command is never replayed, including when the commit result is unknown. */
public final class DatabaseDisconnectedException extends IllegalStateException {
    public DatabaseDisconnectedException(Throwable cause) {
        super("Connessione al database interrotta. Attendere il ripristino del collegamento. "
                + "L'operazione non è stata ripetuta: prima di riprovare un salvataggio, verificarne l'esito.", cause);
    }
}
