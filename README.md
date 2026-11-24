# Dreamboard

A premium, private, offline-first personal finance app for Android: net worth, debt elimination, budgets, the Ignite Motors car-flipping business, and the Porsche Cayenne 2035 goal.

**Phase 2 (this version):** everything from Phase 1, now on a local Room database: accounts, assets with valuation history, debts with payments, transactions with category splits, category limits, weekly net-worth snapshots, and CSV/Excel import with duplicate detection. Ignite still uses labelled demo data until Phase 4.

- Architecture, screen map, design system, ER model, phased plan and assumptions: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Build and install on your phone: [`docs/BUILD_AND_INSTALL.md`](docs/BUILD_AND_INSTALL.md)

Kotlin · Jetpack Compose · Material 3 · MVVM · Hilt · Navigation Compose · Coroutines/Flow · DataStore. No paid services, no cloud, no ads, no analytics, no internet permission.

Money is stored as `Long` minor units and basis points; it is never handled as floating point.
