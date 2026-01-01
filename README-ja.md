<h1 align="center">
	<img src="fastlane/metadata/android/en-US/images/icon.png" width="25%" /> <br>
	YTDLnis
</h1>

<div align="center">
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README.md">English</a>
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
	Japanese
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-ro.md">Română</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-zh-Hans.md">Chinese Simplified</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-bn-IN.md">Bengali India</a>
</div>

<h3 align="center">
	YTDLnisは、Android 7.0以降に対応した、yt-dlpを使用した無料かつオープンソースのビデオ/オーディオダウンローダーです。
</h3>
<h4 align="center">
	Denis Çerriによって作成されました
</h4>

<div align="center">

[![Official website](https://custom-icon-badges.herokuapp.com/badge/Official%20Website-violet?style=for-the-badge&logo=download&logoColor=white)](https://ytdlnis.org)
[![Github Download](https://custom-icon-badges.herokuapp.com/badge/Download-blue?style=for-the-badge&logo=download&logoColor=white)](https://github.com/deniscerri/ytdlnis/releases/latest)
[![IzzyOnDroid Repo](https://custom-icon-badges.herokuapp.com/badge/IzzyOnDroid%20Repo-red?style=for-the-badge&logo=download&logoColor=white)](https://android.izzysoft.de/repo/apk/com.deniscerri.ytdl)
[![UpToDown](https://custom-icon-badges.herokuapp.com/badge/UpToDown-green?style=for-the-badge&logo=download&logoColor=white)](https://ytdlnis.en.uptodown.com/android/download)

![CI](https://github.com/deniscerri/ytdlnis/actions/workflows/android.yml/badge.svg?branch=main&event=pull)
[![preview release](https://img.shields.io/github/release/deniscerri/ytdlnis.svg?maxAge=3600&include_prereleases&label=preview)](https://github.com/deniscerri/ytdlnis/releases) 
[![downloads](https://img.shields.io/github/downloads/deniscerri/ytdlnis/total?style=flat-square)](https://github.com/deniscerri/ytdlnis/releases) 
[![Translation status](https://hosted.weblate.org/widgets/ytdlnis/-/svg-badge.svg)](https://hosted.weblate.org/engage/ytdlnis/?utm_source=widget) 
[![community](https://img.shields.io/badge/Discord-YTDLnis-blueviolet?style=flat-square&logo=discord)](https://discord.gg/WW3KYWxAPm) 
[![community](https://img.shields.io/badge/Telegram-YTDLnis-blue?style=flat-square&logo=telegram)](https://t.me/ytdlnis)
[![community](https://img.shields.io/badge/Telegram-Updates-red?style=flat-square&logo=telegram)](https://t.me/ytdlnisupdates)
![GitHub Sponsor](https://img.shields.io/github/sponsors/deniscerri?label=Sponsor&logo=GitHub)

### 上記のリンクのみがYTDLnisの信頼できるソースです。それ以外は私とは一切関係ありません。

</div>

## 💡 特徴:

- <a href="https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md">1000以上のウェブサイト</a>からオーディオ/ビデオファイルをダウンロード
- プレイリストの処理
	- 通常のダウンロードアイテムと同様に、各プレイリストアイテムを個別に編集可能
	- すべてのアイテムに共通のフォーマットを選択、またはビデオとしてダウンロードする場合に複数のオーディオフォーマットを選択可能
	- すべてのアイテムのダウンロードパスを一括選択
	- すべてのアイテムのファイル名テンプレートを一括選択
	- ワンクリックでダウンロードタイプをオーディオ/ビデオ/カスタムコマンドにバッチ更新
- ダウンロードをキューに入れ、日付と時間でスケジュール設定
	- 複数のアイテムを同時にスケジュールすることも可能
- 複数のアイテムを同時にダウンロード
- カスタムコマンドとテンプレートを使用するか、組み込みのターミナルで完全なyt-dlpモードを使用
	- テンプレートをバックアップおよび復元して友達と共有可能
- クッキーのサポート。アカウントにログインして、プライベート/利用不可のビデオをダウンロードしたり、プレミアムフォーマットをロック解除したりできます
- タイムスタンプとビデオチャプターに基づいてビデオをカット（yt-dlpの実験的機能）
	- 無制限にカット可能
- ダウンロードしたアイテムからSponsorBlock（スポンサー）要素を削除
	- ビデオのチャプターとして埋め込むことも可能
- 字幕/メタデータ/チャプターなどを埋め込み
- タイトルや著者などのメタデータを変更
- チャプターに応じてアイテムを別々のファイルに分割
- 異なるダウンロードフォーマットを選択
- アプリを開かずに共有メニューから直接ボトムカードを表示
	- txtファイルを作成し、新しい行でリンク/プレイリスト/検索クエリを区切って入力すると、アプリがそれらを処理します
- アプリからリンクを検索または挿入
	- 検索をスタックして同時に処理可能
- 問題が発生した場合のダウンロードログ機能
- キャンセルまたは失敗したダウンロードを再ダウンロード
	- 左にスワイプして再ダウンロード、右にスワイプして削除するジェスチャーを使用可能
	- 詳細シートの再ダウンロードボタンを長押しして、ダウンロードカードを表示し、より多くの機能を利用可能
- ダウンロード履歴やログを保存したくない場合のシークレットモード
- クイックダウンロードモード
	- データの処理を待たずに即座にダウンロード。ボトムカードをオフにすると、即座に開始されます
- 完了した通知から直接ダウンロードしたファイルを開く/共有
- ほとんどのyt-dlp機能が実装されており、提案は歓迎します
- Material Youインターフェース
- テーマオプション
- バックアップと復元機能
- WorkManagerを使用したMVVMアーキテクチャ

## 📲 スクリーンショット

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

## 💬 連絡先

ディスカッション、発表、リリースについては、[Discord](https://discord.gg/WW3KYWxAPm)または[Telegramチャンネル](https://t.me/ytdlnis)に参加してください！

## 😇 貢献

貢献したい場合は、[貢献](CONTRIBUTING.MD)セクションをお読みください。

## 📝 Weblateで翻訳を手伝う
<a href="https://hosted.weblate.org/engage/ytdlnis/">
<img src="https://hosted.weblate.org/widgets/ytdlnis/-/strings/open-graph.png" alt="Translation status" />
</a>


<a href="https://hosted.weblate.org/engage/ytdlnis/">
<img src="https://hosted.weblate.org/widgets/ytdlnis/-/multi-auto.svg" alt="Translation status" />
</a>

## 🔑 パッケージ名を使用してサードパーティアプリと連携

アプリのパッケージ名は「com.deniscerri.ytdl」です。

## 🤖 インテントを使用してサードパーティアプリと連携

TaskerやMacrodroidなどのアプリでインテントを使用して、ユーザーの操作なしにダウンロードを実行するコマンドをアプリに送信できます。
受け入れられる変数:

<b>TYPE</b> -> audio, video, command のいずれか <br/>
<b>BACKGROUND</b> -> true, false のいずれか。trueの場合、アプリはダウンロードカードを表示せず、強制的にバックグラウンドでダウンロードを実行します <br/>

### Taskerでバックグラウンドでオーディオをダウンロードする例
1. 「Send Intent（インテント送信）」タスクを作成
2. Action: android.intent.action.SEND
3. Cat: Default
4. Mime Type: text/*
5. Extra: android.intent.extra.TEXT:url （"url"の代わりにダウンロードしたいビデオのURLを入力）
6. Extra: TYPE:audio
7. Extra: BACKGROUND:true


## 📄 ライセンス

[GNU GPL v3.0](https://github.com/deniscerri/ytdlnis/blob/main/LICENSE)

GPLv3ライセンスの下でライセンスされたソースコードを除き、他のすべての当事者はダウンローダーアプリとして「YTDLnis」という名前を使用することを禁止されており、その派生物についても同様です。派生物には、フォークや非公式ビルドが含まれますが、これらに限定されません。

## 😁 寄付


[<img src="https://raw.githubusercontent.com/WSTxda/WSTxda/main/images/BMC.svg"
alt='Donate with BMC'
height="80">](https://www.buymeacoffee.com/deniscerri)

## 🙏 感謝

- [decipher3114](https://github.com/decipher3114) アプリアイコンの提供
- [dvd](https://github.com/yausername/dvd) youtubedl-androidの実装例の提示
- [seal](https://github.com/JunkFood02/Seal) 開発開始時にこのアプリに取り入れたい特定のデザイン要素や機能の提供
- [youtubedl-android](https://github.com/yausername/youtubedl-android) yt-dlpのAndroidへの移植
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) このツールを可能にしてくれた貢献者。これがなければ、このアプリは存在しませんでした。


そして、貢献者など、他の多くの人々に感謝します。