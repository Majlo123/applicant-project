import type { Metadata } from 'next';
import Link from 'next/link';
import './globals.css';

export const metadata: Metadata = { title: 'People · Person management', description: 'A simple person management application' };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body><header className="topbar"><div className="nav-inner"><Link href="/persons" className="brand"><span className="brand-icon">p.</span> People<span className="brand-caption">PERSON MANAGEMENT</span></Link><span className="workspace"><span className="status-dot" />Local workspace</span></div></header><main>{children}</main><footer>People management <span>Simple records. Clear information.</span></footer></body></html>;
}
