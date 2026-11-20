# Preparazione delle produzioni TON

Aprire **TON → Produzione → Produzioni e bozze → Nuova produzione**.
La pagina descrive i dischi da realizzare e calcola quanto pesare: non registra
pezzi già prodotti e non movimenta magazzino, scarti, lotti o cotture.

## Preparare il lavoro

1. Scegliere data, linea, prodotto/colore e diametro nominale.
2. Inserire i pezzi accanto ai formati disponibili. Zero esclude il formato.
3. Controllare **Pesate e miscele**: dosi per disco/strato, spessore da pressare e
   quantità complessive di ogni polvere nelle miscele da preparare.
4. Scegliere facoltativamente un modello documento approvato e scrivere le note.
   **Documento** mostra il modello dell'utente compilato con i dati correnti.
5. **Salva bozza** permette di riprendere il lavoro; **Prepara produzione**, dopo
   il riepilogo di conferma, fissa quantità, specifiche e calcoli di lavorazione.

Una produzione comprende un solo prodotto/colore e più formati dello stesso
diametro. I formati devono utilizzare la stessa versione di composizione e
modello disco, ereditati dalla specifica approvata predefinita del prodotto.
Non vengono selezionate specifiche separate per altezza. In assenza di una
predefinita, o se il prodotto importato è da verificare, occorre configurarlo
nei Dati tecnici. Il diametro dell’articolo sceglie stampo di riferimento e g/mm
nel modello; un gruppo mancante impedisce di usare quel formato. Gli articoli
non disponibili mostrano il motivo e i collegamenti a prodotto e articolo.

La configurazione a sinistra e i contenuti delle schede scorrono separatamente.
La barra delle azioni resta raggiungibile senza far scorrere l'intera pagina.
L'elenco usa ricerca, filtro per stato e doppio clic/Invio per riaprire il piano.

## Calcoli

I calcoli usano `BigDecimal`, con quote e grammi esatti fino agli arrotondamenti
operativi. Per ogni strato si parte dall'altezza nominale del formato moltiplicata
per la percentuale del modello. Si aggiungono i sovramateriali superiore al primo
strato e inferiore all'ultimo; nello strato unico si aggiungono entrambi.
Le fasce specifiche di altezza del modello sostituiscono i sovramateriali base
nell'intervallo `[da, a)`. Il coefficiente g/mm del modello converte in grammi.

Le dosi per disco non comprendono maggiorazioni. Il totale analitico dello strato,
sommato per tutti i pezzi, viene moltiplicato **una sola volta per 1,005** e
arrotondato a **0,1 g**. Le quote delle polveri vengono distribuite in decimi di
grammo, assegnando i residui alle frazioni maggiori: la somma coincide esattamente
con il totale operativo della miscela. Le dosi mostrate per disco sono anch'esse
visualizzate a 0,1 g; i totali derivano dai valori esatti, non dai valori mostrati.

La pressione visualizzata è quella del modello, in **kg/cm² sul materiale**.
Non viene presentata come pressione macchina in bar. La conversione dipenderà
dalla futura sezione Macchinari; non viene inventato un valore P300.

## Bozze, versioni e documenti

Le bozze sono salvate nel DB condiviso, con controllo di versione: una seconda
postazione non può sovrascrivere una modifica concorrente. Prima di salvare o
preparare, i riferimenti tecnici selezionati vengono riletti e verificati nella
stessa transazione. Composizione, modello, specifiche e polveri devono essere
approvati/disponibili. Tornando su una bozza si ricaricano le specifiche che aveva
selezionato per il prodotto; un riferimento ritirato viene segnalato e impedisce
la preparazione. Le bozze anteriori alla V21 ricaricano invece la specifica
condivisa corrente, con un avviso di verifica prima del nuovo salvataggio.

La produzione preparata è di sola lettura. Si riapre dalla fotografia conservata,
senza cambiare dosi se successivamente vengono aggiornati i cataloghi. **Duplica**
crea un nuovo lavoro modificabile e rilegge le specifiche disponibili correnti.

Il modello documento viene creato e approvato dall'utente in **Documenti**: layout,
colonne, testi, immagini e proprietà dei blocchi sono mantenuti. I blocchi e i
campi dinamici ricevono dati reali della produzione, compreso il campo **Note di
lavorazione**. In progettazione il modello continua a usare dati dimostrativi.

La preparazione conserva una sola emissione **HTML autosufficiente** nel DB.
Le ristampe usano quel contenuto. Se la generazione fallisce dopo il salvataggio,
il piano rimane preparato e il documento può essere rigenerato dalla sua fotografia
riaprendo la scheda Documento. La stampa usa il dialogo nativo della postazione;
non è ancora implementata l'archiviazione automatica di un PDF.

V15 aggiunge `TON.PIANI_PRODUZIONE`, `TON.PIANO_PRODUZIONE_RIGHE` e
`TON.PIANI_DOCUMENTI`. Le tabelle delle produzioni fisiche preesistenti rimangono
distinte dai piani richiesti. Dalla V21 i nuovi piani (fotografia JSON versione 2)
registrano le righe in `TON.PIANO_PRODOTTO_RIGHE`, con articolo e sua revisione,
prodotto e specifica condivisa. I piani già preparati mantengono JSON, righe e
documenti originali. Nessun dato dimostrativo viene inserito nei DB reali.

## Verifiche di sviluppo

I test del servizio coprono calcoli e arrotondamenti, stato immutabile, riferimenti
ritirati, conflitti fra bozze, righe non disponibili ed emissione idempotente.
La prova JavaFX percorre selezione → quantità → pesate → modello utente → bozza
→ riapertura → preparazione → duplicazione, verificando anche la finestra ridotta.
Il modello della prova contiene titolo e note personalizzati: non si limita al
layout del mockup. I test delle migrazioni controllano anche l'inizializzazione
ripetuta e la conservazione dei dati precedenti.
