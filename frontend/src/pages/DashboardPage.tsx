import { AlertTriangle, Boxes, ClipboardList, DollarSign, PackageX, ShoppingCart, Tags, TrendingUp } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { dashboardApi } from '../api/endpoints';
import { MovementBadge, StatusBadge } from '../components/ui/Badge';
import { PageHeader } from '../components/ui/PageHeader';
import { StatCard } from '../components/ui/StatCard';
import { EmptyState, ErrorState, LoadingBlock } from '../components/ui/States';
import { useApi } from '../hooks/useApi';
import { formatCompactMoney, formatMoney, formatNumber, isoDate, timeAgo } from '../lib/format';

const CHART_COLORS = ['#6366f1', '#14b8a6', '#f59e0b', '#ec4899', '#0ea5e9', '#8b5cf6', '#84cc16', '#f97316'];
const AXIS = { fontSize: 12, fill: '#94a3b8' };

type Preset = '30d' | '90d' | '6m' | '12m' | 'ytd' | 'custom';

function rangeFor(preset: Preset): { from: string; to: string } {
  const today = new Date();
  const from = new Date(today);
  if (preset === '30d') from.setDate(today.getDate() - 29);
  if (preset === '90d') from.setDate(today.getDate() - 89);
  if (preset === '6m') { from.setMonth(today.getMonth() - 5); from.setDate(1); }
  if (preset === '12m') { from.setMonth(today.getMonth() - 11); from.setDate(1); }
  if (preset === 'ytd') { from.setMonth(0); from.setDate(1); }
  return { from: isoDate(from), to: isoDate(today) };
}

function ChartCard({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="card p-5">
      <div className="mb-4 flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

const tooltipStyle = { borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12 };

export function DashboardPage() {
  const [preset, setPreset] = useState<Preset>('6m');
  const [range, setRange] = useState(rangeFor('6m'));
  const { data, error, loading, reload } = useApi(() => dashboardApi.get(range), [range.from, range.to]);

  const stocked = data?.stockByCategory.filter((c) => Number(c.value) > 0) ?? [];

  const choosePreset = (p: Preset) => {
    setPreset(p);
    if (p !== 'custom') setRange(rangeFor(p));
  };

  const filters = (
    <div className="flex flex-wrap items-center gap-2">
      <select aria-label="Date range" className="input w-auto" value={preset} onChange={(e) => choosePreset(e.target.value as Preset)}>
        <option value="30d">Last 30 days</option>
        <option value="90d">Last 90 days</option>
        <option value="6m">Last 6 months</option>
        <option value="12m">Last 12 months</option>
        <option value="ytd">Year to date</option>
        <option value="custom">Custom range</option>
      </select>
      {preset === 'custom' && (
        <>
          <input type="date" aria-label="From date" className="input w-auto" value={range.from} max={range.to}
                 onChange={(e) => e.target.value && setRange({ ...range, from: e.target.value })} />
          <input type="date" aria-label="To date" className="input w-auto" value={range.to} min={range.from}
                 onChange={(e) => e.target.value && setRange({ ...range, to: e.target.value })} />
        </>
      )}
    </div>
  );

  return (
    <>
      <PageHeader title="Dashboard" description="Live figures computed from your inventory, purchasing and sales data." actions={filters} />
      {error ? <div className="card"><ErrorState message={error} onRetry={reload} /></div>
        : !data ? <LoadingBlock label="Crunching the numbers…" />
        : (
          <div className={loading ? 'opacity-60 transition-opacity' : ''}>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <StatCard label="Inventory value (at cost)" value={formatMoney(data.kpis.inventoryValue)} icon={<DollarSign className="h-5 w-5" />}
                        hint={`${formatNumber(data.kpis.totalUnits)} units in ${data.kpis.totalWarehouses} warehouses`} to="/reports?report=valuation" />
              <StatCard label="Active products" value={formatNumber(data.kpis.totalProducts)} icon={<Boxes className="h-5 w-5" />}
                        hint={<span className="inline-flex items-center gap-1"><Tags className="h-3 w-3" />{data.kpis.totalCategories} categories</span>} to="/products" tone="blue" />
              <StatCard label="Low stock" value={formatNumber(data.kpis.lowStockCount)} icon={<AlertTriangle className="h-5 w-5" />}
                        hint="At or below reorder threshold" to="/products?stockStatus=LOW_STOCK" tone="amber" />
              <StatCard label="Out of stock" value={formatNumber(data.kpis.outOfStockCount)} icon={<PackageX className="h-5 w-5" />}
                        hint="No units in any warehouse" to="/products?stockStatus=OUT_OF_STOCK" tone="red" />
              <StatCard label="Sales revenue" value={formatMoney(data.kpis.salesRevenue)} icon={<TrendingUp className="h-5 w-5" />}
                        hint={`${formatNumber(data.kpis.salesOrderCount)} orders in range`} to={`/reports?report=sales&from=${data.from}&to=${data.to}`} tone="green" />
              <StatCard label="Purchase spend" value={formatMoney(data.kpis.purchaseSpend)} icon={<DollarSign className="h-5 w-5" />}
                        hint="Approved purchase orders in range" to={`/reports?report=purchases-by-supplier&from=${data.from}&to=${data.to}`} tone="blue" />
              <StatCard label="Pending purchase orders" value={formatNumber(data.kpis.pendingPurchaseOrders)} icon={<ClipboardList className="h-5 w-5" />}
                        hint="Submitted, approved or partially received" to="/purchase-orders?status=SUBMITTED" />
              <StatCard label="Pending sales orders" value={formatNumber(data.kpis.pendingSalesOrders)} icon={<ShoppingCart className="h-5 w-5" />}
                        hint="Draft or confirmed, not yet shipped" to="/sales-orders?status=CONFIRMED" tone="amber" />
            </div>

            <div className="mt-6 grid gap-6 xl:grid-cols-3">
              <div className="xl:col-span-2">
                <ChartCard title="Monthly sales vs. purchases">
                  <div className="h-72">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={data.trends} margin={{ left: 8, right: 8 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#94a3b833" vertical={false} />
                        <XAxis dataKey="month" tick={AXIS} tickLine={false} axisLine={false} />
                        <YAxis tick={AXIS} tickLine={false} axisLine={false} tickFormatter={(v) => formatCompactMoney(v)} width={64} />
                        <Tooltip contentStyle={tooltipStyle} formatter={(v) => formatMoney(Number(v))} cursor={{ fill: '#94a3b822' }} />
                        <Legend wrapperStyle={{ fontSize: 12 }} />
                        <Bar dataKey="sales" name="Sales" fill="#6366f1" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="purchases" name="Purchases" fill="#14b8a6" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </ChartCard>
              </div>
              <ChartCard title="Stock value by category">
                {stocked.length === 0 ? <EmptyState title="No stock yet" /> : (
                  <div className="h-72">
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie data={stocked} dataKey="value" nameKey="name" innerRadius={55} outerRadius={90} paddingAngle={2}>
                          {stocked.map((c, i) => <Cell key={c.categoryId} fill={CHART_COLORS[i % CHART_COLORS.length]} />)}
                        </Pie>
                        <Tooltip contentStyle={tooltipStyle} formatter={(v) => formatMoney(Number(v))} />
                        <Legend wrapperStyle={{ fontSize: 12 }} />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                )}
              </ChartCard>
            </div>

            <div className="mt-6 grid gap-6 xl:grid-cols-3">
              <ChartCard title="Top-selling products" action={<Link to={`/reports?report=product-performance&from=${data.from}&to=${data.to}`} className="text-xs font-medium text-brand-600">Full report</Link>}>
                {data.topProducts.length === 0 ? <EmptyState title="No sales in this period" /> : (
                  <div className="h-64">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={data.topProducts} layout="vertical" margin={{ left: 8, right: 16 }}>
                        <XAxis type="number" tick={AXIS} tickLine={false} axisLine={false} tickFormatter={(v) => formatCompactMoney(v)} />
                        <YAxis type="category" dataKey="sku" tick={AXIS} tickLine={false} axisLine={false} width={72} />
                        <Tooltip contentStyle={tooltipStyle} cursor={{ fill: '#94a3b822' }}
                                 formatter={(v, _n, item) => [`${formatMoney(Number(v))} · ${formatNumber(item.payload.quantitySold)} units`, item.payload.name]} />
                        <Bar dataKey="revenue" fill="#8b5cf6" radius={[0, 4, 4, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                )}
              </ChartCard>

              <ChartCard title="Stock alerts" action={<Link to="/reports?report=low-stock" className="text-xs font-medium text-brand-600">Reorder report</Link>}>
                {data.alerts.length === 0 ? <EmptyState title="All stock levels are healthy" /> : (
                  <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                    {data.alerts.map((a) => (
                      <li key={a.productId}>
                        <Link to={`/products/${a.productId}`} className="flex items-center justify-between gap-3 py-2.5 hover:text-brand-600">
                          <span className="min-w-0">
                            <span className="block truncate text-sm font-medium">{a.name}</span>
                            <span className="text-xs text-slate-500">{a.sku} · {formatNumber(a.onHand)} on hand / threshold {a.reorderThreshold}</span>
                          </span>
                          <StatusBadge status={a.status} />
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </ChartCard>

              <ChartCard title="Recent inventory movements" action={<Link to="/inventory/movements" className="text-xs font-medium text-brand-600">View all</Link>}>
                {data.recentMovements.length === 0 ? <EmptyState title="No movements yet" /> : (
                  <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                    {data.recentMovements.map((m) => (
                      <li key={m.id} className="flex items-center justify-between gap-3 py-2.5">
                        <span className="min-w-0">
                          <Link to={`/products/${m.productId}`} className="block truncate text-sm font-medium hover:text-brand-600">{m.productName}</Link>
                          <span className="text-xs text-slate-500">{m.warehouseCode} · {timeAgo(m.createdAt)}</span>
                        </span>
                        <span className="flex shrink-0 items-center gap-2">
                          <span className={`text-sm font-semibold tabular-nums ${m.quantity > 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                            {m.quantity > 0 ? '+' : ''}{m.quantity}
                          </span>
                          <MovementBadge type={m.type} />
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </ChartCard>
            </div>
          </div>
        )}
    </>
  );
}
