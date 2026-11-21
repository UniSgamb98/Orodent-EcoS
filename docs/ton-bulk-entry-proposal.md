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

1. La linea è preselezionata aprendo dalla sua scheda; altrimenti si sceglie dal
   menu. Il menu resta sempre visibile e mostra la linea selezionata, senza un
   riquadro di riepilogo o un comando separato per cambiarla.
2. Si scelgono più colori commerciali, con possibilità di aggiungerne altri.
3. L'anteprima genera una riga per colore. Il codice segue una composizione a
   blocchi modificabile, inizialmente **Linea + Colore**, che conserva la regola
   attuale. Si possono riordinare le parti **Linea**, **Colore** e
   **Testo fisso**, scegliendo anche un separatore. La descrizione deriva dalla
   linea e dal colore. «Senza colore» non aggiunge un suffisso colore né un
   separatore finale.
4. **Personalizza composizione…** offre lo stesso pannello degli articoli, con
   esempio aggiornato durante la scelta e annullamento delle modifiche.
5. Le note comuni si compilano una volta sola; si selezionano le righe da creare.

Esempio: MST con A0, A1, A2, A3 produce MSTA0, MSTA1, MSTA2, MSTA3.
La grafia dei colori e i limiti dei campi restano quelli di `CatalogModels`.
Con **Testo fisso «ZR» + Linea + Colore**, ad esempio, si ottiene ZRMSTA3. Una regola
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
   Non ci sono caselle di ricerca. **Seleziona tutto** comprende tutti i prodotti
   attivi della linea, anche oltre la pagina caricata. Cambiare linea azzera la selezione precedente.
   L'elenco è più alto e non mostra etichette separate dei prodotti selezionati.
   Non esistono ricerca della linea, opzione «Tutte le linee» o riquadro di riepilogo
   dei prodotti selezionati. Linea e colore si ereditano dal prodotto.
2. Si inseriscono uno o più diametri e una serie di altezze.
3. L'anteprima si aggiorna automaticamente con le combinazioni **prodotti × diametri ×
   altezze**, dopo una pausa di circa 450 ms nella digitazione o nelle selezioni.
   Non serve un pulsante per rigenerare: i conteggi compaiono soltanto a destra.
4. **Personalizza composizione…** apre un pannello con i blocchi **Diametro**, **Altezza**, **Prodotto**, per
   spostarli con le frecce, aggiungerli o rimuoverli, inserire un **Testo fisso** e
   scegliere un separatore. Non si scrivono formule né parentesi. L'esempio del
   primo articolo si aggiorna subito; **Annulla** conserva la regola precedente.
   I prefissi ZR o ZS si inseriscono con Testo fisso. Le misure decimali usano il punto nei codici
   e non aggiungono zeri iniziali o decimali superflui: 12,50 diventa 12.5.
5. Codice articolo, diametro e altezza sono in sola lettura nell'anteprima.
   Le misure si cambiano nei campi comuni e i codici da **Personalizza composizione…**.
   Nella griglia si possono soltanto includere o escludere le combinazioni.

Esempio: MSTA1, MSTA2 e MSTA3 con diametro 98 e cinque altezze producono quindici
articoli, cinque per ciascun prodotto. Con due diametri diventano trenta.

L'anteprima include la colonna **Prodotto collegato**. Ogni riga mantiene
l'identificativo del proprio prodotto, indipendentemente dalla composizione
scelta per il codice. Il componente **Prodotto** compone **codice linea + colore
commerciale**, usando i dati strutturati della riga e non il codice salvato del
prodotto. **Senza colore** non aggiunge un suffisso. Un Testo fisso iniziale già
presente nel prodotto sposta quel prefisso davanti al formato senza ripeterlo.

Esempio: ZRMST con colore A0, Testo fisso ZR, diametro 98 e altezza 12 genera
**ZR9812MSTA0**. Codici delle linee, codici dei prodotti e collegamenti nel DB
restano invariati, anche con prodotti dal codice personalizzato. Gli articoli
già salvati non vengono rinominati.
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
alla volta e mantengono i prodotti selezionati caricando altre pagine della stessa linea.

## Anteprima e conferma

Le due operazioni condividono lo stesso schema visivo:

- A sinistra soltanto i campi e i criteri di generazione, senza intestazione o frasi introduttive.
- A destra la griglia in sola lettura, con selezione delle righe ed esito.
- Conteggio delle righe, degli elementi da creare e di quelli già presenti.
- Pulsante finale esplicito: **Crea 10 articoli** / **Crea 10 prodotti**.
- Una sola nota sotto l'anteprima riunisce il collegamento alle anagrafiche e il
  passaggio tecnico successivo; usa una riga quando lo spazio lo permette.

A due colonne, elenco prodotti e anteprima occupano l'altezza disponibile e hanno
il proprio scorrimento; campi e pulsante Crea rimangono visibili senza far scorrere
l'intera pagina. Torna al catalogo e Aggiorna dati condividono la stessa riga.
In una finestra stretta o troppo bassa i pannelli si dispongono in verticale con
uno scorrimento naturale della pagina. La griglia permette di cambiare le spunte
senza modificare i valori generati.

La validazione distingue:

- **Nuovo**: codice disponibile e dati validi.
- **Già presente**: stesso codice e stessi dati strutturali/collegamento. Escluso
  esplicitamente dalla creazione; nessun aggiornamento silenzioso.
- **Conflitto**: codice già usato per dati strutturali diversi, anche in archivio.
  Occorre modificare i dati comuni o la composizione, oppure escludere la riga.
- **Da correggere**: duplicato interno, campo obbligatorio mancante, misura non
  valida o limite di lunghezza/precisione superato.

Le righe escluse non bloccano il salvataggio delle altre. Il conteggio finale
rispecchia quanto sarà creato. All'uscita si proteggono le modifiche non
salvate con la conferma di abbandono degli editor TON. Le due modalità mantengono
bozze indipendenti finché si rimane nella pagina. Gli aggiornamenti automatici
ricalcolano codici e misure dalle impostazioni e conservano le esclusioni delle
combinazioni che restano nel gruppo. Le combinazioni rimosse dalla selezione o dai formati
scompaiono dall'anteprima. Dati incompleti o non validi disabilitano la creazione
finché non vengono corretti. Il riquadro rosso **Da correggere** appare solo
quando ci sono problemi: resta utile anche senza editing delle celle, ad esempio
se la regola genera codici duplicati o se il DB contiene formati con codici diversi.
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
agli articoli con preselezione automatica. I nuovi prodotti sono già selezionabili
nella modalità articoli della stessa pagina.

L'inserimento multiplo usa selezioni e combinazioni interne a TON; non include
importazione da Excel.

## Prefissi espliciti

Il blocco Materiale è stato rimosso. La regola iniziale degli articoli concatena
Diametro, Altezza e Prodotto (linea + colore). Per `ZR9512MSTA0`, usare Testo fisso
`ZR`, Diametro, Altezza, Prodotto. Se il prodotto inizia già con quel testo fisso,
il prefisso viene spostato davanti al formato senza ripeterlo: `ZRMSTA0` →
`ZR9512MSTA0`. Non viene ricavato dalla materia prima né dalle polveri.
