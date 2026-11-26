# ViolinSheet Spec

A sheet music app for a violinist playing in an orchestra. Capitalised terms (Sheet, Ink, Symbol, Cover, Text box…) are defined in [`GLOSSARY.md`](../GLOSSARY.md).

**Platforms:** the app is cross-platform ([ADR 0002](adr/0002-kotlin-multiplatform-with-native-ink.md)). V1.0 ships on the **Samsung Galaxy Tab S9+ (Android 13+)** with the S Pen. iPad with Apple Pencil follows after V1.0.

There are four features. Feature 1 is the main one and takes precedence; it is all of V1.0.

| # | Feature | Release |
|---|---|---|
| 1 | Annotation editing | V1.0 |
| 2 | Sheet fixing | Post-V1.0 |
| 3 | Automatic page flipping | Post-V1.0 |
| 4 | Metronome | Post-V1.0 |

Lower-priority ideas are listed under [Nice-to-have](#nice-to-have). They are built only once the app is stable after deploying.

## Feature 1: Annotation editing (V1.0)

Mark up a Sheet with the pen. Printed-look edits should look like they were printed, not like handwriting.

### Sheets and reading

- Sheets are imported as **PDF only**, via the system file picker, and copied into app storage. The imported PDF is never modified ([ADR 0001](adr/0001-pdf-pages-with-overlay-annotations.md)).
- A new Sheet is named after the file, without ".pdf". Importing the same PDF again makes a separate Sheet with its own Annotations, named "\<name\> (2)", unless a Sheet with that file is waiting for its PDF after a restore (see backup below).
- Home screen: a flat list of Sheets with rename and delete. Delete shows an "Undo" bar for about 10 seconds before the Sheet and its Annotations are removed.
- Sheet records (name, file hash, per-Sheet choices), Annotations and Settings are covered by Android's built-in backup. Imported PDFs are not (the backup is capped at 25 MB per app). After a restore, a Sheet whose PDF is missing shows "PDF missing, re-import"; re-importing the same file, recognised by a hash of its contents, reattaches its Annotations. Re-importing from one Sheet's "PDF missing" prompt fixes that Sheet; a normal import from the home screen fixes every missing Sheet with that hash instead of creating a new Sheet.
- Layout follows rotation: one Page in portrait, two Pages side by side in landscape. In landscape a turn advances two Pages; a per-Sheet "advance by one" toggle makes it advance one.
- The next Page (or spread) is pre-rendered so page turns are instant.
- The **pen** draws and edits. A hand resting on the screen while the pen is near is a palm and does nothing. **Fingers** only navigate: swipe or tap the edge to turn the page, pinch to zoom, tap a Link to open it. When zoomed in, a swipe pans; an edge tap, or a deliberate swipe past the Page edge (starting with the edge already on screen), turns the page. A turn keeps the zoom and its height on the Page and lands on the opposite side: the left edge going forward, the right edge going back. A rotation returns to fit.

### Modes

There are three modes: **Ink mode**, **Edit mode** and **Text mode**.

### Ink mode

Freehand Ink, kept as drawn.

| Tool | Colours | Size |
|---|---|---|
| Pen | Presets black, blue, red, green + custom colour picker | Presets + slider, 0.5–10 pt |
| Highlighter | Presets yellow, green, pink + custom colour picker | Presets + slider, 4–30 pt |
| Eraser | — (area and stroke modes; removes Ink only) | Presets + slider, 2–40 pt |
| Lasso | — | — |

- Sizes are measured on the Page, not the screen, so a stroke keeps its thickness relative to the notes at any zoom.
- **Holding the S Pen side button** erases while held; releasing it returns to the previous tool. On iPad the trigger is decided at the port (the Apple Pencil has no side button).
- The custom colour picker opens in a panel above the toolbar, never a dialog. One button at the end of the toolbar opens and closes the panel; it stays open while drawing, and switching tool shows that tool's controls in it. The panel holds the picker (a saturation/brightness square and a hue strip) and the size slider; for the eraser, only the slider.
- A custom highlighter colour keeps the highlighter's translucency: the picker sets the hue, not the opacity.

**Lasso:** circle strokes with the pen; a stroke is selected if most of it is inside the lasso. The selection can be:

- **moved** by dragging,
- **resized** with corner handles (stroke thickness scales with it),
- **rotated** with a rotate handle (free, snapping at 0°, 90° and 180°),
- **copied** and **pasted** on any Page of the same Sheet, at the same position and size in Page units, already selected. Strokes that would fall off the Page are moved inside its edge. Pasting into another Sheet is Post-V1.0.
- **deleted** from the toolbar.

### Edit mode

Printed-look Symbols, and Covers over Printed markings.

**Symbols**, built in this order:

1. **Fingerings:** 0, 1, 2, 3, 4
2. **Articulations:** tenuto, staccato, accent, fermata
3. **Bowings:** up-bow, down-bow, retake
4. **Dynamic letters:** pp, p, mp, mf, f, ff, sfz, fp
5. **Ties/slurs:** tap start, tap end; one handle sets arch height and flips above/below
6. **Hairpins:** crescendo and decrescendo; tap start, tap end; one handle sets the opening

Text dynamics ("cresc.", "dim.", "subito") are written as Ink or a Text box.

**Placing:** pick a Symbol in the palette, tap to place, see a ghost preview, confirm.

**Changing:** tap a Symbol or Cover to select it; drag to move, handles to resize, delete button in the toolbar. The S Pen side button does nothing in Edit mode.

**Eraser:** an Edit mode eraser removes a whole Symbol when it touches any part of it. It never removes Covers (select and delete those), and it has no area mode.

**Colour:** every Symbol starts in the default Symbol colour Setting (black unless changed), with black and blue presets and the custom colour picker.

**Size:** the Symbol scale is detected per Page from the staff-line spacing. A per-Sheet slider adjusts it, and a single Symbol can be resized. Pages with no detectable staff (title pages, bad scans) use a standard size.

**Covers:** drag a rectangle over a Printed marking, then adjust with handles. The fill colour is sampled from the paper just outside the rectangle. To change a printed tie or bowing, cover it and place a new Symbol.

### Text mode

Typed, printed-look text in Text boxes.

- **Placing:** tap to place a Text box, then type with the on-screen keyboard (handwriting-to-text keyboards work too).
- **Font:** a printed-look serif in **italic** (for "pizz.", "cresc.") or **regular** (for plain notes such as "breathe"). One font per Text box; no mixed styling inside a box.
- **Colour:** same presets and custom colour picker as Symbols.
- **Size:** defaults from the Symbol scale, with a size slider.
- **Changing:** tap a Text box to select it; drag to move, handles set the box width (text wraps inside; the size slider sets the font size, and the box grows in height to fit), delete button in the toolbar. Double-tap to edit the words. There is no eraser in Text mode, and the S Pen side button does nothing.
- **Links:** a "Link" button on a selected Text box takes a web URL. The whole box becomes the Link and is drawn underlined. A **finger** tap on a Link shows an "Open \<site\>?" bar above the toolbar (not a dialog); confirming opens the browser. A **pen** tap only selects it. A finger tap on a Link always opens it, even inside the page-turn edge area.

### Layers, undo and saving

- There are three layers, each with a show/hide toggle: **Ink**, **Edit** and **Text**. Covers sit under Symbols on the Edit layer, so hiding it brings back the Printed markings under the Covers. Hiding all three shows the Sheet exactly as printed.
- Stacking, bottom to top: Page → Covers → Symbols → Text boxes → Ink. The highlighter blends (multiply) so the notes under it stay sharp.
- Switching to a mode shows its layer if it was hidden. Layer toggles are remembered per Sheet.
- Each Sheet has its own undo/redo history, covering Ink (including lasso actions), Symbols, Covers and Text boxes. It lasts while the Sheet is open and is cleared on close.
- Saving is automatic. There is no Save button.

### Settings

A full page opened from a gear icon on the home screen. It holds app-wide Settings: the defaults every Sheet starts with. A change in a Sheet's toolbar lasts only while that Sheet is open, and per-Sheet choices (layer toggles, "advance by one", Symbol scale slider) stay in the Sheet. The eraser has no Setting: its last mode and size carry over to the next Sheet while the app runs.

- Each feature declares its own Settings (name, type, default). The page is built from those declarations, grouped by feature, and the type picks the control (toggle, slider, choice, colour). A colour control shows its presets and the same custom colour picker as the toolbar.
- Changes are saved instantly and covered by Android backup. There is no user-editable config file.
- V1.0 Settings:
  - **Reading:** landscape turns advance two Pages or one.
  - **Ink:** default pen colour and size; default highlighter colour and size.
  - **Edit:** default Symbol colour.
  - **Text:** default font (italic or regular).
  - **Links:** ask "Open \<site\>?" before opening (on by default), or open straight away.

### Post-V1.0

- Export and import of Settings.
- **Sharps/flats/naturals** as Symbols, with spacing adjustment. Making room may need notes or barlines moved, which needs the music data below.
- Pasting Ink into another Sheet.
- **Handwriting to Symbols:** write a marking by hand and have it converted to a printed-look Symbol, possibly using a small on-device model.
- **Gesture Covers:** a scribble or cross over a Printed marking, or the side button in Edit mode, applies a Cover automatically. Which gesture is undecided.
- Free-form Cover shapes.
- **Erasing Printed markings:** the Edit mode eraser also removes Printed markings it touches, by covering them automatically. Related to Gesture Covers.
- **Automatic bar counting:** detect barlines and number every bar.
- **Music data:** notes and barlines as a hidden layer the app reads but never displays, from MusicXML import and/or optical music recognition (OMR) on the PDF. Needed for bar counting, sharps/flats spacing and playback. Decided together with Feature 3.
- Export a Sheet with its Annotations as a flattened PDF.
- Folders and set lists.

## Feature 2: Sheet fixing (Post-V1.0)

Scanned Pages may be skewed, discoloured, or printed small.

- Apply perspective correction and mesh warp to straighten Pages, and fill discoloured areas. Existing Annotations must move with the Page.
- **Crop and enlarge:** a saved crop per Page that trims margins and empty space so the music fills more of the screen, with "apply to all Pages". The PDF is not changed and Annotations stay in the original Page coordinates, so removing a crop loses nothing.

## Feature 3: Automatic page flipping (Post-V1.0)

- Listen through the microphone and turn the page at the right time. The music may come from the whole orchestra, not just the violin. The approach (following notes from music data, or aligning to an imported recording) is decided when this feature is grilled.
- Accept page-turn signals (left/right) from a Bluetooth device, custom or off-the-shelf.

## Feature 4: Metronome (Post-V1.0)

A built-in metronome.

## Nice-to-have

Built only once the app is stable after deploying. Specifics are not decided yet.

- **Page navigation:** partial page flipping (the upper half turns first), coda jumps integrated with page flipping, reflow (detect each system and lay systems out again, larger or continuous across Page breaks).
- **Page editing:** page deletion, merging Pages from several Sheets into one Sheet.
- **Annotation extras:** custom Symbols, note-name Symbols (F#, G…), handwriting beautification (smooth jagged edges), lasso selection in Edit mode.
- **Playback and audio:** MIDI-like playback of selected bars (optionally looping, with or without delays) or the whole Sheet, with or without a synced metronome; imported audio playing in the background with a red line showing progress; built-in tuner.
- **Recording:** front-camera recording for practice and concerts, playable in the app, showing the battery level when low.
- **Practice:** practice streak.
- **Appearance:** custom background around the Page, including an imported image with adjustable opacity.
- **Ensemble:** conductor score integration with the other features.
