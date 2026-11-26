# Editor locale e rendering dei documenti

L’editor condiviso tra TON e Misurazioni è in `orodent-documenti`. Tiptap gestisce il contenuto dei riquadri; gli oggetti della pagina hanno un modello di coordinate distinto. Le risorse sono incluse nell’applicazione e funzionano senza CDN o servizi di editing online.

## Risorse

Il sorgente frontend è in `orodent-documenti/src/main/frontend/document-editor/`. Le risorse distribuite sono in `orodent-documenti/src/main/resources/ton/document-editor/`.

Con Node.js 20 o successivo:

```sh
cd orodent-documenti/src/main/frontend/document-editor
npm ci
npm test
npm run build
npm run check
```

Il build produce HTML con CSS e JavaScript incorporati, hash CSP, manifest delle risorse e licenze delle dipendenze effettivamente distribuite. I test GS1 usano un decoder indipendente. Le immagini dei simboli sono accompagnate dall’attribuzione in `symbols/` e vengono incorporate come PNG nei modelli.

## Proprietà e strumenti dell’editor

`editor-ui.js` coordina inserimento, navigazione e comandi. L’ispettore è composto
da moduli separati:

- `object-properties-profile.js`: nomi e capacità dei tipi di oggetto.
- `object-properties.js`: selezione e composizione dei pannelli.
- `geometry-properties.js`: posizione, dimensioni e azioni comuni.
- `text-properties.js`: carattere e stile condivisi; strumenti di paragrafo solo
  quando il profilo li consente.
- `specific-properties.js`: registro dei pannelli specifici per immagini, GS1,
  tabelle, blocchi operativi e dati automatici.
- `automatic-field-properties.js`: punto di estensione per pannelli di singoli
  dati automatici, identificati dalla chiave (`article`, `lot`, ecc.). Per
  aggiungere proprietà a un dato si registra il relativo `render`/`bind` senza
  duplicare geometria e tipografia. Nuovi attributi persistenti devono sempre
  essere validati nello schema del documento.

`alignment.js` contiene soltanto geometria di allineamento, verificabile senza
DOM; `alignment-guides.js` prepara i riferimenti una volta per trascinamento e
mostra le guide temporanee. La soglia è in pixel di schermo. I controlli di
selezione usano `--chrome-scale`, inverso dello zoom: non alterano le dimensioni
salvate o la stampa. Le maniglie sono ancorate al centro dei bordi geometrici;
la trasformazione inversa comprende anche il loro tratto. Il contorno di
selezione è un overlay CSS con tratto di un pixel sullo schermo, centrato sugli
stessi bordi. Guide e stato dello snap non entrano nel modello salvato.

`symbol-layout.js` gestisce proporzioni e limiti visibili dei simboli della
libreria. Il catalogo conserva dimensioni ritagliate, originali incorporati e
coordinate del ritaglio: permette di riconoscere esattamente i simboli già
salvati e adattare i riquadri nelle sole bozze aperte in modifica, mantenendo
posizione e scala visibili. Immagini importate dal PC e DataMatrix non vengono
ritagliati. Il comando di sviluppo `npm run prepare:symbols` riproduce il
catalogo usando ImageMagick; non è richiesto per eseguire l'applicazione o i test.

`selection-properties.js` contiene azioni e coordinate delle selezioni
multiple e dei gruppi persistenti. `canvas-groups.js` gestisce appartenenza,
validazione e riferimenti di allineamento. I gruppi usano un `groupId` condiviso
fra oggetti nella stessa pagina: non introducono contenitori annidati e non
cambiano coordinate o ordine dei livelli. Le selezioni si espandono a tutti i
membri, anche con Ctrl + clic, undo e ripristino. Lo snap prepara un solo
rettangolo esterno per gruppo, con bordi e centro comuni, senza riferimenti
ai singoli membri.

`matrix-layout.js` aggiorna soltanto le bozze di vecchi oggetti DataMatrix.
L'attributo `gs1Matrix.tight` distingue il codice senza padding dal rendering
precedente. La quiet zone di un modulo viene disegnata come ombra bianca
esterna, proporzionale alla larghezza del codice, senza influire sui limiti
geometrici o causare overflow nella verifica di stampa. I dati GS1 codificati
non cambiano. Il rendering passivo non applica queste conversioni.

`date-field-properties.js` fornisce il pannello condiviso di produzione e
scadenza, registrato in `automatic-field-properties.js`. Il formato è un
attributo del singolo `labelValue` (e dei campi inline `labelField` compatibili).
`date-format.js` contiene parser e formatter deterministici: token `dd`, `mm`,
`yyyy`, `yy`, cifre fisse e separatori, senza conversioni di fuso orario.
`labelValueText` è usato sia dal rendering che da **Adatta al dato**; la
conversione dei vecchi campi inline preserva il formato. Il formato vuoto usa
il precedente `dd/mm/yyyy`. Il validatore Java controlla tipo, lunghezza e
grammatica prima del salvataggio. Date originali, payload GS1 ed etichette già
emesse non vengono riscritti.

## Formato interno e compatibilità

Il formato interno alla versione 8 contiene titolo, utilizzo, impostazioni della pagina e contenuto. `layout: canvas` indica oggetti `canvasObject` con coordinate X/Y, larghezza, altezza, pagina, ordine di sovrapposizione e stile. Ogni oggetto contiene nodi Tiptap per testo, immagini, tabelle, campi, DataMatrix o blocchi automatici. I dati automatici dell’etichetta usano `kind: field` con un unico nodo atomico `labelValue`: non sono contenitori di testo editabile. I campi inline `labelField` rimangono leggibili per i modelli precedenti; nelle bozze i riquadri con un solo campo vengono convertiti senza modificare i testi misti. Le coordinate sono pixel di riferimento a 96 dpi; le dimensioni fisiche della carta sono millimetri. La versione 7 aggiunge appartenenza ai gruppi e geometria aderente dei DataMatrix. La versione 8 aggiunge `dateFormat` ai campi data. Le versioni da 1 a 7 rimangono leggibili; i gruppi devono contenere almeno due membri della stessa pagina. Non è necessaria una migrazione dello schema del database.

La selezione multipla (`canvas-selection.js`) è una selezione ProseMirror con intervalli separati per gli oggetti e bookmark basati sugli ID. La selezione non entra nel modello salvato. Spostamenti e cancellazioni di gruppo producono una sola transazione di modifica; gli oggetti non selezionati fra due intervalli restano intatti. La geometria del rettangolo e lo spostamento del gruppo usano pixel di riferimento, indipendenti dallo zoom.

Il validatore Java controlla coordinate, pagine, identificatori, stili e limiti fisici prima di salvare. Il validatore frontend controlla anche lo schema Tiptap. Le versioni precedenti rimangono leggibili nel loro formato a flusso: il rendering passivo non converte il modello. Solo una bozza aperta in modifica viene convertita in oggetti, conservando le sezioni a colonne come gruppi.

`DocumentEditorPane` espone un’API JavaScript limitata, invocata da Java; non inserisce oggetti Java nella pagina. La navigazione esterna viene bloccata. La serializzazione è riservata al salvataggio e al rendering interni; la UI non offre importazione o esportazione di file JSON.

## Stampa

I nuovi documenti vengono emessi come pagine fisiche con oggetti posizionati. L’HTML generato conserva coordinate, immagini, contenuto e CSS della specifica emissione. `CanvasPrintLayout` controlla i limiti e compensa i margini fisici non stampabili senza cambiare la scala del documento. Il formato deve essere esposto dal driver della stampante.

I fogli precedenti continuano a usare il rendering a flusso. I documenti già emessi e le etichette storiche vengono ristampati dal contenuto conservato, senza ricostruirlo da un modello aggiornato.
