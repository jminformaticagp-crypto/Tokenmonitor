package com.jean.tokenmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
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
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final long REFRESH_MS = 5000L;
    private static final String DEX_API = "https://api.dexscreener.com/latest/dex/tokens/";
    private static final String FX_API = "https://economia.awesomeapi.com.br/json/last/USD-BRL";
    private static final String FX_FALLBACK_API = "https://api.binance.com/api/v3/ticker/price?symbol=USDTBRL";
    private static final long FX_REFRESH_MS = 60000L;
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
    private TextView walletStatusText;
    private TextView walletAddressText;
    private WalletManager walletManager;
    private String pendingBackupJson;
    private static final int REQ_CREATE_BACKUP = 701;
    private static final int REQ_OPEN_BACKUP = 702;
    private boolean refreshRunning = false;
    private boolean fxFetching = false;
    private int completed = 0;
    private double usdBrl = 0;
    private long lastFxUpdate = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        walletGateway = new DisabledWalletGateway();
        walletManager = new WalletManager(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        fetchUsdBrlAsync();
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

        root.addView(buildWalletPanel());

        TextView footer = text("Tokens a cada 5 s • carteira em R$ • backup local criptografado", 11,
                Typeface.NORMAL, MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(15), 0, 0);
        root.addView(footer);

        return scroll;
    }

    private View buildWalletPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(13), dp(14), dp(13));
        panel.setBackground(makeRoundedStroke(CARD, 16, BORDER));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("CARTEIRA", 12, Typeface.BOLD, MUTED);
        title.setLetterSpacing(0.08f);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        walletStatusText = text("NÃO CRIADA", 10, Typeface.BOLD, AMBER);
        header.addView(walletStatusText);
        panel.addView(header);

        walletAddressText = text("Crie uma carteira nova ou recupere uma carteira existente.", 13,
                Typeface.NORMAL, TEXT);
        walletAddressText.setPadding(0, dp(8), 0, dp(10));
        panel.addView(walletAddressText);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button create = smallButton("Criar carteira", Color.rgb(18, 137, 87));
        Button recover = smallButton("12 palavras", Color.rgb(54, 69, 88));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        p1.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        p2.setMargins(dp(5), 0, 0, 0);
        row1.addView(create, p1);
        row1.addView(recover, p2);
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);
        Button backup = smallButton("Backup arquivo", Color.rgb(64, 80, 103));
        Button restore = smallButton("Restaurar backup", Color.rgb(64, 80, 103));
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        p3.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams p4 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        p4.setMargins(dp(5), 0, 0, 0);
        row2.addView(backup, p3);
        row2.addView(restore, p4);
        panel.addView(row2);

        TextView note = text(
                "A frase e a chave ficam criptografadas no aparelho. O backup em arquivo usa senha própria. " +
                        "Compras e vendas reais continuam desativadas até concluirmos os testes da carteira.",
                11, Typeface.NORMAL, MUTED);
        note.setPadding(0, dp(10), 0, 0);
        panel.addView(note);

        create.setOnClickListener(v -> confirmCreateWallet());
        recover.setOnClickListener(v -> showRecoverMnemonicDialog());
        backup.setOnClickListener(v -> startBackupFlow());
        restore.setOnClickListener(v -> openBackupFile());

        updateWalletPanel();
        return panel;
    }

    private void updateWalletPanel() {
        if (walletStatusText == null || walletAddressText == null || walletManager == null) return;
        if (walletManager.hasWallet()) {
            try {
                String address = walletManager.getAddress();
                walletStatusText.setText("ATIVA");
                walletStatusText.setTextColor(GREEN);
                walletAddressText.setText(address + "\nRobinhood Chain • EVM");
            } catch (Exception e) {
                walletStatusText.setText("ERRO");
                walletStatusText.setTextColor(RED);
                walletAddressText.setText("Não foi possível abrir a carteira local.");
            }
        } else {
            walletStatusText.setText("NÃO CRIADA");
            walletStatusText.setTextColor(AMBER);
            walletAddressText.setText("Crie uma carteira nova ou recupere uma carteira existente.");
        }
    }

    private void confirmCreateWallet() {
        if (walletManager.hasWallet()) {
            new AlertDialog.Builder(this)
                    .setTitle("Carteira já existe")
                    .setMessage("Este aparelho já possui uma carteira. Criar outra substituiria o acesso local atual. " +
                            "Faça um backup antes de qualquer substituição.")
                    .setPositiveButton("Fechar", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Criar nova carteira")
                .setMessage("A carteira será criada somente neste aparelho. Antes de receber valores, anote as 12 palavras " +
                        "e faça também um backup criptografado. Não envie a frase para ninguém.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Criar", (d, w) -> createWalletNow())
                .show();
    }

    private void createWalletNow() {
        try {
            WalletManager.CreatedWallet wallet = walletManager.createNewWallet();
            updateWalletPanel();
            showNewMnemonic(wallet.mnemonic, wallet.address);
        } catch (Throwable e) {
            new AlertDialog.Builder(this)
                    .setTitle("Não foi possível criar")
                    .setMessage("Erro completo:\n" + e.toString() + "\n\nCausa:\n" + String.valueOf(e.getCause()))
                    .setPositiveButton("Fechar", null)
                    .show();
        }
    }

    private void showNewMnemonic(String mnemonic, String address) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        TextView warning = text(
                "ANOTE ESTAS 12 PALAVRAS NA ORDEM EXATA.\n\nElas recuperam a carteira em outro celular. " +
                        "Quem tiver essas palavras poderá controlar os fundos.",
                13, Typeface.BOLD, Color.rgb(125, 40, 40));
        box.addView(warning);

        TextView words = text(mnemonic, 17, Typeface.BOLD, Color.DKGRAY);
        words.setPadding(0, dp(14), 0, dp(14));
        words.setTextIsSelectable(false);
        box.addView(words);

        TextView addr = text("Endereço:\n" + address, 12, Typeface.NORMAL, Color.DKGRAY);
        box.addView(addr);

        new AlertDialog.Builder(this)
                .setTitle("Frase de recuperação")
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("Anotei as 12 palavras", (d, w) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Faça o segundo backup")
                            .setMessage("Agora use “Backup arquivo” e crie um arquivo criptografado por senha. " +
                                    "Teste a recuperação antes de colocar valores importantes.")
                            .setPositiveButton("Entendi", null)
                            .show();
                })
                .show();
    }

    private void showRecoverMnemonicDialog() {
        if (walletManager.hasWallet()) {
            new AlertDialog.Builder(this)
                    .setTitle("Carteira já existe")
                    .setMessage("Há uma carteira salva neste aparelho. Para evitar substituição acidental, esta Beta não sobrescreve uma carteira existente.")
                    .setPositiveButton("Fechar", null)
                    .show();
            return;
        }

        EditText mnemonicInput = new EditText(this);
        mnemonicInput.setHint("Digite as 12 palavras separadas por espaço");
        mnemonicInput.setMinLines(4);
        mnemonicInput.setGravity(Gravity.TOP);
        mnemonicInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Recuperar com 12 palavras")
                .setMessage("Digite a frase somente dentro deste aplicativo. Nunca envie a frase por mensagem ou para suporte.")
                .setView(mnemonicInput)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Recuperar", (d, w) -> {
                    try {
                        String address = walletManager.importMnemonic(mnemonicInput.getText().toString());
                        updateWalletPanel();
                        new AlertDialog.Builder(this)
                                .setTitle("Carteira recuperada")
                                .setMessage("Endereço recuperado:\n" + address)
                                .setPositiveButton("OK", null)
                                .show();
                    } catch (Throwable e) {
                        new AlertDialog.Builder(this)
                                .setTitle("Frase inválida")
                                .setMessage("Não foi possível validar as 12 palavras. Confira a ordem e a grafia.")
                                .setPositiveButton("Fechar", null)
                                .show();
                    }
                })
                .show();
    }

    private void startBackupFlow() {
        if (!walletManager.hasWallet()) {
            Toast.makeText(this, "Crie ou recupere uma carteira primeiro.", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);

        EditText p1 = new EditText(this);
        p1.setHint("Senha do arquivo (mínimo 8 caracteres)");
        p1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(p1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        EditText p2 = new EditText(this);
        p2.setHint("Repita a senha");
        p2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(p2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        new AlertDialog.Builder(this)
                .setTitle("Criar backup criptografado")
                .setMessage("Guarde a senha separada do arquivo. Sem a senha, o arquivo não poderá ser restaurado.")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar", (d, w) -> {
                    String a = p1.getText().toString();
                    String b = p2.getText().toString();
                    if (a.length() < 8 || !a.equals(b)) {
                        Toast.makeText(this, "As senhas devem ser iguais e ter pelo menos 8 caracteres.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        pendingBackupJson = walletManager.createEncryptedBackup(a.toCharArray());
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        intent.putExtra(Intent.EXTRA_TITLE, "token-monitor-wallet-backup.tmj.json");
                        startActivityForResult(intent, REQ_CREATE_BACKUP);
                    } catch (Throwable e) {
                        Toast.makeText(this, "Falha ao preparar o backup.", Toast.LENGTH_LONG).show();
                    } finally {
                        java.util.Arrays.fill(a.toCharArray(), '\0');
                        java.util.Arrays.fill(b.toCharArray(), '\0');
                    }
                })
                .show();
    }

    private void openBackupFile() {
        if (walletManager.hasWallet()) {
            new AlertDialog.Builder(this)
                    .setTitle("Carteira já existe")
                    .setMessage("Para evitar substituir a carteira deste aparelho por engano, a restauração de arquivo só é permitida quando não há carteira local.")
                    .setPositiveButton("Fechar", null)
                    .show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_OPEN_BACKUP);
    }

    private void askBackupPasswordAndRestore(String backupJson) {
        EditText password = new EditText(this);
        password.setHint("Senha do backup");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Restaurar backup")
                .setMessage("Informe a senha usada quando o arquivo foi criado.")
                .setView(password)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Restaurar", (d, w) -> {
                    char[] pass = password.getText().toString().toCharArray();
                    try {
                        String address = walletManager.restoreEncryptedBackup(backupJson, pass);
                        updateWalletPanel();
                        new AlertDialog.Builder(this)
                                .setTitle("Backup restaurado")
                                .setMessage("Carteira recuperada:\n" + address)
                                .setPositiveButton("OK", null)
                                .show();
                    } catch (Throwable e) {
                        new AlertDialog.Builder(this)
                                .setTitle("Não foi possível restaurar")
                                .setMessage("Senha incorreta, arquivo inválido ou backup danificado.")
                                .setPositiveButton("Fechar", null)
                                .show();
                    } finally {
                        java.util.Arrays.fill(pass, '\0');
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_CREATE_BACKUP) {
            if (pendingBackupJson == null) return;
            try (OutputStream os = getContentResolver().openOutputStream(uri, "w")) {
                if (os == null) throw new IllegalStateException("Sem saída");
                os.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
                os.flush();
                pendingBackupJson = null;
                new AlertDialog.Builder(this)
                        .setTitle("Backup salvo")
                        .setMessage("O arquivo criptografado foi salvo. Guarde-o em um local seguro e separado da senha.")
                        .setPositiveButton("OK", null)
                        .show();
            } catch (Throwable e) {
                Toast.makeText(this, "Não foi possível salvar o arquivo.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_OPEN_BACKUP) {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) throw new IllegalStateException("Sem entrada");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int total = 0;
                int n;
                while ((n = is.read(buf)) != -1) {
                    total += n;
                    if (total > 1024 * 1024) throw new IllegalArgumentException("Arquivo muito grande");
                    baos.write(buf, 0, n);
                }
                askBackupPasswordAndRestore(baos.toString(StandardCharsets.UTF_8.name()));
            } catch (Throwable e) {
                Toast.makeText(this, "Não foi possível ler o backup.", Toast.LENGTH_LONG).show();
            }
        }
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

        portfolioValue = text("R$ 0,00", 30, Typeface.BOLD, TEXT);
        portfolioValue.setPadding(0, dp(8), 0, dp(7));
        panel.addView(portfolioValue);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        portfolioInvested = text("Investido  R$ 0,00", 12, Typeface.NORMAL, MUTED);
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
        TextView invested = text("Investido  R$ 0,00", 12, Typeface.NORMAL, MUTED);
        TextView current = text("Valor atual  R$ 0,00", 12, Typeface.NORMAL, MUTED);
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
        refs.investedBrl = readDouble("invested_brl_" + token.symbol);
        refs.quantity = readDouble("quantity_" + token.symbol);
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

        EditText investedInput = decimalInput("Valor total investido em R$");
        if (refs.investedBrl > 0) investedInput.setText(rawNumber(refs.investedBrl));
        box.addView(investedInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        EditText quantityInput = decimalInput("Quantidade de tokens que você possui");
        if (refs.quantity > 0) quantityInput.setText(rawNumber(refs.quantity));
        box.addView(quantityInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        new AlertDialog.Builder(this)
                .setTitle("Posição • " + token.symbol)
                .setView(box)
                .setNeutralButton("Zerar", (d, w) -> {
                    refs.investedBrl = 0;
                    refs.quantity = 0;
                    savePosition(token, refs);
                    updatePosition(token, refs);
                    updatePortfolio();
                })
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (d, w) -> {
                    double investedValue = parseUserNumber(investedInput.getText().toString());
                    double quantityValue = parseUserNumber(quantityInput.getText().toString());
                    if (investedValue <= 0 || quantityValue <= 0) {
                        Toast.makeText(this, "Informe valores maiores que zero.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    refs.investedBrl = investedValue;
                    refs.quantity = quantityValue;
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
                ? "Valor da compra em R$" : "Quantidade de tokens");
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
                        if (usdBrl <= 0 || System.currentTimeMillis() - lastFxUpdate >= FX_REFRESH_MS) {
                            fetchUsdBrlAsync();
                        }
                        updatePortfolio();
                    }
                });
            });
        }
    }

    private void fetchUsdBrlAsync() {
        if (fxFetching) return;
        fxFetching = true;
        pool.submit(() -> {
            double rate = fetchUsdBrl();
            runOnUiThread(() -> {
                fxFetching = false;
                if (rate > 0) {
                    usdBrl = rate;
                    lastFxUpdate = System.currentTimeMillis();
                    updateAllPositions();
                    updatePortfolio();
                }
            });
        });
    }

    private double fetchUsdBrl() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(FX_API);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "TokenMonitorJean/0.2-beta");

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                StringBuilder body = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) body.append(line);
                }
                JSONObject root = new JSONObject(body.toString());
                JSONObject quote = root.optJSONObject("USDBRL");
                if (quote != null) {
                    double bid = parseDouble(quote.optString("bid", "0"));
                    if (bid > 0) return bid;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }

        conn = null;
        try {
            URL url = new URL(FX_FALLBACK_API);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                StringBuilder body = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) body.append(line);
                }
                JSONObject root = new JSONObject(body.toString());
                return parseDouble(root.optString("price", "0"));
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return 0;
    }

    private void updateAllPositions() {
        for (Token token : tokens) {
            CardRefs refs = cards.get(token.symbol);
            if (refs != null) {
                updatePosition(token, refs);
                updateMarket(refs);
            }
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
        } catch (Throwable e) {
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
        refs.liquidityUsd = result.liquidity;
        refs.volume24Usd = result.volume24;
        updateMarket(refs);
        updatePosition(token, refs);
    }

    private void updateMarket(CardRefs refs) {
        if (usdBrl > 0) {
            refs.market.setText("Liquidez " + compactBrl(refs.liquidityUsd * usdBrl)
                    + "  •  Volume 24h " + compactBrl(refs.volume24Usd * usdBrl));
        } else {
            refs.market.setText("Liquidez R$ —  •  Volume 24h R$ —");
        }
    }

    private void updatePosition(Token token, CardRefs refs) {
        refs.investedText.setText("Investido  " + moneyBrl(refs.investedBrl));

        if (refs.quantity > 0 && refs.investedBrl > 0) {
            double averageBrl = refs.investedBrl / refs.quantity;
            refs.averageText.setText("Preço médio  " + moneyBrl(averageBrl));
        } else {
            refs.averageText.setText("Preço médio  —");
        }

        if (refs.investedBrl <= 0 || refs.quantity <= 0 || refs.lastPrice <= 0 || usdBrl <= 0) {
            refs.currentValueBrl = 0;
            refs.currentText.setText("Valor atual  R$ 0,00");
            refs.pnlText.setText("Resultado  —");
            refs.pnlText.setTextColor(MUTED);
            return;
        }

        refs.currentValueBrl = refs.quantity * refs.lastPrice * usdBrl;
        double pnlBrl = refs.currentValueBrl - refs.investedBrl;
        double pnlPercent = (pnlBrl / refs.investedBrl) * 100.0;

        refs.currentText.setText("Valor atual  " + moneyBrl(refs.currentValueBrl));
        refs.pnlText.setText("Resultado  " + signedMoneyBrl(pnlBrl) + "  (" + signed(pnlPercent) + "%)");
        refs.pnlText.setTextColor(pnlBrl >= 0 ? GREEN : RED);
    }

    private void updatePortfolio() {
        double investedTotal = 0;
        double currentTotal = 0;
        for (CardRefs refs : cards.values()) {
            investedTotal += refs.investedBrl;
            currentTotal += refs.currentValueBrl;
        }

        double pnl = currentTotal - investedTotal;
        double pnlPct = investedTotal > 0 ? (pnl / investedTotal) * 100.0 : 0;

        portfolioValue.setText(moneyBrl(currentTotal));
        portfolioInvested.setText("Investido  " + moneyBrl(investedTotal));

        if (investedTotal > 0 && usdBrl > 0) {
            portfolioResult.setText("Resultado  " + signedMoneyBrl(pnl) + " (" + signed(pnlPct) + "%)");
            portfolioResult.setTextColor(pnl >= 0 ? GREEN : RED);
        } else {
            portfolioResult.setText("Resultado  —");
            portfolioResult.setTextColor(MUTED);
        }
    }

    private void savePosition(Token token, CardRefs refs) {
        prefs.edit()
                .putString("invested_brl_" + token.symbol, Double.toString(refs.investedBrl))
                .putString("quantity_" + token.symbol, Double.toString(refs.quantity))
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
        catch (Throwable e) { return 0; }
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

    private String moneyBrl(double value) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(new Locale("pt", "BR")));
        return "R$ " + f.format(value);
    }

    private String signedMoneyBrl(double value) {
        return (value > 0 ? "+" : value < 0 ? "-" : "") + moneyBrl(Math.abs(value));
    }

    private String compactBrl(double value) {
        DecimalFormat f2 = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(new Locale("pt", "BR")));
        DecimalFormat f1 = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(new Locale("pt", "BR")));
        if (value >= 1_000_000_000) return "R$ " + f2.format(value / 1_000_000_000) + "B";
        if (value >= 1_000_000) return "R$ " + f2.format(value / 1_000_000) + "M";
        if (value >= 1_000) return "R$ " + f1.format(value / 1_000) + "K";
        return "R$ " + f2.format(value);
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
        double investedBrl = 0;
        double quantity = 0;
        double currentValueBrl = 0;
        double liquidityUsd = 0;
        double volume24Usd = 0;

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
