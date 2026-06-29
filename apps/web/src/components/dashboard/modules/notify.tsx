"use client";

import { Send, CircleAlert } from "lucide-react";
import { MailIcon } from "@/components/ui/mail-icon";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  MonoChip,
  Panel,
  StatTile,
  StatusBadge,
} from "@/components/dashboard/shared";
import { tint } from "@/components/dashboard/anim";

const templates = [
  { id: "t-1", name: "Order shipped", subject: "Your order #{{id}} has shipped", trigger: "order.shipped", lastSent: "12 min ago" },
  { id: "t-2", name: "Payment failed", subject: "Payment failed for order #{{id}}", trigger: "payment.failed", lastSent: "3 hours ago" },
  { id: "t-3", name: "Weekly summary", subject: "Your weekly project summary", trigger: "schedule.weekly", lastSent: "2 days ago" },
];

const deliveries = [
  { id: "d-1", to: "elena@vidal.me", subject: "Your order #10482 has shipped", status: "delivered" as const, time: "12 min ago" },
  { id: "d-2", to: "pablo@soto.io", subject: "Payment failed for order #7731", status: "delivered" as const, time: "3 hours ago" },
  { id: "d-3", to: "mark@old.invalid", subject: "Your weekly project summary", status: "bounced" as const, time: "2 days ago" },
  { id: "d-4", to: "vera@lago.net", subject: "Your order #7781 has shipped", status: "delivered" as const, time: "2 days ago" },
];

export function NotifyModule() {
  return (
    <>
      <Panel title="Delivery" description="Transactional email delivery for this project.">
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <StatTile Icon={Send} iconBg={tint.emerald.bg} iconColor={tint.emerald.text} label="Sent (24h)" value="1 284" hint="Email" />
          <StatTile Icon={MailIcon} iconBg={tint.indigo.bg} iconColor={tint.indigo.text} label="Delivery rate" value="99.4%" hint="Last 7 days" />
          <StatTile Icon={MailIcon} iconBg={tint.violet.bg} iconColor={tint.violet.text} label="Open rate" value="42.1%" hint="Tracking on" />
          <StatTile Icon={CircleAlert} iconBg={tint.red.bg} iconColor={tint.red.text} label="Bounced (24h)" value="8" hint="Soft · hard" />
        </div>
      </Panel>

      <Panel
        title="Outbound email (SMTP)"
        description="SMTP relay used to send notifications."
        action={<Button size="sm">Send test email</Button>}
      >
        <div className="flex flex-col gap-2.5 text-sm">
          <Row label="Status"><StatusBadge tone="emerald" dot>Connected</StatusBadge></Row>
          <Row label="Host" value={<MonoChip>smtp.example.com:587</MonoChip>} />
          <Row label="Encryption" value="STARTTLS" />
          <Row label="Username" value="notifications@example.com" />
          <Row label="From" value="Example project <notifications@example.com>" />
          <Row label="Reply-to" value="support@example.com" />
        </div>
      </Panel>

      <Panel title="Templates" description="Trigger-keyed email templates with subject + body.">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Template</TableHead>
              <TableHead>Subject</TableHead>
              <TableHead>Trigger</TableHead>
              <TableHead>Last sent</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {templates.map((t) => (
              <TableRow key={t.id}>
                <TableCell className="font-medium text-foreground">{t.name}</TableCell>
                <TableCell className="text-muted-foreground">{t.subject}</TableCell>
                <TableCell><MonoChip>{t.trigger}</MonoChip></TableCell>
                <TableCell className="text-muted-foreground">{t.lastSent}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>

      <Panel title="Recent deliveries" description="Latest outbound emails.">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Recipient</TableHead>
              <TableHead>Subject</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Sent</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {deliveries.map((d) => (
              <TableRow key={d.id}>
                <TableCell><MonoChip>{d.to}</MonoChip></TableCell>
                <TableCell className="text-muted-foreground">{d.subject}</TableCell>
                <TableCell>
                  {d.status === "delivered" ? (
                    <StatusBadge tone="emerald" dot>Delivered</StatusBadge>
                  ) : (
                    <StatusBadge tone="red" dot>Bounced</StatusBadge>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">{d.time}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>
    </>
  );
}

function Row({ label, value, children }: { label: string; value?: React.ReactNode; children?: React.ReactNode }) {
  return (
    <div className="flex items-center gap-3">
      <span className="w-28 shrink-0 text-xs text-muted-foreground">{label}</span>
      <span className="font-medium">{value ?? children}</span>
    </div>
  );
}
