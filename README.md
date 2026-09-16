# Token Monitor Jean — Android

Versão 1 de teste para uso pessoal.

## O que já faz
- Monitora AI, PONS, CASHCAT e MEME simultaneamente.
- Usa os contratos exatos na Robinhood Chain.
- Atualiza automaticamente a cada 5 segundos.
- Mostra preço USD, variação de 1h/24h, liquidez e volume 24h.
- Seleciona o par Robinhood com maior liquidez retornado pelo DexScreener.
- Botões Comprar/Vender pedem valor e confirmação.
- A negociação real fica deliberadamente desativada nesta versão.
- Botão "Abrir Binance" abre o app oficial instalado (pacote com.binance.dev).
- Não armazena senha, API key, seed phrase ou chave privada.

## Contratos cadastrados
- AI: 0x2e8c31162b855a2ffa90f6f8634643ad6f111e18
- PONS: 0x39dbed3a2bd333467115de45665cc57f813c4571
- CASHCAT: 0x020bfc650a365f8bb26819deaabf3e21291018b4
- MEME: 0x385f4f8ae47651ce5f58f5265395a669f8281e18

## Gerar APK gratuitamente no GitHub
1. Crie um repositório GitHub e envie todos os arquivos deste projeto.
2. Abra a aba Actions.
3. Execute "Build Android APK" (ou faça um push para main/master).
4. Ao final, baixe o artefato `TokenMonitorJean-APK`.
5. Dentro do ZIP estará `app-debug.apk`, pronto para instalação de teste.

## Próxima etapa: ordens reais
Para comprar/vender dentro do app sem expor a chave privada, a integração deve usar uma sessão de carteira (ex.: WalletConnect/Binance Wallet) ou uma API oficial compatível com Robinhood Chain. A assinatura da transação deve continuar sob controle da carteira oficial.
