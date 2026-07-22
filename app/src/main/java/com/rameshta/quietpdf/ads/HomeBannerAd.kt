package com.rameshta.quietpdf.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.rameshta.quietpdf.R

@Composable
fun rememberHomeBannerAdSession(adUnitId: String, enabled: Boolean): HomeBannerAdSession? {
    val context = LocalContext.current
    val session = remember(context, adUnitId, enabled) {
        if (enabled && adUnitId.isNotBlank()) HomeBannerAdSession(context, adUnitId) else null
    }
    DisposableEffect(session) {
        onDispose { session?.destroy() }
    }
    return session
}

@Composable
fun HomeBannerAd(session: HomeBannerAdSession, modifier: Modifier = Modifier) {
    if (session.status == BannerStatus.Failed) return
    val context = LocalContext.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember(context, widthDp) {
            AdSize.getInlineAdaptiveBannerAdSize(widthDp, 60)
        }
        val adView = remember(session, widthDp) { session.adView(widthDp, adSize) }
        DisposableEffect(adView) {
            adView.resume()
            onDispose { adView.pause() }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("home_banner_ad"),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.advertisement_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                AndroidView(
                    factory = { adView },
                    // Inline adaptive sizes report their final creative height only after the
                    // request is resolved. Using AdSize.height here can collapse the AndroidView
                    // to zero even though the SDK successfully loaded an ad.
                    modifier = Modifier.fillMaxWidth().height(MaxInlineBannerHeight)
                        .alpha(if (session.status == BannerStatus.Loaded) 1f else 0f),
                )
            }
        }
    }
}

class HomeBannerAdSession internal constructor(
    private val context: android.content.Context,
    private val adUnitId: String,
) {
    internal var status by mutableStateOf(BannerStatus.Loading)
        private set

    private var retainedAdView: AdView? = null
    private var retainedWidthDp: Int? = null

    internal fun adView(widthDp: Int, adSize: AdSize): AdView {
        retainedAdView?.takeIf { retainedWidthDp == widthDp }?.let { return it }
        retainedAdView?.destroy()
        status = BannerStatus.Loading
        return AdView(context).apply {
            this.adUnitId = this@HomeBannerAdSession.adUnitId
            setAdSize(adSize)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    status = BannerStatus.Loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    status = BannerStatus.Failed
                }
            }
            loadAd(AdRequest.Builder().build())
            retainedAdView = this
            retainedWidthDp = widthDp
        }
    }

    internal fun destroy() {
        retainedAdView?.destroy()
        retainedAdView = null
        retainedWidthDp = null
    }
}

private val MaxInlineBannerHeight = 60.dp

internal enum class BannerStatus { Loading, Loaded, Failed }
