# Editor visuale offline: prima versione

Aprire **TON → Documenti → Modelli documento → Nuovo modello**.
L’editor Tiptap è incluso nell’applicazione e non richiede Internet. I pannelli
Inserisci/Proprietà permettono di progettare il foglio operativo con un esempio
completo di laboratorio. I modelli si salvano nel database condiviso di EcoS.

La stessa pagina può ancora essere provata nel browser aprendo
`orodent-ton/src/main/resources/ton/document-editor/index.html`: in questa modalità
separata i modelli si conservano solo tramite esportazione JSON.

## Bozze e versioni nel progetto

1. Scegliere **Nuovo modello**, inserire un codice stabile (es. `PRESSATURA`) e
   modificare il nome direttamente nell’editor. Il codice identifica la famiglia
   delle versioni; dopo il primo salvataggio non cambia.
2. **Salva bozza** conserva nome, layout, testi e immagini nel DB condiviso.
   L’elenco mostra l’ultima versione per codice; la ricerca usa codice e nome.
3. **Salva e approva** verifica la presenza dei blocchi operativi essenziali e
   rende la versione di sola lettura. La verifica non certifica calcoli produttivi.
4. **Nuova versione** copia la versione selezionata in una nuova bozza. Per ogni
   codice può esistere una sola bozza, anche con richieste concorrenti.
5. Il selettore **Versione** riapre lo storico senza modificare i contenuti approvati.
   **Elimina bozza** elimina soltanto la bozza selezionata; le versioni approvate
   rimangono conservate.

Le modifiche non salvate richiedono conferma prima di cambiare pagina, versione o
chiudere la finestra. Durante un salvataggio non si può lasciare la scheda.
I conflitti fra postazioni vengono rifiutati e il contenuto locale resta
nell’editor; **Esporta JSON** permette di conservarlo prima di ricaricare.
Le scritture interrotte non vengono ripetute automaticamente.

Questa prima integrazione riguarda i **modelli di produzione**. I documenti
emessi e i modelli di cottura verranno collegati ai rispettivi flussi produttivi.

## Comandi disponibili

- **Testo:** paragrafi, titoli, caratteri, dimensioni, grassetto, corsivo,
  sottolineato, colore, evidenziatore, allineamenti, elenchi e interlinea.
  Selezionare il testo prima di applicare la formattazione.
- **Tabella libera:** scegliere righe, colonne e intestazione nel dialogo.
  Con il cursore nella tabella, il pannello Proprietà permette di aggiungere o
  eliminare righe/colonne, unire/dividere celle, alternare le righe, cambiare
  spaziatura ed eliminare la tabella. I bordi delle colonne sono ridimensionabili.
- **Immagini:** scegliere un file PNG, JPEG o WebP dal PC, fino a 2 MB. Le immagini
  sono incorporate nel modello; le dimensioni si modificano dal pannello Proprietà.
  Non vengono caricate immagini remote.
- **Impaginazione:** formato A4 verticale/orizzontale, margini, zoom e sezioni a
  due colonne. Il contenuto editoriale scorre continuamente; la paginazione PDF
  definitiva è un passaggio ancora da implementare.
- **Blocchi TON:** produzione, parametri pressa, pesature per formato, miscele,
  totali e firme. Inserirli con un clic o trascinarli dalla palette. Selezionare
  il bordo per modificare titolo, carattere, dimensione del testo e spaziatura;
  trascinare la maniglia per riordinarli.
  I dati operativi sono in sola lettura. Nei blocchi pesature, g/mm e ppm sono
  facoltativi. Pesi, conteggi, spessori e pressione restano visibili.
- **Campi nel testo:** inserire numero produzione, data, linea, prodotto e numero
  dei dischi come campi riconoscibili, senza scrivere segnaposto o formule.
- **Annulla/ripeti:** cronologia delle modifiche al contenuto, compresi blocchi
  e tabelle. Nome del file, formato pagina e margini sono impostazioni del modello
  conservate separatamente dalla cronologia del contenuto.

## File e anteprima

Nell’applicazione **Esporta JSON** e **Importa JSON** usano i dialoghi nativi di
Windows. L’import sostituisce il contenuto della bozza corrente, dopo la conferma
per eventuali modifiche locali, e deve essere salvato nel DB. Un import non valido
mantiene il contenuto corrente. I modelli hanno un limite di 8 MB; le immagini
singole di 2 MB. Nel browser separato gli stessi comandi sono Apri/Esporta modello.

**Verifica** segnala eventuali blocchi mancanti per una scheda operativa TON.
**Anteprima** mostra il contenuto compilato con esempi. Nell’app non è ancora
abilitata la stampa definitiva: prima devono essere collegati calcoli, produzione
e conservazione del PDF emesso. La pagina aperta separatamente nel browser offre
la stampa del browser, che non costituisce un’emissione archiviata.

Il campione riprende 64 dischi, quattro strati, P300 a 83,3 bar, maggiorazione
× 1,005 e pesatura a 0,1 g. I valori sono precompilati in `sample-data.json`,
con le convenzioni descritte nella [scheda di laboratorio](ton-documenti-laboratorio.md).
L'editor non ricostruisce i calcoli fisici: li forniranno i servizi TON.

## Struttura del codice

In `orodent-ton/src/main/frontend/document-editor/`:

| File | Responsabilità |
| --- | --- |
| `editor.js` | Creazione Tiptap, sessione, stato delle modifiche e ponte per l’host JavaFX |
| `editor-ui.js` | Toolbar, pannelli, dialoghi, trascinamento, import/export e rilascio dei listener |
| `extensions.js` | Nodi per blocchi TON, campi, colonne, immagini locali e proprietà delle tabelle |
| `document-model.js` | Schema JSON, validazione, migrazione del contenuto testuale v1 e impostazioni della pagina |
| `laboratory-blocks.js` | Presentazione dei blocchi e controllo della struttura operativa |
| `sample-data.json` | Dati dimostrativi, separati dal layout e dai calcoli produttivi |
| `editor.css`, `index.html` | Aspetto e struttura dell'interfaccia |

Il JSON conserva le scelte del modello e i tipi di blocco, non una copia dei dati
produttivi di esempio. I blocchi automatici sono nodi atomici: il loro contenuto
non può essere alterato digitando nella tabella. Un'immagine è invece incorporata
nel modello per essere disponibile offline. L’host JavaFX gestisce repository e revisioni in
`features/documents/`, separando modello, SQL, servizio asincrono, controller e viste.
Le tabelle già presenti `TON.MODELLI_DOCUMENTO` conservano il JSON e la versione;
`TON.DOCUMENTI` non viene ancora scritta. Non è necessaria una migrazione DB.

Sorgenti, lockfile, compilazione riproducibile, licenze e controlli offline sono
descritti nella [guida Tiptap locale](ton-editor-offline.md).
