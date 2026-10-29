# Composizioni

Aprire **TON → Dati tecnici → Composizioni**, oppure **Apri composizioni**
dalla Panoramica TON. Il catalogo usa provider ed executor di EcoS e le tabelle
già presenti nello schema V6: non introduce una nuova migrazione.

## Elenco e dati generali

L'elenco mostra una riga per prodotto, con linea, colore, numero di strati,
ultima versione approvata ed eventuale bozza. Ricerca e filtri sono paginati
per 50 risultati; si possono includere anche i prodotti archiviati.

**Nuova composizione** sceglie un prodotto attivo con linea e colore già definiti.
Linea e colore sono ereditati in sola lettura. Non occorre inventare un codice
per la ricetta: la identificano prodotto e numero di versione. Dopo il primo
salvataggio il prodotto resta fisso; le note appartengono alla singola versione.

## Ricetta per strato

Ogni strato contiene polveri, revisioni precise e quote percentuali in massa.
Il selettore indica insieme codice, versione, nome e classe Y della polvere.
La ricerca è paginata e propone solo l'ultima revisione approvata di ogni polvere
attiva. Una bozza più recente non sostituisce la scheda approvata. Una revisione
già inserita rimane visibile nella propria riga anche se precedente all'ultima,
fuori dalla pagina corrente o successivamente ritirata: i riferimenti salvati
non vengono aggiornati automaticamente.

- Si possono aggiungere, rimuovere, spostare e nominare gli strati. La numerazione
  viene mantenuta consecutiva. La sequenza è distinta dalle quote di altezza
  definite nel modello disco.
- **Copia da strato** sostituisce gli ingredienti dello strato selezionato dopo
  conferma. I nuovi campi sono indipendenti: modificarli non cambia lo strato origine.
- Aggiungere o rimuovere ingredienti non ridistribuisce le percentuali.
- Sono accettati virgola o punto e fino a quattro decimali, senza arrotondamenti
  dei valori salvati. Ogni quota deve essere positiva e al massimo 100%.
- Una stessa polvere compare una sola volta nello strato, anche se si tentano
  di usare due revisioni differenti. Per approvare, ogni strato deve arrivare
  esattamente a 100% e contenere almeno un ingrediente.

La barra della miscela e il totale si aggiornano durante la compilazione.
Solo i controlli dello strato selezionato vengono costruiti; gli altri dati
restano nel buffer della ricetta, comprese le modifiche ancora da correggere.

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
Il futuro strumento di progettazione assistita con dosi/ppm resta da realizzare.

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
di raggiungere anche le revisioni più vecchie tramite paginazione.

Uscita, ricarica e cambio versione chiedono conferma in presenza di modifiche
non salvate. Durante le scritture editor e navigazione sono bloccati. I conflitti
mantengono i campi locali e richiedono una ricarica esplicita; nessun salvataggio
interrotto viene ripetuto automaticamente dopo il ripristino della connessione.

## Modelli e articoli

**Modelli** cerca le versioni approvate e confronta numero e numerazione degli
strati con la ricetta corrente, anche se ancora in modifica. Il filtro **Solo
strati corrispondenti** mostra la corrispondenza strutturale; non certifica da solo
la compatibilità del formato o del processo. **Apri modello** apre la versione
precisa. La composizione non memorizza un modello esclusivo.

L'abbinamento effettivo avviene in **Articoli → Specifiche di produzione**:
articolo + versione approvata di composizione dello stesso prodotto + versione
approvata di modello. Il servizio verifica anche strati e totali.
**Utilizzi** elenca le specifiche che puntano alla versione selezionata e permette
di aprire l'articolo, anche se archiviato. Approvare una nuova ricetta non
sostituisce i riferimenti già salvati nelle specifiche precedenti.

## Organizzazione del codice

`features/compositions` separa dati immutabili, SQL, transazioni, controller e viste.
`TON.COMPOSIZIONI`, `TON.COMPOSIZIONE_STRATI` e `TON.COMPOSIZIONE_INGREDIENTI`
conservano i dati; il bilancio nominale viene derivato dalle revisioni delle
polveri, senza colonne aggiuntive di medie. L'abbinamento con il modello rimane
in `TON.CONFIGURAZIONI_ARTICOLO`.

Le scritture bloccano linea, prodotto, composizione e polveri in ordine coerente
con gli altri cataloghi. I controlli di stato e concorrenza avvengono nel servizio,
anche se la richiesta arriva da una seconda postazione. Le letture del dettaglio
mantengono coerenti intestazione e figli. Ricerca con debounce, pagine limitate
e controllo delle richieste superate evitano aggiornamenti tardivi della vista.
Uscendo vengono rilasciati listener, timer e buffer; query e transazioni non
girano sul thread grafico.
