package com.orodent.ecos.database.csv;

import com.orodent.ecos.core.csv.CsvImportResult;
import com.orodent.ecos.core.csv.CsvImporter;
import com.orodent.ecos.core.csv.CsvReadOptions;
import com.orodent.ecos.core.csv.CsvReader;
import com.orodent.ecos.database.model.Cliente;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClienteCsvImportTest {
    private static final String HEADER = "Codice;ISO_CODE;Categoria;PriceList;Agente;"
            + "MA_CustSupp_CustSuppKind;Ragione sociale";

    @Test
    void mapsAllCustomerFields() {
        CsvImportResult<Cliente> result = importLines(List.of(
                HEADER,
                "20ZR988;BE;CF;24_ZR55;DIRETTO;UE;3D Openminds BV"
        ));

        assertTrue(result.valid());
        Cliente cliente = result.validRows().getFirst().value();
        assertEquals("20ZR988", cliente.codiceCliente());
        assertEquals("BE", cliente.codiceIso());
        assertEquals("CF", cliente.categoria());
        assertEquals("24_ZR55", cliente.listino());
        assertEquals("DIRETTO", cliente.agente());
        assertEquals("UE", cliente.tipoCliente());
        assertEquals("3D Openminds BV", cliente.ragioneSociale());
    }

    @Test
    void joinsUnquotedContinuationLinesIntoCompanyName() {
        CsvImportResult<Cliente> result = importLines(List.of(
                HEADER,
                "2002A017;IT;;LAB.;BINOTTO;Nazionale;AB 2C DENTAL",
                "di C. Casartelli",
                "20ZR942;EG;;2020;;Extra UE;Adamex import and Export",
                "Mr. Safwaan Ali Mohamed Alshashaey"
        ));

        assertTrue(result.valid());
        assertEquals("AB 2C DENTAL di C. Casartelli",
                result.validRows().get(0).value().ragioneSociale());
        assertEquals("Adamex import and Export Mr. Safwaan Ali Mohamed Alshashaey",
                result.validRows().get(1).value().ragioneSociale());
    }

    @Test
    void preservesLiteralQuotesInMalformedExport() {
        CsvImportResult<Cliente> result = importLines(List.of(
                HEADER,
                "20DR1030;IT;;;BRUNITTO;Nazionale;\"IL LABORATORIO\" di Secchi Claudio & C."
        ));

        assertTrue(result.valid());
        assertEquals("\"IL LABORATORIO\" di Secchi Claudio & C.",
                result.validRows().getFirst().value().ragioneSociale());
    }

    private CsvImportResult<Cliente> importLines(List<String> lines) {
        String normalized = new ClienteCsvSourceNormalizer().normalize(lines);
        return new CsvImporter<>(new CsvReader(), new ClienteCsvMapper(), new ClienteValidator())
                .importReader(new StringReader(normalized), CsvReadOptions.semicolonSeparated());
    }
}
