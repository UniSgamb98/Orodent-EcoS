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

Nella cartella estratta il file **`config/application.properties`**, accanto all’EXE,
sceglie la directory database e le porte. Il valore distribuito è
`database.home=I:/CliZr/Tommaso/Database`: Derby crea la sottocartella `OrodentEcoS`.
Il percorso deve essere disponibile e scrivibile sulla macchina HOST.
I log rimangono in `%LOCALAPPDATA%\Orodent-EcoS\logs\application.log`
(con fallback sotto il profilo utente se LOCALAPPDATA manca).
Non copiare il file della configurazione di produzione nella configurazione di sviluppo.
Nessun database precedente viene spostato o migrato automaticamente.

Il pacchetto non include database, installer, collegamenti o aggiornamenti automatici.
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
