# Token Monitor Jean — Beta 0.4.0

## Beta 0.4.0 — alertas de preço

- alertas individuais de compra e venda em US$;
- take profit e stop-loss em percentual sobre o preço unitário pago;
- notificação sonora e visual no Android;
- distância até o alvo mais próximo em cada token;
- intervalo de segurança de 30 minutos contra notificações repetidas;
- todos os recursos são somente avisos e não executam operações;
- nesta versão, o monitoramento funciona enquanto o app permanece aberto.

## Beta 0.3.9 — valor unitário da posição

- o botão Posição mostra o valor unitário pago por token em US$;
- a tela de posição possui um campo próprio para registrar o preço unitário exato em dólar;
- ao zerar a posição, o botão volta a exibir somente Posição.

## Beta 0.3.8 — relatório dentro do app

- histórico consultado e exibido diretamente na aba Relatórios;
- fallback automático entre duas rotas de consulta da Robinhood Chain;
- resumo com quantidade de entradas, saídas e total de taxas;
- botão para atualizar o relatório sem abrir o navegador;
- links externos removidos da tela de relatório.

## Beta 0.3.7 — atualização rápida

- cotações atualizadas a cada 2 segundos;
- proteção contra consultas sobrepostas para reduzir travamentos;
- botão para pausar e retomar a atualização;
- aviso quando as cotações ficam desatualizadas por 10 segundos;
- compra e venda reais continuam desativadas nesta etapa.

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


## Beta 0.3.6 — Histórico da carteira

- consulta somente leitura das 25 transações mais recentes;
- identifica entradas e saídas;
- mostra valor em ETH, taxa, data, status e hash;
- permite abrir a carteira e cada transação no explorador da Robinhood Chain;
- compras e vendas reais continuam desativadas.
