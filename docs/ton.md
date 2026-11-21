# TON: navigazione e cataloghi tecnici

TON dispone di **Panoramica**, **Produzione**, **Cotture**, **Dati tecnici**,
**Attrezzature e Fornitori**, **Tracciabilità** e **Documenti**. Il percorso completo è mostrato nell'intestazione
(es. **TON › Dati tecnici › Articoli**), senza una seconda barra sotto o titoli
ripetuti nel corpo delle pagine. L'inserimento multiplo aggiunge l'ultimo livello:
**TON › Dati tecnici › Prodotti tecnici › Inserimento multiplo** (oppure Articoli);
**Home Orodent-EcoS** torna al catalogo principale e **Panoramica** resta in TON.

Dati tecnici mantiene al massimo due colonne, disposte per righe:

| Sinistra | Destra |
| --- | --- |
| Articoli | Composizioni |
| Prodotti tecnici | Modelli disco |
| Linee | Polveri |

Tutti e sei i cataloghi tecnici sono operativi e si aprono anche dalla
Panoramica. In **Documenti → Modelli documento** è disponibile l’editor visuale
con bozze e versioni nel DB. In **Produzione → Disponibilità** si consultano
giacenze, ordini aperti e vendite, con accesso diretto alla preparazione.
In **Produzione → Produzioni e bozze** si preparano
i dischi richiesti e le relative pesate. **Attrezzature e Fornitori**, nel gruppo
Preparazione, contiene le anagrafiche operative di presse, stampi, forni e fornitori.
Le funzioni restanti sono indicate come
**In preparazione**, senza contatori fittizi o comandi non implementati. Il cubaggio resta rinviato.

- [Linee, prodotti, articoli, storico e specifiche produttive](ton-cataloghi.md).
- [Inserimento multiplo di prodotti e articoli](ton-bulk-entry-proposal.md).
- [Polveri, ossidi e schede tecniche approvate](ton-polveri.md).
- [Fornitori delle polveri](ton-fornitori.md).
- [Modelli disco, strati, sovramateriali e versioni](ton-modelli-disco.md).
- [Attrezzature e Fornitori: presse, stampi, forni e fornitori](ton-macchinari.md).
- [Composizioni, ricette per strato, ossidi e utilizzi](ton-composizioni.md).
- [Disponibilità prodotti: giacenze, ordini, vendite e accesso alla produzione](ton-disponibilita.md).
- [Produzioni e bozze: quantità, pesate e documenti reali](ton-produzione.md).
- [Mockup di riferimento della produzione](ton-produzione-proposta.md).
- [Proposta iniziale dell'editor visuale dei documenti](ton-documenti-proposta.md).
- [Base Tiptap offline e ricostruzione delle risorse](ton-editor-offline.md).
- [Editor integrato, bozze, versioni e blocchi dimostrativi](ton-editor-visuale.md).
- [Struttura e collegamenti del dominio TON](ton-schema-proposta.md).
- [Proposta grafica di riferimento](ton-cataloghi-proposta.md).

**Menu TON / Chiudi menu** richiude la barra laterale. Sotto 1020 pixel si
richiude automaticamente e dopo la scelta di un'area lascia spazio al contenuto.
**Esc** la richiude nella modalità compatta; allargando la finestra torna visibile.
Le schede e gli elenchi scorrono quando lo spazio non basta.

## Ciclo di vita

Il provider del modulo non apre il DB e non costruisce controlli prima della
selezione. TonController crea la pagina richiesta e ne rilascia letture,
listener e timer quando la si lascia. Tutti i cataloghi ricevono provider ed
executor di EcoS; non aprono un database o un pool di thread autonomo.

FeatureController espone salvataggio, verifica delle modifiche non salvate e
chiusura della pagina. Durante le scritture vengono disabilitati la Home di
EcoS e i collegamenti interni. I salvataggi non sono cancellati né ripetuti
quando si interrompe la connessione.

TonView mantiene header, menu, percorso e contenuto; TonFeature definisce ordine
e disponibilità delle funzioni; TonPages costruisce le pagine introduttive senza
query DB. Gli stili sono limitati alla radice TON. Le aree riutilizzano la stessa
finestra, senza avviare un processo distinto per catalogo.

## Database e aggiornamenti

V3 crea il catalogo prodotti, V4 aggiunge il dominio produttivo e documentale,
V5 il colore delle polveri e V6 la gerarchia dei cataloghi con storico reale.
V15 aggiunge i piani di preparazione e il foglio operativo conservato.
V17 ricava croma e tinta dalle percentuali fisiche delle composizioni,
rimuovendo le due tabelle dei parametri introdotte nella V16.
V18 aggiunge le anagrafiche di presse e stampi e riusa il catalogo dei forni.
V19 conserva per versione del modello lo stampo di riferimento e gli altri
stampi ammessi manualmente, con misure e scostamenti teorici.
V20 distingue i gruppi per diametro articolo, ciascuno con g/mm e stampi propri.
La V21 sposta le specifiche produttive sul prodotto e conserva i riferimenti
storici degli articoli e dei piani preparati.
Il dominio TON comprende 40 tabelle. Le migrazioni pubblicate restano immutabili.

L'HOST salva il backup prima delle scritture e applica le migrazioni pendenti in
transazione, prima di pubblicare il servizio. Il CLIENT verifica lo schema
corrente in sola lettura; il failover non applica DDL. Clienti, vendite, ID e UUID
restano conservati. Vedere [schema condiviso e migrazioni](database-schema.md).

Le sole migrazioni DB arrivano con la normale release firmata dei componenti e
non richiedono la sostituzione di Java o launcher. Per migrare un archivio esistente chiudere le istanze
e riavviare prima l'HOST aggiornato, poi i CLIENT.

L’editor in Documenti aggiunge JavaFX Web e `jdk.jsobject`. Il primo ZIP completo
installa il launcher con updater di protocollo 2; in seguito componenti e Java
applicativo possono aggiornarsi automaticamente insieme. Non cambia lo schema DB;
dettagli nella [guida alle release](releases.md).
