# Tiptap locale per l'editor di EcoS

Tiptap **3.32.1** è scaricato e distribuito come risorsa locale del modulo TON.
La base include testo formattato, cronologia e tabelle ridimensionabili:
`@tiptap/core`, `@tiptap/starter-kit` e `@tiptap/extension-table`.
Non usa CDN, font remoti, Tiptap Cloud o servizi online.

## Distribuzione

I file pronti sono in
`orodent-ton/src/main/resources/ton/document-editor/`:

- `index.html`: pagina autosufficiente con Tiptap, dipendenze e stile incorporati,
  senza import remoti. I caratteri sono quelli di sistema.
- `THIRD_PARTY_NOTICES.txt`: testi delle licenze dei pacchetti effettivamente inclusi.
- `bundle-manifest.json`: versioni, dimensioni e SHA-256 delle risorse distribuite.

Maven include questi file nel JAR di TON. Il JAR entra nella distribuzione e negli
aggiornamenti dei componenti già esistenti. La normale compilazione dello ZIP
non richiede npm: i file compilati sono versionati insieme ai sorgenti.
Gli operatori non devono installare Node.js o scaricare dipendenze JavaScript.

## Stato dell'integrazione

È pronta la **base offline**, non ancora la schermata Documenti completa di TON.
La risorsa non viene aperta automaticamente e non modifica i cataloghi, il DB,
le migrazioni o i modelli della proposta grafica. Non implementa ancora i blocchi
operativi, il salvataggio dei modelli nel DB, il motore PDF o l'archivio delle emissioni.

Il prossimo passaggio collegherà questa risorsa a una vista JavaFX `WebView`,
aggiungendo `javafx-web` e i suoi binari alla distribuzione. Il controllo della
risorsa è stato svolto anche con JavaFX WebView 21.0.6, caricandola dal JAR.
Il modulo web non è ancora una dipendenza runtime dell'applicazione corrente.

Il ponte JavaScript `window.OrodentDocumentEditor` espone operazioni delimitate:
serializzazione/ripristino JSON con versione dello schema, comandi ammessi,
sola lettura e rilascio. Non espone accesso al database o richieste HTTP.
Lo schema del contenuto editoriale è distinto dalle versioni dei modelli TON.

La pagina usa una policy che blocca le connessioni e autorizza soltanto lo script
incorporato tramite il suo hash SHA-256. Non dipende dalla risoluzione di script
esterni attraverso il protocollo `jar:` di JavaFX e non avvia un server HTTP.
Font, icone o estensioni aggiunti in futuro devono
essere inclusi nelle risorse, mantenendo questa proprietà offline.

## Ricostruzione delle risorse — solo per sviluppo

Sorgenti, versioni fissate e lockfile sono in
`orodent-ton/src/main/frontend/document-editor/`. Con Node.js 20 o successivo:

```sh
cd orodent-ton/src/main/frontend/document-editor
npm ci --ignore-scripts
npm run build
npm run check
```

Il download avviene durante `npm ci`, sul PC di sviluppo. L'applicazione usa
esclusivamente il risultato locale della compilazione. `npm run check` ricostruisce
in memoria e controlla che i file versionati siano identici al risultato atteso.
I fine riga sono fissati a LF per rendere il controllo riproducibile su Windows.

`node_modules` resta escluso da Git; `package-lock.json` conserva risoluzioni e
integrità dei pacchetti. L'aggiornamento di Tiptap è una modifica esplicita:
aggiornare dipendenze, ricompilare, controllare licenze e compatibilità WebView,
quindi distribuire le nuove risorse con la normale release EcoS.

## Verifiche

- Browser in modalità offline, con le sole risorse locali fornite al caricamento.
- Scrittura, grassetto, inserimento tabelle, annulla/ripeti.
- Ripristino JSON, rifiuto di versioni non supportate, sola lettura e rilascio.
- Caricamento diretto dal JAR nel WebView JavaFX previsto dal progetto.
- Presenza e hash delle risorse nel JAR TON contenuto nello ZIP di distribuzione.

La verifica WebView effettuata in cloud è su Linux. La prima integrazione completa
dovrà includere anche la prova nell'EXE Windows e la gestione del suo ciclo di vita.
