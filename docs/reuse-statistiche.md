# Base applicativa Ton-V3

Codice riutilizzato da `UniSgamb98/Statistiche`, branch
`codex/statistiche-app-icon`, commit `1e7c4fdf8dd6fa0e648093ad26c4ee4a757374fd`.

- `core`: ConnectionProvider, database Derby HOST/CLIENT, discovery UDP, schema,
  modelli, repository, lettura CSV e servizi di importazione asincroni.
- `app`: composizione delle dipendenze, executor di background/importazione,
  inizializzazione asincrona con Task JavaFX, schermata di avvio con riprova,
  navigazione e chiusura delle risorse.
- `features/home`: Home essenziale; la Home del riferimento è una dashboard vendite,
  le cui funzionalità di analisi non sono incluse in questo progetto.
- CSS globale e test del livello core ripresi dal riferimento.

Package adattati a `com.orodent.tonv3`. Ton-V3 utilizza `TonV3Database`,
protocollo discovery `TONV3`, TCP 1528 e UDP 45680: non scopre host Statistiche.
Il database conserva lo schema clienti/vendite del riferimento.
La directory predefinita sui sistemi non Windows è `~/.ton-v3/database`;
`ton.database.home` o `TON_DATABASE_HOME` consentono di cambiarla.
Vedere anche [database-discovery.md](database-discovery.md).

## Sviluppo

Richiede Java 21, Maven (oppure `bash mvnw`) e le librerie native JavaFX.

```sh
bash mvnw verify
bash mvnw javafx:run
```

Il punto d'ingresso è `app.MainApp`; `Launcher` delega allo stesso avvio.
I test includono database, transazioni/importazioni e discovery con processi separati.
Il discovery tra macchine richiede una LAN con broadcast UDP e porte accessibili.
