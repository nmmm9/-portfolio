import { BrowserRouter, Routes, Route } from "react-router-dom";
import ImpactDashboard from "./pages/ImpactDashboard";
import EmissionsDetail from "./pages/EmissionsDetail";
import DonationsDetail from "./pages/DonationsDetail";
import VolunteerDetail from "./pages/VolunteerDetail";
import PositiveNewsDetail from "./pages/PositiveNewsDetail";
import AIChatPage from "./pages/AIChatPage";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ImpactDashboard />} />
        <Route path="/emissions" element={<EmissionsDetail />} />
        <Route path="/donations" element={<DonationsDetail />} />
        <Route path="/volunteer" element={<VolunteerDetail />} />
        <Route path="/positive-news" element={<PositiveNewsDetail />} />
        <Route path="/ai-chat" element={<AIChatPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
