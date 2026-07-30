# BadukNext Development Log

## 2026-07-29 — kata-analyze integration & UI restructure

### Changes
- **UI restructure**: Unified Play/Analyze layout (TopBar → GameInfo → WinRateBar → Board → Footer)
- **kata-analyze**: Added `KataGoEngine.analyzePosition()` — sends `kata-analyze`, parses JSON for winrate/scoreLead/candidates/ownership
- **Win rate bar**: Shows real data from kata-analyze (B% / scoreLead / W%)
- **Territory dialog**: Shows AI-estimated score with "Force End Game" button
- **5 sounds**: All user-provided WAV/OGG files as selectable place sounds (S1-S5)
- **Settings**: Collapsible sections (Sound/Game/Theme)
- **Analysis mode**: Free placement, move navigation, sub-tabs (落子树/Chart/Candidates)

### Fixed
- WAV byte order in StoneSoundPlayer (was using Integer.reverseBytes incorrectly)
- Board jumping (fixed height constraints)
- onSettings parameter name
- Duplicate @Composable annotation
- PlayButton parameter order (modifier before enabled)
- GameRecorder not recording moves (analysis mode was empty)

### Known Issues
- kata-analyze may not work on all KataGo configs (requires `kata-analyze` GTP command support)
- Ownership territory overlay not yet rendered on board
- Analysis chart still placeholder
- "Direct end game" via kata-analyze not available
