# Store age-rating questionnaire answers

Prepared on 14 October 2026 from the current working tree. These are completed
answer drafts, not submissions to App Store Connect or Play Console. No connected
tool in this session can edit those consoles. Google wording below is a mapping
by topic: the live IARC form is conditional, and its exact questions have not been
inspected in an authenticated console.

## Scope and evidence

Assess the current native phone apps and their companion watches, including the
barcode wallet, stopwatch, finish scanner, joining a run by QR code, recorded
sessions, and CSV/JSON export through the system share sheet. The separate web
organiser creates runs and combines sessions into results. It is useful context
for the native race tools, but it is not an embedded website in either app.

Evidence:

- `app/iosApp/iosApp/ContentView.swift` and
  `app/androidApp/src/main/kotlin/com/github/nols1000/bibless/App.kt`: reachable
  timer, scanner, wallet and session screens.
- `app/iosApp/iosApp/JoinRunView.swift`: a QR code supplies run metadata.
- `app/sharedLogic/src/commonMain/kotlin/com/github/nols1000/bibless/SessionExport.kt`:
  exports include positions, times, athlete IDs, optional names and device names.
- `app/iosApp/iosApp/SessionsView.swift` and
  `app/androidApp/src/main/kotlin/com/github/nols1000/bibless/ShareFiles.kt`:
  user-initiated sharing to external apps.
- `app/androidApp/src/main/AndroidManifest.xml`: camera permission; no location
  permission in the source manifest.
- `server/src/main/kotlin/com/github/nols1000/bibless/Application.kt`: placeholder
  server, with no implemented public event/results service.

Future accounts, public feeds, runner profiles, public result hosting and volunteer
messaging are excluded. The source review does not establish which features are
already in the binary currently listed in each store.

## Apple: age-rating questionnaire

Use the current questionnaire for iOS 26 and later. Apple's categories and rating
thresholds are defined in its [age-rating reference](https://developer.apple.com/help/app-store-connect/reference/app-information/age-ratings-values-and-definitions/).

| Question / descriptor | Answer | Reason |
| --- | --- | --- |
| Parental controls | No | No parent-managed restrictions or permissions. Saving family barcodes is not a parental control. |
| Age assurance | No | No age check, verification or Declared Age Range integration. |
| Unrestricted web access | No | No browser or arbitrary webpage navigation within the app. Reading QR metadata does not open arbitrary web content. |
| User-generated content | No | User-entered names and run metadata are local or explicitly exchanged files; the app provides no broad distribution of user content. |
| Social media | No | No feed, discovery, likes, comments or amplification of user content. |
| Social media disabled for users under 13 | No / not applicable | No social media capability exists. |
| Messaging and chat | No | No in-app messaging or public posting. The system share sheet hands files to another app. |
| Advertising | No | No paid promotional placements or advertising SDKs. The parkrun association is the app's purpose, not a paid ad placement. |
| Profanity or crude humor | None | No authored offensive language or crude humor. |
| Horror / fear themes | None | No frightening content or storylines. |
| Alcohol, tobacco or drug use or references | None | No such content. |
| Medical or treatment information | None | No diagnosis, treatment, medication or medical guidance. |
| Health or wellness topics | No | No self-care, lifestyle, diet or exercise recommendations, or calorie tracking. Barcode storage and race timing support participation but provide no wellness guidance. |
| Mature or suggestive themes | None | No adult or sensitive narrative themes. |
| Sexual content or nudity | None | No such content. |
| Graphic sexual content and nudity | None | No such content. |
| Cartoon or fantasy violence | None | No violent depictions. |
| Realistic violence | None | No violent depictions. |
| Prolonged graphic or sadistic realistic violence | None | No violent depictions. |
| Guns or other weapons | None | No weapon references or depictions. |
| Gambling | No | No betting, wagers, lotteries or money-based chance games. |
| Simulated gambling | None | No simulated betting or casino mechanics. |
| Contests | Frequent | Race timing and finisher positions are permanent, repeatable features supporting sports rankings. Apple explicitly includes sport and fitness contests. |
| Loot boxes | No | No randomized purchasable rewards. |

**Expected global result: 13+**, driven by frequent contests. Regional ratings and
ratings on older OS versions may differ. This is a predicted questionnaire result,
not a verified assigned store rating.

The contest frequency is an interpretation: it describes a substantial recurring
feature, rather than how often an individual chooses to run. I would use Frequent
for the current race-support app. A barcode-wallet-only build needs its own
assessment and should not inherit this answer automatically.

The wellness answer follows Apple's content definition, not the store category
or the fact that running benefits fitness. Adding training suggestions, dieting
advice or lifestyle recommendations would change this answer; Apple associates
health/wellness topics with 9+.

For any final higher-age override, leave the calculated rating unless an actual
minimum-age policy applies. Do not elect the Kids category based solely on family
barcode storage. Neither code nor documentation establishes a developer-imposed
minimum age.

## Google Play: IARC content-rating questionnaire

Choose **All other app types** (or the equivalent non-game category). Bibless is a
utility for real-world runs, not a game, social network or news/reference app.
Use the existing publisher contact email for the questionnaire; no new contact
identity is inferred here.

| Question topic, if shown | Answer | Reason |
| --- | --- | --- |
| Violence, blood, gore or violent references | No | No such authored content. |
| Sexual content, nudity or sexual themes | No | No such content. |
| Offensive language / crude humor | No | No such authored content. |
| Drugs, alcohol or tobacco | No | No such content. |
| Gambling, simulated gambling or gambling promotion | No | Sports timing has no betting or gambling mechanics. |
| Horror / frightening content | No | No such content. |
| Other age-restricted goods or activities | No | No restricted-product sales or promotion. |
| In-app purchases / digital goods | No | No billing, subscriptions, purchases or randomized purchases. |
| Sharing users' current physical location | No | No GPS tracking or user-location sharing. A run's name/date is not a user's live location. |
| Web browser / search engine | No | Neither capability is implemented. |
| Primarily news or educational material | No | Primarily a barcode and race-day utility. |
| In-app chat, social network, or public user-content distribution | No | No messaging service, feed or public posting within the native apps. |
| Ability to share personal information or user-created content with others, including external sharing | Yes | Users can export athlete IDs, optional names and session records through the system share sheet. |
| Sport competitions, if explicitly asked | Yes | The timer and scanner support timed runs and finishing order. Answer any gambling follow-up No. |

**User-interaction wording matters.** If the live question combines exchanging
content or personal information with communication between users, answer **Yes**
because session export supports exchanging that information. If it explicitly
limits the question to built-in chat or public posting, answer **No**. Do not
answer a broad sharing question No merely because the app is offline. Follow-up
questions about public profiles, stranger contact, blocking/reporting, moderation,
or online chat should reflect that those facilities do not exist; do not invent
controls to satisfy a branch.

The above table is a complete topic-based draft for the reviewed functionality,
not a claim to reproduce every conditional question in the live form. A new
question or materially different helper text needs to be matched against the
facts above before submission.

**Expected content rating: Everyone / PEGI 3 / USK 0**, subject to the actual IARC
output and any interactive-element descriptors triggered by sharing. Google's
system does not simply copy Apple's 13+ contest threshold. Record the generated
regional results rather than manually claiming a universal rating.

Google explains its [content-rating questionnaire and regional ratings](https://support.google.com/googleplay/android-developer/answer/9859655?hl=en-EN);
[IARC describes the rating process](https://globalratings.iarcservices.com/faq/).

## Separate declarations and current documentation mismatch

These answers cover age/content ratings. Google target-audience age groups,
Families eligibility, health-app declarations, Data safety, and Apple's privacy
declarations are separate forms. An all-ages content rating does not establish
that the product was designed for every child age group.

Both current privacy pages say the app does not use the camera and that everything
stays on the user's devices. That no longer describes the scanner and voluntary
session exports. The store descriptions also omit race tools. Those documents
should be updated for a release containing the reviewed functionality; their
older statements were not used to override the implemented behavior here.
