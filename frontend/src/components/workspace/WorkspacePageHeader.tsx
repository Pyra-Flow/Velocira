import type { ReactNode } from "react";

type Props = {
  eyebrow?: string;
  title: string;
  description: string;
  actions?: ReactNode;
};

export default function WorkspacePageHeader({ eyebrow, title, description, actions }: Props) {
  return (
    <div className="workspace-page__header">
      <div>
        {eyebrow && <p className="workspace-page__eyebrow">{eyebrow}</p>}
        <h1 className="workspace-page__title">{title}</h1>
        <p className="workspace-page__description">{description}</p>
      </div>
      {actions && <div className="workspace-page__actions">{actions}</div>}
    </div>
  );
}
