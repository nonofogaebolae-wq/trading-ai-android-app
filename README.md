# Trading AI Android App

This repository contains a starter Android app for a trading signal dashboard with Deriv-ready configuration.

It is intended for use as a foundation for:
- TradingView webhook signal handling
- Deriv WebSocket authorization
- BUY / SELL / HOLD trade actions
- Android UI for live signal management

## Project structure

- `app/` — Android application source and resources
- `settings.gradle.kts` — project settings
- `build.gradle.kts` — root project build configuration

## Build instructions

1. Install Android Studio.
2. Open the project folder.
3. Let Gradle sync complete.
4. Build the APK:
   - Build > Build Bundle(s) / APK(s) > Build APK(s)
5. APK output:
   - `app/build/outputs/apk/debug/app-debug.apk`

## Deriv configuration

The app expects:
- Deriv WebSocket URL: `wss://ws.deriv.com/websockets/v3`
- A valid Deriv API token
- A valid app ID, if required by your broker configuration

The app will send JSON payloads which you can adapt for your exact Deriv contract parameters.

## Important note

Direct live order execution depends on:
- a valid Deriv account
- correct API token
- authorized contract parameters for your trading mode
- proper account permissions

The code in this repository is a practical mobile starter and not a replacement for official Deriv API docs.
