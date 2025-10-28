import React, { useState, useEffect } from 'react';
import {
  Container,
  Box,
  Typography,
  Card,
  CardContent,
  LinearProgress,
  IconButton,
  List,
  ListItem,
  Chip
} from '@mui/material';
import CancelIcon from '@mui/icons-material/Cancel';
import axios from 'axios';
import { useWebSocket } from '../context/WebSocketContext';

const Downloads = () => {
  const [activeDownloads, setActiveDownloads] = useState([]);
  const { messages } = useWebSocket();

  useEffect(() => {
    fetchActiveDownloads();
  }, []);

  useEffect(() => {
    // Update downloads based on WebSocket messages
    messages.forEach((msg) => {
      if (msg.type === 'progress') {
        setActiveDownloads((prev) =>
          prev.map((download) =>
            download.id === msg.downloadId
              ? { ...download, progress: msg.progress, speed: msg.speed, eta: msg.eta }
              : download
          )
        );
      } else if (msg.type === 'complete' || msg.type === 'error' || msg.type === 'cancelled') {
        setActiveDownloads((prev) =>
          prev.filter((download) => download.id !== msg.downloadId)
        );
      }
    });
  }, [messages]);

  const fetchActiveDownloads = async () => {
    try {
      const response = await axios.get('/api/downloads/active');
      setActiveDownloads(response.data);
    } catch (error) {
      console.error('Failed to fetch active downloads:', error);
    }
  };

  const handleCancel = async (id) => {
    try {
      await axios.post(`/api/download/${id}/cancel`);
      setActiveDownloads((prev) => prev.filter((download) => download.id !== id));
    } catch (error) {
      console.error('Failed to cancel download:', error);
    }
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Typography variant="h4" gutterBottom>
        Active Downloads
      </Typography>

      {activeDownloads.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 8 }}>
          <Typography variant="h6" color="text.secondary">
            No active downloads
          </Typography>
        </Box>
      ) : (
        <List>
          {activeDownloads.map((download) => (
            <ListItem key={download.id} sx={{ px: 0 }}>
              <Card sx={{ width: '100%' }}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="h6" noWrap>
                        {download.title || download.url}
                      </Typography>
                      <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                        <Chip
                          label={download.type}
                          size="small"
                          color="primary"
                        />
                        <Chip
                          label={download.status}
                          size="small"
                          color={download.status === 'downloading' ? 'success' : 'default'}
                        />
                      </Box>
                    </Box>
                    <IconButton onClick={() => handleCancel(download.id)} color="error">
                      <CancelIcon />
                    </IconButton>
                  </Box>

                  <LinearProgress
                    variant="determinate"
                    value={download.progress || 0}
                    sx={{ mb: 1, height: 8, borderRadius: 4 }}
                  />

                  <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Typography variant="body2" color="text.secondary">
                      {download.progress ? `${download.progress.toFixed(1)}%` : '0%'}
                    </Typography>
                    <Box sx={{ display: 'flex', gap: 2 }}>
                      {download.speed && (
                        <Typography variant="body2" color="text.secondary">
                          {download.speed}
                        </Typography>
                      )}
                      {download.eta && (
                        <Typography variant="body2" color="text.secondary">
                          ETA: {download.eta}
                        </Typography>
                      )}
                    </Box>
                  </Box>
                </CardContent>
              </Card>
            </ListItem>
          ))}
        </List>
      )}
    </Container>
  );
};

export default Downloads;

