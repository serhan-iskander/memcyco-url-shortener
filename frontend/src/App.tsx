import { Route, Routes } from 'react-router-dom';
import { Layout } from './components';
import {
  AnalyticsPage,
  NotFoundPage,
  ShortLinkFormPage,
  ShortLinksPage,
} from './pages';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<ShortLinksPage />} />
        <Route path="links/new" element={<ShortLinkFormPage mode="create" />} />
        <Route path="links/:id/edit" element={<ShortLinkFormPage mode="edit" />} />
        <Route path="links/:id" element={<AnalyticsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
