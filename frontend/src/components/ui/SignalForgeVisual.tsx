import Image from "next/image";
import { cn } from "@/lib/utils";

export type SignalForgeVisualName =
  | "hero"
  | "intelligence"
  | "documents"
  | "traceability"
  | "auth"
  | "collaboration";

const visuals: Record<SignalForgeVisualName, { src: string; alt: string; label: string }> = {
  hero: {
    src: "/visuals/signal-forge/hero-workflow.webp",
    alt: "Abstract technical system map with linked signal paths",
    label: "Live documentation flow",
  },
  intelligence: {
    src: "/visuals/signal-forge/system-intelligence.webp",
    alt: "Abstract technical workflow network",
    label: "System intelligence",
  },
  documents: {
    src: "/visuals/signal-forge/documentation-package.webp",
    alt: "Abstract documentation artifacts and blueprint details",
    label: "Generated artifacts",
  },
  traceability: {
    src: "/visuals/signal-forge/traceability-evidence.webp",
    alt: "Abstract map of traceability links and evidence nodes",
    label: "Evidence connections",
  },
  auth: {
    src: "/visuals/signal-forge/auth-signal-field.webp",
    alt: "Subtle technical signal field",
    label: "Signal field",
  },
  collaboration: {
    src: "/visuals/signal-forge/collaboration-handoff.webp",
    alt: "Abstract technical collaboration and review handoff network",
    label: "Review handoff",
  },
};

type Props = {
  name: SignalForgeVisualName;
  className?: string;
  priority?: boolean;
  decorative?: boolean;
  label?: string;
};

export default function SignalForgeVisual({ name, className, priority = false, decorative = false, label }: Props) {
  const visual = visuals[name];

  return (
    <figure className={cn("sf-visual", `sf-visual--${name}`, className)}>
      <Image
        src={visual.src}
        alt={decorative ? "" : visual.alt}
        fill
        priority={priority}
        sizes="(max-width: 620px) 100vw, (max-width: 1100px) 55vw, 560px"
        className="sf-visual__image"
      />
      {!decorative && <figcaption className="sf-visual__label">{label ?? visual.label}</figcaption>}
    </figure>
  );
}
