package com.gxdevs.lore.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Google Play Billing Manager for Aethra+.
 *
 * Supported Products:
 * 1. [PRODUCT_MONTHLY]:  `lore_sanctuary_monthly` (Monthly Subscription)
 * 2. [PRODUCT_ANNUAL]:   `lore_sanctuary_annual`  (Annual Subscription - 50% OFF)
 * 3. [PRODUCT_LIFETIME]: `lore_sanctuary`         (Lifetime Sanctuary Pass - In-App Product)
 *
 * Uses Play Billing Library v9 API.
 * Entitlement is stored in DataStore via SettingsRepository so it
 * survives app restarts offline.
 */
class PremiumManager private constructor(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        const val TAG = "PremiumManager"

        const val PRODUCT_MONTHLY  = "lore_sanctuary_monthly"
        const val PRODUCT_ANNUAL   = "lore_sanctuary_annual"
        const val PRODUCT_LIFETIME = "lore_sanctuary"

        @Volatile
        private var INSTANCE: PremiumManager? = null

        fun getInstance(context: Context): PremiumManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PremiumManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val settingsRepo = com.gxdevs.lore.data.SettingsRepository(context)

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _productDetailsList = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetailsList: StateFlow<List<ProductDetails>> = _productDetailsList.asStateFlow()

    private val _billingStatus = MutableStateFlow<String>("Initializing")
    val billingStatus: StateFlow<String> = _billingStatus.asStateFlow()

    private var billingClient: BillingClient? = null

    init {
        // Restore local entitlement from DataStore
        scope.launch {
            settingsRepo.isPremiumUnlocked.collect { unlocked ->
                _isPremium.value = unlocked
            }
        }
        setupBillingClient()
    }

    private fun setupBillingClient() {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        connectToGooglePlay()
    }

    private fun connectToGooglePlay() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _billingStatus.value = "Connected"
                    Log.i(TAG, "✅ [LoreBilling] Billing setup finished successfully (OK)")
                    queryAvailableProducts()
                    queryExistingPurchases()
                } else {
                    _billingStatus.value = "Setup Failed (${billingResult.responseCode})"
                    Log.e(TAG, "❌ [LoreBilling] Billing setup failed. Code=${billingResult.responseCode}, Msg=${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingStatus.value = "Disconnected"
                Log.w(TAG, "⚠️ [LoreBilling] Billing service disconnected. Reconnecting in 5s...")
                scope.launch {
                    kotlinx.coroutines.delay(5_000)
                    connectToGooglePlay()
                }
            }
        })
    }

    /** Queries available products (Subscriptions & Lifetime IAP) from Google Play Console. */
    fun queryAvailableProducts() {
        val client = billingClient ?: return
        if (!client.isReady) {
            Log.w(TAG, "⚠️ [LoreBilling] Cannot query products — BillingClient not ready")
            return
        }

        Log.d(TAG, "🔍 [LoreBilling] Querying product details for: $PRODUCT_MONTHLY, $PRODUCT_ANNUAL, $PRODUCT_LIFETIME")

        val subsList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ANNUAL)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val inappList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val accumulatedProducts = mutableListOf<ProductDetails>()

        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subsList)
            .build()

        client.queryProductDetailsAsync(subsParams) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "✅ [LoreBilling] SUBS products fetched: ${queryResult.productDetailsList.size} item(s)")
                queryResult.productDetailsList.forEach { details ->
                    val price = details.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "N/A"
                    Log.d(TAG, "   ↳ Sub Product: id=${details.productId}, title=${details.title}, price=$price")
                }
                accumulatedProducts.addAll(queryResult.productDetailsList)
            } else {
                Log.w(TAG, "⚠️ [LoreBilling] queryProductDetailsAsync SUBS failed: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")
            }

            val inappParams = QueryProductDetailsParams.newBuilder()
                .setProductList(inappList)
                .build()

            client.queryProductDetailsAsync(inappParams) { inappResult, inappQueryResult ->
                if (inappResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "✅ [LoreBilling] INAPP products fetched: ${inappQueryResult.productDetailsList.size} item(s)")
                    inappQueryResult.productDetailsList.forEach { details ->
                        val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: "N/A"
                        Log.d(TAG, "   ↳ InApp Product: id=${details.productId}, title=${details.title}, price=$price")
                    }
                    accumulatedProducts.addAll(inappQueryResult.productDetailsList)
                } else {
                    Log.w(TAG, "⚠️ [LoreBilling] queryProductDetailsAsync INAPP failed: code=${inappResult.responseCode}, msg=${inappResult.debugMessage}")
                }
                _productDetailsList.value = accumulatedProducts.toList()
                _billingStatus.value = "Products Loaded (${accumulatedProducts.size})"
                Log.i(TAG, "📦 [LoreBilling] Total available products ready: ${accumulatedProducts.size}")
            }
        }
    }

    /** Queries existing purchases to restore entitlements (SUBS & INAPP). */
    fun queryExistingPurchases() {
        val client = billingClient ?: return
        if (!client.isReady) {
            Log.w(TAG, "⚠️ [LoreBilling] Cannot query purchases — BillingClient not ready")
            return
        }

        Log.d(TAG, "🔎 [LoreBilling] Checking active purchases on Google Play...")

        // Check Subscriptions
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "✅ [LoreBilling] Query SUBS purchases result: ${purchases.size} purchase(s) found")
                processPurchases(purchases)
            } else {
                Log.w(TAG, "⚠️ [LoreBilling] Query SUBS purchases error: ${billingResult.debugMessage}")
            }
        }

        // Check Lifetime In-App Purchases
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "✅ [LoreBilling] Query INAPP purchases result: ${purchases.size} purchase(s) found")
                processPurchases(purchases)
            } else {
                Log.w(TAG, "⚠️ [LoreBilling] Query INAPP purchases error: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Launches the Google Play billing purchase flow for selected [productId].
     * Defaults to [PRODUCT_ANNUAL] if not specified.
     */
    fun launchPurchaseFlow(activity: Activity, productId: String = PRODUCT_ANNUAL, onResult: (Boolean, String) -> Unit) {
        val client = billingClient
        if (client == null || !client.isReady) {
            Log.w(TAG, "❌ [LoreBilling] Billing client not ready for launchPurchaseFlow — reconnecting")
            connectToGooglePlay()
            onResult(false, "Connecting to Google Play... Please try again.")
            return
        }

        Log.i(TAG, "🚀 [LoreBilling] Launching purchase flow for productId: $productId")
        val details = _productDetailsList.value.find { it.productId == productId }
        if (details == null) {
            Log.w(TAG, "⚠️ [LoreBilling] Product details not found for $productId in cache (${_productDetailsList.value.size} items available). Re-querying...")
            queryAvailableProducts()
            onResult(false, "Loading product details from Google Play... Please try again in a moment.")
            return
        }

        val productDetailsParamsList = if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
            Log.d(TAG, "   ↳ Preparing SUBS purchase: offerToken=${if (offerToken.isNotBlank()) "VALID" else "EMPTY"}")
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            )
        } else {
            Log.d(TAG, "   ↳ Preparing INAPP lifetime purchase")
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            )
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val response = client.launchBillingFlow(activity, billingFlowParams)
        Log.i(TAG, "💳 [LoreBilling] launchBillingFlow returned code=${response.responseCode}, msg=${response.debugMessage}")
        if (response.responseCode != BillingClient.BillingResponseCode.OK) {
            onResult(false, "Billing Error: ${response.debugMessage}")
        } else {
            onResult(true, "Opening Google Play purchase sheet...")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        Log.i(TAG, "🔔 [LoreBilling] onPurchasesUpdated event received: code=${billingResult.responseCode}, count=${purchases?.size ?: 0}")
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    purchases.forEach { p ->
                        Log.d(TAG, "   ↳ Purchase: orderId=${p.orderId}, products=${p.products}, state=${p.purchaseState}, acknowledged=${p.isAcknowledged}")
                    }
                    processPurchases(purchases)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "ℹ️ [LoreBilling] Purchase flow cancelled by user.")
            }
            else -> {
                Log.e(TAG, "❌ [LoreBilling] Purchase flow error: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        var hasValidPurchase = false
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                hasValidPurchase = true
                Log.d(TAG, "✅ [LoreBilling] Valid purchased item found: ${purchase.products}")
                if (!purchase.isAcknowledged) {
                    Log.d(TAG, "   ↳ Acknowledging purchase: ${purchase.purchaseToken.take(12)}...")
                    acknowledgePurchase(purchase)
                }
            }
        }
        Log.i(TAG, "🔒 [LoreBilling] Processing purchases complete — hasValidPurchase=$hasValidPurchase")
        updatePremiumEntitlement(hasValidPurchase)
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "✅ [LoreBilling] Purchase acknowledged successfully.")
            } else {
                Log.w(TAG, "⚠️ [LoreBilling] Acknowledgement failed: ${billingResult.debugMessage}")
            }
        }
    }

    /** Updates premium entitlement based on Google Play purchases. */
    fun updatePremiumEntitlement(unlocked: Boolean) {
        Log.i(TAG, "💎 [LoreBilling] Updating entitlement state: unlocked=$unlocked")
        _isPremium.value = unlocked
        scope.launch {
            settingsRepo.setPremiumUnlocked(unlocked)
            settingsRepo.setSubscriptionPlan(if (unlocked) "LORE SANCTUARY (PRO)" else "FREE")
        }
    }
}
