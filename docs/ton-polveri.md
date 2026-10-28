# Polveri: schede tecniche e ossidi

Aprire **TON → Dati tecnici → Polveri**, oppure **Apri polveri** dalla Panoramica.
La funzione utilizza il database condiviso e le tabelle introdotte dalla V4;
la V5 aggiunge il colore della polvere. Le migrazioni sono gestite dall'HOST con
il normale backup preventivo; non vengono inseriti dati dimostrativi.

## Elenco e scheda

L'elenco cerca codice o nome, con pagine di 50 polveri. **Mostra anche archiviate**
include le anagrafiche disattivate. L'ordine predefinito è **bianche → gialle →
rosa → grigie**, poi per codice all'interno dello stesso colore. Il colore è
visibile anche nell'elenco. Le colonne distinguono l'ultima versione
approvata dall'eventuale bozza; la classe Y mostrata appartiene alla scheda
approvata, se presente, altrimenti alla versione più recente.

Con **Nuova polvere** o **Apri scheda** si entra nell'editor a quattro schede:

- **Anagrafica**: codice obbligatorio e univoco (50 caratteri), nome obbligatorio
  (100 caratteri), colore obbligatorio e stato attivo. Questi campi sono
  comuni a tutte le versioni; il codice viene normalizzato in maiuscolo.
  Il colore si sceglie tra **Bianca, Gialla, Rosa e Grigia**: non occorre assegnare
  un numero per ordinare i risultati. Le polveri create prima della V5 sono mostrate
  come **Da specificare**, dopo i quattro colori, e richiedono la scelta al
  successivo salvataggio. Non si deduce il colore dal vecchio ordine numerico.
- **Dati tecnici**: classe Y, resistenza in MPa, eventuale traslucenza e note.
  Tutti sono facoltativi. La classe Y è un intero positivo: i suggerimenti
  comprendono 3Y, 4Y, 5Y e 6Y, ma si possono digitare altre classi. Non viene
  calcolata dalla percentuale in peso di Y₂O₃. Se si dichiara la traslucenza,
  prima dell'approvazione occorre riportarne metodo e scala nelle note.
- **Ossidi**: una formula e una percentuale in peso per riga, con totale visibile.
- **Versioni**: storico con stato e date; si può aprire una versione precedente.

## Percentuali e incolla da Excel

I valori sono esatti in **wt%**. Sono accettati sia virgola sia punto decimale,
senza separatori delle migliaia: `5,150` e `5.150` rappresentano lo stesso valore.
La visualizzazione conserva almeno tre decimali e l'eventuale quarto decimale;
oltre quattro decimali significativi il dato viene rifiutato, senza arrotondarlo.
Ogni percentuale deve essere maggiore di zero e non superiore a 100. Un dato
sconosciuto si omette; non viene trasformato in zero.

Le formule supportano anche gli indici Unicode: `Y₂O₃` e `Y2O3` identificano lo
stesso ossido e non possono essere inseriti due volte nella stessa scheda.
Le formule proposte sono suggerimenti: si possono aggiungere altri ossidi.

**Incolla da Excel** apre un'anteprima da confermare. Copiare le colonne
`ossido / valore`, oppure `ossido / wt% / valore`, separate da tabulazioni o
punto e virgola. Per esempio:

```text
Y2O3	wt%	5,150
ZrO2	wt%	94,850
```

Se il foglio contiene più polveri affiancate, copiare gli ossidi e i valori di
una sola polvere alla volta. Le righe confermate vengono aggiunte alla bozza,
che deve poi essere salvata. Duplicati o righe non valide impediscono l'inserimento
dell'intero blocco.

Il programma non completa automaticamente ZrO₂ e non normalizza il totale a 100.
Le analisi parziali sono ammesse. Una bozza può essere incompleta o superare 100
con un avviso; l'approvazione richiede almeno un ossido e un totale non superiore
a 100 wt%.

## Bozze e storico

**Salva bozza** conserva i dati modificabili. **Salva e approva** salva anagrafica,
caratteristiche e ossidi insieme, quindi rende le caratteristiche di sola lettura.
Si possono ancora modificare nome, codice, colore e stato dell'anagrafica con
**Salva anagrafica**.

**Crea nuova versione** copia una scheda approvata o ritirata in una nuova bozza,
con il numero successivo. È consentita una sola bozza per polvere. La versione
precedente resta invariata e i riferimenti delle ricette continuano a indicare
la versione precisa usata originariamente.

**Ritira versione** conserva caratteristiche, ossidi e riferimenti storici;
distingue una scheda approvata che non si vuole più utilizzare per nuove ricette.
Le schermate delle ricette verranno implementate in seguito. Disattivare invece
**Polvere attiva** archivia l'intera anagrafica, che può essere riattivata senza
perdere lo storico. Non è prevista la cancellazione fisica.

## Concorrenza e asincronicità

Le operazioni DB usano i worker condivisi di EcoS. Ricerca e caricamento ignorano
i risultati superati quando si cambia pagina; la navigazione è bloccata durante
un salvataggio. Le modifiche non salvate richiedono conferma prima di essere scartate.

Ogni scrittura acquisisce la revisione attesa della polvere nella stessa
transazione che modifica scheda e ossidi. Due operatori non possono sovrascriversi
né creare contemporaneamente due versioni con lo stesso numero. In caso di
conflitto il testo locale resta nell'editor: **Ricarica scheda** permette di leggere
i dati correnti dopo aver confermato lo scarto delle proprie modifiche. Un errore
SQL annulla l'intero comando. I salvataggi interrotti non vengono ripetuti automaticamente.

I servizi impediscono di riscrivere caratteristiche approvate; non sono presenti
trigger che impongano questa regola a eventuali scritture SQL esterne all'applicazione.
