package com.jean.tokenmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final long REFRESH_MS = 5000L;
    private static final String DEX_API = "https://api.dexscreener.com/latest/dex/tokens/";
    private static final String BINANCE_PACKAGE = "com.binance.dev";

    private final Token[] tokens = new Token[] {
            new Token("AI", "0x2e8c31162b855a2ffa90f6f8634643ad6f111e18"),
            new Token("PONS", "0x39dbed3a2bd333467115de45665cc57f813c4571"),
            new Token("CASHCAT", "0x020bfc650a365f8bb26819deaabf3e21291018b4"),
            new Token("MEME", "0x385f4f8ae47651ce5f58f5265395a669f8281e18")
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Map<String, CardRefs> cards = new HashMap<>();
    private TextView statusText;
    private boolean refreshRunning = false;
    private int completed = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(autoRefresh);
        handler.postDelayed(autoRefresh, REFRESH_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(autoRefresh);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            refreshAll();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 246, 248));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Monitor dos 4 Tokens", 26, Typeface.BOLD, Color.rgb(20, 25, 32));
        root.addView(title);

        TextView subtitle = text("Robinhood Chain • atualização automática a cada 5 s", 14,
                Typeface.NORMAL, Color.rgb(92, 99, 110));
        subtitle.setPadding(0, dp(4), 0, dp(10));
        root.addView(subtitle);

        statusText = text("Buscando preços…", 13, Typeface.BOLD, Color.rgb(75, 85, 99));
        statusText.setPadding(0, 0, 0, dp(12));
        root.addView(statusText);

        for (Token token : tokens) {
            root.addView(buildTokenCard(token));
        }

        Button refresh = button("Atualizar agora", Color.rgb(31, 41, 55));
        refresh.setOnClickListener(v -> refreshAll());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        rp.setMargins(0, dp(4), 0, dp(10));
        root.addView(refresh, rp);

        Button binance = button("Abrir Binance", Color.rgb(240, 185, 11));
        binance.setTextColor(Color.BLACK);
        binance.setOnClickListener(v -> openBinance());
        root.addView(binance, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView safety = text(
                "Segurança: esta versão não armazena senha, API key, chave privada ou frase-semente. " +
                        "Comprar/Vender abre uma confirmação e mantém a ordem real desativada até a conexão segura da Binance Wallet ser adicionada.",
                12, Typeface.NORMAL, Color.rgb(92, 99, 110));
        safety.setPadding(0, dp(14), 0, 0);
        root.addView(safety);

        return scroll;
    }

    private View buildTokenCard(Token token) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(makeRounded(Color.WHITE, 18));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView symbol = text(token.symbol, 20, Typeface.BOLD, Color.rgb(17, 24, 39));
        header.addView(symbol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView live = text("● AO VIVO", 11, Typeface.BOLD, Color.rgb(22, 163, 74));
        header.addView(live);
        card.addView(header);

        TextView price = text("$ —", 30, Typeface.BOLD, Color.rgb(17, 24, 39));
        price.setPadding(0, dp(8), 0, dp(3));
        card.addView(price);

        TextView change = text("1h —   •   24h —", 14, Typeface.BOLD, Color.rgb(75, 85, 99));
        card.addView(change);

        TextView market = text("Liquidez: —   |   Volume 24h: —", 13, Typeface.NORMAL, Color.rgb(92, 99, 110));
        market.setPadding(0, dp(5), 0, dp(10));
        card.addView(market);

        TextView contract = text(shortAddress(token.address), 11, Typeface.NORMAL, Color.rgb(107, 114, 128));
        contract.setPadding(0, 0, 0, dp(12));
        card.addView(contract);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button buy = button("Comprar", Color.rgb(22, 163, 74));
        Button sell = button("Vender", Color.rgb(220, 38, 38));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        bp.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        sp.setMargins(dp(6), 0, 0, 0);
        actions.addView(buy, bp);
        actions.addView(sell, sp);
        card.addView(actions);

        CardRefs refs = new CardRefs(price, change, market);
        cards.put(token.symbol, refs);
        buy.setOnClickListener(v -> showTradeDialog(token, "COMPRA", refs.lastPrice));
        sell.setOnClickListener(v -> showTradeDialog(token, "VENDA", refs.lastPrice));
        return card;
    }

    private void showTradeDialog(Token token, String side, double currentPrice) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        TextView info = text(
                token.symbol + "\nPreço atual: " + (currentPrice > 0 ? "$ " + fmtPrice(currentPrice) : "aguardando preço"),
                15, Typeface.NORMAL, Color.DKGRAY);
        box.addView(info);

        EditText amount = new EditText(this);
        amount.setHint(side.equals("COMPRA") ? "Valor da compra em US$" : "Quantidade de tokens");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextSize(17);
        box.addView(amount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        new AlertDialog.Builder(this)
                .setTitle(side + " • " + token.symbol)
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    String value = amount.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "Informe um valor.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showSafeModeDialog(token, side, value);
                })
                .show();
    }

    private void showSafeModeDialog(Token token, String side, String value) {
        new AlertDialog.Builder(this)
                .setTitle("Confirmação de segurança")
                .setMessage(side + " de " + token.symbol + "\nValor: " + value +
                        "\n\nA negociação real ainda está desativada nesta versão de teste. " +
                        "A Binance será aberta para você continuar pela carteira oficial.")
                .setNegativeButton("Fechar", null)
                .setPositiveButton("Abrir Binance", (d, w) -> openBinance())
                .show();
    }

    private void openBinance() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(BINANCE_PACKAGE);
            if (launch != null) {
                startActivity(launch);
                return;
            }
        } catch (Exception ignored) { }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + BINANCE_PACKAGE)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Não foi possível abrir a Binance.", Toast.LENGTH_LONG).show();
        }
    }

    private synchronized void refreshAll() {
        if (refreshRunning) return;
        refreshRunning = true;
        completed = 0;
        statusText.setText("Atualizando preços…");
        for (Token token : tokens) {
            pool.submit(() -> {
                PriceResult result = fetchPrice(token);
                runOnUiThread(() -> {
                    updateCard(token, result);
                    completed++;
                    if (completed >= tokens.length) {
                        refreshRunning = false;
                        statusText.setText("Atualizado: " + nowString());
                    }
                });
            });
        }
    }

    private PriceResult fetchPrice(Token token) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(DEX_API + token.address);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "TokenMonitorJean/1.0");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return PriceResult.error("HTTP " + code);
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) body.append(line);
            }

            JSONObject root = new JSONObject(body.toString());
            JSONArray pairs = root.optJSONArray("pairs");
            if (pairs == null || pairs.length() == 0) {
                return PriceResult.error("Sem par encontrado");
            }

            JSONObject best = null;
            double bestLiquidity = -1;
            for (int i = 0; i < pairs.length(); i++) {
                JSONObject pair = pairs.optJSONObject(i);
                if (pair == null) continue;
                if (!"robinhood".equalsIgnoreCase(pair.optString("chainId"))) continue;
                double liq = pair.optJSONObject("liquidity") != null
                        ? pair.optJSONObject("liquidity").optDouble("usd", 0) : 0;
                if (best == null || liq > bestLiquidity) {
                    best = pair;
                    bestLiquidity = liq;
                }
            }
            if (best == null) return PriceResult.error("Sem par Robinhood");

            double price = parseDouble(best.optString("priceUsd", "0"));
            JSONObject pc = best.optJSONObject("priceChange");
            double h1 = pc != null ? pc.optDouble("h1", 0) : 0;
            double h24 = pc != null ? pc.optDouble("h24", 0) : 0;
            JSONObject volume = best.optJSONObject("volume");
            double v24 = volume != null ? volume.optDouble("h24", 0) : 0;
            return new PriceResult(price, h1, h24, Math.max(bestLiquidity, 0), v24, null);
        } catch (Exception e) {
            return PriceResult.error(e.getClass().getSimpleName());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void updateCard(Token token, PriceResult result) {
        CardRefs refs = cards.get(token.symbol);
        if (refs == null) return;
        if (result.error != null) {
            refs.price.setText("$ —");
            refs.change.setText("Falha temporária: " + result.error);
            refs.change.setTextColor(Color.rgb(180, 83, 9));
            refs.market.setText("Toque em Atualizar agora para tentar novamente.");
            return;
        }
        refs.lastPrice = result.price;
        refs.price.setText("$ " + fmtPrice(result.price));
        refs.change.setText("1h " + signed(result.h1) + "%   •   24h " + signed(result.h24) + "%");
        refs.change.setTextColor(result.h24 >= 0 ? Color.rgb(22, 163, 74) : Color.rgb(220, 38, 38));
        refs.market.setText("Liquidez: " + compactMoney(result.liquidity) + "   |   Volume 24h: " + compactMoney(result.volume24));
    }

    private TextView text(String value, int sp, int style, int color) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, style);
        tv.setLineSpacing(0, 1.08f);
        return tv;
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(makeRounded(color, 14));
        return b;
    }

    private android.graphics.drawable.GradientDrawable makeRounded(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String shortAddress(String address) {
        if (address.length() < 16) return address;
        return address.substring(0, 8) + "…" + address.substring(address.length() - 6);
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); }
        catch (Exception e) { return 0; }
    }

    private String fmtPrice(double value) {
        if (value >= 1) return new DecimalFormat("0.0000").format(value);
        if (value >= 0.01) return new DecimalFormat("0.000000").format(value);
        return new DecimalFormat("0.00000000").format(value);
    }

    private String signed(double value) {
        return (value > 0 ? "+" : "") + new DecimalFormat("0.00").format(value);
    }

    private String compactMoney(double value) {
        if (value >= 1_000_000_000) return "$ " + new DecimalFormat("0.00").format(value / 1_000_000_000) + "B";
        if (value >= 1_000_000) return "$ " + new DecimalFormat("0.00").format(value / 1_000_000) + "M";
        if (value >= 1_000) return "$ " + new DecimalFormat("0.0").format(value / 1_000) + "K";
        return "$ " + new DecimalFormat("0.00").format(value);
    }

    private String nowString() {
        SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss", new Locale("pt", "BR"));
        df.setTimeZone(TimeZone.getDefault());
        return df.format(new Date());
    }

    private static class Token {
        final String symbol;
        final String address;
        Token(String symbol, String address) {
            this.symbol = symbol;
            this.address = address;
        }
    }

    private static class CardRefs {
        final TextView price;
        final TextView change;
        final TextView market;
        double lastPrice = 0;
        CardRefs(TextView price, TextView change, TextView market) {
            this.price = price;
            this.change = change;
            this.market = market;
        }
    }

    private static class PriceResult {
        final double price;
        final double h1;
        final double h24;
        final double liquidity;
        final double volume24;
        final String error;
        PriceResult(double price, double h1, double h24, double liquidity, double volume24, String error) {
            this.price = price;
            this.h1 = h1;
            this.h24 = h24;
            this.liquidity = liquidity;
            this.volume24 = volume24;
            this.error = error;
        }
        static PriceResult error(String message) {
            return new PriceResult(0, 0, 0, 0, 0, message);
        }
    }
}
