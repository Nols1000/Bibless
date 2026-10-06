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

## Architecture

1. **One race-day model, in `core`.** `core` reaches every target, including the server.
   `RunSetup` (run ID, name, date, timing mode, token range) is serialisable and fits in a QR code.
2. **Append-only records with IDs.** Finish taps and scans have their own IDs and are undone with
   tombstones, the same approach as `Barcode` and `mergeBarcodes`. Merging devices, re-importing
   files and re-uploading are all "union by ID", so they can't create duplicates.
3. **Combining is a pure function** in `core` (`setup + taps + scans → results + issues`), shared by
   the phones, the web app and the server.
4. **Before the server:** the organiser creates the run in the browser (stored locally) and shows a
   run QR code; volunteers scan it to join. Data comes back as a JSON bundle (lossless) or CSV (for
   people). Once there is a server, invite links (#34) carry the same payload and uploads (#28)
   send the same records.
5. **On device:** a `RaceRepository` in `sharedLogic`, built like `BarcodeRepository`, persists
   every tap immediately. Elapsed time comes from a monotonic clock anchored to the stored start.
6. **Athlete IDs:** scanners accept parkrun `A…` IDs from day one; Bibless IDs (#31) get a distinct
   prefix.

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
