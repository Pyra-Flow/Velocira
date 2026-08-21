"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowRight,
  BrainCircuit,
  Check,
  CheckCircle2,
  Lightbulb,
  Loader2,
  Pencil,
  RotateCcw,
  Sparkles,
} from "lucide-react";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import SignalMeter from "@/components/ui/SignalMeter";
import {
  interviewApi,
  type InterviewAnswerDisposition,
  type InterviewAnswerResponse,
  type InterviewQuestionResponse,
  type InterviewSessionResponse,
} from "@/lib/api";

type Props = {
  projectId: string;
  onUpdated?: () => void;
  onReadinessChanged?: (generationReady: boolean) => void;
  onContinueToSrs?: () => void;
};

function label(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function questionFromAnswer(answer: InterviewAnswerResponse): InterviewQuestionResponse {
  return {
    questionKey: answer.questionKey,
    category: answer.category,
    questionText: answer.questionText,
    whyWeAsk: answer.whyWeAsk,
    riskLevel: "MEDIUM",
    allowsMultiple: answer.allowsMultiple,
    options: answer.options,
    selectionReason: answer.selectionReason ?? "This question was part of the captured discovery path.",
    missingRequirement: answer.missingRequirement ?? "This answer supplies context used by downstream documents.",
    sourceContext: answer.sourceContext ?? [],
    confirmedContextUsed: answer.confirmedContextUsed ?? [],
    assumptionsToValidate: answer.assumptionsToValidate ?? [],
    candidateScores: answer.candidateScores ?? [],
    planner: answer.planner ?? "persisted-question-plan",
    model: answer.model ?? "unknown",
  };
}

function sourceLabel(source: string) {
  if (source.startsWith("project:")) return `Project ${source.slice("project:".length).replaceAll("-", " ")}`;
  if (source.startsWith("answer:")) return `Earlier answer: ${label(source.slice("answer:".length))}`;
  if (source.startsWith("open-question:")) return `Open gap: ${label(source.slice("open-question:".length))}`;
  if (source.startsWith("evidence:")) return "Approved project evidence";
  return source;
}

export default function InterviewPanel({ projectId, onUpdated, onReadinessChanged, onContinueToSrs }: Props) {
  const [session, setSession] = useState<InterviewSessionResponse | null>(null);
  const [answerText, setAnswerText] = useState("");
  const [selectedOptionKeys, setSelectedOptionKeys] = useState<string[]>([]);
  const [editing, setEditing] = useState<InterviewAnswerResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activeQuestion = editing ? questionFromAnswer(editing) : session?.nextQuestion ?? null;
  const readinessPercent = session
    ? Math.round((session.readiness.answeredRequiredCategories / Math.max(session.readiness.requiredCategoryCount, 1)) * 100)
    : 0;

  const applySession = (next: InterviewSessionResponse, notifyParent = true) => {
    setSession(next);
    setError(null);
    onReadinessChanged?.(next.readiness.generationReady);
    if (notifyParent) onUpdated?.();
  };

  useEffect(() => {
    let active = true;
    const start = async () => {
      setLoading(true);
      const response = await interviewApi.start(projectId);
      if (!active) return;
      if (response.success && response.data) {
        applySession(response.data, false);
      } else {
        setError(response.message || "The discovery interview could not be loaded.");
      }
      setLoading(false);
    };
    void start();
    return () => { active = false; };
    // A session starts only when its project workspace changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const submit = async (disposition: InterviewAnswerDisposition) => {
    if (!activeQuestion) return;
    if (disposition === "ANSWERED" && !answerText.trim() && selectedOptionKeys.length === 0) {
      setError("Choose an answer or add your own details. You can also choose Unknown or Skip so the gap stays visible.");
      return;
    }
    setSubmitting(true);
    setError(null);
    const payload = {
      questionKey: activeQuestion.questionKey,
      disposition,
      answerText: disposition === "ANSWERED" && answerText.trim() ? answerText.trim() : undefined,
      selectedOptionKeys: disposition === "ANSWERED" ? selectedOptionKeys : undefined,
    };
    const response = editing
      ? await interviewApi.reviseAnswer(projectId, editing.id, payload)
      : await interviewApi.answer(projectId, payload);
    if (response.success && response.data) {
      applySession(response.data);
      setAnswerText("");
      setSelectedOptionKeys([]);
      setEditing(null);
    } else {
      setError(response.message || "The answer could not be saved.");
    }
    setSubmitting(false);
  };

  const editAnswer = (answer: InterviewAnswerResponse) => {
    setEditing(answer);
    setAnswerText(answer.customAnswerText ?? answer.answerText ?? "");
    setSelectedOptionKeys(answer.selectedOptionKeys ?? []);
    setError(null);
  };

  const updateSelectedOptions = (optionKey: string) => {
    if (!activeQuestion) return;
    setSelectedOptionKeys((current) => {
      if (!activeQuestion.allowsMultiple) return current.includes(optionKey) ? [] : [optionKey];
      if (optionKey === "not-decided") return current.includes(optionKey) ? [] : [optionKey];
      const decided = current.filter((key) => key !== "not-decided");
      return decided.includes(optionKey)
        ? decided.filter((key) => key !== optionKey)
        : [...decided, optionKey];
    });
  };

  const reopen = async () => {
    setSubmitting(true);
    const response = await interviewApi.reopen(projectId);
    if (response.success && response.data) {
      applySession(response.data);
      setEditing(null);
      setAnswerText("");
      setSelectedOptionKeys([]);
    } else {
      setError(response.message || "The brief could not be reopened.");
    }
    setSubmitting(false);
  };

  if (loading) {
    return <Card className="flex items-center gap-2 py-8 text-sm text-foreground-secondary"><Loader2 className="h-5 w-5 animate-spin text-accent" /> Preparing your tailored discovery…</Card>;
  }
  if (!session) {
    return <Card><p className="text-sm text-error" role="alert">{error ?? "Discovery is unavailable."}</p></Card>;
  }

  return (
    <section className="space-y-5" aria-labelledby="discovery-heading">
      <div className="overflow-hidden rounded-lg border border-accent/25 bg-card p-5 sm:p-7">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="max-w-2xl">
            <div className="sf-meta flex items-center gap-2 font-semibold uppercase tracking-[0.16em] text-accent"><BrainCircuit className="h-4 w-4" /> Adaptive discovery</div>
            <h2 id="discovery-heading" className="mt-2 text-2xl font-semibold tracking-tight text-foreground">A product consultant, one useful question at a time.</h2>
            <p className="mt-2 text-sm leading-6 text-foreground-secondary">Your title, description, and earlier answers steer the planner. It asks for evidence that changes the documentation—not filler.</p>
          </div>
          <div className="min-w-52 border-l border-accent/35 pl-4">
            <SignalMeter
              value={readinessPercent}
              label="Required evidence"
              detail={`${session.readiness.answeredRequiredCategories} / ${session.readiness.requiredCategoryCount} areas`}
              tone={session.readiness.generationReady ? "accent" : "warning"}
            />
          </div>
        </div>
      </div>

      {error && <Card className="border-error/30 bg-error/5"><p className="text-sm text-error" role="alert">{error}</p></Card>}

      <AnimatePresence mode="wait">
        {activeQuestion ? (
          <motion.div key={editing ? `edit-${editing.id}` : activeQuestion.questionKey} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }} transition={{ duration: 0.2 }}>
            <Card className="overflow-hidden p-0">
              <div className="border-b border-border bg-background-secondary/45 px-5 py-4 sm:px-6">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="sf-meta font-semibold uppercase tracking-[0.16em] text-accent">{label(activeQuestion.category)} · {activeQuestion.riskLevel.toLowerCase()} impact</p>
                    <h3 className="mt-2 max-w-3xl text-xl font-semibold leading-snug text-foreground">{activeQuestion.questionText}</h3>
                  </div>
                  {editing && <Button variant="ghost" size="sm" onClick={() => { setEditing(null); setAnswerText(""); setSelectedOptionKeys([]); }} disabled={submitting}>Cancel</Button>}
                </div>
              </div>
              <div className="space-y-4 p-5 sm:p-6">
                <div className="flex gap-3 rounded-md border border-warning/20 bg-warning/5 p-3.5 text-sm text-foreground-secondary">
                  <Lightbulb className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
                  <p><span className="font-semibold text-foreground">Why this matters:</span> {activeQuestion.whyWeAsk}</p>
                </div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-md border border-border bg-background-secondary/35 p-3.5 text-sm text-foreground-secondary">
                    <p className="font-semibold text-foreground">Why this question now</p>
                    <p className="mt-1 leading-5">{activeQuestion.selectionReason}</p>
                  </div>
                  <div className="rounded-md border border-border bg-background-secondary/35 p-3.5 text-sm text-foreground-secondary">
                    <p className="font-semibold text-foreground">What it unlocks</p>
                    <p className="mt-1 leading-5">{activeQuestion.missingRequirement}</p>
                  </div>
                </div>
                {activeQuestion.sourceContext.length > 0 && (
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.12em] text-foreground-secondary">Context considered</p>
                    <div className="mt-2 flex flex-wrap gap-2">
                      {activeQuestion.sourceContext.map((source) => <span key={source} className="rounded-full border border-border bg-card px-2.5 py-1 text-xs text-foreground-secondary">{sourceLabel(source)}</span>)}
                    </div>
                  </div>
                )}
                {(activeQuestion.confirmedContextUsed.length > 0 || activeQuestion.assumptionsToValidate.length > 0) && (
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div className="rounded-md border border-success/20 bg-success/5 p-3.5">
                      <p className="text-xs font-semibold uppercase tracking-[0.12em] text-success">Confirmed context used</p>
                      <ul className="mt-2 space-y-1 text-xs leading-5 text-foreground-secondary">
                        {activeQuestion.confirmedContextUsed.map((fact) => <li key={fact}>• {fact}</li>)}
                      </ul>
                    </div>
                    <div className="rounded-md border border-warning/20 bg-warning/5 p-3.5">
                      <p className="text-xs font-semibold uppercase tracking-[0.12em] text-warning">Still needs validation</p>
                      {activeQuestion.assumptionsToValidate.length > 0
                        ? <ul className="mt-2 space-y-1 text-xs leading-5 text-foreground-secondary">{activeQuestion.assumptionsToValidate.map((item) => <li key={item}>• {item}</li>)}</ul>
                        : <p className="mt-2 text-xs leading-5 text-foreground-secondary">No assumptions were introduced for this question.</p>}
                    </div>
                  </div>
                )}
                {activeQuestion.candidateScores.length > 0 && (
                  <details className="rounded-md border border-border bg-background-secondary/25 p-3.5">
                    <summary className="cursor-pointer text-xs font-semibold uppercase tracking-[0.12em] text-foreground-secondary">Review question ranking</summary>
                    <div className="mt-3 space-y-2">
                      {activeQuestion.candidateScores.slice(0, 5).map((candidate) => (
                        <div key={candidate.key} className="flex items-start justify-between gap-4 text-xs text-foreground-secondary">
                          <div><span className="font-medium text-foreground">{label(candidate.category)}</span><span className="ml-2">{candidate.reasons.join("; ")}</span></div>
                          <span className="shrink-0 font-mono text-foreground">{candidate.score}</span>
                        </div>
                      ))}
                    </div>
                  </details>
                )}
                {activeQuestion.options.length > 0 && (
                  <fieldset disabled={submitting}>
                    <legend className="text-sm font-semibold text-foreground">Decision patterns to consider</legend>
                    <p className="mt-1 text-xs text-foreground-secondary">These are possibilities with consequences, not answers assumed for your project. {activeQuestion.allowsMultiple ? "Choose all that apply." : "Choose the closest fit."}</p>
                    <div className="mt-3 grid gap-2 sm:grid-cols-2">
                      {activeQuestion.options.map((option) => {
                        const selected = selectedOptionKeys.includes(option.key);
                        return (
                          <label
                            key={option.key}
                            className={`flex min-h-20 cursor-pointer gap-3 rounded-md border p-3 transition-colors focus-within:ring-2 focus-within:ring-accent/30 ${selected ? "border-accent bg-accent-light/50" : "border-border bg-card hover:border-accent/50 hover:bg-card-hover"}`}
                          >
                            <input
                              className="sr-only"
                              type={activeQuestion.allowsMultiple ? "checkbox" : "radio"}
                              name={`interview-choice-${activeQuestion.questionKey}`}
                              checked={selected}
                              onChange={() => updateSelectedOptions(option.key)}
                              disabled={submitting}
                            />
                            <span className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center border ${activeQuestion.allowsMultiple ? "rounded" : "rounded-full"} ${selected ? "border-accent bg-accent text-on-primary" : "border-input-border bg-input-bg text-transparent"}`} aria-hidden="true">
                              <Check className="h-3.5 w-3.5" strokeWidth={3} />
                            </span>
                            <span><span className="block text-sm font-medium text-foreground">{option.label}</span><span className="mt-0.5 block text-xs leading-5 text-foreground-secondary">{option.description}</span></span>
                          </label>
                        );
                      })}
                    </div>
                  </fieldset>
                )}
                <label className="block">
                  <span className="text-sm font-semibold text-foreground">Your answer or another approach <span className="font-normal text-foreground-secondary">(optional when a pattern fits)</span></span>
                  <textarea
                    rows={4}
                    value={answerText}
                    onChange={(event) => setAnswerText(event.target.value)}
                    placeholder="Add context, a missing option, or a completely custom answer in your own words."
                    className="mt-2 w-full resize-y rounded-md border border-input-border bg-input-bg px-4 py-3 text-foreground placeholder:text-placeholder focus:border-input-focus focus:outline-none focus:ring-2 focus:ring-accent/20"
                    disabled={submitting}
                  />
                </label>
                <div className="flex flex-col gap-2 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between">
                  <p className="text-xs text-foreground-secondary">Your selected choices and custom details are saved together.</p>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="ghost" size="sm" onClick={() => void submit("SKIPPED")} disabled={submitting}>Skip</Button>
                    <Button variant="outline" size="sm" onClick={() => void submit("UNKNOWN")} disabled={submitting}>I don&apos;t know</Button>
                    <Button size="sm" onClick={() => void submit("ANSWERED")} loading={submitting} icon={<ArrowRight className="h-4 w-4" />}>Save & continue</Button>
                  </div>
                </div>
              </div>
            </Card>
          </motion.div>
        ) : session.readiness.generationReady ? (
          <motion.div key="questions-complete" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.25 }}>
            <Card className="border-accent/30 bg-card p-6 sm:p-7">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex gap-3"><CheckCircle2 className="mt-0.5 h-6 w-6 shrink-0 text-success" /><div><p className="text-xs font-semibold uppercase tracking-[0.16em] text-success">Questions complete</p><h3 className="mt-1 text-xl font-semibold text-foreground">Ready to generate your SRS.</h3><p className="mt-2 max-w-xl text-sm leading-6 text-foreground-secondary">Your confirmed answers are the source context for the generated requirements.</p></div></div>
                <div className="flex flex-wrap gap-2">
                  {onContinueToSrs && <Button size="sm" icon={<Sparkles className="h-4 w-4" />} onClick={onContinueToSrs} disabled={submitting}>Configure and generate SRS</Button>}
                  <Button variant="outline" size="sm" icon={<RotateCcw className="h-4 w-4" />} onClick={() => void reopen()} loading={submitting}>Edit discovery</Button>
                </div>
              </div>
              <div className="mt-5 flex items-center gap-2 rounded-md border border-accent/20 bg-accent-light/40 px-3 py-2.5 text-sm text-foreground-secondary"><Sparkles className="h-4 w-4 text-accent" /> Your confirmed answers are ready for the SRS generator. Choose a standards profile and depth below.</div>
            </Card>
          </motion.div>
        ) : (
          <motion.div key="questions-blocked" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.25 }}>
            <Card className="border-warning/30 bg-card p-6 sm:p-7">
              <div className="flex gap-3">
                <Lightbulb className="mt-0.5 h-6 w-6 shrink-0 text-warning" />
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-warning">More detail needed</p>
                  <h3 className="mt-1 text-xl font-semibold text-foreground">Resolve the remaining discovery gap.</h3>
                  <p className="mt-2 text-sm leading-6 text-foreground-secondary">The interview cannot safely generate requirements from an unknown or tentative decision.</p>
                  {session.readiness.blockers.length > 0 && <ul className="mt-4 space-y-2 text-sm text-foreground-secondary">{session.readiness.blockers.map((blocker) => <li key={blocker}>• {blocker}</li>)}</ul>}
                  <Button className="mt-5" variant="outline" size="sm" icon={<RotateCcw className="h-4 w-4" />} onClick={() => void reopen()} loading={submitting}>Load the required follow-up</Button>
                </div>
              </div>
            </Card>
          </motion.div>
        )}
      </AnimatePresence>

      {session.answers.length > 0 && (
        <details className="rounded-lg border border-border bg-card p-4">
          <summary className="cursor-pointer text-sm font-semibold text-foreground">Review or edit {session.answers.length} captured answer{session.answers.length === 1 ? "" : "s"}</summary>
          <div className="mt-4 grid gap-2 sm:grid-cols-2">{session.answers.map((answer) => <div key={answer.id} className="flex items-center justify-between gap-3 rounded-md border border-border p-3"><div className="min-w-0"><p className="text-sm font-medium text-foreground">{label(answer.category)}</p><p className="truncate text-xs text-foreground-secondary">{answer.disposition === "ANSWERED" ? answer.answerText : label(answer.disposition)}</p></div><Button variant="ghost" size="sm" icon={<Pencil className="h-3.5 w-3.5" />} onClick={() => editAnswer(answer)} disabled={submitting}>Edit</Button></div>)}</div>
        </details>
      )}
    </section>
  );
}
