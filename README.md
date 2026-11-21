# iPinch for Android 🦀

Put your own AI assistant behind your Android phone's **assistant button**.

iPinch is a tiny Android app (one screen, no third-party libraries). Make it your
*Digital assistant app*. Then long-pressing home or the side button opens a chat
that starts listening straight away. Your speech goes to your own conversation
endpoint, and the reply is read back out loud. It keeps listening after each reply
until the assistant says the conversation is over.

It's a thin client and has no AI built in. It talks to any server that speaks the
small HTTP protocol below, so you can put Claude, a local model, Home Assistant
or anything else behind it.

```
assistant button ──▶ iPinch ──speech→text──▶ POST /v1/turn ──▶ your assistant
                       ▲                                          │
                       └────────── spoken reply, listen again ◀───┘
```

## Protocol

```
POST {base_url}/v1/turn
X-Pinch-Token: <shared secret>
Content-Type: application/json

{"text": "is the heating on?", "conversation_id": "phone-1a2b3c4d", "device": "phone"}
```

```json
{"reply": "Yes, it's set to 20 degrees.", "conversation_id": "phone-1a2b3c4d", "end": false}
```

- `conversation_id`: the app makes one up. Echo it back, or return your own and the
  app will use it from then on. After 9 minutes idle the app starts a new one.
- `device`: free text you set in the app. Handy for giving different devices
  different permissions.
- `end: true` means the person signed off ("thanks, bye"). The app stops listening
  and starts a new conversation next time. A literal `<end>` in `reply` is removed.
- Return `401` for a bad token. Keep the reply short enough to speak; the app waits
  up to 30 s.
- `GET {base_url}/healthz` → `200`, no auth. This is what the **Test connection**
  button calls.

## Install

Requires JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the phone:

1. Open iPinch → ⚙ → enter your endpoint URL and token → **Test connection** → **Save**.
2. ⚙ → **Set as assistant app…** → choose iPinch. Or from a computer:
   `adb shell cmd role add-role-holder android.app.role.ASSISTANT com.ipinch.app 0`
3. Hold the assistant button and talk.

**Samsung:** the side button and long-press home don't always go to the assistant.
Use Settings → Advanced features → Side button → Press and hold → *Digital assistant*.
Long-press home starts Circle to Search until you turn that off under
Settings → Display → Navigation bar.

## Design notes

- **`ACTION_ASSIST`, not a `VoiceInteractionService`.** An activity that handles
  `ACTION_ASSIST` is enough to appear under *Default apps → Digital assistant app*.
  A full voice interaction service also has to ship a speech recogniser. Android then
  makes that the phone's system recogniser, which breaks voice typing in other apps.
- Speech recognition and TTS come from the phone (`RecognizerIntent`, `TextToSpeech`),
  set to UK English. Change that in `ChatActivity.kt` if you want another language.
- Plain HTTP is allowed because the endpoint is usually on your LAN or a Tailscale
  address. The token is stored in the app's private storage, and backups are turned off.
- Typed messages get text replies only; only spoken turns are read aloud.

## License

MIT
