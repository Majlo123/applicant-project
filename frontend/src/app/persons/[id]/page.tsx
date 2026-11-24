import { PersonDetail } from '@/features/persons/PersonDetail';
export default async function Detail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PersonDetail id={id} />;
}
