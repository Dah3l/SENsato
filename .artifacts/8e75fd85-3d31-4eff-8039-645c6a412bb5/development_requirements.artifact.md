# Especificación de Requisitos del Entorno y Proyecto Android

Este documento detalla las versiones, dependencias y configuraciones exactas utilizadas en este entorno de desarrollo para garantizar compatibilidad total en futuros proyectos.

## 1. Toolchain y Versiones Base
* **Sistema Operativo**: Windows (compatible con Linux/macOS).
* **IDE**: Android Studio (con SDK configurado localmente).
* **Gradle Version**: `8.7` (`gradle-wrapper.properties`).
* **Android Gradle Plugin (AGP)**: `8.5.2`.
* **Kotlin Version**: `1.9.24`.
* **Java / JVM Target**: Java 17 (`JavaVersion.VERSION_17`).
* **SDK Config**:
  * `minSdk = 26` (Android 8.0 Oreo)
  * `targetSdk = 34` (Android 14)
  * `compileSdk = 34` (o superior compatible)

## 2. UI y Framework de Presentación
* **Jetpack Compose**: Material 3 (M3).
* **Compose BOM**: `2024.09.02`.
* **Kotlin Compiler Extension Version**: `1.5.14`.
* **Ciclo de vida y Estado**:
  * `androidx.activity:activity-compose:1.9.2`
  * `androidx.lifecycle:lifecycle-runtime-compose:2.8.6`
  * `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6`

## 3. Almacenamiento y Persistencia
* **Jetpack DataStore Preferences**: `androidx.datastore:datastore-preferences:1.1.1` (para almacenamiento clave-valor reactivo con Flow).
* **Serialización ligera**: `org.json:json` (nativo del SDK de Android para serialización de objetos en DataStore sin requerir librerías externas pesadas).

## 4. Procesamiento en Segundo Plano y Concurrencia
* **WorkManager**: `androidx.work:work-runtime-ktx:2.9.1` (implementado mediante `OneTimeWorkRequest` en bucle para intervalos cortos menores a 15 minutos, como 2 minutos).
* **Kotlin Coroutines**: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1`.

## 5. Red y Conectividad
* **Web Scraping / HTTP**: `org.jsoup:jsoup:1.17.2` (para análisis de vistas web públicas sin autenticación).

## 6. Configuración de Repositorios y Conectividad (Plugin Management)
* Es obligatorio incluir espejos de repositorio (ej. AliYun u otros espejos de Google Maven) en `settings.gradle.kts` si se opera en regiones con restricciones de red hacia `dl.google.com`:
  ```kotlin
  pluginManagement {
      repositories {
          maven { url = uri("https://maven.aliyun.com/repository/google") }
          maven { url = uri("https://maven.aliyun.com/repository/public") }
          google()
          mavenCentral()
          gradlePluginPortal()
      }
  }
  ```

## 7. Permisos y Componentes en AndroidManifest.xml
* `<uses-permission android:name="android.permission.INTERNET" />`
* `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` (Android 13+)
* `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />` (Reinicio automático del servicio)
* **BroadcastReceivers**:
  * `NotificationActionReceiver` (para acciones inline de la notificación permanente: Pausar/Reanudar).
  * `BootReceiver` (para restaurar tareas y notificaciones al encender el dispositivo).
