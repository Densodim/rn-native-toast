# react-native-blur-toast

Native toast module for React Native with real blur background, 60fps animations, haptic feedback, and full accessibility support.

Built on **Expo Modules API** — supports both New Architecture (TurboModule) and legacy bridge.

---

## Installation

The module is located in `modules/rn-native-toast/` and is automatically linked by Expo.

### Requirements

| Dependency    | Version   |
|---------------|-----------|
| expo          | >= 50.0.0 |
| react         | >= 18.0.0 |
| react-native  | >= 0.73.0 |

After adding the module, rebuild the native apps:

```bash
npx expo prebuild --clean
npx expo run:ios
npx expo run:android
```

---

## Quick Start

### Using the `useToast` hook (recommended)

```tsx
import { useToast } from '@/features/toast'

function MyComponent() {
  const { showError, showSuccess, showWarning, showInfo, hide } = useToast()

  return (
    <>
      <Button onPress={() => showError('Invalid credentials')} title="Error" />
      <Button onPress={() => showSuccess('Profile updated')} title="Success" />
      <Button onPress={() => showWarning('Low battery')} title="Warning" />
      <Button onPress={() => showInfo('New version available')} title="Info" />
      <Button onPress={hide} title="Dismiss" />
    </>
  )
}
```

### Using `NativeToast` directly (imperative API)

```tsx
import { NativeToast } from 'react-native-blur-toast'

// Basic usage
NativeToast.show({
  type: 'error',
  titleKey: 'Error',
  messageKey: 'Something went wrong',
})

// With all options
NativeToast.show({
  type: 'success',
  titleKey: 'Success',
  messageKey: 'Your changes have been saved',
  duration: 3000,
  position: 'top',
  haptic: true,
  blurStyle: 'systemMaterial',
  queueMode: 'replace',
})

// Dismiss
NativeToast.hide()

// Dismiss all (including queued)
NativeToast.hideAll()
```

---

## API Reference

### `NativeToast.show(config: ToastConfig)`

Show a toast notification.

#### `ToastConfig`

| Property     | Type             | Required | Default            | Description                                       |
|-------------|------------------|----------|--------------------|---------------------------------------------------|
| `type`      | `ToastType`      | Yes      | —                  | Visual style: `'error'`, `'success'`, `'warning'`, `'info'` |
| `titleKey`  | `string`         | Yes      | —                  | Title text (use i18n key or plain text)            |
| `messageKey`| `string`         | Yes      | —                  | Message body (empty string to hide message)        |
| `duration`  | `number`         | No       | `4000`             | Auto-dismiss duration in ms. `0` = manual dismiss only |
| `position`  | `ToastPosition`  | No       | `'top'`            | Screen position: `'top'` or `'bottom'`             |
| `haptic`    | `boolean`        | No       | `true`             | Trigger haptic feedback on show                    |
| `blurStyle` | `BlurStyle`      | No       | `'systemMaterial'` | Blur background style                             |
| `queueMode` | `ToastQueueMode` | No       | `'replace'`        | Behavior when another toast is visible             |

### `NativeToast.hide()`

Dismiss the currently visible toast with animation.

### `NativeToast.hideAll()`

Dismiss all toasts immediately, including any queued.

### `NativeToast.supportsBlur: boolean`

Whether native blur is supported on this device.
- iOS: always `true` (iOS 10+)
- Android: always `true` — bitmap capture + stack blur works on all API levels (minSdk 24+)

### `NativeToast.platformVersion: number`

Platform version: iOS major version or Android API level.

---

## `useToast` Hook

Located in `src/features/toast/hooks/useToast.ts`. Provides convenience methods with automatic i18n title localization.

```tsx
const {
  show,         // (data: ToastData) => void — generic show
  hide,         // () => void — dismiss current toast
  showError,    // (message: string) => void
  showSuccess,  // (message: string, title?: string) => void
  showWarning,  // (message: string, title?: string) => void
  showInfo,     // (message: string, title?: string) => void
  showNative,   // (config: ToastConfig) => void — full native config
} = useToast()
```

### Methods

| Method        | Arguments                          | Default Title        |
|---------------|------------------------------------|--------------------|
| `showError`   | `(message: string)`                | `LL.TEXT.ERROR()`   |
| `showSuccess` | `(message: string, title?: string)`| `LL.TEXT.WELL_DONE()` |
| `showWarning` | `(message: string, title?: string)`| `LL.TEXT.ERROR()`   |
| `showInfo`    | `(message: string, title?: string)`| `''` (empty)        |
| `showNative`  | `(config: ToastConfig)`            | —                  |

### `ToastData`

```ts
type ToastData = {
  type: 'error' | 'success' | 'warning' | 'info'
  title: string
  message: string
}
```

---

## Types

### `ToastType`

```ts
type ToastType = 'error' | 'success' | 'warning' | 'info'
```

### `ToastPosition`

```ts
type ToastPosition = 'top' | 'bottom'
```

### `ToastQueueMode`

```ts
type ToastQueueMode = 'replace' | 'queue'
```

- **`replace`** — immediately dismisses the current toast and shows the new one
- **`queue`** — enqueues the new toast; shows after the current one is dismissed

### `BlurStyle`

```ts
type BlurStyle = 'light' | 'dark' | 'systemMaterial'
```

- **`light`** — light blur background
- **`dark`** — dark blur background
- **`systemMaterial`** — adapts to system light/dark mode automatically (recommended)

---

## Queue Modes

### Replace mode (default)

Each new toast replaces the current one instantly. Best for most use cases.

```ts
NativeToast.show({
  type: 'error',
  titleKey: 'Error',
  messageKey: 'First error',
})

// This immediately replaces the first toast
NativeToast.show({
  type: 'success',
  titleKey: 'Success',
  messageKey: 'Fixed!',
})
```

### Queue mode

New toasts wait in line. Each shows after the previous one is dismissed.

```ts
NativeToast.show({
  type: 'info',
  titleKey: 'Step 1',
  messageKey: 'Uploading...',
  queueMode: 'queue',
  duration: 2000,
})

NativeToast.show({
  type: 'info',
  titleKey: 'Step 2',
  messageKey: 'Processing...',
  queueMode: 'queue',
  duration: 2000,
})

NativeToast.show({
  type: 'success',
  titleKey: 'Done',
  messageKey: 'Upload complete!',
  queueMode: 'queue',
})
```

---

## Usage Examples

### Form validation error

```tsx
const { showError } = useToast()

const onSubmit = async (data: FormData) => {
  try {
    await api.register(data)
  } catch (error) {
    showError(error.message)
  }
}
```

### Success after action

```tsx
const { showSuccess } = useToast()

const onSave = async () => {
  await api.saveProfile(profileData)
  showSuccess('Profile saved successfully')
}
```

### Custom title

```tsx
const { showSuccess } = useToast()

showSuccess('You earned 100 points!', 'Achievement Unlocked')
```

### Manual dismiss (persistent toast)

```tsx
NativeToast.show({
  type: 'info',
  titleKey: 'Uploading',
  messageKey: 'Please wait...',
  duration: 0, // won't auto-dismiss
})

// Later, when upload completes:
NativeToast.hide()
```

### Bottom position

```tsx
NativeToast.show({
  type: 'info',
  titleKey: 'Tip',
  messageKey: 'Swipe left to delete',
  position: 'bottom',
})
```

### Without haptic feedback

```tsx
NativeToast.show({
  type: 'info',
  titleKey: 'Note',
  messageKey: 'Silent notification',
  haptic: false,
})
```

### Full advanced config

```tsx
NativeToast.show({
  type: 'warning',
  titleKey: 'Warning',
  messageKey: 'Your session will expire in 5 minutes',
  duration: 6000,
  position: 'top',
  haptic: true,
  blurStyle: 'dark',
  queueMode: 'queue',
})
```

---

## Events

The native module emits lifecycle events:

| Event          | Payload                                              |
|---------------|------------------------------------------------------|
| `onToastShow` | `{ id: string, type: ToastType }`                     |
| `onToastHide` | `{ id: string, type: ToastType, reason: 'timeout' \| 'manual' \| 'replaced' }` |

---

## Architecture

### iOS

- **UIWindow overlay** at `.alert + 1` — renders above navigation, modals, keyboard
- **UIVisualEffectView** for blur — GPU-composited, ~0.5ms per frame
- **Spring animations** via `UIView.animate` with damping
- **Safe Area & Dynamic Island** — automatic via `safeAreaInsets`
- **Passthrough touches** — only the toast captures touches, rest passes through

### Android

- **WindowManager overlay** — renders above all views
- **Bitmap capture + Stack Blur** — captures decorView content, crops to toast area, applies stack blur algorithm. Works on all API levels (minSdk 24+)
- **Frosted glass effect** — blurred bitmap displayed as ImageView with tint overlay
- **ObjectAnimator** with hardware layer acceleration
- **WeakReference** for Activity — prevents memory leaks

### Shared

- **Haptic feedback** — `UIImpactFeedbackGenerator` (iOS), `HapticFeedbackConstants` (Android)
- **RTL support** — automatic layout mirroring
- **Accessibility** — live region announcements, Reduce Motion support
- **Dark mode** — `systemMaterial` blur auto-adapts
- **App lifecycle** — timers pause in background, resume on foreground
- **Memory warnings** — queue cleared on low memory (iOS)

---

## File Structure

```
modules/rn-native-toast/
├── src/
│   ├── index.ts              # Public API exports
│   ├── NativeToast.ts        # Imperative API wrapper
│   ├── types.ts              # TypeScript type definitions
│   └── constants.ts          # Default configuration values
├── ios/
│   ├── NativeToastModule.swift    # Expo bridge (no logic)
│   ├── ToastManager.swift         # Lifecycle manager singleton
│   ├── ToastView.swift            # UIKit toast view
│   ├── ToastAnimator.swift        # Animation orchestrator
│   ├── ToastConfiguration.swift   # Data structures & enums
│   ├── BlurViewFactory.swift      # UIVisualEffectView factory
│   └── Extensions/
│       └── UIColor+Toast.swift    # Color helpers
├── android/src/main/java/expo/modules/nativetoast/
│   ├── NativeToastModule.kt      # Expo bridge (no logic)
│   ├── ToastManager.kt           # Lifecycle manager singleton
│   ├── ToastView.kt              # Android View toast
│   ├── ToastAnimator.kt          # ObjectAnimator orchestrator
│   ├── ToastConfig.kt            # Data classes & enums
│   ├── BlurFactory.kt            # Blur background factory (bitmap / fallback)
│   ├── BitmapBlurHelper.kt       # DecorView capture + stack blur algorithm
│   └── util/
│       └── MainThreadEnforcer.kt # Thread-safety utility
├── package.json
└── expo-module.config.json
```

### Application integration

```
src/features/toast/
├── index.ts                 # Public exports (NativeToast, useToast, types)
├── hooks/
│   ├── index.ts
│   └── useToast.ts          # React hook with i18n
└── types/
    ├── index.ts
    └── toast.types.ts       # ToastData, ToastType
```

---

## Imports

```tsx
// Hook (recommended for components)
import { useToast } from '@/features/toast'

// Direct native API (outside components or for advanced config)
import { NativeToast } from '@/features/toast'
// or
import { NativeToast } from 'react-native-blur-toast'

// Types
import type { ToastConfig, ToastType, ToastData } from '@/features/toast'
```

---

## Defaults

| Property    | Default Value      |
|-------------|-------------------|
| `duration`  | `4000` (4 seconds) |
| `position`  | `'top'`           |
| `haptic`    | `true`            |
| `blurStyle` | `'systemMaterial'` |
| `queueMode` | `'replace'`       |
