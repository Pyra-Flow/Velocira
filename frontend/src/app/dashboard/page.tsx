import { redirect } from "next/navigation";

/** The former dashboard duplicated the project library; keep one clear workspace entry. */
export default function DashboardPage() {
  redirect("/projects");
}
