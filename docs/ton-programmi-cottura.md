# Programmi di cottura

In **TON → Cotture → Programmi di cottura**, **Nuovo programma** apre una scheda
con dati generali, ciclo di sinterizzazione e versioni. L'elenco permette ricerca
per codice o nome e apertura con doppio clic o Invio.

## Step e tempi

Ogni step contiene **temperatura finale in °C**, **tempo di rampa** e **tempo di
mantenimento**. La rampa porta dalla temperatura precedente alla nuova temperatura
nel tempo indicato; il mantenimento resta alla temperatura finale. Sono possibili
anche step di raffreddamento. Gli step possono essere aggiunti, spostati e rimossi.

I tempi usano **ore.minuti**, con due cifre per i minuti:

- `1.30` indica 1 ora e 30 minuti, cioè 90 minuti.
- `0.05` indica 5 minuti.
- `2.00` indica 2 ore; è accettato anche `2`.
- `0.00` indica un tempo nullo.

I minuti devono essere tra `00` e `59`: `1.60`, `1.3` e valori negativi vengono
respinti. Le temperature devono essere positive, con al massimo due decimali.
I tempi sono salvati come minuti interi, senza conversioni in ore decimali.
Sono consentiti fino a 200 step e una bozza senza step; per approvare serve
almeno uno step completo e valido.

La durata totale somma rampe e mantenimenti. Il grafico mostra la successione
dei setpoint; la **temperatura iniziale di esempio** serve solo all'anteprima,
è modificabile e non fa parte del programma salvato. La temperatura iniziale
effettiva dipenderà dal forno al momento dell'esecuzione.

## Versioni e conservazione

**Salva bozza** aggiunge una revisione senza riscrivere i salvataggi precedenti.
**Salva e approva** conserva una revisione approvata, in sola lettura.
**Crea nuova versione** copia il ciclo selezionato in una nuova bozza. Non possono
coesistere due bozze correnti dello stesso programma. Il codice non cambia dopo
il primo salvataggio; nome, note, stato di disponibilità e step sono conservati
insieme a ogni revisione.

La scheda comune **Versioni** mostra versione, revisione, stato e istante di
salvataggio. Doppio clic o Invio aprono la revisione scelta. Le revisioni precedenti,
comprese quelle di bozza, rimangono consultabili in sola lettura. In caso di
modifiche simultanee da più postazioni, un salvataggio obsoleto viene respinto
senza sovrascrivere il lavoro dell'altro operatore.

La migrazione **V31** aggiunge le famiglie dei programmi e le revisioni alle
tabelle già esistenti `TON.PROGRAMMI_COTTURA` e `TON.PROGRAMMA_PASSI`. Ogni
revisione ha un proprio `programma_id`: i programmi precedenti conservano ID,
step e riferimenti già presenti nei cicli di cottura. I nuovi salvataggi aggiungono
record e non modificano il contenuto dei programmi già registrati.

Questa sezione configura e conserva i programmi. La scelta del programma per
un carico, l'avvio del forno, le sonde e gli esiti saranno collegati nel passaggio
dedicato all'esecuzione della cottura.
