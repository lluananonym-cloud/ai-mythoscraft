# Architecture rules
- Keep Mythos Code desktop sources as raw files under `src/lib/mythos-code-app`; `mythosCli.ts` packages them into the downloadable build ZIP.
- Keep the browser extension source under `extension`; regenerate `public/mythos-browser.zip` after every extension change.
- Connect the desktop app to the browser extension only through a token-protected loopback bridge bound to `127.0.0.1`.
- Show safe, concrete work activity to users; never expose private model chain-of-thought or raw reasoning tokens.
