# Misurare le prestazioni

`orodent-performance` contiene esclusivamente codice di prova in `src/test/java`.
Il profilo Maven `performance` lo aggiunge al reactor soltanto quando richiesto.
Nessuna applicazione dipende da questo modulo: build ordinarie, ZIP, EXE e
aggiornamenti firmati continuano a distribuire solo i componenti applicativi.
Non occorre cancellare cartelle o modificare sorgenti prima delle release.

## Esecuzione da terminale

Serve un JDK 21 completo, lo stesso usato per compilare il progetto.
Dalla radice della repository, in PowerShell:

```powershell
# Database e analisi, senza aprire finestre
.\mvnw.cmd -Pperformance -pl orodent-performance -am verify

# Include avvio dell'applicazione e navigazione JavaFX; richiede un display
.\mvnw.cmd -Pperformance,performance-ui -pl orodent-performance -am verify
```

Su Linux/macOS sostituire `.\mvnw.cmd` con `bash mvnw`. Su Linux grafico serve
un display reale o virtuale e le librerie native JavaFX già richieste dall'app.
La prova UI non viene saltata silenziosamente se è richiesta ma manca il display:
fallisce e conserva il log. Il profilo `performance-ui` si usa insieme a `performance`.
Non avviare il launcher o l'EXE per fare queste prove.

`-am` ricompila e verifica anche le dipendenze: le misurazioni usano il codice
corrente del progetto. Dopo un `install` completo, per ripetere soltanto il
modulo di misura senza cambiare codice applicativo:

```powershell
.\mvnw.cmd -Pperformance,performance-ui -pl orodent-performance test
```

Le prove vengono lanciate in JVM figlie separate, con heap configurato e timeout.
L'avvio di Maven, la compilazione e gli altri test non fanno parte dei tempi.
Le JVM di misura usano il classpath dei test: non misurano la risoluzione JPMS
del pacchetto distribuito. Il driver grafico esegue anche controlli degli errori
e dei CSS durante il polling, con un costo di strumentazione proprio.
La generazione dei dati precede le misurazioni; la prova UI riparte in una nuova
JVM dopo l'arresto pulito del database generato dalla prova senza interfaccia.

## Dati e parametri

Il carico predefinito genera 50.000 righe di vendita, 1.000 clienti e 100 codici
prodotto su tre anni fino al 2025, con seed 42, circa il 5% di resi e sconti su
parte delle vendite. Sono dati sintetici: il numero di righe non dimostra che il
carico sia rappresentativo dell'ufficio. Il seed e l'ultimo anno sono fissi per
rendere ripetibili date e importi; si possono modificare esplicitamente.

| Parametro JVM Maven | Default | Significato |
| --- | ---: | --- |
| `performance.sales` | 50000 | Righe di vendita: 100–2.000.000 |
| `performance.customers` | 1000 | Clienti: 1–100.000, non più delle vendite |
| `performance.products` | 100 | Codici prodotto: 1–10.000 |
| `performance.years` | 3 | Anni: 1–10 |
| `performance.lastYear` | 2025 | Ultimo anno del dataset |
| `performance.seed` | 42 | Seed del generatore |
| `performance.iterations` | 5 | Campioni ripetuti dei servizi: 2–50 |
| `performance.cycles` | 5 | Aperture/chiusure ripetute di Statistiche: 2–30 |
| `performance.importRows` | 5000 | Righe del CSV sintetico per la prova UI |
| `performance.heapMb` | 512 | Heap massimo di ciascuna JVM di prova, in MiB: 256–8192 |
| `performance.timeoutSeconds` | 180 | Timeout delle operazioni; il processo figlio è limitato a quattro volte questo valore, minimo 120 secondi |

Esempio veloce per verificare che gli strumenti funzionino:

```powershell
.\mvnw.cmd -Pperformance,performance-ui -pl orodent-performance -am verify "-Dperformance.sales=3000" "-Dperformance.customers=100" "-Dperformance.products=20" "-Dperformance.iterations=2" "-Dperformance.cycles=2" "-Dperformance.importRows=300"
```

Per dataset molto più grandi aumentare l'heap e il timeout consapevolmente.
Mantenere gli stessi parametri quando si confrontano due versioni del codice.
Chiudere elaborazioni pesanti estranee alla prova e ripetere più volte l'intera
esecuzione. Conservare anche l'identificativo del commit confrontato.

## Isolamento del database

Ogni esecuzione crea una directory nuova:

```text
orodent-performance/target/performance/run-<data-UTC>-<id>/
├── settings.properties
├── environment.properties
├── dataset.properties
├── generated-database/OrodentEcoS/
├── headless-samples.csv
├── sql.csv
├── headless.log
├── report.md
└── ... file della prova UI, quando richiesta
```

La directory non viene riutilizzata e il generatore rifiuta un DB già presente.
Non sono accettati percorsi di database reali o file di configurazione dell'ufficio.
La prova senza interfaccia usa un URL JDBC assoluto della directory generata.
La prova grafica imposta esplicitamente directory, file config e porte libere;
richiede che l'app si avvii come HOST prima di procedere. La variabile
`ORODENT_ECOS_DATABASE_HOME` e i profili personali non scelgono il database di prova.
Il controllo degli aggiornamenti non viene eseguito: si avvia direttamente MainApp.

Il CSV generato viene importato in una data successiva al dataset principale;
non sostituisce le vendite usate per il confronto dei servizi. Rimane tutto
all'interno del database della singola esecuzione. Le prove non modificano né
copiano dati di produzione.

Le directory possono occupare spazio: restano disponibili per analisi e confronto.
Per eliminarle, dopo aver chiuso le prove e salvato i report utili:

```powershell
.\mvnw.cmd -Pperformance -pl orodent-performance clean
```

Questo pulisce solo il `target` del modulo selezionato.

## Cosa viene misurato

Senza interfaccia: creazione dello schema, generazione dei dati e servizi reali
di dashboard, riepilogo e dettaglio cliente, prodotti, sconti, resi e archivio.
Per ciascun servizio vengono registrati prima chiamata, riscaldamento e campioni
ripetuti. Il fatturato viene confrontato con quello calcolato dal generatore;
le connessioni devono essere rilasciate dopo ogni operazione.

`sql.csv` raggruppa per operazione e SQL: numero di esecuzioni, tempo totale e
massimo di `execute*`, errori. Questi tempi escludono lettura delle righe e calcoli
Java, già compresi nei tempi dei servizi. La strumentazione ha un costo proprio.

Con interfaccia: MainApp fino alla Home, prima apertura della dashboard, visite
alle analisi, cicli di apertura/chiusura, heap alla Home dopo richieste di GC,
ritardo di callback JavaFX accodati ogni 50 ms, uscita dal modulo durante il
caricamento e arresto dell'applicazione. Il controllo dei callback mantiene al
massimo un evento in attesa, evitando di riempire la coda grafica.
Il campione di memoria del ciclo 0 viene raccolto dopo la prima visita alle
analisi, così i cicli successivi partono con quelle classi già caricate.

La prova CSV usa il servizio di importazione reale su un executor dedicato di
misura e naviga in TON durante il lavoro. Registra se il pulsante è stato azionato
mentre l'importazione era ancora in corso; non automatizza il file chooser e non
modifica i servizi dell'app. La prova segue le uscite anticipate: eventuali letture
ancora attive possono competere con l'importazione.

## Come leggere il report

`report.md` mostra mediana, p95 e massimo, mentre i CSV conservano i singoli campioni.
Un esito PASSED significa che le operazioni e i controlli funzionali sono terminati,
non che le prestazioni siano automaticamente soddisfacenti. Un fallimento conserva
report e log parziali con esito FAILED. Non ci sono soglie temporali universali.

- La prima chiamata dei servizi avviene dopo aver generato i dati: non misura un
  disco con cache fredda. L'apertura grafica include due pulse; il polling è di
  50 ms. L'avvio UI esclude EXE, launcher e bootstrap della JVM.
- L'heap non comprende tutta la memoria del processo. Il GC può ignorare la richiesta;
  una crescita temporanea o riferimenti deboli non raccolti non dimostrano un leak.
  Per accertarlo servono esecuzioni più lunghe e strumenti come JFR/heap dump.
- I tre worker condivisi possono rimanere vivi alla Home. Si osserva la CPU dopo
  l'uscita anticipata; dopo la chiusura completa si richiedono zero worker EcoS.
- Il p95 usa il rango superiore. Con due o cinque campioni è spesso il massimo;
  per una distribuzione utile aumentare le iterazioni e ripetere le esecuzioni.
- Un display virtuale Linux e il PC Windows dell'ufficio possono dare risultati
  molto diversi. Confrontare le versioni nella stessa configurazione.

Per aggiungere un futuro modulo alle prove, aggiungere le sue operazioni ai
programmi di misura e la sua dipendenza con scope `test`; non introdurre sonde
nei componenti distribuiti e non esportare package applicativi solo per il profiler.
