# PinynIME

Android IME (tastiera) per l'inserimento del pinyin, con predizione neurale dei toni, dizionario HSK ponderato e motore di correzione.

Porting nativo **Kotlin** dell'estensione browser [Pinyn](https://github.com/kkongiu/Pinyn).

## Funzionalità

- **Tastiera QWERTY** custom, disegnata a mano (nessuna dipendenza UI)
- **Predizione dei toni** tramite rete neurale (ToneNet, MLP int8 quantizzato, ~82% accuratezza) eseguita nativamente in Kotlin
- **Dizionario HSK 3.0** completo (10.974 parole), ponderato per livello con decadimento esponenziale `0.5^(level-1)`
- **Match parole multi-sillaba**: `nihao` → `nǐ hǎo` / `你好`
- **Correzione e candidati**: varianti di tono ordinate per frequenza pesata
- **Modalità Hanzi/Pinyin**: tasto `字` per alternare tra caratteri cinesi e pinyin accentato
- **Segmentazione DP**: `nihao` → `["ni", "hao"]`

## Architettura

```
app/src/main/java/com/pinyn/ime/
├── PinynIME.kt          # InputMethodService (gestione tasti/commit)
├── PinynKeyboardView.kt # disegno tastiera + candidate bar
├── PinyinEngine.kt      # segmentazione + accenti + candidati
├── ToneNet.kt           # forward pass rete neurale toni
├── WordIndex.kt         # dizionario multi-sillaba (da asset)
├── PinyinData.kt        # sillabe valide + candidati (generato)
└── SettingsManager.kt   # preferenze

app/src/main/assets/data/
├── tone-net-data.js     # pesi rete neurale (int8, 215KB)
└── words.txt            # dizionario parole (8553 voci)
```

## Build

```bash
export JAVA_HOME=<path-to-jdk>
export ANDROID_HOME=<path-to-android-sdk>
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Test

```bash
./gradlew testDebugUnitTest
```

40 test: segmentazione, tone marks, ToneNet (confronto con output JS), dizionario, parole reali.

## Licenza

Il dizionario HSK 3.0 e le reti neurali derivano dal progetto Pinyn (vedi attribuzioni: CC-CEDICT CC-BY-SA 4.0, Tatoeba CC-BY).
