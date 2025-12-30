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
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-ja.md">Japanese</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README-ro.md">Română</a>
    &nbsp;&nbsp;| &nbsp;&nbsp;
    Chinese Simplified
    &nbsp;&nbsp;| &nbsp;&nbsp;
    <a href="https://github.com/deniscerri/ytdlnis/blob/main/README-bn-IN.md">Bengali India</a>
</div>

<h3 align="center">
	YTDLnis 是一款免费开源的视频/音频下载器，基于 yt-dlp，适用于 Android 7.0 及以上版本。
</h3>
<h4 align="center">
	由 Denis Çerri 开发
</h4>

<div align="center">

[![官方网站](https://custom-icon-badges.herokuapp.com/badge/Official%20Website-violet?style=for-the-badge&logo=download&logoColor=white)](https://ytdlnis.org)
[![GitHub Releases](https://custom-icon-badges.herokuapp.com/badge/Download-blue?style=for-the-badge&logo=download&logoColor=white)](https://github.com/deniscerri/ytdlnis/releases/latest)
[![IzzyOnDroid 仓库](https://custom-icon-badges.herokuapp.com/badge/IzzyOnDroid%20Repo-red?style=for-the-badge&logo=download&logoColor=white)](https://android.izzysoft.de/repo/apk/com.deniscerri.ytdl)
[![Uptodown](https://custom-icon-badges.herokuapp.com/badge/UpToDown-green?style=for-the-badge&logo=download&logoColor=white)](https://ytdlnis.en.uptodown.com/android/download)

![CI](https://github.com/deniscerri/ytdlnis/actions/workflows/android.yml/badge.svg?branch=main&event=pull)
[![预览版本](https://img.shields.io/github/release/deniscerri/ytdlnis.svg?maxAge=3600&include_prereleases&label=preview)](https://github.com/deniscerri/ytdlnis/releases) 
[![下载量](https://img.shields.io/github/downloads/deniscerri/ytdlnis/total?style=flat-square)](https://github.com/deniscerri/ytdlnis/releases) 
[![翻译状态](https://hosted.weblate.org/widgets/ytdlnis/-/svg-badge.svg)](https://hosted.weblate.org/engage/ytdlnis/?utm_source=widget) 
[![社区](https://img.shields.io/badge/Discord-YTDLnis-blueviolet?style=flat-square&logo=discord)](https://discord.gg/WW3KYWxAPm) 
[![社区](https://img.shields.io/badge/Telegram-YTDLnis-blue?style=flat-square&logo=telegram)](https://t.me/ytdlnis)
[![社区](https://img.shields.io/badge/Telegram-Updates-red?style=flat-square&logo=telegram)](https://t.me/ytdlnisupdates)
![GitHub Sponsor](https://img.shields.io/github/sponsors/deniscerri?label=Sponsor&logo=GitHub)

### 仅以上链接为 YTDLnis 的可信来源。其他任何来源均与本项目无关。

</div>

## 💡 功能特性：

- 从超过 <a href="https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md">1000 个网站</a>下载音频/视频文件
- 处理播放列表
	- 像编辑普通下载项目一样，单独编辑播放列表中的每个项目
	- 为所有项目选择统一格式，或者在下载视频时同时选择多种音频格式
	- 为所有项目选择统一的下载路径
	- 为所有项目选择统一的文件名模板
	- 一键批量将下载类型更新为音频/视频/自定义命令
- 队列下载并按日期和时间安排任务
	- 您还可以同时安排多个项目
- 同时下载多个项目
- 使用自定义命令和模板，或通过内置终端使用 yt-dlp
	- 支持备份和恢复模板，方便与朋友分享
- 支持 Cookie：登录您的账号以下载私有/受限视频、解锁高级格式等
- 根据时间戳和视频章节裁剪视频（yt-dlp 实验性功能）
	- 支持无限次裁剪
- 从下载项目中移除 SponsorBlock（赞助商广告）片段
	- 或将其作为章节嵌入到视频中 
- 嵌入字幕/元数据/章节等
- 修改元数据（如标题和作者）
- 根据章节将项目分割为独立文件
- 选择不同的下载格式
- 直接从分享菜单调出底部卡片，无需打开应用 
	- 您可以创建一个 txt 文件，填入以换行符分隔的链接/播放列表/搜索关键词，应用将自动处理它们
- 在应用内搜索或插入链接
	- 支持叠加多个搜索以便同时处理
- 记录下载日志以便排查问题
- 重新下载已取消或失败的任务
	- 手势操作：左滑重新下载，右滑删除
	- 长按详情页中的重新下载按钮可显示下载卡片，以获取更多功能
- 无痕模式：不保存下载历史或日志
- 快速下载模式
	- 无需等待数据处理即可立即开始下载。关闭底部卡片后将立即启动
- 直接从完成通知中打开/分享已下载的文件
- 已实现大部分 yt-dlp 功能，欢迎提出建议
- Material You 界面设计
- 丰富的主题选项
- 备份与恢复功能
- 基于 WorkManager 的 MVVM 架构

## 📲 屏幕截图

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

## 💬 联系方式

加入我们的 [Discord](https://discord.gg/WW3KYWxAPm) 或 [Telegram 频道](https://t.me/ytdlnis) 获取公告、讨论和最新发布信息。

## 😇 参与贡献

如果您想做出贡献，请阅读 [贡献指南](CONTRIBUTING.MD)。

## 📝 在 Weblate 上协助翻译
<a href="https://hosted.weblate.org/engage/ytdlnis/">
<img src="https://hosted.weblate.org/widgets/ytdlnis/-/strings/open-graph.png" alt="Translation status" />
</a>


<a href="https://hosted.weblate.org/engage/ytdlnis/">
<img src="https://hosted.weblate.org/widgets/ytdlnis/-/multi-auto.svg" alt="Translation status" />
</a>

## 🔑 通过包名连接第三方应用

应用的包名为 `com.deniscerri.ytdl`。


## 🤖 通过 Intent 连接第三方应用

您可以使用 Intent (意图) 向应用推送命令，从而在无需用户交互的情况下运行下载。
可接受的变量：

<b>TYPE</b> -> 可选值：audio (音频), video (视频), command (命令) <br/>
<b>BACKGROUND</b> -> 可选值：true (是), false (否)。如果为 true，应用无论如何都不会显示下载卡片，并将在后台运行下载任务。<br/>

### 使用 Tasker 在后台下载音频项目的示例
1. 创建“发送 Intent” (Send Intent) 任务
2. 操作 (Action): `android.intent.action.SEND`
3. 类别 (Cat): Default
4. Mime 类型: `text/*`
5. Extra: `android.intent.extra.TEXT:url` (将 "url" 替换为您要下载的视频 URL)
6. Extra: `TYPE:audio`
7. Extra: `BACKGROUND:true`

## 📄 许可证

[GNU GPL v3.0](https://github.com/deniscerri/ytdlnis/blob/main/LICENSE)

除根据 GPLv3 许可证授权的源代码外，严禁任何其他方将 "YTDLnis" 名称用于下载器应用，其衍生作品亦同。衍生作品包括但不限于复刻 (Forks) 和非官方构建版本。

## 😁 捐赠


[<img src="https://raw.githubusercontent.com/WSTxda/WSTxda/main/images/BMC.svg"
alt='通过 BMC 捐赠'
height="80">](https://www.buymeacoffee.com/deniscerri)

## 🙏 致谢

- [decipher3114](https://github.com/decipher3114) 设计应用图标
- [dvd](https://github.com/yausername/dvd) 提供了优秀的 youtubedl-android 实现示例
- [seal](https://github.com/JunkFood02/Seal) 提供了一些我在开发初期想要实现的设计元素和功能参考
- [youtubedl-android](https://github.com/yausername/youtubedl-android) 将 yt-dlp 移植到 Android
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) 及其贡献者使这个工具成为可能。没有它，本应用就不会存在


以及许多其他提供帮助的人，例如各位贡献者。
