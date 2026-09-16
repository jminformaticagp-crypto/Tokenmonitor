package com.jean.tokenmonitor;

import android.app.Activity;

public interface WalletGateway {

    enum Side {
        BUY,
        SELL
    }

    final class TradeRequest {
        public final String symbol;
        public final String tokenAddress;
        public final Side side;
        public final double amount;
        public final double referencePrice;

        public TradeRequest(String symbol, String tokenAddress, Side side,
                            double amount, double referencePrice) {
            this.symbol = symbol;
            this.tokenAddress = tokenAddress;
            this.side = side;
            this.amount = amount;
            this.referencePrice = referencePrice;
        }
    }

    interface Callback {
        void onSuccess(String message);
        void onError(String message);
    }

    boolean isConnected();

    void requestTrade(Activity activity, TradeRequest request, Callback callback);
}
