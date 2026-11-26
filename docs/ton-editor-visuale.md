# Editor visuale dei modelli

In **TON → Documenti → Modelli di documento**, **Nuovo modello** apre un foglio bianco. L’editor non aggiunge intestazioni aziendali, righe decorative o piè di pagina. I modelli si salvano e si versionano nel database; non sono presenti funzioni di importazione o esportazione JSON.

## Pagina

Nel pannello sinistro, scheda **Pagina**, scegli:

- Utilizzo: foglio operativo di produzione oppure etichetta dischi. Dopo il primo salvataggio il tipo non cambia.
- A4 verticale/orizzontale oppure formato personalizzato con larghezza e altezza in millimetri.
- Margine di riferimento per il posizionamento iniziale dei nuovi oggetti.
- Numero di pagine per i documenti. Un’etichetta occupa una sola pagina.

Un foglio di 102 × 38 mm mantiene queste proporzioni nell’editor e nell’anteprima. Lo zoom ingrandisce la vista e non modifica le misure del documento.

## Oggetti

Inserisci riquadri di testo, immagini, simboli, GS1 DataMatrix, tabelle e blocchi automatici. Ogni oggetto ha coordinate e dimensioni proprie, indipendenti dagli altri elementi.

- Trascina la maniglia sopra l’oggetto per spostarlo. Immagini, dati automatici, codici e blocchi automatici si possono trascinare anche dal contenuto.
- Trascina i bordi o gli angoli per ridimensionarlo.
- Nel pannello destro inserisci **X, Y, larghezza e altezza** in pixel. Il riferimento è l’angolo superiore sinistro della pagina.
- Con l’oggetto selezionato, le frecce spostano di **1 px**; **Maiusc + frecce** spostano di **10 px**.
- Entra nel testo con un clic nel riquadro o con Invio. Esc torna alla selezione dell’oggetto.
- **Annulla/Ripeti** conserva anche spostamenti e ridimensionamenti. Un trascinamento costituisce una singola operazione.
- **Duplica**, **In primo piano**, **Sullo sfondo** ed **Elimina oggetto** si trovano nelle proprietà.

I pixel sono quelli di riferimento CSS, 96 per pollice: 1 px corrisponde a 25,4/96 mm. Il pannello mostra anche l’equivalente fisico del riquadro. Il rapporto con lo zoom e con la risoluzione della stampante non modifica le coordinate salvate.

Gli oggetti rimangono entro la pagina. Se un nuovo formato non contiene gli oggetti esistenti, l’editor segnala il problema e ripristina i valori precedenti; puoi prima spostarli o ridimensionarli.

## Testo e contenuti variabili

Selezionando un riquadro di testo, sulla destra compaiono stile del paragrafo, carattere, dimensione, grassetto, corsivo, sottolineatura, colori, allineamento, elenchi e interlinea. Con una porzione di testo selezionata puoi formattare quella porzione; selezionando l’intero oggetto modifichi il riquadro.

Nel pannello **Dati automatici**, codice articolo, lotto, GTIN e gli altri valori
sono oggetti autonomi: non si può scrivere prima o dopo il dato. Il contenuto parte
dall’angolo superiore sinistro del riquadro, senza margini di paragrafo. Le proprietà
consentono di cambiare carattere, dimensione, colore, grassetto, corsivo e
sottolineatura; **Adatta al dato** calcola larghezza e altezza usando il valore
visualizzato. I valori si aggiornano dal lavoro alla generazione dell’etichetta.

I normali riquadri di testo restano disponibili per le scritte libere. I campi
operativi dei documenti di produzione possono ancora essere inseriti nel testo;
i dati dei blocchi continuano a provenire dal piano, senza alterare pesi o calcoli.

Tra i simboli sono disponibili anche **# · Numero modello**, **Tenere all’asciutto**
(ombrello) e **Data di scadenza** (clessidra), oltre a quelli già presenti.

Un contenuto troppo grande viene segnalato nelle proprietà. Puoi aumentare il riquadro, adattarne l’altezza o ridurre il testo. La stampa dei nuovi modelli controlla che gli oggetti e il loro contenuto rientrino nel formato, per evitare tagli silenziosi. I dati reali possono occupare più spazio rispetto agli esempi: verifica anche l’anteprima del documento generato.

## Versioni precedenti

I modelli approvati e i documenti già emessi conservano il contenuto originale. Quando apri una bozza precedente per modificarla, l’editor racchiude i blocchi esistenti in oggetti posizionabili; le sezioni a colonne vengono mantenute come gruppi. I riquadri contenenti unicamente un campo etichetta vengono convertiti in dati automatici autonomi nelle bozze modificabili, conservandone posizione e stile; i testi misti restano conservati. Controlla la disposizione prima di approvare la nuova versione.

Per etichette, GTIN, immagini dal PC e collegamenti alle specifiche di produzione, vedi [Etichette dei dischi](ton-etichette.md).
