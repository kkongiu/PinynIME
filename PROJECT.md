# Pinyn IME — Android Pinyin Input Method

Porting su Android 8.0 (API 26) ARM64 del progetto [Pinyn](https://github.com/...),
correttore pinyin intelligente per studenti di cinese.

## Architettura

```
┌─── Pinyn IME (InputMethodService) ────────────────────┐
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐ │
│  │ PinynIME.kt │──│ PinyinEngine │──│ PinyinData.kt │ │
│  │ (I/O tasti) │  │ (segmentaz.,  │  │ (dizionario)  │ │
│  └─────────────┘  │  correzione) │  └───────────────┘ │
│                    └──────────────┘                     │
│                         │                              │
│  ┌──────────────────────┴───────────────────────────┐  │
│  │ Componenti (tutti portati e integrati)           │  │
│  │                                                   │  │
│  │ ✅ ToneNet.kt        — rete neurale toni (215KB)  │  │
│  │ ✅ Levenshtein.kt    — edit distance + swap       │  │
│  │ ✅ CorrectionEngine.kt — bigram + rank + accenti  │  │
│  │ ✅ PinyinUtils.kt    — toni numeri↔diacritici     │  │
│  │ ✅ HskData.kt        — parser HSK 3.0 + POS       │  │
│  │ ✅ SentenceNet.kt    — language model (1.5MB)     │  │
│  │ ✅ GrammarRules.kt   — 3 regole conservative      │  │
│  │ ✅ WordIndex.kt      — dizionario multi-sillaba   │  │
│  └───────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

## Stato corrente (v1.0)

| Funzionalità | Stato | Note |
|---|---|---|
| Tastiera QWERTY | Fatto | Layout 4 righe, tasti disegnati a mano |
| Candidate bar | Fatto | Mostra hanzi + pinyin con toni |
| Segmentazione pinyin | Fatto | DP longest-valid-syllable-first |
| Segmentazione parole | Fatto | `segmentWords` (maximal munch) per LM/grammar |
| Lookup toni | Fatto | ToneNet (MLP int8) + fallback euristico |
| Accenti automatici | Fatto | `accentRun` ibrido euristica/neurale |
| Correzione typo | Fatto | Levenshtein + swap adiacenti |
| Ranking contestuale | Fatto | Bigramma per correzione + ordinamento candidati |
| Hanzi da lookup | Fatto | CANDIDATE_MAP + dizionario parole |
| Sentence LM | Fatto | Flag "struttura insolita" (perplexity > 7.5) |
| Grammar rules | Fatto | Warning in candidate bar (3 regole) |
| Toggle Hanzi/Pinyin | Fatto | Tasto `字` sulla tastiera |
| Impostazioni | Fatto | Hanzi mode, accent mode, grammar |

## Componenti

### ToneNet — Rete Neurale Toni
`ToneNet.kt` · MLP a finestra ±5 sillabe, int8 quantizzato (~215KB).
```
Embedding(V×32) → Flatten(11×32=352) → FC 352×128 ReLU → FC 128×64 ReLU → FC 64×5 softmax
```
Forward pass puro Kotlin, pesi caricati da `assets/data/tone-net-data.js`.

### CorrectionEngine — correzione e accenti
`CorrectionEngine.kt` · Levenshtein (`Levenshtein.kt`) + bigrammi derivati dal
vocabolario HSK + filtro livello + indice parole. Espone `rankSuggestions`,
`findWordSuggestions`, `accentRun`, `accentChoice`, `toneVariants`, `toneSuspect`,
`pinyinLevel`, `segmentWords`. `HskData.kt` parsa `hsk-3.0.js` (10.974 parole).

### SentenceNet — Language Model forma
`SentenceNet.kt` · MLP predittore a finestra K=3 parole (~1.5MB).
```
Embedding(V×32) → Flatten(K×32) → FC 96×V logits
```
`scoreSentence(words)` → media `-log P`, flag se > 7.5.

### GrammarRules — verifica forma
`GrammarRules.kt` · 3 regole conservative su POS non ambigui (`hsk-pos.js`):
`很`+nome, `是`+verbo, `吗` non a fine frase.

## Asset

```
app/src/main/assets/data/
├── hsk-3.0.js        # vocabolario HSK 3.0 (10.974 parole, {w,p,l})
├── hsk-pos.js        # POS per le regole grammaticali (9.631 voci)
├── sentence-net.json # pesi SentenceNet (int8, 1.5MB)
├── tone-net-data.js  # pesi ToneNet (int8, 215KB)
└── words.txt         # dizionario parole (8.553 voci: compatto/toni/hanzi)
```

## Build & Sviluppo

```bash
# Requisiti PC
export JAVA_HOME=~/java/jdk-21.0.12+8   # Eclipse Temurin
export ANDROID_HOME=~/Android/Sdk          # API 26 + 34

# Build
cd ~/pinyn-ime
./gradlew assembleDebug

# Test
./gradlew testDebugUnitTest

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Logcat
adb logcat | grep -i pinyn
```

## Riferimenti

- Progetto originale Pinyn: `/home/kongiu/projects/Pinyn/`
- Android IME docs: https://developer.android.com/reference/android/inputmethodservice/InputMethodService
