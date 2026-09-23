# Cluster Radio 📻

> **Rádio AM/FM de alta performance com estética de cockpit esportivo, projetado sob medida para centrais multimídia automotivas Android.**

Parte do ecossistema **Cluster** (ao lado de [Cluster Launcher](https://github.com/joaohouto/clusterlauncher) e [Cluster Player](https://github.com/joaohouto/clusterplayer)).

---

## 🏎️ Destaques e Ergonomia de Cockpit

* **Estética Automotiva Deep Metallic (`#0B0C0E`):** Acabamento acetinado escuro, botões chanfrados de alta precisão e iluminação de instrumentos configurável (*Needle Red*, *M-Sport Blue*, *Racing Yellow*, *Green Hell*, *Sunset Orange*, *Electric Cyan*, *Pure Silver*).
* **Mostrador Digital com Tipografia de Instrumentos:** Frequência em dígitos gigantes (68sp Bold), indicação de unidade no acento ativo e status de sintonia (*STEREO* / *RDS*).
* **Régua Analógico-Digital (60 FPS):** Dial horizontal com ponteiro iluminado estilo velocímetro e botões laterais táteis quadrados (64x64 dp) para ajuste fino de frequência (0.1 MHz no FM / 10 kHz no AM).
* **Grade de Estações Favoritas (P1..P6):** Acesso rápido de toque único para alternar entre emissoras memorizadas e toque longo para salvar a estação atual com feedback sonoro e tátil.
* **Barra de Controle Ergonômica (84dp):** Botões inferiores de toque ultra-acessível para segurança ao volante: Volume -, Busca Anterior (*Seek Down*), Play/Pause em destaque, Busca Próxima (*Seek Up*) e Volume +.
* **Notificação de Sistema MediaStyle:** Controle total através da barra de notificações e painel de mídia rápido do Android, com botões de estação anterior, play/pause e próxima estação.
* **Integração Veicular Completa (SWC & Audio Focus):** Resposta imediata a teclas de volante físicas e knobs rotativos do painel (`dispatchKeyEvent`), áudio ducking suave com instruções de GPS (Google Maps, Waze) e suporte a centrais chinesas (FYT/Syu, Microntek, Topway).
* **Inicialização Escura Instantânea (Zero Flash Branco):** Janela nativa com fundo escuro desde o milissegundo zero, eliminando qualquer clarão branco ao ligar a ignição do veículo.

---

## 📦 Download do APK

Baixe a versão otimizada mais recente na aba de [Releases](https://github.com/joaohouto/clusterradio/releases/latest).

---

## 🛠️ Tecnologias Utilizadas

* **UI:** Jetpack Compose com Material 3 e Custom Automotive Shaders/Gradients
* **Áudio & MediaSession:** AndroidX Media3 (1.3.1) & ExoPlayer
* **Persistência:** Jetpack DataStore Preferences
* **Linguagem & Tooling:** Kotlin 2.2 / Gradle 9 / Minificação R8 ProGuard
