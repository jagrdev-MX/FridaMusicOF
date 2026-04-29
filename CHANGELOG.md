# [0.2.0](https://github.com/jagrdev-MX/FridaMusicOF/compare/v0.1.6...v0.2.0) (2026-04-29)


### Bug Fixes

* **ci:** elevate GITHUB_TOKEN permissions for release ([50478a0](https://github.com/jagrdev-MX/FridaMusicOF/commit/50478a063627e56a19f2c7c732f05897091ffc16))


### Features

* implement project site with landing page, styling, and crashlytics configuration ([66550c9](https://github.com/jagrdev-MX/FridaMusicOF/commit/66550c956b315b54056f95b14de5d8b31dce3e2a))
* trigger release pipeline ([82235cd](https://github.com/jagrdev-MX/FridaMusicOF/commit/82235cdc282f440669659d79a1668d12a481f9f1))

# CHANGELOG

All notable changes in this repository are documented here, based strictly on commit history.

## [v0.0.6] - 2026-04-26

### ✨ Features
- ES: Se añadió un landing web estático visual para FridaMusic con estética blur/glass.
- EN: Added a static visual FridaMusic web landing page with a blur/glass aesthetic.

### 🛠 Improvements
- ES: Se incorporó una redirección desde la raíz del repositorio hacia `site/` para facilitar acceso.
- EN: Added a root-level redirect to `site/` for easier access.

### 🐛 Fixes
- ES: No se registran commits de corrección explícita en esta versión.
- EN: No explicit fix commits were recorded in this version.

### ⚙️ Tech
- ES: Se agregaron utilidades locales para servir el sitio (`serve-local.sh`, `start-server.bat`) y documentación de ejecución.
- EN: Added local utilities to serve the site (`serve-local.sh`, `start-server.bat`) and run documentation.

### 🚀 Release Notes
- ES: Esta versión introduce una presencia web visual lista para demo, con navegación por enlaces externos y operación local simple para revisión de producto.
- EN: This release introduces a demo-ready visual web presence, with external-link navigation and simple local run workflows for product review.

### 🧠 Dev Notes
- ES: Cambios concentrados en `index.html` raíz y carpeta `site/`; sin integración backend, login ni Firebase.
- EN: Changes are concentrated in root `index.html` and the `site/` folder; no backend, login, or Firebase integration.

## [v0.0.5] - 2026-04-25

### ✨ Features
- ES: Se añadieron pantalla de ajustes y servicio de reproducción (`MusicService`) para ampliar capacidades de la app.
- EN: Added a settings screen and playback service (`MusicService`) to expand app capabilities.

### 🛠 Improvements
- ES: Se mejoraron flujo principal, reproducción en pantalla principal/now playing y manejo de repositorio de audio.
- EN: Improved the main flow, playback behavior in main/now-playing screens, and audio repository handling.

### 🐛 Fixes
- ES: Se aplicaron ajustes iterativos en navegación y estado de biblioteca durante la serie de commits `v0.1.5`.
- EN: Applied iterative fixes to navigation and library state through the `v0.1.5` commit series.

### ⚙️ Tech
- ES: Actualizaciones en `build.gradle.kts` y `AndroidManifest.xml` para soportar la evolución funcional.
- EN: Updated `build.gradle.kts` and `AndroidManifest.xml` to support feature evolution.

### 🚀 Release Notes
- ES: Versión enfocada en madurez de experiencia: ajustes, continuidad de reproducción y base más sólida para iterar funcionalidades multimedia.
- EN: A maturity-focused release: settings, playback continuity, and a stronger base for multimedia feature iteration.

### 🧠 Dev Notes
- ES: Esta versión consolida tres commits consecutivos con el mismo marcador (`v0.1.5`) en un único corte semántico.
- EN: This version consolidates three consecutive commits with the same marker (`v0.1.5`) into a single semantic cut.

## [v0.0.4] - 2026-04-25

### ✨ Features
- ES: Se introdujeron `AudioRepository`, modelo de dominio `Song` y `LibraryViewModels`.
- EN: Introduced `AudioRepository`, `Song` domain model, and `LibraryViewModels`.

### 🛠 Improvements
- ES: Se refinó la pantalla de biblioteca, pantalla principal y paleta visual de tema.
- EN: Refined the library screen, main screen, and theme color palette.

### 🐛 Fixes
- ES: Se ajustó `AndroidManifest.xml` como parte de estabilización funcional.
- EN: Updated `AndroidManifest.xml` as part of functional stabilization.

### ⚙️ Tech
- ES: Primer bloque claro de arquitectura por capas (data/domain/presentation) visible en commits.
- EN: First clear layered architecture block (data/domain/presentation) visible in commits.

### 🚀 Release Notes
- ES: Esta versión marca la transición de una UI base a una app con base de datos de canciones y estado de biblioteca gestionado.
- EN: This release marks the transition from base UI to an app with song data foundations and managed library state.

### 🧠 Dev Notes
- ES: El corte se alinea con el commit etiquetado internamente como `v0.0.2`.
- EN: The cut aligns with the commit internally labeled `v0.0.2`.

## [v0.0.3] - 2026-04-25

### ✨ Features
- ES: Se añadió `FridaMusicApp` y se integró composición principal adicional en `MainActivity`.
- EN: Added `FridaMusicApp` and integrated additional main composition in `MainActivity`.

### 🛠 Improvements
- ES: Se renombró el componente de navegación inferior a `VitreaBottomNavigation` y se ajustó el flujo principal.
- EN: Renamed the bottom navigation component to `VitreaBottomNavigation` and adjusted main flow.

### 🐛 Fixes
- ES: No se registran commits de fix explícitos en esta versión.
- EN: No explicit fix commits were recorded in this version.

### ⚙️ Tech
- ES: Ajustes en módulos DI y dependencias Gradle para soportar organización de app.
- EN: Updates in DI modules and Gradle dependencies to support app organization.

### 🚀 Release Notes
- ES: Versión de consolidación arquitectónica: prepara la app para escalar navegación, estado global y módulos.
- EN: Architectural consolidation release: prepares the app to scale navigation, global state, and modules.

### 🧠 Dev Notes
- ES: Incluye un rename casi total de componente (`R099`) detectado en historial de git.
- EN: Includes a near-total component rename (`R099`) detected in git history.

## [v0.0.2] - 2026-04-24

### ✨ Features
- ES: Se incorporó la base visual de la app con pantallas Compose (Home, Search, Library, Main, Now Playing) y mini reproductor.
- EN: Added the app’s visual foundation with Compose screens (Home, Search, Library, Main, Now Playing) and mini player.

### 🛠 Improvements
- ES: Se añadió tipografía `Be Vietnam Pro` y sistema de tema inicial.
- EN: Added `Be Vietnam Pro` typography and initial theming system.

### 🐛 Fixes
- ES: Se corrigió naming de módulo DI (`AooModules` → `AppModules`).
- EN: Fixed DI module naming (`AooModules` → `AppModules`).

### ⚙️ Tech
- ES: Actualización de configuración Gradle y manifiesto para soportar Compose y estructura UI.
- EN: Updated Gradle and manifest configuration to support Compose and UI structure.

### 🚀 Release Notes
- ES: Esta versión define la primera experiencia visual navegable de FridaMusic y el lenguaje de diseño inicial.
- EN: This release defines FridaMusic’s first navigable visual experience and initial design language.

### 🧠 Dev Notes
- ES: Se agrupan commits iniciales del 24 de abril posteriores al bootstrap para reflejar el primer salto funcional real.
- EN: Groups the April 24 initial commits after bootstrap to reflect the first real functional leap.

## [v0.0.1] - 2026-04-24

### ✨ Features
- ES: Bootstrap inicial del proyecto Android con estructura base de app, tests de ejemplo y configuración de Gradle Wrapper.
- EN: Initial Android project bootstrap with base app structure, sample tests, and Gradle Wrapper setup.

### 🛠 Improvements
- ES: Se añadieron configuraciones de IDE para estandarizar entorno local de desarrollo.
- EN: Added IDE configuration files to standardize local development environment.

### 🐛 Fixes
- ES: No se registran commits de fix explícitos en esta versión.
- EN: No explicit fix commits were recorded in this version.

### ⚙️ Tech
- ES: Incluye configuración de build raíz, catálogo de versiones y recursos base Android.
- EN: Includes root build config, version catalog, and base Android resources.

### 🚀 Release Notes
- ES: Primera entrega técnica del repositorio: establece cimientos de compilación, estructura y ejecución para futuras iteraciones.
- EN: First technical delivery of the repository: establishes build, structure, and runtime foundations for future iterations.

### 🧠 Dev Notes
- ES: Versión anclada al primer commit histórico del repositorio.
- EN: Version anchored to the repository’s first historical commit.

## Tag References

- `v0.0.1` → `ec8670c`
- `v0.0.2` → `968cbfc`
- `v0.0.3` → `22c7c5e`
- `v0.0.4` → `ef9a2e9`
- `v0.0.5` → `3be3a98`
- `v0.0.6` → `9227953`
