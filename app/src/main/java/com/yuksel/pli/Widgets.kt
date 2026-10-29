package com.yuksel.pli

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup

/** Hosts the Android widgets of the "Today" column and runs the add (bind + configure) flow. */
class Widgets(private val activity: Activity) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, HOST_ID)
    private val views = HashMap<Int, AppWidgetHostView>()
    private var pending: Pair<Int, AppWidgetProviderInfo>? = null
    private var onAdded: ((Int, AppWidgetProviderInfo) -> Unit)? = null

    fun start() = runCatching { host.startListening() }
    fun stop() = runCatching { host.stopListening() }

    /** Providers for every profile, grouped by app label. */
    fun providers(): List<AppWidgetProviderInfo> = runCatching { manager.installedProviders }.getOrDefault(emptyList())

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun label(info: AppWidgetProviderInfo): String = runCatching { info.loadLabel(activity.packageManager) }.getOrNull() ?: ""
    fun appLabel(info: AppWidgetProviderInfo): String = runCatching {
        activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(info.provider.packageName, 0)).toString()
    }.getOrDefault(info.provider.packageName)
    fun preview(info: AppWidgetProviderInfo): Drawable? = runCatching {
        info.loadPreviewImage(activity, activity.resources.displayMetrics.densityDpi) ?: info.loadIcon(activity, activity.resources.displayMetrics.densityDpi)
    }.getOrNull()

    fun view(@Suppress("UNUSED_PARAMETER") context: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        val view = views.getOrPut(id) { host.createView(activity, id, info) }
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    fun resize(id: Int, widthDp: Float, heightDp: Float) = runCatching {
        views[id]?.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
    }

    fun delete(id: Int) {
        views.remove(id)
        runCatching { host.deleteAppWidgetId(id) }
    }

    fun begin(info: AppWidgetProviderInfo, added: (Int, AppWidgetProviderInfo) -> Unit) {
        onAdded = added
        val id = host.allocateAppWidgetId()
        pending = id to info
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)
        if (bound) configureOrFinish(id, info)
        else runCatching {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile), REQUEST_BIND)
        }.onFailure { cancel() }
    }

    private fun configureOrFinish(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL == 0) {
            runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null) }
                .onFailure { finish() }
        } else finish()
    }

    private fun finish() {
        val (id, info) = pending ?: return
        pending = null
        onAdded?.invoke(id, info)
    }

    private fun cancel() {
        pending?.first?.let { runCatching { host.deleteAppWidgetId(it) } }
        pending = null
    }

    /** Returns true when the result belonged to the widget flow. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        val (id, info) = pending ?: return requestCode == REQUEST_BIND || requestCode == REQUEST_CONFIGURE
        when (requestCode) {
            REQUEST_BIND -> if (resultCode == Activity.RESULT_OK) configureOrFinish(id, info) else cancel()
            REQUEST_CONFIGURE -> if (resultCode == Activity.RESULT_OK) finish() else cancel()
            else -> return false
        }
        return true
    }

    companion object {
        const val HOST_ID = 0x504C49
        const val REQUEST_BIND = 4101
        const val REQUEST_CONFIGURE = 4102
    }
}
