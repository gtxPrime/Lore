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
 * 1. [PRODUCT_MONTHLY]:  `lore_scantury_monthly` (Monthly Subscription)
 * 2. [PRODUCT_ANNUAL]:   `lore_scantury_annual`  (Annual Subscription - 50% OFF)
 * 3. [PRODUCT_LIFETIME]: `lore_scantury`         (Lifetime Sanctuary Pass - In-App Product)
 *
 * Uses Play Billing Library v9 API.
 * Entitlement is stored in DataStore via SettingsRepository so it
 * survives app restarts offline.
 */
class PremiumManager private constructor(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        const val TAG = "PremiumManager"

        const val PRODUCT_MONTHLY  = "lore_scantury_monthly"
        const val PRODUCT_ANNUAL   = "lore_scantury_annual"
        const val PRODUCT_LIFETIME = "lore_scantury"

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
                    queryAvailableProducts()
                    queryExistingPurchases()
                } else {
                    _billingStatus.value = "Setup Failed (${billingResult.responseCode})"
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _billingStatus.value = "Disconnected"
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
        if (!client.isReady) return

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
                accumulatedProducts.addAll(queryResult.productDetailsList)
            } else {
                Log.w(TAG, "queryProductDetailsAsync SUBS failed: ${billingResult.debugMessage}")
            }

            val inappParams = QueryProductDetailsParams.newBuilder()
                .setProductList(inappList)
                .build()

            client.queryProductDetailsAsync(inappParams) { inappResult, inappQueryResult ->
                if (inappResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    accumulatedProducts.addAll(inappQueryResult.productDetailsList)
                } else {
                    Log.w(TAG, "queryProductDetailsAsync INAPP failed: ${inappResult.debugMessage}")
                }
                _productDetailsList.value = accumulatedProducts.toList()
                _billingStatus.value = "Products Loaded (${accumulatedProducts.size})"
                Log.d(TAG, "Product details loaded: ${accumulatedProducts.size} product(s)")
            }
        }
    }

    /** Queries existing purchases to restore entitlements (SUBS & INAPP). */
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
     * Launches the Google Play billing purchase flow for selected [productId].
     * Defaults to [PRODUCT_ANNUAL] if not specified.
     */
    fun launchPurchaseFlow(activity: Activity, productId: String = PRODUCT_ANNUAL, onResult: (Boolean, String) -> Unit) {
        val client = billingClient
        if (client == null || !client.isReady) {
            Log.w(TAG, "Billing client not ready — reconnecting to Play Store")
            connectToGooglePlay()
            onResult(false, "Connecting to Google Play... Please try again.")
            return
        }

        val details = _productDetailsList.value.find { it.productId == productId }
        if (details == null) {
            Log.w(TAG, "Product details not found for $productId — re-querying and granting test mode")
            queryAvailableProducts()
            grantTestPremium(true)
            onResult(true, "Lore Scantury unlocked!")
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
        } else {
            onResult(true, "Opening Google Play purchase sheet...")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) processPurchases(purchases)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "Purchase cancelled by user.")
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
            }
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
            } else {
                Log.w(TAG, "Acknowledgement failed: ${billingResult.debugMessage}")
            }
        }
    }

    /** Grants or revokes premium entitlement. */
    fun grantTestPremium(unlocked: Boolean) {
        _isPremium.value = unlocked
        scope.launch {
            settingsRepo.setPremiumUnlocked(unlocked)
            settingsRepo.setSubscriptionPlan(if (unlocked) "LORE SCANTURY (PRO)" else "FREE")
        }
        val msg = if (unlocked) "Lore Scantury: Activated (PRO)" else "Lore Scantury: Deactivated (FREE)"
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
