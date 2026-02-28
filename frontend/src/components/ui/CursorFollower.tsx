"use client";

import { useEffect, useRef, useCallback } from "react";

export default function CursorFollower() {
  const dotRef = useRef<HTMLDivElement>(null);
  const circleRef = useRef<HTMLDivElement>(null);
  const mouse = useRef({ x: 0, y: 0 });
  const circlePos = useRef({ x: 0, y: 0 });
  const rafId = useRef<number>(0);
  const isHovering = useRef(false);

  const animate = useCallback(() => {
    const lerp = 0.15;
    circlePos.current.x += (mouse.current.x - circlePos.current.x) * lerp;
    circlePos.current.y += (mouse.current.y - circlePos.current.y) * lerp;

    if (dotRef.current) {
      dotRef.current.style.transform = `translate(${mouse.current.x - 4}px, ${mouse.current.y - 4}px)`;
    }
    if (circleRef.current) {
      circleRef.current.style.transform = `translate(${circlePos.current.x - 18}px, ${circlePos.current.y - 18}px) scale(${isHovering.current ? 1.5 : 1})`;
    }

    rafId.current = requestAnimationFrame(animate);
  }, []);

  useEffect(() => {
    // Don't render on touch devices
    if (typeof window !== "undefined" && "ontouchstart" in window) return;

    const handleMove = (e: MouseEvent) => {
      mouse.current.x = e.clientX;
      mouse.current.y = e.clientY;
    };

    const handleEnter = () => {
      isHovering.current = true;
      dotRef.current?.classList.add("hover");
      circleRef.current?.classList.add("hover");
    };

    const handleLeave = () => {
      isHovering.current = false;
      dotRef.current?.classList.remove("hover");
      circleRef.current?.classList.remove("hover");
    };

    document.addEventListener("mousemove", handleMove, { passive: true });

    // Track elements that already have listeners to avoid duplicates
    const tracked = new WeakSet<Element>();
    const interactiveSelector =
      "a, button, [role='button'], input, textarea, select, [data-cursor-hover]";
    const addHoverListeners = () => {
      document.querySelectorAll(interactiveSelector).forEach((el) => {
        if (tracked.has(el)) return;
        tracked.add(el);
        el.addEventListener("mouseenter", handleEnter);
        el.addEventListener("mouseleave", handleLeave);
      });
    };

    addHoverListeners();

    // Re-scan when DOM changes (navigation, modals, etc.)
    const observer = new MutationObserver(() => {
      addHoverListeners();
    });
    observer.observe(document.body, { childList: true, subtree: true });

    rafId.current = requestAnimationFrame(animate);

    return () => {
      document.removeEventListener("mousemove", handleMove);
      cancelAnimationFrame(rafId.current);
      observer.disconnect();
      document.querySelectorAll(interactiveSelector).forEach((el) => {
        el.removeEventListener("mouseenter", handleEnter);
        el.removeEventListener("mouseleave", handleLeave);
      });
    };
  }, [animate]);

  // Hide on touch devices via CSS (class already in globals.css)
  return (
    <>
      <div ref={dotRef} className="cursor-dot" aria-hidden="true" />
      <div ref={circleRef} className="cursor-circle" aria-hidden="true" />
    </>
  );
}
