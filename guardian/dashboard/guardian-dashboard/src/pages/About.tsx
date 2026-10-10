import type { ReactNode } from 'react';
import { Box, Card, CardContent, Chip, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import SensorsOutlined from '@mui/icons-material/SensorsOutlined';
import CellTowerOutlined from '@mui/icons-material/CellTowerOutlined';
import SmsOutlined from '@mui/icons-material/SmsOutlined';
import VisibilityOutlined from '@mui/icons-material/VisibilityOutlined';
import DirectionsRunRounded from '@mui/icons-material/DirectionsRunRounded';
import TaskAltOutlined from '@mui/icons-material/TaskAltOutlined';
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded';

const FLOW: { icon: ReactNode; title: string; who: string; text: string }[] = [
  {
    icon: <SensorsOutlined />,
    title: 'Crash detected',
    who: 'Vehicle device',
    text: "IMU + GPS + speed scored on the device. A collision gives the driver 10 s to tap I'M SAFE.",
  },
  {
    icon: <CellTowerOutlined />,
    title: 'Reported',
    who: 'Device LTE → cloud',
    text: "Sent over the device's own cellular link. Queued and retried if there is no coverage. Phone not needed.",
  },
  {
    icon: <SmsOutlined />,
    title: 'Everyone alerted',
    who: 'Automatic',
    text: 'Police, nearest hospital / ambulance and every family contact get an SMS with full details and a personal link.',
  },
  {
    icon: <VisibilityOutlined />,
    title: 'Seen',
    who: 'Automatic',
    text: 'Opening the link is recorded as seen. They get location + navigation, vehicle, driver and family numbers — no buttons to press.',
  },
  {
    icon: <DirectionsRunRounded />,
    title: 'Help goes',
    who: 'Responders',
    text: 'Ambulance and police act through their own systems; family can call the driver. Guardian just gives them everything instantly.',
  },
  {
    icon: <TaskAltOutlined />,
    title: 'Closed',
    who: 'Driver',
    text: "The driver taps I'M SAFE (app or device button) and every responder's page shows \"Stand down\".",
  },
];

const RECIPIENTS = [
  {
    role: 'Police (112)',
    gets: 'SMS + link: severity, time, map / navigate, vehicle registration, driver name & phone, crash data, phone-not-reachable flag',
    does: 'Nothing in Guardian — read and respond as usual',
  },
  {
    role: 'Hospital / ambulance (108)',
    gets: 'Same complete details as police',
    does: 'Nothing in Guardian — read and dispatch',
  },
  {
    role: 'Family (emergency contacts)',
    gets: 'Same complete details, plus who else has seen the alert',
    does: 'Nothing in Guardian — call the driver / go to the location',
  },
  {
    role: 'Driver',
    gets: 'Crash alert in the Guardian app (when the phone survives)',
    does: "I'M SAFE cancels the incident and stands everyone down",
  },
];

export function About() {
  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">How Guardian's response works</Typography>
        <Typography color="text.secondary" sx={{ maxWidth: 900 }}>
          No control room and no operator in the loop. The moment the device reports a crash, the backend alerts
          police, hospital and family directly with everything they need. This dashboard is only a <b>read-only
          monitor</b> to show what is happening during the demo.
        </Typography>
      </Box>

      <Card>
        <CardContent>
          <Typography variant="overline" color="text.secondary">
            From impact to help
          </Typography>
          <Box
            sx={{
              display: 'grid',
              gap: 1.5,
              mt: 1,
              gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(3, minmax(0, 1fr))', xl: 'repeat(6, minmax(0, 1fr))' },
            }}
          >
            {FLOW.map((f, i) => (
              <Box key={f.title} sx={{ p: 2, borderRadius: 2, bgcolor: 'action.hover', position: 'relative' }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', color: 'primary.main' }}>
                  {f.icon}
                  <Typography sx={{ fontWeight: 800, color: 'text.primary' }}>
                    {i + 1}. {f.title}
                  </Typography>
                </Stack>
                <Chip size="small" label={f.who} sx={{ my: 1 }} />
                <Typography variant="body2" color="text.secondary">
                  {f.text}
                </Typography>
                {i < FLOW.length - 1 && (
                  <ArrowForwardRounded
                    sx={{ display: { xs: 'none', xl: 'block' }, position: 'absolute', right: -16, top: 18, color: 'text.disabled' }}
                  />
                )}
              </Box>
            ))}
          </Box>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="overline" color="text.secondary">
            Who gets what — the same complete details for everyone
          </Typography>
          <Box sx={{ overflowX: 'auto' }}>
          <Table size="small" sx={{ mt: 1, minWidth: 640 }}>
            <TableHead>
              <TableRow>
                <TableCell>Recipient</TableCell>
                <TableCell>Receives</TableCell>
                <TableCell>Action in Guardian</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {RECIPIENTS.map((r) => (
                <TableRow key={r.role}>
                  <TableCell sx={{ fontWeight: 800, whiteSpace: 'nowrap' }}>{r.role}</TableCell>
                  <TableCell>{r.gets}</TableCell>
                  <TableCell>{r.does}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          </Box>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>
            No app or login for responders — just the link in the SMS. Each link is unique and unguessable, so it only
            opens that one incident.
          </Typography>
        </CardContent>
      </Card>

      <Typography variant="body2" color="text.secondary">
        Prototype scope: SMS delivery is simulated (open each responder's view from the Live monitor or Showcase). In
        production: SMS / WhatsApp / voice gateway, nearest police station and hospital chosen from the GPS location, link
        expiry, and an authorised handoff to 112 ERSS.
      </Typography>
    </Stack>
  );
}
