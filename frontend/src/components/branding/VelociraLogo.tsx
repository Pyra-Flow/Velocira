import Image from "next/image";

type Props = {
  size?: number;
  className?: string;
  priority?: boolean;
};

export default function VelociraLogo({ size = 32, className, priority = false }: Props) {
  return (
    <Image
      src="/velocira-logo.png"
      alt=""
      aria-hidden="true"
      width={size}
      height={size}
      priority={priority}
      className={className}
    />
  );
}
