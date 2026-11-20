# Croma per strato

In **Dati tecnici → Composizioni → Ricetta per strato**, ogni strato può usare
**Calcola rosa e gialle dai ppm**. Le ricette precedenti rimangono in modalità
manuale finché l’utente sceglie di attivare il calcolo.

1. Selezionare le polveri con le rispettive revisioni.
2. Impostare il croma dello strato in ppm.
3. Per ogni rosa o gialla inserire il rapporto rispetto alla somma delle sole
   rosa e gialle. Questi rapporti devono sommare esattamente 100%.
4. Per le grigie inserire la percentuale rispetto a tutto lo strato.
5. Leggere **Finale sul totale**: sono le percentuali effettive calcolate.
6. Completare manualmente le bianche fino al 100% dello strato. Il pannello
   indica bianche necessarie, già inserite e ancora da aggiungere o in eccesso.

Le bianche non vengono ripartite o completate automaticamente. Nelle quote
manuali `1` significa **1%**, nei rapporti `60` significa **60%**.

## Formula e esempio

La regola empirica del laboratorio, espressa con frazioni, è:

`quota rosa/gialla = (ppm / 2000 − quota grigia totale) × rapporto rosa/gialla`.

Con i campi espressi in percentuale la stessa formula diventa:

`percentuale finale = (ppm / 20 − percentuale grigia totale) × rapporto / 100`.

Con **1000 ppm**, **1% grigia**, **60% gialla e 40% rosa** fra le colorate:

| Polvere | Percentuale di tutto lo strato |
| --- | ---: |
| Gialla | 29,4% |
| Rosa | 19,6% |
| Grigia | 1% |
| Bianche da inserire manualmente | 50% |

Il grigio viene sottratto una sola volta, sommando tutte le grigie presenti.
I ppm sono il parametro empirico concordato; restano distinti dal bilancio
chimico degli ossidi e dalla classe Y.

## Validazione e precisione

Il croma deve essere fra **0 e 2000 ppm** e non può essere inferiore a
`20 × percentuale grigia totale`. Con 1% grigia il minimo è quindi 20 ppm.
Un valore impossibile o rapporti rosa/gialle diversi da 100% mostrano un errore;
il programma non corregge o normalizza gli input automaticamente.

Le percentuali fisiche conservano quattro decimali. Il croma accetta multipli
di **0,002 ppm**, fino a tre decimali, per mantenere quella precisione anche
quando si passa da una ricetta manuale al calcolo ppm. Nella distribuzione
rosa/gialle gli eventuali residui di arrotondamento vengono assegnati ai maggiori
resti, così la somma delle percentuali calcolate rimane esatta.

Una bozza può avere le bianche ancora da completare; l’approvazione richiede
che ciascuno strato raggiunga esattamente il 100%, con gli altri controlli di
stato e validità già previsti per le composizioni.

## Versioni e modalità manuale

Il salvataggio conserva ppm, rapporti, revisioni e percentuali finali insieme
alla versione della composizione. Cambiare strato o copiare uno strato conserva
anche gli input in corso di compilazione. Gli errori di compilazione non
vengono salvati nel database.

Una polvere configurata che riceve lo 0% rimane disponibile fra i parametri,
ma non compare fra gli ingredienti fisici e non entra in pesate o consumi.
Disattivando il calcolo ppm si mantengono le percentuali finali positive e
si torna all’editing manuale. Le versioni approvate restano in sola lettura;
una nuova versione copia anche i parametri del croma.
