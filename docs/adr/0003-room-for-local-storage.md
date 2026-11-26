# Room (KMP) for local storage

Sheet records, and later Annotations and Settings, are stored with Room in a SQLite database in `:core:data`'s `commonMain`. Room is Google's SQLite library and has supported Kotlin Multiplatform (including iOS) since 2.7, so the same entities and DAOs serve the iPad port. It gives schema migrations, typed queries and `Flow` results, which the ViewModels turn into `StateFlow`.

## Considered options

- **SQLDelight:** mature on KMP and SQL-first, but similar weight to Room; Room is the platform default on Android and has first-party support.
- **JSON files (kotlinx.serialization):** least setup, but no migrations or queries, and each save rewrites a whole file. Annotation editing saves often.

## Consequences

- The build uses KSP to generate Room code. KSP does not run for iOS targets on a non-macOS host, so the iOS targets are only declared when building on macOS (`buildSrc/.../violinsheet.kmp-library.gradle.kts`).
- The schema is exported to `core/data/schemas/`; keep it in git so migrations can be written and tested against old versions.
- The database lives in the app's `databases/` folder, which Android's built-in backup covers. Imported PDFs live in `noBackupFilesDir` and are not backed up (SPECS: Sheets and reading).
- On Android, Room uses the platform SQLite. The iOS port must set a driver (`BundledSQLiteDriver`).
