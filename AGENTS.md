Before performing QuietPDF feature work, read docs/product/quietpdf-product-context.md and
docs/execution/quietpdf-feature-queue.md. Treat the product context as persistent. The feature queue
is authoritative and must be processed sequentially; example feature IDs are not the complete queue.
The current feature prompt defines the only implementation scope for that run.

The user creates every feature branch manually. Codex must never create the next feature branch.
Before changing feature code, verify that the current branch exactly matches the first pending
feature. Follow the product context for commit, push, pull request, merge, main synchronization, and
queue-completion requirements.
