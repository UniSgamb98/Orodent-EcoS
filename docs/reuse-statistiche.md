# Statistiche in Orodent-EcoS

Il modulo `orodent-statistiche` integra il codice di
[`UniSgamb98/Statistiche`](https://github.com/UniSgamb98/Statistiche/tree/codex/statistiche-app-icon),
branch `codex/statistiche-app-icon`, commit `1e7c4fdf8dd6fa0e648093ad26c4ee4a757374fd`.
Dalla Home di EcoS selezionare **Apri Statistiche**. Il pulsante **Orodent-EcoS**
nell'intestazione del modulo torna alla Home; **Dashboard** torna al riepilogo vendite.

## Funzionalità incluse

- Dashboard vendite e previsioni mensili.
- Analisi clienti: riepilogo, andamento, frequenza, follow-up, storico e proiezioni.
- Analisi prodotti, varianti e dettaglio dei clienti.
- Analisi sconti, resi e note di credito.
- Archivio vendite, con il limite di 500 righe del progetto originale.
- Importazione CSV vendite e anagrafica clienti, con avanzamento asincrono.

Le importazioni conservano la semantica del progetto originale: le vendite
sostituiscono le righe nell'intervallo di date del CSV; l'anagrafica sostituisce
l'elenco clienti. Ogni importazione esegue la sostituzione in una transazione.
L'integrazione non copia automaticamente i dati dal vecchio database Statistiche.

## Confini del modulo

`StatisticheModule` è l'unico punto d'ingresso esportato dal modulo Java.
`AppController` gli passa il `ConnectionProvider`, l'executor di background,
i due servizi di importazione e il callback di ritorno alla Home. La composizione
dei servizi analitici e la navigazione interna restano in `statistiche.internal`;
ogni funzionalità conserva i propri controller, servizi, modelli e viste.

Statistiche non crea un altro `Application`, `Stage`, server Derby o pool di thread.
`AppContainer` mantiene la proprietà delle risorse condivise e le chiude quando si
esce da EcoS. Il cambio di schermata rilascia i callback e le risorse grafiche della
pagina precedente. I CSS sono contenuti sotto `/statistiche/css` e applicati alla
radice del modulo.

## Database condiviso

Statistiche e gli altri moduli usano lo stesso database `OrodentEcoS`, scelto dal
profilo di sviluppo o produzione di EcoS. Non servono utenti Derby distinti per
aprire i diversi moduli: l'utente JDBC identifica la connessione dell'applicazione.
Il percorso, le porte e il ruolo HOST/CLIENT sono descritti in
[database-discovery.md](database-discovery.md).

Le tabelle clienti/vendite del progetto originale coincidono con quelle già
presenti in EcoS. Per mantenere la compatibilità, restano in `APP`: questa modifica
non le sposta in nuovi schemi. La separazione fisica per dominio richiederà una
migrazione successiva; non bisogna creare copie dell'anagrafica in ogni modulo.

`DatabaseSchema` registra la base compatibile come versione 1 in
`APP.ECOS_SCHEMA_HISTORY`, con checksum dello script e data di applicazione.
L'HOST crea le tabelle mancanti e registra la versione in un'unica transazione;
su un database esistente verifica la struttura e conserva le righe. Un CLIENT
controlla la struttura e l'eventuale storico senza scrivere. Una versione futura
o uno storico incompatibile impediscono l'apertura. Lo script della versione 1
non va modificato: cambiamenti strutturali futuri richiedono una nuova migrazione
e una revisione della compatibilità dichiarata dall'updater.

## Build, test e distribuzione

Dopo il pull ricaricare il POM principale in IntelliJ e usare la configurazione
**Orodent-EcoS - Sviluppo**. Non è necessaria una configurazione separata per Statistiche.

```sh
bash mvnw clean install
bash mvnw -pl orodent-app javafx:run
bash mvnw -Pui-smoke verify
```

Il test grafico usa database temporaneo e porte dedicate, importa dati CSV di
esempio tramite i servizi comuni e verifica la lettura dalle schermate analitiche,
oltre alla navigazione Home → Ton → Statistiche → Home. I test originali delle
analisi e dei componenti sono in `orodent-statistiche/src/test`.

Il normale package include automaticamente il JAR `orodent-statistiche` in
`app/lib` e nei componenti delle release. Non occorre cambiare lo script Windows
o configurare un secondo aggiornamento: il launcher aggiorna l'insieme coerente
dei JAR, mantenendo il protocollo e lo schema dati compatibili con la linea attuale.
