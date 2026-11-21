package com.orodent.ecos.database;

import com.orodent.ecos.core.PowderAppearance;

import java.sql.*;
import java.util.*;

/** Read-only appearance lookup; callers keep their own transaction and worker. */
public record PowderDisplayCatalogue(
        Map<Long, PowderAppearance> identities,
        Map<Long, PowderAppearance> revisions,
        Map<Version, PowderAppearance> versions) {
    public record Version(long powder, int number) {}

    public PowderDisplayCatalogue {
        identities = Map.copyOf(identities);
        revisions = Map.copyOf(revisions);
        versions = Map.copyOf(versions);
    }

    public static PowderDisplayCatalogue empty() {
        return new PowderDisplayCatalogue(Map.of(), Map.of(), Map.of());
    }

    public PowderAppearance identity(long id, String code) {
        return identities.getOrDefault(id, PowderAppearance.unknown(id, code));
    }

    public PowderAppearance version(long id, int number, String code) {
        return versions.getOrDefault(new Version(id, number), identity(id, code));
    }

    public static PowderDisplayCatalogue read(Connection connection) throws SQLException {
        var identities = new HashMap<Long, PowderAppearance>();
        var revisions = new HashMap<Long, PowderAppearance>();
        var versions = new HashMap<Version, PowderAppearance>();
        try (var q = connection.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT"
                                        + " p.polvere_id,p.codice,"
                                        + SupplierCatalogue.powderName("p")
                                        + ",p.colore,r.polvere_revisione_id,r.numero_versione,r.classe_ittria_y"
                                        + " FROM TON.POLVERI p LEFT JOIN TON.POLVERI_REVISIONI r ON"
                                        + " r.polvere_id=p.polvere_id ORDER BY p.polvere_id,CASE"
                                        + " WHEN r.stato='APPROVATA' THEN 0 ELSE 1"
                                        + " END,r.numero_versione DESC")) {
            while (r.next()) {
                var p =
                        new PowderAppearance(
                                r.getLong(1),
                                r.getString(2),
                                r.getString(3),
                                r.getString(4),
                                (Integer) r.getObject(7));
                identities.putIfAbsent(p.id(), p);
                if (r.getObject(5) != null) {
                    revisions.put(r.getLong(5), p);
                    versions.put(new Version(p.id(), r.getInt(6)), p);
                }
            }
        }
        return new PowderDisplayCatalogue(identities, revisions, versions);
    }

    /**
     * Trusted SQL aliases only. Display Y matches latest approved revision, or latest available.
     */
    public static String latestJoin(String powder, String revision) {
        return " LEFT JOIN TON.POLVERI_REVISIONI "
                + revision
                + " ON "
                + revision
                + ".polvere_id="
                + powder
                + ".polvere_id AND "
                + revision
                + ".numero_versione=COALESCE((SELECT MAX(py.numero_versione) FROM"
                + " TON.POLVERI_REVISIONI py WHERE py.polvere_id="
                + powder
                + ".polvere_id AND py.stato='APPROVATA'),(SELECT MAX(py.numero_versione) FROM"
                + " TON.POLVERI_REVISIONI py WHERE py.polvere_id="
                + powder
                + ".polvere_id)) ";
    }

    public static String sqlOrder(String powder, String yttria) {
        String supplier = SupplierCatalogue.powderName(powder);
        return " CASE WHEN TRIM("
                + supplier
                + ")='' THEN 1 ELSE 0 END,UPPER(TRIM("
                + supplier
                + ")),CASE "
                + powder
                + ".colore WHEN 'BIANCA' THEN 0 WHEN 'GIALLA' THEN 1 WHEN 'ROSA' THEN 2 WHEN"
                + " 'GRIGIA' THEN 3 ELSE 4 END,"
                + yttria
                + " ASC NULLS LAST,UPPER("
                + powder
                + ".codice),"
                + powder
                + ".polvere_id";
    }
}
