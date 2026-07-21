Before performing QuietPDF feature work, read docs/product/quietpdf-product-context.md and
docs/execution/quietpdf-feature-queue.md. Treat the product context as persistent. The feature queue
is authoritative and must be processed sequentially; example feature IDs are not the complete queue.
The current feature prompt defines the only implementation scope for that run.

The user owns the complete branch, Git, pull-request, merge, main-synchronization, and
queue-completion workflow. Codex is responsible for implementing the feature, writing its focused
tests, and running relevant verification after the implementation is complete. Codex must report
the verification commands and results before handing the feature back. Codex must not perform any
Git, pull-request, merge, pull, push, or queue-status operation.
