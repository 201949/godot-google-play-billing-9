package com.magikelle.godotgoogleplaybilling.utils;

import android.util.Log;

import org.godotengine.godot.Dictionary;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.ProductDetails;
import com.magikelle.godotgoogleplaybilling.GodotGooglePlayBilling;

import java.util.List;

public class GooglePlayBillingUtils {

    // Convert a list of purchases into an array of Dictionary objects
    public static Object[] convertPurchaseListToDictionaryObjectArray(List<Purchase> purchaseList) {
        if (purchaseList == null || purchaseList.isEmpty()) {
            return new Object[0];
        }
        Object[] result = new Object[purchaseList.size()];
        for (int i = 0; i < purchaseList.size(); i++) {
            result[i] = convertPurchaseToDictionary(purchaseList.get(i));
        }
        return result;
    }

    // Convert a single Purchase into a Dictionary
    public static Dictionary convertPurchaseToDictionary(Purchase purchase) {
        Dictionary dictionary = new Dictionary();
        if (purchase == null) {
            return dictionary;
        }

        dictionary.put("orderId", purchase.getOrderId());
        dictionary.put("packageName", purchase.getPackageName());

        // Safely get the first product ID from the list
        List<String> products = purchase.getProducts();
        if (products != null && !products.isEmpty()) {
            dictionary.put("productId", products.get(0));
        } else {
            dictionary.put("productId", "");
        }

        dictionary.put("purchaseTime", purchase.getPurchaseTime());
        dictionary.put("purchaseState", purchase.getPurchaseState());
        dictionary.put("purchaseToken", purchase.getPurchaseToken());
        dictionary.put("signature", purchase.getSignature());
        dictionary.put("isAutoRenewing", purchase.isAutoRenewing());
        dictionary.put("originalJson", purchase.getOriginalJson());
        dictionary.put("isAcknowledged", purchase.isAcknowledged());
        return dictionary;
    }

    // Convert a list of ProductDetails into an array of Dictionary objects
    public static Object[] convertProductDetailsListToDictionaryObjectArray(List<ProductDetails> productDetailsList) {
        if (productDetailsList == null || productDetailsList.isEmpty()) {
            return new Object[0];
        }
        Object[] result = new Object[productDetailsList.size()];
        for (int i = 0; i < productDetailsList.size(); i++) {
            result[i] = convertProductDetailsToDictionary(productDetailsList.get(i));
        }
        return result;
    }

    // Convert a single ProductDetails into a Dictionary
    public static Dictionary convertProductDetailsToDictionary(ProductDetails details) {
        Dictionary dictionary = new Dictionary();
        if (details == null) {
            return dictionary;
        }

        dictionary.put("productId", details.getProductId());
        dictionary.put("title", details.getTitle());
        dictionary.put("description", details.getDescription());
        dictionary.put("type", details.getProductType());

        // Handle pricing info based on product type (one-time purchase or subscription)
        ProductDetails.OneTimePurchaseOfferDetails oneTimeOffer = details.getOneTimePurchaseOfferDetails();
        if (oneTimeOffer != null) {
            dictionary.put("price", oneTimeOffer.getPriceAmountMicros() / 1000000.0);
            dictionary.put("currencyCode", oneTimeOffer.getPriceCurrencyCode());
            dictionary.put("formattedPrice", oneTimeOffer.getFormattedPrice());
        } else {
            List<ProductDetails.SubscriptionOfferDetails> subscriptionOffers = details.getSubscriptionOfferDetails();
            if (subscriptionOffers != null && !subscriptionOffers.isEmpty()) {
                // Use the first subscription offer
                ProductDetails.SubscriptionOfferDetails subscriptionOfferDetails = subscriptionOffers.get(0);
                List<ProductDetails.PricingPhase> pricingPhases =
                        subscriptionOfferDetails.getPricingPhases().getPricingPhaseList();
                if (pricingPhases != null && !pricingPhases.isEmpty()) {
                    ProductDetails.PricingPhase firstPhase = pricingPhases.get(0);
                    dictionary.put("price", firstPhase.getPriceAmountMicros() / 1000000.0);
                    dictionary.put("currencyCode", firstPhase.getPriceCurrencyCode());
                    dictionary.put("formattedPrice", firstPhase.getFormattedPrice());
                }
            }
        }

        if (GodotGooglePlayBilling.logLevel > 0) {
            Log.i(GodotGooglePlayBilling.logTag, dictionary.toString());
        }

        return dictionary;
    }
}
