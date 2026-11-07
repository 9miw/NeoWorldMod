# Current NPC speech playback plan

Approved scope: hide player responses, clear visible speech immediately after selection, animate only the latest NPC speech, group Thai marks, Enter/Space reveal without selecting, enable choices only after completion.

1. Add a Minecraft-independent `DialogueTextPlayback` with elapsed-time reveal, Unicode grapheme boundaries, waiting-for-NPC state and held-key guard. Test time boundaries, Thai/emoji, clear/new speech, skip/release and reflow before integrating.
2. Capture the actual NPC `TextBlockClient` only from `appendDialog` RETURN. Never derive displayed speech from the last entry of the mixed NPC/player history after a response is selected. Clear the captured block and visible text at `handleDialogSelection` HEAD.
3. Reflow the current speech into styled lines, render only the visible prefix without changing inline glyph styles, and follow the revealed lines as needed. Resize must not restart playback.
4. Gate all response clicks, keyboard navigation and choice scrolling until ready. Preserve close/forced-dialogue/wheel/sub-GUI behavior. Keep held Enter from becoming a second action via key repeat.
5. Run focused regression tests and compileJava, review changed files, and record in-game verification separately. Do not restart the user's running multiplayer client.

Completed: model, focused tests, current-NPC capture/clear, styled reveal renderer and input guards. compileJava and both focused regression suites passed. Independent follow-up review found no significant issue. Live in-game verification remains pending a client restart.
