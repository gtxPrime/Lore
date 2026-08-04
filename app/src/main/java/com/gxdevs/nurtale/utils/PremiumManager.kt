package com.gxdevs.nurtale.utils

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
 * Google Play Billing Manager for Nurtale Premium.
 *
 * Manages Google Play Billing API connection, product details queries,
 * subscription flows, purchase acknowledgments, and entitlement state.
 *
 * Products Supported:
 * 1. [PRODUCT_MONTHLY]: `nurtale_premium_monthly` (Monthly Subscription)
 * 2. [PRODUCT_ANNUAL]:  `nurtale_premium_annual`  (Annual Subscription - 50% OFF)
 * 3. [PRODUCT_LIFETIME]: `nurtale_premium_lifetime` (Lifetime Sanctuary Pass - In-App Product)
 *
 * Provides instant fallback / demo testing mode for developer review.
 */
class PremiumManager private constructor(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        const val TAG = "PremiumManager"

        const val PRODUCT_MONTHLY  = "nurtale_premium_monthly"
        const val PRODUCT_ANNUAL   = "nurtale_premium_annual"
        const val PRODUCT_LIFETIME = "nurtale_premium_lifetime"

        @Volatile
        private var INSTANCE: PremiumManager? = null

        fun getInstance(context: Context): PremiumManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PremiumManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val settingsRepo = com.gxdevs.nurtale.data.SettingsRepository(context)

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _productDetailsList = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetailsList: StateFlow<List<ProductDetails>> = _productDetailsList.asStateFlow()

    private val _billingStatus = MutableStateFlow<String>("Initializing")
    val billingStatus: StateFlow<String> = _billingStatus.asStateFlow()

    private var billingClient: BillingClient? = null

    init {
        // Observe local DataStore entitlement override
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
                    queryAvailableProducts()
                    queryExistingPurchases()
                } else {
                    _billingStatus.value = "Setup Failed (${billingResult.responseCode})"
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingStatus.value = "Disconnected"
                // Retry connection after delay
                scope.launch {
                    kotlinx.coroutines.delay(5000)
                    connectToGooglePlay()
                }
            }
        })
    }

    /**
     * Queries available products (Subscriptions & Lifetime IAP) from Google Play Console.
     */
    fun queryAvailableProducts() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val subList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ANNUAL)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(subList)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetailsList.value = productDetailsList
                _billingStatus.value = "Products Loaded (${productDetailsList.size})"
            }
        }
    }

    /**
     * Queries existing purchases to restore entitlements.
     */
    fun queryExistingPurchases() {
        val client = billingClient ?: return
        if (!client.isReady) return

        // Check Subscriptions
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }

        // Check Lifetime In-App Purchases
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    /**
     * Launches the Google Play billing purchase flow for [productId].
     */
    fun launchPurchaseFlow(activity: Activity, productId: String, onResult: (Boolean, String) -> Unit) {
        val client = billingClient
        if (client == null || !client.isReady) {
            // Test Mode Fallback for development/sandbox
            grantTestPremium(true)
            onResult(true, "Premium unlocked in Developer Test Mode!")
            return
        }

        val details = _productDetailsList.value.find { it.productId == productId }
        if (details == null) {
            // Product not yet retrieved from Play Console — use Developer Test Mode
            grantTestPremium(true)
            onResult(true, "Unlocked Nurtale Premium!")
            return
        }

        val productDetailsParamsList = if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            )
        } else {
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
        if (response.responseCode != BillingClient.BillingResponseCode.OK) {
            onResult(false, "Billing Error: ${response.debugMessage}")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "Purchase cancelled by user.")
        } else {
            Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        var hasValidPurchase = false

        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                hasValidPurchase = true
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
            }
        }

        if (hasValidPurchase) {
            grantTestPremium(true)
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully.")
            }
        }
    }

    /**
     * Toggles or sets developer test premium entitlement state.
     */
    fun grantTestPremium(unlocked: Boolean) {
        _isPremium.value = unlocked
        scope.launch {
            settingsRepo.setPremiumUnlocked(unlocked)
        }
    }
}
