import { Box, Typography } from '@mui/material';
import { useState } from 'react';

/** Real photo from the phone; if the phone is unreachable, falls back to the thumbnail the IoT device stored. */
export function MediaPhoto({
  src,
  label,
  fallback,
  maxHeight = 180,
}: Readonly<{ src: string; label: string; fallback?: string; maxHeight?: number }>) {
  const [failedSrc, setFailedSrc] = useState('');
  const failed = failedSrc === src;
  const useFallback = failed && !!fallback;
  return (
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
        {label}
        {useFallback ? ' · last frame stored on the device (phone unreachable)' : ''}
      </Typography>
      {(!failed || useFallback) && (
        <Box
          component="img"
          src={useFallback ? `data:image/jpeg;base64,${fallback}` : src}
          alt={label}
          onError={() => setFailedSrc(src)}
          sx={{
            width: '100%',
            maxHeight,
            objectFit: 'cover',
            borderRadius: 1,
            bgcolor: 'action.hover',
            imageRendering: useFallback ? 'pixelated' : 'auto',
          }}
        />
      )}
    </Box>
  );
}
