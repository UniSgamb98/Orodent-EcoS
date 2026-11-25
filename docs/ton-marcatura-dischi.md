# Marcatura dischi

Aprire **TON → Documenti → Marcatura dischi**. L'elenco comune TON permette di
cercare codice o nome, mostrare anche gli archiviati e creare un **Nuovo modello**.
Doppio clic o Invio apre il modello. L'editor ha tre schede:

- **Dati generali**: codice stabile, nome, descrizione e stato attivo.
- **Marcatura**: scritta, campi, decimali e anteprima con dati di esempio.
- **Versioni**: tutte le revisioni salvate, consultabili con doppio clic o Invio.

## Campi e formati

La scritta combina testo fisso e campi tra parentesi graffe, ad esempio:

```text
{codice_articolo} {descrizione_articolo} Ø{diametro_nominale}x{altezza_nominale}
LOT {lotto} Disco {numero_batch} R {fattoreRitiro}
```

I campi disponibili sono:

- `codice_articolo`, `descrizione_articolo`, `codice_prodotto` e `lotto`.
- `numero_batch`: numero batch assegnato al singolo disco in misurazione;
  è testo, conserva eventuali zeri iniziali e non è l'ID interno del disco.
- `diametro_nominale` e `altezza_nominale`: dimensioni nominali in mm
  specificate nell'articolo, distinte dalle letture degli strumenti.
- `D` (diametro misurato in mm), `Hmedia` (altezza media misurata in mm)
  e `P` (peso misurato in g).

La regola di cubatura è facoltativa: scegliendo una **versione approvata**,
diventano disponibili anche tutti i suoi risultati con i nomi delle formule.
**Inserisci campo** inserisce il campo selezionato alla posizione del cursore.
I nomi distinguono maiuscole e minuscole. Le parentesi graffe sono riservate ai
campi; i nomi sconosciuti o incompleti vengono segnalati.

Ogni campo numerico ha un numero di decimali da **0 a 8**, inizialmente 3.
Il separatore decimale può essere punto o virgola. L'arrotondamento usa la
regola aritmetica HALF_UP e riguarda solo la scritta; non modifica i risultati
dei calcoli. Gli zeri finali sono mantenuti secondo il formato scelto.

L'anteprima calcola i risultati della regola usando D, Hmedia e P di esempio.
Puoi modificare anche descrizione, numero batch, dimensioni nominali, codice
articolo, codice prodotto e lotto di esempio. Le formule continuano a usare
le sole letture D, Hmedia e P; le dimensioni nominali non le sostituiscono.
Cambiare testo, regola, decimali o valori aggiorna l'anteprima. In presenza di
errori la scritta precedente viene cancellata. I dati di esempio non vengono
salvati e non sono misurazioni reali.

## Versioni e conservazione dei dati

**Salva bozza** permette di conservare una scritta incompleta. Ogni salvataggio
aggiunge una revisione `rN` della stessa versione `vN`, mantenendo tutte le
precedenti. **Salva e approva** controlla testo, campi e formati e rende la
versione di sola lettura. **Crea nuova versione** copia il contenuto in una
nuova bozza `vN+1`; può esserci una sola bozza corrente per codice.

Ogni revisione conserva nome, descrizione, stato attivo, testo, decimali,
separatore e identificativo della precisa versione della regola di cubatura.
Nuove approvazioni delle regole non modificano i modelli salvati. Le vecchie
bozze e le versioni approvate restano consultabili in sola lettura, con
anteprima utilizzabile. Non sono previsti comandi di cancellazione delle revisioni.
Le modifiche concorrenti sono respinte senza sovrascrivere i dati.

La migrazione **V28** aggiunge `TON.MODELLI_MARCATURA` e
`TON.MARCATURA_VERSIONI`; non cambia i modelli documento o le produzioni esistenti.
Questo passaggio prepara i modelli: l'assegnazione nelle Specifiche di produzione,
l'assegnazione del numero batch, la generazione della scritta per il singolo disco
e l'invio a Clarinet verranno
collegati successivamente, conservando versioni e contenuti effettivamente generati.
