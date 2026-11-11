package com.orodent.ecos.crm.core.async;

import com.orodent.ecos.crm.core.database.Database;

import java.sql.SQLException;

public final class CrmErrors {
    private CrmErrors() {}

    public static boolean uncertain(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause())
            if (e instanceof SQLException sql
                    && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("08")) return true;
        return false;
    }

    public static String message(Throwable error) {
        if (uncertain(error))
            return "Connessione interrotta: l'esito del salvataggio non è confermato. Ricarica la"
                    + " pagina e verifica i dati prima di riprovare.";
        for (Throwable e = error; e != null; e = e.getCause())
            if (e instanceof Database.Conflict) return e.getMessage();
        return error.getMessage() == null
                ? "Operazione non riuscita. Ricarica i dati prima di riprovare."
                : error.getMessage();
    }
}
