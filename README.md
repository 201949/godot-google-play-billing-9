# Godot Google Play Billing 9

Godot Android plugin for the Google Play Billing Library version 9 (tested on Godot 3.6.2 and newer).

This plugin bridges the Godot Engine with the latest Google Play Billing API v9.1.0, enabling secure in-app purchases, subscriptions, and advanced sub-response error codes handling required for modern Android releases, including Android 16 (API 36).

[![Android](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://developer.android.com)
[![Godot](https://img.shields.io/badge/Godot%20Engine-3.6.2-blue.svg)](https://github.com/godotengine/godot/)
[![GPBL](https://img.shields.io/badge/Google%20Play%20Billing%20Library-9.1.0-green.svg)](https://developer.android.com/google/play/billing/integrate)
[![MIT license](https://img.shields.io/badge/License-MIT-yellowgreen.svg)](https://github.com/201949/godot-google-play-billing-7/blob/main/LICENSE)

## Supported Features

- **One-time in-app purchases**
- **Repeat in-app purchases**
- **Subscriptions**

## Disclaimer

If you want me to continue developing the plugin and keeping it up-to-date, please support me by:

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://buymeacoffee.com/magikelle)

Please also consider giving a star :star: to the plugin repository if you found it useful.

## Supporters

A big thank you to the following people for their sponsorship:
- [davekaa](https://github.com/davekaa)
- [mackatap](https://github.com/mackatap)
- [galaxiusgames](https://github.com/galaxiusgames)

## Production use
*Google Play Store:*

- ["Ugly Button Adventure"](https://play.google.com/store/apps/details?id=com.magikelle.uglybutton) by [Magikelle Studio aka Ugly Button](https://play.google.com/store/apps/dev?id=8681639065134696403)
- ["Ugly Button 2"](https://play.google.com/store/apps/details?id=com.magikelle.uglybutton.chapter.two) by [Magikelle Studio aka Ugly Button](https://play.google.com/store/apps/dev?id=8681639065134696403)
- ["Stunt Riders"](https://play.google.com/store/apps/details?id=com.magikelle.bikeriders) by [Magikelle Studio aka Ugly Button](https://play.google.com/store/apps/dev?id=8681639065134696403)
- ["Lifty Circus Action Platformer"](https://play.google.com/store/apps/details?id=com.magikelle.liftycircus) by [Magikelle Studio aka Ugly Button](https://play.google.com/store/apps/dev?id=8681639065134696403)
- ["Galaxius: Space Battle"](https://play.google.com/store/apps/details?id=com.justjustin.galaxius&hl=en_US) **(GPBL 6.2.1)** by [Galaxius Games](https://play.google.com/store/apps/dev?id=4953770330659300081&hl=en_US)

## Google Play Billing Library Version Deprecation Information

For information on version deprecation, visit: [Google Play Billing Library Deprecation FAQ](https://developer.android.com/google/play/billing/deprecation-faq)

## Compiling the plugin .aar file

You can easily compile this plugin yourself for any versions of Godot libraries.

1. Go to the downloads page for your version of Godot (e.g., Godot 3.6.2).
2. Download the Android AAR Library (e.g., `godot-lib.3.6.2.stable.release.aar`).
3. Create a folder named `libs` in the root directory of this repository.
4. Place the newly downloaded `.aar` file into the `libs` directory.
5. Open a command window (or Git Bash) in the repository root directory, then run the appropriate command:
   ```bash
   ./gradlew.bat build
   ```
6. The newly generated plugin `.aar` file will be located inside `app/build/outputs/aar/`.
7. Take the 'release' `.aar` file from that directory along with the `GodotGooglePlayBilling.gdap` file from the repository root directory, and place them both in your Godot project's `android/plugins/` directory.

## Preparing the Editor and Project for Plugin Use

1. Check your Android export template settings. You need to specify a minimum SDK version of 26 and a target SDK version of 36 to meet the Google Play target platform actual requirements.

    ![Pic 01](https://raw.githubusercontent.com/201949/godot-google-play-billing-9/main/pic_01.png)

2. Check the `android/build/config.gradle` file and make any necessary changes to the SDK version specification.

    ![Pic 02](https://raw.githubusercontent.com/201949/godot-google-play-billing-9/main/pic_02.png)

3. In the Android export template "Options" section under "Permissions", set "Access Network State" and "Internet" to "On". Also, add the following permission under "Custom Permissions": `com.android.vending.BILLING` (this may be required).

## Example of Usage on Godot

1. After copying the plugin `.aar` and `.gdap` files into `res://android/plugins/`, make sure to enable the plugin in the **Export** window under the Android preset options.
2. Create an **Autoload** script (e.g., `Payment.gd`) and initialize the plugin as follows:

```gdscript
extends Node

signal shop_item_purchased(item_id)
signal shop_subscription_purchased(sub_id)
signal shop_purchase_restored(item_id)
signal shop_error(error_message)

var payment
var is_billing_supported = false

var tracked_purchases = {}
var available_products = {}

func _ready():
	_initialize_shopping()

func _initialize_shopping():
	if Engine.has_singleton("GodotGooglePlayBilling"):
		payment = Engine.get_singleton("GodotGooglePlayBilling")
		
		# Connect core connection signals
		payment.connect("connected", self, "_on_connected")
		payment.connect("disconnected", self, "_on_disconnected")
		payment.connect("connect_error", self, "_on_connect_error")
		
		# Connect purchase operations signals
		payment.connect("purchases_updated", self, "_on_purchases_updated")
		payment.connect("purchase_error", self, "_on_purchase_error")
		
		# Connect inventory query signals
		payment.connect("sku_details_query_completed", self, "_on_sku_details_query_completed")
		payment.connect("sku_details_query_error", self, "_on_sku_details_query_error")
		
		# Connect lifecycle management signals
		payment.connect("purchase_acknowledged", self, "_on_purchase_acknowledged")
		payment.connect("purchase_acknowledgement_error", self, "_on_purchase_acknowledgement_error")
		payment.connect("purchase_consumed", self, "_on_purchase_consumed")
		payment.connect("purchase_consumption_error", self, "_on_purchase_consumption_error")
		
		# Connect new Billing Library 9 response signals
		payment.connect("query_purchases_response", self, "_on_query_purchases_response")

		payment.startConnection()
	else:
		print("Android IAP platform-specific engine singleton not found")
		emit_signal("shop_error", "Billing singleton not available")

func _on_connected():
	print("Google Play Billing standard connection successful")
	is_billing_supported = true
	_reload_shop_inventory()

func _on_disconnected():
	print("Google Play Billing disconnected")
	is_billing_supported = false

func _on_connect_error(response_id, debug_message):
	print("Connect error id: ", response_id, " message: ", debug_message)
	is_billing_supported = false
	emit_signal("shop_error", "Connection failed: " + debug_message)

func _reload_shop_inventory():
	if not is_billing_supported:
		return
	query_sku_details(["no_ads", "gold_pack_small", "gold_pack_large"], "inapp")
	query_sku_details(["vip_subscription_monthly", "premium_club_yearly"], "subs")

func query_sku_details(items, type = "inapp"):
	if is_billing_supported:
		payment.querySkuDetails(items, type)

func _on_sku_details_query_completed(skus):
	print("SKU details query completed successfully")
	for sku in skus:
		print("SKU found: ", sku.sku, " Type: ", sku.type, " Price: ", sku.price)
		available_products[sku.sku] = sku
	_validate_and_restore_owned_assets()

func _on_sku_details_query_error(response_id, debug_message, skus):
	print("SKU details query error id: ", response_id, " message: ", debug_message, " skus: ", skus)
	emit_signal("shop_error", "Failed to load shop items data")

func _validate_and_restore_owned_assets():
	query_purchases("inapp")
	query_purchases("subs")

func query_purchases(type = "inapp"):
	if is_billing_supported:
		payment.queryPurchases(type)

func _on_query_purchases_response(purchases, sub_response_code):
	print("Query purchases response received. Sub response code: ", sub_response_code)
	_on_purchases_updated(purchases)

func make_purchase(sku_id):
	if not is_billing_supported:
		emit_signal("shop_error", "Billing service not connected")
		return
	
	if not available_products.has(sku_id):
		emit_signal("shop_error", "Product details not found for ID: " + sku_id)
		return
		
	var product = available_products[sku_id]
	var response = payment.purchase(sku_id)
	if response.status != OK:
		print("Error initiating purchase: ", response.message)
		emit_signal("shop_error", "Failed to start purchase: " + response.message)

func _on_purchases_updated(purchases):
	print("Purchases list updated: ", purchases)
	for purchase in purchases:
		# Save purchase token to local tracking dictionary
		tracked_purchases[purchase.purchase_token] = purchase
		
		match int(purchase.purchase_state):
			1: # 1 is PURCHASED
				_process_successful_transaction(purchase)
			2: # 2 is PENDING
				print("Purchase is pending for SKU: ", purchase.sku)
				emit_signal("shop_error", "Payment is pending. Please complete transaction.")
			0: # 0 is UNSPECIFIED_STATE
				print("Unspecified purchase state for SKU: ", purchase.sku)

func _process_successful_transaction(purchase):
	if not purchase.is_acknowledged:
		# Check product type from the cache map
		if available_products.has(purchase.sku):
			var product_info = available_products[purchase.sku]
			
			if product_info.type == "subs":
				# Subscriptions must always be acknowledged
				print("Acknowledging subscription purchase for: ", purchase.sku)
				payment.acknowledgePurchase(purchase.purchase_token)
			else:
				# Handle standard in-app purchases based on game design
				if "gold" in purchase.sku or "pack" in purchase.sku:
					# Consumable items (e.g. coins, packs)
					print("Consuming multiple-time purchase for: ", purchase.sku)
					payment.consumePurchase(purchase.purchase_token)
				else:
					# Non-consumable items (e.g. no ads, full unlock)
					print("Acknowledging single-time purchase for: ", purchase.sku)
					payment.acknowledgePurchase(purchase.purchase_token)
		else:
			# Safety fallback handling if cache details are missing
			print("Fallback handling: Acknowledging unknown product type for: ", purchase.sku)
			payment.acknowledgePurchase(purchase.purchase_token)
	else:
		# Restore entitlements if already acknowledged on startup
		print("Restoring already acknowledged purchase for SKU: ", purchase.sku)
		_grant_entitlement(purchase.sku, true)

func _on_purchase_error(response_id, debug_message):
	print("Purchase error id: ", response_id, " message: ", debug_message)
	emit_signal("shop_error", "Purchase failed: " + debug_message)

func _on_purchase_acknowledged(purchase_token):
	print("Purchase acknowledged successfully! Token: ", purchase_token)
	if tracked_purchases.has(purchase_token):
		var purchase = tracked_purchases[purchase_token]
		_grant_entitlement(purchase.sku, false)

func _on_purchase_acknowledgement_error(response_id, debug_message, purchase_token):
	print("Purchase acknowledgement error id: ", response_id, " message: ", debug_message, " token: ", purchase_token)
	emit_signal("shop_error", "Acknowledgement failed: " + debug_message)

func _on_purchase_consumed(purchase_token):
	print("Purchase consumed successfully! Token: ", purchase_token)
	if tracked_purchases.has(purchase_token):
		var purchase = tracked_purchases[purchase_token]
		_grant_entitlement(purchase.sku, false)

func _on_purchase_consumption_error(response_id, debug_message, purchase_token):
	print("Purchase consumption error id: ", response_id, " message: ", debug_message, " token: ", purchase_token)
	emit_signal("shop_error", "Consumption failed: " + debug_message)

func _grant_entitlement(sku_id, is_restored):
	if available_products.has(sku_id):
		var product_info = available_products[sku_id]
		if product_info.type == "subs":
			print("Granting subscription benefits for: ", sku_id)
			emit_signal("shop_subscription_purchased", sku_id)
			return
			
	if is_restored:
		print("Restoring purchase entitlement for: ", sku_id)
		emit_signal("shop_purchase_restored", sku_id)
	else:
		print("Granting standard purchase entitlement for: ", sku_id)
		emit_signal("shop_item_purchased", sku_id)

func get_product_price(sku_id) -> String:
	if available_products.has(sku_id):
		return available_products[sku_id].price
	return ""

func is_product_available(sku_id) -> bool:
	return available_products.has(sku_id)
```

## Upcoming Improvements

Stay tuned for updates, and feel free to [open an issue](https://github.com/201949/godot-google-play-billing-7/issues) or [contribute](https://github.com/201949/godot-google-play-billing-7/pulls) if you have any suggestions or feedback!
