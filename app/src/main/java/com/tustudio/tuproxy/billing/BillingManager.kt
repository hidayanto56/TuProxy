package com.tustudio.tuproxy.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-time "Remove ads" purchase.
 * Setup in Play Console: Monetize -> Products -> In-app products -> create
 * product ID "tuproxy_remove_ads" (managed, one-time) before this can sell.
 * Until then the button shows a message and ads keep showing.
 */
object BillingManager {
    const val PRODUCT_REMOVE_ADS = "tuproxy_remove_ads"

    /** Consumable one-time tips; buyers can repeat them. Prices are set in Play Console. */
    const val PRODUCT_DONATE_SMALL = "tuproxy_donate_small"
    const val PRODUCT_DONATE_MEDIUM = "tuproxy_donate_medium"
    const val PRODUCT_DONATE_LARGE = "tuproxy_donate_large"
    val DONATE_PRODUCTS = listOf(PRODUCT_DONATE_SMALL, PRODUCT_DONATE_MEDIUM, PRODUCT_DONATE_LARGE)

    private val _pro = MutableStateFlow(false)
    val pro: StateFlow<Boolean> = _pro.asStateFlow()

    private var client: BillingClient? = null
    private var connecting = false

    fun init(context: Context) {
        if (client != null) return
        val listener = PurchasesUpdatedListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                purchases.forEach { handlePurchase(it) }
            }
        }
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener(listener)
            .enablePendingPurchases()
            .build()
        connect { refreshPro() }
    }

    fun buyRemoveAds(activity: Activity, onMessage: (String) -> Unit) {
        val c = client
        if (c == null || !c.isReady) {
            connect {
                if (c?.isReady == true) buyRemoveAds(activity, onMessage)
                else onMessage("Billing unavailable — check your connection and try again.")
            }
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_REMOVE_ADS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        c.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || details.isEmpty()) {
                onMessage("Remove-ads product not found yet — create \"$PRODUCT_REMOVE_ADS\" in Play Console first.")
                return@queryProductDetailsAsync
            }
            launchFlow(activity, details.first(), onMessage)
        }
    }

    private fun launchFlow(activity: Activity, details: ProductDetails, onMessage: (String) -> Unit) {
        val c = client ?: return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = c.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage("Could not open purchase sheet (${result.responseCode}).")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.products.contains(PRODUCT_REMOVE_ADS)) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                _pro.value = true
                if (!purchase.isAcknowledged) acknowledge(purchase)
            }
            return
        }
        // Donations are consumable so supporters can tip again.
        if (purchase.products.any { it in DONATE_PRODUCTS } &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        ) {
            consume(purchase)
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val c = client ?: return
        c.acknowledgePurchase(
            com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { _ -> }
    }

    private fun consume(purchase: Purchase) {
        val c = client ?: return
        c.consumeAsync(
            com.android.billingclient.api.ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { _, _ -> }
    }

    /** One-off tip of any tier; message explains setup until products exist. */
    fun donate(activity: Activity, productId: String, onMessage: (String) -> Unit) {
        val c = client
        if (c == null || !c.isReady) {
            connect {
                if (c?.isReady == true) donate(activity, productId, onMessage)
                else onMessage("Billing unavailable — check your connection and try again.")
            }
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        c.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || details.isEmpty()) {
                onMessage("Donations aren't set up yet — create \"$productId\" in Play Console first.")
                return@queryProductDetailsAsync
            }
            launchFlow(activity, details.first(), onMessage)
        }
    }

    fun refreshPro() {
        val c = client ?: return
        if (!c.isReady) return
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { _, purchases ->
            _pro.value = purchases.any {
                it.products.contains(PRODUCT_REMOVE_ADS) &&
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
        }
    }

    private fun connect(done: () -> Unit) {
        val c = client ?: return
        if (c.isReady || connecting) return
        connecting = true
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) done()
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
            }
        })
    }
}
