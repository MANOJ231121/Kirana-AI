// Audio playback utility for Rime AI voice & natural browser TTS fallback

export function getNaturalVoice() {
  if (!('speechSynthesis' in window)) return null;
  const voices = window.speechSynthesis.getVoices();
  if (!voices || !voices.length) return null;

  // 1. Prefer natural Hindi voices (Swara, Google Hindi, etc.)
  const hindiVoice = voices.find(
    (v) => (v.lang.includes('hi') || v.name.toLowerCase().includes('hindi')) &&
           (v.name.toLowerCase().includes('natural') || v.name.toLowerCase().includes('google') || v.name.toLowerCase().includes('swara') || v.name.toLowerCase().includes('heera'))
  ) || voices.find((v) => v.lang.includes('hi') || v.name.toLowerCase().includes('hindi'));

  if (hindiVoice) return hindiVoice;

  // 2. Prefer Indian English natural voices
  const indianEngVoice = voices.find(
    (v) => v.lang.includes('en-IN') || v.name.toLowerCase().includes('india')
  );
  if (indianEngVoice) return indianEngVoice;

  // 3. Prefer any natural voice
  const naturalVoice = voices.find((v) => v.name.toLowerCase().includes('natural') || v.name.toLowerCase().includes('google'));
  return naturalVoice || voices[0];
}

export function speakBrowser(text) {
  try {
    if (!('speechSynthesis' in window) || !text) return;
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'hi-IN';
    u.rate = 0.95;
    u.pitch = 1.0;

    const voice = getNaturalVoice();
    if (voice) {
      u.voice = voice;
    }

    window.speechSynthesis.speak(u);
  } catch (e) {
    console.warn('Browser TTS failed:', e);
  }
}

export function playBase64Audio(base64, mime = 'audio/mpeg') {
  if (!base64) return false;
  try {
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const blob = new Blob([bytes], { type: mime });
    const url = URL.createObjectURL(blob);
    const audio = new Audio(url);
    audio.volume = 1.0;

    const playPromise = audio.play();
    if (playPromise !== undefined) {
      playPromise.catch((err) => {
        console.warn('Rime AI audio playback prevented or deferred by browser:', err);
      });
    }
    return true;
  } catch (e) {
    console.warn('Base64 audio decode error:', e);
    return false;
  }
}
