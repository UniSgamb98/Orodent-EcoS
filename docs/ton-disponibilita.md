# Disponibilità prodotti

Aprire **TON → Produzione → Disponibilità**, oppure **Disponibilità prodotti** dalla
Panoramica. La pagina usa i codici reali degli articoli TON e comprende anche le
anagrafiche archiviate, riconoscibili dal tooltip; queste non aprono nuove produzioni.

## Consultazione

La tabella rimane completa e si ordina per linea, prodotto, diametro e altezza.
Mostra solo **Codice prodotto**, **Disponibilità**, **Da evadere** e **Venduti**.
Le quantità sono allineate a destra; giacenze nulle o negative sono evidenziate.
L’indice verticale a sinistra è ridimensionabile e scorre indipendentemente.

- Clic sulla linea: raggiunge il suo primo codice.
- Clic destro sulla linea: apre i prodotti/colori; una scelta raggiunge il primo
  codice del prodotto. La tabella non viene filtrata.
- Doppio clic sulla riga o Invio: apre una nuova produzione con prodotto e
  diametro del codice selezionato. Le quantità da produrre restano da inserire.
- **Torna alla disponibilità** ripristina periodo, selezione, posizione e larghezza
  dell’indice. Aprire la produzione senza modificare valori non richiede di
  scartare modifiche inesistenti.

Importazioni e Aggiorna sono nella parte alta. I totali riguardano gli articoli
mostrati; un totale incompleto è indicato come **parziale** e il tooltip precisa
quanti codici hanno dati. Non ci sono ricerche,
contatore in fondo o istruzioni ripetute sotto la tabella. La parte alta mostra
separatamente le date delle due fonti e l’intervallo delle vendite.

## Importare i due CSV

**Importa disponibilità** e **Importa ordini** aggiornano due fotografie distinte.
L’anteprima permette di scegliere separatore (punto e virgola, virgola, tabulazione),
codifica UTF-8 o Windows-1252, colonna del codice, colonna della quantità e data
a cui i dati si riferiscono. Per gli ordini scegliere la **quantità residua da
evadere**, senza includere quanto già consegnato.

Il formato dei numeri è esplicito: virgola decimale italiana con eventuali punti
per le migliaia, oppure punto decimale senza raggruppamento. Non vengono
arrotondati i dati importati; sono ammessi fino a tre decimali. Le disponibilità
possono essere negative; le quantità da evadere devono essere non negative.
I codici sono normalizzati in maiuscolo e hanno il limite del catalogo (80 caratteri).

**Somma le righe con lo stesso codice** permette di aggregare righe distinte,
per esempio più ordini dello stesso articolo; è preselezionato per gli ordini.
Per le giacenze è una scelta esplicita, da usare se le righe rappresentano
quantità distinte da sommare. I duplicati non autorizzati e le righe non valide
bloccano l’intera importazione. Il file ha intestazioni e deve essere entro
16 MiB e 100.000 righe.

**Considera zero i codici assenti dal file** è una scelta esplicita, inizialmente
disattivata. Senza questa scelta, l’assenza di un codice significa **dato non
disponibile**, rappresentato con `—`. Anche una fonte mai importata mostra `—`.
Un file vuoto con intestazioni può svuotare la fonte: verificare l’anteprima.

Con **Importa** si sostituisce la fotografia corrente della sola fonte scelta;
non si aggiunge la quantità al caricamento precedente. Le fotografie precedenti
rimangono conservate nel DB. Importazioni concorrenti della stessa fonte
richiedono una nuova anteprima e non si sovrascrivono silenziosamente.
I codici non associati restano conservati, sono segnalati nell’anteprima e nella
pagina, e non concorrono ai totali TON. Un codice precedente di un articolo
viene associato tramite le revisioni, mantenendo visibile il codice corrente.

## Vendite

Si leggono i dati condivisi di `VENDITE.VENDITE_DETTAGLIO`, senza un terzo CSV.
Il periodo può essere di 30, 90, 180 o 365 giorni e termina all’ultima data di
vendita disponibile non futura, indicata nella pagina. Si sommano le quantità
positive di tipo `VENDITA`; resi e note di credito sono esclusi, come nel ritmo
di vendita di Statistiche. Anche i codici precedenti degli articoli vengono
ricondotti all’identità corrente. Non vengono ricostruiti formati analizzando
il testo del codice.

La pagina mostra le due quantità operative come riportate nei rispettivi CSV:
non sottrae ordini dalla disponibilità. Vendite, bozze e produzioni preparate
non modificano automaticamente le giacenze importate.

## Database e verifiche

La V23 aggiunge `TON.DISPONIBILITA_IMPORT`, `TON.DISPONIBILITA_VALORI` e
`TON.DISPONIBILITA_FONTI`. Le due fonti iniziali sono metadati vuoti, senza
quantità dimostrative. Backup e migrazione sono gestiti dall’HOST.
Le letture usano una transazione coerente; applicazione e cambio della fonte
corrente avvengono in un’unica transazione con controllo di versione.

I test coprono aggiornamenti indipendenti, sostituzione delle fotografie,
concorrenza, duplicati, decimali, codici precedenti, codici non associati,
intervalli delle vendite e codici articolo lunghi. La prova JavaFX verifica
l’indice su oltre 80 articoli, il menu prodotti, l’apertura della produzione
con diametro diverso da 98, il ritorno alla selezione e l’importazione guidata.
