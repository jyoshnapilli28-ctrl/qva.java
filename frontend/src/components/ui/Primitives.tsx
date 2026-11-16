import type { ComponentPropsWithoutRef, ReactNode } from 'react'

export function Button({ variant = 'secondary', className, type = 'button', ...props }:
  ComponentPropsWithoutRef<'button'> & { variant?: 'primary' | 'secondary' | 'icon-button' }) {
  return <button type={type} className={[variant, className].filter(Boolean).join(' ')} {...props} />
}

export function Panel({ icon, title, detail, children }: {
  icon: ReactNode; title: string; detail: string; children: ReactNode
}) {
  return <section className="card panel">
    <div className="panel-head">
      <div className="panel-icon" aria-hidden="true">{icon}</div>
      <div><h2>{title}</h2><p>{detail}</p></div>
    </div>
    {children}
  </section>
}

export function Notice({ kind, children, onRetry }: {
  kind: 'loading' | 'error' | 'empty' | 'info'; children: ReactNode; onRetry?: () => void
}) {
  return <div className={`notice notice-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
    <p>{children}</p>
    {onRetry && <Button onClick={onRetry}>Try again</Button>}
  </div>
}

export function Avatar({ name }: { name: string }) {
  return <span className="avatar" aria-hidden="true">{name.slice(0, 2).toUpperCase()}</span>
}
