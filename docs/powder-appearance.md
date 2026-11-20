# Colori e ordinamento delle polveri

Gli elenchi di polveri condividono l'ordine **fornitore → colore → classe Y →
codice alfabetico**, senza distinzione tra maiuscole e minuscole per fornitore e
codice. I colori seguono bianca, gialla, rosa, grigia; eventuali colori sconosciuti
vengono dopo. Fornitori non indicati e classi Y mancanti seguono i dati conosciuti
nel rispettivo raggruppamento. A parità di codice l'identificativo stabilizza
l'ordine anche tra pagine diverse.

La palette è quella di Costi e consumi:

| Colore | Sfondo |
| --- | --- |
| Bianca | `#f6f6f0` |
| Gialla | `#fff1b8` |
| Rosa | `#fbe1e8` |
| Grigia | `#e3e7ed` |
| Non indicato | `#edf3f7` |

Il colore identifica la polvere nella sua cella o nel selettore. La selezione
rimane riconoscibile; negli ordini restano visibili anche gli sfondi alternati
per ordine. Nei grafici dei consumi rimane un indicatore del colore della serie,
distinto dallo sfondo della polvere.

## Pagine interessate

- TON: elenco Polveri; selettori, legenda e matrice delle Composizioni; polveri
  nelle miscele di Produzione.
- Costi e consumi: Polveri e prezzi, collegamenti CSV e polveri degli ordini,
  materiali in Costi prodotti, tabelle e selezione delle polveri in Consumi e
  fabbisogni, prezzi/ingredienti/selezione polveri negli Scenari.

Le righe degli ordini continuano a essere raggruppate per ordine: l'ordinamento
comune riguarda il catalogo di polveri e le scelte dei collegamenti. I formati,
gli strati, gli ordini e le date mantengono il proprio significato operativo.
Il layout dei documenti stampati resta deciso dal modello dell'utente.

## Implementazione

`PowderAppearance` in core definisce palette e comparatore. `PowderCells` nel
module API applica gli sfondi alle celle; il selettore delle composizioni usa
la medesima palette anche nel popup e sulla voce selezionata.
`PowderDisplayCatalogue` nel database fornisce soltanto metadati di visualizzazione
per identità e revisioni esatte, oltre alle espressioni SQL condivise.
L'ordinamento delle query paginate viene applicato **prima** della paginazione.

I cataloghi usano la classe Y dell'ultima revisione approvata; in assenza di
approvate viene usata l'ultima disponibile. Nei riepiloghi che descrivono una
revisione precisa viene usata la sua classe Y. Un nuovo dato in bozza non
sostituisce quindi una scheda approvata nell'ordine del catalogo.

I metadati letti per le viste di costi, scenari e produzione restano separati dai
calcoli: non si cambiano quantità, prezzi, composizioni o fotografie salvate.
Non serve una migrazione dello schema DB. I vecchi scenari restano caricabili.
