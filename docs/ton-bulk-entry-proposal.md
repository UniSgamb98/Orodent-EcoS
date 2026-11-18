# TON: inserimento multiplo di prodotti e articoli

Stato: implementato in TON. La pagina JavaFX riprende la demo approvata e salva
le anagrafiche nel database EcoS. La demo HTML resta un riferimento grafico con
dati di esempio; la creazione singola resta disponibile.

[Apri la demo interattiva](mockups/ton-inserimento-multiplo.html).

## Accesso e contesto

- Nella scheda Linea, accanto a «Nuovo prodotto»: **Crea più prodotti…**.
- Nella scheda Prodotto, accanto a «Nuovo articolo»: **Crea più articoli…**.
- Negli elenchi Prodotti tecnici e Articoli: **Inserimento multiplo…**; si sceglie
  la linea o si selezionano i prodotti. Un filtro padre già impostato viene ripreso.

La linea si seleziona una volta sola per creare prodotti; per gli articoli si
sceglie una linea e si selezionano uno o più prodotti appartenenti a quella linea.
Aprendo il flusso dalla scheda di un prodotto, linea e prodotto sono preselezionati;
si possono aggiungere altri prodotti della stessa linea. Si propongono
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

La creazione singola propone la concatenazione linea/colore. Una modifica alle
sole note o allo stato di un prodotto creato con una composizione personalizzata
conserva il suo codice, se linea e colore non cambiano.

## Articoli per più prodotti

1. Si sceglie una linea dal menu a tendina, poi i prodotti con le caselle di spunta.
   La ricerca dei prodotti non cambia la selezione. **Seleziona tutto** comprende
   tutti i prodotti attivi della linea, anche oltre la pagina caricata o fuori
   dalla ricerca corrente. Cambiare linea azzera la selezione precedente.
   L'elenco è più alto e non mostra etichette separate dei prodotti selezionati.
   Non esistono ricerca della linea, opzione «Tutte le linee» o riquadro di riepilogo
   dei prodotti selezionati. Linea, colore e materiale si ereditano dal prodotto.
2. Si inseriscono uno o più diametri e una serie di altezze.
3. L'anteprima si aggiorna automaticamente con le combinazioni **prodotti × diametri ×
   altezze**, dopo una pausa di circa 450 ms nella digitazione o nelle selezioni.
   Non serve un pulsante per rigenerare: i conteggi compaiono soltanto a destra.
4. **Personalizza composizione…** apre un pannello con i blocchi **Materiale**,
   **Diametro**, **Altezza**, **Prodotto**, per
   spostarli con le frecce, aggiungerli o rimuoverli, inserire un **Testo fisso** e
   scegliere un separatore. Non si scrivono formule né parentesi. L'esempio del
   primo articolo si aggiorna subito; **Annulla** conserva la regola precedente.
   Il materiale è ereditato dalla linea (ZR o ZS); se manca va completato prima
   di generare un codice che lo usa. Le misure decimali usano il punto nei codici
   e non aggiungono zeri iniziali o decimali superflui: 12,50 diventa 12.5.
5. Ogni codice e ogni misura è correggibile nella griglia. Si possono escludere
   le combinazioni indesiderate senza ricominciare.

Esempio: MSTA1, MSTA2 e MSTA3 con diametro 98 e cinque altezze producono quindici
articoli, cinque per ciascun prodotto. Con due diametri diventano trenta.

L'anteprima include la colonna **Prodotto collegato**. Ogni riga mantiene
l'identificativo del proprio prodotto: correggere il codice dell'articolo non
cambia questo collegamento. Il componente **Prodotto** compone **codice linea
senza prefisso materiale + colore commerciale**, usando i dati strutturati della
riga e non il codice salvato del prodotto. Rimuove una sola occorrenza iniziale
corrispondente alla materia prima della linea; le altre parti del codice linea
restano invariate. **Senza colore** non aggiunge un suffisso.
Il componente **Materiale** usa la materia prima della linea.

Esempio: le linee ZRMST (ZR) e ZSMST (ZS), entrambe con colore A0, generano
rispettivamente **ZR9812MSTA0** e **ZS9812MSTA0**. Codici delle linee, codici dei
prodotti e collegamenti nel DB restano invariati, anche con prodotti dal codice
personalizzato. Gli articoli già salvati non vengono rinominati.
La demo permette di scegliere separatamente una linea ZR o una linea ZS.
Ogni gruppo contiene prodotti di una sola linea; il servizio ricontrolla questo
vincolo sul DB anche al salvataggio.
I codici sono etichette, non sono usati per indovinare il collegamento al prodotto.
Un codice appartenente a un articolo di un altro prodotto viene segnalato come
conflitto, senza spostare articoli o trasformarlo in un elemento già presente.

La pagina verifica sul DB codici duplicati, valori incompleti, collegamenti e
codici riservati dallo storico. Applica i limiti già adottati dal catalogo:
misure positive con massimo due decimali e vincolo esistente di formato univoco
per prodotto. Lo stesso colore non può essere creato due volte nella stessa linea.
Ogni gruppo può contenere al massimo 1.000 righe; i selettori caricano 50 risultati
alla volta e mantengono i prodotti selezionati cambiando ricerca nella stessa linea.

## Anteprima e conferma

Le due operazioni condividono lo stesso schema visivo:

- A sinistra i dati comuni e i criteri di generazione.
- A destra la griglia modificabile, con riga, selezione ed esito.
- Conteggio delle righe, degli elementi da creare e di quelli già presenti.
- Pulsante finale esplicito: **Crea 10 articoli** / **Crea 10 prodotti**.

In una finestra stretta i pannelli si dispongono in verticale con uno scorrimento
naturale della pagina. La griglia permette Tab e modifiche delle righe senza
rigenerarsi ad ogni carattere digitato.

La validazione distingue:

- **Nuovo**: codice disponibile e dati validi.
- **Già presente**: stesso codice e stessi dati strutturali/collegamento. Escluso
  esplicitamente dalla creazione; nessun aggiornamento silenzioso.
- **Conflitto**: codice già usato per dati strutturali diversi, anche in archivio.
  Occorre correggere o escludere la riga.
- **Da correggere**: duplicato interno, campo obbligatorio mancante, misura non
  valida o limite di lunghezza/precisione superato.

Le righe escluse non bloccano il salvataggio delle altre. Il conteggio finale
rispecchia quanto sarà creato. All'uscita si proteggono le modifiche non
salvate con la conferma di abbandono degli editor TON. Le due modalità mantengono
bozze indipendenti finché si rimane nella pagina. Gli aggiornamenti automatici
conservano codici, misure corrette manualmente ed esclusioni delle combinazioni
che restano nel gruppo. Le combinazioni rimosse dalla selezione o dai formati
scompaiono dall'anteprima. Dati incompleti o non validi disabilitano la creazione
finché non vengono corretti. La digitazione nelle celle mantiene il cursore.
Il calcolo automatico non scrive sul DB: occorre sempre premere **Crea**.

## Salvataggio e configurazioni tecniche

Il salvataggio riconvalida codici, collegamenti e revisioni all'ultimo momento e
crea tutte le nuove righe selezionate in una singola transazione, con lo storico
individuale già previsto dal catalogo. Un conflitto concorrente o un errore di
scrittura annulla il gruppo. La bozza resta disponibile per aggiornare e
ricontrollare l'anteprima; non vengono ripetute scritture automaticamente. Durante
il salvataggio la navigazione è bloccata. Dopo la creazione compare il riepilogo
e si può aprire l'elenco oppure continuare nell'altra modalità.

La creazione anagrafica non attribuisce approvazioni tecniche implicitamente.
Ricette, modelli disco e specifiche restano un passaggio esplicito. Un eventuale
comando successivo per applicare una configurazione comune deve creare bozze
distinte per ciascun articolo.

Restano sviluppi futuri la creazione multipla di linee, un comando per applicare
configurazioni tecniche comuni e il passaggio diretto dai prodotti appena creati
agli articoli con preselezione automatica. I nuovi prodotti sono già ricercabili
nella modalità articoli della stessa pagina.

L'inserimento multiplo usa selezioni e combinazioni interne a TON; non include
importazione da Excel.
