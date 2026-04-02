<h1 align="center">
	<img src="fastlane/metadata/android/en-US/images/icon.png" width="25%" /> <br>
	YTDLnis
</h1>

<div align="center">
	<a href="https://github.com/deniscerri/ytdlnis/blob/main/README.md">English</a>
	&nbsp;&nbsp;| &nbsp;&nbsp;
	简体中文
</div>

<h3 align="center">
	YTDLnis 是一款免费且开源的视频/音频下载器，使用 yt-dlp ，支持 Android 7.0 及以上版本。
</h3>
<h4 align="center">
	本项目由 Denis Çerri 创建
</h4>

<div align="center">

[![GitHub Releases](https://custom-icon-badges.herokuapp.com/badge/Download-blue?style=for-the-badge&logo=download&logoColor=white)](https://github.com/deniscerri/ytdlnis/releases/latest)
[![F-Droid](https://custom-icon-badges.herokuapp.com/badge/FDroid-violet?style=for-the-badge&logo=download&logoColor=white)](https://f-droid.org/en/packages/com.deniscerri.ytdl)
[![IzzyOnDroid repository](https://custom-icon-badges.herokuapp.com/badge/IzzyOnDroid%20Repo-red?style=for-the-badge&logo=download&logoColor=white)](https://android.izzysoft.de/repo/apk/com.deniscerri.ytdl)
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

### 只有以上链接是 YTDLnis 的唯一可信来源。其他任何地方的版本都与作者无关。

</div>

## 💡 功能：

- 从超过 <a href="https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md">1000 个网站</a> 下载音频/视频文件
- 支持处理播放列表
	- 可以像编辑普通下载一样单独编辑列表中的每一项
	- 为列表中所有视频选择统一的格式，或下载为视频时选择多个音频格式
	- 为列表中的所有项设定下载路径
	- 为列表中的所有项选择文件名模板
	- 支持一键批量更新所有项的下载类型（音频/视频/自定义命令）
- 队列下载，按日期和时间计划下载
	- 也可以同时计划多个项目
- 同时下载多个项目
- 使用自定义命令和模板，或在内置终端中使用 yt-dlp
	- 可以备份和恢复模板，方便与朋友分享
- 支持 Cookie。使用账户登录并下载私密或其他视频，解锁高级格式等
- 基于时间戳和视频章节分割视频（实验性 yt-dlp 功能）
	- 可以进行无限次分割
- 从已下载项目中移除 SponsorBlock 元素
	- 可以将其作为章节嵌入视频中
- 嵌入字幕/元数据/章节等
- 修改元数据，如标题和作者
- 根据章节将项目分割成单独的文件
- 选择不同的下载格式
- 直接从分享菜单底部卡片操作，无需打开应用
	- 可以创建 txt 文件，填入链接/播放列表/搜索查询（每行一个），应用会自动处理
- 从应用中搜索或插入链接
	- 可以堆叠搜索并同时处理
- 记录下载日志，方便排查问题
- 重新下载已取消或失败的下载
	- 可以使用手势向左滑动重新下载，向右滑动删除
	- 长按详情表中的重新下载按钮可显示下载卡片获取更多功能
- 隐私模式：当你不想保存下载历史或日志时使用
- 快速下载模式
	- 立即下载，无需等待数据处理。关闭底部卡片即可立即开始
- 直接从完成通知中打开/分享已下载的文件
- 已实现大部分 yt-dlp 功能，欢迎提出建议
- Material You 界面设计
- 主题选项
- 备份和恢复功能
- 基于 MVVM 架构和 WorkManager

## 📲 预览截图

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

## 💬 联系我们

加入我们的 [Discord](https://discord.gg/WW3KYWxAPm) 或 [Telegram 频道](https://t.me/ytdlnis) 获取公告、讨论和新版本发布资讯。

## 😇 参与贡献

如果您想参与贡献，请阅读 [贡献](CONTRIBUTING.MD) 

## 📝 在 Weblate 上帮助翻译
<a href="https://hosted.weblate.org/engage/ytdlnis/">
<img src="https://hosted.weblate.org/widgets/ytdlnis/-/strings/open-graph.png" alt="翻译状态" />
</a>


<a href="https://hosted.weblate.org/engage/ytdlnis/">
<img src="https://hosted.weblate.org/widgets/ytdlnis/-/multi-auto.svg" alt="翻译状态" />
</a>

## 🔑 使用包名与第三方应用连接

应用的包名是 "com.deniscerri.ytdl"。


## 🤖 使用 Intent 与第三方应用连接

您可以使用 Intent 推送命令到应用，无需用户交互即可运行下载。
接受的变量：

<b>TYPE</b> -> 可以是：audio、video、command <br/>
<b>BACKGROUND</b> -> 可以是：true、false。如果为 true，应用将在后台运行下载，不显示下载卡片 <br/>

### 使用 Tasker 在后台下载音频的示例
1. 创建 Send Intent 任务
2. Action：android.intent.action.SEND
3. Cat：Default
4. Mime Type：text/*
5. Extra：android.intent.extra.TEXT:url（将 "url" 替换为要下载的视频 URL）
6. Extra：TYPE:audio
7. Extra：BACKGROUND:true

## 📄 许可

[GNU GPL v3.0](https://github.com/deniscerri/ytdlnis/blob/main/LICENSE)

除了在 GPLv3 许可证下的源代码外，禁止其他任何方以下载器应用的名义使用 "YTDLnis"，其衍生产品也不例外。衍生产品包括但不限于 fork 版本和非官方构建。

## 😁 捐赠


[<img src="https://raw.githubusercontent.com/WSTxda/WSTxda/main/images/BMC.svg"
alt='通过 BMC 捐赠'
height="80">](https://www.buymeacoffee.com/deniscerri)

## 🙏 致谢

- [decipher3114](https://github.com/decipher3114) 设计的应用图标
- [dvd](https://github.com/yausername/dvd) 提供的 youtubedl-android 实现示例
- [seal](https://github.com/JunkFood02/Seal) 提供的某些设计元素和功能
- [youtubedl-android](https://github.com/yausername/youtubedl-android) 将 yt-dlp 移植到 Android
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) 及其贡献者使得这个工具成为可能。没有它就不会有这个应用


以及很多包括贡献者在内的其他人
