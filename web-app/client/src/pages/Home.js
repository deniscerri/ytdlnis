import React, { useState } from 'react';
import {
  Container,
  Box,
  TextField,
  Button,
  Card,
  CardContent,
  CardMedia,
  Typography,
  CircularProgress,
  ToggleButtonGroup,
  ToggleButton,
  Alert,
  Snackbar
} from '@mui/material';
import AudiotrackIcon from '@mui/icons-material/Audiotrack';
import VideoFileIcon from '@mui/icons-material/VideoFile';
import axios from 'axios';

const Home = () => {
  const [url, setUrl] = useState('');
  const [videoInfo, setVideoInfo] = useState(null);
  const [loading, setLoading] = useState(false);
  const [downloadType, setDownloadType] = useState('video');
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' });

  const handleGetInfo = async () => {
    if (!url) {
      setSnackbar({ open: true, message: 'Please enter a URL', severity: 'error' });
      return;
    }

    setLoading(true);
    try {
      const response = await axios.post('/api/info', { url });
      setVideoInfo(response.data);
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to fetch video info', severity: 'error' });
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = async () => {
    if (!url) {
      setSnackbar({ open: true, message: 'Please enter a URL', severity: 'error' });
      return;
    }

    try {
      const response = await axios.post('/api/download', {
        url,
        type: downloadType,
        format: downloadType === 'audio' ? 'mp3' : 'mp4',
        options: {}
      });
      
      setSnackbar({ 
        open: true, 
        message: `Download started! ID: ${response.data.downloadId}`, 
        severity: 'success' 
      });
      
      // Clear form
      setUrl('');
      setVideoInfo(null);
    } catch (error) {
      setSnackbar({ open: true, message: 'Failed to start download', severity: 'error' });
      console.error(error);
    }
  };

  const formatDuration = (seconds) => {
    if (!seconds) return 'Unknown';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    
    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
    return `${minutes}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Box sx={{ mb: 4, textAlign: 'center' }}>
        <Typography variant="h3" component="h1" gutterBottom fontWeight="bold">
          YTDLnis Web
        </Typography>
        <Typography variant="subtitle1" color="text.secondary">
          Download videos and audio from 1000+ websites
        </Typography>
      </Box>

      <Box sx={{ mb: 3 }}>
        <TextField
          fullWidth
          label="Enter URL"
          variant="outlined"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          onKeyPress={(e) => {
            if (e.key === 'Enter') {
              handleGetInfo();
            }
          }}
          placeholder="https://www.youtube.com/watch?v=..."
          sx={{ mb: 2 }}
        />
        
        <Box sx={{ display: 'flex', gap: 2 }}>
          <Button
            variant="outlined"
            onClick={handleGetInfo}
            disabled={loading}
            fullWidth
          >
            {loading ? <CircularProgress size={24} /> : 'Get Info'}
          </Button>
          
          <ToggleButtonGroup
            value={downloadType}
            exclusive
            onChange={(e, newType) => newType && setDownloadType(newType)}
            fullWidth
          >
            <ToggleButton value="audio">
              <AudiotrackIcon sx={{ mr: 1 }} />
              Audio
            </ToggleButton>
            <ToggleButton value="video">
              <VideoFileIcon sx={{ mr: 1 }} />
              Video
            </ToggleButton>
          </ToggleButtonGroup>
        </Box>
      </Box>

      {videoInfo && (
        <Card sx={{ mb: 3 }}>
          {videoInfo.thumbnail && (
            <CardMedia
              component="img"
              height="300"
              image={videoInfo.thumbnail}
              alt={videoInfo.title}
              sx={{ objectFit: 'cover' }}
            />
          )}
          <CardContent>
            <Typography variant="h5" component="h2" gutterBottom>
              {videoInfo.title}
            </Typography>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              By {videoInfo.uploader}
            </Typography>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              Duration: {formatDuration(videoInfo.duration)}
            </Typography>
            {videoInfo.description && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                {videoInfo.description.substring(0, 200)}
                {videoInfo.description.length > 200 && '...'}
              </Typography>
            )}
            <Button
              variant="contained"
              fullWidth
              size="large"
              onClick={handleDownload}
              sx={{ mt: 3 }}
            >
              Download as {downloadType}
            </Button>
          </CardContent>
        </Card>
      )}

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

export default Home;

