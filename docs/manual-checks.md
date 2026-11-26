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
