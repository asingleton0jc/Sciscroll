# SciScroll

SciScroll is a native Android MVP for discovering scientific papers in a vertical, Instagram/Reels-style feed.

## Included in v0.1
- Vertical swipe-through paper feed
- Live Europe PMC paper discovery
- Topic feeds: biotech, neuroscience, medicine, genetics, AI, general science
- Natural-language search
- 30-second abstract summary (extractive, not AI-generated)
- “Why it may matter” abstract-based highlight
- Citation count / year / open-access metadata where available
- Save/bookmark locally
- Saved-paper feed
- “Not interested” filtering
- Copy citation
- Share paper
- Open DOI / Europe PMC record
- Offline welcome card if the API is unreachable
- Dark, science-focused UI

## Privacy
No account is required. Bookmarks and ignored-paper IDs are stored locally in Android SharedPreferences. Search terms are sent to the Europe PMC public API to retrieve papers.

## Data source
Europe PMC REST API: https://www.ebi.ac.uk/europepmc/webservices/rest/search

## Build
This project targets Android 36 with minSdk 26. It uses only the Android framework; there are no app runtime dependencies.

Open in Android Studio and build `app`, or push to GitHub and run the included `Build SciScroll APK` Actions workflow. The debug APK is produced at:

`app/build/outputs/apk/debug/app-debug.apk`

## Next features worth adding
- Real AI summaries with a user-selected model/API
- Custom named collections
- Follow researchers / journals / keywords
- Related papers and citation graph
- Full-text “Ask this paper” for open-access papers
- Personalised recommendation ranking
- Paper figures/images when licensing and API metadata allow
- Export to BibTeX / RIS / Zotero
