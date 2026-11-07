package cz.helicode.hondaescanner;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class AppToast {

    public static final int LENGTH_SHORT = Toast.LENGTH_SHORT;
    public static final int LENGTH_LONG = Toast.LENGTH_LONG;

    private static View sCurrentToastView = null;
    private static Runnable sDismissRunnable = null;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    public static void show(Context context, int stringResId) {
        show(context, stringResId, LENGTH_SHORT);
    }

    public static void show(Context context, int stringResId, int duration) {
        if (context == null) return;
        show(context, context.getString(stringResId), duration);
    }

    public static void show(Context context, CharSequence message) {
        show(context, message, LENGTH_SHORT);
    }

    public static void show(Context context, CharSequence message, int duration) {
        if (context == null || message == null) return;

        Activity activity = null;
        if (context instanceof Activity) {
            activity = (Activity) context;
        } else {
            activity = HondaEApplication.getCurrentActivity();
        }

        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            // Fallback for non-activity context
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Toast.makeText(context, message, duration).show();
            } else {
                final Activity finalActivity = activity;
                sMainHandler.post(() -> Toast.makeText(context, message, duration).show());
            }
            return;
        }

        final Activity act = activity;
        final CharSequence text = message;
        final int dur = duration;

        Runnable showTask = () -> {
            if (act.isFinishing() || act.isDestroyed()) return;

            ViewGroup root = act.findViewById(android.R.id.content);
            if (root == null) {
                Toast.makeText(act, text, dur).show();
                return;
            }

            // Remove currently visible toast if any
            if (sCurrentToastView != null) {
                if (sCurrentToastView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) sCurrentToastView.getParent()).removeView(sCurrentToastView);
                }
                sCurrentToastView = null;
            }
            if (sDismissRunnable != null) {
                sMainHandler.removeCallbacks(sDismissRunnable);
                sDismissRunnable = null;
            }

            LayoutInflater inflater = LayoutInflater.from(act);
            View toastView = inflater.inflate(R.layout.view_app_toast, root, false);

            ImageView iconView = toastView.findViewById(R.id.toast_icon);
            TextView textView = toastView.findViewById(R.id.toast_text);

            int iconRes = AppIconHelper.getSelectedIconDrawableId(act);
            if (iconView != null) {
                iconView.setImageResource(iconRes);
            }
            if (textView != null) {
                textView.setText(text);
            }

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = dpToPx(act, 80);
            toastView.setLayoutParams(lp);

            toastView.setAlpha(0f);
            toastView.setScaleX(0.85f);
            toastView.setScaleY(0.85f);
            root.addView(toastView);
            sCurrentToastView = toastView;

            toastView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160)
                    .start();

            long delay = (dur == LENGTH_LONG) ? 3500L : 2000L;
            sDismissRunnable = () -> {
                if (sCurrentToastView == toastView && toastView.getParent() != null) {
                    toastView.animate()
                            .alpha(0f)
                            .scaleX(0.85f)
                            .scaleY(0.85f)
                            .setDuration(160)
                            .withEndAction(() -> {
                                if (toastView.getParent() instanceof ViewGroup) {
                                    ((ViewGroup) toastView.getParent()).removeView(toastView);
                                }
                                if (sCurrentToastView == toastView) {
                                    sCurrentToastView = null;
                                }
                            })
                            .start();
                }
            };
            sMainHandler.postDelayed(sDismissRunnable, delay);
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            showTask.run();
        } else {
            act.runOnUiThread(showTask);
        }
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }
}
