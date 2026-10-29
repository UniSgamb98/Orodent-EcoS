# Editor visuale offline: prima versione

La pagina inclusa nelle risorse di TON è ora un editor Tiptap utilizzabile, con
pannelli Inserisci/Proprietà e un documento di laboratorio dimostrativo.
Non è ancora collegata al menu Documenti dell'applicazione o al DB produttivo.
Per provarla dopo il Pull, aprire nel browser
`orodent-ton/src/main/resources/ton/document-editor/index.html`: è un file
autosufficiente e non richiede server o connessione Internet.

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

## Modelli locali e anteprima

**Esporta modello** scarica un file JSON contenente testo, struttura, immagini e
impostazioni della pagina. **Apri modello** lo ricarica; gli import non validi
mantengono il contenuto corrente. Le modifiche sono in memoria fino all'esportazione;
non vengono salvate nel database di EcoS. La pagina non è un archivio di versioni.

**Verifica** segnala eventuali blocchi mancanti per una scheda operativa TON.
Il comando non approva un modello e non certifica calcoli o dati produttivi.
**Anteprima** mostra il contenuto compilato con gli esempi e offre la stampa
del browser. Questo non produce ancora il PDF archiviato collegato alla produzione.

Il campione riprende 64 dischi, quattro strati, P300 a 83,3 bar, maggiorazione
× 1,005 e pesatura a 0,1 g. I valori sono precompilati in `sample-data.json`,
con le convenzioni descritte nella [scheda di laboratorio](ton-documenti-laboratorio.md).
L'editor non ricostruisce i calcoli fisici: li forniranno i servizi TON.

## Struttura del codice

In `orodent-ton/src/main/frontend/document-editor/`:

| File | Responsabilità |
| --- | --- |
| `editor.js` | Creazione Tiptap, sessione, stato delle modifiche e ponte per il futuro host JavaFX |
| `editor-ui.js` | Toolbar, pannelli, dialoghi, trascinamento, import/export e rilascio dei listener |
| `extensions.js` | Nodi per blocchi TON, campi, colonne, immagini locali e proprietà delle tabelle |
| `document-model.js` | Schema JSON, validazione, migrazione del contenuto testuale v1 e impostazioni della pagina |
| `laboratory-blocks.js` | Presentazione dei blocchi e controllo della struttura operativa |
| `sample-data.json` | Dati dimostrativi, separati dal layout e dai calcoli produttivi |
| `editor.css`, `index.html` | Aspetto e struttura dell'interfaccia |

Il JSON conserva le scelte del modello e i tipi di blocco, non una copia dei dati
produttivi di esempio. I blocchi automatici sono nodi atomici: il loro contenuto
non può essere alterato digitando nella tabella. Un'immagine è invece incorporata
nel modello per essere disponibile offline. L'host JavaFX gestirà in seguito
repository, revisioni e salvataggio delle emissioni.

Sorgenti, lockfile, compilazione riproducibile, licenze e controlli offline sono
descritti nella [guida Tiptap locale](ton-editor-offline.md).
