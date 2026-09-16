package com.jean.tokenmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.text.DecimalFormatSymbols;
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
    private static final String PREFS = "token_monitor_beta_02";

    private static final int BG = Color.rgb(9, 14, 21);
    private static final int CARD = Color.rgb(18, 26, 36);
    private static final int CARD_ALT = Color.rgb(24, 34, 47);
    private static final int BORDER = Color.rgb(42, 56, 73);
    private static final int TEXT = Color.rgb(244, 247, 251);
    private static final int MUTED = Color.rgb(145, 161, 180);
    private static final int GREEN = Color.rgb(31, 196, 126);
    private static final int RED = Color.rgb(244, 85, 104);
    private static final int AMBER = Color.rgb(245, 183, 55);

    private final Token[] tokens = new Token[] {
            new Token("AI", "0x2e8c31162b855a2ffa90f6f8634643ad6f111e18"),
            new Token("PONS", "0x39dbed3a2bd333467115de45665cc57f813c4571"),
            new Token("CASHCAT", "0x020bfc650a365f8bb26819deaabf3e21291018b4"),
            new Token("MEME", "0x385f4f8ae47651ce5f58f5265395a669f8281e18")
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Map<String, CardRefs> cards = new HashMap<>();

    private SharedPreferences prefs;
    private WalletGateway walletGateway;
    private TextView statusText;
    private TextView portfolioValue;
    private TextView portfolioInvested;
    private TextView portfolioResult;
    private boolean refreshRunning = false;
    private int completed = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        walletGateway = new DisabledWalletGateway();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
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
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(26));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Token Monitor", 25, Typeface.BOLD, TEXT);
        TextView subtitle = text("JEAN  •  ROBINHOOD CHAIN", 11, Typeface.BOLD, MUTED);
        subtitle.setLetterSpacing(0.08f);
        brand.addView(title);
        brand.addView(subtitle);
        top.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView beta = text("BETA 0.2", 11, Typeface.BOLD, BG);
        beta.setGravity(Gravity.CENTER);
        beta.setBackground(makeRounded(AMBER, 18));
        top.addView(beta, new LinearLayout.LayoutParams(dp(78), dp(30)));
        root.addView(top);

        statusText = text("Conectando ao mercado…", 12, Typeface.NORMAL, MUTED);
        statusText.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusText);

        root.addView(buildPortfolioPanel());

        TextView section = text("ATIVOS MONITORADOS", 11, Typeface.BOLD, MUTED);
        section.setLetterSpacing(0.08f);
        section.setPadding(dp(2), dp(18), 0, dp(10));
        root.addView(section);

        for (Token token : tokens) {
            root.addView(buildTokenCard(token));
        }

        LinearLayout walletPanel = new LinearLayout(this);
        walletPanel.setOrientation(LinearLayout.VERTICAL);
        walletPanel.setPadding(dp(14), dp(13), dp(14), dp(13));
        walletPanel.setBackground(makeRoundedStroke(CARD, 16, BORDER));

        TextView walletTitle = text("Carteira integrada", 14, Typeface.BOLD, TEXT);
        TextView walletInfo = text(
                "Estrutura preparada para conexão e assinatura de compras/vendas. Nesta Beta 0.2 a carteira permanece desativada.",
                12, Typeface.NORMAL, MUTED);
        walletInfo.setPadding(0, dp(5), 0, 0);
        walletPanel.addView(walletTitle);
        walletPanel.addView(walletInfo);
        root.addView(walletPanel);

        TextView footer = text("Preços atualizados automaticamente a cada 5 segundos", 11,
                Typeface.NORMAL, MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(15), 0, 0);
        root.addView(footer);

        return scroll;
    }

    private View buildPortfolioPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(17), dp(15), dp(17), dp(15));
        panel.setBackground(makeRoundedStroke(CARD_ALT, 18, Color.rgb(52, 72, 96)));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("VALOR TOTAL DA CARTEIRA", 11, Typeface.BOLD, MUTED);
        label.setLetterSpacing(0.07f);
        header.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView live = text("● AO VIVO", 10, Typeface.BOLD, GREEN);
        header.addView(live);
        panel.addView(header);

        portfolioValue = text("US$ 0,00", 30, Typeface.BOLD, TEXT);
        portfolioValue.setPadding(0, dp(8), 0, dp(7));
        panel.addView(portfolioValue);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        portfolioInvested = text("Investido  US$ 0,00", 12, Typeface.NORMAL, MUTED);
        portfolioResult = text("Resultado  —", 12, Typeface.BOLD, MUTED);
        portfolioResult.setGravity(Gravity.END);
        metrics.addView(portfolioInvested, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        metrics.addView(portfolioResult, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(metrics);

        return panel;
    }

    private View buildTokenCard(Token token) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackground(makeRoundedStroke(CARD, 18, BORDER));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, dp(11));
        card.setLayoutParams(cp);

        LinearLayout headline = new LinearLayout(this);
        headline.setOrientation(LinearLayout.HORIZONTAL);
        headline.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        TextView symbol = text(token.symbol, 19, Typeface.BOLD, TEXT);
        TextView contract = text(shortAddress(token.address), 10, Typeface.NORMAL, MUTED);
        identity.addView(symbol);
        identity.addView(contract);
        headline.addView(identity, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout quote = new LinearLayout(this);
        quote.setOrientation(LinearLayout.VERTICAL);
        quote.setGravity(Gravity.END);
        TextView price = text("US$ —", 22, Typeface.BOLD, TEXT);
        price.setGravity(Gravity.END);
        TextView change = text("1h —  •  24h —", 11, Typeface.BOLD, MUTED);
        change.setGravity(Gravity.END);
        quote.addView(price);
        quote.addView(change);
        headline.addView(quote);
        card.addView(headline);

        TextView market = text("Liquidez —  •  Volume 24h —", 11, Typeface.NORMAL, MUTED);
        market.setPadding(0, dp(9), 0, dp(10));
        card.addView(market);

        LinearLayout position = new LinearLayout(this);
        position.setOrientation(LinearLayout.VERTICAL);
        position.setPadding(dp(11), dp(9), dp(11), dp(9));
        position.setBackground(makeRounded(CARD_ALT, 12));

        LinearLayout posTop = new LinearLayout(this);
        posTop.setOrientation(LinearLayout.HORIZONTAL);
        TextView invested = text("Investido  US$ 0,00", 12, Typeface.NORMAL, MUTED);
        TextView current = text("Valor atual  US$ 0,00", 12, Typeface.NORMAL, MUTED);
        current.setGravity(Gravity.END);
        posTop.addView(invested, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        posTop.addView(current, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        position.addView(posTop);

        LinearLayout posBottom = new LinearLayout(this);
        posBottom.setOrientation(LinearLayout.HORIZONTAL);
        posBottom.setPadding(0, dp(5), 0, 0);
        TextView average = text("Preço médio  —", 11, Typeface.NORMAL, MUTED);
        TextView pnl = text("Resultado  —", 12, Typeface.BOLD, MUTED);
        pnl.setGravity(Gravity.END);
        posBottom.addView(average, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        posBottom.addView(pnl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        position.addView(posBottom);
        card.addView(position);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(10), 0, 0);

        Button positionButton = smallButton("Posição", Color.rgb(54, 69, 88));
        Button buy = smallButton("Comprar", Color.rgb(18, 137, 87));
        Button sell = smallButton("Vender", Color.rgb(173, 54, 70));

        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(38), 1f);
        ap.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        bp.setMargins(dp(5), 0, dp(5), 0);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        sp.setMargins(dp(5), 0, 0, 0);
        actions.addView(positionButton, ap);
        actions.addView(buy, bp);
        actions.addView(sell, sp);
        card.addView(actions);

        CardRefs refs = new CardRefs(price, change, market, invested, current, average, pnl);
        refs.invested = readDouble("invested_" + token.symbol);
        refs.averageEntry = readDouble("average_" + token.symbol);
        cards.put(token.symbol, refs);
        updatePosition(token, refs);

        positionButton.setOnClickListener(v -> showPositionDialog(token, refs));
        buy.setOnClickListener(v -> showTradeDialog(token, WalletGateway.Side.BUY, refs.lastPrice));
        sell.setOnClickListener(v -> showTradeDialog(token, WalletGateway.Side.SELL, refs.lastPrice));
        return card;
    }

    private void showPositionDialog(Token token, CardRefs refs) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        TextView help = text(
                "Enquanto a carteira não está conectada, informe sua posição manualmente para calcular valor atual e lucro/prejuízo.",
                13, Typeface.NORMAL, Color.DKGRAY);
        box.addView(help);

        EditText investedInput = decimalInput("Valor total investido em US$");
        if (refs.invested > 0) investedInput.setText(rawNumber(refs.invested));
        box.addView(investedInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        EditText averageInput = decimalInput("Preço médio de entrada em US$");
        if (refs.averageEntry > 0) averageInput.setText(rawNumber(refs.averageEntry));
        box.addView(averageInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        new AlertDialog.Builder(this)
                .setTitle("Posição • " + token.symbol)
                .setView(box)
                .setNeutralButton("Zerar", (d, w) -> {
                    refs.invested = 0;
                    refs.averageEntry = 0;
                    savePosition(token, refs);
                    updatePosition(token, refs);
                    updatePortfolio();
                })
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (d, w) -> {
                    double investedValue = parseUserNumber(investedInput.getText().toString());
                    double averageValue = parseUserNumber(averageInput.getText().toString());
                    if (investedValue <= 0 || averageValue <= 0) {
                        Toast.makeText(this, "Informe valores maiores que zero.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    refs.invested = investedValue;
                    refs.averageEntry = averageValue;
                    savePosition(token, refs);
                    updatePosition(token, refs);
                    updatePortfolio();
                })
                .show();
    }

    private void showTradeDialog(Token token, WalletGateway.Side side, double currentPrice) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        String sideLabel = side == WalletGateway.Side.BUY ? "COMPRA" : "VENDA";
        TextView info = text(
                token.symbol + "\nPreço atual: " + (currentPrice > 0 ? "US$ " + fmtPrice(currentPrice) : "aguardando preço"),
                14, Typeface.NORMAL, Color.DKGRAY);
        box.addView(info);

        EditText amount = decimalInput(side == WalletGateway.Side.BUY
                ? "Valor da compra em US$" : "Quantidade de tokens");
        box.addView(amount, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        new AlertDialog.Builder(this)
                .setTitle(sideLabel + " • " + token.symbol)
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    double value = parseUserNumber(amount.getText().toString());
                    if (value <= 0) {
                        Toast.makeText(this, "Informe um valor maior que zero.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    requestWalletTrade(token, side, value, currentPrice);
                })
                .show();
    }

    private void requestWalletTrade(Token token, WalletGateway.Side side, double amount, double price) {
        WalletGateway.TradeRequest request = new WalletGateway.TradeRequest(
                token.symbol, token.address, side, amount, price);
        walletGateway.requestTrade(this, request, new WalletGateway.Callback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Carteira preparada")
                        .setMessage(message + "\n\nOs botões já usam a camada de negociação da Beta 0.2. Quando a carteira for adicionada, esta mesma tela poderá enviar a ordem sem abrir outro aplicativo.")
                        .setPositiveButton("Entendi", null)
                        .show());
            }
        });
    }

    private synchronized void refreshAll() {
        if (refreshRunning) return;
        refreshRunning = true;
        completed = 0;
        statusText.setText("Atualizando cotações…");
        for (Token token : tokens) {
            pool.submit(() -> {
                PriceResult result = fetchPrice(token);
                runOnUiThread(() -> {
                    updateCard(token, result);
                    completed++;
                    if (completed >= tokens.length) {
                        refreshRunning = false;
                        statusText.setText("Mercado atualizado às " + nowString() + "  •  ciclo de 5 s");
                        updatePortfolio();
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
            conn.setRequestProperty("User-Agent", "TokenMonitorJean/0.2-beta");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return PriceResult.error("HTTP " + code);

            StringBuilder body = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) body.append(line);
            }

            JSONObject root = new JSONObject(body.toString());
            JSONArray pairs = root.optJSONArray("pairs");
            if (pairs == null || pairs.length() == 0) return PriceResult.error("Sem par encontrado");

            JSONObject best = null;
            double bestLiquidity = -1;
            for (int i = 0; i < pairs.length(); i++) {
                JSONObject pair = pairs.optJSONObject(i);
                if (pair == null) continue;
                if (!"robinhood".equalsIgnoreCase(pair.optString("chainId"))) continue;
                JSONObject liquidity = pair.optJSONObject("liquidity");
                double liq = liquidity != null ? liquidity.optDouble("usd", 0) : 0;
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
            refs.change.setText("Cotação temporariamente indisponível");
            refs.change.setTextColor(AMBER);
            return;
        }

        refs.lastPrice = result.price;
        refs.price.setText("US$ " + fmtPrice(result.price));
        refs.change.setText("1h " + signed(result.h1) + "%  •  24h " + signed(result.h24) + "%");
        refs.change.setTextColor(result.h24 >= 0 ? GREEN : RED);
        refs.market.setText("Liquidez " + compactMoney(result.liquidity) + "  •  Volume 24h " + compactMoney(result.volume24));
        updatePosition(token, refs);
    }

    private void updatePosition(Token token, CardRefs refs) {
        refs.investedText.setText("Investido  " + money(refs.invested));
        refs.averageText.setText(refs.averageEntry > 0
                ? "Preço médio  US$ " + fmtPrice(refs.averageEntry)
                : "Preço médio  —");

        if (refs.invested <= 0 || refs.averageEntry <= 0 || refs.lastPrice <= 0) {
            refs.currentValue = 0;
            refs.currentText.setText("Valor atual  US$ 0,00");
            refs.pnlText.setText("Resultado  —");
            refs.pnlText.setTextColor(MUTED);
            return;
        }

        double quantity = refs.invested / refs.averageEntry;
        refs.currentValue = quantity * refs.lastPrice;
        double pnlUsd = refs.currentValue - refs.invested;
        double pnlPercent = (pnlUsd / refs.invested) * 100.0;

        refs.currentText.setText("Valor atual  " + money(refs.currentValue));
        refs.pnlText.setText("Resultado  " + signedMoney(pnlUsd) + "  (" + signed(pnlPercent) + "%)");
        refs.pnlText.setTextColor(pnlUsd >= 0 ? GREEN : RED);
    }

    private void updatePortfolio() {
        double investedTotal = 0;
        double currentTotal = 0;
        for (CardRefs refs : cards.values()) {
            investedTotal += refs.invested;
            currentTotal += refs.currentValue;
        }
        double pnl = currentTotal - investedTotal;
        double pnlPct = investedTotal > 0 ? (pnl / investedTotal) * 100.0 : 0;

        portfolioValue.setText(money(currentTotal));
        portfolioInvested.setText("Investido  " + money(investedTotal));
        if (investedTotal > 0) {
            portfolioResult.setText("Resultado  " + signedMoney(pnl) + " (" + signed(pnlPct) + "%)");
            portfolioResult.setTextColor(pnl >= 0 ? GREEN : RED);
        } else {
            portfolioResult.setText("Resultado  —");
            portfolioResult.setTextColor(MUTED);
        }
    }

    private void savePosition(Token token, CardRefs refs) {
        prefs.edit()
                .putString("invested_" + token.symbol, Double.toString(refs.invested))
                .putString("average_" + token.symbol, Double.toString(refs.averageEntry))
                .apply();
    }

    private double readDouble(String key) {
        return parseDouble(prefs.getString(key, "0"));
    }

    private EditText decimalInput(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setTextSize(16);
        e.setSingleLine(true);
        return e;
    }

    private TextView text(String value, int sp, int style, int color) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, style);
        tv.setLineSpacing(0, 1.05f);
        return tv;
    }

    private Button smallButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setGravity(Gravity.CENTER);
        b.setBackground(makeRounded(color, 12));
        return b;
    }

    private android.graphics.drawable.GradientDrawable makeRounded(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private android.graphics.drawable.GradientDrawable makeRoundedStroke(int color, int radiusDp, int strokeColor) {
        android.graphics.drawable.GradientDrawable d = makeRounded(color, radiusDp);
        d.setStroke(dp(1), strokeColor);
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

    private double parseUserNumber(String s) {
        if (s == null) return 0;
        String clean = s.trim().replace(" ", "");
        if (clean.contains(",") && clean.contains(".")) {
            if (clean.lastIndexOf(',') > clean.lastIndexOf('.')) {
                clean = clean.replace(".", "").replace(',', '.');
            } else {
                clean = clean.replace(",", "");
            }
        } else {
            clean = clean.replace(',', '.');
        }
        return parseDouble(clean);
    }

    private String rawNumber(double value) {
        DecimalFormat f = new DecimalFormat("0.########", DecimalFormatSymbols.getInstance(Locale.US));
        return f.format(value);
    }

    private String fmtPrice(double value) {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.US);
        if (value >= 1) return new DecimalFormat("0.0000", s).format(value);
        if (value >= 0.01) return new DecimalFormat("0.000000", s).format(value);
        return new DecimalFormat("0.00000000", s).format(value);
    }

    private String signed(double value) {
        DecimalFormat f = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(new Locale("pt", "BR")));
        return (value > 0 ? "+" : "") + f.format(value);
    }

    private String money(double value) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(new Locale("pt", "BR")));
        return "US$ " + f.format(value);
    }

    private String signedMoney(double value) {
        return (value > 0 ? "+" : value < 0 ? "-" : "") + money(Math.abs(value));
    }

    private String compactMoney(double value) {
        DecimalFormat f2 = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US));
        DecimalFormat f1 = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));
        if (value >= 1_000_000_000) return "US$ " + f2.format(value / 1_000_000_000) + "B";
        if (value >= 1_000_000) return "US$ " + f2.format(value / 1_000_000) + "M";
        if (value >= 1_000) return "US$ " + f1.format(value / 1_000) + "K";
        return "US$ " + f2.format(value);
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
        final TextView investedText;
        final TextView currentText;
        final TextView averageText;
        final TextView pnlText;
        double lastPrice = 0;
        double invested = 0;
        double averageEntry = 0;
        double currentValue = 0;

        CardRefs(TextView price, TextView change, TextView market,
                 TextView investedText, TextView currentText,
                 TextView averageText, TextView pnlText) {
            this.price = price;
            this.change = change;
            this.market = market;
            this.investedText = investedText;
            this.currentText = currentText;
            this.averageText = averageText;
            this.pnlText = pnlText;
        }
    }

    private static class PriceResult {
        final double price;
        final double h1;
        final double h24;
        final double liquidity;
        final double volume24;
        final String error;

        PriceResult(double price, double h1, double h24,
                    double liquidity, double volume24, String error) {
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
