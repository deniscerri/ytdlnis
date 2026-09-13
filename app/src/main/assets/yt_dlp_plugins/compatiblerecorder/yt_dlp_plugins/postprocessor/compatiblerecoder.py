from pathlib import Path

from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor


class CompatibleRecoderPP(FFmpegPostProcessor):
    """
    Re-encode incompatible streams to H.264/AAC while preserving compatible
    streams, text subtitles, chapters, metadata, and an existing embedded
    thumbnail.

    Target container: MP4
    Target video codec: H.264
    Target audio codec: AAC
    """

    VIDEO_CODEC_NAMES = {
        'h264',
    }

    VIDEO_CODEC_TAGS = {
        'avc1',
        'avc3',
    }

    AUDIO_CODEC_NAMES = {
        'aac',
    }

    AUDIO_CODEC_TAGS = {
        'mp4a',
    }

    # Subtitle codecs that FFmpeg can reasonably convert to MP4 timed text.
    TEXT_SUBTITLE_CODECS = {
        'mov_text',
        'subrip',
        'srt',
        'webvtt',
        'ass',
        'ssa',
        'ttml',
    }

    # MP4 can use an attached picture as cover art. Normally yt-dlp's
    # EmbedThumbnail postprocessor handles this, but preserve one if it is
    # already embedded when this postprocessor runs.
    ATTACHED_PICTURE_CODECS = {
        'mjpeg',
        'jpeg',
        'png',
    }

    def _stream_codec(self, stream):
        """Return the codec name and codec tag as lowercase strings."""
        codec_name = (stream.get('codec_name') or '').lower()
        codec_tag = (stream.get('codec_tag_string') or '').lower()
        return codec_name, codec_tag

    def _is_h264(self, stream):
        codec_name, codec_tag = self._stream_codec(stream)

        return (
            codec_name in self.VIDEO_CODEC_NAMES
            or codec_name.startswith('h264')
            or codec_tag in self.VIDEO_CODEC_TAGS
        )

    def _is_aac(self, stream):
        codec_name, codec_tag = self._stream_codec(stream)

        return (
            codec_name in self.AUDIO_CODEC_NAMES
            or codec_name.startswith('aac')
            or codec_tag in self.AUDIO_CODEC_TAGS
        )

    def _is_attached_picture(self, stream):
        disposition = stream.get('disposition') or {}

        if disposition.get('attached_pic'):
            return True

        codec_name, _ = self._stream_codec(stream)
        return codec_name in self.ATTACHED_PICTURE_CODECS

    def _subtitle_can_be_embedded(self, stream):
        codec_name, codec_tag = self._stream_codec(stream)

        return (
            codec_name in self.TEXT_SUBTITLE_CODECS
            or codec_tag in self.TEXT_SUBTITLE_CODECS
        )

    def _probe_streams(self, filepath):
        """
        Probe the input using yt-dlp's configured FFprobe executable.

        FFmpegPostProcessor.get_metadata_object() uses self.probe_executable,
        so this does not assume that 'ffprobe' is globally available.
        """
        metadata = self.get_metadata_object(str(filepath))
        return metadata.get('streams') or [], metadata

    def _build_ffmpeg_options(self, streams):
        """
        Build explicit stream mappings and per-output codec options.

        Returns:
            (options, mapped_stream_count)
        """
        options = []

        video_outputs = []
        audio_outputs = []
        subtitle_outputs = []

        attached_picture_found = False

        # Preserve the original stream order.
        streams = sorted(
            streams,
            key=lambda stream: stream.get('index', 0),
        )

        for stream in streams:
            stream_type = (stream.get('codec_type') or '').lower()
            stream_index = stream.get('index')

            if stream_index is None:
                continue

            if stream_type == 'video':
                if self._is_attached_picture(stream):
                    # MP4 cover art should not be treated as normal video.
                    # Preserve one embedded thumbnail only.
                    if attached_picture_found:
                        self.to_screen(
                            'Skipping additional embedded thumbnail stream '
                            f'{stream_index}'
                        )
                        continue

                    attached_picture_found = True
                    video_outputs.append({
                        'input_index': stream_index,
                        'codec': 'copy',
                        'attached_pic': True,
                    })
                    continue

                video_outputs.append({
                    'input_index': stream_index,
                    'codec': 'copy' if self._is_h264(stream) else 'libx264',
                    'attached_pic': False,
                })

            elif stream_type == 'audio':
                audio_outputs.append({
                    'input_index': stream_index,
                    'codec': 'copy' if self._is_aac(stream) else 'aac',
                })

            elif stream_type == 'subtitle':
                if self._subtitle_can_be_embedded(stream):
                    codec_name, _ = self._stream_codec(stream)

                    subtitle_outputs.append({
                        'input_index': stream_index,
                        'codec': (
                            'copy'
                            if codec_name == 'mov_text'
                            else 'mov_text'
                        ),
                    })
                else:
                    codec_name, codec_tag = self._stream_codec(stream)

                    self.to_screen(
                        'Skipping unsupported subtitle stream '
                        f'{stream_index} ({codec_name or codec_tag or "unknown"})'
                    )

            elif stream_type == 'attachment':
                filename = (
                    stream.get('tags', {}).get('filename')
                    or stream.get('id')
                    or 'unknown'
                )

                self.to_screen(
                    f'Skipping attachment stream {stream_index} ({filename}); '
                    'MP4 does not support Matroska-style attachments'
                )

            elif stream_type == 'data':
                self.to_screen(
                    f'Skipping data stream {stream_index}; '
                    'it is not part of the MP4 compatibility target'
                )

        # ------------------------------------------------------------------
        # Explicit stream mapping
        # ------------------------------------------------------------------

        output_streams = []

        for item in video_outputs:
            options += ['-map', f'0:{item["input_index"]}']
            output_streams.append(('video', item))

        for item in audio_outputs:
            options += ['-map', f'0:{item["input_index"]}']
            output_streams.append(('audio', item))

        for item in subtitle_outputs:
            options += ['-map', f'0:{item["input_index"]}']
            output_streams.append(('subtitle', item))

        # ------------------------------------------------------------------
        # Per-output codec selection
        #
        # The indexes below are output-type indexes, NOT input stream indexes.
        # This is why each video/audio/subtitle track can be handled
        # independently.
        # ------------------------------------------------------------------

        video_index = 0
        audio_index = 0
        subtitle_index = 0

        for stream_type, item in output_streams:
            if stream_type == 'video':
                codec = item['codec']

                options += [
                    f'-c:v:{video_index}',
                    codec,
                ]

                # Preserve an already embedded thumbnail as cover art.
                if item['attached_pic']:
                    options += [
                        f'-disposition:v:{video_index}',
                        'attached_pic',
                    ]

                video_index += 1

            elif stream_type == 'audio':
                options += [
                    f'-c:a:{audio_index}',
                    item['codec'],
                ]
                audio_index += 1

            elif stream_type == 'subtitle':
                options += [
                    f'-c:s:{subtitle_index}',
                    item['codec'],
                ]
                subtitle_index += 1

        # Explicit metadata/chapter mapping.
        options += [
            '-map_metadata',
            '0',
            '-map_chapters',
            '0',
        ]

        # Do not let FFmpeg attempt to include unknown/data streams.
        options += [
            '-dn',
            '-ignore_unknown',
            '-f',
            'mp4',
        ]

        return options, len(output_streams)

    def run(self, info):
        filepath = Path(info['filepath'])

        if not filepath.exists():
            self.report_error(f'{filepath} does not exist!')
            return [], info

        # Probe the real file rather than relying solely on info['vcodec'] and
        # info['acodec']. This lets us make a decision for every individual
        # video/audio track.
        try:
            streams, metadata = self._probe_streams(filepath)
        except Exception as error:
            self.report_error(f'Unable to probe {filepath}: {error}')
            return [], info

        video_streams = [
            stream
            for stream in streams
            if (stream.get('codec_type') or '').lower() == 'video'
            and not self._is_attached_picture(stream)
        ]

        audio_streams = [
            stream
            for stream in streams
            if (stream.get('codec_type') or '').lower() == 'audio'
        ]

        # If there are no ordinary video/audio streams, this is not something
        # this compatibility recoder should process.
        if not video_streams and not audio_streams:
            self.to_screen(f'Not converting {filepath}; no media streams found')
            return [], info

        video_reencode = any(
            not self._is_h264(stream)
            for stream in video_streams
        )

        audio_reencode = any(
            not self._is_aac(stream)
            for stream in audio_streams
        )

        # The original plugin's compatibility decision is based on video/audio
        # codecs. Do not rewrite an already-compatible file merely because it
        # contains subtitles or metadata.
        if not video_reencode and not audio_reencode:
            self.to_screen(f'Not converting {filepath}; all media codecs are compatible')
            return [], info

        new_filepath = filepath.with_suffix('.compatible.mp4')

        if new_filepath.exists():
            self.report_error(f'{new_filepath} already exists')
            return [], info

        options, mapped_stream_count = self._build_ffmpeg_options(streams)

        if not mapped_stream_count:
            self.report_error(
                f'No streams that can be placed into MP4 were found in {filepath}'
            )
            return [], info

        video_actions = [
            'copy' if self._is_h264(stream) else 'H.264'
            for stream in video_streams
        ]

        audio_actions = [
            'copy' if self._is_aac(stream) else 'AAC'
            for stream in audio_streams
        ]

        self.to_screen(
            f'Re-encoding {filepath} -> {new_filepath} '
            f'(video: {", ".join(video_actions) or "none"}; '
            f'audio: {", ".join(audio_actions) or "none"})'
        )

        try:
            self.run_ffmpeg(
                str(filepath),
                str(new_filepath),
                options,
            )
        except Exception as error:
            self.report_error(f'FFmpeg failed: {error}')

            # Avoid leaving a partial output behind.
            try:
                if new_filepath.exists():
                    new_filepath.unlink()
            except OSError:
                pass

            return [], info

        # Replace the original file.
        try:
            filepath.unlink()
        except OSError as error:
            self.report_error(
                f'Unable to remove original file {filepath}: {error}'
            )

            try:
                if new_filepath.exists():
                    new_filepath.unlink()
            except OSError:
                pass

            return [], info

        final_filepath = filepath.with_suffix('.mp4')

        if final_filepath.exists():
            self.report_error(f'{final_filepath} already exists')

            try:
                if new_filepath.exists():
                    new_filepath.unlink()
            except OSError:
                pass

            return [], info

        try:
            new_filepath.replace(final_filepath)
        except OSError as error:
            self.report_error(
                f'Unable to move {new_filepath} to {final_filepath}: {error}'
            )
            return [], info

        # Critical for later yt-dlp postprocessors such as EmbedThumbnail and
        # FFmpegMetadata: they must see the new file path.
        info['filepath'] = str(final_filepath)

        return [], info