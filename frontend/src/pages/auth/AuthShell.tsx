import type { ReactNode } from 'react';
import { BarChart3, Boxes, ShieldCheck } from 'lucide-react';

export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="hidden flex-col justify-between bg-gradient-to-br from-brand-700 via-brand-600 to-indigo-500 p-12 text-white lg:flex">
        <div className="flex items-center gap-3">
          <img src="/favicon.svg" alt="" className="h-10 w-10 rounded-lg ring-2 ring-white/30" />
          <span className="text-xl font-semibold">StockPilot</span>
        </div>
        <div>
          <h2 className="text-3xl font-semibold leading-tight">Inventory, purchasing and sales<br />in one reliable system.</h2>
          <ul className="mt-8 space-y-4 text-brand-100">
            <li className="flex items-center gap-3"><Boxes className="h-5 w-5" /> Real-time stock across every warehouse</li>
            <li className="flex items-center gap-3"><ShieldCheck className="h-5 w-5" /> Approval workflows and full audit trail</li>
            <li className="flex items-center gap-3"><BarChart3 className="h-5 w-5" /> Live analytics and exportable reports</li>
          </ul>
        </div>
        <p className="text-sm text-brand-100">Smart Inventory and Supply Chain Management Platform</p>
      </div>
      <div className="flex items-center justify-center px-4 py-12 sm:px-6">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-2 lg:hidden">
            <img src="/favicon.svg" alt="" className="h-8 w-8" />
            <span className="text-lg font-semibold">StockPilot</span>
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </div>
    </div>
  );
}
