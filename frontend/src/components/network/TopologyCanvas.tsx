import { Laptop, Network, Server } from 'lucide-react'
import type { NetworkTopology } from '../../types'
import { formatTimestamp, type DeviceState } from '../../services/operations'
import { Notice, Panel } from '../ui/Primitives'

export function TopologyCanvas({ topology, devices }: {
  topology: NetworkTopology; devices?: DeviceState[]
}) {
  return <Panel icon={<Network size={17} />} title="Network topology" detail="Relay at the centre of the workspace">
    <figure className="topology">
      {topology.host && <div className="relay-node">
        <span className="panel-icon" aria-hidden="true"><Server size={18} /></span>
        <strong>{topology.host.label}</strong>
        <span>{topology.host.status === 'reachable' ? 'Service reachable' : 'Service unavailable'}</span>
      </div>}
      {!!devices?.length && <div className="peer-graph" aria-hidden="true">
        {devices.map(({ device, connected }) => <div className={connected ? 'graph-peer graph-online' : 'graph-peer'} key={device.id}>
          <Laptop size={17} /><strong>{device.name}</strong><small>{connected ? 'Connected' : 'Disconnected'}</small>
        </div>)}
      </div>}
      <figcaption>{topology.host ? 'Relay health is verified by the application.' : 'Waiting for a verified relay status.'}</figcaption>
    </figure>
    <p className="muted vpn-status">VPN status <strong>Unknown</strong></p>
    {devices === undefined && <Notice kind="info">Client topology is unavailable until device status can be verified.</Notice>}
    {devices?.length === 0 && <Notice kind="empty">No approved devices are visible to this account.</Notice>}
    {!!devices?.length && <ul className="device-status-list" aria-label="Client connections">
      {devices.map(({ device, connected, authenticated, connectedAt }) => <li key={device.id}>
        <div><strong>{device.name}</strong><span>@{device.username}</span></div>
        <div><strong>{connected ? 'Connected' : 'Disconnected'}</strong>
          <span>{authenticated ? 'Authenticated' : 'No active session'} · {device.status}</span></div>
        <div><span>Last seen {formatTimestamp(device.lastSeen)}</span>
          {connectedAt && <span>Connected since {formatTimestamp(connectedAt)}</span>}</div>
      </li>)}
    </ul>}
  </Panel>
}
