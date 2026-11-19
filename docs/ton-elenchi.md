# Elenchi comuni di TON

Gli elenchi principali di Polveri, Linee, Prodotti tecnici, Articoli, Composizioni,
Modelli disco e Modelli documento usano `features/technical/view/TonListView<T>`.
La palette è definita una sola volta in `ton/css/lists.css`, caricata dal componente.

## Struttura e opzioni

La struttura comune comprende descrizione e azioni in alto, ricerca con
**Aggiorna**, pannello bianco e tabella che occupa lo spazio disponibile.
Lo scorrimento appartiene alla tabella. Le colonne si distribuiscono sulla
larghezza disponibile. Non sono presenti contatori, frecce o pulsanti di apertura
in fondo; doppio clic o Invio aprono la riga selezionata.

Ogni elenco configura soltanto i contenuti necessari:

- testo introduttivo, ricerca e pulsante di creazione;
- colonne e formato dei valori;
- `filters(id, content)` per la sezione **Filtri**, inizialmente chiusa;
- `options(...)` per controlli come **Mostra anche archiviati**;
- `addAction(...)` per azioni aggiuntive, fra cui **Inserimento multiplo**.

Composizioni e Modelli disco ospitano il filtro di stato nella sezione **Filtri**.
Le sezioni opzionali assenti non occupano spazio.

## Responsabilità

Il componente gestisce impaginazione, controlli comuni, caricamento, errori e
attivazione della riga. Il controller passa la navigazione a `onOpen(...)`,
conservando i controlli sulle modifiche non salvate. Le query, la ricerca con
debounce, i filtri, l'annullamento delle letture superate e le scritture restano
nei controller e nei servizi di ciascuna funzione.

Si può estendere `TonListView<T>` per aggiungere colonne e controlli di una
funzione, oppure includerne un'istanza nella vista, come per i modelli documento.
I nuovi elenchi riutilizzano questo componente senza copiare il layout o il CSS.

Gli elenchi principali mostrano tutti i risultati filtrati. Nei documenti viene
caricata solo l'anagrafica dell'ultima versione di ciascun codice; il contenuto
completo e il WebView vengono caricati all'apertura della scheda.
Storici, versioni e riferimenti interni agli editor conservano la paginazione e
il componente `TechnicalTablePage`, perché non sono pagine elenco autonome.
Non sono necessarie migrazioni del database.
