# Pster Voice

Voice dictation for Android through a floating bubble. Works in any app: hold the bubble, speak, release to transcribe, and the text is automatically pasted into the focused field.

Transcription runs on [ElevenLabs Scribe v2](https://elevenlabs.io/speech-to-text) (`model_id=scribe_v2`), with an optional cleanup pass by [Gemini](https://ai.google.dev/) (punctuation, filler/stutter removal, and voice commands like "quebra linha" / "vírgula" / "ponto").

**[Leia em português ↓](#leia-em-português)**

## How it works

1. An `AccessibilityService` (`BubbleAccessibilityService`) draws a floating bubble over any app (a `TYPE_ACCESSIBILITY_OVERLAY` overlay).
2. **Hold** the bubble (≥450ms) to start recording. The bubble expands and shows a live waveform.
3. Release over the confirm zone to stop and transcribe, or over the cancel zone to discard.
4. The audio is captured as WAV and sent to the ElevenLabs API (`VoiceCaptureService`, a foreground service with `foregroundServiceType="microphone|connectedDevice"`).
5. If configured, the Gemini key runs a quick cleanup pass on the raw text before delivering it.
6. The final text is copied to the clipboard and pasted into the focused text field via `AccessibilityNodeInfo` (`ACTION_PASTE`, with a fallback to `ACTION_SET_TEXT`).
7. The bubble remembers the connected Bluetooth audio device (`setPreferredDevice`) and shows a "connecting" state while it negotiates the microphone input.
8. A single tap (no hold) on the idle bubble also starts recording; the bubble hides when the keyboard isn't visible and recording is stopped, and reappears with the keyboard.

## Features

- **Draggable floating bubble**, with its position saved and snapping to the nearest screen edge.
- **Personal dictionary** (`DictionaryActivity`): register vocabulary terms (keyterms sent to the API on every dictation, improving recognition of names/jargon) or "wrong word → correction" substitution pairs.
- **Diagnostics** (`DiagnosticsActivity`): tests permissions, the accessibility service, battery optimization, and validates the ElevenLabs and Gemini keys with a real test call (including an optional real 2s audio capture, since it consumes credits).
- **Local error log** (`LogActivity` + `FileLogger`): records the exact cause of every transcription failure, no USB cable/logcat needed to debug in the field.
- **Home screen with overall status** of the app (permissions, service, battery, API keys).

## Requirements

- Android 8.0 (API 26) or higher.
- An [ElevenLabs](https://elevenlabs.io) API key with write permission on "Speech to Text".
- (Optional, but recommended) A [Google AI Studio](https://aistudio.google.com/apikey) API key for the Gemini text cleanup step. Without it, the app uses the raw transcription text.
- Android Studio (Giraffe or newer) or the Gradle wrapper included in the repository, with JDK 17.

## Installation and build

1. Clone the repository:

   ```bash
   git clone https://github.com/J-Pster/pster-voice.git
   cd pster-voice
   ```

2. Copy the environment variables example file and fill in your keys. This file is **not** versioned, and the keys get embedded into the APK's `BuildConfig` at compile time (they never live in plaintext in any resource file).

   ```bash
   cp .env.example .env
   ```

   ```env
   ELEVENLABS_API_KEY=your_api_key_here
   GEMINI_API_KEY=your_api_key_here
   ```

3. Build and install on a connected device (or emulator):

   ```bash
   ./gradlew installDebug
   ```

   On Windows, use `gradlew.bat installDebug`. Or open the project in Android Studio and run it normally (`Run ▶`) after creating the `.env`.

   If you change the keys after already building once, run `./gradlew clean` before rebuilding, since `BuildConfig` is generated from `.env` at build time.

## On-device setup

After installing, open the app and follow the home screen, which shows the status of each required item:

1. **Permissions**: microphone, Bluetooth (Android 12+) and notifications (Android 13+). Tap "Grant".
2. **Accessibility service**: required to draw the bubble and paste text automatically. Tap "Open" and enable "Pster Voice" in Android's accessibility settings.
3. **Battery optimization**: recommended to exempt the app, otherwise Android may cut audio capture off the charger.
4. **API keys**: use the "Test" button on each row to validate the key with a real call to the corresponding API.

## Usage

- Hold the bubble to record, release over the confirm icon to transcribe and paste, or over the cancel icon to discard.
- A simple tap on the idle bubble also starts recording.
- Drag the bubble to reposition it; it snaps to the nearest edge on release.
- Use the **Personal dictionary** to teach proper names, technical jargon, or recurring dictation corrections.
- If something fails, check the **Error log** (shows the exact cause: network, invalid key, empty audio, etc.) or run **Diagnostics**.

## Project structure

```
app/src/main/java/com/joaopster/pstervoice/
├── MainActivity.kt              # home screen with system status
├── BubbleAccessibilityService.kt # floating bubble, gestures, text pasting
├── VoiceCaptureService.kt       # foreground service: audio recording, volume level
├── ElevenLabsClient.kt          # HTTP call to the speech-to-text API
├── GeminiCleanupClient.kt       # HTTP call to the Gemini API for text cleanup
├── KeytermSanitizer.kt          # normalizes dictionary terms to the API's rules
├── DictionaryStore.kt           # personal dictionary persistence
├── DictionaryActivity.kt        # personal dictionary UI
├── DiagnosticsRunner.kt         # permission, key, and real capture tests
├── DiagnosticsActivity.kt       # diagnostics UI
├── FileLogger.kt                # local transcription error log
├── LogActivity.kt               # error log UI
└── WavUtils.kt                  # WAV encoding of the captured audio
```

## Privacy

Recorded audio is sent directly to the ElevenLabs API (and, optionally, the transcribed text to the Gemini API) for processing. No audio or text is stored on any servers owned by this project; the app has no backend. API keys live only in your local `.env` and embedded in your own compiled APK.

## License

[PolyForm Noncommercial 1.0.0](LICENSE) — free for noncommercial use, with attribution to the author.

---

## Leia em português

Ditado por voz para Android via uma bolha flutuante. Funciona em qualquer app: toque e segure a bolha, fale, solte para transcrever e o texto é colado automaticamente no campo focado.

Transcrição feita pela [ElevenLabs Scribe v2](https://elevenlabs.io/speech-to-text) (`model_id=scribe_v2`), com um passo opcional de limpeza do texto pelo [Gemini](https://ai.google.dev/) (pontuação, remoção de gagueira/repetição e comandos de voz como "quebra linha", "vírgula", "ponto").

### Como funciona

1. Um `AccessibilityService` (`BubbleAccessibilityService`) desenha uma bolha flutuante sobre qualquer app (overlay do tipo `TYPE_ACCESSIBILITY_OVERLAY`).
2. **Toque e segure** a bolha (≥450ms) para começar a gravar. A bolha expande e mostra a forma de onda em tempo real.
3. Solte sobre a área de confirmação para parar e transcrever, ou sobre a área de cancelar para descartar.
4. O áudio é gravado como WAV e enviado para a API da ElevenLabs (`VoiceCaptureService`, um foreground service com `foregroundServiceType="microphone|connectedDevice"`).
5. Se configurada, a chave do Gemini roda uma limpeza rápida em cima do texto bruto antes de entregá-lo.
6. O texto final é copiado para a área de transferência e colado no campo de texto focado via `AccessibilityNodeInfo` (`ACTION_PASTE`, com fallback para `ACTION_SET_TEXT`).
7. A bolha lembra o dispositivo de áudio Bluetooth conectado (`setPreferredDevice`) e mostra um estado "conectando" enquanto negocia o input de microfone.
8. Um clique simples (sem segurar) na bolha em estado ocioso também inicia a gravação; a bolha some quando não há teclado visível e a gravação está parada, e reaparece com o teclado.

### Funcionalidades

- **Bolha flutuante arrastável**, com posição salva e snap para a borda mais próxima da tela.
- **Dicionário pessoal** (`DictionaryActivity`): cadastre termos de vocabulário (keyterms enviados à API a cada ditado, melhora reconhecimento de nomes/jargão) ou pares de substituição "palavra errada → correção".
- **Diagnóstico** (`DiagnosticsActivity`): testa permissões, serviço de acessibilidade, otimização de bateria, e valida as chaves da ElevenLabs e do Gemini com uma chamada real de teste (incluindo captura real de 2s de áudio, opcional, pois consome créditos).
- **Log de erros local** (`LogActivity` + `FileLogger`): grava a causa exata de cada falha de transcrição, sem precisar de cabo USB/logcat para depurar em campo.
- **Tela inicial com status geral** do app (permissões, serviço, bateria, chaves de API).

### Requisitos

- Android 8.0 (API 26) ou superior.
- Uma chave de API da [ElevenLabs](https://elevenlabs.io) com permissão de escrita em "Speech to Text".
- (Opcional, mas recomendado) Uma chave de API do [Google AI Studio](https://aistudio.google.com/apikey) para o passo de limpeza do texto via Gemini. Sem ela, o app usa o texto bruto da transcrição.
- Android Studio (Giraffe ou mais recente) ou o Gradle wrapper incluso no repositório, com JDK 17.

### Instalação e build

1. Clone o repositório:

   ```bash
   git clone https://github.com/J-Pster/pster-voice.git
   cd pster-voice
   ```

2. Copie o arquivo de exemplo de variáveis de ambiente e preencha suas chaves. Esse arquivo **não** é versionado, e as chaves são embutidas no `BuildConfig` do APK em tempo de compilação (não ficam em texto puro em nenhum arquivo de recurso).

   ```bash
   cp .env.example .env
   ```

   ```env
   ELEVENLABS_API_KEY=sua_chave_aqui
   GEMINI_API_KEY=sua_chave_aqui
   ```

3. Compile e instale no dispositivo conectado (ou emulador):

   ```bash
   ./gradlew installDebug
   ```

   No Windows, use `gradlew.bat installDebug`. Ou abra o projeto no Android Studio e rode normalmente (`Run ▶`) depois de criar o `.env`.

   Se trocar as chaves depois de já ter compilado uma vez, rode `./gradlew clean` antes de recompilar, pois o `BuildConfig` é gerado a partir do `.env` no momento do build.

### Configuração no aparelho

Depois de instalar, abra o app e siga a tela inicial, que mostra o status de cada item necessário:

1. **Permissões**: microfone, Bluetooth (Android 12+) e notificações (Android 13+). Toque em "Conceder".
2. **Serviço de acessibilidade**: obrigatório para desenhar a bolha e colar o texto automaticamente. Toque em "Abrir" e ative "Pster Voice" nas configurações de acessibilidade do Android.
3. **Otimização de bateria**: recomenda-se isentar o app, senão o Android pode cortar a captura de áudio fora do carregador.
4. **Chaves de API**: use o botão "Testar" em cada linha para validar a chave com uma chamada real à API correspondente.

### Uso

- Segure a bolha para gravar, solte sobre o ícone de confirmar para transcrever e colar, ou sobre o de cancelar para descartar.
- Toque simples na bolha ociosa também inicia a gravação.
- Arraste a bolha para reposicioná-la; ela gruda na borda mais próxima ao soltar.
- Use o **Dicionário pessoal** para ensinar nomes próprios, jargão técnico ou correções recorrentes de ditado.
- Se algo falhar, confira o **Log de erros** (mostra a causa exata: rede, chave inválida, áudio vazio etc.) ou rode o **Diagnóstico**.

### Estrutura do projeto

```
app/src/main/java/com/joaopster/pstervoice/
├── MainActivity.kt              # tela inicial com status do sistema
├── BubbleAccessibilityService.kt # bolha flutuante, gestos, colagem do texto
├── VoiceCaptureService.kt       # foreground service: grava áudio, nível de volume
├── ElevenLabsClient.kt          # chamada HTTP à API de speech-to-text
├── GeminiCleanupClient.kt       # chamada HTTP à API do Gemini para limpeza do texto
├── KeytermSanitizer.kt          # normaliza termos do dicionário para as regras da API
├── DictionaryStore.kt           # persistência do dicionário pessoal
├── DictionaryActivity.kt        # UI do dicionário pessoal
├── DiagnosticsRunner.kt         # testes de permissões, chaves e captura real
├── DiagnosticsActivity.kt       # UI do diagnóstico
├── FileLogger.kt                # log local de erros de transcrição
├── LogActivity.kt               # UI do log de erros
└── WavUtils.kt                  # encoding do áudio capturado em WAV
```

### Privacidade

O áudio gravado é enviado diretamente para a API da ElevenLabs (e, opcionalmente, o texto transcrito para a API do Gemini) para processamento. Nenhum áudio ou texto é armazenado em servidores próprios do projeto; o app não tem backend. As chaves de API ficam apenas no seu `.env` local e embutidas no seu próprio APK compilado.

### Licença

[PolyForm Noncommercial 1.0.0](LICENSE) — uso livre para fins não comerciais, com atribuição ao autor.
