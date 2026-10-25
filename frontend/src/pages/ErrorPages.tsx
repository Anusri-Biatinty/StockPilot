import { Link } from 'react-router-dom';
import { Button } from '../components/ui/Button';

function ErrorScreen({ code, title, message }: { code: string; title: string; message: string }) {
  return (
    <div className="flex min-h-[70vh] flex-col items-center justify-center px-4 text-center">
      <p className="text-sm font-semibold text-brand-600">{code}</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight">{title}</h1>
      <p className="mt-2 max-w-md text-slate-500">{message}</p>
      <Link to="/" className="mt-6"><Button>Back to dashboard</Button></Link>
    </div>
  );
}

export function NotFoundPage() {
  return <ErrorScreen code="404" title="Page not found" message="The page you are looking for does not exist or has been moved." />;
}

export function UnauthorizedPage() {
  return <ErrorScreen code="403" title="Access denied" message="Your role does not have permission to view this page. Ask an administrator if you need access." />;
}
