# Release pubbliche e aggiornamenti Orodent-EcoS

## Stato e limiti della prima versione

Il launcher distribuito può controllare la release pubblica stabile più recente di
`UniSgamb98/Orodent-EcoS`, verificare il catalogo firmato Ed25519, scaricare tutti i
componenti di una release e avviarli. Non serve alcun token sui computer degli utenti.
La rete non è usata dall'app avviata direttamente in IntelliJ.

I pacchetti manuali hanno `updates.enabled=false`: l'abilitazione avviene nelle
build firmate di GitHub Actions. Anche con il flag attivo, senza la chiave pubblica
il launcher non accetta aggiornamenti e usa la versione installata.
**I vecchi EXE non acquisiscono questa funzionalità da soli:** la prima distribuzione
con il nuovo launcher e la chiave va installata manualmente, una volta.

La linea attuale supporta Windows x64, Java 21 e protocollo updater 1. Aggiorna i JAR
come un insieme; EXE, launcher e Java rimangono quelli installati. Il contratto di
compatibilità resta `database.schema=1`, come richiesto dai launcher già distribuiti.
Il contatore interno delle migrazioni DB è distinto: attualmente vale 2.

`ReleaseTool` legge `database/version.properties` dal JAR database che sta firmando
e dichiara nel catalogo `database.migration.minimum` e `database.migration.target`.
I vecchi launcher ignorano questi campi aggiuntivi, mantenendo tutte le verifiche
esistenti; l'applicazione HOST esegue la migrazione supportata in transazione al primo
avvio. Per il passaggio da DB 1 a 2 basta quindi una normale release firmata, senza
sostituire il launcher. Conservare config e percorso dei dati e riavviare prima
l'HOST, poi i CLIENT: vedere il [passaggio allo schema corrente](database-schema.md).

Cambiare il runtime, il launcher o un contratto non supportato dalle installazioni
esistenti richiede ancora una distribuzione completa. Aumentare soltanto il contatore
delle migrazioni non implica un cambio di contratto; occorre verificare che i nuovi
componenti gestiscano realmente la migrazione e rifiutino database incompatibili.

## 1. Creare la chiave una sola volta, sul proprio PC

Dalla radice del progetto, con JDK 21 standard:

```powershell
java scripts/ReleaseTool.java keygen
```

Vengono creati:

- `config/update-public-key.pem`: pubblica, da aggiungere alla repository.
- `.release-keys/update-private-key.pem`: privata, esclusa da Git; non pubblicarla,
  non allegarla a issue o chat e conservarne un backup protetto.

Il comando rifiuta di sovrascrivere chiavi esistenti. Non rigenerare la coppia per
ogni versione: i launcher già distribuiti conoscono soltanto la chiave pubblica iniziale.

Su GitHub aprire **Settings → Secrets and variables → Actions → New repository secret**:

- Nome: `UPDATE_SIGNING_PRIVATE_KEY`.
- Valore: contenuto completo del file privato, incluse le righe BEGIN/END.

Caricare soltanto la chiave pubblica nel codice:

```powershell
git add config/update-public-key.pem
git commit -m "Configure public release signing key"
git push
```

Il token `GITHUB_TOKEN` per creare la release è fornito automaticamente dal workflow.
Non va creato un PAT né aggiunto un token all'applicazione. La firma del catalogo
non equivale alla firma Authenticode dell'EXE: Windows può comunque mostrare SmartScreen.

## 2. Creare la prima release

Prima fare il merge della PR e della chiave pubblica sul branch principale.
Verificare che GitHub Actions sia abilitato nella repository. Dalla copia aggiornata:

```powershell
git switch master
git pull
git tag v0.1.0
git push origin v0.1.0
```

Il workflow `.github/workflows/release.yml` usa un runner Windows con Temurin 21,
imposta la versione Maven dal tag, abilita gli aggiornamenti nella sola build CI,
esegue test e packaging, firma i componenti e crea una **bozza** in GitHub Releases.
Non modifica o sposta tag esistenti; per correzioni pubblicare una nuova versione.
Se manca il secret o la chiave non corrisponde, non viene pubblicata una release utilizzabile.

La bozza contiene:

| Asset | Uso |
| --- | --- |
| `Orodent-EcoS-windows-portable.zip` | Prima installazione manuale: EXE, Java, launcher, moduli e config iniziale |
| `Orodent-EcoS-components.zip` | Soltanto `lib/*.jar`; nessun database o configurazione |
| `update.properties` | Versione, runtime/schema compatibili e checksum dei componenti |
| `update.properties.sig` | Firma Ed25519 dei byte esatti del catalogo |

Provare il pacchetto completo Windows della bozza prima di selezionare **Publish release**.
Pubblicare come release stabile, non prerelease: il launcher usa `/releases/latest/`.
Non sostituire gli asset di una versione già pubblicata e non fare il downgrade dei tag.

## 3. Provare un aggiornamento con due versioni

1. Pubblicare `v0.1.0`, estrarre il pacchetto completo e avviarlo. La Home mostra `0.1.0`.
2. Per la prova usare un file config con un database di test e porte dedicate,
   non i dati di produzione.
3. Pubblicare un piccolo cambiamento come tag `v0.1.1` (stesso runtime e schema).
4. Chiudere l'app e riaprire l'EXE **0.1.0 già estratto**, senza sostituirlo.
5. Il launcher verifica e scarica i componenti 0.1.1; la Home mostra `0.1.1`.
6. Scollegare la rete e riaprire: deve funzionare la versione 0.1.1 già verificata.
7. Controllare che directory DB e contenuto del proprio file config siano invariati.

La pubblicazione e questo ciclo Windows/GitHub devono essere verificati sul servizio
reale: i test locali coprono firma, integrità, installazione, cache offline, versioni
precedenti, archivio malevolo e fallimento della validazione runtime.

## Comportamento operativo

Il catalogo e la firma sono scaricati dagli URL pubblici `releases/latest/download`;
il pacchetto è scaricato dal tag esatto firmato. La chiave fidata è incorporata nel
launcher. Firma e SHA-256 non sono disabilitabili. Sono applicati limiti di dimensione,
timeout, controlli sui nomi ZIP e una verifica `java --dry-run` prima dell'attivazione.

Le versioni sono conservate sotto `%LOCALAPPDATA%/Orodent-EcoS/updates/windows-x64`
(con fallback nel profilo utente). Ogni cartella è immutabile; un file `active`
seleziona la versione tramite sostituzione atomica. Un lock protegge l'installazione
contemporanea. Non vengono cancellate automaticamente le versioni precedenti.
La cache è controllata nuovamente contro il catalogo firmato a ogni avvio.

Se rete, firma, checksum o controllo runtime falliscono, il launcher usa la versione
verificata già disponibile (oppure quella inclusa). I dettagli sono in
`%LOCALAPPDATA%/Orodent-EcoS/logs/updates.log`. Il controllo del grafo moduli non
sostituisce i test funzionali: un bug applicativo dopo l'avvio può comunque causare
un errore e non attiva un riavvio automatico della vecchia app.

`updates.enabled=false` nel config disabilita controllo e selezione della cache,
avviando i componenti inclusi nel pacchetto originario. Usare questa opzione per
una prova diagnostica soltanto quando il vecchio codice è compatibile con i dati.
Il config personale, la directory database e il runtime non sono mai nel pacchetto
componenti e non vengono sovrascritti dall'updater.

## Se il primo tag fallisce nei test

I workflow sono associati al commit del tag: rilanciare lo stesso job non usa le
correzioni successive. Correggere il codice su un branch, attendere il workflow
Windows tests e integrare la PR, poi creare un nuovo tag (ad esempio `v0.1.1`
se `v0.1.0` e fallito). Non spostare il tag precedente. I report di errore sono
scaricabili negli artifact `windows-test-reports` o `release-test-reports`.
