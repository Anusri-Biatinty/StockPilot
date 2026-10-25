import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import type { Role } from '../api/types';
import { useAuth } from './AuthContext';
import { FullPageSpinner } from '../components/ui/Spinner';

interface Props {
  children: ReactNode;
  /** When set, only these roles may open the route; others are sent to /unauthorized. */
  roles?: Role[];
}

export function ProtectedRoute({ children, roles }: Props) {
  const { user, initializing } = useAuth();
  const location = useLocation();

  if (initializing) return <FullPageSpinner />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (roles && !roles.includes(user.role)) return <Navigate to="/unauthorized" replace />;
  return <>{children}</>;
}
