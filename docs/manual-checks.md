# Manual checks

Things to try on the Galaxy Tab S9+ that automated adb checks can't judge. Each issue adds its items when it's committed. Tick an item when it's checked; delete an issue's block once all its items are ticked. Report anything that fails, and it becomes a fix.

All checks for #2–#6, and the fixes after the first check (palm rest, zoomed turns, zoom kept on turn), were confirmed by the user on 2026-11-26.

## #7 Undo/redo

- [ ] Pen mid-stroke while a finger taps Undo: Undo removes the previous finished stroke, and the one being drawn completes normally. No half stroke is left behind.
- [ ] Palm resting on the toolbar while writing: Undo/Redo don't fire.
- [ ] Lift the pen and tap Undo straight away, several times fast: the stroke just drawn disappears and never stays on screen as a leftover (at worst it lingers 300 ms).
- [ ] A fresh stroke doesn't blink while a slow Page renders (the 300 ms leftover cleanup must not remove it).
- [ ] Draw a few overlapping strokes, close the Sheet and reopen it: they come back in drawing order (newer on top). Strokes drawn before #7 keep their old, random order.
- [ ] The Undo/Redo icons read clearly at a glance, and the disabled state is obvious.

## #8 Highlighter, eraser, side button

- [ ] Side button pressed mid-stroke: the stroke finishes as drawn, and erasing starts with the next pen down.
- [ ] Side button held, finger touches the screen: the finger only navigates and nothing is erased.
- [ ] Palm resting while erasing with the pen: the palm neither erases nor draws.
- [ ] Side button released mid-erase: it keeps erasing until pen up, and the pen draws again on the next pen down.
- [ ] Real S Pen side button (adb only injected it): holding it erases, and doesn't open Air command.
- [ ] Highlighter over printed notes, zoomed in: notes black and sharp, colours read as highlighter (yellow, green, pink).
- [ ] The highlighter is translucent while drawing and multiplies on lift: the switch doesn't look jarring.
- [ ] The eraser circle follows the pen smoothly, and live erasing keeps up on a Page with many strokes.
- [ ] Area eraser across a line leaves two clean pieces; undo brings the whole line back in its place.
- [ ] Toolbar icons (pen, marker, eraser) read clearly, and the toolbar fits in portrait.
