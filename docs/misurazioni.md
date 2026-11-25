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
tutti i dischi hanno entrambe le letture salvate. **Strumenti** mostra i collegamenti.

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

**Ogni acquisizione è salvata automaticamente nel database**, anche se è arrivata
soltanto la lettura di uno strumento. **Misure salvate** conferma il salvataggio.
Tornando all'elenco o chiudendo e riaprendo il modulo, le letture precedenti sono
ripristinate nella stessa schermata e si continua dalle righe incomplete.
Un lavoro può quindi restare **In corso** con qualsiasi numero di dischi rimanenti.

Durante il salvataggio si attende prima di acquisire altre misure o cambiare
lavoro. Se il salvataggio fallisce, i valori restano visibili, accompagnati da
**Misure non salvate** e **Riprova salvataggio**. **Ricarica lavoro** ripristina i
valori del database; scartare dati non salvati richiede conferma. Una modifica
concorrente non viene sovrascritta automaticamente.

La migrazione **V33** introduce `TON.DISCO_MISURE_REVISIONI`: acquisizioni parziali,
correzioni e cancellazioni di valori aggiungono revisioni, conservando le precedenti.
Sono conservati ID del disco, versione della regola, istante e operatore quando
selezionato in EcoS. Dal disco si risale al carico e alle versioni di specifica,
modello e composizione originali. I dati acquisiti prima di V33, che erano solo
in memoria, non possono essere ricostruiti automaticamente.

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
l'esecuzione delle formule sulle letture, il numero batch, le etichette e l'invio a
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
