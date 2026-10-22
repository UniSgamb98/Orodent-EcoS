package com.orodent.tonv3.core.database.csv;

import com.orodent.tonv3.core.csv.CsvImportResult;
import com.orodent.tonv3.core.csv.CsvImporter;
import com.orodent.tonv3.core.csv.CsvMappingException;
import com.orodent.tonv3.core.csv.CsvReadOptions;
import com.orodent.tonv3.core.csv.CsvReader;
import com.orodent.tonv3.core.database.model.TipoOperazione;
import com.orodent.tonv3.core.database.model.VenditaDettaglio;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VenditaDettaglioCsvMapperTest {

    private static final String HEADER = "Data reg.;Nr. doc.;MA_InventoryEntries_StoragePhase1;"
            + "MA_InventoryReasons_Reason;MA_InventoryEntries_CustSupp;Articolo;Descrizione;"
            + "Unità misura;Quantità;Divisa;Qta UM base;Valore unitario;Sconto;Imp. sconto;"
            + "Importo riga;Importo Riga in Divisa Base";

    private final VenditaDettaglioCsvMapper mapper = new VenditaDettaglioCsvMapper();
    private final VenditaDettaglioValidator validator = new VenditaDettaglioValidator();

    @Test
    void mapsSalesRowAndCalculatesCompoundDiscount() {
        String row = "06/10/2026;001005;MAGMOD;VEND;20ZR1036;ZR9816MSA2;"
                + "ORODENT EOS 98X16 COLOR: A2;N.;1;EUR;1;211;55+10;125,54;85,46;85,46";

        VenditaDettaglio vendita = readAndMap(row);

        assertEquals(LocalDate.of(2026, 10, 6), vendita.dataVendita());
        assertEquals("2026:001005", vendita.documentoId());
        assertEquals("001005", vendita.numeroDocumento());
        assertEquals(1, vendita.numeroRiga());
        assertEquals("MAGMOD", vendita.sorgente());
        assertEquals("20ZR1036", vendita.codiceCliente());
        assertEquals("ZR9816MSA2", vendita.codiceProdotto());
        assertEquals(new BigDecimal("1"), vendita.quantita());
        assertEquals(new BigDecimal("211"), vendita.prezzoUnitario());
        assertEquals(new BigDecimal("59.5"), vendita.scontoPercentuale());
        assertEquals(new BigDecimal("125.54"), vendita.scontoImporto());
        assertEquals(new BigDecimal("85.46"), vendita.importoNetto());
        assertEquals(TipoOperazione.VENDITA, vendita.tipoOperazione());
        assertTrue(validator.validate(vendita).isEmpty());
    }

    @Test
    void mapsVenOReasonAsSale() {
        String row = "01/10/2026;000976;MAGMOD;VEN-O;20ZR774;ZR9816MSTA2;"
                + "ORODENT THOR 98x16 COLOR: A2;N.;2;EUR;2;206;60+10;263,68;148,32;148,32";

        VenditaDettaglio vendita = readAndMap(row);

        assertEquals(TipoOperazione.VENDITA, vendita.tipoOperazione());
        assertEquals(new BigDecimal("64"), vendita.scontoPercentuale());
    }

    @Test
    void interpretsLeadingPlusAsAPositiveDiscount() {
        String row = "01/10/2026;000976;MAGMOD;VEND;20ZR774;ZR9816MSTA2;"
                + "Prodotto;N.;1;EUR;1;100;+5;5;95;95";

        VenditaDettaglio vendita = readAndMap(row);

        assertEquals(new BigDecimal("5"), vendita.scontoPercentuale());
        assertTrue(validator.validate(vendita).isEmpty());
    }

    @Test
    void stillRejectsAPlusWithoutAnyPercentage() {
        String row = "01/10/2026;000976;MAGMOD;VEND;20ZR774;ZR9816MSTA2;"
                + "Prodotto;N.;1;EUR;1;100;+;0;100;100";

        CsvMappingException exception = assertThrows(CsvMappingException.class, () -> readAndMap(row));

        assertEquals(VenditaDettaglioCsvMapper.SCONTO, exception.column());
        assertEquals("+", exception.rawValue());
    }

    @Test
    void mapsCompanySpecificReasonAsSale() {
        String row = "01/10/2026;000976;MAGMOD;VMAG10;20ZR774;ZR9816MSTA2;"
                + "ORODENT THOR 98x16 COLOR: A2;N.;2;EUR;2;206;60;247,2;164,8;164,8";

        VenditaDettaglio vendita = readAndMap(row);

        assertEquals(TipoOperazione.VENDITA, vendita.tipoOperazione());
        assertTrue(validator.validate(vendita).isEmpty());
    }

    @Test
    void mapsRepresentative2023Row() {
        String row = "10/01/2023;000012;MAGMOD;VEND;20ZR713;ZR9816H;"
                + "ORODENT WHITE MATT 1400 MPA 98X16 COLOR: WHITE;N.;1;EUR;1;155;50+6;82,15;72,85;72,85";

        VenditaDettaglio vendita = readAndMap(row);

        assertEquals(LocalDate.of(2023, 1, 10), vendita.dataVendita());
        assertEquals("2023:000012", vendita.documentoId());
        assertEquals(new BigDecimal("53"), vendita.scontoPercentuale());
        assertEquals(new BigDecimal("82.15"), vendita.scontoImporto());
        assertEquals(new BigDecimal("72.85"), vendita.importoNetto());
        assertTrue(validator.validate(vendita).isEmpty());
    }

    @Test
    void createsDifferentDocumentIdsWhenDocumentNumbersRepeatInDifferentYears() {
        String row2023 = "09/01/2023;000186;MAGMOD;VEND;CLIENTE;PRODOTTO;Prodotto;N.;1;EUR;1;10;0;0;10;10";
        String row2026 = "09/01/2026;000186;MAGMOD;VEND;CLIENTE;PRODOTTO;Prodotto;N.;1;EUR;1;10;0;0;10;10";

        VenditaDettaglio sale2023 = readAndMap(row2023);
        VenditaDettaglio sale2026 = readAndMap(row2026);

        assertEquals("2023:000186", sale2023.documentoId());
        assertEquals("2026:000186", sale2026.documentoId());
    }

    @Test
    void roundsImportedDecimalsToDatabaseScale() {
        String row = "01/10/2026;000976;MAGMOD;VEND;20ZR774;ZR9816MSTA2;"
                + "Prodotto;N.;1,1239;EUR;1;206,12345;10;7,9000000000001;85,465;85,465";

        VenditaDettaglio vendita = readAndMap(row);

        assertEquals(new BigDecimal("1.124"), vendita.quantita());
        assertEquals(new BigDecimal("206.1235"), vendita.prezzoUnitario());
        assertEquals(new BigDecimal("7.9"), vendita.scontoImporto());
        assertEquals(new BigDecimal("85.47"), vendita.importoNetto());
        assertTrue(validator.validate(vendita).isEmpty());
    }

    @Test
    void keepsExplicitReturnAndCreditNoteReasons() {
        String returnRow = "01/10/2026;000976;MAGMOD;RESO;20ZR774;ZR9816MSTA2;"
                + "Prodotto;N.;-1;EUR;-1;206;0;0;-206;-206";
        String creditNoteRow = "01/10/2026;000977;MAGMOD;NC-CLIENTE;20ZR774;ZR9816MSTA2;"
                + "Prodotto;N.;-1;EUR;-1;206;0;0;-206;-206";

        assertEquals(TipoOperazione.RESO, readAndMap(returnRow).tipoOperazione());
        assertEquals(TipoOperazione.NOTA_CREDITO, readAndMap(creditNoteRow).tipoOperazione());
    }

    @Test
    void reportsInvalidDateAsMappingError() {
        String row = "31/02/2026;001005;MAGMOD;VEND;20ZR1036;ZR9816MSA2;"
                + "Prodotto;N.;1;EUR;1;211;55+10;125,54;85,46;85,46";

        CsvMappingException exception = assertThrows(CsvMappingException.class, () -> readAndMap(row));

        assertEquals(VenditaDettaglioCsvMapper.DATA_REGISTRAZIONE, exception.column());
        assertEquals("31/02/2026", exception.rawValue());
    }

    @Test
    void importerReportsZeroQuantityAsRowValidationError() {
        String row = "06/10/2026;001005;MAGMOD;VEND;20ZR1036;ZR9816MSA2;"
                + "Prodotto;N.;0;EUR;0;211;55+10;0;0;0";
        CsvImporter<VenditaDettaglio> importer = new CsvImporter<>(new CsvReader(), mapper, validator);

        CsvImportResult<VenditaDettaglio> result = importer.importReader(
                new StringReader(HEADER + "\n" + row),
                CsvReadOptions.semicolonSeparated()
        );

        assertEquals(1, result.totalRows());
        assertTrue(result.validRows().isEmpty());
        assertEquals(1, result.rowErrors().size());
        assertEquals(VenditaDettaglioCsvMapper.QUANTITA, result.rowErrors().getFirst().field());
        assertEquals("0", result.rowErrors().getFirst().rawValue());
        assertFalse(result.valid());
    }

    @Test
    void acceptsOptionalPercentSuffixWithoutChangingDiscountCalculations() {
        String[][] examples = {
                {"40%", "40"}, {" 40 % ", "40"}, {"40,5%", "40.5"},
                {"40.5%", "40.5"}, {"+5%", "5"}, {"40%+10%", "46"},
                {"40 + 10%", "46"}, {"40% + 10", "46"}, {"0%", "0"}, {"100%", "100"}
        };
        for (String[] example : examples) {
            assertEquals(0, new BigDecimal(example[1]).compareTo(readAndMap(discountRow(example[0])).scontoPercentuale()), example[0]);
        }
        var plain = readAndMap(discountRow("40+10"));
        var suffixed = readAndMap(discountRow("40%+10%"));
        assertEquals(plain, suffixed);
    }

    @Test
    void rejectsMalformedPercentExpressionsAndOutOfRangeComponents() {
        for (String discount : new String[]{"%", "40%%", "%40", "4%0", "40%+", "40%++10%", "101%", "-1%", "40%+101%"}) {
            CsvMappingException error = assertThrows(CsvMappingException.class,
                    () -> readAndMap(discountRow(discount)), discount);
            assertEquals(VenditaDettaglioCsvMapper.SCONTO, error.column());
            assertEquals(discount, error.rawValue());
        }
    }

    private String discountRow(String discount) {
        return "01/10/2026;000976;MAGMOD;VEND;20ZR774;ZR9816MSTA2;"
                + "Prodotto;N.;1;EUR;1;100;" + discount + ";46;54;54";
    }

    private VenditaDettaglio readAndMap(String row) {
        return mapper.map(new CsvReader()
                .read(new StringReader(HEADER + "\n" + row), CsvReadOptions.semicolonSeparated())
                .rows()
                .getFirst());
    }
}
