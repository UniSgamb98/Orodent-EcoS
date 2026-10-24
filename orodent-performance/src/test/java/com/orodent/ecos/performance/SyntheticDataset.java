package com.orodent.ecos.performance;

import com.orodent.ecos.core.ConnectionProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;

/** Deterministic generated office data; no source database or external input is accepted. */
final class SyntheticDataset {
    static void populate(ConnectionProvider connections, PerformanceSettings settings, Path run) throws Exception {
        Random random = new Random(settings.seed());
        LocalDate first = LocalDate.of(settings.lastYear() - settings.years() + 1, 1, 1);
        int days = Math.toIntExact(ChronoUnit.DAYS.between(first, LocalDate.of(settings.lastYear() + 1, 1, 1)));
        BigDecimal[] expected = {BigDecimal.ZERO};
        connections.withTransaction(connection -> {
            try (var customer = connection.prepareStatement("""
                    INSERT INTO ANAGRAFICHE.CLIENTI(codice_cliente, codice_iso, categoria, listino, agente,
                        tipo_cliente, ragione_sociale) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (int i = 1; i <= settings.customers(); i++) {
                    customer.setString(1, customer(i));
                    customer.setString(2, i % 4 == 0 ? "DE" : "IT");
                    customer.setString(3, i % 3 == 0 ? "Rivenditore" : "Laboratorio");
                    customer.setString(4, "Listino prova");
                    customer.setString(5, "Agente " + (i % 10));
                    customer.setString(6, "Nazionale");
                    customer.setString(7, "Cliente sintetico " + i);
                    customer.addBatch();
                    if (i % 500 == 0) customer.executeBatch();
                }
                customer.executeBatch();
            }
            try (var sale = connection.prepareStatement("""
                    INSERT INTO VENDITE.VENDITE_DETTAGLIO(sorgente, documento_id, numero_documento,
                        numero_riga, data_vendita, codice_cliente, codice_prodotto, descrizione_prodotto,
                        categoria_prodotto, quantita, prezzo_unitario, sconto_percentuale, sconto_importo,
                        importo_netto, tipo_operazione, agente)
                    VALUES ('PERFORMANCE', ?, ?, 1, ?, ?, ?, ?, 'Dischi', ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (int i = 0; i < settings.sales(); i++) {
                    LocalDate date = i < settings.years() ? LocalDate.of(settings.lastYear() - i, 6, 15)
                            : first.plusDays(random.nextInt(days));
                    int product = random.nextInt(settings.products());
                    boolean returned = i % 20 == 19;
                    int quantity = 1 + random.nextInt(5);
                    BigDecimal gross = BigDecimal.valueOf(100L * quantity);
                    int discount = !returned && i % 3 == 0 ? 10 : 0;
                    BigDecimal discountAmount = gross.multiply(BigDecimal.valueOf(discount)).movePointLeft(2);
                    BigDecimal net = gross.subtract(discountAmount);
                    sale.setString(1, "PERF-" + i);
                    sale.setString(2, "PERF-" + i);
                    sale.setDate(3, Date.valueOf(date));
                    sale.setString(4, customer(i < settings.years() ? 1 : 1 + random.nextInt(settings.customers())));
                    sale.setString(5, product(product));
                    sale.setString(6, description(product));
                    sale.setInt(7, returned ? -quantity : quantity);
                    sale.setBigDecimal(8, BigDecimal.valueOf(100));
                    sale.setInt(9, discount);
                    sale.setBigDecimal(10, discountAmount);
                    sale.setBigDecimal(11, returned ? net.negate() : net);
                    sale.setString(12, returned ? "RESO" : "VENDITA");
                    sale.setString(13, "Agente " + (i % 10));
                    sale.addBatch();
                    if (date.getYear() == settings.lastYear()) expected[0] = expected[0].add(returned ? net.negate() : net);
                    if ((i + 1) % 500 == 0) sale.executeBatch();
                }
                sale.executeBatch();
            }
            return null;
        });
        Properties metadata = new Properties();
        metadata.setProperty("expected.lastYear.netRevenue", expected[0].toPlainString());
        metadata.setProperty("customers", Integer.toString(settings.customers()));
        metadata.setProperty("sales", Integer.toString(settings.sales()));
        metadata.setProperty("seed", Integer.toString(settings.seed()));
        try (var output = Files.newOutputStream(run.resolve("dataset.properties"))) { metadata.store(output, "Generated data only"); }
        if (settings.ui()) writeImportCsv(settings, run.resolve("generated-import.csv"));
    }

    private static void writeImportCsv(PerformanceSettings settings, Path path) throws IOException {
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("Data reg.;Nr. doc.;MA_InventoryEntries_StoragePhase1;MA_InventoryReasons_Reason;MA_InventoryEntries_CustSupp;Articolo;Descrizione;Unità misura;Quantità;Divisa;Qta UM base;Valore unitario;Sconto;Imp. sconto;Importo riga;Importo Riga in Divisa Base\n");
            for (int i = 0; i < settings.importRows(); i++) {
                writer.write("02/01/" + (settings.lastYear() + 1) + ";IMPORT-" + i
                        + ";MAGMOD;VEND;C00001;" + product(0) + ";" + description(0) + ";N.;1;EUR;1;100;0;0;100;100\n");
            }
        }
    }

    static String customer(int index) { return String.format(Locale.ROOT, "C%05d", index); }
    private static String product(int index) { return String.format(Locale.ROOT, "ZR98%02dMSA%d", 6 + index / 4, 1 + index % 4); }
    private static String description(int index) { return "ORODENT EOS 98X" + (6 + index / 4) + " COLOR: A" + (1 + index % 4); }
}
