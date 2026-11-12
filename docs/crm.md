# CRM in Orodent EcoS

Il modulo `orodent-crm` deriva da **Clients**, ramo `DEPLOYIED`, commit
[`fc733861891c68cf7da2b0a17bae0397da80a026`](https://github.com/UniSgamb98/Clients/commit/fc733861891c68cf7da2b0a17bae0397da80a026).
La prima integrazione trasferisce anagrafiche, ricerca e filtri, preferiti e viste
salvate, scheda completa con contatti/indirizzi, note e chiamate, calendario
mensile/settimanale e cataloghi di forni, fresatori, materiali e canali di acquisto.
Le voci prive di una funzione collegata nel progetto originale sono nascoste.

## Accesso e operatori

Aprire **Home → CRM**, scegliere l'operatore e premere **Usa questo operatore**.
Il database nuovo contiene `utente` (Operatore ufficio); **Nuovo operatore**
permette di aggiungere un nome. È una selezione dell'autore delle attività,
non un'autenticazione con password. **Cambia operatore** permette una nuova scelta.

`OperatorSession` appartiene all'AppController di EcoS: la scelta resta valida
passando dalla Home agli altri moduli e riaprendo il CRM. Viene verificato che
l'operatore sia ancora attivo. Non è necessario selezionarlo per usare Statistiche,
TON o Costi. Preferiti e viste salvate rimangono separati per UUID dell'operatore.

## Scheda cliente

L'intestazione resta visibile durante lo scorrimento. Contatti e recapiti sono
nella colonna laterale; la cronologia occupa l'area principale. Su finestre più
strette le due sezioni si dispongono in verticale. I recapiti hanno un comando
Copia; dati fiscali/commerciali e attrezzature sono richiudibili.

I riepiloghi tecnici importati da CRM2 sono separati dalle conversazioni.
Nelle chiamate riconoscibili del vecchio formato, il testo originale completo
resta nei dettagli espandibili; nessuna nota viene modificata nel DB. I testi
lunghi hanno Mostra tutto / Mostra meno. Il richiamo dell'ultima chiamata viene
evidenziato se scaduto; i filtri dello storico non modificano l'intestazione.

## Database e distinzione dal gestionale

La migrazione immutabile **V13** crea lo schema `CRM` e `ECOS.OPERATORI`. Le altre
anagrafiche e i dati di vendita restano nei rispettivi schemi. Un'anagrafica CRM
ha un UUID proprio e può esistere senza essere cliente del gestionale.
`CRM.CLIENTI.CODICE_GESTIONALE` è facoltativo e predisposto per un futuro
collegamento esplicito: questa integrazione non associa automaticamente le vendite
né espone ancora tale collegamento nella UI.

I forni/fresatori CRM descrivono il parco macchine dei laboratori contattati;
non sono i forni di produzione TON. I cataloghi restano quindi distinti.

La V13 si applica con il normale percorso EcoS: backup prima dell'evoluzione,
modifiche dello schema sull'HOST e verifica di compatibilità sui CLIENT. La
cartella e il DB della vecchia applicazione Clients non vengono aperti né modificati.
Il CRM nuovo parte vuoto. Per i backup **CRM2** (`clients.txt` e note XML) è
disponibile il [kit di migrazione](../scripts/crm-migration/LEGGIMI.md): il primo
BAT prepara i dati e il report; il secondo verifica la destinazione sull'HOST,
crea un backup ed esegue l'importazione in un'unica transazione. Richiede un CRM
ancora vuoto e conserva gli operatori già presenti, riusando i nomi coincidenti.
Questo percorso importa CRM2, non un database Clients aggiornato. Non basta
copiare i file Derby del vecchio DB sopra quelli di EcoS.

## Operazioni, concorrenza e ciclo di vita

Il modulo riceve ConnectionProvider ed Executor da EcoS. Ogni operazione completa
usa una nuova connessione in una transazione con schema CRM; repository e servizi
annidati lavorano sulla stessa connessione per quel solo comando. Non vengono
avviati server Derby, discovery, thread pool o tentativi di subentro propri.

L'AsyncLoader del modulo serializza i lavori sui worker condivisi ed esegue i
callback UI sul thread JavaFX. Alla chiusura scadono i callback e viene fermato il
debounce della ricerca; Executor e database restano di proprietà dell'applicazione.
La navigazione attende le operazioni in corso e protegge le modifiche ai moduli.
Un errore conserva i campi inseriti. Una disconnessione con esito incerto blocca
ulteriori comandi della pagina: ricaricare e verificare prima di ripetere il salvataggio.
Nessuna scrittura viene ripetuta automaticamente.

`CLIENTI.VERSIONE_MODIFICA` protegge la scheda e i suoi dettagli da aggiornamenti
concorrenti. La revisione letta viene confrontata al salvataggio e incrementata
una volta per transazione; i valori nella sessione avanzano soltanto dopo il commit.
Le note e le chiamate partecipano alla medesima protezione, così un salvataggio
completo non può cancellare attività inserite nel frattempo da un'altra postazione.

I cataloghi delle impostazioni hanno una revisione per catalogo. Il salvataggio
aggiorna le identità esistenti e inserisce/elimina soltanto le righe cambiate.
Una voce ancora usata da una scheda non può essere eliminata: il DB rifiuta
l'operazione e tutta la modifica viene annullata. Non si cancellano/reinseriscono
più indiscriminatamente i cataloghi perdendo le associazioni ai laboratori.

Le revisioni qui sono controlli di concorrenza, non uno storico completo delle
anagrafiche come le specifiche tecniche TON. Le interazioni commerciali conservano
le loro date e il rispettivo operatore.

## Verifica

Il generatore CRM2 ha test Python su formato, identità, storico e anomalie.
`LegacyCrmImportTest` esegue il pacchetto sintetico generato da Python su Derby,
verificando backup, relazioni, rollback, operatori e rifiuto di file alterati.

`CrmIntegrationTest` copre creazione del lead e separazione dei dati, chiamata e
calendario, conflitti fra sessioni, rollback e revisione, integrità dei collegamenti
ai cataloghi e separazione delle viste per operatore. `CrmSchemaTest` verifica
l'evoluzione da V12 con backup singolo e il controllo in sola lettura.

`CrmUiTest` (profilo `ui-smoke`) percorre selezione operatore, creazione, chiamata,
protezione delle modifiche, calendario, impostazioni e riapertura dalla Home;
controlla che non ci siano accessi JDBC sul thread grafico. Gli screenshot sono
in `orodent-crm/target/crm-*.png`.
