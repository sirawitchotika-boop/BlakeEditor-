package com.blake.editor.ui.preview

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Debug-only system-bar and camera-cutout treatment for Blake 2.0 screens.
 *
 * AuraCalc taught us to style the launch theme and the running Activity.
 * Unlike Capacitor, native Blake also has to draw a real background behind
 * the transparent Android 15+ status bar and respect display-cutout insets.
 */
object PreviewSystemBars {
    fun install(activity: ComponentActivity, root: View, background: Int) {
        val darkIcons = PreviewSafeArea.darkSystemIcons(background)
        activity.enableEdgeToEdge(
            statusBarStyle = if (darkIcons) {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(Color.TRANSPARENT)
            },
            navigationBarStyle = if (darkIcons) {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(Color.TRANSPARENT)
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes = activity.window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val base = SafeEdges(root.paddingLeft, root.paddingTop,
            root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val result = PreviewSafeArea.padding(
                base,
                SafeEdges(bars.left, bars.top, bars.right, bars.bottom),
                SafeEdges(cutout.left, cutout.top, cutout.right, cutout.bottom),
                keyboard
            )
            view.setPadding(result.left, result.top, result.right, result.bottom)
            insets
        }
        update(activity, root, background)
        ViewCompat.requestApplyInsets(root)
    }

    fun update(activity: ComponentActivity, root: View, background: Int) {
        // On API 35+ Android ignores statusBarColor: the root itself draws the
        // status-bar / camera region. On older versions it matches the theme.
        root.setBackgroundColor(background)
        activity.window.decorView.setBackgroundColor(background)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isStatusBarContrastEnforced = false
            activity.window.isNavigationBarContrastEnforced = false
        }
        val darkIcons = PreviewSafeArea.darkSystemIcons(background)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
