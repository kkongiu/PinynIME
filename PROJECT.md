# Pinyn IME — Android Pinyin Input Method

Porting su Android 8.0 (API 26) ARM64 del progetto [Pinyn](https://github.com/...),
correttore pinyin intelligente per studenti di cinese.

## Architettura

```
┌─── Pinyn IME (InputMethodService) ────────────────────┐
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │ PinynIME.kt │──│ PinyinEngine │──│ PinyinData.kt │ │
│  │ (I/O tasti) │  │ (segmentaz., │  │ (dizionario)  │ │
│  └─────────────┘  │  correzione) │  └───────────────┘ │
│                    └──────────────┘                     │
│                         │                              │
│  ┌──────────────────────┴───────────────────────────┐  │
│  │ Componenti (in ordine di priorità porting)       │  │
│  │                                                   │  │
│  │ ⬜ ToneNet.kt        — rete neurale toni (215KB)  │  │
│  │ ⬜ LevRank.kt        — Levenshtein + bigram rank  │  │
│  │ ✅ Segmentation.kt   — DP segmentazione           │  │
│  │ ⬜ AccentEngine.kt   — euristico + ibrido neurale │  │
│  │ ⬜ SentenceLM.kt     — language model (1.5MB)     │  │
│  │ ⬜ GrammarRules.kt   — 3 regole conservative      │  │
│  └───────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

## Stato corrente (v0.1)

| Funzionalità | Stato | Note |
|---|---|---|
| Tastiera QWERTY | Fatto | Layout 4 righe, tasti disegnati a mano |
| Candidate bar | Fatto | Mostra hanzi + pinyin con toni |
| Segmentazione pinyin | Fatto | DP longest-valid-syllable-first |
| Lookup toni | Stativo | Lookup statico da 1533 parole monosillabe HSK |
| Correzione typo | Mancante | Levenshtein da portare |
| Rete neurale toni | Mancante | Forward pass MLP da portare |
| Ranking contestuale | Mancante | Bigram da portare |
| Hanzi dalle reti | Mancante | Sentence LM da portare |
| Toggle Hanzi/Pinyin | Fatto | Tasto `字` sulla tastiera |
| Impostazioni | Fatto | Hanzi mode, accent mode, grammar |

## Componenti da portare

### 1. ToneNet — Rete Neurale Toni

**Source file:** `src/llm/tone-net.js` + `src/data/tone-net-data.js`
**Dimensione modello:** ~215 KB (int8 quantizzato)
**Architettura:** MLP a finestra di contesto ±5 sillabe
```
Embedding(V x 32) → Flatten(11 × 32 = 352) → FC 352×128 ReLU → FC 128×64 ReLU → FC 64×5 softmax
```
**Accuratezza:** ~82% per sillaba (vs ~73% euristico)
**Runtime:** forward pass in Kotlin (~50 righe), caricamento pesi da asset JSON→Float32Array
**Impatto:** toni predetti da AI invece che lookup statico

### 2. Levenshtein + Bigram Ranking

**Source file:** `src/utils/levenshtein.js`, `src/utils/correction-engine.js`
**Funzione:** correzione typo (edit distance + swap adiacenti) + ranking contestuale bigram
**Impatto:** `hkao` → suggerisce `hǎo`, candidati ordinati per contesto frase

### 3. Accent Engine (accentRun / accentChoice)

**Source file:** `src/utils/correction-engine.js:accentRun()`
**Funzione:** scelta ibrida euristico/neurale (confidenza ≥65% = euristico, altrimenti ToneNet)
**Impatto:** accenti automatici a frase intera, non solo sillaba singola

### 4. Sentence Language Model

**Source file:** `src/llm/sentence-net.js` + `src/data/sentence-net-data.js`
**Dimensione modello:** ~1.5 MB (int8 quantizzato)
**Architettura:** MLP predittore a finestra K=3 parole
```
Embedding(V × 32) → Flatten(K×32) → FC 96×V logits
```
**Impatto:** punteggio di plausibilità frasi, flag per struttura insolita

### 5. Grammar Rules

**Source file:** `src/utils/grammar-rules.js`
**Funzione:** 3 regole conservative (很+nome, 是+verbo, 吗 non a fine frase)
**Impatto:** warning in candidate bar

## Build & Sviluppo

```bash
# Requisiti PC
export JAVA_HOME=~/java/jdk-21.0.12+12   # Eclipse Temurin
export ANDROID_HOME=~/Android/Sdk          # API 26 + 34

# Build
cd ~/pinyn-ime
./gradlew assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Logcat
adb logcat | grep -i pinyn
```

## Riferimenti

- Progetto originale Pinyn: `/home/kongiu/projects/Pinyn/`
- Android IME docs: https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- Componenti JS source: `src/llm/`, `src/utils/`, `src/data/`
