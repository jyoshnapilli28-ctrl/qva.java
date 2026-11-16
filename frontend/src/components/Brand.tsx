import { Network } from 'lucide-react'

declare const __QVA_CLASSIC_URL__: string
export const classicUrl = import.meta.env.DEV ? __QVA_CLASSIC_URL__ : '/'

export function Brand() {
  return <div className="brand">
    <span className="brand-mark" aria-hidden="true"><Network size={20} /></span>
    <span>QVA<small>NETWORK OPERATIONS</small></span>
  </div>
}
