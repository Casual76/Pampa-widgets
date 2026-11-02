# Pampa Widgets

Un piccolo store di widget Android fatti da Pampa. Oggi ne contiene uno, **Media Controls**: i
controlli della musica sulla home (Spotify, Fluidify o la sessione attiva), da 2×2 a tutta pagina,
con sorgente, colori e dettagli scelti per ogni istanza.

L'app è costruita sul [Fluid Engine](https://github.com/Casual76/fluid-engine), agganciato come
submodule in `engine/` (la versione sta in `engine.properties`). Il widget è `RemoteViews` classico;
le schermate dell'app sono Compose con i componenti dell'engine.

## Compilare

```powershell
git submodule update --init
./gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

La build di release si firma con la chiave delle app Pampa, letta da un file ignorato da git
(`keystore.properties` o `local.properties`). Senza, si compila solo la debug, che ha un
`applicationId` suo (`com.pampa.widgets.debug`) e si installa accanto a quella vera.

## Licenza

Pampa Widgets è software libero, distribuito sotto la **GNU General Public License, versione 3**
(testo completo in [`LICENSE`](LICENSE)).

Il motivo è l'engine: dalla 3.0 il Fluid Engine include codice di
[LibrePods](https://github.com/librepods-org/librepods) (GPL-3.0) — il comportamento dei pulsanti e
delle pagine — e un'app che lo distribuisce è un'opera derivata. Quindi:

- chi riceve l'APK ha diritto al codice sorgente completo, con la stessa licenza: è questo
  repository, [github.com/Casual76/Pampa-widgets](https://github.com/Casual76/Pampa-widgets), con il
  submodule `engine` alla versione indicata in `engine.properties`;
- le versioni fino alla 0.4.x (engine 1.x, senza codice di LibrePods) erano uscite senza una licenza
  dichiarata; dalla 0.5.0 vale la GPL-3.0.

Le altre opere di terze parti che arrivano con l'engine mantengono la propria licenza: il vetro di
[AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) di Kyant (Apache-2.0) e il font
Inter (SIL Open Font License 1.1). L'elenco completo, con gli avvisi che le licenze chiedono, è
nell'app in **Impostazioni › Licenze** e in `engine/LICENSES/`.

Questo programma è distribuito nella speranza che sia utile, ma **senza alcuna garanzia**; vedi la
GNU General Public License per i dettagli.
