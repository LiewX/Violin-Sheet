# Manual checks

Things to try on the Galaxy Tab S9+ that automated adb checks can't judge. Each issue adds its items when it's committed. Tick an item when it's checked; delete an issue's block once all its items are ticked. Report anything that fails, and it becomes a fix.

All checks for #2–#6, and the fixes after the first check (palm rest, zoomed turns, zoom kept on turn), were confirmed by the user on 2026-11-26.

## #7 Undo/redo

- [ ] Pen mid-stroke while a finger taps Undo: Undo removes the previous finished stroke, and the one being drawn completes normally. No half stroke is left behind.
- [x] Palm resting on the toolbar while writing: Undo/Redo don't fire.
- [x] Lift the pen and tap Undo straight away, several times fast: the stroke just drawn disappears and never stays on screen as a leftover (at worst it lingers 300 ms).
- [x] A fresh stroke doesn't blink while a slow Page renders (the 300 ms leftover cleanup must not remove it).
- [x] Draw a few overlapping strokes, close the Sheet and reopen it: they come back in drawing order (newer on top). Strokes drawn before #7 keep their old, random order.
- [x] The Undo/Redo icons read clearly at a glance, and the disabled state is obvious.

## #8 Highlighter, eraser, side button

- [x] Side button pressed mid-stroke: the stroke finishes as drawn, and erasing starts with the next pen down.
- [ ] Side button held, finger touches the screen: the finger only navigates and nothing is erased.
- [x] Palm resting while erasing with the pen: the palm neither erases nor draws.
- [x] Side button released mid-erase: it keeps erasing until pen up, and the pen draws again on the next pen down.
- [x] Real S Pen side button (adb only injected it): holding it erases, and doesn't open Air command.
- [x] Highlighter over printed notes, zoomed in: notes black and sharp, colours read as highlighter (yellow, green, pink).
- [x] The highlighter is translucent while drawing and multiplies on lift: the switch doesn't look jarring.
- [x] The eraser circle follows the pen smoothly, and live erasing keeps up on a Page with many strokes.
- [x] Area eraser across a line leaves two clean pieces; undo brings the whole line back in its place.
- [x] Toolbar icons (pen, marker, eraser) read clearly, and the toolbar fits in portrait.

## #9 Colour picker and size sliders

- [ ] Pen dragging in the picker while a finger touches the Page: the Page doesn't turn.
- [ ] Palm landing on the panel while the pen draws on the Page: the picker may move (accepted for V1.0); say if it gets in the way.
- [ ] Picker feel with the S Pen: hue strip and square easy to hit; panel size and position look right.
- [ ] A custom highlighter colour looks translucent on the Page, and its preview swatch matches.
- [ ] Settings page: the picker opens under the colour row and a custom colour becomes the default for the next Sheet.

## #10 Ink lasso

- [ ] Palm resting, then the pen draws a lasso: the palm adds no lasso points. If palm rejection cancels the pen instead, the lasso is dropped and the selection stays clear.
- [ ] Pen mid-drag, then a finger pinch: the selection stays under the pen tip while the zoom changes.
- [ ] Two hands at once (pen dragging, fingers swiping): fingers only navigate; a page turn mid-drag clears the selection and nothing jumps.
- [ ] Side button pressed in the middle of a lasso or drag: nothing changes until the pen lifts.
- [ ] Side button held at pen down with a selection: it erases as always, and the frame disappears.
- [ ] Handles (12 dp drawn, 24 dp hit radius) are easy to grab with the S Pen, and the frame is visible on a busy score. A straight line's frame is padded to 24 dp: is there enough room between its corners?
- [ ] With "Include partly enclosed strokes" off, a lasso that just grazes a stroke's end leaves it out, as Samsung Notes does. The switch is easy to find by tapping the Lasso tool again.
- [ ] A pen tap on a thin stroke selects it reliably (4 dp beyond the ink), and a slightly moving tap doesn't turn into a lasso (8 dp).
- [ ] Turn the Page (or zoom the selected Page off screen): the frame disappears, and Undo of a lasso action on a Page no longer shown doesn't bring it back.
- [ ] Move, resize and rotate previews keep up with the pen, with no flicker at the lift.
- [ ] The lasso icon reads as a lasso next to the Eraser.

## #23 Toolbar keeps one width

- [ ] Switch tools on the toolbar: no button jumps.
- [ ] The Lasso toolbar has empty space right of Delete (the slot is as wide as the Pen/Highlighter controls): does it look acceptable?

## #24 Palm rejection for navigation

- [ ] Palm resting on the Sheet with the pen away, then a finger swipes: two fingers, so the page never turns, and it zooms only past 15 % spread or 24 dp. Is lifting the palm acceptable?
- [ ] A palm lands while a finger swipe is in progress: that swipe no longer turns the page.
- [ ] Right after writing, tap the edge with the other hand while the pen hovers: the 300 ms window still treats it as a palm. Does a deliberate tap soon after writing feel blocked?
- [ ] A touch the system cancels (Samsung palm rejection, the notification shade, a window change) never turns the page, even after resting briefly on the edge.
- [ ] A resting hand (one or two contacts, still or shifting slightly) neither turns the page nor zooms.
- [ ] Quick edge taps and flicks feel reliable: 250 ms for a tap and 400 ms for a flick aren't too strict.
- [ ] A real pinch starts smoothly past the threshold, with no jump and no sticky dead zone.

## #25 Strokes split at Page edges

- [ ] Palm resting while the pen draws across from one Page to the other: both parts are saved and the palm leaves no mark.
- [ ] Draw across two Pages while pinch-zooming or panning with the other hand: parts are cut where the Pages are at lift, so they may be misplaced (accepted).
- [ ] Draw across two Pages at normal speed: the line shows whole while drawing, both parts stay after lift with no blink, and anything in the surround disappears at lift.
- [ ] Side button pressed mid-stroke across Pages: the stroke finishes as drawn, then splits.
- [ ] Lasso a part on one Page: only that part is selected.
- [ ] A highlighter stroke across two Pages looks continuous at the edge.

## #22 Saved colour palettes and editable size presets

- [ ] Palm on the page while dragging a swatch (Settings): the swatch is neither dropped nor duplicated; a second pointer on another swatch does nothing.
- [ ] Rest a palm on the screen while the pen moves in the colour picker: the panel stays open (the palm is a second pointer). A palm that lands *first* outside the panel does close it.
- [ ] Settings → Pen palette: a short S Pen press on a swatch opens its panel and doesn't start a drag; hold then drag reorders, with the pen and with a finger.
- [ ] A hold on a swatch released without moving opens the panel (the tap still fires). Fine, or should a hold never open it?
- [ ] Drag the toolbar palette sideways with the S Pen and with a finger: it scrolls. A pen tap on a swatch still selects it.
- [ ] With the picker open, turn the Page with a finger flick: the panel closes, the unsaved colour is discarded, and the Page turns.
- [ ] Highlighter: + Add colour and editing keep the colour translucent; a new highlighter stroke still reads as a highlighter.
- [ ] The toolbar palette's edge fade looks right at both ends, and the last swatch isn't visibly clipped.
- [ ] The colour panel looks compact next to the mockup.
- [ ] Tapping +, the selected swatch, or the selected preset a second time closes its panel.
- [ ] The Undo bar's swatch is easy to see against the dark bar; its text reads "Colour deleted from the pen palette"; Undo is easy to hit (about 10 s).
- [ ] Delete in the swatch panel is red and far enough from Save not to be hit by mistake.
- [ ] The preset previews read clearly at each size: the pen line, the highlighter band, the eraser circle.
- [ ] TalkBack reads the toolbar's swatches and presets as "selected".
- [ ] Restore default palettes and presets asks no confirmation. Is a one-tap restore OK?
- [ ] After the update, a Sheet opens with the pen on the first pen palette colour and the middle size; dragging a colour to the front in Settings changes what the next Sheet starts with.

## #11 Lasso copy and paste

- [ ] With a Sheet with several Pages: copy a lasso selection on Page 1, turn to Page 3, tap Paste: strokes land at the same place, selected, and Undo removes them in one step.
- [ ] Paste with nothing selected after switching tools (Pen, back to Lasso): Paste still works.
- [ ] Pen-down on a Page with the Highlighter or Eraser, then Lasso and Paste: the paste goes to that Page.
- [ ] Palm resting on another Page does not change where Paste lands.

## #12 Edit mode + fingering Symbols

- [ ] A fingering at the standard size (5 pt) looks right against the notes of a real Sheet; if not, say how much bigger or smaller.
- [ ] Edit mode toolbar: the Fingering 0–4 buttons, the Symbol tool's "1" and the group pill look printed (Bravura) and are centred in their buttons.
- [ ] Group panel: the accent, down-bow, "mf", slur arc and crescendo wedge read as their Symbols; the five unbuilt ones look greyed out.
- [ ] Mode switch: the nib (Ink), note (Edit) and serif T (Text, greyed out) are clear, and don't clash with the Pen tool's pencil.
- [ ] Switching between Ink and Edit mode, nothing in the bar jumps (mode switch, Undo, Redo, Layers stay put), landscape and portrait.
- [ ] Portrait: the slot scrolls sideways with a faded edge, by finger and by pen.
- [ ] Palm down first, then the pen in Edit mode: the palm neither places nor moves a Symbol, and the pen still works.
- [ ] Pen dragging a Symbol, then a finger lands: the drag stays under the pen (the view may zoom).
- [ ] Palm rejection mid-drag, mid-erase or mid-tap in Edit mode: the drag is dropped, the erased Symbols come back, nothing is placed.
- [ ] Two pens or hands at once in Edit mode: only the first pen is followed.
- [ ] Side button pressed mid-gesture in Edit mode: nothing changes.
- [ ] Fingerings look printed (Bravura) at the standard size against real Sheets (Decision 6), and stay on their note when zoomed.
- [ ] A selected fingering: dragging its middle moves it, dragging a corner handle resizes it.
