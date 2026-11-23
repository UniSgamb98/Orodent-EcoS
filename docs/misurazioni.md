# Misurazioni

Modulo autonomo, sempre visibile nella Home di EcoS, che riprende la schermata e
il flusso di [Marzia 1.0.1](https://github.com/UniSgamb98/Marzia/commit/849773dc7e02c086efee27b0a2596b3216f8ec4e).

- All'apertura tenta il collegamento a micrometro e bilancia in background.
  Stato verde: collegato; rosso: scollegato; giallo: operazione in corso.
  Clic sullo stato per scollegare o riprovare il collegamento.
- **Acquisisci** legge dallo strumento attivo; **Cambia strumento** passa all'altro.
  Il pedale che invia Invio mantiene il comportamento originale: pressione breve
  per acquisire, pressione di almeno 300 ms per cambiare strumento.
- La misura completa la prima riga senza un valore di quello strumento; in
  mancanza di righe libere ne aggiunge una. Le due **×** svuotano separatamente
  micrometro e bilancia, permettendo di riacquisire il dato.
- **Copia su appunti** esporta righe separate da ritorni a capo e colonne separate
  da tabulazioni: dati del micrometro, poi peso in grammi. I decimali usano la
  virgola, come in Marzia, per l'incolla nel foglio Excel esistente.
- **Reset** svuota l'elenco. I dati restano nella sessione aperta: non vengono
  ancora salvati nel database. Uscire dal modulo chiude i collegamenti.

Gli indirizzi sono configurabili nel file `application.properties` dell'installazione
(in sviluppo `config/development.properties`):

```properties
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

Non sono ancora inclusi calcoli di temperatura, etichette, cotture o collegamenti
alle produzioni. Le verifiche automatiche usano strumenti TCP simulati; la prova
con gli strumenti fisici va eseguita sulla postazione di misurazione.
