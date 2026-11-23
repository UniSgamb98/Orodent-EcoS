package com.orodent.ecos.database.audit;

import java.sql.Clob;
import java.sql.SQLException;
import java.util.*;

import javax.sql.rowset.serial.SerialClob;

/** Lossless, length-prefixed fields: null, empty strings and multiline CLOBs remain distinct. */
public final class AuditValues {
    private AuditValues() {}

    public static Clob field(String name, Clob value) throws SQLException {
        String text = text(value);
        return new SerialClob(
                (name.length() + ":" + name + (text == null ? "-1:" : text.length() + ":" + text))
                        .toCharArray());
    }

    public static String text(Clob value) throws SQLException {
        if (value == null) return null;
        int length = Math.toIntExact(value.length());
        return length == 0 ? "" : value.getSubString(1, length);
    }

    public static Map<String, String> decode(String value) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (value == null) return fields;
        int pos = 0;
        while (pos < value.length()) {
            int colon = value.indexOf(':', pos);
            int length = Integer.parseInt(value.substring(pos, colon));
            String key = value.substring(colon + 1, colon + 1 + length);
            pos = colon + 1 + length;
            colon = value.indexOf(':', pos);
            length = Integer.parseInt(value.substring(pos, colon));
            pos = colon + 1;
            fields.put(key, length < 0 ? null : value.substring(pos, pos + length));
            if (length >= 0) pos += length;
        }
        return fields;
    }

    static boolean equal(String before, String after) {
        if (before == null || after == null) return before == after;
        var a = decode(before);
        var b = decode(after);
        for (String technical : List.of("UPDATED_AT", "CREATED_AT", "VERSIONE_MODIFICA")) {
            a.remove(technical);
            b.remove(technical);
        }
        return a.equals(b);
    }
}
