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
- Layout follows rotation: one Page in portrait, two Pages side by side in landscape. In landscape a turn advances two Pages, or one if the Reading Setting says so. V1.0 has no per-Sheet toggle for it; per-Sheet (and per-Page) settings are Post-V1.0.
- The next Page (or spread) is pre-rendered so page turns are instant.
- The **pen** draws and edits. A hand resting on the screen while the pen is near (or was, within 0.15 s) is a palm and does nothing. A resting hand is also kept from navigating when the pen is away: an edge tap turns only if the finger lifts within 0.25 s, a swipe only if it is a flick done within 0.4 s, and two fingers zoom or pan only once their spread changes by more than about 15 % or their centre moves more than about 24 dp. **Fingers** only navigate: swipe or tap the edge to turn the page, pinch to zoom (two fingers also pan), tap a Link to open it. When zoomed in, a swipe pans; an edge tap, or a deliberate flick (within 0.4 s) past the Page edge (starting with the edge already on screen), turns the page. A turn keeps the zoom and its height on the Page and lands on the opposite side: the left edge going forward, the right edge going back. A rotation returns to fit.

### Modes

There are three modes: **Ink mode**, **Edit mode** and **Text mode**.

**Toolbar across modes:** one slim bar at the bottom edge, the same in every mode: the **mode switch** (a segmented control with an icon each: a pen for Ink, a music note for Edit, a serif T for Text; one tap changes mode; a Sheet always opens in Ink mode), Undo and Redo, the mode's tools and its slot, then a **Layers** button at the right end. The bar has the same width and position in all three modes, sized for Edit mode's (the widest), so the mode switch, Undo, Redo and Layers never move; Ink and Text leave empty space in their slot. Where the bar doesn't fit the screen (portrait), the slot scrolls sideways and the rest of the bar stays put.

### Ink mode

Freehand Ink, kept as drawn (trimmed to its Page).

| Tool | Colours | Size |
|---|---|---|
| Pen | Palette (starts black, blue, red, green) + Add colour | 3 size presets + slider, 0.5–10 pt |
| Highlighter | Palette (starts yellow, green, pink) + Add colour | 3 size presets + slider, 4–30 pt |
| Eraser | — (area and stroke modes; removes Ink only) | 3 size presets + slider, 2–40 pt |
| Lasso | — | — |

- Sizes are measured on the Page, not the screen, so a stroke keeps its thickness relative to the notes at any zoom.
- A stroke belongs to one Page. A stroke drawn across into the other Page of a spread is split at the edge when the pen lifts, into one stroke per Page (one undo step); the parts outside any Page (the surround, the gap between Pages) are trimmed. A stroke that leaves a Page and comes back becomes separate strokes, and one that starts off any Page keeps its parts that are on a Page. While drawing, the whole line shows.
- **Holding the S Pen side button** erases while held; releasing it returns to the previous tool. On iPad the trigger is decided at the port (the Apple Pencil has no side button).
- **The toolbar** keeps one width and position for every tool: buttons never move under the pen.
- **The panel** above the toolbar (never a dialog) holds either the colour picker (a saturation/brightness square and a hue strip, with the tool's factory colours under it, and **Cancel** and **Save**) or a size preset's slider. It opens three ways: the **+ Add colour** button at the end of the palette (Save adds the colour to the end of the tool's palette and selects it), tapping the selected colour (edits that palette colour: Save replaces it app-wide; strokes already drawn keep their colour), or tapping the selected size preset (see Size presets). There is no separate "Colour and size" button. The panel shows no text other than its buttons (Cancel, Save, Delete). A pen touch outside the panel, or a finger tap there that lifts within 0.25 s, closes it (an unsaved colour is discarded, as with Cancel) and still does its job (a stroke draws, a toolbar button works, a flick turns the Page); a resting finger or palm outside it never closes it.
- **Palettes:** the pen and the highlighter each have one app-wide palette, kept across Sheets and restarts. A colour joins it only through Save; every colour drawn with is a palette colour. Saving a colour that is already in the palette (when adding, or by editing one to match another) keeps one swatch and selects it. The toolbar shows about five swatches and scrolls sideways beyond that, with an edge fade; the + Add colour button stays fixed at the end.
- A custom highlighter colour keeps the highlighter's translucency: the picker sets the hue, not the opacity.
- **Size presets** (three per tool, pen, highlighter and eraser) are app-wide and editable. The panel opened by tapping the selected size preset edits that preset, and the presets re-sort small to large on release. Sizes come only from the presets: there is no size for one Sheet only.
- **Size cursor:** while the pen hovers over a Page with the pen, highlighter or eraser, a circle the size of the current tool's stroke (or eraser) shows under the tip. Android only; on iPad it is decided at the port.

**Lasso:** circle strokes with the pen; as in Samsung Notes, a stroke is selected only if it is fully inside the lasso. An "Include partly enclosed strokes" switch (off by default, app-wide) also selects strokes with most of their length inside. The switch is in the panel above the toolbar, opened by tapping the Lasso tool while it is selected. Tapping a stroke with the Lasso tool selects that stroke alone; tapping empty space clears the selection. There is no rectangle selection in V1.0. The selection can be:

- **moved** by dragging,
- **resized** with corner handles, keeping its proportions (stroke thickness scales with it),
- **rotated** with a rotate handle (free, snapping at every right angle: 0°, 90°, 180° and 270°),
- **copied** and **pasted** on any Page of the same Sheet, at the same position and size in Page units, already selected, with Copy and Paste buttons in the toolbar. A paste lands on the Page the pen last touched (the first shown Page if that one is off screen). On the Page it was copied from, it is offset a little down and right so the copy shows; each further paste on that Page steps the same amount further. Strokes that would fall off the Page are moved inside its edge; a selection larger than the Page is first scaled down to fit. Copy and Paste show with the Lasso tool; Paste works whenever something is copied, even with nothing selected. The copy lasts while the Sheet is open, across tool switches. Pasting into another Sheet is Post-V1.0.
- **deleted** from the toolbar.

A move, resize or rotate never takes strokes off the Page: the selection stops at the Page edge. A selection is a stroke's whole Ink, highlighter strokes included. A selection is on one Page: a lasso selects only strokes on the Page it starts on, so the two parts of a stroke split at a Page edge are selected separately. Undoing or redoing a lasso action leaves its strokes selected, if they are still on the Page, so the user can try again straight away. A paste is one undo step; Copy is not an undo step.

### Edit mode

Printed-look Symbols, and Covers over Printed markings.

**Symbols**, built in this order:

1. **Fingerings:** 0, 1, 2, 3, 4
2. **Articulations:** tenuto, staccato, accent, fermata
3. **Bowings:** up-bow, down-bow, retake
4. **Dynamic letters:** pp, p, mp, mf, f, ff, sfz, fp
5. **Ties/slurs:** tap start and tap end, or drag from start to end; one handle sets arch height and flips above/below
6. **Hairpins:** crescendo and decrescendo; tap start and tap end, or drag from start to end; one handle sets the opening

Text dynamics ("cresc.", "dim.", "subito") are written as Ink or a Text box.

**Toolbar:** the Edit tools are Symbol, Lasso, Eraser and Cover (its icon a mosaic pattern). With the Symbol tool, the slot holds a group switch (a button showing the group's Symbol; tapping it lists the six groups in the panel, each as its Symbol's icon), the chosen group's Symbols (each button shows the Symbol as printed, never a text label), the current colour swatch and a Symbol scale button (colour and the scale slider open in the panel). With Lasso, the slot holds Delete and the colour swatches for the selection; with Cover, Delete; the Eraser has no options.

**Placing:** pick a Symbol in the slot and put the pen down on the Page: the Symbol shows under the tip at once, follows the pen while it drags, and is placed and selected where the pen lifts (one undo step), so it can still be dragged or resized afterwards. Tapping an existing Symbol selects it instead of placing another; a quick tap on empty Page while a Symbol is selected only clears the selection, and the next tap places. The Symbol tool's slot has no Delete: a wrong placement goes with Undo, the Eraser, the side button, or Lasso and Delete. Ties/slurs and hairpins are placed with two taps (start, end) or one drag from start to end, and are then selected with their handle. A placement is one undo step.

**Changing:** tap a Symbol or Cover to select it (the Lasso tool selects either; the Symbol tool selects Symbols, and the Cover tool Covers). With the Lasso tool, circling with the pen selects every Symbol and Cover fully inside the loop (on the Page it starts on); a multiple selection can be moved, deleted and recoloured together, and resize handles show only for a single selection; drag to move (pen down on an unselected Symbol and drag selects and moves it in one go), handles to resize (kept on the Page, at least 2 pt), delete button in the toolbar. Undoing or redoing a change leaves its Symbol selected, as with the lasso. Holding the S Pen side button in Edit mode erases as the Eraser does, whatever the tool; releasing it returns to the tool.

**Eraser:** an Edit mode eraser removes a whole Symbol or Cover when it touches any part of it, and it has no area mode.

**Colour:** every Symbol starts in the Symbol palette's first colour, as Ink does (no separate default to set). The palette starts black and blue (app-wide, saved and edited like the Ink palettes), with the custom colour picker.

**Size:** the Symbol scale is detected per Page from the staff-line spacing. A per-Sheet slider adjusts it, and a single Symbol can be resized. A Symbol's size is kept relative to its Page's Symbol scale, so a change of the scale resizes Symbols already placed. Pages with no detectable staff (title pages, bad scans) use a standard size.

**Covers:** drag a rectangle over a Printed marking, then adjust with handles. The fill colour is sampled from the paper just outside the rectangle. To change a printed tie or bowing, cover it and place a new Symbol.

### Text mode

Typed, printed-look text in Text boxes.

- **Placing:** tap to place a Text box, then type with the on-screen keyboard (handwriting-to-text keyboards work too).
- **Font:** a printed-look serif in **italic** (for "pizz.", "cresc.") or **regular** (for plain notes such as "breathe"). One font per Text box; no mixed styling inside a box.
- **Toolbar:** Text mode has no tools; its slot holds the font switch (Italic | Regular, each in its own style), the colour swatches with + Add colour, the size slider, then Link and Delete (enabled only with a Text box selected). Font, colour and size change the selected Text box (one undo step each), or set the next Text box when none is selected. While the keyboard is up, the toolbar and its panel sit above it.
- **Colour:** same presets and custom colour picker as Symbols.
- **Size:** defaults from the Symbol scale, with a size slider.
- **Changing:** tap a Text box to select it; drag to move, handles set the box width (text wraps inside; the size slider sets the font size, and the box grows in height to fit), delete button in the toolbar. Double-tap to edit the words. There is no eraser in Text mode, and the S Pen side button does nothing.
- **Links:** a "Link" button on a selected Text box opens a field in the panel for a web URL (http or https only: anything else shows an error under the field and Save stays disabled), with Cancel, Save and, when the box already has a Link, Remove link. The whole box becomes the Link and is drawn underlined. A **finger** tap on a Link shows an "Open \<site\>?" bar above the toolbar (not a dialog); confirming opens the browser. A finger tap on a Link follows the edge-tap palm rules (lifts within 0.25 s; ignored within 0.15 s of the pen). A **pen** tap only selects it. A finger tap on a Link always opens it, even inside the page-turn edge area.

### Layers, undo and saving

- There are three layers, each with a show/hide toggle: **Ink**, **Edit** and **Text**. Covers sit under Symbols on the Edit layer, so hiding it brings back the Printed markings under the Covers. Hiding all three shows the Sheet exactly as printed.
- Stacking, bottom to top: Page → Covers → Symbols → Text boxes → Ink. The highlighter blends (multiply) so the notes under it stay sharp.
- The toggles are switches in the panel, opened by the **Layers** button at the right end of the toolbar (a dot on the button shows when a layer is hidden); the panel opens and closes like the others. Any layer can be hidden, the current mode's included; drawing or placing on a hidden layer shows it again.
- Switching to a mode shows its layer if it was hidden. Layer toggles are remembered per Sheet.
- Each Sheet has its own undo/redo history, covering Ink (including lasso actions), Symbols, Covers and Text boxes. It lasts while the Sheet is open and is cleared on close.
- Saving is automatic. There is no Save button.

### Settings

A full page opened from a gear icon on the home screen. It holds app-wide Settings: the defaults every Sheet starts with. A change of the chosen tool, colour or size in a Sheet's toolbar lasts only while that Sheet is open (palettes, size presets and the lasso switch are app-wide and saved at once), and per-Sheet choices (layer toggles, Symbol scale slider) stay in the Sheet. The eraser has no Setting: its last mode and size carry over to the next Sheet while the app runs.

- Each feature declares its own Settings (name, type, default). The page is built from those declarations, grouped by feature, and the type picks the control (toggle, slider, choice, palette, size presets). A palette control shows its colours and the same custom colour picker as the toolbar.
- Changes are saved instantly and covered by Android backup. There is no user-editable config file.
- V1.0 Settings:
  - **Reading:** landscape turns advance two Pages or one.
  - **Ink:** the pen and highlighter palettes, the size presets, and "Include partly enclosed strokes" for the lasso. A Sheet opens with the pen and highlighter on their palette's first colour and their middle size preset; there is no separate default to set. The palettes are edited here without an edit mode: tapping a swatch opens its colour panel with Delete, Cancel and Save (the last colour left has no Delete), hold and drag reorders (the hold gives a short vibration, and the other swatches slide aside while one is dragged; dragging a colour to the front makes Sheets start with it), and **+ Add colour** opens the same picker with Cancel and Save. Each palette keeps at least one colour, and a deletion shows an Undo bar with the deleted colour's swatch. The hint under each palette reads "Tap a colour to edit or delete it; hold and drag to reorder." The size presets of the pen, highlighter and eraser are edited here too, each shown with a stroke preview at its size; tapping one opens its slider, and they re-sort small to large on release. One "Restore default palettes and presets" button brings back the factory values (and keeps saved colours), after a confirmation in place (the button turns into "Restore? Cancel / Restore", not a dialog). Strokes already drawn keep their colour whatever is deleted.
  - **Edit:** the Symbol palette, edited like the Ink palettes (its first colour is the one new Symbols start in).
  - **Text:** default font (italic or regular).
  - **Links:** ask "Open \<site\>?" before opening (on by default), or open straight away.

### Post-V1.0

- Export and import of Settings.
- Per-Sheet settings (including per-Page ones) that override the app-wide Settings, such as advancing by one Page in landscape.
- **Sharps/flats/naturals** as Symbols, with spacing adjustment. Making room may need notes or barlines moved, which needs the music data below.
- Pasting Ink into another Sheet.
- Linked parts: the parts of a stroke split at a Page edge selected, moved and resized as one, with a selection spanning both Pages of a spread.
- Recognising a palm by its contact size (revisit for V1.0 if palms still get through after #24).
- **Handwriting to Symbols:** write a marking by hand and have it converted to a printed-look Symbol, possibly using a small on-device model.
- **Gesture Covers:** a scribble or cross over a Printed marking applies a Cover automatically. Which gesture is undecided.
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
