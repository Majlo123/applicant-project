import { EditPersonForm } from '@/features/persons/PersonForm';
export default async function Edit({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <EditPersonForm id={id} />;
}
