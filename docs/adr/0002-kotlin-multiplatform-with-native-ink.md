# Kotlin Multiplatform, with a native ink canvas per platform

The app must run on both Android (Galaxy Tab, S Pen) and iPad (Apple Pencil). We use Kotlin Multiplatform with Compose Multiplatform for the shared UI and logic, and embed a native ink canvas on each platform: the Android Ink API on Android, and PencilKit or a Metal canvas on iOS. Pen feel is the product, and the low-latency paths (front-buffered rendering, predicted strokes, palm rejection, stylus buttons) exist only in each platform's native APIs. Frameworks that draw ink through their own renderer (Flutter, React Native, .NET MAUI) can't reach them, and we'd have to write ink rendering from scratch.

V1.0 ships on Android only. The code is cross-platform from day one, and iPad follows after V1.0.

## Considered options

- **Flutter:** mature cross-platform UI, but ink goes through Flutter's renderer with no front-buffer path, and stylus button support is basic.
- **React Native:** same rendering problem, plus a JS bridge in the input path.
- **.NET MAUI:** thin stylus support.
- **Two native apps:** best pen feel, but the Annotation model, undo, storage and staff-line detection would all be written twice.

## Consequences

- The Annotation model, undo stack, storage and staff-line detection live in shared Kotlin code. Ink strokes are stored in a shared format, not the Android Ink API's own types, so the iOS canvas can read the same data.
- Each platform implements the ink canvas, PDF page rendering (Android `PdfRenderer`, iOS PDFKit), the file picker and the mapping from pen hardware to pen actions. "Erase while held" is a pen action. The S Pen side button triggers it on Android; the Apple Pencil trigger is decided at the iPad port.
- Compose Multiplatform for iOS is stable (since 1.8.0), but some of its native-view embedding API (`UIKitView`) is still experimental. Check it again before the iPad port.
