from pathlib import Path

from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor


class CompatibleRecoderPP(FFmpegPostProcessor):
    def run(self, info):
        filepath = Path(info['filepath'])

        vcodec = (info.get('vcodec') or '').lower()
        acodec = (info.get('acodec') or '').lower()

        video_ok = vcodec.startswith(('h264', 'avc'))
        audio_ok = acodec.startswith(('aac', 'mp4a'))

        if video_ok and audio_ok:
            self.to_screen(f'Not converting {filepath}')
            return [], info

        if not filepath.exists():
            self.report_error(f'{filepath} does not exist!')
            return [], info

        new_filepath = filepath.with_suffix('.new.mp4')

        if new_filepath.exists():
            self.report_error(f'{new_filepath} already exists')
            return [], info

        # Copy streams that already satisfy the codec requirements.
        video_codec = 'copy' if video_ok else 'libx264'
        audio_codec = 'copy' if audio_ok else 'aac'

        self.to_screen(
            f'Re-encoding {filepath} '
            f'(video: {"copy" if video_ok else "H.264"}, '
            f'audio: {"copy" if audio_ok else "AAC"})')

        try:
            self.run_ffmpeg(
                str(filepath),
                str(new_filepath),
                [
                    '-c:v', video_codec,
                    '-c:a', audio_codec,
                    '-f', 'mp4',
                ],
            )
        except Exception as e:
            self.report_error(f'ffmpeg failed: {e}')
            return [], info

        filepath.unlink()

        final_filepath = filepath.with_suffix('.mp4')

        if final_filepath.exists():
            self.report_error(f'{final_filepath} already exists')
            return [], info

        new_filepath.replace(final_filepath)

        info['filepath'] = str(final_filepath)

        return [], info