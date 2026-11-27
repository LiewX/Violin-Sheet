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
