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

## Formato interno e compatibilità

Il formato interno alla versione 6 contiene titolo, utilizzo, impostazioni della pagina e contenuto. `layout: canvas` indica oggetti `canvasObject` con coordinate X/Y, larghezza, altezza, pagina, ordine di sovrapposizione e stile. Ogni oggetto contiene nodi Tiptap per testo, immagini, tabelle, campi, DataMatrix o blocchi automatici. I dati automatici dell’etichetta usano `kind: field` con un unico nodo atomico `labelValue`: non sono contenitori di testo editabile. I campi inline `labelField` rimangono leggibili per i modelli precedenti; nelle bozze i riquadri con un solo campo vengono convertiti senza modificare i testi misti. Le coordinate sono pixel di riferimento a 96 dpi; le dimensioni fisiche della carta sono millimetri.

La selezione multipla (`canvas-selection.js`) è una selezione ProseMirror con intervalli separati per gli oggetti e bookmark basati sugli ID. La selezione non entra nel modello salvato. Spostamenti e cancellazioni di gruppo producono una sola transazione di modifica; gli oggetti non selezionati fra due intervalli restano intatti. La geometria del rettangolo e lo spostamento del gruppo usano pixel di riferimento, indipendenti dallo zoom.

Il validatore Java controlla coordinate, pagine, identificatori, stili e limiti fisici prima di salvare. Il validatore frontend controlla anche lo schema Tiptap. Le versioni precedenti rimangono leggibili nel loro formato a flusso: il rendering passivo non converte il modello. Solo una bozza aperta in modifica viene convertita in oggetti, conservando le sezioni a colonne come gruppi.

`DocumentEditorPane` espone un’API JavaScript limitata, invocata da Java; non inserisce oggetti Java nella pagina. La navigazione esterna viene bloccata. La serializzazione è riservata al salvataggio e al rendering interni; la UI non offre importazione o esportazione di file JSON.

## Stampa

I nuovi documenti vengono emessi come pagine fisiche con oggetti posizionati. L’HTML generato conserva coordinate, immagini, contenuto e CSS della specifica emissione. `CanvasPrintLayout` controlla i limiti e compensa i margini fisici non stampabili senza cambiare la scala del documento. Il formato deve essere esposto dal driver della stampante.

I fogli precedenti continuano a usare il rendering a flusso. I documenti già emessi e le etichette storiche vengono ristampati dal contenuto conservato, senza ricostruirlo da un modello aggiornato.
