import json
import urllib.parse

from yt_dlp.networking import Request
from yt_dlp.networking.exceptions import HTTPError, TransportError
from yt_dlp.postprocessor.common import PostProcessor

API_URL = 'https://sponsor.ajay.app/api/branding?videoID='


def pick_title(branding):
    """The best community title DeArrow has, or None to keep the original.

    Titles come sorted best first. Only confirmed ones count (locked or upvoted), and when the
    best confirmed entry is the original title the community kept it, so nothing changes.
    """
    for entry in branding.get('titles') or []:
        if not (entry.get('locked') or (entry.get('votes') or 0) > 0):
            continue
        if entry.get('original'):
            return None
        title = (entry.get('title') or '').strip()
        return title or None
    return None


class DeArrowPP(PostProcessor):
    """Replaces a YouTube video's clickbait title with its DeArrow title.

    Runs with when=pre_process, before the filename and the metadata are built. The app
    only adds it for downloads without a title picked in the download card, which already
    shows (and lets the user edit) the DeArrow title. A failed lookup keeps the original.
    """

    def run(self, info):
        if info.get('extractor_key') != 'Youtube' or not info.get('id'):
            return [], info
        try:
            req = Request(API_URL + urllib.parse.quote(info['id']), headers={'User-Agent': 'YTDLnis'})
            # yt-dlp's urlopen: uses its certifi bundle and proxy settings, bare urllib fails SSL on Android
            with self._downloader.urlopen(req) as res:
                title = pick_title(json.loads(res.read().decode('utf-8')))
        except (HTTPError, TransportError, ValueError) as e:
            self.report_warning(f'Could not get the DeArrow title: {e}')
            return [], info

        if title:
            self.to_screen(f'Using DeArrow title: {title}')
            info['title'] = title
        return [], info
