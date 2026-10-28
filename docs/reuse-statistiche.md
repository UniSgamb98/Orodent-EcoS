# Statistiche in Orodent-EcoS

Il modulo `orodent-statistiche` integra il codice di
[`UniSgamb98/Statistiche`](https://github.com/UniSgamb98/Statistiche/tree/codex/statistiche-app-icon),
branch `codex/statistiche-app-icon`, commit `1e7c4fdf8dd6fa0e648093ad26c4ee4a757374fd`.
Dalla Home di EcoS selezionare **Apri Statistiche**. Il pulsante con icona casa
**Home Orodent-EcoS**, in alto a sinistra nell'intestazione del modulo, torna
al catalogo delle applicazioni; **Dashboard** torna al riepilogo vendite.

## Funzionalità incluse

- Dashboard vendite e previsioni mensili.
- Analisi clienti: riepilogo, andamento, frequenza, follow-up, storico e proiezioni.
- Analisi prodotti, varianti e dettaglio dei clienti.
- Ritmo settimanale, confronto recente e quantità media/mediana per documento,
  anche per variante: [definizioni e limiti](statistiche-ritmo-vendite.md).
- Analisi sconti, resi e note di credito.
- Archivio vendite, con il limite di 500 righe del progetto originale.
- Importazione CSV vendite e anagrafica clienti, con avanzamento asincrono.

Le importazioni conservano la semantica del progetto originale: le vendite
sostituiscono le righe nell'intervallo di date del CSV; l'anagrafica sostituisce
l'elenco clienti. Ogni importazione esegue la sostituzione in una transazione.
L'integrazione non copia automaticamente i dati dal vecchio database Statistiche.

## Confini del modulo

`StatisticheModule` implementa il contratto comune `EcosModule` ed è registrato
come provider JPMS e classpath. Il catalogo ne legge nome, descrizione e icona;
`AppController` lo apre attraverso `ModuleContext`, con il `ConnectionProvider`,
l'executor di background, i due servizi CSV e il ritorno alla Home.
La sessione restituita espone la radice e rilascia le risorse alla chiusura.
Vedere [contratto dei moduli](modules.md). La composizione
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

Le tabelle fisiche sono `ANAGRAFICHE.CLIENTI` e `VENDITE.VENDITE_DETTAGLIO`.
Statistiche le condivide con gli altri moduli; non mantiene copie proprie.

`DatabaseSchema` migra i database precedenti da `APP` ai nuovi schemi in un'unica
transazione, conservando dati, identificativi, timestamp e sequenza delle vendite.
Lo storico delle versioni 1 e 2 è in `ECOS.ECOS_SCHEMA_HISTORY`.
Il CLIENT richiede lo schema corrente 4 e controlla la compatibilità senza scrivere.
Vedere [struttura e migrazioni del database](database-schema.md).

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
dei JAR. Il contatore delle migrazioni DB è separato dal contratto del launcher:
anche il passaggio da schema 1 a 2 può arrivare tramite una normale release firmata.
Riavviare prima l'HOST e poi i CLIENT; vedere [la procedura](database-schema.md).
