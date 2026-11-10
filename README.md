# Blake Editor

Mobile-first text and code editor for Android with Markdown Preview.

## Build
From GitHub Actions, open **Build Debug APK** and run the workflow manually.
Pull requests also run unit tests and build a debug APK.

## Blake Editor 2.0 (in progress)
The existing app is retained while the rebuild is developed in reviewable steps.
See [the V2 foundation and phone tests](docs/BLAKE_EDITOR_V2_FOUNDATION.md).

Debug APKs built from the V2 foundation branch have an additional launcher icon
called **Blake Input Lab**. It tests native Android text input and Thai cursor
positioning. It is a diagnostic screen only: it does not open or save files.
