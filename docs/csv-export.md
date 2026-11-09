# Esportazione CSV condivisa

Il componente è in `orodent-core`, nello stesso package `core.csv` usato
dall'importazione di Statistiche. Non dipende da JavaFX, JDBC o dai modelli di
un'applicazione specifica. Non modifica il database.

| Importazione esistente | Esportazione |
| --- | --- |
| `CsvReadOptions` | `CsvWriteOptions` |
| `CsvReader` | `CsvWriter` |
| `CsvRowMapper<T>` | `CsvExportMapper<T>` |
| `CsvImporter<T>` | `CsvExporter<T>` |
| Servizio del modulo, sui worker EcoS | Servizio del modulo, sui worker EcoS |

Il mapper del modulo definisce intestazioni ordinate e valori delle celle.
Il writer condiviso gestisce codifica, separatori, virgolette, righe e formati.
Il servizio sceglie i dati e l'ambito; il controller gestisce selezione del file,
conferma di sovrascrittura, avanzamento ed esito.

## Contratto dei dati

- Restituire numeri come `BigDecimal` o altri tipi numerici supportati, non
  etichette formattate dell'interfaccia (`"12,50 €"` non è un valore numerico).
- Il writer non arrotonda `BigDecimal`: scrive tutti i decimali disponibili,
  senza separatori delle migliaia e senza notazione esponenziale.
- `null` produce una cella vuota. Zero produce il numero zero. Quantità e costi
  mancanti non diventano zero.
- Unità e valuta appartengono alle intestazioni, ad esempio `Peso (g)` e
  `Costo polveri (EUR)`. Le date sono ISO `YYYY-MM-DD`.
- Stringhe, booleani, date/ore Java e numeri sono supportati; altri oggetti e
  numeri non finiti sono rifiutati. Ogni riga deve avere tante celle quante colonne.
- Intestazioni vuote o duplicate sono rifiutate. Esportare zero righe produce
  soltanto l'intestazione, con conteggio zero.

`CsvWriteOptions.excelItalian()` usa UTF-8 con BOM, separatore `;`, decimali con
virgola e terminatori CRLF. `commaSeparated()` usa virgola, punto decimale e nessun
BOM. Si possono costruire opzioni esplicite per altri destinatari.

I profili predefiniti antepongono un apostrofo ai **testi** che un foglio di
calcolo potrebbe interpretare come formule. I numeri negativi rimangono numeri.
Un'integrazione che richieda testi esatti può disattivare questa opzione.
Il CSV conserva i codici come stringhe, compresi gli zeri iniziali; Excel può
comunque applicare conversioni automatiche quando si apre un CSV. Per preservare
codici numerici lunghi o con zeri iniziali, importare quelle colonne come testo.
Non inseriamo formule Excel del tipo `="000123"` nei dati.

## Utilizzo dal modulo

```java
record CostRow(String articleCode, BigDecimal powderCost, BigDecimal totalCost) {}

CsvExportMapper<CostRow> mapper = new CsvExportMapper<>() {
    public List<String> headers() {
        return List.of("Codice articolo", "Costo polveri (EUR)", "Costo totale (EUR)");
    }

    public List<?> map(CostRow row) {
        // Arrays.asList ammette celle null, List.of no.
        return Arrays.asList(row.articleCode(), row.powderCost(), row.totalCost());
    }
};
CsvExporter<CostRow> exporter = new CsvExporter<>(new CsvWriter(), mapper);

// Congelare dati e parametri prima dell'esportazione; non leggere controlli FX sul worker.
List<CostRow> snapshot = List.copyOf(calculatedRows);
CompletableFuture<CsvExporter.Result> future = CompletableFuture.supplyAsync(
        () -> exporter.exportFile(path, snapshot, CsvWriteOptions.excelItalian()),
        sharedWorkers);
```

`exportFile` restituisce percorso e numero di righe dati. Scrive prima un file
temporaneo nella stessa cartella, chiude il writer, poi sostituisce la destinazione.
Errori di conversione o scrittura lasciano intatto il file precedente e rimuovono
il temporaneo, salvo blocchi esterni del filesystem. La sostituzione usa rename
atomico quando disponibile, con fallback a rename normale sullo stesso filesystem.
Non crea cartelle mancanti. Gli errori riportano il numero di record (intestazione
1, prima riga dati 2), oppure 0 per errori di apertura/chiusura/sostituzione del file.

La sorgente è percorsa una sola volta e le righe sono convertite una alla volta,
senza costruire in memoria l'intero testo CSV. I servizi possono fornire una lista
immutabile o un iterabile gestito nella durata della propria lettura. Devono
esportare tutto l'insieme richiesto, non soltanto la pagina caricata nella tabella.
Una sorgente che fallisce a metà interrompe l'esportazione del file.

`exportWriter` serve per stream e test: esegue flush ma non chiude il writer del
chiamante. Solo `exportFile` offre la sostituzione tramite file temporaneo.

## Stato dell'integrazione

Il motore condiviso è implementato e verificato. **Costi prodotti** usa i mapper
`ProductCostCsv` per riepilogo e dettaglio e il pannello riutilizzabile
`com.orodent.ecos.module.csv.CsvExportDialog` in `orodent-module-api`. Il pannello
mostra anteprima e conteggio, sceglie il file e scrive sui worker condivisi,
senza arrestarli alla chiusura.
[Uso e ambiti di esportazione](costi-e-consumi.md#esportazione-csv).

Anche [Consumi e fabbisogni](consumi-e-fabbisogni.md) usa lo stesso pannello e
writer per tabelle di copertura, serie giornaliere e contributi del piano.
