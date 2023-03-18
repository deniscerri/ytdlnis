# PREFACE
Before reading, you may know what yt-dlp is and what it does. In short, it's a CLI (Command Line Interface) program written in python, which lets you download videos from 1000+ websites.

For bug reports and feature requests, please search in issues first (including the closed ones). If there're no duplicates, feel free to submit an issue with an issue template.

We'll probably ignore and close your issue if it's not using the existing templates or doesn't contain sufficient description.

For questions or any other ideas to improve, you can join or start a new discussion, talk in the telegram group or in the discord server.

## Bug Report
When submitting a bug report, please make sure your issue contains enough information for reproducing the problem, including the options or the custom command being used, the link to the video, and other fields in the issue template if there is one. A video recording of the problem is very helpful.

## Feature Request
YTDlnis is and will remain a simple GUI for yt-dlp, providing most of the functionality of yt-dlp as is, without modifications. Thus, we'll not take requests for features that yt-dlp does not support.

The app has three download modes:

- <b>General mode</b>: Save as audio, download playlist, and many other options that can be used individually or combined for normal download tasks. Once the download is complete, YTDlnis will scan the files into the system media library, and store them in the download history.
- <b>Custom Command mode</b>: For advanced usage of yt-dlp, a user can create and store multiple command templates in the app, then select and use one of them directly to execute the yt-dlp command like in a terminal. In this mode, all of the GUI options and features in the general mode will be disabled.
  Since most of the functions can be implemented in custom command mode, the "feature request" would be treated as adding a shortcut to the general mode. However, not all feature requests will be accepted and implemented in the app. Why not add an option for that?
- <b>Terminal mode</b>: A CLI-like interface to be as close to yt-dlp

## Pull Request
If you wish to contribute to the project by submitting code directly, please first leave a comment under the relevant issue or file a new issue, describe the changes you are about to make.

To avoid multiple pull requests resolving the same issue, let others know you are working on it by saying so in a comment, or ask the issue to be assigned to yourself.

## Building From Source
Fork this project, import and compile it with the latest version of Android Studio Canary.
