import { ButtonLink } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/states";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-xl px-4 py-16">
      <EmptyState title="Page not found" description="The page you are looking for does not exist or has moved."
        action={<ButtonLink href="/" variant="secondary">Go home</ButtonLink>} />
    </div>
  );
}
