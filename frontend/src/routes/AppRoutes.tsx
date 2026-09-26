import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { PublicOnlyRoute } from './PublicOnlyRoute';
import { UserOnlyRoute } from './UserOnlyRoute';
import { LoginPage } from '../pages/LoginPage';
import { RegisterPage } from '../pages/RegisterPage';
import { VerifyEmailPage } from '../pages/VerifyEmailPage';
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage';
import { ResetPasswordPage } from '../pages/ResetPasswordPage';
import { AccountPage } from '../pages/AccountPage';
import { VerifyEmailChangePage } from '../pages/VerifyEmailChangePage';
import { TicketListPage } from '../pages/TicketListPage';
import { TicketCreatePage } from '../pages/TicketCreatePage';
import { TicketDetailPage } from '../pages/TicketDetailPage';
import { TicketEditPage } from '../pages/TicketEditPage';
import { NotFoundPage } from '../pages/NotFoundPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Route>

      <Route element={<ProtectedRoute />}>
        <Route path="/tickets" element={<TicketListPage />} />
        <Route path="/tickets/:id" element={<TicketDetailPage />} />
        <Route path="/account" element={<AccountPage />} />
        <Route element={<UserOnlyRoute />}>
          <Route path="/tickets/new" element={<TicketCreatePage />} />
          <Route path="/tickets/:id/edit" element={<TicketEditPage />} />
        </Route>
      </Route>

      {/* メール認証・メールアドレス変更確認リンクの遷移先。ログイン有無に関わらず
          アクセスできる必要があるため、PublicOnlyRoute/ProtectedRouteのどちらにも
          属さない独立したルートにする。 */}
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/verify-email-change" element={<VerifyEmailChangePage />} />

      <Route path="/" element={<Navigate to="/tickets" replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
