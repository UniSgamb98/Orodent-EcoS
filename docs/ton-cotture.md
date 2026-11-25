# Dischi prodotti e carichi forno

## Conferma della produzione

In **Produzioni e bozze**, **Conferma produzione** salva quantità, specifiche,
pesate e tutti i singoli dischi nella stessa transazione. La produzione diventa
**Confermata**, in sola lettura, e i pezzi compaiono subito in Cotture.
**Salva bozza** permette invece di continuare a modificare il lavoro e non genera
pezzi. Il passaggio separato «Registra dischi prodotti» è stato rimosso.

Ogni disco conserva un'identità interna distinta per la tracciabilità. Errori nel
salvataggio dei pezzi annullano anche la conferma del piano: non possono rimanere
produzioni confermate solo parzialmente. La conferma è idempotente e conserva le
versioni tecniche originali. Le giacenze importate dai CSV restano indipendenti.

La migrazione **V30** adegua anche i piani precedentemente **Preparati** con
fotografia JSON supportata: crea soltanto i pezzi mancanti, conserva le identità
esistenti e i dischi già caricati, usando i codici e i formati del piano salvato.
I vecchi segnaposto importati privi di un piano leggibile restano invariati.
L'istante di registrazione automatica è conservato in `dischi_confermati_il`.

## Cotture → Pianificazione e carichi

La scheda **Da caricare** mostra una riga per coppia **produzione + articolo**,
con formato, quantità disponibile e **Da caricare**. Produzioni diverse dello
stesso articolo rimangono separate. Ogni riga rappresenta tutti i pezzi ancora
in attesa di quel formato, pur mantenendo i record individuali nel database.

Inserire le quantità da assegnare al forno; zero esclude la riga. **Seleziona
visibili** riempie le quantità con tutti i pezzi disponibili delle righe mostrate;
**Deseleziona** le azzera. La ricerca considera articolo, prodotto, linea e numero
della produzione; cambiarla azzera le quantità selezionate.

Scegliere un forno attivo, inserire il **lotto manuale**, scegliere un **programma
approvato** e indicare la **temperatura finale in °C**, data e note, poi premere
**Registra carico**. Le quantità indicate ricevono lo stesso forno e lotto.
Si possono assegnare, per esempio, 20 pezzi su 30 a un forno e caricare i 10
rimanenti in un altro carico. EcoS assegna i singoli record internamente, in
ordine stabile di identificativo, evitando sovrapposizioni tra carichi.

Il lotto è normalizzato in maiuscolo e deve identificare un solo carico.
Sono ammessi da 1 a 10000 pezzi per carico. Quantità superiori alla disponibilità,
anche a causa di un altro operatore, vengono respinte senza salvataggi parziali.
La data del carico non può precedere quella della produzione dei pezzi assegnati.

Dopo il salvataggio le disponibilità diminuiscono e le righe esaurite scompaiono.
La scheda **Carichi registrati** conserva forno, lotto, data e numero di pezzi.
Mostra anche il programma con versione e revisione, la temperatura finale e
l'eventuale cambio di programma rispetto al carico precedente di quel forno.
Anche il dettaglio del carico mostra produzione, articolo, formato e quantità
raggruppati. Le assegnazioni rimangono conservate per la successiva tracciabilità.

## Durata e avvisi sulle date

Il carico mostra la durata prevista del programma, sommando tutti i tempi di
rampa e mantenimento, e la data di fine stimata. Per i carichi già registrati
viene usata la versione del programma effettivamente assegnata al carico.

Scegliendo forno, programma e data, EcoS confronta il periodo previsto con i
carichi già registrati sullo stesso forno. In caso di sovrapposizione compare un
avviso giallo vicino a **Registra carico**, con carico, lotto e fine prevista.
Il pulsante rimane utilizzabile: non sono richieste conferme aggiuntive e la
sovrapposizione non impedisce il salvataggio. L'avviso si aggiorna anche digitando
la data, cambiando forno o programma e aggiornando l'elenco dei carichi.

Poiché viene indicata soltanto la data, la stima è per giorni: un ciclo di 10
giorni caricato il 25 novembre rende il forno disponibile dal 5 dicembre.
Le frazioni di giornata sono arrotondate al giorno successivo: un ciclo di
24 ore e 1 minuto occupa due giornate. Il giorno di nuova disponibilità non è
incluso nel periodo occupato. Viene rilevato anche un nuovo ciclo che inizi
prima di un altro carico ma si prolunghi nel suo periodo previsto.

È una stima di pianificazione basata sui carichi registrati, distinta dalle future
rilevazioni di avvio e fine effettivi del forno. I vecchi carichi senza programma
o senza step non hanno una durata ricostruibile e non generano una previsione.

## Dati per la tracciabilità

Il percorso conservato è:

```text
codice articolo + lotto
  → singolo disco → carico → forno e sua revisione al caricamento
  → temperatura finale del carico + versione esatta del programma termico
  → registrazione di produzione → piano confermato
  → specifiche precise → composizione, polveri e modello utilizzati
```

Il disco conserva codice articolo, codice prodotto, linea, colore e formato
originali. I riferimenti al piano e alle specifiche rimangono quelli effettivamente
usati, anche se in seguito cambia l'anagrafica o viene scelta una nuova specifica
predefinita. Il carico conserva ID, codice, nome e revisione del forno al momento
della registrazione, oltre a lotto, data, note e istante del salvataggio.

Le transazioni impediscono che lo stesso disco venga caricato da due postazioni:
le identità dei dischi vengono bloccate e ricontrollate prima di creare il carico.
In caso di errore non rimangono cicli, lotti o assegnazioni parziali.

La migrazione **V29** introduce `TON.ESECUZIONI_PIANO`, `TON.DISCHI_PRODOTTI` e
`TON.CARICO_DISCHI`, utilizzando `TON.CICLI_COTTURA`, `TON.LOTTI_COTTURA` e
`TON.FORNI` per i carichi. In questa fase un ciclo con `caricato_il` valorizzato
rappresenta un carico registrato, ancora distinto dall'avvio del programma termico.
I [programmi di cottura](ton-programmi-cottura.md) si configurano nella sezione
dedicata, con step e versioni conservate. L'ultimo programma usato è ricordato
per ciascun forno; la temperatura finale del carico risolve `Tfinale` negli step.
Le sonde, l'avvio e la conclusione della cottura, gli esiti e il numero batch alla misurazione
saranno collegati nei prossimi passaggi. L'ID interno del disco fornisce già il
riferimento stabile a cui aggiungere quei dati.
