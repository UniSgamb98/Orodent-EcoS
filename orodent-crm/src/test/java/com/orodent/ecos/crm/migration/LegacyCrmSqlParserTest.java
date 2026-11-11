package com.orodent.ecos.crm.migration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;

class LegacyCrmSqlParserTest {
    @Test
    void preservesOriginalNoteContentWhileIgnoringRealComments() throws Exception {
        var statements =
                LegacyCrmImport.splitSql(
                        "-- comment\n"
                            + "INSERT INTO CRM.NOTE_CLIENTE(TESTO) VALUES ('l''ufficio;\n"
                            + "--nota\n"
                            + "/* nota */'); /* separator */ INSERT INTO CRM.CLIENTI(ID) VALUES"
                            + " ('a');");
        assertEquals(2, statements.size());
        assertTrue(statements.getFirst().contains("l''ufficio;\n--nota\n/* nota */"));
        assertFalse(statements.getFirst().contains("-- comment"));
    }

    @Test
    void refusesIncompleteSqlInsteadOfRunningAPartialStatement() {
        assertThrows(
                IOException.class,
                () -> LegacyCrmImport.splitSql("INSERT INTO CRM.CLIENTI(ID) VALUES ('open);"));
        assertThrows(
                IOException.class,
                () -> LegacyCrmImport.splitSql("INSERT INTO CRM.CLIENTI(ID) VALUES ('a')"));
        assertThrows(IOException.class, () -> LegacyCrmImport.splitSql("/* open"));
    }

    @Test
    void defaultCliIsPreviewAndUnknownOptionsAreRejected() throws Exception {
        assertFalse(
                LegacyCrmImport.options(new String[] {"--dir", "output"}).containsKey("--apply"));
        assertThrows(
                IOException.class,
                () -> LegacyCrmImport.options(new String[] {"--apply", "--apply"}));
    }
}
