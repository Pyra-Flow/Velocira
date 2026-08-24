"use client";

import { useState } from "react";
import {
  ArrowRight,
  CheckCircle2,
  Clock3,
  FileCheck2,
  Lightbulb,
  ListChecks,
  Pencil,
  Users,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";
import { projectApi, type ProjectResponse } from "@/lib/api";

type Props = {
  project: ProjectResponse;
  created?: boolean;
  onBegin: () => void;
  onUpdated: () => void;
};

function typeLabel(type: string) {
  return type.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export default function ProjectBriefing({ project, created = false, onBegin, onUpdated }: Props) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description);
  const [audience, setAudience] = useState(project.targetAudience ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    if (name.trim().length < 3 || description.trim().length < 10) {
      setError("Add a clear project name and a short description before saving.");
      return;
    }
    setSaving(true);
    setError(null);
    const response = await projectApi.update(project.id, {
      name: name.trim(),
      description: description.trim(),
      targetAudience: audience.trim() || undefined,
    });
    setSaving(false);
    if (!response.success) {
      setError(response.message || "We couldn’t save those changes. Try again.");
      return;
    }
    setEditing(false);
    onUpdated();
  };

  return (
    <section className="project-briefing" aria-labelledby="project-briefing-title">
      <ol className="journey-steps" aria-label="Project setup progress">
        <li className="is-complete"><span><CheckCircle2 aria-label="Complete" /></span><strong>Create</strong></li>
        <li className="is-current"><span>2</span><strong>Review brief</strong></li>
        <li><span>3</span><strong>Answer questions</strong></li>
      </ol>

      {created && <div className="project-briefing__success" role="status"><CheckCircle2 aria-hidden="true" /><span><strong>Project created</strong>Your starting brief is ready. Review it before the questions begin.</span></div>}

      <header className="project-briefing__header">
        <p className="public-eyebrow">Project briefing</p>
        <h1 id="project-briefing-title">{project.name}</h1>
        <p>{project.description}</p>
        <div className="project-briefing__meta" aria-label="Project details">
          <span>{typeLabel(project.type)}</span>
          {project.targetAudience && <span><Users aria-hidden="true" /> {project.targetAudience}</span>}
          <span><Clock3 aria-hidden="true" /> About 8–12 minutes</span>
        </div>
      </header>

      <div className="project-briefing__outcome">
        <FileCheck2 aria-hidden="true" />
        <div><h2>What you’ll receive</h2><p>A review-ready requirements baseline with the decisions, open questions, acceptance guidance, and source context kept together.</p></div>
      </div>

      <section className="project-briefing__process" aria-labelledby="next-title">
        <div><p className="public-eyebrow">Before you begin</p><h2 id="next-title">What you’ll do next</h2></div>
        <ol>
          <li><span>1</span><div><strong>Answer one question at a time</strong><p>Each question covers a decision that changes the final requirements.</p></div></li>
          <li><span>2</span><div><strong>Review your answers</strong><p>Go back, revise, or leave a decision explicitly open before generating.</p></div></li>
          <li><span>3</span><div><strong>Generate the requirements</strong><p>Choose the documentation depth after the discovery is complete.</p></div></li>
        </ol>
      </section>

      <details className="project-briefing__tips">
        <summary><Lightbulb aria-hidden="true" /> Helpful things to have nearby</summary>
        <ul>
          <li>The main users and the job each one needs to complete</li>
          <li>What the first release must include—and what can wait</li>
          <li>Known constraints, integrations, policies, or risks</li>
        </ul>
        <p>You do not need every answer now. “Not sure yet” keeps the gap visible without inventing a decision.</p>
      </details>

      {editing && (
        <section className="project-briefing__edit" aria-labelledby="edit-project-title">
          <h2 id="edit-project-title">Edit project details</h2>
          <div className="project-briefing__edit-fields">
            <Input label="Project name" value={name} onChange={(event) => setName(event.target.value)} />
            <Input label="Primary audience" value={audience} onChange={(event) => setAudience(event.target.value)} placeholder="For example: prospective clients" />
          </div>
          <label htmlFor="project-briefing-description">Project description</label>
          <textarea id="project-briefing-description" rows={4} value={description} onChange={(event) => setDescription(event.target.value)} />
          {error && <p role="alert" className="project-create-error">{error}</p>}
          <div><Button onClick={() => void save()} loading={saving}>Save changes</Button><Button variant="ghost" onClick={() => { setEditing(false); setError(null); }}>Cancel</Button></div>
        </section>
      )}

      <div className="project-briefing__actions">
        <div><ListChecks aria-hidden="true" /><span><strong>Answers save automatically</strong>You can leave and resume without starting over.</span></div>
        <div><Button variant="outline" icon={<Pencil className="h-4 w-4" />} onClick={() => setEditing((current) => !current)}>Edit project details</Button><Button size="lg" icon={<ArrowRight className="h-4 w-4" />} onClick={onBegin}>Begin questions</Button></div>
      </div>
    </section>
  );
}
