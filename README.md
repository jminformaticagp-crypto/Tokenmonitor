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


## Ajuste 0.2.1

- somente o preço unitário atual do token permanece em US$;
- valor investido, valor atual, resultado e carteira total são exibidos em R$;
- liquidez e volume também são mostrados em R$;
- a posição manual agora usa **valor investido em R$ + quantidade de tokens**;
- o app consulta USD/BRL e recalcula a carteira automaticamente.


## Carteira e backup — 0.2.2

- criação local de uma carteira EVM com 12 palavras BIP-39;
- derivação padrão Ethereum `m/44'/60'/0'/0/0`;
- recuperação por 12 palavras;
- frase armazenada localmente com AES-GCM e chave protegida pelo Android Keystore;
- backup portátil criptografado por senha com PBKDF2-HMAC-SHA256 + AES-256-GCM;
- restauração do arquivo de backup em outro aparelho;
- o backup usa o seletor de arquivos do Android, então o app não precisa de permissão ampla de armazenamento;
- compra/venda real permanece bloqueada nesta etapa até testarmos criação, backup e restauração com segurança.

### Regra de segurança

Antes de enviar valores importantes para a carteira, faça os dois backups e teste a restauração em outro aparelho ou instalação limpa.
Nunca coloque frase-semente, chave privada ou senha de backup no GitHub.
