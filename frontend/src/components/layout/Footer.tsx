"use client";

import Link from "next/link";
import { Zap, Github, Twitter, Linkedin, Mail, MapPin } from "lucide-react";
import { useLocale } from "@/providers/LocaleProvider";
import { FadeIn } from "@/components/ui/Animations";

const socialLinks = [
  { icon: Github, href: "https://github.com/Pyra-Flow", label: "GitHub" },
  { icon: Twitter, href: "https://twitter.com/velocira", label: "Twitter" },
  { icon: Linkedin, href: "https://linkedin.com/company/velocira", label: "LinkedIn" },
];

export default function Footer() {
  const { t } = useLocale();
  const year = new Date().getFullYear();

  const productLinks = [
    { label: t("footer.features"), href: "/features" },
    { label: t("footer.pricing"), href: "/pricing" },
    { label: t("footer.docs"), href: "/docs" },
    { label: "Status", href: "/status" },
  ];

  const companyLinks = [
    { label: t("footer.about"), href: "/about" },
    { label: t("footer.contact"), href: "/contact" },
    { label: t("footer.careers"), href: "/careers" },
  ];

  const legalLinks = [
    { label: t("footer.privacy"), href: "/privacy" },
    { label: t("footer.terms"), href: "/terms" },
  ];

  return (
    <FadeIn>
      <footer className="border-t border-border bg-card/50 backdrop-blur-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
          {/* Gradient line accent at top */}
          <div className="gradient-line mb-10 opacity-40" />
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-10">
            {/* Brand */}
            <div className="sm:col-span-2 lg:col-span-1">
              <Link href="/" className="flex items-center gap-2 mb-3">
                <div className="p-1.5 rounded-lg bg-primary/10">
                  <Zap className="h-5 w-5 text-primary" />
                </div>
                <span className="text-lg font-bold font-display gradient-text">
                  Velocira
                </span>
              </Link>
              <p className="text-sm text-foreground-secondary mb-5 leading-relaxed">
                {t("footer.tagline")}
              </p>
              <div className="flex items-center gap-2">
                {socialLinks.map(({ icon: Icon, href, label }) => (
                  <a
                    key={label}
                    href={href}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label={label}
                    className="p-2 rounded-lg text-foreground-secondary hover:text-primary hover:bg-primary/10 transition-all duration-200 hover:-translate-y-0.5"
                  >
                    <Icon className="h-4 w-4" />
                  </a>
                ))}
              </div>
            </div>

            {/* Product */}
            <div>
              <h4 className="text-sm font-semibold text-foreground mb-4 font-display">
                {t("footer.product")}
              </h4>
              <ul className="space-y-2.5">
                {productLinks.map((link) => (
                  <li key={link.href}>
                    <Link
                      href={link.href}
                      className="text-sm text-foreground-secondary hover:text-primary transition-colors"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>

            {/* Company */}
            <div>
              <h4 className="text-sm font-semibold text-foreground mb-4 font-display">
                {t("footer.company")}
              </h4>
              <ul className="space-y-2.5">
                {companyLinks.map((link) => (
                  <li key={link.href}>
                    <Link
                      href={link.href}
                      className="text-sm text-foreground-secondary hover:text-primary transition-colors"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>

            {/* Legal */}
            <div>
              <h4 className="text-sm font-semibold text-foreground mb-4 font-display">
                {t("footer.legal")}
              </h4>
              <ul className="space-y-2.5">
                {legalLinks.map((link) => (
                  <li key={link.href}>
                    <Link
                      href={link.href}
                      className="text-sm text-foreground-secondary hover:text-primary transition-colors"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>

            {/* Contact */}
            <div>
              <h4 className="text-sm font-semibold text-foreground mb-4 font-display">
                {t("footer.contact")}
              </h4>
              <ul className="space-y-3">
                <li>
                  <a
                    href="mailto:hello@velocira.com"
                    className="flex items-center gap-2 text-sm text-foreground-secondary hover:text-primary transition-colors"
                  >
                    <Mail className="h-4 w-4 shrink-0" />
                    hello@velocira.com
                  </a>
                </li>
                <li className="flex items-center gap-2 text-sm text-foreground-secondary">
                  <MapPin className="h-4 w-4 shrink-0" />
                  Cairo, Egypt
                </li>
              </ul>
            </div>
          </div>

          {/* Bottom Bar */}
          <div className="mt-12 pt-6 border-t border-border flex flex-col sm:flex-row items-center justify-between gap-3">
            <p className="text-sm text-foreground-secondary">
              {t("footer.rights", { year: String(year) })}
            </p>
            <p className="text-sm text-foreground-secondary">
              Part of the <span className="font-semibold text-primary">PyraFlow</span> Suite
            </p>
          </div>
        </div>
      </footer>
    </FadeIn>
  );
}
