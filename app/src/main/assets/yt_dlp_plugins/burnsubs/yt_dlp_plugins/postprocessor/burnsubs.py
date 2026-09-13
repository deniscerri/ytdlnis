import os
from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor

class BurnSubsPP(FFmpegPostProcessor):
    def run(self, info):
        subs = info.get('requested_subtitles') or {}
        
        # Find subtitle language key and filepath
        sub_info = next(((lang, s.get('filepath')) for lang, s in subs.items() if s.get('filepath')), None)
        
        if not sub_info or not sub_info[1]:
            self.to_screen('No subtitle file found; skipping burn-in')
            return [], info
        
        lang, sub = sub_info
        path = info['filepath']
        
        # Terminal updates
        self.to_screen(f'Found subtitles [{lang}]: {os.path.basename(sub)}')
        self.to_screen(f'Burning subtitles into {os.path.basename(path)}...')

        temp = f'{path}.burn.mp4'
        # libass parses this as a filter-graph arg: escape ':' and '\'
        esc = sub.replace('\\', '/').replace(':', r'\:')
        
        self.run_ffmpeg_multiple_files(
            [path], temp,
            ['-vf', f"subtitles='{esc}':fontsdir=/system/fonts", '-c:a', 'copy']
        )
        
        os.replace(temp, path)
        info['filepath'] = path
        
        self.to_screen(f'Successfully completed subtitle burn-in.')
        return [], info