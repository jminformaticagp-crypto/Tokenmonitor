# Token Monitor Jean — Beta 0.2

Aplicativo Android pessoal para acompanhar AI, PONS, CASHCAT e MEME na Robinhood Chain.

## Beta 0.2

- visual escuro e mais profissional;
- botões menores e interface mais compacta;
- preço dos tokens em US$;
- atualização automática a cada 5 segundos, sem botão Atualizar;
- painel de valor total da carteira;
- valor investido, valor atual, preço médio e lucro/prejuízo por token;
- posição manual salva no aparelho enquanto a carteira ainda não está conectada;
- camada `WalletGateway` pronta para receber a futura carteira sem redesenhar a tela de compra/venda;
- compras e vendas reais permanecem desativadas nesta versão.

## Como registrar a posição nesta versão

Em cada token, toque em **Posição** e informe:
1. valor total investido em US$;
2. preço médio de entrada em US$.

O app calcula automaticamente o valor atual e o PNL usando a cotação atual.

## Segurança

A Beta 0.2 não armazena seed phrase, chave privada, senha da Binance nem API secret. A carteira será adicionada em uma etapa posterior.

## APK

O GitHub Actions gera o artefato **TokenMonitorJean-Beta-0.2**. Dentro do ZIP está o `app-debug.apk`.
