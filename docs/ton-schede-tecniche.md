# Schede comuni di Dati tecnici

Le schede di Linee, Prodotti tecnici, Articoli, Polveri, Composizioni e Modelli
disco condividono `features/technical/view/TechnicalDetailView`.
Il componente è circoscritto a **Dati tecnici**: Documenti e le future sezioni
Produzione e Forni e cotture mantengono libertà nella propria struttura.

## Struttura

Il contenitore organizza:

- **Torna a…** a sinistra e **Aggiorna dati** a destra;
- riquadro di stato con revisione/versione e messaggi di sola lettura;
- controlli specifici accanto allo stato, come apertura del padre, selezione
  versione o creazione di una nuova versione;
- schede centrali e scorrimento dei rispettivi contenuti;
- barra inferiore delle azioni e messaggi di esito/errore.

I titoli sono nell'intestazione del modulo, compresa la composizione identificata
con codice e nome del prodotto. Il componente espone il titolo della scheda alla
vista che gestisce la navigazione.

La palette è definita in `ton/css/technical-editor.css`, caricata dal contenitore
e limitata alla classe `.ton-technical-detail`. Le pagine configurano i contenuti
con `status(...)`, `addStatusAction(...)`, `addSheet(...)` e `actions(...)`.
Linee, Prodotti e Articoli continuano a usare il proprio editor parametrizzato,
che a sua volta estende il contenitore comune.

## Regole e dati

La base gestisce solo la presentazione. Controlli sulle modifiche non salvate,
caricamento dei dati, salvataggi, versioni, archiviazione, eliminazione e regole
di sola lettura appartengono ancora ai rispettivi controller e servizi.
Il codice che mostra o abilita un'azione resta nella funzione che ne conosce
le condizioni. Nei cataloghi la barra inferiore resta visibile nell'Anagrafica;
nelle altre schede valgono i comportamenti già previsti per ciascuna funzione.

Il selettore delle versioni delle Composizioni resta disponibile accanto allo
stato. Polveri e Modelli disco conservano l'accesso alle versioni dalla scheda
Versioni. Le anteprime del modello e il contenuto della ricetta mantengono i
propri componenti e adattamenti alla larghezza.
Non cambiano schema del database o formati dei dati salvati.
