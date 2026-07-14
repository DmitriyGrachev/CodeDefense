# Architecture

CodeDefense uses a small ports-and-adapters design. Picocli commands translate arguments into application use cases; use cases depend on domain models and boundary interfaces; filesystem scanning, Codex process execution, terminal input, and report storage are adapters.

The dependency direction is adapters → application → domain. Domain models must not depend on Picocli, JLine, Jackson adapter annotations, or `ProcessBuilder`. The application object graph is wired explicitly by `CodeDefenseApplication`; no dependency-injection framework or Spring is used.
