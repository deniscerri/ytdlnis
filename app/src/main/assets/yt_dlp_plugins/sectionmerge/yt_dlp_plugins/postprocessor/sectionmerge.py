import os
from yt_dlp.postprocessor.common import PostProcessor
from yt_dlp.postprocessor.ffmpeg import FFmpegConcatPP


class SectionMergePP(PostProcessor):
    """Merges every downloaded --download-sections cut into a single file.

    yt-dlp has no "all downloads finished" hook, so the number of expected
    sections is passed in as `count` and the merge runs on the last one.
    """

    def __init__(self, downloader=None, count=0, **kwargs):
        super().__init__(downloader)
        self._count = int(count)
        self._files = []

    def run(self, info):
        self._files.append(info['filepath'])
        if self._count < 2 or len(self._files) < self._count:
            return [], info

        base, ext = os.path.splitext(self._files[0])
        out = f'{base} [merged]{ext}'
        self.to_screen('Merging cuts into one...')
        FFmpegConcatPP(self._downloader).concat_files(self._files, out)

        # delete the individual cuts and point the final result to the merged file
        to_delete = list(self._files)
        self._files = []
        info['filepath'] = out
        return to_delete, info
