# Community runs

How Bibless grows from a barcode wallet into a tool for organising free, timed community runs.
The epic is [#37](https://github.com/Nols1000/Bibless/issues/37); this page holds the decisions
that span its stories.

## Who uses what

- **Organisers** use the **web app**: set up a run, choose how it is timed, print finish tokens,
  combine everyone's data into results.
- **Timekeepers and scanner volunteers** use the **phone and watch apps**: stopwatch, barcode
  scanner, export.
- **Runners** keep using the barcode wallet; later they get Bibless athlete IDs and a results
  history.

## Timing modes

- **Tokens:** the timer records position → time; scanners pair an athlete barcode with a numbered
  finish token. The most robust option, but needs printed tokens.
- **Scan order:** no tokens. Scanners record athletes in the order they leave the funnel, and the
  scan index is the position. Simpler to run, but a missed or swapped scan shifts everyone behind
  it, so the scanner offers "insert unknown finisher" and the review screen makes reordering easy.

Runs have **open attendance**: anyone can turn up, with no registration list and no fixed set
of tokens. Any athlete ID and any token number is accepted.

## Architecture

The race-day model lives in `core/.../race`, which reaches every target, including the server.

1. **Event sourcing.** A run is the sum of its events: `CreatedEvent` (the organiser creates the
   run: name and timing mode), `StartEvent` (a timer or scanner session starts), `RecordTimeEvent`,
   `RemoveTimeEvent`, `RecordAthleteEvent` (athlete ID and token, both optional) and
   `RemoveAthleteEvent`. Events are never changed; mistakes are undone with `Remove…` events, and
   the organiser fixes data by adding events of their own. Positions aren't stored: they follow
   from the order of the times.
2. **Every event is signed by its author.** Each device has an ECDSA P-256 key; its public key
   identifies the author. Each session is a chain: entries are numbered and point to the previous
   entry's hash, so a changed, missing or duplicated entry shows. Signatures cover the exact JSON
   bytes, which are never re-encoded.
3. **Trust by certificates.** Devices record into their own chains without knowing about the run.
   When the organiser merges their data, they sign a certificate per chain: "this author's chain,
   up to this entry, belongs to my run". Volunteers' events keep only their own signatures, and
   data recorded before joining a run needs nothing extra. Later entries need a newer certificate.
4. **Run result.** The organiser signs the results together with the certificates and the timer
   session they were computed from, and publishes them with the merged log (`RunLog`). Anyone can
   recompute the results from the log and check that they match (`verifyRunResult`).
5. **Platform-native crypto, no library.** java.security on the JVM and Android, the Security
   framework on iOS and watchOS, WebCrypto in the browser. P-256 is the one signature scheme all of
   them have, and it allows hardware-backed keys later.
6. **Before the server:** the organiser creates the run in the browser and shows a run QR code
   (`RunHint`: run ID, organiser key, name, timing mode); volunteers scan it to join. Data comes
   back as a `RunLog` file (lossless) or CSV (for people). Once there is a server, invite links
   (#34) carry the same hint and uploads (#28) send the same signed events.
7. **On device:** a repository in `sharedLogic`, built like `BarcodeRepository`, signs and persists
   every event immediately. Finish times are the session start plus elapsed time from a monotonic
   clock, so changing the device clock mid-run can't move them. The monotonic clock starts over
   when the phone restarts (told apart by Android's boot count and Apple's boot session UUID); the
   timer then carries on from the wall clock.
8. **Athlete IDs:** scanners accept parkrun `A…` IDs from day one; Bibless IDs (#31) get a distinct
   prefix.

### Threat model

- Signatures and chains make changes in transit, and after the fact, visible, and they tie
  published results to exactly the data behind them.
- A device can still record false data; the organiser vouches for a chain by certifying it.
- In Phase 1 keys are stored like other app data. The organiser's key lives in the browser's local
  storage (`RunRepository`), so a run is organised from the browser that created it. Accounts take
  over the root of trust in Phase 2.

## Phases

Each phase is useful on its own.

1. **Race day, offline, no server.** Shared model → run setup in the browser → phone stopwatch →
   join a run by QR → scanner (tokens) → scan-order mode → finishers without a barcode → export →
   combine results in the browser → print tokens → watch timer. Done when a real run has been timed
   with two phones and a laptop.
2. **Server: events and results.** Sign-in, events with a schedule, uploads, review and publish in
   the browser, public results pages.
3. **Runners.** Bibless athlete IDs, hiding and deleting results, results history.
4. **Open organising.** Volunteer invites and roles, finding events, reporting and moderation.

### Gates

- Runners must be able to hide their name (#33), and the privacy policy must cover results,
  before results pages (#30) are public beyond test events.
- Reporting and moderation (#36) must exist before anyone can create events.
