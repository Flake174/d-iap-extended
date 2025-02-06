package com.defold.iap;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.samsung.android.sdk.iap.lib.helper.IapHelper;
import com.samsung.android.sdk.iap.lib.constants.HelperDefine;
import com.samsung.android.sdk.iap.lib.listener.OnGetProductsDetailsListener;
import com.samsung.android.sdk.iap.lib.listener.OnPaymentListener;
import com.samsung.android.sdk.iap.lib.listener.OnConsumePurchasedItemsListener;
import com.samsung.android.sdk.iap.lib.listener.OnGetPromotionEligibilityListener;
import com.samsung.android.sdk.iap.lib.listener.OnGetOwnedListListener;
import com.samsung.android.sdk.iap.lib.vo.ErrorVo;
import com.samsung.android.sdk.iap.lib.vo.ProductVo;
import com.samsung.android.sdk.iap.lib.vo.PurchaseVo;
import com.samsung.android.sdk.iap.lib.vo.ConsumeVo;
import com.samsung.android.sdk.iap.lib.vo.PromotionEligibilityVo;
import com.samsung.android.sdk.iap.lib.vo.OwnedProductVo;

public class IapSamsung {
    public static final String TAG = "IapSamsung";

    private boolean autoFinishTransactions;
    private Activity activity;
    private IapHelper iapHelper;

    public IapSamsung(Activity activity, boolean autoFinishTransactions) {
        iapHelper = IapHelper.getInstance(activity);
        iapHelper.setOperationMode(HelperDefine.OperationMode.OPERATION_MODE_PRODUCTION);
        this.activity = activity;
        this.autoFinishTransactions = autoFinishTransactions;
    }

    public void listItems(final String products, final IListProductsListener productsListener, final long commandPtr) {
        // iapHelper.getOwnedList(HelperDefine.PRODUCT_TYPE_ALL, new OnGetOwnedListListener() {
        //     public void onGetOwnedProducts(ErrorVo errorVo, ArrayList<OwnedProductVo> ownedList) {
        //         Log.d(TAG, "onGetOwnedProducts");
        //         if (errorVo.getErrorCode() == IapHelper.IAP_ERROR_NONE) {
        //             StringJoiner joiner = new StringJoiner(",");
        //             for (OwnedProductVo item : ownedList) {
        //                 if (item.getIsConsumable()) {
        //                     joiner.add(item.getPurchaseId());
        //                 }
        //             }
        //             String productsToConsume = joiner.toString();
        //             if (!productsToConsume.trim().isEmpty()) {
        //                 Log.d(TAG, "start consuming...");
        //                 iapHelper.consumePurchasedItems(productsToConsume, new OnConsumePurchasedItemsListener() {
        //                     public void onConsumePurchasedItems(ErrorVo errorVo, ArrayList<ConsumeVo> consumeList) {
        //                         if (errorVo.getErrorCode() == IapHelper.IAP_ERROR_NONE) {
        //                             for (ConsumeVo item : consumeList) {
        //                                 Log.d(TAG, "Purchase consumed successfully: " + item.getPurchaseId());
        //                             }
        //                         } else {
        //                             Log.e(TAG, "Unable to consume purchase: " + errorVo.getErrorString());
        //                         }
        //                     }
        //                 });
        //             }
        //         } else {
        //             Log.e(TAG, "Unable to get owned purchases: " + errorVo.getErrorString());
        //         }
        //     }
        // });

        iapHelper.getProductsDetails(products, new OnGetProductsDetailsListener() {
            @Override
            public void onGetProducts(ErrorVo errorVo, ArrayList<ProductVo> iapProductList) {
                if (errorVo.getErrorCode() == IapHelper.IAP_ERROR_NONE) {
                    JSONArray a = new JSONArray();

                    for (ProductVo item : iapProductList) {
                        if (item != null) {
                            JSONObject p = new JSONObject();
                            try {
                                p.put("ident", item.getItemId());
                                p.put("title", item.getItemName());
                                p.put("description", item.getItemDesc());
                                p.put("price", item.getItemPrice());
                                p.put("price_string", item.getItemPriceString());
                                p.put("currency_code", item.getCurrencyCode());
                            } catch (JSONException e) {
                                Log.wtf(TAG, "Failed to convert product details", e);
                            }

                            a.put(p);
                        }
                    }
                    productsListener.onProductsResult(IapJNI.BILLING_RESPONSE_RESULT_OK, a.toString(), commandPtr);

                } else {
                    Log.e(TAG, "Unable to list products: " + errorVo.getErrorString());
                    productsListener.onProductsResult(IapJNI.BILLING_RESPONSE_RESULT_ERROR, null, commandPtr);
                }
            }
        });
    }

    private String toISO8601(final Date date) {
        String formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(date);
        return formatted.substring(0, 22) + ":" + formatted.substring(22);
    }

    public void buy(final String product, final String token, final IPurchaseListener listener) {
        iapHelper.startPayment(product, token, new OnPaymentListener() {
            @Override
            public void onPayment(ErrorVo errorVo, PurchaseVo purchaseVo) {
                if (errorVo.getErrorCode() == IapHelper.IAP_ERROR_NONE) {
                    if (purchaseVo != null) {
                        String passThroughParam = purchaseVo.getPassThroughParam();
                        JSONObject transaction = new JSONObject();
                        try {
                            transaction.put("ident", purchaseVo.getItemId());
                            transaction.put("state", IapJNI.TRANS_STATE_PURCHASED);
                            transaction.put("date", purchaseVo.getPurchaseDate());
                            transaction.put("trans_ident", purchaseVo.getOrderId());
                            transaction.put("receipt", purchaseVo.getPurchaseId());
                            transaction.put("signature", purchaseVo.getPaymentId());
                            transaction.put("original_json", purchaseVo.getJsonString());
                        }
                        catch (JSONException e) {
                            Log.wtf(TAG, "Failed to convert purchase", e);
                        }
                        listener.onPurchaseResult(IapJNI.BILLING_RESPONSE_RESULT_OK, transaction.toString());
                    }
                } else {
                    listener.onPurchaseResult(IapJNI.BILLING_RESPONSE_RESULT_ERROR, errorVo.getErrorString());
                }
            }
        });
    }

    public void finishTransaction(final String purchaseToken, final IPurchaseListener purchaseListener) {
        Log.d(TAG, "finishTransaction() " + purchaseToken);
        iapHelper.consumePurchasedItems(purchaseToken, new OnConsumePurchasedItemsListener() {
            @Override
            public void onConsumePurchasedItems(ErrorVo errorVo, ArrayList<ConsumeVo> consumeList) {
                if (errorVo.getErrorCode() == IapHelper.IAP_ERROR_NONE) {
                    for (ConsumeVo item : consumeList) {
                        Log.d(TAG, "Purchase consumed successfully: " + item.getPurchaseId());
                    }
                } else {
                    Log.e(TAG, "Unable to consume purchase: " + errorVo.getErrorString());
                }
            }
        });
    }

    public void stop() {
        Log.d(TAG, "stop()");
    }

    public void restore(final IPurchaseListener listener) {
        Log.d(TAG, "restore()");
    }

    public void processPendingConsumables(final IPurchaseListener listener) {
        Log.d(TAG, "processPendingConsumables()");
    }

    public void acknowledgeTransaction(final String purchaseToken, final IPurchaseListener purchaseListener) {
        Log.d(TAG, "acknowledgeTransaction()");
        // Stub to prevent errors.
    }
}