# ViolinSheet

A violin player's sheet music reader for a pen tablet, used at the orchestra stand to read, mark up and turn pages of their own parts.

## Language

### Sheets

**Sheet**:
One imported PDF of the player's own part for a piece.
_Avoid_: Part, score, file, document

**Page**:
One page of a Sheet.

**Printed marking**:
Anything already on the imported Sheet: notes, ties, bowings, fingerings, dynamics.
_Avoid_: Default annotation, original annotation

### Annotations

**Annotation**:
Anything the player adds on top of a Sheet: Ink, a Symbol, a Cover or a Text box.

**Ink**:
A freehand pen or highlighter stroke, kept exactly as drawn.
_Avoid_: Raw annotation, handwriting

**Symbol**:
A printed-look notation mark placed from the palette, such as a fingering, articulation, bowing or tie/slur.
_Avoid_: Converted annotation

**Cover**:
A paper-coloured patch that hides a Printed marking without altering the Sheet.
_Avoid_: Erase, white-out, mask

**Text box**:
Printed-look typed text placed on a Page.
_Avoid_: Note, comment

**Link**:
A Text box that opens a web URL.

**Symbol scale**:
The size at which Symbols are drawn on a Sheet so they match its printed notation.

### App

**Setting**:
An app-wide default that a new Sheet starts from, declared by the feature it belongs to.
_Avoid_: Preference, config, option

### Modes

**Ink mode**:
The mode for drawing, erasing and lasso-selecting Ink.

**Edit mode**:
The mode for placing, changing and removing Symbols and Covers.
_Avoid_: Conversion mode, annotation mode

**Text mode**:
The mode for placing and changing Text boxes and Links.
