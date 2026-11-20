# Macchinari e attrezzature

Aprire **TON → Preparazione → Macchinari e attrezzature**, accanto a **Dati tecnici**.
Le tre pagine **Presse**, **Stampi** e **Forni** riutilizzano `TonListView`, lo stesso
componente degli elenchi dei cataloghi tecnici: sfondo azzurro, pannello bianco,
ricerca, **Aggiorna**, filtro delle archiviate e tabella con colonne distribuite
su tutta la larghezza. Non ci sono frecce di pagina, contatore o pulsante di
apertura in fondo: doppio clic o Invio aprono l’anagrafica selezionata.

## Anagrafiche essenziali

| Catalogo | Dati |
| --- | --- |
| Presse | Codice, nome, alesaggio del pistone idraulico in mm, note, stato attivo |
| Stampi | Codice, nome, diametro di pressatura dello stampo in mm, note, stato attivo |
| Forni | Codice, nome, note, stato attivo |

**Nuova pressa**, **Nuovo stampo** e **Nuovo forno** aprono il modulo di creazione.
Codice e nome sono obbligatori; alesaggio e diametro devono essere positivi.
Le misure accettano punto o virgola e fino a tre decimali. Il codice viene
normalizzato in maiuscolo ed è univoco nel proprio catalogo. Nome: massimo
100 caratteri; codice: massimo 50; note: massimo 1000.

**Salva anagrafica** conserva i dati nel DB. Per archiviare, togliere
**Anagrafica attiva** e salvare. **Mostra anche archiviati** include questi record
nell’elenco; riattivarli richiede di riaprire l’anagrafica e ripristinare la spunta.
Non è prevista la cancellazione fisica delle attrezzature.

**Aggiorna dati** ricarica l’anagrafica aperta. Uscita o ricarica con modifiche
non salvate chiedono conferma. Le scritture bloccano temporaneamente la navigazione;
le letture vengono eseguite in background. Due postazioni non possono sovrascriversi
silenziosamente: un conflitto conserva i campi locali e richiede una ricarica.
Un salvataggio identico non incrementa la revisione.

## Forni e cotture

**Forni** è stato spostato nell’area delle attrezzature; la precedente area
**Forni e cotture** si chiama **Cotture** e mantiene pianificazione, carichi,
esiti e programmi. Il catalogo è sempre `TON.FORNI`: non esistono copie separate.
Gli identificativi dei forni e i collegamenti ai cicli già registrati restano
conservati, anche quando si rinomina o archivia un forno.

## Confini di questo primo passo

Si registrano le identità fisiche e le prime misure. Non vengono ancora definite
compatibilità pressa/stampo, manutenzioni, formule di conversione della pressione,
densità del verde o associazioni fra attrezzature e lotti. L’alesaggio non cambia
la pressione target dei modelli disco. Il passaggio delle specifiche dall’articolo
al prodotto rimane in pausa.

Prima di collegare le attrezzature alle lavorazioni andrà registrata la fotografia
della configurazione effettiva utilizzata: cambiare un’anagrafica in futuro non
dovrà riscrivere le condizioni di una produzione già eseguita.

## Database

La V18 aggiunge `TON.PRESSE` e `TON.STAMPI`, con misure decimali, codici univoci,
stato, revisione per i conflitti e timestamp. `TON.FORNI` rimane invariata.
L’HOST effettua il normale backup prima della migrazione; i CLIENT verificano
lo schema aggiornato in sola lettura. Nessun dato dimostrativo viene inserito
nell’archivio dell’utente.
