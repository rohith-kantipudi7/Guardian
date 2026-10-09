/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  readonly VITE_WS_URL?: string;
  readonly VITE_MAP_PROVIDER?: string;
  readonly VITE_SIMULATOR_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
