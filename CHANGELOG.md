# YTDLnis Changelog

> # 1.8.7.1 (2026-01)

# What's Changed

## Filename Template Live Preview & Info JSON

Brought back info json feature to prevent the app from calling servers twice. Also with all the metadata stored in a file, i added the feature to preview the filename template.
You can turn off using info json if you'd like in advanced settings.

## Icon Picker

You can select the icon and not have it update depending on theme. I am open to custom icons if you'd like, ill add them to the app.
Looking at you decipher :P

## Other stuff

- Fix app closing webview when leaving the app. You have to return from recents and not relaunch the app.
- Add time to the backup filename
- Reversed don't prefer DRC audio to prefer DRC audio preference
- Fix items in navigation rail in landscape being cut off
- Fix youtube playlist link fetching being null
- Add Hausa language
- Add intent for quick download in the share menu. You can enable it in the general settings. This ignores the download card and downloads immediately.
- Consider "creators" field when parsing author in the app
- Add youtube charts support for newpipe home recommendations
- Fix observe sources items not showing properly in light mode

> # 1.8.7 (2025-11)

# What's Changed

## Add QuickJS runtime

Implements yt-dlp's --js-runtimes command to help with many website challenges while fetching data and downloading.
Hopefully with this many of the current issues are resolved

- Upgrade to ffmpeg 7.1.1
- Fix hardcoded user id in path so it supports multiple users
- fix --cache-dir not properly formatted when used as command template
- Add toggle to always update yt-dlp before downloading

> # 1.8.6 (2025-10)

# What's Changed

- Fix app sometimes giving you dubbed audio format instead of original audio
- Fix app crashing sometimes when pasting a link to terminal, also btw u can enable color highlight if disabled, wont crash the app i guess
- Make sponsorblock preference clear by default
- For playlists app now uses --lazy-playlist to load large playlists continuously in pages like newpipe does, its faster
- Enable / Disable certain cookie records instead of all at once
- Add option to write cookie description or label
- Add GIF to video containers
- Don't apply sponsorblock api url if sponsorblock is disabled
- Add --no-check-certificates command when aria2 is applied
- Add embed thumbnail toggle in adjust video chips
- Some video chips are combined into one with hidden dialog menu when clicked to save space
- Add badges on the adjust audio/video chips with hidden menus so u can know how many settings are active on each of them
- Don't apply generated po tokens when cookies are disabled
- remove data sync id fetch from normal cookies webview, only in po token webview
- Add filename template selection in the terminal screen
- Add toggle to not prefer DRC Audio formats
- Add ability to long press the terminate button to show the warning dialog even if u checked to not show it
- Add warning in the download card if selected format is too big (if format size is known, also this is an approximate as format size could change in the final file)
- Removed prefer smallest formats preference and instead u can select if u prefer small formats or large size formats
- fix app deleting custom cache folder when clearing temporary data
- apply proper autonumber metadata for each item when downloading multiple items
- add ability to remove file from mediastore when deleting file
- fix app crashing when sometimes yt-dlp results subtitles as null instead of an array
- Other small details and changes i forgot to note idk

## Advanced changes

- Added toggle to disable --flat-playlist when data fetching
- Add Subs PO Token support when generating po token
- PO Token generation now has two modes, Auth and Non Auth
- Added toggle to use the url user gave to the app as playlist url instead of the playlist_webpage_url tag from the json dump

> # 1.8.5 (2025-07)

# What's Changed

## Bulk Edit items in the Multiple Download Card #817

Now you can select a subset of the items in the multiple download card and apply changes only to them.
- Select items between 2 items
- Write down item indexes to select
- remove items
- Invert selected
- All options in this screen that previously could apply to all items are implemented for this function.

Also if you select a sublist and try to update formats and decide to update in the background, the app will remember the other items you didnt select when you return back to it but only the selected items will have their formats updated of course.

## Other changes:

- App now can show FFmpeg output in the download card to avoid confusion on cases where it might appear as if the download is stuck but its running a ffmpeg subprocess
- App can now downgrade to stable when in beta. You will get a popup to update to the prev stable release if you installed from github and had use beta disabled
- Fixed app crashing if yt-dlp sources in preferences is badly written
- Fixed app having multiple columns for format items instead of just one #800
- Clear results when changing home recommendation source to avoid confusion
- Fix app passing --cache-dir twice in the command
- Remove buffer time in cut player, could help it load faster
- Fix app not consistently preferring codec in a format
- Prevent app from showing 'null' in author field if its not available #814
- Change text to be Sentence Case #793
- Add feature to convert audio bitrate up to 320kbps. * Even though it doesnt really increase quality many people requested it so here you go
- Added feature to show available subtitles a video has when selecting subtitle languages
- Made default subtitle format default instead of srt
- Fix app not properly putting data fetching extra commands in some cases
- Added "system" option in language selection so it follows the system language always
- Fixed app crashing when putting unreal numbers in cut section
- Hid download all and clipboard chip when selecting items to avoid confusion
- Added separate icons for success, running and failed downloads
- Used  ^.*$ for replace-in-metadata
- Groupped finished, running, errored notifications
- Added subs in po token creator screen
- Fix download failing when title has forbidden characters #847
- Fix titles doubling up #835
- Add option to reverse items in the multiple download card
- Made app not restart when restoring backup
- Fix app not removing items that you redownload from cancelled/erroret etc

## For advanced users:

For anyone who is using intent arguments in YTDLnis for automation, the COMMAND argument has been removed. This is due to a found security vulnerability by SonarPaul where user information and app integrity could be compromised. Read the pdf for more information in the YTDLnis Updates Telegram Channel.

If you were using that argument for extra commands with audio/video downloads, you could make a command template about it and use it as extra command and also add a url regex if you want.


> # 1.8.4 (2025-04)

# What's Changed

## Added ability to set custom url for home recommendations

You can technically set anything like this and yt-dlp will process it just as how it can if you search it in the home screen.

## Formats list screen is reworked. Now it will open instantly and wont be prone to crashes and lag

- Fixed app not continuously starting queued items
- Fixed app not properly cancelling all downloads when cancel all is pressed
- Fixed app not properly showing yt-dlp version sometimes in updating settings
- Fixed app crashing sometimes when restoring settings with po token config
- Added get data sync id in the po token webview screen
- Added ability to set a custom youtube url when generating po tokens
- Fixed container in observe sources item not saving if its chosen as "Default"
- Prevented --no-cache-dir even though user set --cache-dir in extra commands
- Fixed bottom app bar in multiple download card not showing properly when using old navigation bar
- Other small visual bug fixes

> # 1.8.3 (2025-03)

# What's Changed

## Automatically generate WEB PO Tokens & Visitor Data inside the app

Since youtube is enforcing a policy that sessions need po tokens, you might need them to appear more legitimate and not get errors like 403, or even unlock formats that need po tokens.

With some references from LibreTube and OuterTune, the app now has a po token generator using it's WebView.
These records are stored in the apps preferences. In advanced settings now there is a toggle to enable the use of the tokens in yt-dlp.

Some formats need PO Token to show up like HIGH AUDIO, you will need to log in with cookies and then generate the tokens. There is also the option to use visitor data but you need to disable cookies. 
yt-dlp doesn't recommend using it but the option is there if u need it.

The app will apply these settings as youtube extractor arguments
youtube:player_client=web;po_token=web.gvs+GVS_TOKEN,web.player+PLAYER_TOKEN;player-skip=webpage,configs;visitor_data=VISITOR_DATA
If you have po tokens set up with web client with po token, there might be duplication happening, so check that out.
Also since these tokens work for any web client, there is also an option to select which web clients to include as extractor arguments.

Other stuff
- Cut Section has been reworked,thanks to madmini. Now you can cut down to milliseconds.
- Added safeBrowsingEnabled in WebView for generating cookies for devices of API 26 and above. Thought to generate incognito cookies that last longer
- Added feature to reset all settings belonging to a certain preference page
- Added option to turn off the code color highlighter
- Fixed app not applying prefer small sized formats. It should've been last in format sorting not first.
- Added ability to enable automatic backup when the app checks for new update and finds one
- Added write-subs and write-auto-subs and --compat-options no-keep-subs when the user embeds subs but doesnt want to save them so he can get more subtitles to embed
- Turned the changelog dialog to a separate screen for better visibility
- Added --external-downloader-args aria2c:"--check-certificate=false" to fix aria2 not working
- Added bitrate info for audio formats card in the download card
- Some small bug fix here and there

> # 1.8.2.2 (2025-02)

# What's Changed

- Hot fixes with format sorting in GUI
- Got those leftover translations

> # 1.8.2.1 (2025-02)

# What's Changed

- Fixed downloads crashing on items with titles that have quotes in them
- Removed custom format sorting by default to avoid common issues with format selection, and moved the preferences to advanced settings. Most of users don't need to customize sorting formats anyway :)
- Fixed app sometimes building -f as bv/bv+ba/b instead of bv+ba/bv/b
- Update WorkManager library, hopefully fixing #720
- Looks like adding preferred language in -S is useless so i added it as part of ba[lang^=...]
- Other small things

## Note

The alternate downloader Aria2c is broken, so turn it off for the time being until something is found out how to fix it/

Hopefully this build is stable for the time being!


> # 1.8.2 (2025-02)

## Note
This version has database updates. It's very unlikely you will face issues but make sure to make a backup of the app's data before updating to this version.

# What's Changed

- Added File Size in Format Importance Order Preference
- Made Format Importance Order applicable to format sorting in the command building process
- Fixed The app doesn't update yt-dlp past to 2024.11.18 #659
- Fixed Starting redownload of thousands of songs at once crashed the app. #661
- Fixed app not deselecting when selecting all in download history
- Added ability to customize the filename template when cutting an item.
- Since WAV cant embed thumbnails and crashes the download, made it disabled when you select that container
- Fixed app freezing when selecting "Also Download as Audio"
- Added a progressbar to the download log when the download is running
- Added more descriptions to the Observe Sources configuration card to clear up confusion
- Added long press to audio,video buttons in the result item details card
- Added a custom cache directory preference
- Removed the -P command in command/terminal downloads if the user has written it himself
- Added preference to skip checking certificates
- #680 removed the hardcoded trimming of author and title
- Added ability to quickly fetch cookies after a failed data fetch in the home screen, through the error dialog and then try again to fetch data
- Fixed app sometimes putting empty extractor arguments for youtube downloads
- Added livestream settings for video downloads, Wait for Video and ability to download the live from the start. Also set up how often to retry the download if the live is not available
- Added option to prefer container instead of codec for audio downloads. this will ignore codec preference in format sorting and only apply container preference
- made add cookies dialog a bottom sheet to make the cookie text more visible
- added ability to set playlist name as album name when enabling "Use Playlist name as Album Metadata" in the settings
- added ability to backup logs as .txt file
- organized backups to their respective folders inside the YTDLnis folder. This includes logs, settings backups, and cookie exports

# Po Tokens & Player Clients

Now you can set up configurations for many youtube player clients and their respective GVS & Player PO Tokens.
Also if you want certain PO Tokens to apply only for a certain youtube url, like youtube music you can do that with URL Regex. 
These configurations will always be used, both in data fetching and in the download process

# Preferred Command Templates & URL Matching

Now command templates are more powerful than ever. The preferred command template is now reset but now you can set limitless preferred command templates. 
Along with that, you can now set URL Matching with a regex. You can set a preferred template for youtube downloads and one for reddit etc etc.
The same logic applies to command templates that are set as extra commands and as data fetching extra commands.

If you have multiple preferred command templates that dont have url regex setup, then one of them will be selected for command downloads.
If you have multiple command templates as extra commands without url regex, they will be added to any download.


> # 1.8.1.2 (2024-12)

# What's Changed

- Extra Command bug fixes
- Added ability to quickly download all saved items from the top menu
- Made app delete bad info json file if it failed with JSONDecodeError
- Sorted the subtitles in the subtitle selection dialog, and they show which ones are selected correctly
- Prevented app from calling yt-dlp every time on updating settings to check version

Hopefully this release is stable enough since there will be some few changes in the next version

> # 1.8.1.1 (2024-12)

# What's Changed

- Fixed some preferences having wrong description
- Fixed app not matching vp9 formats sometimes
- Fixed command templates order icon 
- Fixed app crashing when going landscape on large tablets
- Player Client preference is now reset so its empty by default. If you had modified it, please set it again :)
- Added ability for the app to also move the write info json files along with the video if the user requests it as extra command
- Made the re-download button always show the download card for error downloads 
- Used format sorting for selecting worst format instead of wv or wa

> # 1.8.1 (2024-11)

# What's Changed

- Implemented pagination in the History screen to help with large lists
- Added delete all for each page in the download queue screen
- Added accessing Terminal from the shortcuts menu of the app
- Made the download notifications grouped together
- Fixed app crashing when pressing the log of a download but the log has been deleted
- Fixed app crashing when queueing long list of items in the download queue
- Added ability to mass re-download items from the history screen
- Made the app remember the last used scheduled time so it can suggest you that time for the next download
- #618, made all preferences with a description show their values
- Fixed bug that prevented app from loading all urls from text file
- Added ability to stop an observed source but not delete it

## Custom yt-dlp source

Now you can create your own yt-dlp source the app can update to. 
The app is running yt-dlp --update-to user/repo@latest where user/repo is provided by you through the app. The default 3 sources are still there ofc.

# Advanced Settings

Added a new category for more advanced users and moved the extractor argument settings there.
- Added ability to make command templates usable while fetching data in the home screen. They will be appended to the data fetching command as an extra command in the end. Enable the toggle in the advanced settings to be able to configure your templates for it
- When PO Token is set, the app now adds the web extractor argument for youtube. I forgor...
  (If you want to set more PO tokens for other player clients i guess set them in the other extractor argument text preference, and also modify the player client. The separate PO Token preference applies it only for the web player client)

## Write Info Json

Added ability to write info json when downloading so when you resume and restart downloads, it will not download the webpage and player clients all over again. It will help prevent you making unnecessary calls to the server.
On advanced settings you can turn this off if you want.
I coded it be available for the next 5 hours. Considering formats tend to expire on some extractors, i dont want the download to be unusable. This is a rare use case, one of them being you starting a download, it saves the info json and you cancel it and you remember to start it again, maybe tomorrow lol. Formats most likely expired, download item is useless. So in that case it will redownload the the webpage and player clients again, and it will save a new info file aswell no problem.

## More Home recommendation options

Now you can select to have more youtube feeds in the home screen from yt-dlp 
- watch later playlist
- recommendations
- liked videos
- watch history

Since the option has been overhauled to put every home recommendation option in a single list-view preference, the preference has been reset so u have to change it again if you had changed it


> # 1.8.0 (2024-10)

# Notice

This version has the updated python version 3.11. Due to yt-dlp's deprecation of python 3.8, this update had to be made, but at the same time support for Android 6 will be dropped. Android 6 users can still keep using the previous version until yt-dlp stops working in the future.

# What's Changed

- Re-added individual pause button for downloads
- Added ability to set preferred command template
- Added PO Token setting for youtube extractor arguments
- Added customizable client for youtube extractor arguments
- Added preference to add extra youtube extractor arguments
- Added a header on cancelled/errored/scheduled/saved tabs to show the count and a dropdown menu to delete and copy urls instead of using the main queue screen menu
- Fixed app not starting scheduled downloads with the resume button if there were no paused downloads, weird ik
- Added Socket Timeout in Download Settings
- Fixed app not adding extra commands in the very end of the command list
- Fixed #580
- Small fixes on the home screen channel tab filters
- Fixed app crashing when pressing the thumbnail icon in the result card details. The icon is hidden if there is no thumbnail
- Watch Videos playlist type link is supported for data fetching and downloading. 
- Added ability to parse the "artists" tag when fetching data for ytm

> # 1.7.9.2 (2024-09)

# What's Changed

- newpipe extractor general fixes
- fixed newpipe formats not working
- removed piped because its more annoying than anything, it doesnt work currently
  *shortest changelog wr*


> # 1.7.9.1 (2024-09)

# What's Changed

## Newpipe Extractor

Since Piped is currently broken and unusable, i implemented the NEWPIPE EXTRACTOR to replace it. This includes:
- video queries
- playlist queries
- trending
- searching
- youtube channel queries
- formats
The speed is as fast as piped and seems to be working.
Now you can select between piped, newpipe or strictly rely on yt-dlp

## Home screen filtering

This is a feature request that is long overdue. When implementing newpipe extractor i also coded in proper youtube channel parsing. Now items have their respective channel playlist name. So the app will show you filter chips if you have multiple playlist available.
So you can filter between videos, shorts, livestreams and choose to just download those by long pressing etc etc.
In case newpipe fails and it defaults to yt-dlp, proper channel playlist parsing is not yet possible.
You can track the issue i have created here:
https://github.com/yt-dlp/yt-dlp/issues/10827

## Other fixes

- Fixed app selecting all playlist items even though user selected a few, if the user is parsing an instagram post. (they all had the same url)
- Fixed app not changing container label in the multiple download card when switching download type
- Fixed app not being able to return to home after clicking the download notification
- Added ability for the app to parse multiple urls at the same time. For now it will do 10 items at max
- Added ability to show "Continue Anyway" in the error dialog so it will keep showing the download card in case you want to save it for later or schedule it
- Added general subtitle variant codes with .*
- Fixed app not applying the subtitle language label in the subtitle configuration dialog
- Fixed app not showing the total format size when being in audio download type in the multiple downlod card
- Fixed app not navigating to the download queue fragment to adjust the errored download? Couldnt reprod but reworked logic lmk
- Fixed app still using original title for metadata even though the user changed it in the textfield
- Slight fixes for artist parsing for ytm videos
- Improved and simplified uploader parsing when quick downloading
- Ignored the recode command when using avi container, because it doesnt support it
- Prevented app selecting bad formats 233 and 234 when quick downloading
- Fixed app not showing best quality and worst quality labels depending on the user language
- Fixed app crashing when selecting formats of an instagram post with the same url
- Added approx filesize in yt-dlp formats, somehow the app was not adding it lol
- Added ability to remember text wrapping in download logs and terminal
- if quick download enabled, added ability to show the download card while sharing the txt file on any download type
- hide download size when enabling cutting because the size is unknown

## Note

Some people are asking for the ability to not use cache while downloading in usb storage. This is an android limitation. Even though you might have all files access, android still doesnt consider usb storage as main storage and cant directly write to it. So i have to use caching and then transfer the file.
If anyone is knowledgeable to pull this off, dm me on telegram and we can make it happen. :)

Also for people cutting really long videos and its slow. Its a yt-dlp / ffmpeg issue of how they handle that, so it can't be helped. I guess its best to just download it fully and cut it somewhere else.

I could've released this sooner but i have been busy :/

> # 1.7.9 (2024-07)

# What's Changed

- Fix App crashing with exception "com.deniscerri.ytdl.database.viewmodel.DownloadViewModel.checkIfAllProcessingItemsHaveSameType(Unknown Source:21)..."
- Added option in download settings to use AlarmManager instead of WorkManager for scheduled downloads to improve accuracy
- When updating formats for multiple items, now the app remembers what items don't need new formats and skips them to save time and data
- Moved format source from the bottom of the format list to the filter bottom sheet
- Added ability for the app to update the result items formats when you update the download item formats if there are any results with the same url
- Moved "In Queue" screen back in its separate tab due to recyclerview performance issues
- Format updating in multiple download card now applies updates immediately on each item
- Deleted saved downloads after a format update in the background after the user decided to open for review
- App now shows the actual predicted filesize along with the audio size when downloading videos in the multiple download card. Also in single download mode when the audio size is known but the video size is unknown the app wont show the audio size only, it wont show anything
- Made snackbar show up above the copy log button
- Fixed App crashing when updating fragments in the download card while in the Share Activity
- Added a setting to use the video url instead of the playlist url to download. The app used playlist url and then indexes to get the video to take advantage of playlist metadata. Some users have been using libretube playlists that cant be recognised properly by yt-dlp. Turn this option if this affects you
- Added Write URI Permission when opening files
- Fixed App still updating formats even though the user closed the format bottom sheet
- Fixed App keeping old video titles in the download card #526
- Refactored code on how to fetch player url and chapters from yt-dlp in the cut bottom sheet
- Added ability to change the theme accent without restarting the app. If you change light/dark mode the app is supposed to restart because the icon activity changes aswell
- Prevented AVI and FLV containers from embedding thumbnails
- Fixed Subs Language preference not being saved in the download card
- Added player_client=default,mediaconnect,android extractor args when data fetching and downloading to use more formats
- Turned some metadata into digits
-> --parse-metadata "%(track_number,playlist_index)d:(?P<track_number>\d+)" --parse-metadata "%(dscrptn_year,release_year,release_date>%Y,upload_date>%Y)s:(?P<meta_date>\d+)"

- Removed NA from meta album artist --parse-metadata "%(artist,uploader|)s:^(?P<meta_album_artist>[^,]*)"
- Added --recode-video toggle in the settings to use instead of --merge-output-format
- Also added that preference as a button in the adjust video in the download card
- Adjusted the "Adjust video" section in the download card and bundled some common items together to save space
- Fixed app not selecting an audio format to show in the format view in the download card after updating formats
- Fixed app showing when the download will start up when you queue up a scheduled download
- Made home recyclerview bottom padding bigger to avoid the FABs
- Fixed Issue #529
- App now remembers the scroll position in the download history tab
- Made piped api not fetch data when fed mix playlists
- Turned the incognito toast to a snackbar in the download card
- Fixed duplicate dialog crashing app when in share activity
- Fixed issue of app not showing audio formats of preferred language at the top when selecting the suggested filter
- Added a feature to delete cancelled downloads, errored downloads and download cache older than a certain period. You can select daily, weekly, monthly.
- Added ability to change the container in the multiple download card
- Fixed App issues with download logs
- Added preferred language in the format sorter in yt-dlp
- More other small changes i forgor what i fixed

> # 1.7.8 (2024-07)

## Incognito right in the download card
Now you can enable incognito on the go and prevent the download from creating a history record of it when its finished. If incognito mode is on, it will be enabled by default

## Observe Sources Sync
Now you can enable sync in observe sources when the app will match every video with what it has downloaded so far. So that if you delete a record from the playlist, the app will do the same

## Customizable Home Screen for Mobile
Now you can select which screens you can show in the bottom navigation. Home and More are essential so they cant be removed.
Available screens: Home, Downloads, Queue, Terminal, More
You can also select the label visibility of the bottom navigation

## Active and Queued Combined
Instead of swiping to see the queued items, now they are in the same screen when queued are right below the active items

- Prevented the user from spamming pause and resume making the app confused in such a short period of time
- Re-implemented format importance sorting in the app, resembling the yt-dlp one
- Added feature to keep screen on while the video plays in the cut player
- Fixed app not adding a fallback to bestaudio when downloading audio formats using a format id
- Made the app limiting titles to 180 characters instead of 120
- Hid free space label in command mode if the app cant read the folder
- Fixed app still using the cache folder when the app has all files access but the folder didn't exist
- Added ability to swap format source from piped to yt-dlp if the item is a youtube video
- Added a feature to choose if you want to restore the backup or erase everything from the app and start fresh with just the saved data
- Moved some youtube-focused settings to their own category in the general settings
- Other small things here and there

> # 1.7.7 (2024-06)

## Shortest changelog ever

- Fixed multiple download card issues. Processing them in the background was not a good idea for most users as it was confusing and users thought it wasnt doing anything. It led to duplicate downloads
Usually playlist downloads dont really have 4000 items in them so background. If you have one, just enable quick download and consider the whole playlist as a single item. :)

- Some users had issues with the display over apps perm. Since that perm ended up not doing anything to the revanced issue, i removed it

Had to push this out since multiple download card was almost broken

> # 1.7.6 (2024-06)

## What's New

- Made finished notification icon color to red
- Added download path in the history item details sheet
- Fixed Download On Schedule feature. Switched back to AlarmManager as its more accurate than WorkManager
- Removed move to top and to bottom in the scheduled tab. Code cleanup
- Fixed a huge issue when you press cancel to the quick download item before the data isn't fully fetched, it would go back to being queued after the data fetching is finally done
- moved force keyframes at cuts in the general category in processing settings
- showed the number when you select all items in the context menu bar
- fixed app going always to the search bar when you return to home if you launched the search shortcut from the app menu
- Fixed app not hiding update formats button after updating formats
- Fixed app not passing ba/b in format logic when using generic best audio format
- Fixed meta track parse metadata
- Fixed app not cancelling downloads in some cases
- Fixed huge issue when using multiple download card and it wouldnt keep user defined changes
- Fixed app not removing download notification when cancelling download
- Fixed some errors in arabic interface when selecting playlist items
- Limited characters in cut interface textboxes to just numbers colons and periods
- When user had a defined preferred format id and the user selects a generic 'best' format the app just accounted that format id and didnt consider the preferred video resolution as a fallback
- Fixed when trying to schedule a video download and setting it to download as audio as well, the audio is put in queued and not in scheduled
- Fixed app crashing when trying to tap adjust download in the errored notification, in case the user removed the download beforehand
- Added ability to modify the download item in the queue. But to prevent the app from starting the download prematurely, it is moved to the saved category and after you hit ok it will be requeued. If you cancel the edit card, the item will stay in saved
- Fixed app reseting user defined changes in the download card like the download path when it finally updates data
- Fixed app crashing when trying to open a download that has no duration in it. Now its disabled
- Previously the app would change the state of downloads to Paused, whether it is activepaused or queuedpaused. Since the app now simply pauses all downloads, there is no need to do such transformations especially when you could have many items in the queue. Now simply the app cancels the ytdlp instances and the download worker and stores a state whether the downloads are paused or not
- Fixed album playlist track not being saved  #482
- Fixed retries and fragment retries settings not applying
- Fixed container in RTL not showing correctly in video tab in the download card
- Fixed bad spacing in RTL for search engines
- Fixed app crashing when you multiple select cancelled downloads for redownload
- Removed the ability of the app to rename extractors in the history tab. Now they are raw from yt-dlp
- Changelog was too slow and buggy, so now its on a recyclerview ultra fast
- Fixed the case when you pasted multiple links in the home section and only one item would be registered
- Fixed observe sources not downloading new uploads when checked as such
- On select playlist items the end textbox was not purely numeric. fixed
- Fixed sub format conversion not applying when embedding subtitles
- added scheduled as a backup category in teh settings
- added ability to show cookie details
- fixed app crash in some cases when u copied multiple url and tapped the FAB in home screen
- removed excessive file permissions for API 30 and above

## Format aspect importance

Now in the settings you can order around which element based on your preferences like codec, container, quality, preferred format id should be prioritised when the app automatically chooses a format for you. The app uses a weigh system to sort formats based on preference.

- fixed app crashing when swipe to delete a scheduled item
- fixed app not modifying time in a scheduled item
- added network constraint for observe sources worker
- added reconfigure button in errored notification
- fixed app not hiding the "Link you copied" after clicking on it
- fixed app crashing when trying to toast in the end (#469)
- added alternate urls for instagram,facebook,and reddit as supported sites
- fixed app not escaping double quotes in filenames
- fixed container text alignment for arabic interface
- rearranged video quality order in descending order in the settings


## Background processing of items for download

Someone tested to download a 4000 long playlist in the app. First of all it couldnt even load them in the multiple download card. In those extreme cases the usual recommendation was to just turn quick download on, and consider the whole playlist as a single item and download.

But still, if a madman wants the ability to modify the list, now the app can handle it and will show the download card. Since it will take a while until all items all processed and converted into download items, a progress bar is added.
Still you are able to hit download and schedule button early and let the app continue on its own by using the default configurations of the download items. So its fast like quick download but still separates all items into separate downloads.

- fixed app using -a "" on observe sources command type downloads
- added milliseconds when trying to cut a download
- added ability to set the folder location of your download archive path. The file has to be named as download_archive.txt
- added feature to number each chapter when using split by chapters
- slight changes to downloads already exist dialog
- fixed app not considering m.youtube links
- other small fixes

> # 1.7.5 (2024-04)

## What's New

- Added AVI / FLV / MOV video container options
- Fixed app not showing the current download's command if it was a command type download when coming from multiple download sheet
- Added release year from description as metadata for audio downloads
- Set progress bar as interterminate when it reached 100% so people wont think it froze (its ffmpeg working, and it doesnt have a progress callback)
- Fix app crashing when trying to toggle on Show Terminal in share sheet
- Fix app crashing when going on landscape when you open the format list
- Fixed app not disabling the keep fragments toggle if you toggled the dont download as fragments
- Fixed app not fetching format list when some formats had None as a filesize
- Fixed app only showing the download type icon in the finished notification only when you expanded it. Now its a popping orange
- Showed navigation bar color when opening the download card
- Showed stylized codec name for generic audio formats
- Fixed app crashing when going on landscape when you open the multiple download card
- Fixed app crashing when you tapped the home button twice on foldable mode
- Fixed observe sources spamming and running every second
- Swapped around some icons
- Added new Language BULGARIAN
- Showed the download path in the finished notification
- Fixed calculating the next time observe sources should run
- Added a scheduled section in the download queue so that they dont stay in the same spot with queued items that are expected to run soon. You can see their ETA there for each item


## Duplicate checking

For a while the app had its own duplicate checking system when it checked the whole configuration with current history items or active downloads.
Since this system was too precise and even a slight change in options will consider it a new download and not an exact replica and most people were confused why the app allowed them to still download
So i created 3 methods of duplicate checking

- download archive -> uses the .txt file of yt-dlp to check if any url has been downloaded before or not
- url and type -> checks download history and running downloads to check if any item with the same url and type was downloaded
- full config -> the good ol method

----

- Removed paused button for each active download item. It didnt make sense. If you paused one item, the other items will continue to run anyway so what was the point. Instead i added a floating action button to pause and resume the whole queue
- Removed the cookie toggle throbbing every time you entered the page
- Slight changes to album_arist metadata parsing
- Fixed app downloading music file instead when using M4A music format
- Fixed app showing the grey deleted filter on present items


## Reordering download queue items

Now you can toggle the drag and drop mode in the queued tab to reorder your items with ease. Also you can now move multiple items to the top and to the bottom of the queue. or a single item

-------

- removed the plus icon in the piped instance dialog.
- combined the thumbnail cropper and resizer commands in audio downloads
- fixed app not removing audio on some pre-merged formats like in tiktok or instagram
- removed the -f bv[height<=...] and instead moved to -S res:... due to some problems when trying to quick download an item
- fix terminal sometimes not showing the finishing line of output or error
- added colors to app shortcut icons

> # 1.7.4 (2024-03)

## What's New

- Added select between two items in home screen
- Fixed select between in cancelled screen not working
- Fixed app crashing when you selected a generic format for multiple items at once
- Fixed graphical glitch on RTL screens when the navigation bar showed up in landscape when moving tabs in download queue
- Moved the cache location to the data folder so its accessible to everyone. If for some reason the app cant read that folder, it will use the old cache location
- Fixed app not converting thumbnail to png even if set so
- Slightly changed the thumbnail cropping command since the png thumbnail commands got added
- Fixed app sometimes not closing the share activity when you dismiss the multiple download card
- Fixed app not enabling sub languages chip when you only selected save auto subtitles
- Added the title of the finished notification to the bottom if you want to see the full one
- Added a toggle in the settings to disable downloading the file as a fragment
- Slightly changed the counter badge offset in the download queue
- Fixed app not checking save auto subtitles when you get back to it
- Added indeterminate progress bar for running downloads notification if its in the beginning
- Reverted cookies functionality since it was broken for people. It will always sign you out when you generate the cookie


### Added supported web addresses to the app

- you can make this app be the default launcher for some website links
currently the sites supported are the major yt-dlp extractors:
facebook
twitter
instagram
youtube
soundcloud
tiktok
bilibili
twitch

- Fixed huge bug of app sometimes showing the title of the previous video in the home screen
- Added select between two items in history screen
- Added select between two items in command templates screen
- Added select between two items when choosing playlist items
- Fixed app sometimes not downloading suggested chapters from the cut screen
- Added "EVERY HOUR" in the observe sources screen

### Added shortcuts when long pressing the app icon

- I added 4 actions
Search
Downloads
Download Queue
New Command Template

- Added big icon to the finished download notification showing the download type
- Added a new permission to make the app be always on top. This slightly helps with the revanced pip issue. The download hook button sometimes sends you to pip but through the share menu its fixed. Not sure how to resolve this. will look further. If anyone has a solution, let me know
- Fixed app downloading the original language of a video even though you chose another language. This was a bug with piped formats format id parsing
- Fixed app not cancelling updating data of an item in the download card
- Added a preference to prefer smaller sized formats
- Added ability to choose another audio format in the video tab but not reset the video format
- Sometimes the preferred resolution is not available, and the video defaulted to the maximum resolution when it should select the next higher quality.
- Now formats that already have audio formats in them show the audio chip in the format list
- Added an internal crash listener for the app. If the app crashes, it creates a crash log for you with the error stacktrace
- Added ability to have saved filename templates. You can add them straight from the filename template dialog. They will also show up at the top of the list
- fixed app sometimes not selecting preferred format based on format id even if it exists
- fixed app not matching preferred vp9 codec formats


### Observe sources table had some changes and it was recreated

If you have any observe sources running, try recreating them manually. Backup and restore wont work as the structure is different

- Added parse metadata commands for audio type to fetch the release year if the description has it
- Removed alarm manager and instead relied in workmanager for scheduled downloads due to android 14 restrictions

> # 1.7.3.1 (2024-03)

## What's New

- Added ability to select all items between two selected items in download queue
- Fixed app only getting one URL when tapping "Link you copied" in search view even if you copied multiple links
- Fixed app crashing on playlist selector when sharing a playlist. Forgor to configure it with the new changes
- Fixed app not removing errored, cancelled, saved downloads after you queued them for re-download
- Added option to copy URLs of selected downloads in the download queue screen
- Made app show all finished and errored notifications, instead of the latest one
- Kinda fixed the app not giving you the correct path when sharing a file from the notification
- Fixed app crashing when loading soundcloud results. They had stupidly large thumbnails, they are resized now
- Added option to reset the recorded links in observe resources when you are trying to update it. From now on they wont get reset like before
- Fixed major bug of app adding extra quotes in the yt-dlp config, making it fail on titles with quotes in them

Had to push this soon because of mostly the last error

> # 1.7.3  (2024-02)

## What's New

- Added names to subtitle codes in the subtitle selector dialog
- To prevent filename templates the app automatically adds .170B in the title tag and .30B in the uploader tag if they are written as the default template, otherwise they wont be tampered with
- Fixed app taking a bit before scheduling a download when it was quick downloaded
- Fixed app not updating the item data after being scheduled
- Fixed new cookie items having the previous' cookie data in them
- Fixed app not updating playlist data thumbnail while downloading
- Added a note in the auto-updater that the apks come from the github repo
- Added an option to disable swipe gestures in the download card between audio,video,command
- While the app is updating data inside the download card and the user taps the cut button, instead of asking the user to update now it knows its updating and just tells him to wait a bit
- When clearing results in the home screen, the running query job is cancelled
- When creating a new cookie, hid the copy cookie button (duh!)
- Replaced some icons around the app
- Now if you have copied multiple links back to back the app will know that and will populate the links in the search view. You can then remove or add more and then hit search
- Added a feature to show the item count for each tab in the download queue screen
- Added a feature to also download the current video item as audio. Just so you know, whatever changes you made in the audio tab will be used for this audio download
- When fetching playlist data, check if there are duplicate records in it
- Made the video and audio titles more noticeable in the format selection card
- Fixed app not updating formats while being in incognito mode in the multiple download card
- Added content description for many buttons in the app to help people with accessibility problems or people who use TalkBack
- Added a feature to only check for future videos in observe sources

> # 1.7.2 (2024-02)

## What's New

- Increased the height of the history card a slight bit
- Fixed app not using the -S format sorter in normal GUI downloads
- Added the multiple download card as an option to disable the swipe gestures
- Added a recyclerview to the search results because they were buggy before and would not show sometimes
- Added a new history item card design in cases if you quick-downloaded a playlist or if you made multiple cuts of a file. Instead of being separate, they are bundled and you can share them all at once and if you want to open the file a new dialog will show telling you to choose which one. If any of them is deleted, they are greyed out
- Fixed the shortcuts behaviour by allowing duplicate shortcuts to be added to textbox and to not check

# Format Filtering

App can now let you filter between 4 categories.
All, self explanatory.
Suggested, a combination of multiple preferences you have set in the app like your preferred codec, container and format id, quality and resolution.
Smallest, it will select for each format the smallest size.
Generic, the generic formats you see on the app while an item hasn't updated to real formats yet, in case you want to use those for some reason.

...

- Added clipboard button right in the home screen for quick access to the url you copied instead of going to the searchview. That item is also available there aswell.
- Added an animation of it shrinking when it pops up
- Added ability to show the URL in a history item if the download produced no title for it
- Same idea with the download notification if you hit download too fast and the data fetching process hadn't finished yet.
- Added zoom-in zoom-out slider for logs and terminal screens. It can remember your zoom choice for both
- Added more options of data fetching for titles and authors, like track, alt_title to avoid the default youtube naming scheme when available. Useful for audio downloads
- Since some people are confused. In the download card each tab item is independent and they each create a distinct download item and when you press download the app picks from the tab you are currently. If you want to select the audio format in the video tab you can just open the format list and scroll to the bottom for the audio formats. You can select multiple if you want and then press ok. But since some people thought that selecting the audio in audio tab would apply to the video tab, i added the ability to synchronise that format automatically in case people dont know about this lol
- Added the snackbar notification when there are no current command templates when trying to edit an existing download item
- Fixed that snackbar to fill the full text
- Fixed app crashing when trying to change the directory of multiple items in the multiple download card
- Fixed cases where the app downloaded a playlist but items had mismatching titles. This was due to playlist index being different from piped API to yt-dlp. Added filtering by ID instead which is fool-proof

# Introducing Observe Sources

A long awaited feature that was planned for a long time, but always pushed back by other features.
You can now set a playlist to be observed by the app over and over based on a time period.
The download configuration is the exact same as if you would do a normal download. You can select types of audio video and command etc etc.
You can set it to run every day, every week, every month. or every 2 days and so on.
You can set the time the app should try to check for new entries to download.
You can set the time the app will start this observation process, in case you dont want it to start now.
You can set the case where the app should stop running it. Like after 10 runs, or after it reaches a certain date.
The item can store which url's it had registered and passed over to the download process, so it can skip them on the next run.
But you can set it to check the app's internal download history in case those files are still present. If not, the app will try to re-download them again.
You can make as much as you want :), but since its running on Alarm Manager its waking up the phone to run the task so be wary of battery usage if you have way too many of them.


...

- added pop-up animations for all lists on the app
- added ability to copy cookie content for a single cookie instead of just all of em
- added a chip filter on the history fragment to hide the deleted records
- added force ipv4 feature
- added bitrate format information for formats long press details card
- made the quick scroll handle rounder
- fixed app still trying to fetch data even though the user cancelled it
- added the ability to show the multiple download card in cancelled/errored/saved downloads when trying to redownload them. If the card is disabled in settings it will act like previous, instant downlaod
- added ability to copy urls for each download tab items
- added a chip option in the video tab to download automatic subtitles. its also an app setting
- fixed a strange bug when the app would consider some video formats as audio formats
- ellipsised the audio format id in the format section in the download card in case it was stupidly long, (facebook...)
- fixed the app checking for duplicate downloads if you are trying to query multiple downloads, it will show a bottom sheet of all duplicate items
- added ability to use the download archive of yt-dlp instead of using the app's method of duplicate checking. archive checks only the video id but the app checks the whole command config. up to you
- Other small bug fixes

The history item has been updated in this version so old backups will not work. Please make a backup of your stuff in the settings before updating. In case this update fails, notify people in telegram and revert back to the past version and use your backup and also give us the backup to reproduce the error if it happens.
Happy Downloading :)

> # 1.7.1 (2023-12)

## What's New
- added ability to notify user when all the queries are finished processing. Helpful when you push a large txt file with links and dont want to stay in the app. Just dont kill the app, leave it in the background
- fixes in autonumber template for cut files. removed extra spacing
- added ability to update the card if the cut button is disabled due to missing data
- fixed generic format string for worst audio and worst video
- removed output-na-placeholder
- added a scrollbar to the cut bottom sheet in cases where you could make an absurd number of cuts
- removed suggested chips that the user had already chosen to avoid duplication
- fixed app crashing if it failed to update yt-dlp in startup
- fixed app crashing if it failed updating formats in the download card. it will now show a dialog of the error
- fixed changelog being too slow after clicking it
- made the details card fullscreen if you are in landscape
- allowed the user to create a command template on the spot if their command tab is disabled in the download card and send you right to it after creation
- other small fixes


Small update just to fix some bugs :)

> # 1.7.0 (2023-12)

## What's New
- fixed error notification not being dismissed and having a progress bar
- fixed editing filename template not using multiple copies of the same tag and writing at the cursor
- fixed appending search items in the search view not working for links
- fixed terminal removing any instance of yt-dlp in the command instead of just the beginning
- added ability to long press an item in the format details sheet to see the full string, and copy it/strings
- ellipsised really long titles and authors in history/download details bottom sheet
- now u can see all available piped instances in the piped instance dialog for you to choose
- removed really long format command and replaced them with -S format sorting
- fixed app not hiding adjust templates if user unchecked it
- added ability to show the command that was used in a history item, u can also see that in a queued,cancelled ... download
- Implemented preferred Audio Language. App will automatically choose an audio with your preference if it can find it, both in the download card also if you quick downloaded it
- added subtitle language codes suggestions in the settings page
- made the extractor chips in history page Sentence case
- added a changelog screen where you can see recent releases and you can download the apks from it too
- prevented app from crashing when trying to backup from a corrupted backup
- added uploader_id as fallback for author data fetching in yt-dlp in case others are empty
- fixed null pointer exception when running the update multiple items formats worker
- added the seconds where the cut starts on downloads with cuts in them
- made autonumbers be normal numbers instead of being 5 digits
- fixed filename templates in cut files and added the index in the beginning and fixed bugs if the users left the template as empty
- added 240p as a generic format

## Intent integration with the app
You can use intents or apps like tasker or macrodroid to run commands to the app to run a download without user interaction
Accepted variables:

TYPE -> it can be: audio,video,command
BACKGROUND -> whether the app downloads on its own and wont show the download card if you have it on
The intent that needs to be created is of android.intent.action.SEND and the intent text should be the url that you need to download.

> # 1.6.9 (2023-11)

## What's New
- Errored downloads sometimes had no title if it was quick downloaded. showed url instead
- Fixed logs not being highlightable
- Fixed app crashing when moving to landscape when having download card on
- same thing for the details card
- Fixed app crashing if you pressed download before data is loaded
- Fixed app going to main activity when using rvx
- Fixed errored downloads log button crashing the app
- added hungarian
- added serbian
- added ability to enable/disable swipe gestures on any screen
- added ability to choose whether extra command applies to audio video or both
- hide search providers if the user has typed out an url in the searchview
- fixed log removing some lines
- added MASTER channel in yt-dlp updates
- made errored downloads as a separate notification channel
- fix notification language conflict for portugal brasil in worker notification
- kept state of download card when going in landscape, even while updating data
- add crop thumbnail to adjust audio preferences
- fix command templates creation card showing extra command checkboxes even though extra command is disabled
- fix preferred audio codec disrupting preferred audio id
- made command tab sync title and author changes in the download item
- fixed app duplicating --download-sections when spamming the extra commands page
- added BUFFER SIZE as a preference in download settings
- fixed prx series search engine not working

> # 1.6.8 (2023-10)

## What's New
- removed trim filenames from command type downloads
- fixed app not showing formats when it has generic formats
- fixed author getting written as NA for kick videos
- added separate channel for the worker notification that users can turn off
- include search history when searching
- removed scroll bug from command tab
- added spacing between command template title and ok button in selection card
- made download progress not dublicate in terminal
- made ability to store terminal state
- added ability to create multiple terminal instances and show them as a list similar to download queue
- fixed thumbnail download not working
- fixed app crashing when clicking on format updated notification
- fixed app crashing when double clicking format on multiple download card
- added custom sponsorblock api preference
- removed contextual app bar when you its enabled and the user taps the log button in the erorred tab
- made app always show quick download card and asynchoronously load data. Quick Download now if its on, it wont load data at all
- added shimmer when loading data in the download card
- fixed app showing no formats if there were no common formats. Now it will give you generic formats
- made open command template list be half the screen, shortcuts third of the screen so the user can see what its being added
- fixed sometimes app slipping queued downloads even though its told to pause all
- fixed trim filenames cutting files too short
- made mediastore scanning of files one by one
- fixed filename template not working in multiple download card
- fixed -F in terminal not being inline
- added preferred audio codec
- made auto update on boot if there are no active downloads
- fixed format text overlapping
- added a new error dialog in cases yt-dlp data fetching in the home screen fails. You can copy the log
- formats auto updating as soon as the download card opens if auto-update is on
- added preferred audio format always in the video tab
- made app post downloads for queue in chunks
- made app always save logs in case it fails, and if succeeds and logs are off it deletes it
- fixed app navigating to home screen when cancelling download card in history screen
- added a button to skip incoming app update so it wont bother you anymore
- fixed settings not restoring some fields
- fixed crunchyroll not working with cookies
- added search for command templates
- added sort filtering for command templates
- added all shortcuts inside filename template creation dialog. Long click them to see the explanation
- added preference to hide elements from the download card
- made avc1 and m4a as preferred codecs for noobs
- u can edit the duplicated download item right from the error dialog or access the history item to view the file
- added extractor args lang when searching in yt-dlp
- removed webarchive search engine since its not supported anymore
- fixed terminal prematurely closing
- made format auto-update on by default for new users
- fixed main activity getting removed from recents when using the app with rvx
- added ability to have the last used command template for the next download
- fixed app crashing in landscape logs
- fixed app constantly going back to home in landscape or config change. now it keeps state
- add subtitle language suggestions in the subtitle dialog
- made command template scroll state hold even if fragment is destroyed
- added slovak sk language
- fixed terminal icon being blank in white mode, and now its red
- fixed share files from notification showing 2 files even though its 1
- fixed history item not being deleted from the bottom sheet
- cleared outdated player urls for stale result items
- added export cookies as file
- added export command templates for selected templates
- added icons for history details sheet chips
- added markdown in the update dialog
- and other random bug fixes


> # 1.6.7 (2023-09)

Beta Releases will be on Github from now on, unless there is a small thing you will find quick builds on telegram
Fixed Terminal not showing shared link
Changed Terminal share logo color
Aded javanese language
Added bengali india language
Fixed app adding bestaudio in format even if remove audio is set
Made app fetch only link parts from sources like pinterest that also include text in front
Added ability to switch to the beta channel for updating. You can also downgrade to the latest release version
Fixed app not restarting when changing language
Added word wrap toggles for logs and terminal
Added Auto Preferred Download Type. It will open as Audio if the site is known to post audio files and video for the rest
Added collected file size in multi download card
Fixed app not selecting best format
Fixed update in beta channel not working
Fixed Youtube Music Playlist not loading
Fixed Sponsorblock not showing as checked in multiple download card #271
Added playlist title and playlist index metadata in playlist items so u can use them in filename templates #272 #270
Added -a path.txt if you share a text file to the app and the preferred download type is command or terminal
Fixed app cancelling workmanager earlier than expected
Added title and author sync between audio and video tabs #241
Fix format details fps in arabic being rtl
Removed h264 from codec
Fixed Search engine not getting updated in home when changing in settings fragment.
Added language preference in android 13 and up
Added multiple selection for command templates
Fix issue when app crashes when creating config files for items with weird titles
Fix auto preferred download type not working correctly in yt music
Add multiline titles of preferences
Fix format item and details sheet on RTL languages
Fix app conflicting format id with a generic format id

> # 1.6.6 (2023-09)

added saved downloads in the settings backup
made icon bigger in downloading notification
fixed notification cancel and pausing not working
fixed app respecting preferred home screen for downloads and more
removed 000001 in the end of the file if the user has made only one cut
removed log id from downloads if the user deleted the log
fixed app crashing when it was listing logs with large texts
fixed case: if the user had multiple preferred audio formats the app would choose the last one and not the first
added ability to combine preferred audio formats by writing like 140+251,250. If the download item finds both first audio formats it will merge them together, otherwise get one of them that is available
fixed app file transfer not working. (had to revert to hidden cache folder as downloads folder wasnt good for some phones)
fixed app saving as mkv even though mp4 is set as container
added shortcuts button to the commands tab so you can drop shortcuts to the current command.
added syntax highlighting in commands tab textbox
fixed tiktok videos not saving properly
fixed open file intent
added hindi language
added croatian language
added norwegian language
added tamil language
added telugu language
added thai language
added readme in azerbajani
added security.md
added ability to show preferred audio / video format ids as a generic format in the format list if the item has no formats available
fixed multi select dialog in the settings not having the same material color as the rest of the system
fixed navbar being black and not the same color as main activity in the settings activity
added license to the app info
added security to the app info
fixed app not converting subtitle formats
fixed app retaining terminal state when going to landscape
made log text stretch horizontally like terminal
more small stuff
Added ability to combine all possible combinations of preferred video formats, audio formats, codex and container.
The app will consider all preferred video ids. For each of them it will consider preferred vcodec and extension. At the same time for each of those elements it will consider all preferred audio commands and/or audio merge. If the format is a generic quality like 1080p or 720p it will also consider that in the long format query. Also the app will add all combos of video + audio only. And all combos of all video + best audio. All video ids alone and best as final fallback. If the user chooses a proper format, the app will not be this descriptive. Preferred video id and audio id combos will only apply on a quick download or best quality generic download
Added Download Scheduling
When on the app will only download depending on the time period you have set
If you turn off scheduling, the app will check for leftover downloads waiting and will immediately start them
If you manually set a time to download, it will ignore the scheduling period. This period only applies to normal downloads that have no set download time
The setting is in the downloading section
Added ability to not use internal storage caching if you have all files access
This is useful if you are downloading livestreams and u dont have to move files from cache in the settings anymore
If u are having write permission errors, turn caching back on so the app will use transferring instead

> # 1.6.5 (2023-08)

App can now be used as share method in Facebook Mod
extracommand preference will be ignored in backups
if you have command as preferred download type, it wont fetch info first even if you are not in quick download mode
if you are in landscape the card doesnt show fully, fixed
cookies import not working, fixed
landscape video player in home screen fits better now
fixed monochome icon being gone
added yt-dlp auto update option in the settings
fixed bilibili videos not working in normal mode
added codec preference h264, h265, av1, avc, vp9
fixed app not enabling ok button in the cut screen if you change the TO text box only
added force keyframes at cuts switch in the cut screen
Added 3 dots in multiple download card. It will have the configure chips in a separate card. Each will have a callback that will update all items in multiple download
fixed start end textboxes showing truncanted text in playlist select card
add ability to start a download now and put the rest of the queue behind it
Scrollbar handle shrinked depending on size. Made it same size
Fixed Tapping the notification of the errored download leading to the running tab instead of errored
Set different icon for terminal in share menu
ignored extra command preference in backups of command templates
made preference titles be multiline if they are too long
fixed cookies import not working from external sources other than the app
fixed app using continue button for a split second when there are no paused downloads
added state in home screen so that it wont populate trending videos while its searching for an item
implemented batch downloads in a single worker to avoid bogging down the system with many work requests
fixed instagram status that have multiple videos just using the first video
fixed bilibili not working in normal mode
fixed app overwriting files instead of adding (1) in the filename instead

> # 1.6.4 (2023-07)

Notice
Its suggested you backup your settings and history in case the app crashes on update, because this is a huge update.


Added Preferred Audio Format ID
fixed korean bad strings
fixed please wait card not showing up
fixed audio format using mp4 containers
added portugese language
added punjabi language
added greek language
fixed container not updating in multi select card
fixed swipe to delete bug in the queued section
added download now option when u highlight only scheduled items in the context menu
tapping on the errored notification will send you to errored tab, if you have logs disabled. If logs on it will send u to the log
added preferred format ordering. By ID, FILESIZE, CONTAINER. Formats grouped by container will also be sorted by filesize
fixed app not showing formats when u try to modify a current download item and its a different type (audio formats not showing if its a video type and vice versa)
fixed app showing generic formats in cases where the format length was the same as that of generic formats (silly mistake)
made the app store static strings for 'best' and 'worst' so that there isnt any confusion when u try to change the language and the stored downloads will have the other language's string
added collected filesize on top of the download queue
potentially fixed app not crashing when going to queued screen? idk
fixed app not moving files when its a fresh install and u havent tried to change the download path.
added download retries options [--retries and --fragment-retries]
made the download logs not freeze the app
download logs will work on quick downloaded items that were later updated
added app arch and build type in the about section
added "force-keyframes-at-cuts" preference
Added download type in logs
fix video not showing in cut screen. This depends on the streaming url
fixed xhamster not showing results
long press download button to save it for later and not schedule it. Also works when u try to queue multiple items
change app icon background based on theme
fixed bug when trying to redownload a history item and wanting to use a different type
made the scheduled item auto-update after its queued so the app doesnt have to update when it begins downloading
some fixes with output templates
fixed container and vcodec being saved with translated strings and not constants
added download thumbnail functionality. Click the result card in the middle and use the video player. Also observe running and queued downloads for that item
added feature to disable thumbnails on certain screens. U can choose of course
added feature to convert subs to different formats
added youtube music search provider
made app name have a color depending on the theme
fixed format card not showing a translated string on best and worst strings
added ability to hide the terminal from the share menu
fixed app killing active downloads when removing a queued item
fixed filename template not showing uploader on odysee
added orange theme (its like yellow but slightly darker)
fixed cookies not getting saved on older droids
fixed app making new folder instead of merging when moving files
added ability to put multiple preferred format ids separated by commas for both audio and video
added support for piped links and treat them like youtube links
fixed bug where if an item has no formats and you update audio formats and then go to video tab it will not show the video formats too
made from and to textboxes wider in the cut video screen depending on the timestamp length
added search engines in the search view
removed filename template override if the user leaves it empty. Now it follows the yt-dlp default
fixed app crashing sometimes when its tablet
added drag handle to scrolling content
added pause functionality [This is somewhat wonky because the YT-DLP python process doesnt finish quick enough. So dont spam pause resume]
made video player faster to load for youtube videos
Added option to turn off usage of cookies
some fixes with the cut screen
Added ability to save command templates as extra commands by default on every downloadcard/AddExtraCommandsDialog
fixed app not saving the proper youtube link and having to refetch data unnecessarily
fixed app scrolling in the tablayout in the download queue screen while you are dragging the vertical scrollbar
fixed app not destroying active downloads when you terminate the app
added rewind button in the cut section
fixed play button appearing in cut section when the video is playing
removed "Downloading" word in the preferences screen. Its Downloads now
Added embed metadata preference. Turning it off will remove any embedding metadata and parsing metadata commands
other small stuff

> # 1.6.3 (2023-06)

Fixed txt file import not working
added ability to select multiple audio streams for a single video
make swipe gestures a preference
crop thumb preference
added support for multiple folders in a single download through filename templates
added code syntax highlighter
moved from invidious to piped for youtube queries

> # 1.6.2 (2023-05)

added pause button in notification
added ability keep cached files when download is finished
added multiple pages to settings
Add backup for history, queued downloads, cancelled downloads, cookies, shortcuts, templates
Add long click format to show details in audio video tab
Implement common format fetching on playlist items from invidious
Invert selected items in playlist bottom sheet
added filename templates as a chip on download card
fixed searchbar not closing on back gesture
small bug fixes

> # 1.6.1 (2023-05)

## Introducing Quick Download Mode
You can launch a download (or card) immediately without fetching data at first.

Added Theme Picker, Accents and High Contrast Mode
Added swipe actions on history,downloads,cookies,templates cards
Added finished download notification with actions
Fixed Tiktok videos not creating result cards
Added active downloads badge in the navigation bar
Added remove audio feature for videos
Added check for present downloads
Added preferred Format ID

> # 1.6.0 (2023-04)

## What's Changed

- Added Restrict Filenames Preference
- Added Shared File from the History Card Details
- Fixed Command Templates Export/Import crashing on older devices
- Improved Boot Up Times
- Added Custom Command Templates, Shortcuts, Folder picker in Terminal Screen
- Added Sponsor Block settings in the download card
- Added Generic formats for audio tab, so that you can update them if you want
- Fixed Custom Command Template not scrolling properly in the download card
- Added Terminate Button with ability to remove the app from recents menu
- Added Navigation Component
- Added Command Chip filter in the History Fragment
- Implemented ability to Cut Videos. (Multiple Cuts can be done)
- Added ability to cut a video based on its internal chapters as well
- Fixed Terminal text jumping
- Added automatic format updates as soon as you click the format section (if the item/s have no formats)
- Added Ignore Battery Optimizations
- Added Range Picker in playlist bottom sheet
- Added ability to stack search queries in the search view and push them all at once (u can mix links with queries or playlists)
- fixed download logs text not being highlightable
- fixed app not deleting some cached files
- fixed filenames being too short
- fixed sometimes error output not being shown in terminal output or large outputs
- added keyboard focus when adding template in the terminal
- added remux-video for audio
- added copy url button similar to history card in the download card
- fixed padding on cards
- fixed configured download sheet not scrolling
- Added contextual menus for main and history fragments
- You can batch delete / download / share using them, select all, invert selected
- Implemented Cookies feature with export/import functionality
- Added preference to whether download without metered networks
- Added export to clipboard for the terminal output
- Added ability to download subtitles in a file. You can also write the subtitle language based on yt-dlp formatting
- Added ability to choose your preferred search engine instead of just having youtube
- Implemented ability to fetch common formats for all playlist items and choosing it for all of them (with this all formats will be updated for all items aswell)
- Implemented tablet ui with a side navigation bar
- Added ability to swipe right/left on download cards like queued,cancelled,errored,processing to delete or retry/redownload
- Added ability to long press a format card to show its details
- Added theme picker
- Fixed bottom sheets not showing properly on tablets

and more


> # 1.5.0 (2023-03)

# What's Changed

New Stuff:
- Moved all of the app to kotlin
- Fixed Song Thumbnails not showing on music apps using mediastore (like Samsung Music)
- Cards update and animate nicely when new items are added
- Turned download service to a worker and combined default downloads with custom command
- Added Formats and File Sizes on Results
- Added ability to change download path from the download card
- Combined Audio / Video / Command in a single card through tabs
- Added ability to download multiple vids at the same time (Limit is 5 for now)
- Added ability to schedule downloads. You can put the date and time.
- Fixed File Scanning
- Made downloads download to temporary folders. Each version of the file depending on the url and format type has its own folder. If you cancel a download and restart it, it will resume the downloads based on the correct format. After download, temp folder gets axed.
- Fixed downloads not getting cancelled sometimes
- Added ability to update yt-dlp from a nightly build
- Changed search suggestion provider to google's own api
- Added more sorting options in the history fragment
- Implemented Language switcher in the app. For devices on Android 13 and up, go to App Details and change it there
- Added material date and time pickers
- Updated bottom sheet design
- Added separate format selection card
- Implemented bottom card in the share menu instead of opening the whole app. If its a txt file it will open the app
- Added m-time as a preference
- Added ability to get result item formats if you searched for it or if it is a playlist item since they don't have formats
- Added Download Logs
- Added Search History
- Added Separate Download Queue Screen. (Double Click the download icon or go to more section to access)
- Fixed bugs on terminal mode
- Fixed downlaod logs crashing on older apis
- Added long press to open download card even if the setting is disabled
- Fixed bug of download card duplicating when switching formats in share activity
- Implemented Custom Command Tab in the download card. (Disabled by default. You need to create at least one command template in the more section)
- Added tabs to download queue section (running, queued, cancelled, errored)
- Added ability to update custom command right from the download card & modify the chosen one just for that download
- Fixed search history storing duplicate queries
- Implemented Preferred Download Type. On Share Activity or when searching for one result, it will open the preferred tab on the download card
- Implemented processing multiple items from a playlist in the share activity. You can select a portion of them, then you can modify each item just like you would on a normal link. You can change the download directory for all items at once, and you can schedule all of them like a normal download. You can also change their type to audio/video/command all at once
- Fixed Download All button not showing up after reopening app with a playlist as result
- Added copy log in the download log screen
- Fixed download logs not properly live scrolling
- Added ability to retry cancelled downloads
- Added ability to open logs directly from the list of errored downloads (if logs were enabled for that download at the time)
- Showed link copied to clipboard as item suggestion in searchview

> # 1.4.9 (2023-01)

# What's Changed
Fixed results duplicating when reopening the app
Fixed no results icon and app icon in more section not showing as good in smaller phones
Fixed white space on bottom nav bar #17
Fixed opus songs not saving thumbnail
Added Share Intent for Custom Command. You can find it with the name 'Run Command'
Made app create its own folder in the downloads directory since the app wouldn't download properly on some devices on a fresh install
Added new Albanian Strings from Hosted Weblate by @weblate in #15
Added new Japanese Strings from Hosted Weblate by @weblate in #16
Added Russian (thank you AntonDzutstsati) from Hosted Weblate by @weblate in #18
# Info
Future updates of this app might not be as often since im focusing on my studies. Feel free to open issues anyway, if you need to. I will check them out when i find the time

> # 1.4.8 (2023-01)

# What's Changed
fixed app not recognising youtube shorts links
fixed app sometimes not removing the downloading card from the downloads tab when it is cancelled
converted more components to kotlin

> # 1.4.7 (2023-01)

# What's Changed
added other sponsorblock categories you can remove from the download. You can select multiple of them too
added fast youtube playlist data fetching without an API key through invidious
fixed the list flashing when loading a large playlist or when sharing a text file to the app with multiple lines

> # 1.4.6 (2023-01)

# What's Changed
added invidous as another method for fetching youtube data faster. This removes the issue of having to make an API key but for some countries you won't be able to get trending videos.
added search suggestions

> # 1.4.5 (2022-12)

# What's Changed
fixed app not downloading files with some bad characters coming from twitter
added worst video quality as selection & default video format
Added Polish from Hosted Weblate by @weblate in #12

> # 1.4.4 (2022-12)

# What's Changed
Added Japanese (thank you HiSubway) from Hosted Weblate by @weblate in #9
Added check for permissions when opening the app

> # 1.4.3 (2022-12)

Added Turkish Language (thank you TRojen610)

> # 1.4.2 (2022-11)

Uninstall the app before updating. Singing key changed
New Features
Added Norwegian Bokmål [incomplete] (thank you comradekingu)
Added German Language (thank you eUgEntOptIc44)
Added French [incomplete] (thank you eUgEntOptIc44)

> # 1.4.1 (2022-10)

fixed app crashing when using links that don't have data like thumbnail, title author etc.
hid download all button when using single links through the search bar

> # 1.4.0 (2022-10)

# PLEASE READ

Due to high app usage, my personal youtube api key was throttled. I removed the built-in api key for default yt-dlp requests. They are slower but usable to everyone. You can add your own youtube api key in the setttings for faster responses. Refer to the readme for steps on how to get your own key. 

## NEW FEATURES

- made clicked audio/video buttons cancellable so you dont have to cancel everything
- made progress bar as thick as the card and somewhat transparent, looks nicer
- made the download progress not cut off and be seamless when transitioning from intermediate to solid colors
- renamed history tab to downloads
- downloaded items if they are not in the filesystem, they are greyed out
- added sorting and filtering chips on top of the downloads tab. You can filter between each website, file type and sort from oldest or newest
- made checking for updates as a toggle in the main settings page.
- downloading items now show in the downloads tab too and you can cancel them there aswell.
- added incognito mode. If on, downloaded stuff wont be added to history / downloads tab
- added a cancel button in the notification itself. It will cancel everything and clear the queue
- added bottom cards when you click audio / video buttons to configure some settings before downloading. You can change title and artist name, change format, and quality if you are choosing video. If you have selected mutliple items or are downloading everything present, you can't edit title and artist because you are dealing with multiple items.
- added a button to open the downloaded file. If the file is greyed out (file is not present) the button won't be there
- added ability to import a txt file. Create a simple txt file contaning any link that yt-dlp supports. You can also use youtube playlist links or simple queries. The app will read each line and update the screen. When finished, download all floating action button will appear. To use this feature just share the txt file to this app

## FIXES
- fixed downloaded items other than youtube not having proper download links
- fixed app crashing when pressing download all button
- fixed not getting all playlist items from generic ytdl request

> # 1.3.1 (2022-09)

added app icon (thank you decipher3114), adaptive depending on system theme
fixed app crashing when you multiselect and unselect
removed utc from history
updated mp3 and mp4 tags to audio and video

> # 1.3.0 (2022-09)

changed folder structure to ytdlnis instead of ytdl
when downloads are finished, history fragment updates itself
changed video duration of livestreams to LIVE
Fixed update dialog showing when you dont have internet
removed download functionality from home fragment and put it all on downloadservice. This makes the download stateless and the ui can be updated when you go back to the app
did the same for custom command activity
slight changes on result card ui
added fastlane metadata
added support for other yt-dlp videos instead of just focusing on youtube. Youtube is still the main focus
updated database
slight design changes
made bottom navigation bar the same color as the background
changed download buttons background colors inside cards
fixed result items changing download status icons when scrolling through recyclerview

> # 1.2.1 (2022-09)

Fixed file dates to be time of creation instead of time of upload
Putting url's on the search bar will clear the previous results and not append
fixed issue if you didn't give storage permission to the app, custom command would fail. Now it asks beforehand
added cancel floating action bar on custom command

> # 1.2.0 (2022-09)

# New Features

Added SponsorBlock Functionality. You can remove non music parts from audio files.
You can make the app put chapters to a downloaded video using sponsorblock / youtube chapters.
Downloading now happens as a background service so even if you kill the app, it will continue to download.
If you are downloading and press another item, it will get added to the queue instead of not doing anything
Converted Lists to Recyclerviews for more performance. The app can now load playlists with Thousands of items without breaking a sweat.
Implemented alot of other notable yt-dlp features as preferences
App can self-update and can even check if there is a new update every time you open the app
You can download partitions of a playlist too instead of just downloading all
You can select videos by long pressing one and then pressing the others to download just those as audio or video

# User Interface 

Fixed Time Formatting
Removed Hardcoded Strings
Ordered History items from new to old
Show no results image when history is empty
Separate Activity for Custom Commands (Do not write the output path, instead configure it in settings)
-Removed download all card, and replaced it with a Floating Action Button
Added floating action buttons for selected items

> # 1.1.0 (2022-08)

removed programmatical creation of cards and instead inflated them through layouts
created shimmer card effects when cards are loading from the api
made api calls in threads so that the app won't hang loading tons of data
made buttons adapt to Material U colors
made result cards show which videos are downloaded already by changing the download icons
showed video duration in result files
added trending videos if results are empty, based on your location
made the cards 16:9 no all devices
removed old menubar and now it blends with the statusbar
When switching to light mode, the status bar now changes as expected
added download progress notification
removed unnecessary toasts
fixed card scrolling when downloading a playlist
added floating action button to scroll to top
made appbar clickable that will also scroll to top
made bottom nav bar buttons scroll to top if you are clicking the highlighted button
fixed text sizes and positions
removed youtubedl-android folders and instead used modules (it now works, previously i had to manually add them)
removed hard coded strings which most were albanian. Now they are english by default and if you change the language in your phone's settings, it changes language too. Currently the app supports only English and Albanian
and other small fixes and details

> # 1.0.1 (2022-06)

Fixed Share Feature
Ditched my custom api server for an internal one
Now the app shows all playlists items

> # 1.0.0 (2022-05)

First Release