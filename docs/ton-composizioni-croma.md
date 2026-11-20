# Croma per strato

In **Dati tecnici → Composizioni → Ricetta per strato**, il calcolo del croma
è sempre disponibile. Nome facoltativo e croma sono affiancati sopra le polveri.
Non ci sono interruttori di modalità, ricerca o paginazione delle polveri:
il selettore contiene tutte le revisioni approvate disponibili e conserva
l’eventuale revisione storica già usata dalla ricetta.

Le righe e i selettori seguono l’ordine **fornitore → colore (bianca, gialla,
rosa, grigia) → classe Y → codice alfabetico**. Le righe senza selezione
rimangono in fondo. Gli sfondi mantengono la palette comune delle polveri.

## Due percentuali, un croma scelto dall’utente

- Per le rosa e gialle, **Tinta %** indica il rapporto fra tutte le sole rosa e
  gialle; **Totale %** indica la quota di quella polvere nell’intero
  strato. Entrambe le caselle sono modificabili.
- Cambiare la tinta aggiorna le percentuali finali con il croma impostato.
  Durante il riequilibrio è possibile avere temporaneamente una tinta diversa
  dal 100%.
- Cambiare una percentuale finale aggiorna la tinta corrispondente. Le quantità
  delle altre polveri rimangono ferme: riequilibrarle spetta all’utente.
- Per bianche e grigie compare solo la percentuale finale modificabile; il
  selettore occupa anche lo spazio libero della casella tinta.
- Il croma resta **fisso durante l’editing** e viene cambiato solo dall’utente.
  Le bianche non vengono completate o ripartite automaticamente.

La targhetta accanto al titolo dello strato mostra insieme totale della miscela
e totale della tinta. Diventa verde quando i totali sono corretti, rossa quando
uno dei due è incompleto o ci sono valori da correggere. Il totale della tinta
non si applica agli strati senza polveri rosa/gialle.

## Formula e esempio

La regola empirica del laboratorio, espressa con frazioni, è:

`quota rosa/gialla = (ppm / 2000 − quota grigia totale) × rapporto rosa/gialla`.

Con i campi espressi in percentuale:

`percentuale finale = (ppm / 20 − percentuale grigia totale) × tinta / 100`.

Con **1000 ppm**, **1% grigia**, **60% gialla e 40% rosa** fra le colorate:

| Polvere | Percentuale di tutto lo strato |
| --- | ---: |
| Gialla | 29,4% |
| Rosa | 19,6% |
| Grigia | 1% |
| Bianche da inserire manualmente | 50% |

Le grigie vengono sommate prima della sottrazione. I ppm restano distinti dal
bilancio chimico degli ossidi e dalla classe Y.

## Arrotondamento e salvataggio

I campi percentuali mostrano al massimo **una cifra decimale**. I nuovi calcoli
ripartiscono le quote rosa/gialle a passi di 0,1%, assegnando gli eventuali
residui ai maggiori resti affinché la somma arrotondata rimanga coerente.
Il calcolo inverso usa la stessa quota colorata arrotondata. Con un croma che
richiede più precisione delle quote in decimi, la miscela rappresenta la sua
approssimazione; il campo croma non viene corretto automaticamente.

Aprire una ricetta precedente non modifica né arrotonda le percentuali già
salvate a quattro decimali. Il tooltip della percentuale riporta il valore
esatto conservato; solo un intervento esplicito cambia quella quantità.

Nel database si salvano **solo strati e percentuali fisiche positive**, come
prima del calcolo assistito. Tinta e croma rimangono nel buffer durante la
compilazione e i cambi di strato, ma non vengono registrati separatamente.
Alla riapertura sono ricavati dalle percentuali effettive: croma = somma delle
quote rosa, gialle e grigie × 20; tinta = quota / somma rosa e gialle × 100.
Perciò la ricostruzione riflette anche l’arrotondamento delle quantità salvate.
I candidati allo 0% non entrano nella ricetta fisica e non riappaiono dopo la
riapertura.

La V17 elimina le tabelle aggiunte dalla V16 per i parametri derivati, senza
modificare ingredienti, percentuali o stato delle versioni esistenti.

Il croma accetta 0–2000 ppm e deve coprire il grigio inserito. Le bozze possono
avere le bianche da completare. Per approvare servono miscela e tinta complete,
oltre ai controlli di validità già previsti. Le versioni approvate restano
in sola lettura.
