Put App Store screenshots in `en-US/` (one folder per locale). deliver assigns each image
to a device slot by its pixel size:

- iPhone 6.9" (required): 1320x2868 or 1290x2796, portrait
- Apple Watch (required, the app includes a watch app): 422x514 (Ultra) or 416x496 (Series 10/11)

Bibless is iPhone-only, so no iPad screenshots are needed.

`bundle exec fastlane ios screenshots` captures them (and `android screenshots` the Play ones).
The phone store screenshots are built from the captured screens by `tools/screenshots/frame.py`,
as listed in `../captions.json`: their order, headlines and which screens each shows (one, two
cascading, or two split diagonally). Watch screenshots stay plain, in the order
of their file names, as on the phones: `01-qr`, `02-barcode`, `03-list`, `04-add`, `05-settings`.
Google Play's Wear OS set adds a square watch's `06-square-qr`, `07-square-barcode` and
`08-square-list`.
