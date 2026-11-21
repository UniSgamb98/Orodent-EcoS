# Fornitori

Aprire **TON → Dati tecnici → Fornitori**. La pagina usa l’elenco comune TON:
ricerca per codice o nome, Aggiorna, Nuovo fornitore e Mostra anche archiviati.
Doppio clic o Invio apre l’anagrafica con codice univoco, nome e stato attivo.
Salvataggi concorrenti vengono rilevati senza sovrascrivere quelli di altri operatori.

La polvere sceglie il fornitore da un menu, visualizzato come **codice · nome**.
Non si scrivono nuovi nomi nella scheda polvere: si crea prima l’anagrafica Fornitori.
Il collegamento è per ID; rinominare il fornitore aggiorna gli elenchi e la ricerca.
Un fornitore archiviato resta visibile nelle associazioni già presenti e non può
essere assegnato a un’altra polvere. Può essere riattivato dall’anagrafica.
L’ordine delle polveri resta **nome fornitore → colore → classe Y → codice**.

## Migrazione V22

L’HOST applica la migrazione con il consueto backup preventivo. La migrazione
crea `ANAGRAFICHE.FORNITORI` e la tabella dei nomi precedenti `FORNITORI_ALIAS`.
Recupera i nomi presenti nelle polveri e negli ordini di acquisto; spazi consecutivi,
spazi iniziali/finali e differenze di maiuscole non creano fornitori distinti.
I codici iniziali `AUTO-…` sono provvisori, modificabili dall’utente. Nomi simili
ma diversi non vengono uniti automaticamente. I valori vuoti restano senza fornitore.

Polveri e ordini ricevono il relativo `fornitore_id`. I vecchi testi non vengono
cancellati. Negli ordini il nome sorgente CSV resta parte della chiave di importazione:
rinominare un fornitore non riscrive ordini, revisioni o associazioni dei codici sorgente.
Le successive importazioni riusano la stessa anagrafica, anche tramite il nome precedente.
I nomi precedenti restano riservati a quel fornitore per mantenere questo collegamento.
La scheda corrente delle Linee non usa più Tecnologia/Materia prima, ma i dati
pregressi e le revisioni restano conservati.
