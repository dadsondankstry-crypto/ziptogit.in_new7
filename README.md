# Explorador ZIP — Android nativo (Java)

## O que faz
- Selecionar uma pasta autorizada pelo Android e navegar por subpastas.
- Selecionar qualquer arquivo ZIP diretamente pelo seletor do Android (inclusive em Downloads, quando disponibilizado).
- Abrir ZIP e navegar em subpastas **sem extrair** nada.
- Mostrar/ocultar nomes que começam por ponto (ex.: `.github`, `.gitignore`).
- Extrair ZIP, se solicitado, para **uma nova subpasta** de uma pasta escolhida pelo usuário.
- Funciona offline após instalado. Não declara permissão de Internet ou acesso irrestrito ao armazenamento.

## Limitações deliberadas
- Apenas ZIP (não RAR/7z); não lê ZIP com senha.
- Dentro do ZIP, visualiza nomes, pastas e tamanhos: não é editor nem pré-visualizador de fotos/PDFs.
- Na visualização de pastas reais, mostra nomes começando por ponto se o provedor de documentos os listar. Alguns provedores filtram esses nomes e o app não consegue contornar essa decisão.
- Em Android 11+, o seletor de pasta não permite conceder a raiz do armazenamento ou a pasta Downloads como **árvore**. Use o botão "Abrir ZIP" para pegar um ZIP em Downloads.
- A extração pode alterar datas de criação e modificação; **navegar sem extrair não altera o ZIP**.
- Limite de 12 mil entradas; extração limitada a 500 MB totais / 150 MB por arquivo. Arquivos muito grandes podem demorar. Em caso de falha de extração, uma subpasta parcial poderá permanecer.

## Como gerar o APK pelo celular
1. Crie um repositório novo no GitHub.
2. Envie o ZIP diretamente pelo ZiptoGit, sem descompactar e compactar de novo.
3. Confirme se `.github/workflows/main.yml` foi enviado. Caso não, crie esse caminho no GitHub e cole o arquivo `YAML_PARA_COPIAR.yml`.
4. Abra `Actions > Gerar APK - Explorador ZIP`. O envio à branch `main` dispara a compilação; alternativamente use `Run workflow`.
5. Em execução verde, baixe o artefato `ExploradorZIP-APK` na área `Artifacts`. Extraia e instale `app-debug.apk`.
6. Teste com uma cópia do ZIP anterior que contém `.github`.

## Teste recomendado
- Toque `Abrir ZIP`, escolha um ZIP do armazenamento.
- Entre nas pastas internas sem extrair.
- Marque "Mostrar arquivos ocultos". Veja `.github` e `.gitignore`, caso existam dentro do ZIP.
- Volte e escolha uma pasta em `Pasta` para explorar arquivos normais.
- Opcionalmente extraia o ZIP em uma pasta vazia de testes. Não use ZIPs desconhecidos.

## Projeto
Android nativo Java, API 35, Gradle 8.9, Java 17. Identificador: `com.exemplo.exploradorzip`.
O código foi produzido para compilação via GitHub Actions; **ainda não foi compilado/testado em dispositivo neste ambiente**.
