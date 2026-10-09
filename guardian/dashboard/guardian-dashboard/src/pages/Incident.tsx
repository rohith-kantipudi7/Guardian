import { Alert, Button, CircularProgress, Stack } from '@mui/material';
import ArrowBackOutlined from '@mui/icons-material/ArrowBackOutlined';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { IncidentDetail } from '../components/IncidentDetail';
import { useCrash } from '../services/queries';

export function Incident() {
  const { id } = useParams();
  const crash = useCrash(id);
  if (crash.isLoading) return <CircularProgress />;
  if (crash.isError || !crash.data) return <Alert severity="error">Incident {id} not found</Alert>;
  return (
    <Stack spacing={2}>
      <Button component={RouterLink} to="/incidents" startIcon={<ArrowBackOutlined />} sx={{ alignSelf: 'flex-start' }}>
        Incident log
      </Button>
      <IncidentDetail incident={crash.data} />
    </Stack>
  );
}
