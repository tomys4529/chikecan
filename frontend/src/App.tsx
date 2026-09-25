import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { Header } from './components/Header';
import { XpPanel } from './components/XpPanel';
import { AppRoutes } from './routes/AppRoutes';
import './App.css';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Header />
        <main className="app-main">
          <AppRoutes />
          <XpPanel />
        </main>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
