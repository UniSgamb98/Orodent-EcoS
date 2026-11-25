# Programmi di cottura

In **TON → Cotture → Programmi di cottura**, **Nuovo programma** apre una scheda
con dati generali, ciclo di sinterizzazione e versioni. L'elenco permette ricerca
per codice o nome e apertura con doppio clic o Invio.

## Step e tempi

Ogni step contiene **temperatura finale in °C**, **tempo di rampa** e **tempo di
mantenimento**. La rampa porta dalla temperatura precedente alla nuova temperatura
nel tempo indicato; il mantenimento resta alla temperatura finale. Sono possibili
anche step di raffreddamento. Gli step possono essere aggiunti, spostati e rimossi.

Nella temperatura si può scrivere **`Tfinale`** per usare la temperatura finale
indicata al momento del carico. Per esempio, `500 → Tfinale → 900` conserva due
temperature fisse e una temperatura dipendente dal carico. Lo stesso riferimento
può essere usato in più step. Il programma conserva il simbolo; ogni carico conserva
il proprio valore numerico. L'anteprima permette di impostare una **Tfinale di
esempio**, che non modifica il programma salvato.

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

## Programma nel carico

In **Pianificazione e carichi** si scelgono una versione approvata del programma
e la temperatura finale del carico. I programmi approvati precedenti restano
selezionabili: creare una nuova versione non sostituisce quella usata su un forno.
Le famiglie archiviate e le bozze non sono proposte per nuovi carichi.

Scegliendo il forno, viene proposto il programma esatto dell'ultimo carico
registrato su quel forno. La memoria è persistente e distinta per forno; segue
l'ordine di registrazione, anche per carichi con date retrodatate. Senza precedenti
il campo è vuoto. Se l'ultimo programma non è più disponibile, il campo resta
vuoto e un messaggio ne indica il motivo: non viene scelto un sostituto automatico.

Scegliendo un altro programma, un avviso ricorda di impostarlo anche sulla
macchina. Il carico salva anche il programma precedente, ricontrollato durante
la transazione di registrazione, per conservare il cambio e poterlo richiamare
nel futuro foglio operativo. Non vengono inviati comandi al forno.

La migrazione **V32** permette il riferimento `Tfinale` negli step e aggiunge
temperatura finale e programma precedente ai cicli di cottura. Gli ID dei programmi,
i riferimenti esistenti e le temperature fisse dei vecchi step sono conservati.
I carichi precedenti privi di temperatura non ricevono valori inventati.
L'avvio del forno, le sonde, gli esiti e la stampa del foglio saranno collegati
nei passaggi successivi.
