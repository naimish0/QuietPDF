Before performing QuietPDF feature work, read docs/product/quietpdf-product-context.md and
docs/execution/quietpdf-feature-queue.md. Treat the product context as persistent. The feature queue
is authoritative and must be processed sequentially; example feature IDs are not the complete queue.
The current feature prompt defines the only implementation scope for that run.

The user owns the complete branch, verification, Git, pull-request, merge, main-synchronization, and
queue-completion workflow. Codex's only responsibility is to implement the feature and write its
focused tests in the workspace. Codex must not run verification or perform any Git, pull-request,
merge, pull, push, or queue-status operation.
