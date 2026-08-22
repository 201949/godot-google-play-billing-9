# Godot Google Play Billing 9

Godot Android plugin for the Google Play Billing Library version 9 (tested on Godot 3.6.2 and newer).

This plugin bridges the Godot Engine with the latest Google Play Billing API v9.1.0, enabling secure in-app purchases, subscriptions, and advanced sub-response error codes handling required for modern Android releases, including Android 16 (API 36).

[![Android](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://developer.android.com)
[![Godot](https://img.shields.io/badge/Godot%20Engine-3.6.2-blue.svg)](https://github.com/godotengine/godot/)
[![GPBL](https://img.shields.io/badge/Google%20Play%20Billing%20Library-9.1.0-green.svg)](https://developer.android.com/google/play/billing/integrate)
[![MIT license](https://img.shields.io/badge/License-MIT-yellowgreen.svg)](https://github.com/201949/godot-google-play-billing-9/blob/main/LICENSE)

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
2. Create an **Autoload** script (for example, `Payment.gd`) and initialize the plugin as follows.

The example below matches the actual API exposed by `GodotGooglePlayBilling 9.1.0` and is intended for **Godot 3.6.2**.

> **Important:** This plugin uses `ProductDetails`, not the old SKU API. Use `queryProductDetails()` and the `product_details_query_*` signals. Do not use `querySkuDetails()`, `sku_details_query_completed`, or `sku_details_query_error`.

```gdscript
extends Node

const NON_CONSUMABLE_ITEMS = [
	"no_ads"
]

const CONSUMABLE_ITEMS = [
	"gold_pack_small",
	"gold_pack_large"
]

const SUBSCRIPTION_ITEMS = [
	"vip_subscription_monthly",
	"premium_club_yearly"
]

signal shop_initialized
signal shop_item_purchased(item_id)
signal shop_purchase_restored(item_id)
signal shop_consumable_purchased(item_id)
signal shop_subscription_purchased(item_id)
signal shop_purchase_pending(item_id)
signal shop_error(error_message)

var payment = null
var is_billing_supported = false
var is_initialized = false

# { "product_id": ProductDetails Dictionary }
var available_products = {}

# { "purchase_token": Purchase Dictionary }
var tracked_purchases = {}


func _ready():
	_initialize_shopping()


func _initialize_shopping():
	if not Engine.has_singleton("GodotGooglePlayBilling"):
		print("GodotGooglePlayBilling singleton not found")
		emit_signal("shop_error", "Billing singleton not available")
		return

	payment = Engine.get_singleton("GodotGooglePlayBilling")

	payment.setLogLevel(1)
	payment.setLogTag("GodotGooglePlayBilling")

	payment.connect("connected", self, "_on_connected")
	payment.connect("disconnected", self, "_on_disconnected")
	payment.connect("connect_error", self, "_on_connect_error")

	payment.connect("purchases_updated", self, "_on_purchases_updated")
	payment.connect("purchase_error", self, "_on_purchase_error")

	payment.connect(
		"product_details_query_completed",
		self,
		"_on_product_details_query_completed"
	)

	payment.connect(
		"product_details_query_error",
		self,
		"_on_product_details_query_error"
	)

	payment.connect(
		"purchase_acknowledged",
		self,
		"_on_purchase_acknowledged"
	)

	payment.connect(
		"purchase_acknowledgement_error",
		self,
		"_on_purchase_acknowledgement_error"
	)

	payment.connect(
		"purchase_consumed",
		self,
		"_on_purchase_consumed"
	)

	payment.connect(
		"purchase_consumption_error",
		self,
		"_on_purchase_consumption_error"
	)

	payment.connect(
		"query_purchases_response",
		self,
		"_on_query_purchases_response"
	)

	payment.startConnection()


func _on_connected():
	print("Google Play Billing connected")

	is_billing_supported = true
	is_initialized = true

	emit_signal("shop_initialized")

	_reload_shop_inventory()


func _on_disconnected():
	print("Google Play Billing disconnected")

	is_billing_supported = false
	is_initialized = false


func _on_connect_error(response_id, debug_message):
	print(
		"Billing connection error: ",
		response_id,
		" ",
		debug_message
	)

	is_billing_supported = false
	is_initialized = false

	emit_signal(
		"shop_error",
		"Connection failed: " + str(debug_message)
	)


func _reload_shop_inventory():
	if not is_billing_supported:
		return

	query_product_details(
		NON_CONSUMABLE_ITEMS + CONSUMABLE_ITEMS,
		"inapp"
	)

	query_product_details(
		SUBSCRIPTION_ITEMS,
		"subs"
	)


func query_product_details(items, type = "inapp"):
	if not is_billing_supported:
		return

	if items.empty():
		return

	payment.queryProductDetails(
		items,
		type
	)


func _on_product_details_query_completed(products):
	print("Product details query completed")

	for product in products:
		var product_id = product.productId

		available_products[product_id] = product

		print(
			"Product found: ",
			product_id,
			" Type: ",
			product.type,
			" Price: ",
			product.formattedPrice
		)

	_validate_and_restore_owned_assets()


func _on_product_details_query_error(
	response_id,
	debug_message,
	product_ids
):
	print(
		"Product details query error: ",
		response_id,
		" ",
		debug_message
	)

	print("Products: ", product_ids)

	emit_signal(
		"shop_error",
		"Failed to load product details"
	)


func _validate_and_restore_owned_assets():
	query_purchases("inapp")
	query_purchases("subs")


func query_purchases(type = "inapp"):
	if not is_billing_supported:
		return

	payment.queryPurchases(type)


func _on_query_purchases_response(response):
	print("Query purchases response: ", response)

	# Success:
	# {
	#     "status": 0,
	#     "purchases": [...]
	# }
	#
	# Error:
	# {
	#     "status": 1,
	#     "response_code": ...,
	#     "debug_message": "..."
	# }

	if response == null:
		return

	if not response.has("status"):
		return

	if int(response.status) != 0:
		var message = "Failed to query purchases"

		if response.has("debug_message"):
			message = str(response.debug_message)

		emit_signal("shop_error", message)
		return

	if not response.has("purchases"):
		return

	_process_purchase_list(
		response.purchases,
		true
	)


func _on_purchases_updated(purchases):
	print("Purchases updated: ", purchases)

	_process_purchase_list(
		purchases,
		false
	)


func _process_purchase_list(purchases, is_restore):
	for purchase in purchases:
		var product_id = str(purchase.productId)
		var purchase_token = str(purchase.purchaseToken)

		tracked_purchases[purchase_token] = purchase

		match int(purchase.purchaseState):
			1:
				_process_successful_purchase(
					purchase,
					product_id,
					is_restore
				)

			2:
				print("Purchase pending: ", product_id)

				emit_signal(
					"shop_purchase_pending",
					product_id
				)

			0:
				print(
					"Unspecified purchase state: ",
					product_id
				)


func _process_successful_purchase(
	purchase,
	product_id,
	is_restore
):
	var purchase_token = str(purchase.purchaseToken)
	var acknowledged = bool(purchase.isAcknowledged)

	if product_id in SUBSCRIPTION_ITEMS:
		if not acknowledged:
			payment.acknowledgePurchase(purchase_token)
		else:
			_grant_subscription(
				product_id,
				is_restore
			)

		return

	if product_id in CONSUMABLE_ITEMS:
		# Grant the consumable only after Google Play
		# confirms that the purchase was consumed.
		payment.consumePurchase(purchase_token)
		return

	if product_id in NON_CONSUMABLE_ITEMS:
		if not acknowledged:
			payment.acknowledgePurchase(purchase_token)
		else:
			_grant_non_consumable(
				product_id,
				is_restore
			)

		return

	print("Unknown product: ", product_id)

	if not acknowledged:
		payment.acknowledgePurchase(purchase_token)


func _on_purchase_acknowledged(purchase_token):
	print("Purchase acknowledged: ", purchase_token)

	if not tracked_purchases.has(purchase_token):
		print("Purchase token is not tracked")
		return

	var purchase = tracked_purchases[purchase_token]
	var product_id = str(purchase.productId)

	if product_id in SUBSCRIPTION_ITEMS:
		_grant_subscription(product_id, false)

	elif product_id in NON_CONSUMABLE_ITEMS:
		_grant_non_consumable(product_id, false)


func _on_purchase_acknowledgement_error(
	response_id,
	debug_message,
	purchase_token
):
	print(
		"Purchase acknowledgement error: ",
		response_id,
		" ",
		debug_message,
		" token: ",
		purchase_token
	)

	emit_signal(
		"shop_error",
		"Acknowledgement failed: " + str(debug_message)
	)


func _on_purchase_consumed(purchase_token):
	print("Purchase consumed: ", purchase_token)

	if not tracked_purchases.has(purchase_token):
		print("Consumed purchase token is not tracked")
		return

	var purchase = tracked_purchases[purchase_token]
	var product_id = str(purchase.productId)

	_grant_consumable(product_id)


func _on_purchase_consumption_error(
	response_id,
	debug_message,
	purchase_token
):
	print(
		"Purchase consumption error: ",
		response_id,
		" ",
		debug_message,
		" token: ",
		purchase_token
	)

	emit_signal(
		"shop_error",
		"Consumption failed: " + str(debug_message)
	)


func _on_purchase_error(response_id, debug_message):
	print(
		"Purchase error: ",
		response_id,
		" ",
		debug_message
	)

	emit_signal(
		"shop_error",
		"Purchase failed: " + str(debug_message)
	)


func make_purchase(product_id):
	if not is_billing_supported:
		emit_signal(
			"shop_error",
			"Billing service not connected"
		)
		return

	if not available_products.has(product_id):
		emit_signal(
			"shop_error",
			"Product details not found for ID: " + product_id
		)
		return

	var product = available_products[product_id]
	var type = str(product.type)

	# The Java plugin gets ProductDetails from its internal cache.
	# GDScript passes product ID, product type, account ID and profile ID.
	payment.purchase(
		product_id,
		type,
		"",
		""
	)


func _grant_non_consumable(product_id, is_restored):
	if is_restored:
		print("Restoring non-consumable: ", product_id)
		emit_signal("shop_purchase_restored", product_id)
	else:
		print("Granting non-consumable: ", product_id)
		emit_signal("shop_item_purchased", product_id)


func _grant_consumable(product_id):
	print("Granting consumable: ", product_id)

	emit_signal(
		"shop_consumable_purchased",
		product_id
	)


func _grant_subscription(product_id, is_restored):
	print("Granting subscription: ", product_id)

	emit_signal(
		"shop_subscription_purchased",
		product_id
	)


func get_product_price(product_id) -> String:
	if available_products.has(product_id):
		return str(
			available_products[product_id].formattedPrice
		)

	return ""


func is_product_available(product_id) -> bool:
	return available_products.has(product_id)
```

### Plugin API

The plugin exposes these methods:

- `startConnection()`
- `endConnection()`
- `isReady()`
- `getConnectionState()`
- `queryPurchases(type)`
- `queryProductDetails(product_ids, type)`
- `acknowledgePurchase(purchase_token)`
- `consumePurchase(purchase_token)`
- `purchase(product_id, type, account_id, profile_id)`

The `purchase()` method uses the `ProductDetails` cached internally by the plugin. Therefore `queryProductDetails()` must be called before starting a purchase.

For subscriptions, the plugin automatically uses the first available subscription offer from the returned `ProductDetails`.

### ProductDetails dictionary

The plugin converts Google Play `ProductDetails` to Godot dictionaries containing:

- `productId`
- `title`
- `description`
- `type`
- `price`
- `currencyCode`
- `formattedPrice`

### Purchase dictionary

Purchases are converted to Godot dictionaries containing:

- `orderId`
- `packageName`
- `productId`
- `purchaseTime`
- `purchaseState`
- `purchaseToken`
- `signature`
- `isAutoRenewing`
- `originalJson`
- `isAcknowledged`

### Product types

Use:

- `inapp` for one-time and consumable products.
- `subs` for subscriptions.

### Consumables

Consumable purchases should be consumed with:

```gdscript
payment.consumePurchase(purchase_token)
```

The example grants the consumable only after the `purchase_consumed` signal is received.

### Non-consumables and subscriptions

Non-consumable purchases and subscriptions should be acknowledged with:

```gdscript
payment.acknowledgePurchase(purchase_token)
```

The example grants the entitlement after `purchase_acknowledged`.

### Pending purchases

If Google Play reports `purchaseState == 2`, the purchase is pending. The example does not grant the product while it is pending. The entitlement is processed only after Google Play later reports the purchase as `PURCHASED`.

### Account and profile IDs

The `purchase()` method accepts obfuscated account and profile IDs:

```gdscript
payment.purchase(
	product_id,
	type,
	account_id,
	profile_id
)
```

The example passes empty strings. Replace them with your own obfuscated identifiers if your application uses them.


## Upcoming Improvements

Stay tuned for updates, and feel free to [open an issue](https://github.com/201949/godot-google-play-billing-9/issues) or [contribute](https://github.com/201949/godot-google-play-billing-9/pulls) if you have any suggestions or feedback!
