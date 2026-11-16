import { RefreshCw } from 'lucide-react'
import { Button } from './ui/Primitives'

export function PageHeader({ title, detail, refreshing, onRefresh }: {
  title: string; detail: string; refreshing?: boolean; onRefresh?: () => void
}) {
  return <div className="page-head">
    <div><p className="eyebrow">NETWORK OPERATIONS</p><h1>{title}</h1><p className="muted">{detail}</p></div>
    {onRefresh && <Button onClick={() => { if (!refreshing) onRefresh() }} aria-disabled={refreshing}
      aria-label="Refresh snapshot"><RefreshCw size={15} aria-hidden="true" />{refreshing ? 'Refreshing…' : 'Refresh'}</Button>}
  </div>
}
