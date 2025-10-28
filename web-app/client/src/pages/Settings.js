import React, { useState, useEffect } from 'react';
import {
  Container,
  Box,
  Typography,
  Card,
  CardContent,
  Switch,
  FormControlLabel,
  TextField,
  Button,
  Divider,
  Alert,
  Snackbar,
  Select,
  MenuItem,
  FormControl,
  InputLabel
} from '@mui/material';
import axios from 'axios';

const Settings = ({ darkMode, setDarkMode }) => {
  const [settings, setSettings] = useState({
    downloadPath: '',
    audioFormat: 'mp3',
    videoFormat: 'mp4',
    audioQuality: '192',
    videoQuality: '1080',
    embedThumbnail: true,
    embedMetadata: true,
    removeAudio: false
  });
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' });

  useEffect(() => {
    fetchSettings();
  }, []);

  const fetchSettings = async () => {
    try {
      const response = await axios.get('/api/settings');
      setSettings({
        ...response.data,
        embedThumbnail: response.data.embedThumbnail === 'true',
        embedMetadata: response.data.embedMetadata === 'true',
        removeAudio: response.data.removeAudio === 'true'
      });
    } catch (error) {
      console.error('Failed to fetch settings:', error);
    }
  };

  const handleSave = async () => {
    try {
      await axios.post('/api/settings', {
        ...settings,
        embedThumbnail: settings.embedThumbnail.toString(),
        embedMetadata: settings.embedMetadata.toString(),
        removeAudio: settings.removeAudio.toString()
      });
      setSnackbar({ open: true, message: 'Settings saved successfully', severity: 'success' });
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to save settings', severity: 'error' });
      console.error(error);
    }
  };

  return (
    <Container maxWidth="md" sx={{ py: 4, pb: 10 }}>
      <Typography variant="h4" gutterBottom>
        Settings
      </Typography>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Appearance
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={darkMode}
                onChange={(e) => setDarkMode(e.target.checked)}
              />
            }
            label="Dark Mode"
          />
        </CardContent>
      </Card>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Download Path
          </Typography>
          <TextField
            fullWidth
            value={settings.downloadPath}
            onChange={(e) => setSettings({ ...settings, downloadPath: e.target.value })}
            placeholder="/path/to/downloads"
            helperText="Default download location for files"
          />
        </CardContent>
      </Card>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Audio Settings
          </Typography>
          
          <FormControl fullWidth sx={{ mb: 2 }}>
            <InputLabel>Audio Format</InputLabel>
            <Select
              value={settings.audioFormat}
              onChange={(e) => setSettings({ ...settings, audioFormat: e.target.value })}
              label="Audio Format"
            >
              <MenuItem value="mp3">MP3</MenuItem>
              <MenuItem value="m4a">M4A</MenuItem>
              <MenuItem value="opus">OPUS</MenuItem>
              <MenuItem value="flac">FLAC</MenuItem>
              <MenuItem value="wav">WAV</MenuItem>
            </Select>
          </FormControl>

          <FormControl fullWidth sx={{ mb: 2 }}>
            <InputLabel>Audio Quality (kbps)</InputLabel>
            <Select
              value={settings.audioQuality}
              onChange={(e) => setSettings({ ...settings, audioQuality: e.target.value })}
              label="Audio Quality (kbps)"
            >
              <MenuItem value="128">128 kbps</MenuItem>
              <MenuItem value="192">192 kbps</MenuItem>
              <MenuItem value="256">256 kbps</MenuItem>
              <MenuItem value="320">320 kbps</MenuItem>
            </Select>
          </FormControl>

          <Divider sx={{ my: 2 }} />

          <Typography variant="h6" gutterBottom>
            Video Settings
          </Typography>

          <FormControl fullWidth sx={{ mb: 2 }}>
            <InputLabel>Video Format</InputLabel>
            <Select
              value={settings.videoFormat}
              onChange={(e) => setSettings({ ...settings, videoFormat: e.target.value })}
              label="Video Format"
            >
              <MenuItem value="mp4">MP4</MenuItem>
              <MenuItem value="mkv">MKV</MenuItem>
              <MenuItem value="webm">WebM</MenuItem>
            </Select>
          </FormControl>

          <FormControl fullWidth sx={{ mb: 2 }}>
            <InputLabel>Video Quality</InputLabel>
            <Select
              value={settings.videoQuality}
              onChange={(e) => setSettings({ ...settings, videoQuality: e.target.value })}
              label="Video Quality"
            >
              <MenuItem value="2160">4K (2160p)</MenuItem>
              <MenuItem value="1440">2K (1440p)</MenuItem>
              <MenuItem value="1080">Full HD (1080p)</MenuItem>
              <MenuItem value="720">HD (720p)</MenuItem>
              <MenuItem value="480">SD (480p)</MenuItem>
            </Select>
          </FormControl>

          <Divider sx={{ my: 2 }} />

          <Typography variant="h6" gutterBottom>
            Processing Options
          </Typography>

          <FormControlLabel
            control={
              <Switch
                checked={settings.embedThumbnail}
                onChange={(e) => setSettings({ ...settings, embedThumbnail: e.target.checked })}
              />
            }
            label="Embed Thumbnail"
          />

          <FormControlLabel
            control={
              <Switch
                checked={settings.embedMetadata}
                onChange={(e) => setSettings({ ...settings, embedMetadata: e.target.checked })}
              />
            }
            label="Embed Metadata"
          />

          <FormControlLabel
            control={
              <Switch
                checked={settings.removeAudio}
                onChange={(e) => setSettings({ ...settings, removeAudio: e.target.checked })}
              />
            }
            label="Remove Audio (Video Downloads)"
          />
        </CardContent>
      </Card>

      <Button variant="contained" fullWidth size="large" onClick={handleSave}>
        Save Settings
      </Button>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert
          onClose={() => setSnackbar({ ...snackbar, open: false })}
          severity={snackbar.severity}
          sx={{ width: '100%' }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Container>
  );
};

export default Settings;

