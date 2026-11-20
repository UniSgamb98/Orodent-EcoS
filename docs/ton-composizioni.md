# Composizioni

Le schede usano il [contenitore comune di Dati tecnici](ton-schede-tecniche.md).

Aprire **TON → Dati tecnici → Composizioni**, oppure **Apri composizioni**
dalla Panoramica TON. Il catalogo usa provider ed executor di EcoS e le tabelle
dei cataloghi TON. La V17 mantiene nel database solo le percentuali fisiche della ricetta.

## Elenco e dati generali

La pagina usa il [componente comune degli elenchi TON](ton-elenchi.md).

L'elenco mostra una riga per prodotto, con linea, colore, numero di strati,
ultima versione approvata ed eventuale bozza. Ricerca e filtri mostrano tutti
i risultati in una sola tabella scorrevole; si possono includere anche i prodotti
archiviati.
Ricerca, filtro, tabella e comandi sono in un pannello bianco con la stessa
palette degli altri elenchi TON. **Aggiorna** è accanto alla ricerca; in basso
non ci sono pulsante di apertura, frecce di pagina o contatore.
Le colonne riempiono uniformemente lo spazio. Doppio clic o Invio aprono la scheda.
Anche la scheda Versioni mostra un elenco completo senza contatore o frecce.

**Nuova composizione** sceglie un prodotto attivo con linea e colore già definiti.
Linea e colore sono ereditati in sola lettura. Non occorre inventare un codice
per la ricetta: la identificano prodotto e numero di versione. Dopo il primo
salvataggio il prodotto resta fisso; le note appartengono alla singola versione.

## Ricetta per strato

Ogni strato contiene polveri, revisioni precise e quote percentuali in massa.
Il selettore indica insieme codice, versione, nome e classe Y della polvere.
Le opzioni e la polvere selezionata hanno lo sfondo del rispettivo colore, con
la stessa [palette comune di TON e Costi e consumi](powder-appearance.md). L'ordine è per fornitore, poi bianca,
gialla, rosa, grigia, quindi classe da 3Y a 6Y; a parità di classe segue il codice.
I fornitori non indicati e le classi Y mancanti seguono quelli conosciuti.
Il selettore propone tutte le ultime revisioni approvate delle polveri attive,
senza ricerca o paginazione. Una bozza più recente non sostituisce la scheda approvata. Una revisione
già inserita rimane visibile nella propria riga anche se precedente all'ultima,
fuori dal catalogo attivo o successivamente ritirata: i riferimenti salvati
non vengono aggiornati automaticamente.

- Si possono aggiungere, rimuovere, spostare e nominare gli strati. La numerazione
  viene mantenuta consecutiva. La sequenza è distinta dalle quote di altezza
  definite nel modello disco.
- **Copia da strato** sostituisce gli ingredienti dello strato selezionato dopo
  conferma. I nuovi campi sono indipendenti: modificarli non cambia lo strato origine.
- Le bianche restano sempre manuali; modificare il totale di una rosa/gialla
  non cambia le quantità delle altre polveri.
- I campi accettano virgola o punto e mostrano al massimo un decimale. Le vecchie
  quote conservano la loro precisione originale finché non vengono modificate.
  I candidati allo 0% rimangono solo nel buffer; ogni ingrediente salvato è positivo
  e al massimo 100%.
- Una stessa polvere compare una sola volta nello strato, anche se si tentano
  di usare due revisioni differenti. Per approvare, ogni strato deve arrivare
  esattamente a 100% e contenere almeno un ingrediente.

La barra della miscela e il totale si aggiornano durante la compilazione.
Solo i controlli dello strato selezionato vengono costruiti; gli altri dati
restano nel buffer della ricetta, comprese le modifiche ancora da correggere.

Il calcolo del croma è sempre disponibile: rosa e gialle hanno **Tinta %** e
**Totale %**, collegate in entrambe le direzioni. Bianche e grigie
hanno solo la percentuale finale. Il croma è fissato dall’utente; il programma
non cambia automaticamente il croma o le altre quantità quando si modifica una
percentuale finale. La targhetta dello strato segnala sia miscela sia tinta.
Vedere [calcolo, arrotondamento e salvataggio](ton-composizioni-croma.md).

## Riepilogo e ossidi

**Riepilogo** mostra la matrice ingredienti/strati, i totali e il bilancio chimico
nominale. Per ogni ossido e strato il calcolo è:

`somma(quota della polvere / 100 × wt% dell'ossido nella revisione scelta)`.

Ad esempio, 75% di una polvere con Y₂O₃ a 5,150 wt% e 25% di una polvere
con Y₂O₃ a 9,350 wt% danno **6,200 wt% Y₂O₃**.

Il valore è mostrato solo se la ricetta dello strato è completa e tutte le
polveri dichiarano quell'ossido. Altrimenti compare **Dati parziali**, con la
copertura dei dati in massa. Un dato mancante non viene assunto pari a zero;
la parte nota non viene rinormalizzata e gli ossidi non vengono completati
automaticamente fino a 100%.

La classe 3Y/4Y/5Y/6Y resta distinta da wt% Y₂O₃. Non si calcolano medie di queste
etichette, proprietà meccaniche della miscela o un valore chimico globale del
disco: quest'ultimo richiederebbe le masse effettive dei diversi strati.
Il calcolo assistito del croma è descritto nella [guida ppm](ton-composizioni-croma.md).

## Versioni e salvataggio

**Salva bozza** salva note, strati e ingredienti in un'unica transazione. La bozza
può avere totali incompleti; i campi compilati devono essere validi. È ammessa una
sola bozza per prodotto. Un salvataggio identico non incrementa inutilmente il
contatore di modifica.

**Salva e approva** controlla prodotto e linea attivi, numerazione consecutiva,
quote al 100%, ingredienti distinti e revisioni approvate di polveri attive.
Una scheda della polvere con ossidi parziali non impedisce l'approvazione della
ricetta: il riepilogo segnala i dati mancanti.

Una versione approvata conserva note, strati e riferimenti in sola lettura.
**Crea nuova versione** copia esattamente il contenuto in una bozza: l'adozione
di una nuova revisione di polvere è sempre esplicita. È possibile copiare anche
una versione ritirata; i riferimenti non più disponibili vanno corretti prima
della nuova approvazione.

**Ritira versione** conserva la ricetta e gli utilizzi storici, escludendola dalle
nuove selezioni. **Elimina bozza** è consentito soltanto se mai utilizzata,
ricontrollando i riferimenti nella transazione. La scheda **Versioni** permette
di raggiungere anche le versioni più vecchie scorrendo l'elenco completo.
Doppio clic o Invio apre la versione; **Aggiorna** è allineato a destra del titolo
e la tabella occupa l'altezza disponibile, usando la struttura comune di Dati tecnici.

Uscita, ricarica e cambio versione chiedono conferma in presenza di modifiche
non salvate. Durante le scritture editor e navigazione sono bloccati. I conflitti
mantengono i campi locali e richiedono una ricarica esplicita; nessun salvataggio
interrotto viene ripetuto automaticamente dopo il ripristino della connessione.

## Modelli e prodotti

**Modelli** cerca le versioni approvate e confronta numero e numerazione degli
strati con la ricetta corrente, anche se ancora in modifica. Il filtro **Solo
strati corrispondenti** mostra la corrispondenza strutturale; non certifica da solo
la compatibilità del formato o del processo. **Apri modello** apre la versione
precisa. La composizione non memorizza un modello esclusivo.

L'abbinamento effettivo avviene in **Prodotti tecnici → Specifiche di produzione**:
prodotto + versione approvata di composizione dello stesso prodotto + versione
approvata di modello. Il servizio verifica anche strati e totali.
**Utilizzi** elenca le specifiche che puntano alla versione selezionata e permette
di aprire il prodotto, anche se archiviato. Approvare una nuova ricetta non
sostituisce i riferimenti già salvati nelle specifiche precedenti.

## Organizzazione del codice

`features/compositions` separa dati immutabili, SQL, transazioni, controller e viste.
`TON.COMPOSIZIONI`, `TON.COMPOSIZIONE_STRATI` e `TON.COMPOSIZIONE_INGREDIENTI`
conservano le percentuali fisiche. Croma e tinta sono calcoli della vista,
ricostruiti dalle percentuali salvate. Il bilancio nominale viene derivato dalle revisioni delle
polveri, senza colonne aggiuntive di medie. L'abbinamento con il modello rimane
in `TON.CONFIGURAZIONI_PRODOTTO` dalla V21.

Le scritture bloccano linea, prodotto, composizione e polveri in ordine coerente
con gli altri cataloghi. I controlli di stato e concorrenza avvengono nel servizio,
anche se la richiesta arriva da una seconda postazione. Le letture del dettaglio
mantengono coerenti intestazione e figli. Ricerca con debounce e controllo delle
richieste superate evitano aggiornamenti tardivi della vista. La scheda Versioni
carica la lista completa; gli altri riferimenti e selettori mantengono i propri
limiti di pagina.
Uscendo vengono rilasciati listener, timer e buffer; query e transazioni non
girano sul thread grafico.
