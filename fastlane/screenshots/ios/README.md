Put App Store screenshots in `en-US/` (one folder per locale). deliver assigns each image
to a device slot by its pixel size:

- iPhone 6.9" (required): 1320x2868 or 1290x2796, portrait
- Apple Watch (required, the app includes a watch app): 422x514 (Ultra) or 416x496 (Series 10/11)

Bibless is iPhone-only, so no iPad screenshots are needed.

`bundle exec fastlane ios screenshots` captures them (and `android screenshots` the Play ones).
The screens are uploaded as captured, with no headlines or frames, in the order of their file
names. iPhone and Android phones: `01-barcode`, `02-barcode-code128`, `03-barcode-dark`, `04-list`,
`05-settings`. Watches: `01-qr`, `02-barcode`, `03-list`, `04-add`, `05-settings`.
