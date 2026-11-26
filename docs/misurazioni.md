# Misurazioni

Modulo autonomo, sempre visibile nella Home di EcoS, che riprende la schermata e
il flusso di [Marzia 1.0.1](https://github.com/UniSgamb98/Marzia/commit/849773dc7e02c086efee27b0a2596b3216f8ec4e).

L'apertura è disabilitata per impostazione predefinita. Solo sulla postazione
che acquisisce dagli strumenti impostare `misurazioni.enabled=true` nel file
`application.properties` (in sviluppo `config/development.properties`). Sugli
altri PC lasciare `false`. Una proprietà assente o diversa da `true` mantiene
il modulo disabilitato: la voce nella Home rimane visibile, ma non è apribile e
non vengono avviate connessioni, nemmeno richiedendo direttamente il modulo.
Dopo una modifica del file, tornare alla Home o riavviare EcoS.

## Lavori e ripresa delle misure

All'apertura si vede **Lavori da completare**, con lavori raggruppati per prodotto,
navigazione ai prodotti sulla sinistra, ricerca per articolo/lotto/forno e
avanzamento delle letture. **Completati** permette di rivedere i lavori per cui
tutti i dischi hanno entrambe le letture salvate. I collegamenti agli strumenti
si controllano dai pulsanti di stato, senza una pagina Strumenti separata.
La colonna **Temperatura · °C** mostra la temperatura finale salvata nel carico,
usata per risolvere `Tfinale` negli step del programma. Un valore storico assente
è indicato con `—`. La fine prevista della cottura continua a determinare quando
il lavoro diventa disponibile.

Un lavoro distingue **carico + produzione + formato**: produzioni con specifiche
diverse rimangono separate anche quando condividono articolo e lotto. Sono proposti
i dischi dei carichi giunti alla fine prevista del ciclo, sommando rampe e
mantenimenti della versione del programma assegnata al carico e arrotondando le
frazioni di giorno come in Cotture. Un termine effettivo registrato ha precedenza.
La disponibilità è una previsione, distinta dalla conferma fisica della cottura.

Doppio clic o Invio aprono la schermata di acquisizione esistente, con prodotto,
articolo, formato, lotto, forno, produzione e regola di cubaggio versionata della
specifica originale. Ogni riga corrisponde a uno specifico disco e mantiene lo
stesso abbinamento durante tutta la lavorazione.

Nel singolo lavoro la barra laterale e il pulsante del menu sono nascosti; **← Lavori**
riporta all'elenco con la sua navigazione. All'apertura il focus è assegnato all'area
di acquisizione: Invio breve acquisisce una misura e Invio tenuto premuto cambia
strumento, senza attivare i pulsanti di navigazione.

Le letture vengono conservate **in memoria durante la lavorazione**: acquisizioni,
correzioni e Reset non scrivono nel database e non ricaricano l'intera lista.
Il contatore **N da completare** si aggiorna usando le letture correnti.
**Salva e stampa etichetta** salva tutte le modifiche in un'unica transazione
nel database EcoS, senza inviare dati a Claricom. Le revisioni dei singoli dischi
restano conservate. È possibile salvare anche un lavoro parziale e riprenderlo.

Dopo il salvataggio si apre la stampa con il modello della **specifica storica
associata ai dischi prodotti**. Alla chiusura del dialogo di stampa si torna
all'elenco lavori; anche annullando la stampa, le misure salvate rimangono nel
database. Una stampante non disponibile permette di riprovare nella stessa
finestra, senza ripetere il salvataggio delle letture. La scadenza è sempre
calcolata dalla data di produzione registrata più cinque anni, anche nei dati
GS1, senza chiedere una data all'operatore. Il 29 febbraio diventa il 28 febbraio
quando l'anno di scadenza non è bisestile.

**Stampa etichetta** apre la stampa dello stesso modello, conservando eventuali
letture non salvate nel lavoro e senza salvarle implicitamente. Un modello
assente nella specifica viene segnalato: non si selezionano altri modelli né
il primo dell'elenco. La ristampa storica rimane un comando esplicito.

La stampa mostra la versione della specifica realmente associata al piano.
Se manca l'etichetta, il messaggio distingue questa versione dalla predefinita
attuale del prodotto: approvare o visualizzare una nuova specifica non la rende
predefinita e non aggiorna i piani già salvati.

Se si tenta di tornare ai lavori, alla Home o di chiudere la finestra con modifiche
non salvate, compare la conferma **Modifiche non salvate**, condivisa con il
catalogo: «Vuoi scartare le modifiche non salvate prima di continuare?». **No**
conserva le letture e mantiene aperto il lavoro; **Sì** prosegue scartandole.
Dopo un errore di salvataggio si resta nel lavoro con i valori locali e i comandi
**Riprova salvataggio** e **Ricarica lavoro**. Quest'ultimo richiede la stessa
conferma prima di scartare le modifiche. Le modifiche concorrenti non vengono
sovrascritte automaticamente.

La migrazione **V33** introduce `TON.DISCO_MISURE_REVISIONI`: acquisizioni parziali,
correzioni e cancellazioni di valori aggiungono revisioni, conservando le precedenti.
Sono conservati ID del disco, versione della regola, istante e operatore quando
selezionato in EcoS. Dal disco si risale al carico e alle versioni di specifica,
modello e composizione originali. I dati acquisiti prima di V33, che erano solo
in memoria, non possono essere ricostruiti automaticamente.

## Disposizione della schermata

Lo sfondo di acquisizione usa lo stesso azzurro del modulo. Nella fascia superiore,
**← Lavori** è a sinistra; **Reset**, micrometro e bilancia sono centrati;
**Stampa etichetta** è allineato a destra. Le righe delle
letture hanno due pixel di padding in meno sopra e sotto. Il contatore dei dischi
da completare è in basso nella colonna con **Cambia strumento**.

Le informazioni del lavoro si trovano sopra **Acquisisci** nella colonna sinistra:
prodotto, articolo, formato, lotto e nome del forno. Codice forno, numero produzione
e descrizione della regola non vengono mostrati. La colonna scorre quando lo spazio
verticale è insufficiente. Nella fascia inferiore **Copia su appunti** è a sinistra
e **Salva e stampa etichetta** a destra; entrambi i comandi sono attivi quando
l'operazione corrente lo consente.

## Acquisizione

- All'apertura tenta il collegamento a micrometro e bilancia in background.
  Stato verde: collegato; rosso: scollegato; giallo: operazione in corso.
  Clic sullo stato per scollegare o riprovare il collegamento.
- **Acquisisci** legge dallo strumento attivo; **Cambia strumento** passa all'altro.
  Il pedale che invia Invio mantiene il comportamento originale: pressione breve
  per acquisire, pressione di almeno 300 ms per cambiare strumento.
- La misura completa la prima riga senza un valore di quello strumento; in
  mancanza di righe libere segnala che tutti i dischi hanno già quella lettura.
  Le due **×** svuotano separatamente
  micrometro e bilancia, permettendo di riacquisire il dato.
- **Copia su appunti** esporta righe separate da ritorni a capo e colonne separate
  da tabulazioni: dati del micrometro, poi peso in grammi. I decimali usano la
  virgola, come in Marzia, per l'incolla nel foglio Excel esistente.
- **Reset**, dopo conferma, azzera le letture del lavoro conservando i dischi e
  le revisioni precedenti. Uscire dal modulo chiude i collegamenti.

Gli indirizzi sono configurabili nel file `application.properties` dell'installazione
(in sviluppo `config/development.properties`):

```properties
misurazioni.enabled=false
misurazioni.micrometro.host=192.168.1.150
misurazioni.micrometro.port=8600
misurazioni.bilancia.host=192.168.1.201
misurazioni.bilancia.port=1800
misurazioni.timeout.ms=2000
```

Il micrometro usa TCP e comando `T1\r`. La bilancia usa Modbus TCP con j2mod 3.1.1,
unità 255, registri 3–4 per il peso e 1102 per i decimali, conservando la conversione
di Marzia 1.0.1. Il micrometro attende un nuovo dato dopo la richiesta; timeout e
disconnessioni sono mostrati senza aggiungere zeri o `NaN` all'elenco.

Questa fase conserva le letture originali dei due strumenti e il riferimento alla
regola di cubaggio. L'interpretazione delle colonne del micrometro in D/Hmedia,
l'esecuzione delle formule sulle letture, il numero batch e l'invio a
Clarinet rimangono passaggi successivi. **Completati** indica entrambe le letture
salvate per ogni disco, senza attestare questi passaggi successivi.
Le verifiche automatiche usano strumenti TCP simulati; la prova
con gli strumenti fisici va eseguita sulla postazione di misurazione.

## Organizzazione del codice

`MisurazioniModule` è il punto d'ingresso del modulo. Le responsabilità sono
distribuite tra `view` (schermata), `controller` (azioni e pedale), `model`
(misure, abbinamento e formato per Excel), `devices` (collegamenti e protocolli)
e `config` (impostazioni della postazione). I test seguono gli stessi package.

`DeviceConnections` gestisce connessioni, letture in background e chiusura degli
strumenti; consegna i risultati al controller tramite callback sul thread della UI.
Il controller gestisce la sessione e l'elenco delle misure. I driver Keyence e
bilancia restano interni al package `devices`; i package del modulo non sono
esportati alle altre applicazioni di EcoS.
