# Cubaggio

Aprire **TON → Cubaggio → Regole di cubatura**, nel gruppo **Lavoro**.
La pagina usa l'elenco comune TON: ricerca, **Aggiorna**, filtro degli archiviati,
tabella che occupa lo spazio disponibile e **Nuova regola** in alto a destra.
Il doppio clic su una riga apre l'editor, composto da tre schede.

## Dati generali

Ogni regola ha un **codice univoco**, un **nome**, una **descrizione** e lo stato
attiva/archiviata. Il codice rimane bloccato quando la regola è utilizzata oppure
ha una versione approvata o importata. Modifiche concorrenti e codici duplicati
sono segnalati. Uscire da un editor modificato richiede conferma; durante il
salvataggio la navigazione è bloccata.

## Formule

Gli ingressi disponibili sono **D** (diametro in mm), **Hmedia** (altezza media in
mm) e **P** (peso in g). Sono valori positivi. **PI** o **pi** rappresentano π.
Ogni riga assegna un nome al risultato di un'espressione e le righe successive
possono riutilizzarlo. Sono accettate entrambe le forme:

```text
Hmedia + D = a1
risultato = a1 * P
```

Un esempio per volume in mm³ e densità in g/mm³:

```text
PI * (D / 2)^2 * Hmedia = volume
P / volume = densita
```

Sono disponibili `+`, `-`, `*`, `/`, `^`, parentesi e le funzioni `sqrt()`,
`cbrt()`, `abs()`. I decimali possono usare la virgola oppure il punto, senza
separatori delle migliaia. `#` introduce un commento fino alla fine della riga;
le righe vuote sono ignorate. I nomi distinguono maiuscole e minuscole, iniziano
con una lettera e possono contenere lettere ASCII, cifre e `_` (massimo 32
caratteri). Non si possono ridefinire ingressi, costanti o risultati precedenti,
né usare risultati prima della loro definizione. Non viene eseguito codice di
scripting. Limiti: 16000 caratteri e 200 righe per programma, 400 termini e 48
livelli di annidamento per espressione.

**Valori di prova** permette di inserire D, Hmedia e P e premere **Prova formule**.
La tabella mostra tutti i risultati nell'ordine delle formule. Gli errori
indicano la riga interessata; divisioni per zero e risultati non reali o fuori
intervallo sono segnalati. Cambiare formule o ingressi azzera la precedente
anteprima. I valori di prova non sono misurazioni e non vengono salvati.

## Versioni

**Salva bozza** conserva anche formule incomplete. Ogni salvataggio aggiunge una
revisione `rN` immutabile della stessa versione `vN`; rimane modificabile solo
l'ultima revisione della bozza corrente. **Salva e approva** verifica la sintassi
del programma e aggiunge la revisione approvata, in sola lettura. La prova con
valori realistici serve a verificare anche gli errori che dipendono dagli ingressi.

**Crea nuova versione** copia una versione salvata in una nuova bozza `vN+1`;
non possono esistere due bozze correnti. La scheda **Versioni** conserva formule,
nome, descrizione e stato di ogni salvataggio. Doppio clic o Invio su una riga
apre quella precisa revisione, comprese le precedenti bozze, in sola lettura.

## Specifiche di produzione e dati preesistenti

Nelle **Specifiche di produzione** del prodotto, **Riferimento metodo di calcolo**
è un selettore delle versioni approvate. Salva l'identificativo della versione
precisa: una nuova approvazione della regola non aggiorna automaticamente le
specifiche già salvate. Le specifiche approvate rimangono di sola lettura. Le
regole archiviate restano visibili per i riferimenti esistenti e non possono
essere assegnate nei nuovi salvataggi.

La migrazione **V26** aveva creato `TON.REGOLE_CUBATURA` partendo dai riferimenti
testuali già presenti nelle specifiche. La migrazione **V27** aggiunge gli
snapshot in `TON.REGOLA_CUBATURA_VERSIONI` e il riferimento
`TON.CONFIGURAZIONI_PRODOTTO.calcolo_versione_id`. Collega ogni specifica
preesistente a uno snapshot della sua regola, conservando codice e dati originali.
Le regole già utilizzate sono importate nello stato **Importata senza formule**:
non vengono inventate formule. Per usarle in nuovi salvataggi occorre creare e
approvare una nuova versione con il programma. Le regole non utilizzate vengono
importate come bozze modificabili. Le produzioni già preparate conservano i
propri riferimenti originali.

L'acquisizione tramite **Invia misurazioni**, il salvataggio dei risultati reali,
i modelli di etichetta, la stampa e l'integrazione Clarinet restano passaggi
successivi.
