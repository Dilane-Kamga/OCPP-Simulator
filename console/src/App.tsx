import { Header } from './components/Header';
import { SiteColumn } from './components/SiteColumn';
import { WSClient } from './ws/WSClient';

export default function App() {
  return (
    <div className="min-h-screen flex flex-col">
      <WSClient />
      <Header />
      <main className="flex-1 flex flex-col md:flex-row">
        <SiteColumn site="NEX_TOWER" />
        <SiteColumn site="NEXTERACOM" />
      </main>
    </div>
  );
}
