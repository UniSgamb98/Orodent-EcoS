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

## Versioni e Revisioni

`TechnicalRecordsView<T>` fornisce la struttura comune delle schede **Versioni**
di Polveri, Composizioni e Modelli disco e delle schede **Revisioni** di Linee,
Prodotti tecnici e Articoli. Il titolo e **Aggiorna** sono sulla stessa riga,
con il pulsante a destra; la tabella occupa l'altezza disponibile. Tutte le righe
si consultano scorrendo, senza contatore, frecce di pagina o pulsante di apertura.
Doppio clic sulla riga o Invio apre il contenuto selezionato.

I nomi distinguono due comportamenti conservati dai servizi: le **Versioni**
tecniche hanno stato Bozza/Approvata/Ritirata e riferimenti precisi nelle ricette
e nelle specifiche; le **Revisioni** anagrafiche registrano i dati a ogni
salvataggio effettivo e aprono una finestra in sola lettura con lo snapshot.
Il componente comune non modifica questi cicli di vita.

Anche le liste di prodotti della Linea e di articoli del Prodotto occupano
l'altezza disponibile. Le schede **Utilizzi** di Polveri, Composizioni e Modelli
disco riusano lo stesso
`TechnicalRecordsView<T>` per mostrare i prodotti collegati. Condividono titolo
e Aggiorna sulla stessa riga, tabella a tutta altezza, caricamento/errori e apertura
con doppio clic su una riga o Invio. Non hanno pulsanti in basso, frecce o contatore;
tutti gli utilizzi si raggiungono scorrendo, anche oltre il precedente limite di
50 righe. Le singole funzioni configurano solo colonne, descrizione e lettura dei
riferimenti. Gli Utilizzi di composizioni e modelli rimangono legati alla versione
precisa selezionata, comprese le specifiche storiche.
