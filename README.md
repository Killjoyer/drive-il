# Driving Theory — Israeli driving theory trainer (Android)

Native Android app for preparing for the Israeli computerized driving theory test, built on the
**official question pool of the Ministry of Transport** (≈1,800 questions). Fully offline, portrait-only,
no server, no permissions. English (default) and Russian, switchable in Settings; anything missing in the
selected language falls back to English.

## Features

| Tab | What it does |
|---|---|
| **Practice** | Practice by topic (rules, safety, signs & markings, vehicle), all topics, or "work on mistakes". Progress bar = % of questions answered correctly at least once. Immediate feedback with the correct answer. |
| **Exam** | Mock test following the official format (below), with a question navigator, countdown, auto-finish on timeout, history, and a detailed per-question breakdown. |
| **Signs** | All official Israeli road signs by category, in official numbering order, with their meanings; filter by category, search by number or text. |
| **Settings** | Language, licence class (B, A, 1, C1, C, D), progress export/import to a JSON file, reset, sources, licences and image credits. |

Every picture (questions and signs) opens full-screen on a single tap, with pinch and double-tap zoom.

### Exam format (researched)

Per the Ministry of Transport / gov.il and consistent across secondary sources:

* **30 questions**, multiple choice, 4 options, exactly one correct;
* **40 minutes** for the whole test;
* **pass = at least 26 correct** (≤ 4 mistakes); unanswered questions count as mistakes;
* questions come from the official pool **for the candidate's licence class** (the pool tags every question with classes);
* you can move between questions and change answers until you submit.

Deliberate deviations from the real test: the clock only runs while the exam is on screen (it pauses when
the phone is locked or the app is in the background), and an exam interrupted by closing the app is
discarded rather than resumed — it never touches the per-question statistics, which are only updated
when an exam is finished.

The Ministry does **not** publish a per-topic quota. The app draws questions randomly *proportionally to
each topic's share of the licence-class pool* (largest-remainder rounding; for class B: 12 rules, 9 signs,
7 safety, 2 vehicle). This is an implementation choice and is stated in the in-app rules.
Option order is shuffled in both modes so answer positions can't be memorized.

### Practice selection

`domain/PracticeSelector.kt`: questions never answered correctly are preferred (ones already answered wrong
get extra weight); with 20 % probability — or always once everything is learned — an already learned
question is repeated, favouring shaky ones and ones not seen for a while; the last 10 questions are not
repeated. Every practice answer and every finished exam updates per-question `correct`/`wrong` counters in Room.

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
│   ├── settings      DataStore (licence class) + app language (SharedPreferences, read in attachBaseContext)
│   └── backup        versioned export/import format via Storage Access Framework
├── di                Hilt modules
└── ui                Compose screens + ViewModels (StateFlow UI state), Material 3, light/dark theme
```

* Hilt DI, Navigation Compose with type-safe routes, Room, DataStore, coroutines/Flow.
* The running exam lives in its ViewModel (survives rotation). After process death it is reported as
  interrupted instead of being restored.
* The app language overrides the activity locale (`applyOverrideConfiguration`), so UI strings, questions
  and sign texts always match; English `res/values` is the default and the fallback for other locales.
* No image library: a ~50-line asset loader with an LRU cache is enough for small bundled WebP files.
* R8 minification + resource shrinking for release. **Release APK ≈ 7.9 MB** (questions: English 0.7 MB +
  Russian 1.3 MB JSON, 444 question pictures 4.3 MB WebP, 255 sign pictures 2.4 MB WebP).
* Progress is never backed up automatically (`allowBackup=false`, data-extraction rules exclude everything);
  transfer it with Export/Import. Imports larger than 10 MB are rejected before parsing (a real export is a
  few hundred KB).

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
  meanings in `i18n/<lang>/signs.json`, attribution (title, author, source, licence of every Commons file)
  fetched from the Commons API into `sources/commons_credits.json`. Road markings are poorly covered on Commons, so the
  "what does this marking mean?" picture questions from the official pool are added to that category.

```bash
python3 -I tools/pipeline/build_content.py en ru   # needs Pillow with WebP support
```

### Adding a language

1. `tools/pipeline/i18n/<lang>/config.json` — the data.gov.il resource id (Hebrew, English, Arabic, French,
   Spanish pools exist) and the mapping of that pool's category names to `RULES/SAFETY/SIGNS/VEHICLE`.
2. `tools/pipeline/i18n/<lang>/signs.json` — category titles and sign meanings (keys = sign numbers).
   Missing entries are fine: the app shows the English text for them (English itself must be complete).
3. Run `build_content.py en ru <lang>`; `BundledContentTest` checks the result.
4. Add `app/src/main/res/values-<lang>/strings.xml` for the UI (missing strings fall back to English).

The app discovers bundled languages automatically and lists them in Settings.

## Known limitations

* **The official pool has no written explanations.** The exam breakdown shows the chosen answer, whether it
  was right, and the correct answer from the Ministry's key — it does not invent explanations.
* The per-topic split of the real exam is not published (see above).
* Sign meanings were translated from the official Hebrew table for this app; question texts are the
  Ministry's own English and Russian translations.

## Licences of bundled content

* Questions & question pictures: Israeli Ministry of Transport, published as open data on data.gov.il / gov.il.
* Sign artwork: Wikimedia Commons — public domain, plus CC BY-SA 3.0/4.0 works. Attribution follows the
  Creative Commons [recommended practices](https://wiki.creativecommons.org/wiki/Recommended_practices_for_attribution)
  (title → file page, author → profile, licence → deed): a caption on every enlarged sign picture, plus an
  "Image credits" screen (Settings → About, or the link at the end of the Signs list) listing every image
  and noting the modification (SVG rasterised to 330 px WebP; modified CC BY-SA images stay under the same
  licence). Data: `tools/pipeline/sources/commons_credits.json` → `assets/content/signs.json`.
