# Ritmo di vendita e quantità tipica acquistata

In **Statistiche → Analisi → Prodotti**, aprire **Ritmo di vendita** nel dettaglio
del gruppo per vedere gli indicatori. La sezione parte chiusa per lasciare spazio
alle tabelle; la sua intestazione mostra già il ritmo settimanale. Contiene:

- **Dischi / settimana**: quantità positiva venduta, divisa per i giorni di
  calendario osservati e moltiplicata per 7. Include giorni e settimane senza
  vendite; non parte dalla prima vendita del singolo prodotto.
- **Dischi / settimana recenti**: lo stesso calcolo sugli ultimi 91 giorni
  compresi nel periodo osservato. Se lo storico dell'anno è più corto, usa solo
  i giorni disponibili e ne mostra il numero.
- **Tipico / documento**: mediana delle quantità acquistate per documento che
  contiene il gruppo. Con due valori centrali si usa la loro media.
- **Media / documento**: quantità positiva venduta divisa per il numero di quei
  documenti.

Il tab **Varianti** aggiunge `Dischi/sett.`, `Tipico/doc.` e `Media/doc.` prima
della quantità netta. Le misure valgono per la singola variante della dimensione
scelta (altezza, diametro, colore o famiglia); per esempio due altezze nello
stesso documento contano insieme nel gruppo e separatamente nelle rispettive
varianti. Quantità, fatturato e quote esistenti conservano il significato netto.

Gli indicatori usano solo le aggregazioni `VENDITA` con quantità positiva.
Resi e note di credito non entrano nelle quantità per documento né nel ritmo:
continuano a comparire nei totali netti e nelle rettifiche. Non si presume che
un reso sia automaticamente disponibile a magazzino.

Le righe e i codici appartenenti allo stesso gruppo o variante vengono sommati
**prima** di calcolare media e mediana. L'identità del documento include anno,
cliente e identificativo. La sorgente importata è una fase/magazzino del
movimento, non un registro documentale: non divide un acquisto in più documenti.
Il dato disponibile è il documento di vendita importato, non un ordine commerciale
ricostruito: la UI usa quindi la parola documento. Il numero di documenti è
visibile nel riepilogo e nei tooltip delle celle. Meno di 5 documenti viene
segnalato come campione ridotto, senza fingere una precisione statistica.

Il periodo va dal più recente tra il 1° gennaio dell'anno selezionato e la prima
data disponibile nell'archivio, fino alla data di riferimento già usata dalla
pagina (limitata a oggi e all'ultima data dei dati). Non include mesi futuri
dell'anno in corso. La prima e l'ultima transazione delimitano lo storico
osservato: non certificano che tutte le importazioni intermedie siano complete.
La pagina espone le date e i giorni utilizzati.

Esempio: in 28 giorni, tre documenti contengono 2, 4 e 22 dischi. Il ritmo è
**7 dischi/settimana**, la mediana è **4 dischi/documento**, la media circa
**9,33 dischi/documento**. La differenza fra media e mediana rende evidente il
peso dell'acquisto più grande.

Sono descrizioni storiche, non previsioni di esaurimento: non conoscono giacenza,
merce in arrivo o scarti. Con 70 dischi disponibili e un ritmo di 7 a settimana,
il semplice rapporto dà circa 10 settimane **se il ritmo si mantiene**; tale
simulazione non viene presentata come data garantita e non è un nuovo calcolo
automatico di giacenza.

Il calcolo è puro su uno snapshot già caricato, eseguito dai worker del dettaglio
prodotti. Cambiare dimensione o ordinamento non genera nuove query. Non vengono
aggiunti dati persistenti o migrazioni DB; i numeri non sono salvati come totali
duplicati e vengono ricalcolati quando si ricaricano i dati.
