"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

/** The authenticated home route uses the live project dashboard. */
export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    router.replace("/dashboard");
  }, [router]);

  return null;
}
