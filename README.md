<h1 align="center">
	<img src="fastlane/metadata/android/en-US/images/icon.png" width="25%" /> <br>
	YTDLnis
</h1>

<div align="center">
	English
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-sq.md">Shqip</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-az.md">Azərbaycanca</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-tr.md">Türkçe</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-id.md">Indonesia</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-pt.md">Português</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-es.md">Español</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-ja.md">Japanese</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-ro.md">Română</a>
</div>

<h3 align="center">
	YTDLnis is a free and open source video/audio downloader using yt-dlp for Android 7.0 and above.
</h3>
<h4 align="center">
	Created by Denis Çerri
</h4>

<div align="center">

[![Official website](https://custom-icon-badges.herokuapp.com/badge/Official%20Website-violet?style=for-the-badge&logo=download&logoColor=white)](https://ytdlnis.org)
[![GitHub Releases](https://custom-icon-badges.herokuapp.com/badge/Download-blue?style=for-the-badge&logo=download&logoColor=white)](https://github.com/deniscerri/ytdlnis/releases/latest)
[<img alt="Get it on F-Droid" height="80" src="https://raw.githubusercontent.com/mueller-ma/android-common/main/assets/get-it-on-fdroid.png"/>](https://f-droid.org/packages/com.deniscerri.ytdl)[<img alt="Download from GitHub" height="80"
[![IzzyOnDroid repository](https://custom-icon-badges.herokuapp.com/badge/IzzyOnDroid%20Repo-red?style=for-the-badge&logo=download&logoColor=white)](https://android.izzysoft.de/repo/apk/com.deniscerri.ytdl)
[![Uptodown](https://custom-icon-badges.herokuapp.com/badge/UpToDown-green?style=for-the-badge&logo=download&logoColor=white)](https://ytdlnis.en.uptodown.com/android/download)

![CI](https://github.com/deniscerri/ytdlnis/actions/workflows/android.yml/badge.svg?branch=main&event=pull)
[![Preview release](https://img.shields.io/github/release/deniscerri/ytdlnis.svg?maxAge=3600&include_prereleases&label=preview)](https://github.com/deniscerri/ytdlnis/releases) 
[![Downloads](https://img.shields.io/github/downloads/deniscerri/ytdlnis/total?style=flat-square)](https://github.com/deniscerri/ytdlnis/releases) 
[![Translation status](https://hosted.weblate.org/widgets/ytdlnis/-/svg-badge.svg)](https://hosted.weblate.org/engage/ytdlnis/?utm_source=widget) 
[![community](https://img.shields.io/badge/Discord-YTDLnis-blueviolet?style=flat-square&logo=discord)](https://discord.gg/WW3KYWxAPm) 
[![community](https://img.shields.io/badge/Telegram-YTDLnis-blue?style=flat-square&logo=telegram)](https://t.me/ytdlnis)
[![community](https://img.shields.io/badge/Telegram-Updates-red?style=flat-square&logo=telegram)](https://t.me/ytdlnisupdates)
![GitHub Sponsor](https://img.shields.io/github/sponsors/deniscerri?label=Sponsor&logo=GitHub)

### Only the links above are the only trusted sources of YTDLnis. Everything else is not related to me.

</div>

## 💡 Features:

- Download audio/video files from more than <a href="https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md">1000 websites</a>
- Process playlists
	- Edit every playlist item separately just like in a normal download item
	- Select a common format for all items and/or select multiple audio formats in case you are downloading them as a video
	- Select a download path for all items
	- Select a filename template for all items
	- Batch update download type to audio/video/custom command in one click
- Queue downloads and schedule them by date and time
	- You can also schedule multiple items at the same time
- Download multiple items at the same time
- Use custom commands and templates or use yt-dlp with the built-in terminal
	- You can backup and restore templates so you can share them with your buddies
- Supports cookies. Log in with your accounts and download private/unavailable videos, unlock premium formats etc.
- Cut videos based on timestamps and video chapters (experimental yt-dlp feature)
	- You can make unlimited cuts
- Remove SponsorBlock elements from downloaded items
	- Embed them as a chapters in your video 
- Embed subtitles/metadata/chapters etc
- Modify metadata such as title and author
- Split item into separate files depending on its chapters
- Select different download formats
- Bottom card right from the share menu, no need to open the app 
	- You can create a txt file and fill it with links/playlists/search queries separate by a new line and the app will process them
- Search or insert a link from the app
	- You can stack searches so you can process them at the same time
- Log downloads in case of problems
- Re-download cancelled or failed downloads
	- You can use gestures to swipe left to redownload and right to delete
	- You can long click the redownload button in the details sheet to show the download card for more functionality
- Incognito mode when you don't want to save a download history or logs
- Quick download mode
	- Download immediately without having to wait for data to process. Turn off the bottom card and it will instantly start
- Open / share downloaded files right from the finished notification
- Most yt-dlp features are implemented, suggestions are welcome
- Material You interface
- Theming options
- Backup and restore features
- MVVM architecture with WorkManager

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
