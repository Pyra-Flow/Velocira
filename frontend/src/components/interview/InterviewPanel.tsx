"use client";

import { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  AlertTriangle,
  ArrowRight,
  BrainCircuit,
  Check,
  CheckCircle2,
  CircleHelp,
  GitPullRequest,
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
  };
}

export default function InterviewPanel({ projectId, onUpdated }: Props) {
  const [session, setSession] = useState<InterviewSessionResponse | null>(null);
  const [answerText, setAnswerText] = useState("");
  const [selectedOptionKeys, setSelectedOptionKeys] = useState<string[]>([]);
  const [editing, setEditing] = useState<InterviewAnswerResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activeQuestion = editing ? questionFromAnswer(editing) : session?.nextQuestion ?? null;
  const briefFields = useMemo(() => {
    if (!session) return [];
    return Object.entries(session.brief.content).filter(
      ([, value]) => typeof value === "string" && value.trim().length > 0
    ) as Array<[string, string]>;
  }, [session]);
  const readinessPercent = session
    ? Math.round((session.readiness.answeredRequiredCategories / Math.max(session.readiness.requiredCategoryCount, 1)) * 100)
    : 0;

  const applySession = (next: InterviewSessionResponse, notifyParent = true) => {
    setSession(next);
    setError(null);
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
      return current.includes(optionKey)
        ? current.filter((key) => key !== optionKey)
        : [...current, optionKey];
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
                {activeQuestion.options.length > 0 && (
                  <fieldset disabled={submitting}>
                    <legend className="text-sm font-semibold text-foreground">Suggested answers</legend>
                    <p className="mt-1 text-xs text-foreground-secondary">{activeQuestion.allowsMultiple ? "Choose all that apply, then add details if useful." : "Choose the closest fit, then add details if useful."}</p>
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
                  <span className="text-sm font-semibold text-foreground">Add your own answer <span className="font-normal text-foreground-secondary">(optional)</span></span>
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
        ) : (
          <motion.div key="questions-complete" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.25 }}>
            <Card className="border-accent/30 bg-card p-6 sm:p-7">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex gap-3"><CheckCircle2 className="mt-0.5 h-6 w-6 shrink-0 text-success" /><div><p className="text-xs font-semibold uppercase tracking-[0.16em] text-success">Questions complete</p><h3 className="mt-1 text-xl font-semibold text-foreground">Start generating your documents.</h3><p className="mt-2 max-w-xl text-sm leading-6 text-foreground-secondary">Your answers are already the input. The SRS and full package controls are directly below—nothing else to confirm.</p></div></div>
                <Button variant="outline" size="sm" icon={<RotateCcw className="h-4 w-4" />} onClick={() => void reopen()} loading={submitting}>Edit discovery</Button>
              </div>
              <div className="mt-5 flex items-center gap-2 rounded-md border border-accent/20 bg-accent-light/40 px-3 py-2.5 text-sm text-foreground-secondary"><Sparkles className="h-4 w-4 text-accent" /> Generate an SRS now, or go straight to the full package after the SRS is created.</div>
            </Card>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="grid gap-4 lg:grid-cols-[1.15fr_.85fr]">
        <Card className="space-y-3">
          <div className="flex items-center justify-between gap-2"><h3 className="font-semibold text-foreground">Live brief</h3><span className="sf-meta text-foreground-secondary">Version {session.brief.version}</span></div>
          {briefFields.length ? <dl className="space-y-3">{briefFields.slice(0, 4).map(([key, value]) => <div key={key}><dt className="sf-meta font-medium uppercase tracking-wider text-foreground-secondary">{label(key)}</dt><dd className="mt-1 line-clamp-3 whitespace-pre-wrap text-sm text-foreground">{value}</dd></div>)}</dl> : <p className="text-sm text-foreground-secondary">Captured facts appear here as you answer.</p>}
        </Card>
        <Card className="space-y-3">
          <div className="flex items-center gap-2"><AlertTriangle className="h-5 w-5 text-warning" /><h3 className="font-semibold text-foreground">Visible gaps</h3></div>
          {session.readiness.generationReady ? <p className="text-sm text-success">No material readiness blocker remains.</p> : <ul className="space-y-2 text-sm text-foreground-secondary">{session.readiness.blockers.slice(0, 3).map((blocker) => <li key={blocker}>• {blocker}</li>)}</ul>}
          {session.assumptions.filter((assumption) => assumption.status === "OPEN").slice(0, 2).map((assumption) => <p key={assumption.id} className="rounded-lg bg-warning/10 p-2 text-xs text-foreground-secondary">Assumption: {assumption.statement}</p>)}
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="space-y-3" aria-labelledby="decision-log-heading">
          <div className="flex items-center justify-between gap-3"><div className="flex items-center gap-2"><GitPullRequest className="h-5 w-5 text-accent" /><h3 id="decision-log-heading" className="font-semibold text-foreground">Decision log</h3></div><span className="sf-meta text-foreground-secondary">{session.decisions.filter((decision) => decision.status === "ACTIVE").length} active</span></div>
          {session.decisions.filter((decision) => decision.status === "ACTIVE").length > 0 ? <ul className="space-y-2">{session.decisions.filter((decision) => decision.status === "ACTIVE").slice(0, 3).map((decision) => <li key={decision.id} className="border-l-2 border-accent bg-accent-light/35 px-3 py-2"><p className="text-sm font-medium text-foreground">{decision.statement}</p><p className="mt-1 text-xs leading-5 text-foreground-secondary">{decision.rationale}</p></li>)}</ul> : <p className="text-sm text-foreground-secondary">Confirmed decisions will be recorded here as the discovery brief develops.</p>}
        </Card>
        <Card className="space-y-3" aria-labelledby="review-queue-heading">
          <div className="flex items-center justify-between gap-3"><div className="flex items-center gap-2"><CircleHelp className="h-5 w-5 text-warning" /><h3 id="review-queue-heading" className="font-semibold text-foreground">Review queue</h3></div><span className="sf-meta text-foreground-secondary">{session.openQuestions.filter((question) => question.status !== "RESOLVED").length} open</span></div>
          {session.openQuestions.filter((question) => question.status !== "RESOLVED").length > 0 ? <ul className="space-y-2">{session.openQuestions.filter((question) => question.status !== "RESOLVED").slice(0, 3).map((question) => <li key={question.id} className="border-l-2 border-warning bg-warning/5 px-3 py-2"><p className="text-sm font-medium text-foreground">{question.questionText}</p><p className="mt-1 text-xs leading-5 text-foreground-secondary">{question.reason}</p></li>)}</ul> : <p className="text-sm text-success">No open questions are waiting for review.</p>}
        </Card>
      </div>

      {session.answers.length > 0 && (
        <details className="rounded-lg border border-border bg-card p-4">
          <summary className="cursor-pointer text-sm font-semibold text-foreground">Review or edit {session.answers.length} captured answer{session.answers.length === 1 ? "" : "s"}</summary>
          <div className="mt-4 grid gap-2 sm:grid-cols-2">{session.answers.map((answer) => <div key={answer.id} className="flex items-center justify-between gap-3 rounded-md border border-border p-3"><div className="min-w-0"><p className="text-sm font-medium text-foreground">{label(answer.category)}</p><p className="truncate text-xs text-foreground-secondary">{answer.disposition === "ANSWERED" ? answer.answerText : label(answer.disposition)}</p></div><Button variant="ghost" size="sm" icon={<Pencil className="h-3.5 w-3.5" />} onClick={() => editAnswer(answer)} disabled={submitting}>Edit</Button></div>)}</div>
        </details>
      )}
    </section>
  );
}
