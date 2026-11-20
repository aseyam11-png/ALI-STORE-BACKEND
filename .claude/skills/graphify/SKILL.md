---
name: graphify
description: "Mandatory graph-first workflow for ALI STORE backend analysis, architecture tracing, impact analysis, and post-change graph refresh."
---

# Graphify — ALI STORE backend skill

For any non-trivial backend question or code change, use Graphify before reading/editing source files.

## Required sequence

1. Ensure the CLI exists:
   `uv tool install --upgrade "graphifyy==0.9.79"`
2. Build the graph when missing:
   `graphify extract . --code-only`
3. Navigate the architecture with:
   `graphify query "<question>"`
   `graphify path "<A>" "<B>"`
   `graphify explain "<symbol>"`
4. Inspect only the source files justified by the graph result.
5. Implement the smallest safe change.
6. Refresh after changes:
   `graphify update .`
7. Run Maven/compiler/tests/security checks separately. Graphify never replaces them.

For frontend/backend changes, build/query both graphs before modifying either side.

Do not alter supplier, inventory, pricing, order, payment, authentication, or authorization behavior as part of Graphify integration.
