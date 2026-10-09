import { useEffect } from 'react';
import { CircleMarker, MapContainer, TileLayer, Tooltip, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import { severityHex } from '../../format';
import { DEFAULT_CENTER, type MapViewProps } from './types';

function Recenter({ lat, lon }: { lat: number; lon: number }) {
  const map = useMap();
  useEffect(() => {
    map.setView([lat, lon], map.getZoom());
  }, [map, lat, lon]);
  return null;
}

export default function LeafletMap({ markers, height = 320, focusId }: MapViewProps) {
  const focus = markers.find((m) => m.id === focusId) ?? markers[0];
  const center: [number, number] = focus ? [focus.latitude, focus.longitude] : DEFAULT_CENTER;

  return (
    <MapContainer center={center} zoom={14} style={{ height, width: '100%', borderRadius: 10 }}>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <Recenter lat={center[0]} lon={center[1]} />
      {markers.map((m) => (
        <CircleMarker
          key={m.id}
          center={[m.latitude, m.longitude]}
          radius={m.id === focus?.id ? 14 : 10}
          pathOptions={{
            color: severityHex[m.severity ?? 'CRITICAL'],
            fillColor: severityHex[m.severity ?? 'CRITICAL'],
            fillOpacity: 0.6,
          }}
        >
          <Tooltip permanent={m.id === focus?.id}>{m.label}</Tooltip>
        </CircleMarker>
      ))}
    </MapContainer>
  );
}
