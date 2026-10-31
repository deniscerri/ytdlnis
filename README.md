<h1 align="center">
	<img src="fastlane/metadata/android/en-US/images/icon.png" width="25%" /> <br>
	YTDLnis
</h1>

<div align="center">
	English
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-sq.md">Shqip</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-az.md">Azərbaycanca</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-tr.md">Türkçe</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-id.md">Indonesia</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-pt.md">Português</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-es.md">Español</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-ja.md">Japanese</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-zh_CN.md">简体中文</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-ro.md">Română</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-ar.md">العربية</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/docs/README-ru.md">Русский</a>
</div>

<h3 align="center">
	YTDLnis is a free and open source video/audio downloader using yt-dlp for Android 7.0 and above.
</h3>
<h4 align="center">
	Created by Denis Çerri
</h4>

<div align="center">

[![GitHub Releases](https://custom-icon-badges.herokuapp.com/badge/Download-blue?style=for-the-badge&logo=download&logoColor=white)](https://github.com/deniscerri/ytdlnis/releases/latest)
[![F-Droid](https://custom-icon-badges.herokuapp.com/badge/FDroid-violet?style=for-the-badge&logo=download&logoColor=white)](https://f-droid.org/en/packages/com.deniscerri.ytdl)
[![IzzyOnDroid repository](https://custom-icon-badges.herokuapp.com/badge/IzzyOnDroid%20Repo-red?style=for-the-badge&logo=download&logoColor=white)](https://apt.izzysoft.de/packages/com.deniscerri.ytdl)
[![Uptodown](https://custom-icon-badges.herokuapp.com/badge/UpToDown-green?style=for-the-badge&logo=download&logoColor=white)](https://ytdlnis.en.uptodown.com/android/download)

![CI](https://github.com/deniscerri/ytdlnis/actions/workflows/android.yml/badge.svg?branch=main&event=pull)
[![Preview release](https://img.shields.io/github/release/deniscerri/ytdlnis.svg?maxAge=3600&include_prereleases&label=preview)](https://github.com/deniscerri/ytdlnis/releases) 
[![Downloads](https://img.shields.io/github/downloads/deniscerri/ytdlnis/total?style=flat-square)](https://github.com/deniscerri/ytdlnis/releases) 
[![Translation status](https://hosted.weblate.org/widgets/ytdlnis/-/svg-badge.svg)](https://hosted.weblate.org/engage/ytdlnis/?utm_source=widget) 
[![community](https://img.shields.io/badge/Discord-YTDLnis-blueviolet?style=flat-square&logo=discord)](https://discord.gg/WW3KYWxAPm) 
[![community](https://img.shields.io/badge/Telegram-YTDLnis-blue?style=flat-square&logo=telegram)](https://t.me/ytdlnis)
[![community](https://img.shields.io/badge/Telegram-Updates-red?style=flat-square&logo=telegram)](https://t.me/ytdlnis_updates)
[![website](https://img.shields.io/badge/Website-orange?style=flat-square&logo=youtube)](https://ytdlnis.org)
![GitHub Sponsor](https://img.shields.io/github/sponsors/deniscerri?label=Sponsor&logo=GitHub)

### Only the links above are the only trusted sources of YTDLnis. Everything else is not related to me.

</div>

## 💡 Features

### ⬇️ Downloading
- Download audio, video or run custom yt-dlp commands on [1000+ websites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)
- Share a link to the app and download straight from the bottom card, no need to open the app
- Quick download mode to start instantly without waiting for data to load
- Playlists and bulk downloads: edit every item separately or bulk edit type, format, path and filename template for all of them, sort by author, duration, title or date
- Feed a `.txt` file of links, playlists or search queries and the app processes them all
- Queue, schedule by date and time, and run multiple downloads at the same time
- Pause / resume, re-download failed or cancelled items with swipe gestures
- Observe sources: watch playlists and channels and automatically download new uploads, with optional sync
- Duplicate checking, failed data fetches are saved so you can retry them later
- Uploads to cloud providers (e.g. Nextcloud) when your download path is an external storage provider
- Android 16 live notifications for every running download

### ✂️ Editing your files
- Cut videos by timestamps or chapters with unlimited cuts, and optionally merge the cuts into one file
- Crop videos right from the download card
- Split items into separate files by chapters
- Remove or mark SponsorBlock segments as chapters
- Embed subtitles, metadata, chapters and thumbnails, or edit the title and author
- Burn subtitles into the video
- Search synced or plain lyrics from [lrclib.net](https://lrclib.net), preview and adjust their timing, then embed them or save them as `.lrc`
- Compatible mode to re-encode into widely supported formats
- Filename templates with live preview, or simple subfolders by playlist, website or download type

### 🧑‍💻 For power users
- Full terminal with bash, where yt-dlp, Python, pip, FFmpeg and the other bundled tools are ready to use
- Command templates with syntax highlighting that you can back up and share
- Cookies support: log in through the built-in browser and download private or premium content
- Generate YouTube PO tokens with the built-in BGUtils provider or inside the app's webview
- Choose NewPipe or yt-dlp as the data fetching extractor
- Python with curl-cffi bundled, plus JS runtimes (QuickJS, Deno, NodeJS) for website challenges
- Custom yt-dlp sources and automatic yt-dlp updates
- Intents for automation with apps like Tasker

### 🎨 App
- Material You design with theming options and a customizable bottom navigation
- Incognito mode so no history or logs are saved
- Download logs, search history and a detailed download history with different card layouts
- Backup and restore almost everything, right from the welcome screen too
- Files manager to clear caches and leftovers

## 🧩 Plugin Support

YTDLnis orchestrates plugins so users can freely upgrade and downgrade components such as:
- Python
- JS Runtimes (NodeJS, Deno)
- FFmpeg
- Aria2c

You can install ytdlnis packages from this repository [ytdlnis-packages](https://github.com/deniscerri/ytdlnis-packages/) or through the updating section in the application.
<br>For more information refer to the repo's README.

## 📲 Screenshots

<div>
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/09.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/11.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12.png" width="30%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/13.png" width="90%" />
</div>

## 💬 Contact

Join our [Discord](https://discord.gg/WW3KYWxAPm) or [Telegram channel](https://t.me/ytdlnis) for announcements, discussion and releases.

## 😇 Contributing

Please read the [contributing](CONTRIBUTING.MD) section if you would like to contribute.

## 📝 Help translate on Weblate
<a href="https://hosted.weblate.org/engage/ytdlnis/">
<img src="https://hosted.weblate.org/widgets/ytdlnis/-/strings/open-graph.png" alt="Translation status" />
</a>


<a href="https://hosted.weblate.org/engage/ytdlnis/">
<img src="https://hosted.weblate.org/widgets/ytdlnis/-/multi-auto.svg" alt="Translation status" />
</a>

## 🔑 Connect with third-party apps using the package name

The app's package name is "com.deniscerri.ytdl".

## 🔍 Verify application signature

The app should contain the signature below. The github workflow action uses it, and the releases are based on it to make it a reproducible build.
If the signature is different, your third party distributor has modified the application. Please use the app with the original signature.
```
Signer #1 certificate DN: CN=Denis Cerri, OU=Personal, O=Personal, L=Albania, ST=Albania, C=AL
Signer #1 certificate SHA-256 digest: 263645cb5272eb290759fe1f59149ae24df6ce171e9f6666eead981d3fc64c95
Signer #1 certificate SHA-1 digest: 2fec9c2fcef68d29a60857e185c795fec5f56fb6
Signer #1 certificate MD5 digest: 429d0c6315d2f99650f66cc44cf5a794
```


## 🤖 Connect with third-party apps using intents

You can use intents to push commands to the app to run downloads without user interaction.
Accepted variables:

<b>TYPE</b> -> it can be: audio,video,command <br/>
<b>BACKGROUND</b> -> it can be: true,false. If its true the app won't show the download card no matter what and run the download in the background <br/>

### An example of downloading an audio item in the background with Tasker
1. Create Send Intent task
2. Action: android.intent.action.SEND
3. Cat: Default
4. Mime Type: text/*
5. Extra: android.intent.extra.TEXT:url (instead of "url" write the URL of the video you want to download)
6. Extra: TYPE:audio
7. Extra: BACKGROUND:true

## 📄 License

[GNU GPL v3.0](https://github.com/deniscerri/ytdlnis/blob/main/LICENSE)

Except for the source code licensed under the GPLv3 license, all other parties are prohibited from using the "YTDLnis" name as a downloader app, and the same is true for its derivatives. Derivatives include but are not limited to forks and unofficial builds.

## 😁 Donate


[<img src="https://raw.githubusercontent.com/WSTxda/WSTxda/main/images/BMC.svg"
alt='Donate with BMC'
height="80">](https://www.buymeacoffee.com/deniscerri)

## 🙏 Thanks

- [decipher3114](https://github.com/decipher3114) for the app's icon
- [dvd](https://github.com/yausername/dvd) for being an example youtubedl-android implementation
- [seal](https://github.com/JunkFood02/Seal) for certain design elements and features I wanted to have in this app when I started developing it
- [youtubedl-android](https://github.com/yausername/youtubedl-android) for porting yt-dlp to Android
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) and its contributors for making this tool possible. Without it this app wouldn't exist


and to a lot of other people, such as contributors.
