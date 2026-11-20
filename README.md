# TriageU · Kotlin Multiplatform


## Estructura

```
kmp/
├─ composeApp/
│  └─ src/
│     ├─ commonMain/      → toda la lógica y la UI (≈ 95 % del código)
│     │   ├─ model/         Activity, Subject, Priority, fechas
│     │   ├─ data/local/    LocalStore (SQLDelight)
│     │   ├─ data/remote/   AuthManager, RemoteApi (Ktor), DTOs, SupabaseConfig
│     │   ├─ data/sync/     SyncQueue, SyncEngine
│     │   ├─ notifications/ ReminderPlanner + ReminderService
│     │   ├─ platform/      interfaces expect/actual
│     │   ├─ ui/            pantallas Compose
│     │   ├─ TriageStore.kt estado central (reemplaza TriageViewModel + SyncRoot)
│     │   └─ App.kt         raíz + barra inferior
│     ├─ androidMain/     → Activity, SQLite, cifrado de sesión, AlarmManager, widget Glance
│     ├─ iosMain/         → Keychain, notificaciones, App Group del widget, ComposeUIViewController
│     ├─ commonTest/      → prioridad y recordatorios
│     └─ commonMain/sqldelight/…/Triage.sq   → esquema local + cola de sync
└─ iosApp/                → host Xcode (SwiftUI mínimo) + widget Swift original
```

## Ejecutar

**Android:** abrir `kmp/` en Android Studio y ejecutar `composeApp`, o `./gradlew :composeApp:installDebug`.

**iOS:** abrir `kmp/iosApp/iosApp.xcodeproj` en Xcode y ejecutar. La fase "Compile Kotlin Framework"
llama a `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. Requiere JDK 17+ y el equipo de firma
con el App Group `group.edu.unicauca.aplimovil.TriageU` (el mismo de la app original).

**Tests:** `./gradlew :composeApp:testDebugUnitTest`

## Equivalencias Swift → Kotlin

| Swift | Kotlin |
|---|---|
| `@Model TriageActivity / Subject` (SwiftData) | `model/Models.kt` + tablas SQLDelight (`Triage.sq`) |
| `TriageViewModel` (`@Observable`) | `TriageStore` + `ActivityFormState` |
| `calculatePriority` | `Priority.calculate` |
| `AuthManager` + `Keychain` | `AuthManager` + `Settings` seguro (Keychain iOS / EncryptedSharedPreferences Android) |
| `SupabaseService` (URLSession) | `RemoteApi` (Ktor) + `SyncEngine` |
| `SyncQueue` (UserDefaults, NWPathMonitor) | `SyncQueue` (tabla `SyncItem`) + `ConnectivityMonitor` |
| IDs sincronizados en UserDefaults | tabla `SyncedId` |
| `NotificationService` | `ReminderPlanner` (común) + `ReminderScheduler` (UNUserNotificationCenter / AlarmManager) |
| `WidgetBridge` + WidgetKit | `WidgetData` (común) → App Group + widget Swift (iOS) / Glance (Android) |
| `ContentView` y vistas SwiftUI | `App.kt` y `ui/*.kt` (Compose) |

## Compatibilidad con la versión iOS actual

- Mismo backend Supabase (`supabase_schema.sql` no cambia) y mismas credenciales publicables.
- Mismos IDs fijos de materias por defecto → no se duplican entre la app Swift y la nueva.
- Los IDs se normalizan a minúsculas (como los devuelve Postgres).

## Diferencias a tener en cuenta

- El widget iOS ahora lee un JSON de texto con fechas en milisegundos (ver `iosApp/TriageUWidget/TriageUWidget.swift`).
- iOS no tiene todavía el equivalente de `NWPathMonitor`: la cola se reintenta al abrir la app y cada 30 s.
- Android reprograma los recordatorios al abrir la app (no hay receptor de reinicio del dispositivo).
- Con Supabase configurado y sin sesión, la app ya no siembra materias locales (en Swift lo hacía antes del login).
