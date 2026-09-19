import os
import shutil
import tempfile
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor

class BurnSubsPP(FFmpegPostProcessor):
    def run(self, info):
        subs = info.get('requested_subtitles') or {}

        # Find subtitle language key and filepath
        sub_info = next(((lang, s.get('filepath')) for lang, s in subs.items() if s.get('filepath')), None)

        if not sub_info or not sub_info[1]:
            self.to_screen('No subtitle file found; skipping burn-in')
            return [], info

        lang, sub_path = sub_info
        path = info['filepath']

        self.to_screen(f'Found subtitles [{lang}]: {os.path.basename(sub_path)}')
        self.to_screen(f'Burning subtitles into {os.path.basename(path)}...')

        temp_out = f'{path}.burn.mp4'

        # FIX: Create a clean temp subtitle copy to avoid single-quote & special char path issues
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_sub = os.path.join(temp_dir, 'sub.srt')
            shutil.copy2(sub_path, temp_sub)

            # Escape paths for FFmpeg filter graph
            esc_sub = temp_sub.replace('\\', '/').replace(':', r'\:').replace("'", r"\'")

            # Basic filter string; drop fixed /system/fonts to avoid Android OS permission crashes
            vf_filter = f"subtitles='{esc_sub}'"

            self.run_ffmpeg_multiple_files(
                [path], temp_out,
                [
                    '-vf', vf_filter,
                    '-c:v', 'libx264',
                    '-preset', 'ultrafast',
                    '-crf', '20',
                    '-c:a', 'copy'
                ]
            )

        os.replace(temp_out, path)
        info['filepath'] = path

        self.to_screen('Successfully completed subtitle burn-in.')
        return [], info