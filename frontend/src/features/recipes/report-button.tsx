"use client";

import { useMutation } from "@tanstack/react-query";
import { Flag } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/display";
import { Field, Textarea } from "@/components/ui/field";
import { useToast } from "@/components/ui/toast";
import { errorMessage } from "@/lib/api/client";
import { reviewApi } from "@/lib/api/endpoints";
import type { ReportTargetType } from "@/types/api";

/** Flags a published recipe or review for moderators. */
export function ReportButton({ targetType, targetId, compact }: { targetType: ReportTargetType; targetId: number; compact?: boolean }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string>();
  const { notify } = useToast();
  const report = useMutation({
    mutationFn: () => reviewApi.report(targetType, targetId, reason.trim()),
    onSuccess: () => {
      notify("Thanks — a moderator will review this.");
      setOpen(false);
      setReason("");
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const fieldId = `report-${targetType}-${targetId}`;
  return (
    <>
      <Button variant="ghost" size="sm" onClick={() => setOpen(true)} icon={<Flag aria-hidden className="h-4 w-4" />}>
        {compact ? <span className="sr-only">Report</span> : "Report"}
      </Button>
      <Modal open={open} onClose={() => setOpen(false)} title={`Report this ${targetType.toLowerCase()}`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button variant="danger" loading={report.isPending} disabled={reason.trim().length === 0} onClick={() => report.mutate()}>Send report</Button>
          </>
        }>
        <Field label="What is wrong?" htmlFor={fieldId} required error={error}>
          <Textarea id={fieldId} value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
        </Field>
      </Modal>
    </>
  );
}
