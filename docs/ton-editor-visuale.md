# Editor visuale dei modelli documento

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
  colonne. Il contenuto editoriale scorre continuamente; la paginazione PDF
  definitiva è un passaggio ancora da implementare.
- **Blocchi TON:** produzione, parametri pressa, pesature per formato, miscele,
  totali e firme. Inserirli con un clic o trascinarli dalla palette. Selezionare
  il bordo per modificare titolo, carattere, dimensione del testo e spaziatura;
  trascinare la maniglia per riordinarli. Il gesto è gestito con eventi puntatore
  nell’editor, evitando la conversione dei blocchi in etichette testuali da parte
  del drag and drop nativo di WebView. Esc annulla il trascinamento.
  Le etichette già inserite come testo con la vecchia versione non si convertono
  automaticamente: eliminarle e reinserire i blocchi dalla palette.
  I dati operativi sono in sola lettura. Nei blocchi pesature, g/mm e ppm sono
  facoltativi e hanno due spunte separate. I ppm occupano una colonna dedicata;
  g/mm compare nell’intestazione del formato, senza aumentare l’altezza delle righe
  delle pesate. Pesi, conteggi, spessori e pressione restano visibili.
- **Campi nel testo:** inserire numero produzione, data, linea, prodotto e numero
  dei dischi come campi riconoscibili, senza scrivere segnaposto o formule.
- **Annulla/ripeti:** cronologia delle modifiche al contenuto, compresi blocchi
  e tabelle. Nome del file, formato pagina e margini sono impostazioni del modello
  conservate separatamente dalla cronologia del contenuto.

## Sezioni a colonne e annidamento

**Inserisci → Sezione a colonne** crea una sezione di impaginazione. Fare clic
sulla barretta **Sezione** sopra il suo bordo per aprire le proprietà a destra,
oppure scegliere una colonna dai pulsanti **Col. 1**, **Col. 2**, ecc.
Il percorso nel pannello permette di risalire dalla selezione alla colonna o a una
sezione esterna: l’annidamento rimane supportato.

- Da **1 a 4 colonne** per sezione, con proporzioni uguali o personalizzate.
- Preset 60/40, 40/60, 70/30, 30/70 per due colonne; campi percentuali per rifinire
  ciascuna larghezza. La somma resta 100%, con minimo 8% per colonne affiancate.
- Divisori trascinabili con anteprima dal vivo; le frecce spostano il divisore di
  1 punto percentuale, Shift + freccia di 5. Esc annulla il gesto. Un trascinamento
  concluso è una sola operazione nella cronologia di annulla/ripeti.
- Spazio fra colonne regolabile da **0 a 12 mm**. Le percentuali si riferiscono
  allo spazio della sezione; in una sezione annidata dipendono dalla colonna madre.
- **＋ Riga sotto** aggiunge una nuova sezione con le stesse proporzioni e spaziatura,
  modificabile indipendentemente dalla precedente.
- Rimuovendo una colonna, i suoi blocchi passano alla vicina; riducendo il numero
  di colonne, i contenuti delle ultime vengono accodati nell’ultima rimasta.
  Il cambiamento del numero delle colonne ridistribuisce le larghezze in parti uguali.
- **Sciogli sezione** dispone i blocchi delle colonne in sequenza senza eliminarli.

Per inserire una sezione annidata, selezionare la colonna contenitore e scegliere
**Sezione a colonne**, oppure inserirla dal cursore al suo interno. I blocchi
rimangono alla larghezza della propria colonna. Un bordo e un avviso segnalano
le tabelle troppo larghe, senza tagliare i numeri o ridurre automaticamente i font.
Barrette, divisori e avvisi non vengono inseriti nell’anteprima stampabile.

## Zoom della pagina

Sono disponibili livelli dal **25% al 200%**, con più valori intermedi.
**Ctrl + rotellina** aumenta o diminuisce lo zoom di 5 punti percentuali per passo,
con ancoraggio al cursore quando lo spazio di scorrimento lo consente.
Lo zoom funziona anche in sola lettura e non modifica il modello, i millimetri
stampati o lo stato delle modifiche. I controlli e il pannello restano leggibili
alla dimensione dell’interfaccia: viene ingrandita soltanto la pagina.

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
| `editor-ui.js` | Toolbar, pannelli, dialoghi, import/export e rilascio dei listener |
| `editor-drag.js` | Trascinamento della palette, riordino dei blocchi, indicatore di inserimento e scorrimento |
| `extensions.js` | Nodi per blocchi TON, campi, colonne, immagini locali e proprietà delle tabelle |
| `layout-model.js` | Geometria in percentuale, limiti e aggiornamento dei modelli precedenti |
| `column-view.js`, `column-controls.js` | Divisori, selezione delle sezioni, proprietà e operazioni che conservano i contenuti |
| `editor-zoom.js`, `paper-layout.js` | Zoom e coordinate coerenti fra browser e JavaFX WebView |
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
`TON.DOCUMENTI` non viene ancora scritta. Non è necessaria una migrazione DB. Il contenuto editoriale usa ora lo schema
**3**; gli schemi 1 e 2 sono letti e aggiornati in memoria. Le vecchie percentuali
50/50 e la spunta unica g/mm+ppm sono conservate come impostazioni equivalenti.
La riapertura non riscrive il CLOB di una versione già approvata; una nuova bozza
si salva con lo schema corrente.

Sorgenti, lockfile, compilazione riproducibile, licenze e controlli offline sono
descritti nella [guida Tiptap locale](ton-editor-offline.md).
