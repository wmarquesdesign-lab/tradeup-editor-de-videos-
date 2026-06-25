TradeUp Videos Android - Renderização Real FFmpeg

Versão: 1.0.8-ffmpeg-render

Implementado nesta entrega:
- FFmpegKit Full GPL integrado ao app Android.
- Exportação Android não copia mais apenas o primeiro vídeo.
- Renderização real em MP4 com:
  * concatenação dos vídeos da timeline;
  * cortes trimIn/trimOut;
  * velocidade do clipe;
  * música de fundo;
  * volume/fade in/fade out;
  * textos sobrepostos no vídeo;
  * filtros de brilho/contraste/saturação;
  * transições por xfade entre clipes;
  * escolha do nome e local pelo seletor do Android.

Observações importantes:
- A primeira compilação no GitHub Actions pode demorar porque baixa FFmpegKit.
- A dependência usada é com.arthenica:ffmpeg-kit-full-gpl:6.0.LTS, publicada no Maven Central.
- Exportação 4K pode ser pesada em celulares fracos.
- No PC, o app continua abrindo pelo index.html e exportando WebM pelo navegador; MP4 real no PC exige versão desktop com Electron/Tauri + FFmpeg.
