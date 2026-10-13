"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Badge, Modal, Pagination, TableWrap } from "@/components/ui/display";
import { Field, Input, Select, Textarea } from "@/components/ui/field";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { useAuth } from "@/features/auth/auth-provider";
import { errorMessage } from "@/lib/api/client";
import { adminApi } from "@/lib/api/endpoints";
import { formatDate } from "@/lib/format";
import type { AdminUserRow, Role, UserStatus } from "@/types/api";

type Pending = { user: AdminUserRow; kind: "status"; value: UserStatus } | { user: AdminUserRow; kind: "role"; value: Role };

export default function AdminUsersPage() {
  const { user: me } = useAuth();
  const qc = useQueryClient();
  const { notify } = useToast();
  const [text, setText] = useState("");
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("");
  const [role, setRole] = useState("");
  const [page, setPage] = useState(0);
  const [pending, setPending] = useState<Pending | null>(null);
  const [reason, setReason] = useState("");
  const users = useQuery({
    queryKey: ["admin", "users", q, status, role, page],
    queryFn: () => adminApi.users({ q: q || undefined, status: status || undefined, role: role || undefined, page }),
  });
  const apply = useMutation({
    mutationFn: (p: Pending) => (p.kind === "status" ? adminApi.setUserStatus(p.user.id, p.value, reason.trim() || undefined) : adminApi.setUserRole(p.user.id, p.value)),
    onSuccess: (u) => {
      notify(`${u.fullName} is now ${u.status.toLowerCase()} (${u.role.toLowerCase()}).`);
      setPending(null);
      setReason("");
      qc.invalidateQueries({ queryKey: ["admin", "users"] });
    },
    onError: (e) => notify(errorMessage(e), "error"),
  });
  function search(e: FormEvent) {
    e.preventDefault();
    setQ(text.trim());
    setPage(0);
  }
  return (
    <div className="flex flex-col gap-5">
      <h1 className="text-2xl font-bold">Users</h1>
      <div className="flex flex-wrap gap-3">
        <form onSubmit={search} role="search" className="flex gap-2">
          <label htmlFor="user-q" className="sr-only">Search name or email</label>
          <Input id="user-q" value={text} onChange={(e) => setText(e.target.value)} placeholder="Name or email" className="w-56" />
          <Button type="submit" variant="secondary">Search</Button>
        </form>
        <label htmlFor="user-status" className="sr-only">Status</label>
        <Select id="user-status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} className="w-40">
          <option value="">All statuses</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option><option value="DELETED">Deleted</option>
        </Select>
        <label htmlFor="user-role" className="sr-only">Role</label>
        <Select id="user-role" value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }} className="w-36">
          <option value="">All roles</option><option value="USER">User</option><option value="ADMIN">Admin</option>
        </Select>
      </div>
      {users.isPending ? <LoadingBlock /> : users.isError ? <ErrorState message={errorMessage(users.error)} onRetry={() => users.refetch()} />
        : users.data.items.length === 0 ? <EmptyState title="No users found" /> : (
          <>
            <TableWrap label="Users">
              <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Joined</th><th>Recipes</th><th>Reviews</th><th>Cooks</th><th>Actions</th></tr></thead>
              <tbody>
                {users.data.items.map((u) => {
                  const self = u.id === me?.id;
                  const deleted = u.status === "DELETED";
                  return (
                    <tr key={u.id}>
                      <td className="font-medium">{u.fullName}{self && <span className="ml-1 text-xs text-muted">(you)</span>}</td>
                      <td>{u.email}</td>
                      <td><Badge tone={u.role === "ADMIN" ? "brand" : "neutral"}>{u.role}</Badge></td>
                      <td><Badge tone={u.status === "ACTIVE" ? "herb" : u.status === "SUSPENDED" ? "danger" : "neutral"}>{u.status}</Badge></td>
                      <td>{formatDate(u.createdAt)}</td>
                      <td>{u.recipeCount}</td><td>{u.reviewCount}</td><td>{u.cookCount}</td>
                      <td className="flex flex-wrap gap-1">
                        {!self && !deleted && (
                          <>
                            <Button size="sm" variant={u.status === "ACTIVE" ? "danger" : "success"}
                              onClick={() => setPending({ user: u, kind: "status", value: u.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE" })}>
                              {u.status === "ACTIVE" ? "Suspend" : "Reactivate"}
                            </Button>
                            <Button size="sm" variant="secondary"
                              onClick={() => setPending({ user: u, kind: "role", value: u.role === "ADMIN" ? "USER" : "ADMIN" })}>
                              {u.role === "ADMIN" ? "Make user" : "Make admin"}
                            </Button>
                          </>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </TableWrap>
            <Pagination page={users.data.page} totalPages={users.data.totalPages} onChange={setPage} />
          </>
        )}
      <Modal open={pending !== null} onClose={() => setPending(null)} title="Confirm account change"
        footer={<><Button variant="ghost" onClick={() => setPending(null)}>Cancel</Button>
          <Button loading={apply.isPending} variant="danger" onClick={() => pending && apply.mutate(pending)}>Confirm</Button></>}>
        {pending && (
          <div className="flex flex-col gap-3 text-sm">
            <p>
              {pending.kind === "status"
                ? `Change ${pending.user.fullName}'s status to ${pending.value.toLowerCase()}?${pending.value === "SUSPENDED" ? " They will be signed out immediately." : ""}`
                : `Change ${pending.user.fullName}'s role to ${pending.value.toLowerCase()}? This is recorded in the audit log.`}
            </p>
            {pending.kind === "status" && (
              <Field label="Reason (recorded in the audit log)" htmlFor="status-reason">
                <Textarea id="status-reason" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
              </Field>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
