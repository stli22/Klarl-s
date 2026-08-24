# Klarl – AI-driven tillgänglighetsapp för Android (prototyp)

En prototyp av en Android-app som hjälper synskadade att navigera andra appar smartare än
traditionella skärmläsare: istället för att läsa upp all text linjärt läser Klarl skärmens
struktur, ber Claude sammanfatta den, och låter användaren navigera med röstkommandon och
följdfrågor. Se `SPEC.md`-innehållet (kravspecifikationen) i uppgiftsbeskrivningen för den
fullständiga bakgrunden – det här dokumentet beskriver vad som byggts och hur man kör det.

## Status

Alla åtta steg i den föreslagna arbetsordningen är implementerade som källkod:

1. ✅ Android-projektskelett (Gradle/Kotlin, minimal status-UI)
2. ✅ `AccessibilityService` som extraherar skärmstruktur
3. ✅ Maskering av känsliga fält, med enhetstester
4. ✅ Claude API-anrop för sammanfattning (strukturerat JSON-svar)
5. ✅ TTS-uppläsning av AI-svaret
6. ✅ `SpeechRecognizer` för röstkommandon, enkla kommandon tolkas lokalt
7. ✅ Komplexa/otydliga kommandon tolkas via Claude API mot senaste skärmstruktur
8. ✅ Bekräftelseflöde för känsliga/destruktiva handlingar

**Viktigt om verifiering:** den här sessionen kördes i en sandlåda utan Android SDK och utan
nätverksåtkomst till `dl.google.com` (Googles Maven-repo, där Android Gradle Plugin och
`androidx`-biblioteken ligger) – se `## Kända begränsningar i den här miljön` nedan. Hela
domänlogiken (extraktion, maskering, serialisering, lokal kommandotolkning,
bekräftelseklassificering) är däremot ren Kotlin utan Android-beroenden och har verifierats
genom att kompilera och köra dess 30 enhetstester direkt med Gradle + JUnit i den här miljön –
alla passerar. Android-lagret (`AccessibilityService`, UI, TTS/STT-wrappers) är skrivet mot
den officiella Android-API:t men har inte kunnat byggas till en APK här; bygg och testa det på
en dator med Android Studio/SDK innan ni litar på det i fält.

## Snabbstart

1. Öppna projektet i Android Studio (Koala/Ladybug eller senare), eller kör från kommandoraden
   med `./gradlew` när Android SDK finns installerat och `ANDROID_HOME`/`sdk.dir` är satt.
2. Kopiera `local.properties.example` till `local.properties` och fyll i:
   - `sdk.dir` – sökväg till din Android SDK
   - `CLAUDE_API_KEY` – din Claude API-nyckel (https://console.anthropic.com/settings/keys)
   - `CLAUDE_MODEL` – valfritt, standard är `claude-opus-5`
3. Bygg och installera appen på en enhet eller emulator (minSdk 26 / Android 8.0+).
4. Starta appen, tryck **"Öppna tillgänglighetsinställningar"** och aktivera *Klarl skärmguide*.
5. Ge appen mikrofonbehörighet via **"Ge mikrofonbehörighet"**.
6. Växla till valfri annan app – Klarl bör läsa upp en kort sammanfattning inom några sekunder
   och sedan lyssna efter ett kommando.

`local.properties` är gitignorad och checkas aldrig in.

## Arkitektur

```
ScreenReaderAccessibilityService   (tunn orkestrering, se nedan)
├── extraction/
│   ├── AccessibilityNode           – plattformsoberoende gränssnitt mot AccessibilityNodeInfo
│   ├── AndroidAccessibilityNodeAdapter – verklig implementation
│   ├── NodeExtractor               – trädvandring, brusfiltrering, cap på nodantal
│   └── SensitiveFieldMasker        – avgör & maskerar lösenord/betalningsfält FÖRE allt annat
├── model/                          – ScreenNode/ScreenSnapshot/VoiceCommand-datamodeller
├── serialization/
│   └── ScreenSnapshotSerializer    – kompakt indenterad textrepresentation för AI-prompten
├── ai/
│   ├── ClaudeConfig                – API-nyckel/modell från BuildConfig
│   ├── ClaudePromptBuilder         – system-prompts + JSON-scheman (structured outputs)
│   ├── ClaudeApiClient             – rå HTTPS mot /v1/messages (OkHttp + org.json)
│   └── CommandInterpreter          – snabb lokal kommandotolkning ("läs mer", "gå tillbaka", …)
├── confirmation/
│   ├── ActionRiskClassifier        – avgör om en handling kräver muntlig bekräftelse
│   └── ConfirmationManager         – tal-fråga + lyssna efter ja/nej
├── actions/
│   └── ScreenActionExecutor        – utför klick/fokus/tillbaka på den levande skärmen
├── voice/                          – TTS/STT-wrappers + mikrofon-aktiv-indikator
├── state/SessionState              – "senast kända skärm" i minnet, för uppföljningskommandon
└── ui/MainActivity, SettingsStore  – status-/inställningsskärm
```

Designprincipen genomgående: allt som *kan* testas utan en riktig Android-enhet (extraktion,
maskering, serialisering, lokal kommandotolkning, riskklassificering) är skrivet mot rena
Kotlin-gränssnitt utan `android.*`-beroenden, med enhetstester i `app/src/test/`. Det som
oundvikligen kräver en riktig `AccessibilityService`/`SpeechRecognizer`/`TextToSpeech`
(`ScreenReaderAccessibilityService`, `ScreenActionExecutor`, `Android*`-klasserna i `voice/`)
hålls tunt och delegerar till den testade logiken.

## Säkerhet och integritet

- **Maskering sker innan data lämnar enheten och innan den ens läggs i trädet som skickas till
  AI:t.** `SensitiveFieldMasker` körs i `NodeExtractor` för varje nod; om ett fält är
  lösenordsflaggat (`isPassword`) eller matchar nyckelord för lösenord/kort/PIN/personnummer
  (svenska och engelska) ersätts dess text och `contentDescription` med `null` innan ett
  `ScreenNode` ens skapas – den riktiga texten finns aldrig i minnet efter extraktionssteget.
  Se `NodeExtractorTest` och `SensitiveFieldMaskerTest`.
- **Maskerade fält läses aldrig upp och skickas aldrig i klartext.** Eftersom texten redan är
  `null` i `ScreenNode` finns inget att läsa upp eller serialisera – `ScreenSnapshotSerializer`
  skriver bara ut `[maskerat fält]`.
- **Mikrofonindikation:** `AndroidMicActivityIndicator` visar en statusfältsnotis och spelar en
  kort ton (`ToneGenerator`) varje gång lyssning startar/stoppar.
- **Bekräftelse för känsliga handlingar:** `ActionRiskClassifier` kräver bekräftelse om (a)
  Claude själv flaggar `requiresConfirmation: true`, eller (b) målet för en aktivering matchar
  nyckelord som "skicka", "radera", "köp", "betala" m.fl. – (a) kan aldrig stängas av lokalt,
  bara (b) kan slås av via inställningen "Kräv muntlig bekräftelse för känsliga åtgärder"
  (fail-closed by design, se `ActionRiskClassifierTest`).
- **API-nyckelhantering (prototypfas):** nyckeln läses från `local.properties` (gitignorad) och
  byggs in i `BuildConfig` vid kompilering – se `app/build.gradle.kts`. Det innebär att appen i
  sitt nuvarande skede anropar `api.anthropic.com` direkt från klienten, vilket **bara är
  avsett för lokal utveckling**. Innan produktion: byt ut `ClaudeApiClient` mot anrop till en
  egen backend-proxy som håller nyckeln server-side, exakt som kravspecen anger.

## Varför rå HTTPS (OkHttp) istället för `anthropic-java`?

Kotlin/Android-projekt pekas normalt mot den officiella `anthropic-java`-SDK:n. Den är dock
byggd för JVM-serverprocesser (Jackson-baserad, inte anpassad för Android/R8/APK-storlek), och
den här appen anropar Claude direkt från en Android-klient – ett mönster som redan bara är
tänkt för prototypfasen (se ovan). `ClaudeApiClient` använder därför OkHttp + `org.json` direkt
mot `/v1/messages`, med `output_config.format` (JSON-schema) för att garantera parsbara svar.
Den dagen anropen flyttas till en backend-proxy är den proxyn en naturlig plats att använda den
officiella SDK:n istället.

## Kända begränsningar i den här miljön

- **Kunde inte köra ett fullständigt Android-bygge här.** Sandlådans nätverksproxy blockerar
  `dl.google.com` (Googles Maven-repo, HTTP 403), vilket gör att Android Gradle Plugin och
  `androidx`-artefakterna inte går att hämta, och ingen Android SDK finns installerad. Bygg och
  kör projektet på en vanlig utvecklingsmaskin med Android Studio.
- **Domänlogiken är verifierad separat** genom att extrahera de rena Kotlin-filerna
  (`model/`, `extraction/`, `ai/CommandInterpreter`, `confirmation/ActionRiskClassifier`,
  `serialization/`) till ett fristående Gradle Kotlin/JVM-projekt och köra deras 30
  JUnit-tester – alla gröna. Det säkerställer att extraktion, maskering, serialisering, lokal
  kommandotolkning och riskklassificering fungerar som avsett; det säger inget om
  `AccessibilityService`-integrationen i sig, som bara kan verifieras på en riktig enhet.
- **Fönstertitel** hämtas bäst-effort via `AccessibilityWindowInfo.title` och kan vara `null`
  för många appar – det påverkar bara loggningen/prompten lite, inte funktionaliteten.
- **"Läs mer"** ber i den här MVP:n om en ny sammanfattning av samma skärmträd (Claude har redan
  hela trädet), snarare än en explicit "elaborera"-instruktion. En naturlig vidareutveckling är
  ett separat prompt-läge för fördjupning.
- Efter ett lokalt "gå tillbaka"-kommando börjar appen lyssna igen direkt, vilket kan krocka med
  att den nya skärmen (efter tillbaka-navigeringen) själv triggar en ny sammanfattning ~0,5 s
  senare. Fungerar men är inte optimerat för en sömlös övergång – en riktig statuscontroller för
  "vem äger just nu lyssningen" är ett bra nästa steg om det stör i praktiken.

## Tester

```
./gradlew test
```

kör enhetstesterna i `app/src/test/` (JUnit 4, ingen Robolectric/emulator behövs eftersom
domänlogiken är byggd mot rena Kotlin-gränssnitt).

## Avgränsningar (per kravspec, görs inte i denna prototyp)

- Ingen iOS-support
- Ingen offline-AI-modell – kräver molnanrop till Claude API
- Ersätter inte TalkBack, är tänkt som ett komplement
