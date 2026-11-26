# Manual checks

Things to try on the Galaxy Tab S9+ that automated adb checks can't judge. Each issue adds its items when it's committed. Tick an item when it's checked; delete an issue's block once all its items are ticked. Report anything that fails, and it becomes a fix.

## #6 Ink pen

- [ ] Writing with the S Pen feels immediate (no visible lag behind the nib), and lines look smooth with pressure
- [ ] Rest your palm on the screen while writing: no stray marks, and the page doesn't move
- [ ] A finger never draws; it still turns pages and zooms
- [ ] Strokes stay on the notes they mark when you zoom, rotate, or switch between 1-up and 2-up
- [ ] Toolbar colours and sizes are easy to hit; Settings → Ink sets the pen a newly opened Sheet starts with

## #5 Settings

- [ ] The gear (top right of the home screen) and the Settings page look and read right
- [ ] Set "Landscape turns advance" to One Page; a Sheet you never toggled then turns one Page at a time in landscape

## #4 Zoom, pan and rotation layout

- [ ] Pinch zoom and one-finger pan feel smooth; the zoomed view turns sharp a moment after you stop
- [ ] Swiping past the Page edge while zoomed turns the page at a natural point (not too eager, not too stubborn)
- [ ] Decide if this is fine: a turn or a rotation resets zoom to fit
- [ ] Decide if this is fine: a spread starts at the current Page, so portrait Page 2 rotates to Pages 2–3 (not 1–2 as in a printed book)
- [ ] The "Advance by one" chip (landscape, top right) is easy to find and doesn't get in the way of the music

## #3 Manage Sheets

- [ ] Rename and delete from the ⋮ menu feel right, and the Undo bar is easy to hit in its 10 seconds
- [ ] Decide if this is fine: deleting a second Sheet within 10 seconds replaces the first one's Undo bar, so the first can't be undone any more (Gmail works the same way)
- [ ] Real backup and restore, if you're willing: Settings → Google → Backup → Back up now, then uninstall and reinstall the app. Sheets come back marked "PDF missing, re-import", and re-importing a PDF fixes them. (adb only simulated this by deleting the PDF files.)

## #2 Read a Sheet (checked 2026-11-26)

- [x] Open a Sheet; swipe and edge-tap with a finger to turn pages
- [x] S Pen swipes and taps don't turn pages
