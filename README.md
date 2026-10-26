# Orodent-EcoS

Un'unica applicazione desktop JavaFX, organizzata come monorepo Maven.
La Home permette di aprire Ton e Statistiche nello stesso processo JavaFX.

## Moduli

| Modulo | Responsabilità |
| --- | --- |
| `orodent-launcher` | Avvio dell’app in un processo separato e creazione dello ZIP di distribuzione |
| `orodent-app` | Avvio JavaFX, AppContainer, navigazione, Home e composizione delle dipendenze |
| `orodent-core` | Contratti comuni, ConnectionProvider e strumenti CSV indipendenti dalla UI |
| `orodent-database` | Derby HOST/CLIENT, discovery, schema, modelli, repository e servizi di importazione |
| `orodent-module-api` | Contratto dei moduli, metadati, servizi condivisi e ciclo di vita della UI |
| `orodent-ton` | Componente Ton, attualmente con una schermata iniziale |
| `orodent-statistiche` | Dashboard vendite, analisi clienti/prodotti/sconti/resi, archivio e importazioni CSV |
| `orodent-performance` | Solo con profilo `performance`: misure su dati sintetici, fuori dai pacchetti distribuiti |

Le dipendenze hanno una sola direzione: `app` compone i servizi e scopre i provider
`EcosModule` tramite `ServiceLoader`. TON e Statistiche implementano il contratto
di `module-api`, che espone JavaFX e i servizi condivisi; `database` utilizza `core`.
I componenti non dipendono dall'app. La Home genera il catalogo dai loro metadati,
con nome, icona, descrizione e apertura della schermata iniziale.
Il launcher dipende dall’app solo per assemblare la distribuzione: il suo codice non
carica JavaFX o classi dei componenti. Il modulo `core` non dipende da JavaFX o Derby. I package Java iniziano con
`com.orodent.ecos`; ogni modulo ha il proprio descrittore `module-info.java`.

Il POM principale centralizza versioni e plugin. `AppContainer` compone i servizi
condivisi: i componenti ricevono connessione, executor e servizi di importazione.
Anagrafiche e vendite risiedono in `orodent-database`, nello stesso database
`OrodentEcoS`. La versione dello schema è registrata dall'HOST; i CLIENT eseguono
solo una verifica di compatibilità. Vedere [integrazione Statistiche](docs/reuse-statistiche.md).

Lo schema DB 2 organizza le tabelle in `ECOS`, `ANAGRAFICHE` e `VENDITE`.
I dati delle precedenti tabelle `APP` vengono migrati dall'HOST in transazione.
Il passaggio può arrivare tramite una normale release dei componenti, mantenendo
il launcher installato: il contatore delle migrazioni è distinto dal contratto
degli aggiornamenti. Vedere [struttura e migrazione del database](docs/database-schema.md).
Prima di migrare dati esistenti, l'HOST salva un backup consistente sotto
`database.home/backups`; se la copia fallisce, la migrazione si ferma.
Vedere [backup e ripristino](docs/database-backup.md).

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
# Include navigazione dei moduli e import/lettura dati; richiede un display reale o virtuale
bash mvnw -Pui-smoke verify
# Solo database e sue dipendenze
bash mvnw -pl orodent-database -am verify
```

Il test grafico usa database temporaneo e porte dedicate.
Il wrapper è conservato nella radice; non esistono wrapper separati per modulo.

## Misure di prestazioni su richiesta

Gli strumenti di misura rimangono versionati in `orodent-performance/src/test`.
Non vengono eseguiti nelle build ordinarie e non sono inclusi nello ZIP o nell'EXE.

```sh
# Servizi e database, senza display
bash mvnw -Pperformance -pl orodent-performance -am verify
# Anche avvio, navigazione, memoria e reattività JavaFX
bash mvnw -Pperformance,performance-ui -pl orodent-performance -am verify
```

Su Windows usare `.\mvnw.cmd`. Ogni esecuzione genera un database nuovo sotto
`orodent-performance/target/performance/run-...`, senza usare quello di sviluppo
o dell'ufficio. La stessa cartella contiene `report.md`, misure CSV e log.
Il carico predefinito è di 50.000 vendite su tre anni; parametri, isolamento e
limiti delle misurazioni sono nella [guida alle prestazioni](docs/performance.md).

## Configurazione IntelliJ condivisa

La repository include `.run/Orodent-EcoS Sviluppo.run.xml`.
Dopo il pull e l'importazione del POM principale, selezionare **Orodent-EcoS - Sviluppo**
nel menu Run/Debug. La configurazione avvia `com.orodent.ecos.Launcher`, compila prima
di partire e usa il profilo sviluppo con percorsi relativi a `$PROJECT_DIR$`.
La variabile DB è vuota nella Run configuration per non ereditare una directory di
produzione da Windows; restano validi i valori di `config/development.properties`.

Selezionare una volta **File → Project Structure → Project → SDK** e indicare un
JDK 21 standard (Temurin o Liberica Standard, non Full): la configurazione eredita
il JDK del progetto, senza salvare il percorso personale del JDK nella repository.
Il vecchio `.idea/misc.xml` può ancora indicare Liberica Full: correggere l'SDK del
progetto prima del primo avvio della configurazione condivisa.
Se il modulo appare non risolto, ricaricare Maven e selezionare `orodent-app`
(o `orodent-app.main` se la propria importazione usa moduli distinti per source set).

Per condividere altre configurazioni: **Run → Edit Configurations → Store as project file**,
salvarle in `.run/` e committare soltanto quelle senza percorsi o credenziali personali.
Se esiste già una configurazione locale con lo stesso nome, eliminare la copia locale
o rinominarla per evitare due voci identiche. La configurazione condivisa riguarda
lo sviluppo; non serve per creare lo ZIP o l'EXE Windows.

## Prima distribuzione con launcher

```sh
bash mvnw clean package
```

Lo ZIP è generato in
`orodent-launcher/target/orodent-launcher-0.1.0-SNAPSHOT-distribution.zip`.
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

Questo ZIP richiede Java 21 già installato. Il pacchetto contiene le librerie native
JavaFX del sistema su cui viene compilato: generare la distribuzione Windows su
Windows. Per ottenere l'EXE con Java incluso, usare lo script descritto sotto.
Usare `clean package` per evitare JAR di vecchie versioni nella cartella di distribuzione.

Il launcher include il controllo degli aggiornamenti pubblici firmati su GitHub Releases.
Nei pacchetti manuali resta disabilitato finché non vengono configurate le chiavi e
pubblicata una release: seguire [la guida release](docs/releases.md).
Il database e il config personale rimangono esterni agli aggiornamenti.

## Windows portabile con EXE e Java incluso

Eseguire su **Windows** con un **JDK 21 standard completo, senza JavaFX incorporato** della stessa architettura delle
postazioni di destinazione. Servono `java`, `javac`, `jlink`, `jpackage` e `jmods`;
un JRE o il solo runtime di IntelliJ non bastano. Per `app-image` non serve WiX.

Dal terminale PowerShell nella radice del progetto:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1 -JdkHome "C:\Java\jdk-21"
```

Il percorso `C:\Java\jdk-21` è un esempio: sostituirlo con quello del proprio JDK
**Eclipse Temurin 21** o **Liberica Standard 21**. Non usare **Liberica Full**:
include JavaFX e può causare `Hash of javafx.base differs ... recorded in java.base`
quando il runtime carica i JAR JavaFX della distribuzione. Non disabilitare i controlli
degli hash. Se il JDK è registrato in IntelliJ, leggere il percorso in
**File → Project Structure → SDKs**. `-JdkHome` è facoltativo se `JAVA_HOME` indica
già il JDK 21 completo oppure `jpackage.exe` è disponibile nel PATH.
L'opzione di esecuzione vale per il solo processo PowerShell avviato.

Lo script seleziona quel JDK anche per Maven, esegue `clean package` con i test
headless, verifica la presenza delle librerie JavaFX Windows e risolve il grafo
dei moduli dell’app con il runtime incorporato tramite `--dry-run`, prima di creare
lo ZIP. Questa verifica non avvia il database. Produce:

```text
target/
├── windows-portable/
│   └── Orodent-EcoS/
│       ├── Orodent-EcoS.exe
│       ├── LEGGIMI.txt
│       ├── runtime/
│       └── app/
└── Orodent-EcoS-windows-portable.zip
```

Chiudere prima le copie del programma avviate da `target`: `clean` rigenera i file
di build. Il runtime mantiene `bin/java.exe` perché il launcher avvia l'app in
una seconda JVM; non dipende da Java installato sul PC di destinazione.

Estrarre lo ZIP interamente e fare doppio clic su `Orodent-EcoS.exe`.
Distribuire tutta la cartella, mai il solo EXE. Il contenuto interno sotto `app`
è gestito da jpackage; non spostare manualmente i JAR.

L'icona Orodent è usata per la barra delle applicazioni anche avviando da IntelliJ.
Su Windows il titolo mantiene i controlli nativi ma nasconde la piccola icona:
`WindowsWindowAppearance` usa JNA per applicare `WS_EX_DLGMODALFRAME` solo alla
finestra principale del processo. L'aspetto va verificato su Windows, anche con
ridimensionamento, massimizzazione e ritorno dalla barra delle applicazioni.
Il pacchetto Windows usa `packaging/windows/orodent-ecos.ico` per l'EXE;
il PNG per JavaFX è in `orodent-app/src/main/resources/icons/orodent-ecos.png`.
Gli aggiornamenti dei componenti aggiornano l'icona della finestra aperta;
per cambiare quella dell'EXE già distribuito serve il nuovo ZIP completo.

Nella cartella estratta il file **`config/application.properties`**, accanto all’EXE,
sceglie la directory database e le porte. Il valore distribuito è
`database.home=I:/CliZr/Tommaso/Database`: Derby crea la sottocartella `OrodentEcoS`.
Il percorso deve essere disponibile e scrivibile sulla macchina HOST.
I log rimangono in `%LOCALAPPDATA%\Orodent-EcoS\logs\application.log`
(con fallback sotto il profilo utente se LOCALAPPDATA manca).
Non copiare il file della configurazione di produzione nella configurazione di sviluppo.
Il database non viene spostato: le migrazioni supportate aggiornano lo schema
nel percorso configurato, con backup preventivo sull'HOST.

Il pacchetto non include database, installer o collegamenti. Per gli aggiornamenti
firmati occorre completare la [configurazione release](docs/releases.md).
Lo script Windows richiede una prova sul PC Windows; la disposizione dei file
jpackage e l'avvio del launcher con runtime incorporato sono verificabili anche
con un'immagine Linux equivalente, ma questo non verifica l'EXE Windows.

## Configurazione database: sviluppo e distribuzione

| Avvio | File | Directory HOST | TCP / discovery UDP |
| --- | --- | --- | --- |
| IntelliJ/app diretta, working directory radice repository | `config/development.properties` | `.local/database` nella repository | 1529 / 45681 |
| EXE Windows | `config/application.properties` accanto all’EXE | `I:/CliZr/Tommaso/Database` | 1528 / 45680 |
| Launcher JAR | `config/application.properties` accanto al JAR | Come distribuzione | 1528 / 45680 |

Il nome Derby resta `OrodentEcoS`: la sua cartella è creata **dentro** la directory
HOST. Nel file properties usare `/` anche nei percorsi Windows, oppure raddoppiare
le barre `\\`. I percorsi relativi del file sono risolti rispetto alla cartella del file.

Priorità della directory: **proprietà JVM `ton.database.home` → variabile
`ORODENT_ECOS_DATABASE_HOME` → `database.home` nel file → default di sviluppo**.
Per le porte: proprietà JVM → file. Il default di sviluppo senza file è
`~/.orodent-ecos/development`, TCP 1529, UDP 45681.

Per un file diverso usare l’opzione JVM:

```text
-Dorodent.ecos.config=C:/percorso/application.properties
```

Un file indicato esplicitamente ma mancante/illeggibile produce un errore: non
viene sostituito silenziosamente con un altro database. Nell’EXE il percorso è
configurato automaticamente; la distribuzione non richiede variabili d’ambiente.

**In IntelliJ**, per usare i dati di sviluppo, impostare la working directory alla
radice della repository e rimuovere l’eventuale `ORODENT_ECOS_DATABASE_HOME` di
produzione e la proprietà `ton.database.home` dalla configurazione Run. Evitare
una variabile globale di Windows che punti alla produzione per tutti i processi.
Se serve, impostare esplicitamente la directory di sviluppo nella sola Run configuration.
Le porte diverse impediscono il discovery incrociato: sviluppo e produzione possono
avere due HOST contemporaneamente, senza condividere i dati.

Il file della distribuzione si modifica dopo aver estratto lo ZIP; riavviare l’app
per applicare cambiamenti. Per modificare il valore predefinito delle distribuzioni
future, editare `orodent-launcher/src/distribution/config/application.properties`.
Quando si aggiorna il programma conservare le personalizzazioni del proprio file config.

Non viene eseguita alcuna migrazione dal precedente database `TonV3Database` né
fusione con Statistiche. Vedere [discovery](docs/database-discovery.md).

## Aggiungere un componente

1. Creare un modulo Maven figlio con lo stesso parent e dipendenza da `orodent-module-api`.
2. Registrarlo in `<modules>` e in `<dependencyManagement>` del POM principale.
3. Dichiarare soltanto le dipendenze necessarie, senza copiare database o executor.
4. Implementare `EcosModule` e registrare il provider in JPMS e `META-INF/services`.
   Aggiungere la dipendenza Maven in `orodent-app`: la Home lo rileva automaticamente.
5. Aggiungere test per la sua logica e verificare l'intero reactor con `bash mvnw verify`.

I moduli vengono compilati e aggiornati insieme; runtime, launcher e schema DB
richiedono una gestione distinta. Vedere [release e aggiornamenti](docs/releases.md).
Il contratto e il ciclo di vita sono descritti nella [guida ai moduli](docs/modules.md).

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
