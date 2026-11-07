import os

from yt_dlp.postprocessor.common import PostProcessor


class ContainerTagsPP(PostProcessor):
    """Writes the meta_ fields that ffmpeg drops or merges for some containers.

    MP4/M4A: ffmpeg never writes publisher or purl, and writes copyright only as
    cprt, while players like VLC read the ©cpy atom.
    Opus/Ogg/FLAC: ffmpeg writes comment as DESCRIPTION, where the description
    overwrites it, and has no mapping to ORGANIZATION, the standard publisher key.

    Must run with when=after_move, so it edits the final file. A failure only warns,
    the download itself is already good.
    """

    def run(self, info):
        path = info.get('filepath')
        if not path or not os.path.exists(path):
            return [], info

        ext = os.path.splitext(path)[1].lower().lstrip('.')
        try:
            if ext in ('m4a', 'mp4'):
                self._write_mp4(path, info)
            elif ext in ('opus', 'ogg', 'oga', 'flac'):
                self._write_vorbis(path, info)
        except Exception as e:
            self.report_warning(f'Could not write metadata tags: {e}')
        return [], info

    @staticmethod
    def _write_mp4(path, info):
        from mutagen.mp4 import MP4
        atoms = {'\xa9pub': 'meta_publisher', '\xa9cpy': 'meta_copyright', 'purl': 'meta_purl'}
        values = {atom: info[field] for atom, field in atoms.items() if info.get(field)}
        if not values:
            return
        audio = MP4(path)
        if audio.tags is None:
            audio.add_tags()
        for atom, value in values.items():
            audio.tags[atom] = [value]
        audio.save()

    @staticmethod
    def _write_vorbis(path, info):
        import mutagen
        keys = {'comment': 'meta_comment', 'organization': 'meta_publisher'}
        values = {key: info[field] for key, field in keys.items() if info.get(field)}
        if not values:
            return
        audio = mutagen.File(path)
        if audio is None:
            raise ValueError('unsupported file')
        for key, value in values.items():
            audio[key] = [value]
        audio.save()
