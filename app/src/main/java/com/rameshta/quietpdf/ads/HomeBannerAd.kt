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
fun HomeBannerAd(adUnitId: String, modifier: Modifier = Modifier) {
    if (adUnitId.isBlank()) return
    val context = LocalContext.current
    var status by remember(adUnitId) { mutableStateOf(BannerStatus.Loading) }
    if (status == BannerStatus.Failed) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember(context, widthDp) {
            AdSize.getInlineAdaptiveBannerAdSize(widthDp, 60)
        }
        val adView = remember(context, adUnitId, widthDp) {
            AdView(context).apply {
                this.adUnitId = adUnitId
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
            }
        }
        DisposableEffect(adView) {
            onDispose { adView.destroy() }
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
                    modifier = Modifier.fillMaxWidth().height(adSize.height.dp)
                        .alpha(if (status == BannerStatus.Loaded) 1f else 0f),
                )
            }
        }
    }
}

private enum class BannerStatus { Loading, Loaded, Failed }
