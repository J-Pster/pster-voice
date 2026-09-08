# Pster Voice

Ditado por voz para Android via uma bolha flutuante. Funciona em qualquer app: toque e segure a bolha, fale, solte para transcrever e o texto é colado automaticamente no campo focado.

Transcrição feita pela [ElevenLabs Scribe v2](https://elevenlabs.io/speech-to-text) (`model_id=scribe_v2`), com um passo opcional de limpeza do texto pelo [Gemini](https://ai.google.dev/) (pontuação, remoção de gagueira/repetição e comandos de voz como "quebra linha", "vírgula", "ponto").

## Como funciona

1. Um `AccessibilityService` (`BubbleAccessibilityService`) desenha uma bolha flutuante sobre qualquer app (overlay do tipo `TYPE_ACCESSIBILITY_OVERLAY`).
2. **Toque e segure** a bolha (≥450ms) para começar a gravar. A bolha expande e mostra a forma de onda em tempo real.
3. Solte sobre a área de confirmação para parar e transcrever, ou sobre a área de cancelar para descartar.
4. O áudio é gravado como WAV e enviado para a API da ElevenLabs (`VoiceCaptureService`, um foreground service com `foregroundServiceType="microphone|connectedDevice"`).
5. Se configurada, a chave do Gemini roda uma limpeza rápida em cima do texto bruto antes de entregá-lo.
6. O texto final é copiado para a área de transferência e colado no campo de texto focado via `AccessibilityNodeInfo` (`ACTION_PASTE`, com fallback para `ACTION_SET_TEXT`).
7. A bolha lembra o dispositivo de áudio Bluetooth conectado (`setPreferredDevice`) e mostra um estado "conectando" enquanto negocia o input de microfone.
8. Um clique simples (sem segurar) na bolha em estado ocioso também inicia a gravação; a bolha some quando não há teclado visível e a gravação está parada, e reaparece com o teclado.

## Funcionalidades

- **Bolha flutuante arrastável**, com posição salva e snap para a borda mais próxima da tela.
- **Dicionário pessoal** (`DictionaryActivity`): cadastre termos de vocabulário (keyterms enviados à API a cada ditado, melhora reconhecimento de nomes/jargão) ou pares de substituição "palavra errada → correção".
- **Diagnóstico** (`DiagnosticsActivity`): testa permissões, serviço de acessibilidade, otimização de bateria, e valida as chaves da ElevenLabs e do Gemini com uma chamada real de teste (incluindo captura real de 2s de áudio, opcional, pois consome créditos).
- **Log de erros local** (`LogActivity` + `FileLogger`): grava a causa exata de cada falha de transcrição, sem precisar de cabo USB/logcat para depurar em campo.
- **Tela inicial com status geral** do app (permissões, serviço, bateria, chaves de API).

## Requisitos

- Android 8.0 (API 26) ou superior.
- Uma chave de API da [ElevenLabs](https://elevenlabs.io) com permissão de escrita em "Speech to Text".
- (Opcional, mas recomendado) Uma chave de API do [Google AI Studio](https://aistudio.google.com/apikey) para o passo de limpeza do texto via Gemini. Sem ela, o app usa o texto bruto da transcrição.
- Android Studio (Giraffe ou mais recente) ou o Gradle wrapper incluso no repositório, com JDK 17.

## Instalação e build

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

## Configuração no aparelho

Depois de instalar, abra o app e siga a tela inicial, que mostra o status de cada item necessário:

1. **Permissões**: microfone, Bluetooth (Android 12+) e notificações (Android 13+). Toque em "Conceder".
2. **Serviço de acessibilidade**: obrigatório para desenhar a bolha e colar o texto automaticamente. Toque em "Abrir" e ative "Pster Voice" nas configurações de acessibilidade do Android.
3. **Otimização de bateria**: recomenda-se isentar o app, senão o Android pode cortar a captura de áudio fora do carregador.
4. **Chaves de API**: use o botão "Testar" em cada linha para validar a chave com uma chamada real à API correspondente.

## Uso

- Segure a bolha para gravar, solte sobre o ícone de confirmar para transcrever e colar, ou sobre o de cancelar para descartar.
- Toque simples na bolha ocioda também inicia a gravação.
- Arraste a bolha para reposicioná-la; ela gruda na borda mais próxima ao soltar.
- Use o **Dicionário pessoal** para ensinar nomes próprios, jargão técnico ou correções recorrentes de ditado.
- Se algo falhar, confira o **Log de erros** (mostra a causa exata: rede, chave inválida, áudio vazio etc.) ou rode o **Diagnóstico**.

## Estrutura do projeto

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

## Privacidade

O áudio gravado é enviado diretamente para a API da ElevenLabs (e, opcionalmente, o texto transcrito para a API do Gemini) para processamento. Nenhum áudio ou texto é armazenado em servidores próprios do projeto; o app não tem backend. As chaves de API ficam apenas no seu `.env` local e embutidas no seu próprio APK compilado.

## Licença

[PolyForm Noncommercial 1.0.0](LICENSE) — uso livre para fins não comerciais, com atribuição ao autor.
