package com.magikelle.godotgoogleplaybilling;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import org.godotengine.godot.Dictionary;
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.UnfetchedProduct;
import com.magikelle.godotgoogleplaybilling.utils.GooglePlayBillingUtils;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GodotGooglePlayBilling extends GodotPlugin implements PurchasesUpdatedListener, BillingClientStateListener {

    private static final String TAG = "GodotGooglePlayBilling";
    private BillingClient billingClient;
    private final ConcurrentHashMap<String, ProductDetails> productDetailsCache = new ConcurrentHashMap<>();
    private volatile String obfuscatedAccountId = "";
    private volatile String obfuscatedProfileId = "";

    // Logging config — static + volatile for thread-safe access from GooglePlayBillingUtils
    public static volatile int logLevel = 0;
    public static volatile String logTag = "godot";

    public GodotGooglePlayBilling(Godot godot) {
        super(godot);

        try {
            PendingPurchasesParams pendingPurchasesParams = PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build();

            billingClient = BillingClient
                    .newBuilder(getGodot().requireContext())
                    .enablePendingPurchases(pendingPurchasesParams)
                    .setListener(this)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing BillingClient", e);
            billingClient = null;
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotGooglePlayBilling";
    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();

        signals.add(new SignalInfo("connected"));
        signals.add(new SignalInfo("disconnected"));
        signals.add(new SignalInfo("connect_error", Integer.class, String.class));
        signals.add(new SignalInfo("purchases_updated", Object[].class));
        signals.add(new SignalInfo("query_purchases_response", Object.class));
        signals.add(new SignalInfo("purchase_error", Integer.class, String.class));
        signals.add(new SignalInfo("product_details_query_completed", Object[].class));
        signals.add(new SignalInfo("product_details_query_error", Integer.class, String.class, String[].class));
        signals.add(new SignalInfo("purchase_acknowledged", String.class));
        signals.add(new SignalInfo("purchase_acknowledgement_error", Integer.class, String.class, String[].class));
        signals.add(new SignalInfo("purchase_consumed", String.class));
        signals.add(new SignalInfo("purchase_consumption_error", Integer.class, String.class, String.class));

        return signals;
    }

    private void log(String message) {
        if (logLevel > 0 && logTag != null && !logTag.isEmpty()) {
            Log.i(logTag, message);
        }
    }

    // Null-safe helper for String values emitted to Godot signals
    private static String sanitize(String value) {
        return value != null ? value : "";
    }

    @UsedByGodot
    public void setLogLevel(int level) {
        logLevel = level;
        log("Log level set to: " + level);
    }

    @UsedByGodot
    public void setLogTag(String tag) {
        if (tag != null && !tag.isEmpty()) {
            logTag = tag;
            log("Log tag set to: " + tag);
        }
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            log("Connected!");
            emitSignal("connected");
        } else {
            log("Connect Error! Response: " + billingResult.getResponseCode() + ", Message: " + billingResult.getDebugMessage());
            emitSignal("connect_error", billingResult.getResponseCode(), sanitize(billingResult.getDebugMessage()));
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        log("Disconnected!");
        emitSignal("disconnected");
    }

    @UsedByGodot
    public void startConnection() {
        if (billingClient == null) {
            log("BillingClient is null — initialization failed");
            emitSignal("connect_error", BillingClient.BillingResponseCode.DEVELOPER_ERROR, "BillingClient not initialized");
            return;
        }
        log("Start Connection!");
        try {
            billingClient.startConnection(this);
        } catch (Exception e) {
            log("Error starting connection: " + e.getMessage());
            emitSignal("connect_error", BillingClient.BillingResponseCode.ERROR, "Failed to start connection: " + e.getMessage());
        }
    }

    @UsedByGodot
    public void endConnection() {
        log("End Connection!");
        try {
            if (billingClient != null) {
                billingClient.endConnection();
            }
        } catch (Exception e) {
            log("Error ending connection: " + e.getMessage());
        }
    }

    @UsedByGodot
    public boolean isReady() {
        boolean ready = billingClient != null && billingClient.isReady();
        log("Is Ready: " + ready);
        return ready;
    }

    @UsedByGodot
    public int getConnectionState() {
        int state = billingClient != null ? billingClient.getConnectionState() : BillingClient.ConnectionState.DISCONNECTED;
        log("Connection State: " + state);
        return state;
    }

    @UsedByGodot
    public void queryPurchases(String type) {
        if (billingClient == null || !billingClient.isReady()) {
            log("BillingClient not ready");
            emitSignal("purchase_error", BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, "BillingClient not connected. Call startConnection() first.");
            return;
        }
        if (type == null || type.isEmpty()) {
            log("Invalid product type");
            emitSignal("purchase_error", BillingClient.BillingResponseCode.DEVELOPER_ERROR, "Invalid product type");
            return;
        }

        log("Query Purchases for type: " + type);
        try {
            QueryPurchasesParams params = QueryPurchasesParams.newBuilder().setProductType(type).build();

            billingClient.queryPurchasesAsync(params, (billingResult, purchaseList) -> {
                Dictionary returnValue = new Dictionary();
                try {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        returnValue.put("status", 0);
                        returnValue.put("purchases", GooglePlayBillingUtils.convertPurchaseListToDictionaryObjectArray(purchaseList));
                    } else {
                        returnValue.put("status", 1);
                        returnValue.put("response_code", billingResult.getResponseCode());
                        returnValue.put("debug_message", sanitize(billingResult.getDebugMessage()));
                    }
                    emitSignal("query_purchases_response", returnValue);
                } catch (Exception e) {
                    log("Error processing purchases response: " + e.getMessage());
                    emitSignal("purchase_error", BillingClient.BillingResponseCode.ERROR, "Failed to process purchases: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            log("Error querying purchases: " + e.getMessage());
            emitSignal("purchase_error", BillingClient.BillingResponseCode.ERROR, "Failed to query purchases: " + e.getMessage());
        }
    }

    @UsedByGodot
    public void queryProductDetails(final String[] list, String type) {
        if (billingClient == null || !billingClient.isReady()) {
            log("BillingClient not ready");
            emitSignal("product_details_query_error", BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, "BillingClient not connected. Call startConnection() first.", new String[0]);
            return;
        }
        if (list == null || list.length == 0) {
            log("Invalid product list");
            emitSignal("product_details_query_error", BillingClient.BillingResponseCode.DEVELOPER_ERROR, "Invalid product list", new String[0]);
            return;
        }

        if (type == null || type.isEmpty()) {
            log("Invalid product type");
            emitSignal("product_details_query_error", BillingClient.BillingResponseCode.DEVELOPER_ERROR, "Invalid product type", list);
            return;
        }

        log("Query Product Details for " + list.length + " products of type: " + type);
        try {
            ArrayList<QueryProductDetailsParams.Product> products = new ArrayList<>();

            for (String productId : list) {
                if (productId != null && !productId.isEmpty()) {
                    products.add(QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(type)
                            .build());
                }
            }

            if (products.isEmpty()) {
                log("No valid products to query");
                emitSignal("product_details_query_error", BillingClient.BillingResponseCode.DEVELOPER_ERROR, "No valid products", list);
                return;
            }

            QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                    .setProductList(products)
                    .build();

            billingClient.queryProductDetailsAsync(params, (billingResult, queryProductDetailsResult) -> {
                try {
                    log("Billing Result: " + billingResult.getResponseCode());

                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (ProductDetails productDetails : queryProductDetailsResult.getProductDetailsList()) {
                            if (productDetails != null) {
                                productDetailsCache.put(productDetails.getProductId(), productDetails);
                            }
                        }
                        log("Product Details Query Completed for " + type + " products.");
                        emitSignal("product_details_query_completed",
                                GooglePlayBillingUtils.convertProductDetailsListToDictionaryObjectArray(queryProductDetailsResult.getProductDetailsList()));

                        List<UnfetchedProduct> unfetchedProducts = queryProductDetailsResult.getUnfetchedProductList();
                        if (!unfetchedProducts.isEmpty()) {
                            for (UnfetchedProduct unfetchedProduct : unfetchedProducts) {
                                log("Unfetched product: " + unfetchedProduct.getProductId());
                            }
                        }
                    } else {
                        log("Product Details Query Error for " + type + " products.");
                        emitSignal("product_details_query_error",
                                billingResult.getResponseCode(),
                                sanitize(billingResult.getDebugMessage()),
                                list);
                    }
                } catch (Exception e) {
                    log("Error processing product details response: " + e.getMessage());
                    emitSignal("product_details_query_error",
                            BillingClient.BillingResponseCode.ERROR,
                            "Failed to process product details: " + e.getMessage(),
                            list);
                }
            });
        } catch (Exception e) {
            log("Error querying product details: " + e.getMessage());
            emitSignal("product_details_query_error",
                    BillingClient.BillingResponseCode.ERROR,
                    "Failed to query product details: " + e.getMessage(),
                    list);
        }
    }

    @UsedByGodot
    public void acknowledgePurchase(final String purchaseToken) {
        if (billingClient == null || !billingClient.isReady()) {
            log("BillingClient not ready");
            emitSignal("purchase_acknowledgement_error", BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, "BillingClient not connected. Call startConnection() first.", new String[]{""});
            return;
        }
        if (purchaseToken == null || purchaseToken.isEmpty()) {
            log("Invalid purchase token");
            emitSignal("purchase_acknowledgement_error", BillingClient.BillingResponseCode.DEVELOPER_ERROR, "Invalid purchase token", new String[]{""});
            return;
        }

        log("Acknowledging purchase");
        try {
            AcknowledgePurchaseParams acknowledgePurchaseParams =
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchaseToken)
                            .build();
            billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    log("Purchase acknowledged successfully");
                    emitSignal("purchase_acknowledged", purchaseToken);
                } else {
                    log("Failed to acknowledge purchase: " + billingResult.getDebugMessage());
                    emitSignal("purchase_acknowledgement_error",
                            billingResult.getResponseCode(),
                            sanitize(billingResult.getDebugMessage()),
                            new String[]{purchaseToken});
                }
            });
        } catch (Exception e) {
            log("Error acknowledging purchase: " + e.getMessage());
            emitSignal("purchase_acknowledgement_error",
                    BillingClient.BillingResponseCode.ERROR,
                    "Failed to acknowledge purchase: " + e.getMessage(),
                    new String[]{purchaseToken});
        }
    }

    @UsedByGodot
    public void consumePurchase(String purchaseToken) {
        if (billingClient == null || !billingClient.isReady()) {
            log("BillingClient not ready");
            emitSignal("purchase_consumption_error", BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, "BillingClient not connected. Call startConnection() first.", "");
            return;
        }
        if (purchaseToken == null || purchaseToken.isEmpty()) {
            log("Invalid purchase token");
            emitSignal("purchase_consumption_error", BillingClient.BillingResponseCode.DEVELOPER_ERROR, "Invalid purchase token", "");
            return;
        }

        log("Consuming purchase");
        try {
            ConsumeParams consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchaseToken)
                    .build();

            billingClient.consumeAsync(consumeParams, (billingResult, outPurchaseToken) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    log("Purchase consumed successfully");
                    emitSignal("purchase_consumed", outPurchaseToken);
                } else {
                    log("Purchase consumption error: " + billingResult.getDebugMessage());
                    emitSignal("purchase_consumption_error",
                            billingResult.getResponseCode(),
                            sanitize(billingResult.getDebugMessage()),
                            outPurchaseToken);
                }
            });
        } catch (Exception e) {
            log("Error consuming purchase: " + e.getMessage());
            emitSignal("purchase_consumption_error",
                    BillingClient.BillingResponseCode.ERROR,
                    "Failed to consume purchase: " + e.getMessage(),
                    purchaseToken);
        }
    }

    @UsedByGodot
    public void purchase(String productId, String type, String accountId, String profileId) {
        if (billingClient == null || !billingClient.isReady()) {
            log("BillingClient not ready");
            emitSignal("purchase_error", BillingClient.BillingResponseCode.SERVICE_DISCONNECTED, "BillingClient not connected. Call startConnection() first.");
            return;
        }
        if (productId == null || productId.isEmpty()) {
            log("Invalid product ID");
            emitSignal("purchase_error", BillingClient.BillingResponseCode.DEVELOPER_ERROR, "Invalid product ID");
            return;
        }

        if (type == null || type.isEmpty()) {
            log("Invalid product type");
            emitSignal("purchase_error", BillingClient.BillingResponseCode.DEVELOPER_ERROR, "Invalid product type (must be 'inapp' or 'subs')");
            return;
        }

        this.obfuscatedAccountId = accountId != null ? accountId : "";
        this.obfuscatedProfileId = profileId != null ? profileId : "";

        log("Initiating purchase for product: " + productId + ", type: " + type);

        ProductDetails productDetails = productDetailsCache.get(productId);
        if (productDetails == null) {
            log("Product details not available in cache for product: " + productId);
            emitSignal("purchase_error",
                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                    "Product details not available. Please query product details first.");
            return;
        }

        log("Product details found in cache");

        try {
            BillingFlowParams.ProductDetailsParams.Builder productDetailsParamsBuilder =
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails);

            if ("subs".equals(type)) {
                List<ProductDetails.SubscriptionOfferDetails> subscriptionOffers =
                        productDetails.getSubscriptionOfferDetails();

                if (subscriptionOffers != null && !subscriptionOffers.isEmpty()) {
                    String offerToken = subscriptionOffers.get(0).getOfferToken();
                    productDetailsParamsBuilder.setOfferToken(offerToken);
                    log("Using offer token: " + offerToken);
                } else {
                    log("Warning: No subscription offers available for product: " + productId);
                    emitSignal("purchase_error",
                            BillingClient.BillingResponseCode.ERROR,
                            "No subscription offers available for product: " + productId);
                    return;
                }
            }

            BillingFlowParams.ProductDetailsParams productDetailsParams = productDetailsParamsBuilder.build();

            BillingFlowParams.Builder builder = BillingFlowParams.newBuilder();
            builder.setProductDetailsParamsList(Collections.singletonList(productDetailsParams));

            // Only set obfuscated IDs when they are non-empty (avoids overriding prior values with blanks)
            if (!this.obfuscatedAccountId.isEmpty()) {
                builder.setObfuscatedAccountId(this.obfuscatedAccountId);
            }
            if (!this.obfuscatedProfileId.isEmpty()) {
                builder.setObfuscatedProfileId(this.obfuscatedProfileId);
            }

            android.app.Activity activity = getActivity();
            if (activity == null) {
                log("Activity is null - cannot launch billing flow");
                emitSignal("purchase_error",
                        BillingClient.BillingResponseCode.ERROR,
                        "Activity not available. Billing flow requires a foreground Activity.");
                return;
            }

            BillingResult billingResult = billingClient.launchBillingFlow(activity, builder.build());

            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                log("Purchase error: " + billingResult.getDebugMessage());
                emitSignal("purchase_error",
                        billingResult.getResponseCode(),
                        sanitize(billingResult.getDebugMessage()));
            } else {
                log("Billing flow launched successfully");
            }
        } catch (Exception e) {
            log("Error during purchase: " + e.getMessage());
            emitSignal("purchase_error",
                    BillingClient.BillingResponseCode.ERROR,
                    "Failed to launch purchase: " + e.getMessage());
        }
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        try {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null && !purchases.isEmpty()) {
                log("Purchase Updated! Count: " + purchases.size());
                for (Purchase purchase : purchases) {
                    log("Purchase token: " + sanitize(purchase.getPurchaseToken()));
                }
                emitSignal("purchases_updated",
                        GooglePlayBillingUtils.convertPurchaseListToDictionaryObjectArray(purchases));
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                log("Purchase cancelled by user");
                emitSignal("purchase_error",
                        billingResult.getResponseCode(),
                        "User cancelled the purchase.");
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                log("Purchase update received but no purchases (empty list)");
                emitSignal("purchases_updated", (Object) new Object[0]);
            } else {
                log("Purchase error: " + billingResult.getDebugMessage());
                emitSignal("purchase_error",
                        billingResult.getResponseCode(),
                        sanitize(billingResult.getDebugMessage()));
            }
        } catch (Exception e) {
            log("Error processing purchase update: " + e.getMessage());
            emitSignal("purchase_error",
                    BillingClient.BillingResponseCode.ERROR,
                    "Failed to process purchase update: " + e.getMessage());
        }
    }

}
