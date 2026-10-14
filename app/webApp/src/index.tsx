import React from 'react';
import ReactDOM from 'react-dom/client';
import { RunOrganiser } from 'sharedLogic';
import { App } from './App.tsx';

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Failed to find the root element');

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <App organiser={RunOrganiser.create()} />
  </React.StrictMode>
);
