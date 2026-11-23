# Dreamboard

A premium, private, offline-first personal finance app for Android: net worth, debt elimination, budgets, the Ignite Motors car-flipping business, and the Porsche Cayenne 2035 goal.

**Phase 1 (this version):** design system, four-tab navigation (Dreamboard · Debt Position · Budget & Expenses · Ignite), dashboards with interactive Canvas charts, detail screens, settings, and unit-tested finance engines. All figures are clearly labelled demonstration data until the Room database arrives in Phase 2.

- Architecture, screen map, design system, ER model, phased plan and assumptions: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Build and install on your phone: [`docs/BUILD_AND_INSTALL.md`](docs/BUILD_AND_INSTALL.md)

Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Navigation Compose · Coroutines/Flow · DataStore. No paid services, no cloud, no ads, no analytics, no internet permission.

Money is stored as `Long` minor units and basis points; it is never handled as floating point.
