package com.jean.tokenmonitor;

import android.app.Activity;

public class DisabledWalletGateway implements WalletGateway {

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void requestTrade(Activity activity, TradeRequest request, Callback callback) {
        callback.onError(
                "A carteira ainda não foi ativada. Nenhuma compra ou venda foi enviada e nenhum aplicativo externo foi aberto."
        );
    }
}
