# Google Play icon conversion experiment plan

The redesigned icon is a conversion hypothesis. Do not infer impact from competitor install bands or
from design scores.

## Assets

- Control: `experiments/control/quietpdf-control-512.png`
- Variant A: `experiments/variant-a-production/quietpdf-variant-a-512.png`
- Variant B: `experiments/variant-b-runner-up/quietpdf-variant-b-512.png`

## Sequence

1. In Play Console, create a Store Listing Experiment for the app icon only.
2. Test Control against Variant A first.
3. Keep the title, descriptions, screenshots, feature graphic, pricing, countries, and acquisition
   campaigns unchanged during the experiment.
4. Use first-time installer conversion as the primary metric.
5. Monitor retention, uninstall rate, rating, and crash-free users as guardrails. Do not optimize for
   AdMob eCPM alone.
6. Do not stop early. Wait for Play Console’s sufficient confidence and account for weekday/weekend
   traffic before declaring a winner.
7. If Variant A is inconclusive or loses, restore the control and later test Variant B as a separate
   experiment. Do not run both new hypotheses at once unless the available traffic supports it.
8. Record traffic split, countries, dates, confidence, conversion lift interval, and guardrail changes.
9. Promote a winner only after the result is statistically and operationally credible.

No asset in this repository has been uploaded, and no experiment has been created or started.
