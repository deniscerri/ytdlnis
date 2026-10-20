import re
import urllib.parse

from yt_dlp.extractor.instagram import InstagramIE
from yt_dlp.networking.exceptions import HTTPError
from yt_dlp.utils import ExtractorError


class InstagramLoginRedirectIE(InstagramIE, plugin_name='ytdlnis'):
    # When the account cookies are rejected, Instagram redirects the media info API request
    # to the login page and serves it with HTTP 200. yt-dlp only falls back to logged-out
    # extraction when that redirect ends in an HTTPError, so it tries to parse the login page
    # as JSON and fails with "Failed to parse JSON". Raise the error yt-dlp already handles.
    _MEDIA_INFO_PATH_RE = re.compile(r'/api/v1/media/\d+/info/?$')

    def _download_webpage_handle(self, url_or_request, *args, **kwargs):
        res = super()._download_webpage_handle(url_or_request, *args, **kwargs)
        if res is False:
            return res

        requested_path = urllib.parse.urlparse(getattr(url_or_request, 'url', url_or_request)).path
        final_path = urllib.parse.urlparse(res[1].url).path
        if self._MEDIA_INFO_PATH_RE.search(requested_path) and (
                final_path.startswith('/accounts/login') or final_path == '/'):
            raise ExtractorError(
                'Instagram redirected the request to the login page', cause=HTTPError(res[1]))
        return res
