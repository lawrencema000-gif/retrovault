package com.retrovault.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Play Billing v7 client for the one-time "pulsar_gold_unlock". On a PURCHASED result the token is
 * SERVER-VERIFIED (Supabase edge fn → Play Developer API) BEFORE the entitlement is granted, then
 * the purchase is acknowledged (mandatory within 3 days or Play auto-refunds). restore() and the
 * on-connect query re-verify owned tokens, so Gold survives reinstall.
 *
 * The live flow is staged: it needs a Play Console listing + merchant account and the server-side
 * service account. The code path compiles + links against Play Billing in the `full` variant.
 */
class PlayBillingManager(private val context: Context) : BillingManager {

    private val entitlements = Entitlements(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val http = OkHttpClient()
    @Volatile private var productDetails: ProductDetails? = null

    override val isGold: Boolean get() = entitlements.isGold
    override val purchaseSupported: Boolean = true

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { p ->
                if (p.purchaseState == Purchase.PurchaseState.PURCHASED) verifyThenGrant(p)
            }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener(purchasesListener)
        .build()

    init { connect() }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    restore()
                }
            }
            override fun onBillingServiceDisconnected() { /* reconnect lazily on next use */ }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(MonetizationConfig.GOLD_PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        ).build()
        client.queryProductDetailsAsync(params) { _, list -> productDetails = list.firstOrNull() }
    }

    override fun purchaseGold(activity: Activity) {
        val pd = productDetails ?: run { queryProduct(); return } // not ready yet — refetch, user retries
        val flowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build())
        ).build()
        client.launchBillingFlow(activity, flowParams)
    }

    override fun restore() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { _, owned ->
            owned.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { verifyThenGrant(it) }
        }
    }

    private fun verifyThenGrant(p: Purchase) {
        scope.launch {
            if (!verifyOnServer(p.purchaseToken)) return@launch  // never grant on an unverified token
            if (!p.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                ) { _ -> }
            }
            entitlements.isGold = true  // local mirror of the server-verified purchase
        }
    }

    /** POST the token to the Supabase verify-purchase edge fn; grant only on {"verified":true}. */
    private fun verifyOnServer(purchaseToken: String): Boolean = runCatching {
        val payload = JSONObject()
            .put("packageName", context.packageName)
            .put("productId", MonetizationConfig.GOLD_PRODUCT_ID)
            .put("purchaseToken", purchaseToken)
            .toString()
            .toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url(MonetizationConfig.VERIFY_PURCHASE_URL).post(payload).build())
            .execute().use { resp ->
                if (!resp.isSuccessful) return false  // fail-closed on any non-200 from the server
                JSONObject(resp.body?.string() ?: "{}").optBoolean("verified", false)
            }
    }.getOrDefault(false)
}
