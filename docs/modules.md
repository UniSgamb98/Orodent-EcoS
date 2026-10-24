# Integrare un'applicazione nell'ecosistema

Il launcher distribuisce un insieme coerente di JAR firmati. La Home di EcoS
costruisce il catalogo con `ServiceLoader`, leggendo i provider `EcosModule`
presenti nei componenti distribuiti. Non contiene un elenco di applicazioni
né collegamenti specifici a TON o Statistiche.

## Contratto comune

`orodent-module-api` espone quattro tipi nel package `com.orodent.ecos.module`:

| Tipo | Responsabilità |
| --- | --- |
| `EcosModule` | Provider senza stato di pagina; dichiara i metadati e apre una sessione |
| `ModuleDescriptor` | ID stabile, nome, descrizione e icona SVG con coordinate 24 × 24 |
| `ModuleContext` | Connessioni, executor, servizi CSV condivisi e ritorno alla Home |
| `ModuleSession` | Radice JavaFX e pulizia delle risorse della sessione |

I provider vengono creati all'avvio; non devono aprire connessioni, creare
controlli JavaFX o avviare attività nei costruttori o in `descriptor()`.
L'ID contiene lettere minuscole, numeri ed eventuali trattini, parte con una
lettera ed è unico nell'ecosistema. Gli ID duplicati interrompono l'avvio.
La Home ordina le applicazioni per nome e crea una scheda con icona, descrizione
e pulsante. I metadati non dipendono dal database.

`open(context)` viene chiamato sul thread JavaFX, dopo l'avvio dei servizi.
Ogni apertura restituisce una nuova `ModuleSession`. Tornando alla Home,
cambiando modulo o chiudendo l'app, EcoS chiama `close()` sulla sessione attiva.
Qui il modulo rimuove listener, tooltip e binding, annulla o invalida le proprie
operazioni pendenti e impedisce ai callback già in coda di aggiornare una pagina
chiusa. Non chiude database, executor o servizi CSV ricevuti dal contesto.
Query e calcoli si eseguono fuori dal thread JavaFX; gli aggiornamenti visivi
tornano sul thread JavaFX. Le transazioni passano da `ConnectionProvider`.

## Aggiungere un modulo

1. Creare un modulo Maven figlio con il parent della repository e dipendenza
   `com.orodent:orodent-module-api`. Registrarlo in `modules` e
   `dependencyManagement` del POM principale.
2. Implementare `EcosModule` con un costruttore pubblico senza argomenti.
   `TonModule` è l'esempio minimo; `StatisticheModule` integra controller e servizi.
3. Nel suo `module-info.java` aggiungere:

   ```java
   requires com.orodent.ecos.module.api;
   provides com.orodent.ecos.module.EcosModule with com.orodent.ecos.esempio.EsempioModule;
   ```

4. Per l'avvio da classpath in IntelliJ e nei test, creare anche
   `src/main/resources/META-INF/services/com.orodent.ecos.module.EcosModule`
   con il nome completo del provider, una riga:

   ```text
   com.orodent.ecos.esempio.EsempioModule
   ```

5. Aggiungere il modulo alle dipendenze Maven di `orodent-app`: il packaging lo
   includerà automaticamente. Non occorre modificare Home, AppController o il
   `module-info` dell'app. La risoluzione JPMS include i provider del servizio.
6. Verificare build, logica del modulo e apertura/chiusura dall'ecosistema.
   `bash mvnw -Pui-smoke clean install` include la navigazione grafica;
   senza display usare `bash mvnw clean install`.

Per i dati, riutilizzare i domini esistenti; aggiungere una migrazione solo per
tabelle nuove effettivamente necessarie. Il modulo non crea un database o un
discovery proprio. Gli stili specifici rimangono sulla sua radice JavaFX.

Il catalogo non installa JAR arbitrari né abilita versioni indipendenti dei moduli:
nuove applicazioni e nuove dipendenze arrivano con una normale release firmata
dei componenti, al successivo avvio del launcher.
