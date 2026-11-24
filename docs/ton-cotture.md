# Dischi prodotti e carichi forno

## Registrazione della produzione effettiva

Una produzione **Preparata** conserva richieste, specifiche e pesate. Quando i
dischi sono stati realizzati, aprire la produzione e usare **Registra dischi prodotti**.
Il dialogo mostra per ciascun articolo quantità prevista, quantità già registrata
e quantità da registrare ora. Indicare data e note della produzione effettiva.

Sono ammesse registrazioni parziali successive. Ogni registrazione deve contenere
da 1 a 10000 dischi e non può superare la quantità ancora prevista per un formato.
Una bozza non può generare dischi. Il salvataggio crea un record per ciascun disco,
con un identificativo interno `D-00000001`, e conserva la registrazione di origine.
Un doppio invio o una modifica concorrente richiedono di ricaricare i dati.

I piani preparati esistenti non vengono convertiti automaticamente in output
fisico. Quantità, documenti e riferimenti originali del piano restano conservati.
I vecchi piani con specifiche articolo mantengono il riferimento storico preciso;
i piani correnti mantengono la versione delle specifiche di prodotto.

## Cotture → Pianificazione e carichi

La scheda **Da caricare** mostra tutti i dischi dichiarati prodotti che non sono
ancora assegnati a un carico. Ogni riga corrisponde a un disco fisico: ID interno,
codice articolo, prodotto, formato, produzione di origine e data di produzione.
Le righe sono raggruppate visivamente per prodotto. La ricerca considera anche
linea e numero della produzione. Cambiare ricerca azzera la selezione.

Selezionare singoli dischi oppure **Seleziona visibili**, scegliere un forno
attivo, inserire il **lotto manuale**, la data e le eventuali note, quindi premere
**Registra carico**. Tutti i dischi selezionati ricevono quel forno e quel lotto.
Per dividerli tra forni diversi registrare carichi separati. Il lotto è normalizzato
in maiuscolo e deve identificare un solo carico; un codice già usato viene respinto.
La data del carico non può precedere la produzione di uno dei dischi selezionati.

Dopo il salvataggio i dischi escono dalla coda. La scheda **Carichi registrati**
mostra forno, data, lotto e numero di dischi. Selezionare un carico per consultarne
i singoli dischi e la provenienza. Le assegnazioni vengono conservate; questa
pagina non prevede cancellazioni o riassegnazioni dei carichi registrati.

## Dati per la tracciabilità

Il percorso conservato è:

```text
codice articolo + lotto
  → singolo disco → carico → forno e sua revisione al caricamento
  → registrazione di produzione → piano preparato
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
Programmi, temperature, sonde, avvio e conclusione della cottura, esiti e numero
batch alla misurazione saranno collegati nei prossimi passaggi. L'ID interno del
disco fornisce già il riferimento stabile a cui aggiungere quei dati.
