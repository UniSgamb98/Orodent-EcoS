# Orodent-EcoS

Un'unica applicazione desktop JavaFX, organizzata come monorepo Maven.
Ton è il primo componente; Statistiche verrà integrato successivamente.

## Moduli

| Modulo | Responsabilità |
| --- | --- |
| `orodent-app` | Avvio JavaFX, AppContainer, navigazione, Home e composizione delle dipendenze |
| `orodent-core` | Contratti comuni, ConnectionProvider e strumenti CSV indipendenti dalla UI |
| `orodent-database` | Derby HOST/CLIENT, discovery, schema, modelli, repository e servizi di importazione |
| `orodent-ton` | Componente Ton, attualmente con una schermata iniziale |

Le dipendenze hanno una sola direzione: `app` utilizza `core`, `database` e `ton`;
`database` utilizza `core`. `ton` per ora richiede solo JavaFX e non dipende dall'app.
Il modulo `core` non dipende da JavaFX o Derby. I package Java iniziano con
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

## Database e compatibilità

Questa fase riorganizza il codice senza cambiare formato, nome o posizione dei dati.
Restano validi `ton.database.home`, `TON_DATABASE_HOME`, `TonV3Database`,
TCP 1528 e UDP 45680. Il protocollo di rete rimane quello introdotto in Ton-V3.
Non viene eseguita alcuna migrazione né fusione con il database di Statistiche.

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
