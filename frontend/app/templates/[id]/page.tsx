import { TemplateDetail } from "@/features/templates/TemplateDetail";

export default async function TemplateDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <TemplateDetail templateId={id} />;
}