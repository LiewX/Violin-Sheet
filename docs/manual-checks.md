# Manual checks

Things to try on the Galaxy Tab S9+ that automated adb checks can't judge. Each issue adds its items when it's committed. Tick an item when it's checked; delete an issue's block once all its items are ticked. Report anything that fails, and it becomes a fix.

## Fixes after the 2026-11-26 check

- [ ] Rest your palm while writing, with the pen near or touching: the page doesn't zoom or pan, and the pen writes. If the palm lands first and the page moves, it should jump back as soon as the pen comes near.
- [ ] Two-finger zoom right after putting the pen down (within about half a second) is ignored as a palm. Check this doesn't get in the way.
- [ ] Zoomed in: a swipe that only pans never turns the page; to turn, start with the Page edge on screen and swipe well past it.
- [ ] Zoomed in, turn forward: the zoom stays, and you land on the new Page's left edge at the same height. Turn back: its right edge. With "Advance by one", it's the Page that just came into view.
- [ ] Pen input was rebuilt for the palm fix. Check again: writing still feels immediate, lines are smooth with pressure, strokes stay on their notes when zooming and rotating, and nothing is drawn when the system cancels a touch.

Checked on 2026-11-26 and fine: #2 reading, #3 managing Sheets, #4 zoom and layout (apart from the items above), #5 Settings, #6 pen (apart from the palm).
