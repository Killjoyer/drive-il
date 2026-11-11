# Теория вождения — Israeli driving theory trainer (Android)

Native Android app for preparing for the Israeli computerized driving theory test, built on the
**official question pool of the Ministry of Transport** (1,802 questions). Fully offline, portrait-only,
no server, no permissions. Content is Russian; the code and content pipeline are multi-language ready.

## Features

| Tab | What it does |
|---|---|
| **Тренировка** (practice) | Practice by topic (rules, safety, signs & markings, vehicle), all topics, or "work on mistakes". Progress bar = % of questions answered correctly at least once. Immediate feedback with the correct answer. |
| **Экзамен** (exam) | Mock test following the official format (below), with a question navigator, countdown, auto-finish on timeout, history, and a detailed per-question breakdown. |
| **Знаки** (signs) | All official Israeli road signs by category, in official numbering order, with Russian meanings; filter by category, search by number or text. |
| **Настройки** (settings) | Licence class (B, A, 1, C1, C, D), progress export/import to a JSON file, reset, sources & licences. |

Every picture (questions and signs) opens full-screen on a single tap, with pinch and double-tap zoom.

### Exam format (researched)

Per the Ministry of Transport / gov.il and consistent across secondary sources:

* **30 questions**, multiple choice, 4 options, exactly one correct;
* **40 minutes** for the whole test;
* **pass = at least 26 correct** (≤ 4 mistakes); unanswered questions count as mistakes;
* questions come from the official pool **for the candidate's licence class** (the pool tags every question with classes);
* you can move between questions and change answers until you submit.

The Ministry does **not** publish a per-topic quota. The app draws questions randomly *proportionally to
each topic's share of the licence-class pool* (largest-remainder rounding; for class B: 12 rules, 9 signs,
7 safety, 2 vehicle). This is an implementation choice and is stated in the in-app rules.
Option order is shuffled in both modes so answer positions can't be memorized.

### Practice selection

`domain/PracticeSelector.kt`: questions never answered correctly are preferred (ones already answered wrong
get extra weight); with 20 % probability — or always once everything is learned — an already learned
question is repeated, favouring shaky ones and ones not seen for a while; the last 10 questions are not
repeated. Every answer (practice and exam) updates per-question `correct`/`wrong` counters in Room.

## Architecture

Single-activity, Jetpack Compose, MVVM + unidirectional data flow, following the official
Android architecture guidance:

```
il.drivetheory
├── core/model        plain Kotlin domain models (Question, ExamRules, RoadSign, …)
├── domain            pure, unit-tested logic: ExamGenerator, PracticeSelector, LearningProgress
├── data
│   ├── content       bundled JSON assets → models (kotlinx.serialization), cached in memory
│   ├── progress      Room database: question stats, exam history (+ answers)
│   ├── settings      DataStore preferences: licence class, content language
│   └── backup        versioned export/import format via Storage Access Framework
├── di                Hilt modules
└── ui                Compose screens + ViewModels (StateFlow UI state), Material 3, light/dark theme
```

* Hilt DI, Navigation Compose with type-safe routes, Room, DataStore, coroutines/Flow.
* The running exam lives in `SavedStateHandle`: survives rotation **and process death**; the clock keeps
  running in the background like in the real test.
* No image library: a ~50-line asset loader with an LRU cache is enough for small bundled WebP files.
* R8 minification + resource shrinking for release. **Release APK ≈ 7.6 MB** (questions 1.4 MB JSON,
  443 question pictures 4.3 MB WebP, 245 sign pictures 2.4 MB WebP).
* Progress is never backed up automatically (`allowBackup=false`, data-extraction rules exclude everything);
  transfer it with Export/Import.

Tooling: AGP 9.5, Gradle 9.6 (wrapper), Kotlin 2.4, compile/target SDK 37, min SDK 26.

## Build

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # minified release APK (debug-signed unless keystore.properties exists)
./gradlew :app:testDebugUnitTest      # unit tests (domain logic, backup format, validation of bundled content)
./gradlew :app:lintDebug
```

Requires JDK 17+ and the Android SDK (`local.properties` → `sdk.dir`). For a properly signed release,
create `keystore.properties` in the project root with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

## Content pipeline

`tools/pipeline/build_content.py` regenerates everything under `app/src/main/assets`:

* questions: data.gov.il datastore API (same data as the gov.il "theoryexam*_data" collector, which is
  behind Cloudflare and can't be fetched by scripts); licence classes are parsed from each question;
* question pictures: gov.il (the per-language `tqpic.mot.gov.il` host in the dataset is offline;
  pictures are language-neutral), converted to WebP;
* signs: numbers/categories/Hebrew wording from the official sign table as listed on Hebrew Wikipedia
  (`sources/signs_master_he.json`), artwork from Wikimedia Commons (standard-size thumbnails),
  Russian meanings in `i18n/ru/signs.json`. Road markings are poorly covered on Commons, so the
  "what does this marking mean?" picture questions from the official pool are added to that category.

```bash
python3 -I tools/pipeline/build_content.py ru      # needs Pillow with WebP support
```

### Adding a language

1. `tools/pipeline/i18n/<lang>/config.json` — the data.gov.il resource id (Hebrew, English, Arabic, French,
   Spanish pools exist) and the mapping of that pool's category names to `RULES/SAFETY/SIGNS/VEHICLE`.
2. `tools/pipeline/i18n/<lang>/signs.json` — category titles and sign meanings (keys = sign numbers).
3. Run `build_content.py ru <lang>`; `BundledContentTest` checks the result is complete.
4. Optionally add `app/src/main/res/values-<lang>/strings.xml` for the UI.

The app discovers bundled languages automatically; a language picker appears in Settings as soon as
there is more than one.

## Known limitations

* **The official pool has no written explanations.** The exam breakdown shows the chosen answer, whether it
  was right, and the correct answer from the Ministry's key — it does not invent explanations.
* The per-topic split of the real exam is not published (see above).
* Sign meanings were translated from the official Hebrew table for this app; question texts are the
  Ministry's own Russian translation.

## Licences of bundled content

* Questions & question pictures: Israeli Ministry of Transport, published as open data on data.gov.il / gov.il.
* Sign artwork: Wikimedia Commons — public domain, plus CC BY-SA 3.0/4.0 works (attribution in the
  About screen; per-file metadata in `tools/pipeline/sources/commons_meta.json`).
