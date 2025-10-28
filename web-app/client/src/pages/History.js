import React, { useState, useEffect } from 'react';
import {
  Container,
  Box,
  Typography,
  Card,
  CardContent,
  IconButton,
  List,
  ListItem,
  Chip,
  Button
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import FolderOpenIcon from '@mui/icons-material/FolderOpen';
import axios from 'axios';

const History = () => {
  const [history, setHistory] = useState([]);

  useEffect(() => {
    fetchHistory();
  }, []);

  const fetchHistory = async () => {
    try {
      const response = await axios.get('/api/history');
      setHistory(response.data);
    } catch (error) {
      console.error('Failed to fetch history:', error);
    }
  };

  const handleDelete = async (id) => {
    try {
      await axios.delete(`/api/history/${id}`);
      setHistory((prev) => prev.filter((item) => item.id !== id));
    } catch (error) {
      console.error('Failed to delete history item:', error);
    }
  };

  const handleOpenFile = (filePath) => {
    // This would require electron or native integration
    console.log('Open file:', filePath);
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleString();
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Typography variant="h4" gutterBottom>
        Download History
      </Typography>

      {history.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 8 }}>
          <Typography variant="h6" color="text.secondary">
            No download history
          </Typography>
        </Box>
      ) : (
        <List>
          {history.map((item) => (
            <ListItem key={item.id} sx={{ px: 0 }}>
              <Card sx={{ width: '100%' }}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <Box sx={{ flex: 1 }}>
                      <Typography variant="h6" gutterBottom>
                        {item.title || item.url}
                      </Typography>
                      
                      <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
                        <Chip label={item.type} size="small" color="primary" />
                        <Chip
                          label={item.status}
                          size="small"
                          color={
                            item.status === 'completed' ? 'success' :
                            item.status === 'error' ? 'error' :
                            'default'
                          }
                        />
                      </Box>

                      <Typography variant="body2" color="text.secondary" gutterBottom>
                        {formatDate(item.createdAt)}
                      </Typography>

                      {item.filePath && (
                        <Typography variant="body2" color="text.secondary" noWrap>
                          {item.filePath}
                        </Typography>
                      )}

                      {item.error && (
                        <Typography variant="body2" color="error" sx={{ mt: 1 }}>
                          Error: {item.error}
                        </Typography>
                      )}

                      {item.filePath && (
                        <Button
                          startIcon={<FolderOpenIcon />}
                          onClick={() => handleOpenFile(item.filePath)}
                          size="small"
                          sx={{ mt: 1 }}
                        >
                          Open File
                        </Button>
                      )}
                    </Box>

                    <IconButton onClick={() => handleDelete(item.id)} color="error">
                      <DeleteIcon />
                    </IconButton>
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

export default History;

