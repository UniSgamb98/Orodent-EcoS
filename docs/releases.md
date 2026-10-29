# Release pubbliche e aggiornamenti Orodent-EcoS

## Applicazioni e runtime aggiornabili

Il launcher distribuito può controllare la release pubblica stabile più recente di
`UniSgamb98/Orodent-EcoS`, verificare il catalogo firmato Ed25519, scaricare tutti i
componenti di una release e avviarli. Non serve alcun token sui computer degli utenti.
La rete non è usata dall'app avviata direttamente in IntelliJ.

I pacchetti manuali hanno `updates.enabled=false`: l'abilitazione avviene nelle
build firmate di GitHub Actions. Anche con il flag attivo, senza la chiave pubblica
il launcher non accetta aggiornamenti e usa la versione installata.
**I vecchi EXE non acquisiscono questa funzionalità da soli:** la prima distribuzione
con il nuovo launcher e la chiave va installata manualmente, una volta.

La linea attuale supporta **Windows x64**, Java 21 o successivo e **protocollo
updater 2**. Il launcher resta avviato con il Java incluso nell’installazione;
per l’applicazione sceglie insieme i JAR e il runtime richiesti dalla release.
Il modulo launcher è compilato con `--release 21`, anche usando un JDK più recente,
per preservare la compatibilità del bootstrap. Un cambio di moduli Java o di versione Java non richiede più la sostituzione
manuale del pacchetto sulle postazioni che hanno questo launcher.

Il primo passaggio dai vecchi launcher di protocollo 1 richiede ancora lo **ZIP
completo una volta**, mantenendo il config personale e la cartella DB. Il vecchio
launcher non comprende il protocollo 2 e lo rifiuta prima di attivare componenti
incompatibili. Lo ZIP nuovo include già `jdk.jsobject` per l’editor e l’updater 2.
Modifiche future al bootstrap/EXE, alla chiave fidata o alla piattaforma possono
ancora richiedere una distribuzione iniziale aggiornata: il runtime applicativo
è distinto dal runtime che ospita il bootstrap.

Il contratto dati resta `database.schema=1`; il contatore interno delle migrazioni
è distinto e attualmente vale **6**. `ReleaseTool` legge `database/version.properties`
dal JAR database e firma `database.migration.minimum` e `database.migration.target`.
Le migrazioni supportate sono eseguite dall’HOST con backup, senza cambiare il
config: vedere il [passaggio allo schema corrente](database-schema.md).
Il nuovo updater non modifica lo schema e non ripete i salvataggi applicativi.

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
esegue test e packaging, firma il catalogo di componenti e runtime e crea una **bozza** in GitHub Releases.
Non modifica o sposta tag esistenti; per correzioni pubblicare una nuova versione.
Se manca il secret o la chiave non corrisponde, non viene pubblicata una release utilizzabile.

La bozza contiene:

| Asset | Uso |
| --- | --- |
| `Orodent-EcoS-windows-portable.zip` | Prima installazione manuale: EXE, Java, launcher, moduli e config iniziale |
| `Orodent-EcoS-components.zip` | Soltanto `lib/*.jar`; nessun database o configurazione |
| `Orodent-EcoS-runtime.zip` | Runtime Windows della release; scaricato solo se manca un runtime identico verificato |
| `update.properties` | Versione, piattaforma, inventari e checksum di componenti e runtime |
| `update.properties.sig` | Firma Ed25519 dei byte esatti del catalogo |

Provare il pacchetto completo Windows della bozza prima di selezionare **Publish release**.
Pubblicare come release stabile, non prerelease: il launcher usa `/releases/latest/`.
Non sostituire gli asset di una versione già pubblicata e non fare il downgrade dei tag.

## 3. Provare un aggiornamento con due versioni

1. Pubblicare `v0.1.0`, estrarre il pacchetto completo e avviarlo. La Home mostra `0.1.0`.
2. Per la prova usare un file config con un database di test e porte dedicate,
   non i dati di produzione.
3. Pubblicare un piccolo cambiamento come tag `v0.1.1` con il nuovo workflow.
4. Chiudere l'app e riaprire l'EXE **0.1.0 già estratto**, senza sostituirlo.
5. Il launcher verifica e scarica i componenti 0.1.1; riusa Java se identico, altrimenti scarica il runtime. La Home mostra `0.1.1`.
6. Scollegare la rete e riaprire: deve funzionare la versione 0.1.1 già verificata.
7. Controllare che directory DB e contenuto del proprio file config siano invariati.

La pubblicazione e questo ciclo Windows/GitHub devono essere verificati sul servizio
reale: i test locali coprono firma, integrità, installazione, cache offline, versioni
precedenti, archivio malevolo e fallimento della validazione runtime.

Per provare anche il cambio di runtime, creare una seconda build con un JDK
standard aggiornato o con una diversa dotazione di moduli nello script di packaging.
Riutilizzare lo stesso EXE già installato: dopo l’aggiornamento `logs/updates.log`
riporta i percorsi selezionati, e il nuovo Java è nella cache `runtimes`.
Il test `WindowsManagedRuntimeTest` esegue su Windows la catena reale jlink → firma
→ download simulato degli asset → estrazione verificata → processo Java scaricato.
La pubblicazione GitHub e la prova sull’EXE distribuito restano controlli di release.

## Catalogo firmato e pubblicazione manuale

Il workflow usa automaticamente:

```powershell
java scripts/ReleaseTool.java release 0.2.0 orodent-launcher/target/distribution/app/lib target/release target/windows-portable/Orodent-EcoS/runtime
```

Richiede il secret di firma già configurato. Non rigenerare la chiave per aggiornare
Java. L’ultimo argomento è l’immagine Java Windows generata dal packaging.
L’inventario firmato include tutti i suoi file, `runtime.id` derivato dai percorsi
e hash SHA-256, versione Java e checksum dell’archivio. Un runtime identico può
essere riusato fra release, indipendentemente dalla versione dell’applicazione.
La forma storica a quattro argomenti conserva il protocollo 1 per i test di
compatibilità; le nuove release distribuite devono usare la forma a cinque.

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

I runtime sono in `updates/windows-x64/runtimes/runtime-<id>-<uuid>`. Ogni file
viene verificato contro l’inventario prima di eseguire Java. Sono rifiutati archivi
incompleti, percorsi esterni e duplicati, collegamenti simbolici e versioni non
corrispondenti. La verifica finale `java --dry-run` usa **il Java selezionato**
insieme ai JAR selezionati. Solo dopo il successo viene sostituito `active`.
Una nuova immagine viene preparata in un’altra cartella; non si sovrascrive mai
il Java che sta ospitando un processo già aperto.

Il catalogo è limitato a 1 MiB, i componenti compressi a 256 MiB e il runtime
compresso a 512 MiB (1 GiB estratto). Non si cancellano automaticamente runtime
o applicazioni precedenti. Non sono necessari privilegi amministrativi.

Se rete, firma, checksum o controllo runtime falliscono, il launcher usa la versione
verificata già disponibile (oppure quella inclusa). I dettagli sono in
`%LOCALAPPDATA%/Orodent-EcoS/logs/updates.log`. Il controllo del grafo moduli non
sostituisce i test funzionali: un bug applicativo dopo l'avvio può comunque causare
un errore e non attiva un riavvio automatico della vecchia app.

`updates.enabled=false` nel config disabilita controllo e selezione della cache,
avviando i componenti inclusi nel pacchetto originario. Usare questa opzione per
una prova diagnostica soltanto quando il vecchio codice è compatibile con i dati.
Il config personale e il database non vengono sovrascritti dall’updater. Il runtime
non è nel pacchetto componenti: ha un archivio separato e una directory immutabile.
Gli errori applicativi dopo l’avvio non provocano un downgrade automatico, che
potrebbe essere incompatibile con un DB già migrato.

## Se il primo tag fallisce nei test

I workflow sono associati al commit del tag: rilanciare lo stesso job non usa le
correzioni successive. Correggere il codice su un branch, attendere il workflow
Windows tests e integrare la PR, poi creare un nuovo tag (ad esempio `v0.1.1`
se `v0.1.0` e fallito). Non spostare il tag precedente. I report di errore sono
scaricabili negli artifact `windows-test-reports` o `release-test-reports`.
