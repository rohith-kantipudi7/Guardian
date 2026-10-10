import type { ReactNode } from 'react';
import { Box, Card, CardContent, Stack, Typography } from '@mui/material';

type Section = { title: string; body: ReactNode };

function LegalPage({ title, updated, sections }: Readonly<{ title: string; updated: string; sections: Section[] }>) {
  return (
    <Stack spacing={3} sx={{ maxWidth: 820 }}>
      <Box>
        <Typography variant="h4">{title}</Typography>
        <Typography variant="body2" color="text.secondary">
          Last updated {updated}
        </Typography>
      </Box>
      <Card>
        <CardContent>
          <Stack spacing={2.5}>
            {sections.map((s) => (
              <Box key={s.title}>
                <Typography variant="h6" sx={{ mb: 0.5 }}>
                  {s.title}
                </Typography>
                <Typography variant="body2" color="text.secondary" component="div">
                  {s.body}
                </Typography>
              </Box>
            ))}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  );
}

export function Privacy() {
  return (
    <LegalPage
      title="Privacy Policy"
      updated="11 October 2026"
      sections={[
        {
          title: 'What this is',
          body: 'Guardian is a proof-of-concept vehicle crash-response system. This policy describes the data the prototype handles. It is not a commercial service.',
        },
        {
          title: 'Data we collect',
          body: 'Driver name, phone number and emergency contacts; vehicle details; GPS position and speed; accelerometer readings around an impact; and, when a crash is detected, photos from the phone camera, a short audio recording and its transcript.',
        },
        {
          title: 'When it is collected',
          body: 'Sensor and location data are read continuously while monitoring is on. Photos and audio are captured on a 10-minute checkpoint and after a detected impact, and are held on the phone and the black-box device.',
        },
        {
          title: 'Who sees it',
          body: 'Only the recipients configured for an incident: police, hospital or ambulance, and the driver\'s emergency contacts. They receive the incident details through a personal link. We do not sell data or use it for advertising.',
        },
        {
          title: 'Storage and retention',
          body: 'Incident records live in the backend database. The black-box device keeps its latest record in memory and loses it on power loss. Media on the phone is replaced by the next capture. Demo data can be cleared with the reset action.',
        },
        {
          title: 'Your choices',
          body: 'You can turn monitoring off in the app, mark yourself safe to close an incident, and ask for your records to be deleted by contacting the project owner.',
        },
        {
          title: 'Security',
          body: 'Responder links use unguessable tokens. The prototype runs on a local network without full transport encryption, so do not use it with real personal data.',
        },
        {
          title: 'Contact',
          body: 'Questions about this policy go to the project owner via the repository issue tracker.',
        },
      ]}
    />
  );
}

export function Terms() {
  return (
    <LegalPage
      title="Terms and Conditions"
      updated="11 October 2026"
      sections={[
        {
          title: 'Prototype status',
          body: 'Guardian is a proof of concept for demonstration. It is not certified as an emergency service and must not be relied on to summon help.',
        },
        {
          title: 'Not a substitute for emergency services',
          body: 'In an emergency call your local emergency number directly. Alerts depend on sensors, battery, network coverage, a SIM for SMS and calls, and may be delayed, missed or wrong.',
        },
        {
          title: 'Detection limits',
          body: 'Crash detection uses thresholds on motion data. It can raise false alarms or miss real events. Review and tune it before any real-world use.',
        },
        {
          title: 'Your responsibilities',
          body: 'Provide accurate contact details, get consent from the people you list as contacts, mount the device as instructed, and keep the phone charged and connected.',
        },
        {
          title: 'Acceptable use',
          body: 'Do not use Guardian to track people without their knowledge, to send false alerts, or to interfere with emergency services.',
        },
        {
          title: 'No warranty and liability',
          body: 'The software is provided as is, without warranty of any kind. To the extent the law allows, the authors are not liable for loss or damage arising from its use.',
        },
        {
          title: 'Changes',
          body: 'These terms may change as the project develops. The date above shows the latest revision.',
        },
      ]}
    />
  );
}
