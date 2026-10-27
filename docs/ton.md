# TON: primo catalogo tecnico

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
- `TonController`: navigazione del modulo e rilascio della pagina attiva.

Il provider del modulo non apre il DB e non crea controlli quando viene caricato
nel catalogo EcoS. I servizi condivisi e la finestra principale restano di EcoS.
Gli stili TON sono limitati alla radice del modulo.

## Database e distribuzione

La migrazione immutabile V3 crea `TON.PRODOTTI`, con ID stabile, codice univoco,
stato attivo, revisione per il controllo concorrente e timestamp. È applicata
solo dall'HOST, con backup prima dell'aggiornamento di archivi esistenti. I dati
Statistiche e l'identità del DB vengono conservati; i CLIENT verificano lo schema
senza migrarlo. Chiudere le istanze prima dell'aggiornamento e riavviare l'HOST
aggiornato prima dei CLIENT. Non è necessario cambiare Java o launcher per V3:
la normale release firmata dei componenti dichiara il nuovo target di migrazione.

Il catalogo parte vuoto. Questo passaggio non importa automaticamente il database
TON-V2 e non aggiunge tabelle per funzioni ancora assenti. Polveri, modelli disco,
composizioni, produzione e forni verranno nei passi successivi; cubaggio resta
rinviato.
