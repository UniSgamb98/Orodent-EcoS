# TON: proposta di inserimento multiplo

Stato: proposta UI da valutare. La demo HTML usa solo dati di esempio e non scrive
nel database. Il catalogo di produzione mantiene per ora il proprio inserimento
singolo.

[Apri la demo interattiva](mockups/ton-inserimento-multiplo.html).

## Accesso e contesto

- Nella scheda Linea, accanto a «Nuovo prodotto»: **Crea più prodotti…**.
- Nella scheda Prodotto, accanto a «Nuovo articolo»: **Crea più articoli…**.
- Negli elenchi, la stessa azione richiede prima di scegliere il padre.

La linea si seleziona una volta sola per creare prodotti; per gli articoli si
selezionano uno o più prodotti. Aprendo il flusso dalla scheda di un prodotto,
questo è preselezionato ma si possono aggiungere altri prodotti. Si propongono
solo collegamenti attivi e utilizzabili già previsti dal catalogo. La creazione
singola resta disponibile per i casi occasionali.

## Più prodotti per una linea

1. La linea è già selezionata.
2. Si scelgono più colori commerciali, con possibilità di aggiungerne altri.
3. L'anteprima genera una riga per colore. Il codice segue una composizione a
   blocchi modificabile, inizialmente **Linea + Colore**, che conserva la regola
   attuale. Si possono riordinare le parti **Linea**, **Colore**, **Materiale** e
   **Testo fisso**, scegliendo anche un separatore. La descrizione deriva dalla
   linea e dal colore. «Senza colore» non aggiunge un suffisso colore né un
   separatore finale.
4. **Personalizza composizione…** offre lo stesso pannello degli articoli, con
   esempio aggiornato durante la scelta e annullamento delle modifiche.
5. Le note comuni si compilano una volta sola; si selezionano le righe da creare.

Esempio: MST con A0, A1, A2, A3 produce MSTA0, MSTA1, MSTA2, MSTA3.
La grafia dei colori e i limiti dei campi restano quelli di `CatalogModels`.
Con **Materiale + Linea + Colore**, ad esempio, si ottiene ZRMSTA3. Una regola
che non distingue i colori genera duplicati segnalati nell'anteprima.

Prodotti e articoli hanno composizioni indipendenti: modificare la regola dei
nuovi prodotti non cambia quella degli articoli o i codici degli elementi già
salvati. La linea e il colore sono collegamenti espliciti delle righe, non vengono
ricavati dal codice generato. Un codice già usato per un altro colore della linea
viene segnalato come conflitto.

Questa configurabilità estende la creazione attuale, dove i prodotti seguono una
regola fissa. L'implementazione applicativa dovrà mantenere coerenti inserimento
singolo e multiplo, senza ricalcolare i codici dei prodotti esistenti.

## Articoli per più prodotti

1. Si selezionano più prodotti con caselle di spunta, ricerca per codice/colore
   e filtro per linea. I selezionati restano visibili anche cambiando filtro.
   Linea, colore e materiale si ereditano dal prodotto di ciascuna riga.
2. Si inseriscono uno o più diametri e una serie di altezze.
3. **Genera combinazioni** prepara il prodotto cartesiano **prodotti × diametri ×
   altezze**, mostrando il conteggio atteso prima della generazione.
4. **Composizione del codice** mostra blocchi ordinati: **Materiale**, **Diametro**,
   **Altezza**, **Prodotto**. **Personalizza composizione…** apre un pannello per
   spostarli con le frecce, aggiungerli o rimuoverli, inserire un **Testo fisso** e
   scegliere un separatore. Non si scrivono formule né parentesi. L'esempio del
   primo articolo si aggiorna subito; **Annulla** conserva la regola precedente.
   Il materiale è ereditato dalla linea (ZR o ZS); se manca va completato prima
   di generare un codice che lo usa. La convenzione resta da confermare prima
   dell'implementazione, soprattutto per misure decimali e zeri iniziali.
5. Ogni codice e ogni misura è correggibile nella griglia. Si possono escludere
   le combinazioni indesiderate senza ricominciare.

Esempio: MSTA1, MSTA2 e MSTA3 con diametro 98 e cinque altezze producono quindici
articoli, cinque per ciascun prodotto. Con due diametri diventano trenta.

L'anteprima include la colonna **Prodotto collegato**. Ogni riga mantiene
l'identificativo del proprio prodotto: correggere il codice dell'articolo non
cambia questo collegamento. Il componente **Prodotto** usa il codice del prodotto
di quella riga; il componente **Materiale** usa la materia prima della sua linea.
La demo include anche un prodotto di un'altra linea per mostrare i prefissi ZR/ZS.
I codici sono etichette, non sono usati per indovinare il collegamento al prodotto.
Un codice appartenente a un articolo di un altro prodotto viene segnalato come
conflitto, senza spostare articoli o trasformarlo in un elemento già presente.

La demo mostra un controllo preventivo dei codici duplicati e dei valori
incompleti. La prima versione applicativa deve usare anche i limiti e la precisione
già adottati dal catalogo, senza aggiungere vincoli impliciti di unicità del formato.

## Anteprima e conferma

Le due operazioni condividono lo stesso schema visivo:

- A sinistra i dati comuni e i criteri di generazione.
- A destra la griglia modificabile, con riga, selezione ed esito.
- Conteggio delle righe, degli elementi da creare e di quelli già presenti.
- Pulsante finale esplicito: **Crea 10 articoli** / **Crea 10 prodotti**.

In una finestra stretta i pannelli si dispongono in verticale con uno scorrimento
naturale della pagina. La griglia permette Tab e modifiche delle righe senza
rigenerarsi ad ogni carattere digitato.

La validazione applicativa deve distinguere:

- **Nuovo**: codice disponibile e dati validi.
- **Già presente**: stesso codice e stessi dati strutturali/collegamento. Escluso
  esplicitamente dalla creazione; nessun aggiornamento silenzioso.
- **Conflitto**: codice già usato per dati strutturali diversi, anche in archivio.
  Occorre correggere o escludere la riga.
- **Da correggere**: duplicato interno, campo obbligatorio mancante, misura non
  valida o limite di lunghezza/precisione superato.

Le righe escluse non bloccano il salvataggio delle altre. Il conteggio finale deve
rispecchiare esattamente quanto sarà creato. All'uscita si proteggono le righe non
salvate con la stessa conferma di abbandono degli editor TON.

## Salvataggio e configurazioni tecniche

La futura implementazione deve riconvalidare codici e collegamenti all'ultimo momento e
salvare tutte le nuove righe selezionate in una singola transazione, con lo storico
individuale già previsto dal catalogo. Un conflitto concorrente annulla il gruppo,
lascia l'anteprima disponibile e indica cosa aggiornare.

La creazione anagrafica non deve attribuire approvazioni tecniche implicitamente.
Ricette, modelli disco e specifiche restano un passaggio esplicito. Un eventuale
comando successivo per applicare una configurazione comune deve creare bozze
distinte per ciascun articolo.

Dopo la creazione dei prodotti si può offrire **Crea gli articoli per questi
prodotti**, aprendo lo stesso flusso con quei prodotti già selezionati. Per le
linee, invece, una griglia codice/nome/tecnologia/materia prima permette
l'inserimento multiplo senza generare automaticamente tecnologie.

L'inserimento multiplo usa selezioni e combinazioni interne a TON; l'importazione
da Excel è stata rimossa dalla proposta.
