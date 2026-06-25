# TradeUp Videos Studio - FINAL IA + PC

Versão revisada sobre a Sprint 8 com mudanças reais no código:

## O que foi adicionado
- Modo PC: o mesmo `index.html` abre no navegador e permite escolher vídeos, imagens e áudio.
- Exportação PC: gera um arquivo `.webm` renderizado pelo navegador com timeline, textos e filtros.
- IA local/offline: cria corte automático, storyboard, legendas e transições sem internet.
- Painel de efeitos: brilho, contraste, saturação, temperatura, blur, nitidez e presets.
- Autosave no navegador/celular.
- Formatos de exportação: vertical 9:16, horizontal 16:9 e quadrado 1:1.
- Projeto JSON com plano de renderização e plano da IA.

## Como testar no celular
Suba no GitHub e rode o Actions normalmente para gerar o APK.

## Como testar no PC
Abra este arquivo no navegador:
`app/src/main/assets/index.html`

No PC, use:
- Adicionar vídeos
- IA montar vídeo
- Play
- Exportar

O PC exporta em `.webm`, porque é o formato suportado pelo navegador sem servidor.
No Android, o app continua usando o seletor nativo para escolher onde salvar.
