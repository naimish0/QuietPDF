package com.rameshta.quietpdf.ads

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.rameshta.quietpdf.R

@Composable
fun HomeNativeAd(
    adUnitId: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val session = rememberHomeNativeAdSession(adUnitId, enabled)
    val nativeAd = session?.nativeAd ?: return
    val context = LocalContext.current
    val advertisementLabel = stringResource(R.string.advertisement_label)
    val colors = NativeAdColors(
        surface = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
        primary = MaterialTheme.colorScheme.primary.toArgb(),
        onPrimary = MaterialTheme.colorScheme.onPrimary.toArgb(),
        text = MaterialTheme.colorScheme.onSurface.toArgb(),
        secondaryText = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
    )

    Surface(
        modifier = modifier.fillMaxWidth().testTag("home_native_ad"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        AndroidView(
            factory = {
                createNativeAdView(context, advertisementLabel, colors).also { view ->
                    bindNativeAd(view, nativeAd)
                }
            },
            update = { view -> bindNativeAd(view, nativeAd) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
        )
    }
}

@Composable
internal fun rememberHomeNativeAdSession(
    adUnitId: String,
    enabled: Boolean,
): HomeNativeAdSession? {
    val applicationContext = LocalContext.current.applicationContext
    val session = remember(applicationContext, adUnitId, enabled) {
        if (enabled && adUnitId.isNotBlank()) {
            HomeNativeAdSession(applicationContext, adUnitId)
        } else null
    }
    DisposableEffect(session) {
        onDispose { session?.destroy() }
    }
    return session
}

internal class HomeNativeAdSession(
    context: Context,
    adUnitId: String,
) {
    internal var nativeAd by mutableStateOf<NativeAd?>(null)
        private set

    private var destroyed = false

    init {
        AdLoader.Builder(context, adUnitId)
            .forNativeAd { loadedAd ->
                if (destroyed) {
                    loadedAd.destroy()
                } else {
                    nativeAd?.destroy()
                    nativeAd = loadedAd
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAd = null
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    internal fun destroy() {
        destroyed = true
        nativeAd?.destroy()
        nativeAd = null
    }
}

private data class NativeAdColors(
    val surface: Int,
    val primary: Int,
    val onPrimary: Int,
    val text: Int,
    val secondaryText: Int,
)

private fun createNativeAdView(
    context: Context,
    advertisementLabel: String,
    colors: NativeAdColors,
): NativeAdView {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val nativeAdView = NativeAdView(context).apply {
        setPadding(dp(16), dp(12), dp(16), dp(16))
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val attribution = TextView(context).apply {
        text = advertisementLabel
        setTextColor(colors.onPrimary)
        textSize = 11f
        gravity = Gravity.CENTER
        minWidth = dp(20)
        minHeight = dp(20)
        setPadding(dp(6), 0, dp(6), 0)
        background = GradientDrawable().apply {
            setColor(colors.primary)
            cornerRadius = dp(4).toFloat()
        }
    }
    content.addView(
        attribution,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.START
            marginEnd = dp(32) // Keeps the SDK-provided AdChoices corner unobstructed.
            bottomMargin = dp(10)
        },
    )

    val media = MediaView(context).apply {
        setBackgroundColor(colors.surface)
    }
    content.addView(
        media,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).apply {
            bottomMargin = dp(12)
        },
    )

    val details = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val icon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    details.addView(
        icon,
        LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) },
    )
    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    val headline = TextView(context).apply {
        setTextColor(colors.text)
        textSize = 17f
        maxLines = 2
    }
    val body = TextView(context).apply {
        setTextColor(colors.secondaryText)
        textSize = 13f
        maxLines = 2
    }
    textColumn.addView(headline)
    textColumn.addView(body)
    details.addView(
        textColumn,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
    )
    content.addView(details)

    val callToAction = Button(context).apply {
        isAllCaps = false
        minHeight = dp(48)
    }
    content.addView(
        callToAction,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(12)
        },
    )

    nativeAdView.addView(content)
    nativeAdView.mediaView = media
    nativeAdView.iconView = icon
    nativeAdView.headlineView = headline
    nativeAdView.bodyView = body
    nativeAdView.callToActionView = callToAction
    return nativeAdView
}

private fun bindNativeAd(view: NativeAdView, nativeAd: NativeAd) {
    (view.headlineView as TextView).text = nativeAd.headline

    (view.bodyView as TextView).apply {
        text = nativeAd.body.orEmpty()
        visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    (view.iconView as ImageView).apply {
        setImageDrawable(nativeAd.icon?.drawable)
        visibility = if (nativeAd.icon == null) View.GONE else View.VISIBLE
    }
    (view.callToActionView as Button).apply {
        text = nativeAd.callToAction.orEmpty()
        visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    view.mediaView?.mediaContent = nativeAd.mediaContent
    view.setNativeAd(nativeAd)
}
