# Etichette dei dischi

In **TON → Documenti → Modelli documenti**, **Nuova etichetta** crea un modello separato dal foglio operativo di pressatura. La pagina iniziale è 100 × 65 mm con margini di 3 mm. Nel pannello **Pagina** puoi scegliere A4 oppure larghezza, altezza e margini in millimetri.

Il formato, il contenuto e le immagini appartengono alla versione del modello. Una versione approvata è in sola lettura: per cambiare etichetta o supporto crea una nuova versione. I modelli precedenti restano disponibili.

Il pannello di inserimento offre i campi del lavoro, i simboli REF, MD, LOT, fabbricazione, non riutilizzare, istruzioni, UDI, fabbricante e CE. **Immagine dal PC** importa PNG, JPEG o WebP fino a 2 MB per immagine; la copia viene incorporata nel modello. La larghezza dell’immagine può essere espressa in millimetri, per esempio `8mm`, o in percentuale.

## Collegamento al prodotto e GTIN

Nelle **Specifiche di produzione** del prodotto scegli una versione approvata nel campo **Modello etichetta**. La produzione conserva quel riferimento tramite la specifica esatta usata per i dischi. Approvare una nuova etichetta non cambia automaticamente le produzioni precedenti.

Il **GTIN · GS1** si imposta nell’anagrafica del singolo **articolo**: i diversi formati commerciali di uno stesso prodotto hanno identificatori distinti. Sono accettati GTIN di 8, 12, 13 o 14 cifre, controllando la cifra finale e conservando la rappresentazione GS1 di 14 cifre. Anche le modifiche al GTIN entrano nello storico dell’articolo. Il campo può restare vuoto per articoli non ancora codificati; una stampa che richiede il GTIN segnala il dato mancante.

## GS1 DataMatrix

Inserisci **GS1 DataMatrix** e selezionalo per modificarne i campi:

| Campo | Valore dal lavoro | Formato di un valore fisso |
| --- | --- | --- |
| GTIN · AI 01 | `{{gtin}}` | 14 cifre con controllo valido |
| Lotto · AI 10 | `{{lot}}` | Da 1 a 20 caratteri |
| Produzione · AI 11 | `{{productionDateGs1}}` | AAMMGG |
| Scadenza · AI 17 | `{{expiryDateGs1}}` | AAMMGG |

Le date nel codice sono facoltative: lascia il campo vuoto per omettere l’AI. FNC1 iniziale e separatori tra gli AI sono inseriti dall’encoder GS1. Una stringa numerica concatenata non viene suddivisa automaticamente: il lotto ha lunghezza variabile e renderebbe ambigua la lettura.

## Stampa da Misurazioni

Apri un lavoro e premi **Stampa etichetta**. Le misure possono essere ancora vuote. L’anteprima usa i codici, formato, lotto, forno, temperatura e data di produzione del lavoro. La scadenza viene inserita esplicitamente se serve al modello; EcoS non inventa una durata commerciale.

Per le produzioni storiche senza modello nella specifica, l’operatore deve scegliere esplicitamente una versione approvata. Per quelle configurate si usa la versione della specifica, anche se successivamente ritirata.

Prima di aprire il dialogo di stampa EcoS conserva l’HTML generato, il modello esatto, i dati, le dimensioni, l’operatore quando disponibile e i collegamenti ai singoli dischi. Annullare il dialogo non elimina questa etichetta preparata. Alla riapertura, **Ristampa ultima etichetta** usa il contenuto originale; **Crea da modello** permette una nuova generazione. La conferma di invio indica l’accettazione da parte del sistema di stampa, non una verifica dell’uscita fisica.

Il formato deve essere disponibile nel driver della stampante selezionata. Se manca, EcoS indica di configurarlo, senza sostituirlo automaticamente con A4. Anche il foglio operativo utilizza le dimensioni e i margini della propria versione di modello.
