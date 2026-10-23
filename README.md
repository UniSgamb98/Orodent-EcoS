# Orodent-EcoS

Un'unica applicazione desktop JavaFX, organizzata come monorepo Maven.
Ton è il primo componente; Statistiche verrà integrato successivamente.

## Moduli

| Modulo | Responsabilità |
| --- | --- |
| `orodent-launcher` | Avvio dell’app in un processo separato e creazione dello ZIP di distribuzione |
| `orodent-app` | Avvio JavaFX, AppContainer, navigazione, Home e composizione delle dipendenze |
| `orodent-core` | Contratti comuni, ConnectionProvider e strumenti CSV indipendenti dalla UI |
| `orodent-database` | Derby HOST/CLIENT, discovery, schema, modelli, repository e servizi di importazione |
| `orodent-ton` | Componente Ton, attualmente con una schermata iniziale |

Le dipendenze hanno una sola direzione: `app` utilizza `core`, `database` e `ton`;
`database` utilizza `core`. `ton` per ora richiede solo JavaFX e non dipende dall'app.
Il launcher dipende dall’app solo per assemblare la distribuzione: il suo codice non
carica JavaFX o classi dei componenti. Il modulo `core` non dipende da JavaFX o Derby. I package Java iniziano con
`com.orodent.ecos`; ogni modulo ha il proprio descrittore `module-info.java`.

Il POM principale centralizza versioni e plugin. `AppContainer` compone i servizi
condivisi: ogni componente aggiunto utilizzerà questa base anziché avviare un proprio DB.
Anagrafiche e vendite rimangono temporaneamente in `orodent-database`;
la separazione dei domini e le migrazioni saranno affrontate con i prossimi moduli.

## Build e avvio

Richiede Java 21 e le librerie native JavaFX della piattaforma.
Dalla radice della repository:

```sh
bash mvnw clean install
bash mvnw -pl orodent-app javafx:run
```

Su Windows utilizzare `mvnw.cmd` al posto di `bash mvnw`.
`install` rende disponibili le dipendenze tra moduli per il successivo avvio.
Non usare `-am javafx:run`: tenterebbe di avviare anche i moduli di libreria.
In IntelliJ importare il `pom.xml` principale e ricaricare il progetto Maven;
il punto d'ingresso è `com.orodent.ecos.app.MainApp` (oppure `com.orodent.ecos.Launcher`).

```sh
# Test e package di tutti i moduli, senza richiedere un display
bash mvnw verify
# Include il test grafico Home → Ton → Home; richiede un display reale o virtuale
bash mvnw -Pui-smoke verify
# Solo database e sue dipendenze
bash mvnw -pl orodent-database -am verify
```

Il test grafico usa database temporaneo e porte dedicate.
Il wrapper è conservato nella radice; non esistono wrapper separati per modulo.

## Prima distribuzione con launcher

```sh
bash mvnw clean package
```

Lo ZIP è generato in
`orodent-launcher/target/orodent-launcher-1.0-SNAPSHOT-distribution.zip`.
Estrarlo interamente; la struttura interna è:

```text
Orodent-EcoS/
├── orodent-launcher.jar
├── LEGGIMI.txt
└── app/
    └── lib/                 # app, moduli e dipendenze runtime
```

Dalla cartella estratta:

```sh
java -jar orodent-launcher.jar
```

Il launcher usa lo stesso Java con cui è stato avviato, avvia l’app in una JVM
separata e ne restituisce il codice di uscita. La cartella di lavoro corrente
non deve coincidere con quella del launcher: i file sono cercati accanto al JAR.
Gli argomenti sono inoltrati all’app senza una shell intermedia.
La variabile `ORODENT_ECOS_DATABASE_HOME` viene ereditata dal processo figlio;
sono inoltrate anche le proprietà JVM `ton.database.home`, `ton.database.port`,
`ton.discovery.port` e `ton.discovery.timeout.ms` se impostate sul launcher.
Gli errori di avvio sono scritti su stderr e mostrati in una finestra quando
è disponibile un ambiente grafico.

Per questa prima fase occorre Java 21 già installato. Il pacchetto contiene le
librerie native JavaFX del sistema su cui viene compilato: generare la distribuzione
Windows su Windows. Il test di distribuzione è stato eseguito su Linux; il wrapping
Launch4j e l’esecuzione Windows sono passaggi successivi da verificare.
Usare `clean package` per evitare JAR di vecchie versioni nella cartella di distribuzione.

**Non è ancora un updater:** non consulta server, non scarica file e non modifica
l’installazione. Questa separazione prepara il prossimo passaggio: catalogo delle
release, download verificati e attivazione di una versione compatibile prima dell’avvio.
Il database è esterno alla distribuzione e non viene sostituito dal launcher.

## Database e compatibilità

Il database Derby si chiama `OrodentEcoS`. La variabile opzionale
`ORODENT_ECOS_DATABASE_HOME` sceglie la directory HOST; la proprietà JVM
`ton.database.home` mantiene la precedenza. Restano invariate directory
predefinite, TCP 1528 e UDP 45680. Il discovery accetta soltanto host che
pubblicano il nuovo nome database.

Un precedente database `TonV3Database` non viene rinominato o migrato automaticamente:
l'app crea `OrodentEcoS` nella directory configurata. Eventuali dati precedenti
restano nel vecchio database e richiedono una migrazione separata.
La vecchia variabile `TON_DATABASE_HOME` non viene più letta.
Non viene eseguita alcuna fusione con il database di Statistiche.

Vedere [configurazione e discovery](docs/database-discovery.md) e
[provenienza del codice](docs/reuse-statistiche.md).

## Aggiungere un componente

1. Creare un modulo Maven figlio, ad esempio `orodent-statistiche`, con lo stesso parent.
2. Registrarlo in `<modules>` e in `<dependencyManagement>` del POM principale.
3. Dichiarare soltanto le dipendenze necessarie, senza copiare database o executor.
4. Esporre la pagina iniziale del componente e collegarla dalla navigazione di `orodent-app`.
5. Aggiungere test per la sua logica e verificare l'intero reactor con `bash mvnw verify`.

I moduli vengono compilati e distribuiti insieme; il sistema di aggiornamento
automatico non è ancora implementato.

## Rinominare la repository su GitHub

La struttura funziona già senza cambiare URL. Quando vuoi rinominarla:

1. Apri `UniSgamb98/Ton-V3` su GitHub, quindi **Settings → General**.
2. In **Repository name** inserisci `Orodent-EcoS` e premi **Rename**.
3. Nei checkout esistenti aggiorna il remote:

   ```sh
   git remote set-url origin https://github.com/UniSgamb98/Orodent-EcoS.git
   git remote -v
   ```

4. Aggiorna anche la repository selezionata nelle impostazioni dell'ambiente cloud
   e gli eventuali collegamenti esterni.

GitHub normalmente reindirizza il vecchio URL e conserva issue e pull request.
Non creare un'altra repository chiamata Ton-V3 dopo la rinomina: interromperebbe
il reindirizzamento. Il nome della cartella locale può restare Ton-V3;
se la sposti, aggiorna anche le istruzioni dell'ambiente che usano percorsi assoluti.
