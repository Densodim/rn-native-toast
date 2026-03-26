/**
 * Toast type variants
 */
export type ToastType = 'error' | 'success' | 'warning' | 'info';

/**
 * Toast display position
 */
export type ToastPosition = 'top' | 'bottom';

/**
 * Queue behavior when multiple toasts are triggered
 * - 'replace': immediately replaces current toast with new one
 * - 'queue': enqueues new toast, shows after current one dismisses
 */
export type ToastQueueMode = 'replace' | 'queue';

/**
 * Blur style for the toast background
 * iOS maps to UIBlurEffect.Style, Android to RenderEffect radius
 */
export type BlurStyle = 'light' | 'dark' | 'systemMaterial';

/**
 * Configuration for a single toast notification
 */
export type ToastConfig = {
  /** Toast variant — determines icon and color scheme */
  type: ToastType;

  /** i18n key for the title. Raw strings are NOT allowed — localize before passing. */
  titleKey: string;

  /** i18n key for the message body */
  messageKey: string;

  /** Auto-dismiss duration in ms (0 = manual dismiss only). Default: 4000 */
  duration?: number;

  /** Screen position. Default: 'top' */
  position?: ToastPosition;

  /** Trigger haptic feedback on show. Default: true */
  haptic?: boolean;

  /** Blur style. Default: 'systemMaterial' */
  blurStyle?: BlurStyle;

  /** Queue behavior. Default: 'replace' */
  queueMode?: ToastQueueMode;
};

/**
 * Read-only module constants exposed from native
 */
export type NativeToastConstants = {
  /** Whether real blur is supported on this device */
  supportsBlur: boolean;
  /** Android API level or iOS major version */
  platformVersion: number;
};

/**
 * Events emitted by the native module
 */
export type NativeToastEvents = {
  onToastShow: { id: string; type: ToastType };
  onToastHide: { id: string; type: ToastType; reason: 'timeout' | 'manual' | 'replaced' | 'swipe' };
};
