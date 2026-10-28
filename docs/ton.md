# TON: navigazione e cataloghi tecnici

La finestra TON ha un menu laterale con **Panoramica**, **Produzione**, **Forni e
cotture**, **Dati tecnici**, **Tracciabilità** e **Documenti**. Il percorso in alto
indica la pagina corrente; il pulsante **Home Orodent-EcoS** torna al catalogo
principale, mentre **Panoramica** resta dentro TON.

Aprire il catalogo prodotti dalla Panoramica oppure da **Dati tecnici → Prodotti
tecnici**. Il catalogo **Polveri** si apre dalla Panoramica oppure da **Dati
tecnici → Polveri**: vedere [schede tecniche e ossidi](ton-polveri.md).
Le altre aree presentano le funzioni previste con lo stato **In
preparazione**, senza contatori fittizi o comandi di lavorazione non implementati.
Il cubaggio rimane rinviato e non compare come funzione operativa.

Il pulsante **Menu TON / Chiudi menu** permette di richiudere la barra. Sotto
1020 pixel si richiude automaticamente, e dopo la selezione di un'area lascia
spazio al contenuto. **Esc** la richiude nella modalità compatta. Gli elenchi e
le schede scorrono quando lo spazio non basta. Allargando la finestra il menu
torna visibile.

Lo [schema completo TON](ton-schema-proposta.md) descrive le 25 tabelle introdotte
da V3/V4, i collegamenti e un esempio di produzione e cottura. La struttura SQL
è disponibile; oltre ai cataloghi prodotti e polveri, i flussi applicativi
successivi restano da realizzare.

Il primo blocco riscritto da TON-V2 è il catalogo dei prodotti tecnici, accessibile
con **TON → Apri catalogo prodotti**. Un prodotto rappresenta una famiglia o un
materiale del laboratorio, per esempio `ZR A2`. Non viene associato automaticamente
agli articoli commerciali analizzati da Statistiche.

## Uso

- Creare un prodotto con codice obbligatorio (massimo 50 caratteri), descrizione
  facoltativa (200 caratteri) e stato attivo. Il codice viene normalizzato in
  maiuscolo e non può duplicare un altro codice, nemmeno di un prodotto archiviato.
- Cercare per codice o descrizione. I risultati sono ordinati e paginati in gruppi
  di 50; caratteri come `%` e `_` sono cercati letteralmente.
- Selezionare una riga e scegliere **Modifica selezionato**. L'editor non cambia
  automaticamente quando si seleziona una riga o si aggiorna l'elenco.
- Disattivare **Prodotto attivo** per archiviarlo. **Mostra anche archiviati**
  permette di ritrovarlo e riattivarlo. Non esiste una cancellazione fisica.
- Le modifiche non salvate richiedono conferma prima di cambiare pagina o
  sostituire il contenuto dell'editor. Durante un salvataggio la navigazione
  interna è sospesa; chiudere la sessione non annulla il comando in corso.

Due modifiche alla stessa revisione non possono sovrascriversi: la prima viene
salvata, la seconda riceve un messaggio di conflitto e mantiene il proprio testo.
Aggiornare l'elenco e riaprire la scheda per lavorare sulla revisione corrente.
Un codice duplicato o un errore SQL annulla l'intera transazione. I salvataggi
interrotti dalla disconnessione non vengono ripetuti automaticamente.

## Organizzazione

- `features/products/model`: dati immutabili, normalizzazione e conflitto.
- `features/products/repository`: SQL qualificato su `TON.PRODOTTI`, senza commit
  o connessioni proprie.
- `features/products/service`: connessioni e transazioni scoped, esecuzione sui
  worker ricevuti da EcoS.
- `features/products/controller`: debounce, paginazione, editor e gestione delle
  richieste superate; annulla solo le letture, senza interrompere le scritture.
- `features/products/view`: controlli JavaFX e stati visivi.
- `features/powders`: stessa separazione model/repository/service/controller/view
  per anagrafiche, revisioni e ossidi. Ogni comando aggiorna l'intera scheda in
  transazione, acquisendo prima la revisione della polvere.
- `navigation/FeatureController`: ciclo di vita comune delle pagine operative,
  blocco della navigazione durante le scritture e verifica dei dati non salvati.
- `TonController`: navigazione del modulo e rilascio della pagina attiva.
- `navigation/TonSection` e `TonFeature`: mappa delle aree e stato delle funzioni.
- `view/TonView`: struttura persistente, menu, percorso e comportamento compatto.
- `view/TonPages`: pagine introduttive create alla selezione, senza query DB.

Una nuova funzione si aggiunge alla mappa, alla sua pagina e al dispatcher di
`TonController`; diventa disponibile solo quando esiste il flusso operativo.
Le aree non aprono finestre separate e non costruiscono in anticipo tutti i
controller. Quando si lascia il catalogo, il controller verifica le modifiche
non salvate prima di cambiare pagina o voce evidenziata. Durante il salvataggio
sono disabilitati sia la Home di EcoS sia i collegamenti di navigazione.

Il provider del modulo non apre il DB e non crea controlli quando viene caricato
nel catalogo EcoS. I servizi condivisi e la finestra principale restano di EcoS.
Gli stili TON sono limitati alla radice del modulo.

## Database e distribuzione

La migrazione immutabile V3 crea `TON.PRODOTTI`, con ID stabile, codice univoco,
stato attivo, revisione per il controllo concorrente e timestamp. È applicata
solo dall'HOST, con backup prima dell'aggiornamento di archivi esistenti. I dati
Statistiche e l'identità del DB vengono conservati; i CLIENT verificano lo schema
senza migrarlo. Chiudere le istanze prima dell'aggiornamento e riavviare l'HOST
aggiornato prima dei CLIENT. Non è necessario cambiare Java o launcher per V3/V4:
la normale release firmata dei componenti dichiara il nuovo target di migrazione.

La V4 aggiunge le altre 24 tabelle: linee e articoli; polveri e revisioni;
composizioni e ingredienti; modelli e configurazioni; produzioni e scarti;
forni, programmi, cicli, carichi e lotti; modelli documento e documenti emessi.
Conserva integralmente il catalogo V3, gli ID, la sequenza e le revisioni; non
importa automaticamente TON-V2 e non crea dati dimostrativi. Un database nuovo
parte vuoto. Il cubaggio resta rinviato.

Sono già attivi i vincoli SQL di identità, unicità, riferimenti, percentuali,
quantità e coerenza dei campi. I servizi futuri dovranno completare i controlli
transazionali su disponibilità e flussi produttivi. Per le polveri sono già
implementati somme, approvazione, immutabilità delle schede approvate e
assegnazione concorrente delle versioni. Creare le altre tabelle non introduce
già le relative schermate o il sistema di stampa.

La V5 aggiunge `TON.POLVERI.colore`, usato nell'anagrafica e per ordinare il
catalogo: bianche, gialle, rosa, grigie, poi codice. Le polveri V4 restano senza
colore finché l'operatore lo specifica; il vecchio numero viene conservato solo
come dato legacy. Anche V5 arriva attraverso la normale release dei componenti,
con backup e migrazione eseguiti dall'HOST prima di accettare i CLIENT aggiornati.
