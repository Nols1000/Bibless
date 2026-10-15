import React from 'react';
import ReactDOM from 'react-dom/client';
import '@fontsource-variable/inter';
import { App } from './App.tsx';

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Failed to find the root element');

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
