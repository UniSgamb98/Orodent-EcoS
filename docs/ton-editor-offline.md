# Tiptap locale per l'editor di EcoS

Tiptap **3.32.1** è scaricato e distribuito come risorsa locale del modulo TON.
La risorsa include testo formattato, cronologia, tabelle ridimensionabili,
stili del testo, allineamenti e immagini locali. Le estensioni Tiptap sono
fissate alla stessa versione, con dipendenze e licenze comprese nel bundle.
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

L’editor è integrato in **TON → Documenti → Modelli documento** tramite un
`WebView` creato soltanto quando si apre una scheda e rilasciato tornando all’elenco.
La [guida ai comandi](ton-editor-visuale.md) descrive bozze, approvazione e versioni.
I modelli sono salvati nel DB condiviso; i blocchi usano ancora dati di esempio.
Il collegamento alle produzioni reali, il motore PDF e l’archivio delle emissioni
restano da integrare.

`javafx-web` e `javafx-media` sono ora dipendenze runtime. Tutti i moduli JavaFX
sono fissati a **21.0.6**, compresi base e graphics, evitando versioni transitive
non allineate. Il pacchetto Java incorporato include anche `jdk.jsobject`.
Il primo ZIP completo installa anche l’**updater di protocollo 2** sulle postazioni
con il vecchio launcher. Da quel momento le release possono aggiornare sia i JAR
sia il Java applicativo: un futuro nuovo modulo Java non richiede di ridistribuire
manualmente lo ZIP. Vedere [runtime e release firmate](releases.md).
Non cambia la versione dello schema DB e non si devono spostare i dati.

Il ponte JavaScript `window.OrodentDocumentEditor` espone operazioni delimitate:
serializzazione/ripristino JSON con versione dello schema, comandi ammessi,
inserimento dei blocchi, stato delle modifiche, sola lettura e rilascio.
Lo schema editoriale è ora **4**; i modelli degli schemi 1, 2 e 3 sono ancora
caricabili. Lo schema 4 conserva anche le opzioni dei blocchi automatici per
contenuto, aspetto e stampa, oltre a larghezze, spazio fra colonne e opzioni ppm/g/mm. La riapertura dei vecchi modelli li aggiorna in memoria
senza riscriverne le versioni approvate. Aprendo un modello la cronologia viene azzerata: annulla non deve
recuperare contenuti di un documento precedentemente aperto.
Il ponte non espone accesso al database o richieste HTTP.
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
npm test
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
- Caratteri, dimensioni, colori, allineamenti, elenchi e interlinea.
- Righe/colonne, unione/divisione celle, stile delle tabelle e immagini locali.
- Trascinamento dalla palette e riordino nativo dei blocchi Tiptap.
- Proprietà dei blocchi TON, formato/margini, esportazione del modello e anteprima.
- Filtri che conservano i totali, larghezze delle tabelle, opzioni di stampa e
  dimensioni 3/5/7 pt; persistenza dello schema 4 senza riscrivere le versioni approvate.
- Sezioni annidate, proporzioni delle colonne, trascinamento dei divisori, annulla/Esc,
  aggiunta/rimozione senza perdita dei blocchi e ripristino dello schema precedente.
- Zoom 25–200% e Ctrl + rotellina, con dimensioni A4 e JSON invariati.
- Puntatore reale sui divisori e Ctrl + rotellina reali dentro JavaFX WebView.
- Ripristino JSON, rifiuto di versioni non supportate, sola lettura e rilascio.
- Caricamento diretto dal JAR nel WebView JavaFX previsto dal progetto.
- Presenza e hash delle risorse nel JAR TON contenuto nello ZIP di distribuzione.

La verifica automatizzata WebView in cloud è su Linux e percorre la navigazione
reale TON, il salvataggio DB, l’approvazione, la copia in nuova bozza, lo storico,
il blocco delle modifiche non salvate e il rilascio del WebView.
Resta da eseguire la prova manuale del pacchetto EXE su Windows.
