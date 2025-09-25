package org.client.scrcpy.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.text.TextUtils;

public final class Progress {

    private static ProgressDialog progressDialog;

    public static void showDialog(Activity context, String title) {
        showDialog(context, title, false);
    }

    public static void showDialog(Activity context, String title, String msg) {
        showDialog(context, title, msg, false);
    }

    public static void showDialog(Activity context, String title, boolean setup) {
        showDialog(context, title, "", setup);
    }

    @SuppressLint("InvalidWakeLockTag")
    public static void showDialog(Activity context, String title, String msg, boolean setup) {
        ThreadUtils.post(() -> {
            if (context == null || context.isFinishing()) {
                return;
            }
            if (progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;
            }
            Context application = context.getApplication();
            progressDialog = new ProgressDialog(context);
            // Configure the ProgressDialog message
            if (!TextUtils.isEmpty(msg)) {
                progressDialog.setTitle(title);
                progressDialog.setMessage(msg);
            } else {
                // Use the title as the ProgressDialog message
                progressDialog.setMessage(title);
            }
            // Prevent cancellation via the back button
            progressDialog.setCancelable(false);
            // Clear the reference when the dialog is dismissed

            if (setup) {
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(100);
            }
            progressDialog.show();
        });
    }

    public static void updateMessage(String msg) {
        ThreadUtils.post(() -> {
            if (progressDialog != null) {
                progressDialog.setMessage(msg);
            }
        });
    }

    public static void updateDialog(String title) {
        ThreadUtils.post(() -> {
            if (progressDialog != null) {
                progressDialog.setTitle(title);
//                progressDialog.setMessage(msg);
            }
        });
    }

    public static boolean isShowing() {
        return progressDialog != null && progressDialog.isShowing();
    }

    public static void updateDialog(int progress) {
        ThreadUtils.post(() -> {
            if (progressDialog != null) {
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(100);
                progressDialog.setProgress(progress);
            }
        });
    }

    public static void closeDialog() {
        ThreadUtils.post(() -> {
            if (progressDialog != null) {
                if (progressDialog.isShowing()) {
                    try {
                        progressDialog.dismiss();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                }
                progressDialog = null;
            }
        });
    }
}
