# Ereuna — Design System

## Philosophy

The app looks like a carefully typeset scientific journal crossed with a field notebook: warm paper, black ink, one sharp accent, dense but calm tables. It earns trust by looking like a reference tool, not a magic assistant.

### Principles

1. **Content is the interface.** Titles, numbers, and quotes carry the screen. Chrome stays minimal.
2. **Hairlines over shadows.** Separate things with 1dp rules and whitespace, like a printed page.
3. **Numbers are first-class.** Monospace, tabular figures, right-aligned, units in a lighter weight.
4. **Show the source.** Every claim has a citation marker `[4]` you can tap.
5. **One accent, used rarely.** The accent marks what's actionable or what's a gap. Nothing else.

## Color Tokens

| Token         | Light     | Dark      | Use                                  |
|---------------|-----------|-----------|--------------------------------------|
| `paper`       | `#F6F4EF` | `#141311` | App background                       |
| `surface`     | `#FFFFFF` | `#1C1B18` | Sheets, table rows, cards            |
| `ink`         | `#1A1917` | `#ECE8E1` | Primary text                         |
| `ink2`        | `#5E5A53` | `#A39E94` | Secondary text, metadata             |
| `rule`        | `#DAD5CB` | `#34322D` | Hairlines, dividers, table borders   |
| `accent`      | `#B4441F` | `#E0714C` | Gaps, primary action, selection      |
| `accentWash`  | `#F4E4DC` | `#3A2219` | Selected row, highlight behind quotes|
| `positive`    | `#2F6B4F` | `#7FC29F` | Agreement markers                    |
| `conflict`    | `#8A5A00` | `#E0B25A` | Contradiction markers                |

No gradients anywhere. Status is never color-only: always a word or symbol too.

## Typography

| Role         | Font                    | Weight   | Size/Line Height | Tracking | Notes                |
|--------------|-------------------------|----------|------------------|----------|----------------------|
| Display      | Newsreader              | SemiBold | 28 / 34 sp       | default  | Topic titles         |
| Heading      | Newsreader              | Medium   | 20 / 26 sp       | default  | Section headings     |
| Paper Title  | Newsreader              | Medium   | 16 / 22 sp       | default  | Paper titles in lists|
| Body         | IBM Plex Sans           | Regular  | 15 / 22 sp       | default  | Body text, UI labels |
| Label        | IBM Plex Sans           | SemiBold | 11 / 16 sp       | +0.08em  | Uppercase small caps |
| Mono         | IBM Plex Mono           | Regular  | 13 / 18 sp       | default  | Numbers, DOIs, years |

All are free Google Fonts, bundled in `res/font/`. Use tabular figures for all numbers.

### Font Files Required

```
res/font/
  newsreader_medium.ttf
  newsreader_semibold.ttf
  newsreader_medium_italic.ttf
  newsreader_semibold_italic.ttf
  ibm_plex_sans_regular.ttf
  ibm_plex_sans_medium.ttf
  ibm_plex_sans_semibold.ttf
  ibm_plex_mono_regular.ttf
  ibm_plex_mono_medium.ttf
```

## Shape

| Element        | Radius |
|----------------|--------|
| Chips, tags    | 2 dp   |
| Sheets, menus  | 6 dp   |
| Cards          | 0 dp (no cards; use hairlines) |

No elevation on content cards. Only bottom sheets and dropdown menus have shadow.

## Spacing

| Token          | Value |
|----------------|-------|
| Grid unit      | 4 dp  |
| Screen gutter  | 20 dp |
| List row vertical padding | 16 dp |
| Hairline divider thickness | 1 dp |

Everything snaps to the 4dp grid.

## Icons

Material Symbols Outlined, weight 300, size 20dp. No filled icons except the bookmark (filled = saved).

## Motion

| Property       | Value              |
|----------------|--------------------|
| Duration       | 150–200 ms         |
| Easing         | Standard (ease-in-out) |
| Types          | Fades, short slides |

No bounce, no shimmer, no typewriter effects. Skeleton loading uses flat `rule`-colored blocks.

## Signature Components

### PaperRow
- Serif title (paperTitle style)
- Mono meta line: `2023 · IEEE TED · 142 cit.`
- Relevance reason in `ink2` (body style)
- Right edge: `FULL TEXT` or `ABSTRACT` tag in label style
- 16dp vertical padding, 1dp `rule` divider below

### CitationMarker
- `[4]` in mono style, `accent` color
- Tappable: opens EvidenceSheet bottom sheet
- Min touch target 48dp
- TalkBack: "Source 4: Kim 2023"

### EvidenceQuote
- 2dp left border in `accent`
- Quote text in serif italic (Newsreader Medium Italic)
- Source line below: paper short label + section + page in `ink2` mono

### MetricCell
- Value in mono `ink`: `12.3`
- Unit in mono `ink2`: `pW/cell`
- Condition on second line in `ink2`: `@ 0.6 V, 25 °C`
- Right-aligned within table cells

### GapItem
- Number badge: `G1` in mono accent
- Statement in body style
- Pattern line in `ink2`: "0 of 24 papers test below 0.5 V"
- Citation markers for supporting papers
- Expandable with: why it matters, linked experiment

### ConfidenceTag
- Text: `HIGH`, `MED`, or `LOW` in label style
- 3-segment bar next to text (filled segments match confidence level)
- HIGH = 3 filled, MED = 2 filled, LOW = 1 filled
- Never color-only; always includes text

### ProgressLog
- Monospace (IBM Plex Mono) timestamped lines
- Format: `00:04  retrieved 187 candidates`
- Grows from bottom, auto-scrolls
- `ink` text on `paper` background

### SectionLabel
- Uppercase text in label style
- 1dp `rule` hairline extending to the right edge
- 8dp below the label text

### DataTable
- Frozen first column (paper short name)
- Horizontally scrollable remaining columns
- Header row: column name in label style + unit in `ink2` mono below
- Sortable: tap header to sort, arrow indicator
- Best value per column underlined in `accent`
- 1dp `rule` grid lines

## Banned Patterns

These are explicitly forbidden in all screens:

- Purple/blue gradients, glowing orbs
- Sparkle ✨ icons, robot or brain illustrations
- Chat bubbles, "Ask me anything", typewriter text animation
- Glassmorphism, neon borders, heavy drop shadows
- Emoji in the UI
- Over-rounded 24dp+ cards
- Giant empty hero illustrations
- Words: "magic", "supercharge", "unlock", "AI-powered" in UI copy
- Color-only status indicators (always include text)
- Generic stock icons for list items

## Copy Voice

Plain and exact, like a methods section:
- ✅ "Found 28 papers (2016–2025). 19 with full text."
- ❌ "Here's what I discovered for you!"
- ✅ "3 gaps identified across 25 papers."
- ❌ "We found some exciting research gaps!"
- ✅ "Semantic Scholar did not respond. Results use OpenAlex only."
- ❌ "Oops! Something went wrong 😅"

## Screen States

Every screen must handle these states:

| State    | Treatment                                                |
|----------|----------------------------------------------------------|
| Empty    | Plain sentence + one action. No illustrations.           |
| Loading  | Flat skeleton rows in `rule` color. No shimmer.          |
| Partial  | Banner: "9 of 28 papers are abstract-only; findings marked MED." |
| Error    | What failed + retry button. Technical but clear.         |
| Offline  | Cached content readable. Search disabled with notice.    |

## Accessibility

| Requirement       | Target                                         |
|-------------------|-------------------------------------------------|
| Touch targets     | Min 48dp                                        |
| Text contrast     | ≥ 4.5:1 against background                     |
| Icon buttons      | Content descriptions on all                     |
| Citation markers  | TalkBack: "Source 4: Kim 2023"                  |
| Font scaling       | Up to 200% without clipping                     |
