# Candidate scorecard

Scores are senior-design heuristic judgments from visual inspection, not user-testing results. Each
criterion is scored out of 10 and converted to the stated weighted total.

| Candidate | Small recognition 25% | Category distinctiveness 20% | Document/PDF association 20% | Premium/trust 15% | Mask 10% | Mono 5% | UI fit 5% | Weighted /100 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| A — Document Q | 9.3 | 9.0 | 9.2 | 9.3 | 9.5 | 9.0 | 10.0 | **92.6** |
| B — Folded Q | 8.1 | 9.2 | 6.8 | 8.7 | 9.6 | 9.5 | 10.0 | **83.6** |
| C — Page Stack Q | 8.4 | 8.7 | 9.3 | 8.4 | 8.8 | 8.0 | 9.5 | **87.2** |
| D — Category First | 8.8 | 8.2 | 10.0 | 8.4 | 9.3 | 8.4 | 9.5 | **89.3** |

## Candidate concepts

- **A — Document Q:** one white page with a short, embedded blue Q. It keeps the direct document cue
  while shortening the control’s tail so the mark reads more like Q and less like search.
- **B — Folded Q:** the Q is dominant and a pale fold is cut into its shoulder. It is the most
  brand-led option but lacks enough document evidence at first glance.
- **C — Page Stack Q:** two page layers communicate toolkit breadth. It remains clear, but the rear
  sheet adds visual mass without improving the core Q.
- **D — Category First:** a large blue block-built PDF label inside a white page. This provides the
  strongest literal category signal and is intentionally reserved for conversion testing.

## Tests performed

- 16 and 24 px reduction plus a 0.45 px blur: A retains page/Q recognition; B retains Q but loses its
  fold; C’s rear layer merges; D’s PDF cue becomes a block rhythm at 16 px and is readable by 32 px.
- 32 px Play-search simulation and 48 px launcher review: A has the best balance of brand and category.
- Grayscale and light/dark surfaces: all candidates retain contrast; A remains the calmest.
- Circle, squircle, rounded-square, square, and teardrop masks: no finalist clips important artwork.
- Themed-icon review: A reduces cleanly to a page silhouette with a Q cutout.
- Protanopia, deuteranopia, and tritanopia approximations: blue/white luminance separation remains.
- Competitor similarity: none reproduces a competitor mark, page fold, or color/shape combination.

## Two refinement passes

Pass one exposed Candidate A as too timid beside the control, Candidate B as overly abstract, Candidate
C as slightly busy, and Candidate D as literal but useful. In pass two, A’s page and Q were enlarged,
its tail stayed shorter than the control, and all critical geometry remained within Android’s safe
zone. The three finalists were then reviewed beside the control on light/dark surfaces, masks,
themed colors, and neutral presentation cells.

## Selection

- **Production recommendation:** Candidate A — Document Q.
- **Experiment alternative:** Candidate D — Category First. It tests a meaningfully different
  hypothesis: literal PDF recognition versus owned Q recognition.
- **Reserve:** Candidate C — Page Stack Q.
- **Rejected:** Candidate B. Its Q is memorable, but category association is materially weaker and
  the fold disappears at the sizes where recognition matters most.

Candidate A wins because it is the only concept that scores strongly in both immediate document
recognition and QuietPDF-specific recall without depending on text, security imagery, or extra layers.
