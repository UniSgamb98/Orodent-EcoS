# TON: proposta di inserimento multiplo

Stato: proposta UI da valutare. La demo HTML usa solo dati di esempio e non scrive
nel database. Il catalogo di produzione mantiene per ora il proprio inserimento
singolo.

[Apri la demo interattiva](mockups/ton-inserimento-multiplo.html).

## Accesso e contesto

- Nella scheda Linea, accanto a «Nuovo prodotto»: **Crea più prodotti…**.
- Nella scheda Prodotto, accanto a «Nuovo articolo»: **Crea più articoli…**.
- Negli elenchi, la stessa azione richiede prima di scegliere il padre.

Il padre si seleziona una volta sola e resta visibile sopra i dati comuni. Si
propongono solo i collegamenti attivi e utilizzabili già previsti dal catalogo.
La creazione singola resta disponibile per i casi occasionali.

## Più prodotti per una linea

1. La linea è già selezionata.
2. Si scelgono più colori commerciali, con possibilità di aggiungerne altri o
   incollare una colonna da Excel.
3. L'anteprima genera una riga per colore usando le regole già esistenti:
   codice della linea + colore, descrizione della linea + colore. «Senza colore»
   conserva la regola di codice attuale.
4. Le note comuni si compilano una volta sola; si selezionano le righe da creare.

Esempio: MST con A0, A1, A2, A3 produce MSTA0, MSTA1, MSTA2, MSTA3.
La grafia dei colori e i limiti dei campi restano quelli di `CatalogModels`.

## Più articoli per un prodotto

1. Il prodotto è già selezionato; linea e colore sono ereditati.
2. Si inseriscono uno o più diametri e una serie di altezze. Si può anche
   incollare una tabella da Excel per formati che non seguono una matrice.
3. **Genera combinazioni** prepara tutte le coppie diametro/altezza.
4. Una regola di codice modificabile genera l'anteprima, per esempio
   `ZR{diametro}{altezza}{prodotto}`. La convenzione è una proposta: va confermata
   prima di adottarla nel catalogo, soprattutto per misure decimali.
5. Ogni codice e ogni misura è correggibile nella griglia. Si possono escludere
   le combinazioni indesiderate senza ricominciare.

Esempio: diametri 98 e 95, altezze 12, 14, 16, 18 e 20: dieci righe. Se serve
un solo diametro con dieci altezze, si generano dieci articoli dello stesso prodotto.

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
naturale della pagina. La griglia permette Tab e incolla da Excel senza rigenerarsi
ad ogni carattere digitato.

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

La futura implementazione deve riconvalidare codici e padre all'ultimo momento e
salvare tutte le nuove righe selezionate in una singola transazione, con lo storico
individuale già previsto dal catalogo. Un conflitto concorrente annulla il gruppo,
lascia l'anteprima disponibile e indica cosa aggiornare.

La creazione anagrafica non deve attribuire approvazioni tecniche implicitamente.
Ricette, modelli disco e specifiche restano un passaggio esplicito. Un eventuale
comando successivo per applicare una configurazione comune deve creare bozze
distinte per ciascun articolo.

Come secondo sviluppo, dopo la creazione dei prodotti si può offrire **Crea gli
articoli per questi prodotti**, riutilizzando gli stessi formati con anteprima del
numero totale. Per le linee, invece, una griglia codice/nome/tecnologia/materia
prima permette l'inserimento multiplo senza generare automaticamente tecnologie.
