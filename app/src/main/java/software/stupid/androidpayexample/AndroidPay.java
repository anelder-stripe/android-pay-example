package software.stupid.androidpayexample;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.BooleanResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wallet.Cart;
import com.google.android.gms.wallet.FullWallet;
import com.google.android.gms.wallet.FullWalletRequest;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.LineItem;
import com.google.android.gms.wallet.MaskedWallet;
import com.google.android.gms.wallet.MaskedWalletRequest;
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters;
import com.google.android.gms.wallet.PaymentMethodTokenizationType;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;
import com.google.android.gms.wallet.fragment.SupportWalletFragment;
import com.google.android.gms.wallet.fragment.WalletFragmentInitParams;
import com.stripe.android.model.Token;
import com.stripe.android.net.TokenParser;

import org.json.JSONException;

public class AndroidPay extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    // TODO: Configuration Section
    // Fill in the following values in a way that makes sense for your application.
    private static final String PUBLISHABLE_KEY = "pk_test_xxx";
    private static final int ANDROID_PAY_ENVIRONMENT = WalletConstants.ENVIRONMENT_TEST;

    // Possible values for the accepted cards are documented here:
    // https://developers.google.com/android/reference/com/google/android/gms/wallet/WalletConstants.CardNetwork
    private static final IsReadyToPayRequest ACCEPTED_CARDS = IsReadyToPayRequest.newBuilder()
            .addAllowedCardNetwork(WalletConstants.CardNetwork.MASTERCARD)
            .addAllowedCardNetwork(WalletConstants.CardNetwork.VISA)
            .addAllowedCardNetwork(WalletConstants.CardNetwork.AMEX)
            .addAllowedCardNetwork(WalletConstants.CardNetwork.DISCOVER)
            .build();

    // Unique identifiers for asynchronous requests
    private static final int LOAD_MASKED_WALLET_REQUEST_CODE = 1000;
    private static final int LOAD_FULL_WALLET_REQUEST_CODE = 1001;

    // Placeholders
    private SupportWalletFragment walletFragment;
    private GoogleApiClient googleApiClient;

    // Logging
    private static final String TAG = "AndroidPay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_android_pay);
        
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wallet.API, new Wallet.WalletOptions.Builder()
                        .setEnvironment(ANDROID_PAY_ENVIRONMENT)
                        .setTheme(WalletConstants.THEME_LIGHT)
                        .build())
                .build();

        Wallet.Payments.isReadyToPay(googleApiClient, ACCEPTED_CARDS).setResultCallback(
            new ResultCallback<BooleanResult>() {
                @Override
                public void onResult(@NonNull BooleanResult booleanResult) {
                    if (booleanResult.getStatus().isSuccess()) {
                        if (booleanResult.getValue()) {
                            showAndroidPay();
                        } else {
                            hideAndroidPay();
                        }
                    } else {
                        // Error making isReadyToPay call
                        Log.e(TAG, "isReadyToPay:" + booleanResult.getStatus());
                    }
                }
            }
        );
    }

    private void hideAndroidPay() {
        walletFragment =
                (SupportWalletFragment) getSupportFragmentManager().findFragmentById(R.id.wallet_fragment);
        walletFragment.getView().setVisibility(View.GONE);
        Toast.makeText(getApplicationContext(), "Android Pay is not supported.", Toast.LENGTH_LONG).show();
    }

    private void showAndroidPay() {

        walletFragment = (SupportWalletFragment) getSupportFragmentManager().findFragmentById(R.id.wallet_fragment);

        MaskedWalletRequest maskedWalletRequest = MaskedWalletRequest.newBuilder()

                // Request credit card tokenization with Stripe by specifying tokenization parameters:
                .setPaymentMethodTokenizationParameters(PaymentMethodTokenizationParameters.newBuilder()
                        .setPaymentMethodTokenizationType(PaymentMethodTokenizationType.PAYMENT_GATEWAY)
                        .addParameter("gateway", "stripe")
                        .addParameter("stripe:publishableKey", PUBLISHABLE_KEY)
                        .addParameter("stripe:version", com.stripe.android.BuildConfig.VERSION_NAME)
                        .build())
                .setEstimatedTotalPrice("1.00")
                .setCurrencyCode("USD")
                .build();

        // Set the parameters:
        WalletFragmentInitParams initParams = WalletFragmentInitParams.newBuilder()
                .setMaskedWalletRequest(maskedWalletRequest)
                .setMaskedWalletRequestCode(LOAD_MASKED_WALLET_REQUEST_CODE)
                .build();

        // Initialize the fragment:
        walletFragment.initialize(initParams);

    }

    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    public void onStop() {
        super.onStop();
        googleApiClient.disconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        walletFragment = (SupportWalletFragment) getSupportFragmentManager().findFragmentById(R.id.wallet_fragment);

        if (requestCode == LOAD_MASKED_WALLET_REQUEST_CODE) {

            Log.d(TAG, "onActivityResult: " + "requestCode=LOAD_MASKED_WALLET_REQUEST_CODE");

            if (resultCode == Activity.RESULT_OK) {
                MaskedWallet maskedWallet = data.getParcelableExtra(WalletConstants.EXTRA_MASKED_WALLET);
                FullWalletRequest fullWalletRequest = FullWalletRequest.newBuilder()
                        .setCart(Cart.newBuilder()
                                .setCurrencyCode("USD")
                                .setTotalPrice("1.00")
                                .addLineItem(LineItem.newBuilder() // Identify item being purchased
                                        .setCurrencyCode("USD")
                                        .setQuantity("1")
                                        .setDescription("Premium Llama Food")
                                        .setTotalPrice("1.00")
                                        .setUnitPrice("1.00")
                                        .build())
                                .build())
                        .setGoogleTransactionId(maskedWallet.getGoogleTransactionId())
                        .build();
                Wallet.Payments.loadFullWallet(googleApiClient, fullWalletRequest, LOAD_FULL_WALLET_REQUEST_CODE);
            }

        } else if (requestCode == LOAD_FULL_WALLET_REQUEST_CODE) {

            Log.d(TAG, "onActivityResult: " + "requestCode=LOAD_FULL_WALLET_REQUEST_CODE");

            if (resultCode == Activity.RESULT_OK) {
                FullWallet fullWallet = data.getParcelableExtra(WalletConstants.EXTRA_FULL_WALLET);
                String tokenJSON = fullWallet.getPaymentMethodToken().getToken();

                // if (mEnvironment == WalletConstants.ENVIRONMENT_PRODUCTION)
                try {
                    Token token = TokenParser.parseToken(tokenJSON);
                    Toast.makeText(getApplicationContext(), token.getId(), Toast.LENGTH_LONG).show();
                } catch (JSONException jsonException) {
                    // Log the error and notify Stripe helpß
                }
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }
    
    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
