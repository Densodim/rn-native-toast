import { requireNativeModule } from 'expo-modules-core';
import {
  DEFAULT_BLUR_STYLE,
  DEFAULT_DURATION,
  DEFAULT_HAPTIC,
  DEFAULT_POSITION,
  DEFAULT_QUEUE_MODE,
} from './constants';
import type { NativeToastConstants, NativeToastEvents, ToastConfig } from './types';

/**
 * Native module interface exposed by Expo Modules API.
 *
 * ASSUMPTION: Expo Modules API is available (expo >= 50).
 * The module uses the imperative Module API (not a View), which maps to
 * TurboModule on New Architecture and NativeModule on old bridge — backward
 * compatible with no extra work from the consumer.
 */
interface NativeToastModuleInterface {
  show(config: Required<ToastConfig>): void;
  hide(): void;
  hideAll(): void;
  getConstants(): NativeToastConstants;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

const NativeToastModule: NativeToastModuleInterface =
  requireNativeModule('NativeToast');

/**
 * Primary public API — imperative toast manager.
 *
 * Usage:
 * ```ts
 * import { NativeToast } from 'react-native-blur-toast';
 *
 * NativeToast.show({
 *   type: 'error',
 *   titleKey: 'common.error',
 *   messageKey: 'auth.invalid_credentials',
 * });
 * ```
 */
export const NativeToast = {
  /**
   * Show a toast notification.
   * Missing optional fields are filled with defaults.
   */
  show(config: ToastConfig): void {
    const resolved: Required<ToastConfig> = {
      type: config.type,
      titleKey: config.titleKey,
      messageKey: config.messageKey,
      duration: config.duration ?? DEFAULT_DURATION,
      position: config.position ?? DEFAULT_POSITION,
      haptic: config.haptic ?? DEFAULT_HAPTIC,
      blurStyle: config.blurStyle ?? DEFAULT_BLUR_STYLE,
      queueMode: config.queueMode ?? DEFAULT_QUEUE_MODE,
    };
    NativeToastModule.show(resolved);
  },

  /** Dismiss the currently visible toast */
  hide(): void {
    NativeToastModule.hide();
  },

  /** Dismiss all toasts (including queued) */
  hideAll(): void {
    NativeToastModule.hideAll();
  },

  /** Whether native blur is supported on this device */
  get supportsBlur(): boolean {
    return NativeToastModule.getConstants().supportsBlur;
  },

  /** Platform version (Android API level / iOS major version) */
  get platformVersion(): number {
    return NativeToastModule.getConstants().platformVersion;
  },
} as const;

export type { NativeToastEvents };
