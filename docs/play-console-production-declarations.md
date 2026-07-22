# QuietPDF Play Console Production Declarations

Use this file as the source of truth when completing Play Console for package
`com.rameshta.quietpdf`. Recheck it whenever the app, advertising SDK, or policy changes.

## App content

- Contains ads: **Yes**.
- App access: **All functionality is available without an account or login**.
- Target audience: **Ages 13–15, Ages 16–17, and Ages 18 and over**.
- Minimum age: **13**. The app does not display an age-selection screen.
- Age treatment: Google Mobile Ads uses `AgeRestrictedTreatment.UNSPECIFIED`. The app does not ask
  for age and does not tag every user as under the age of consent.
- Designed for Families: **No**. The app is a general-purpose productivity utility and is not
  designed primarily for children.
- Ads shown to users of unknown age: **Yes**. UMP consent signals, regional requirements, and
  Google advertising settings apply.
- App Open ads enabled: **Yes**.
- Native ads enabled: **Yes**. One consent-gated native placement appears in the Home content feed;
  the Home bottom banner is suppressed to avoid stacking the two formats.

## Privacy policy

- In-app policy: Settings → Privacy → Privacy Policy.
- Source document: `docs/index.html`.
- Contact: `naimish.app@gmail.com`.
- Public policy URL: publish `docs/index.html` at an active, public, non-geofenced HTTPS URL and
  enter that exact URL in Play Console before submission.

## Data Safety

QuietPDF document processing remains on the device. Do not declare locally accessed PDFs, images,
filenames, document text, passwords, signatures, annotations, or form values as collected because
the app does not transmit them off device.

Google Mobile Ads SDK 25.4.0 automatically collects and shares the following when advertising is
permitted. Declare each as **required** in the global Play form unless the production app adds an
advertising opt-out that is available to every user in every region:

| Play data type | Collected | Shared | Purposes |
| --- | --- | --- | --- |
| Approximate location (inferred from IP address) | Yes | Yes | Advertising or marketing; Analytics; Fraud prevention, security and compliance |
| App interactions | Yes | Yes | Advertising or marketing; Analytics; Fraud prevention, security and compliance |
| Diagnostics | Yes | Yes | Advertising or marketing; Analytics; Fraud prevention, security and compliance |
| Device or other IDs | Yes | Yes | Advertising or marketing; Analytics; Fraud prevention, security and compliance |

- Data encrypted in transit: **Yes** for Google Mobile Ads SDK data.
- Account creation: **No**.
- Account deletion requirement: **Not applicable** because QuietPDF has no accounts.
- Independent security review: answer **No** unless a qualifying external review has actually been
  completed.
- Data deletion request mechanism: the app has no QuietPDF server data. Users can clear local data
  through in-app controls, Android Clear storage, or uninstalling. Saved output PDFs remain under
  the user's control.

## Permissions and declarations

- Camera: used only for the user-initiated document scanner.
- Internet and network state: used only for advertising and consent services.
- Broad storage access / All files access: **Not requested**.
- Advertising ID: present through Google Mobile Ads. Applicable UMP consent signals are applied to
  advertising requests.

## Release checklist

1. Host the privacy policy and verify it in a signed-out browser.
2. Configure and publish the required UMP messages in AdMob Privacy & messaging.
3. Enter the declarations above in Play Console.
4. Upload a bundle signed with the registered upload key.
5. Review Play Console SDK warnings and the pre-launch report before production rollout.
