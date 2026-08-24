"use client";

import { useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  CheckCircle2,
  ChevronDown,
  Cloud,
  Lightbulb,
  Loader2,
  Pencil,
  RotateCcw,
  Sparkles,
} from "lucide-react";
import Button from "@/components/ui/Button";
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
    selectionReason: answer.selectionReason ?? "This decision helps shape the generated requirements.",
    missingRequirement: answer.missingRequirement ?? "Your answer adds context used by the documentation.",
    sourceContext: answer.sourceContext ?? [],
    confirmedContextUsed: answer.confirmedContextUsed ?? [],
    assumptionsToValidate: answer.assumptionsToValidate ?? [],
    candidateScores: answer.candidateScores ?? [],
    planner: answer.planner ?? "saved-question",
    model: answer.model ?? "unknown",
  };
}

function answerPlaceholder(category: string) {
  switch (category) {
    case "USERS": return "Describe the main users, what each person needs to do, and any important permissions.";
    case "WORKFLOWS": return "Walk through the happy path, then note what should happen when it fails or stalls.";
    case "METRICS": return "For example: reduce intake completion time from two days to four hours within three months.";
    case "CONSTRAINTS": return "For example: launch date is fixed; scope may change; must work on mobile web.";
    default: return "Add the details that would help your team make this decision confidently.";
  }
}

function answerSummary(answer: InterviewAnswerResponse) {
  if (answer.disposition === "UNKNOWN") return "Not decided yet";
  if (answer.disposition === "SKIPPED") return "Skipped for now";
  return answer.customAnswerText || answer.answerText || "Answer captured";
}

export default function InterviewPanel({ projectId, onUpdated, onReadinessChanged, onContinueToSrs }: Props) {
  const [session, setSession] = useState<InterviewSessionResponse | null>(null);
  const [answerText, setAnswerText] = useState("");
  const [selectedOptionKeys, setSelectedOptionKeys] = useState<string[]>([]);
  const [editing, setEditing] = useState<InterviewAnswerResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const activeQuestion = editing ? questionFromAnswer(editing) : session?.nextQuestion ?? null;
  const answeredCount = session?.readiness.answeredRequiredCategories ?? 0;
  const requiredCount = Math.max(session?.readiness.requiredCategoryCount ?? 1, 1);
  const progressPercent = Math.round((answeredCount / requiredCount) * 100);
  const currentNumber = Math.min(answeredCount + 1, requiredCount);
  const remainingAfterCurrent = Math.max(requiredCount - currentNumber, 0);

  const capturedAnswers = useMemo(() => session?.answers.filter((answer) => answer.current) ?? [], [session]);

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
      if (response.success && response.data) applySession(response.data, false);
      else setError(response.message || "We couldn’t load the questions. Try again.");
      setLoading(false);
    };
    void start();
    return () => { active = false; };
    // The session changes only when the project changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const submit = async (disposition: InterviewAnswerDisposition) => {
    if (!activeQuestion) return;
    if (disposition === "ANSWERED" && !answerText.trim() && selectedOptionKeys.length === 0) {
      setError("Choose an option or add a short answer before continuing.");
      return;
    }
    setSubmitting(true);
    setError(null);
    setSavedMessage(null);
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
      setSavedMessage(editing ? "Answer updated" : "Answer saved");
      window.scrollTo({ top: 0, behavior: "smooth" });
    } else {
      setError(response.message || "We couldn’t save that answer. Your response is still here, so you can try again.");
    }
    setSubmitting(false);
  };

  const editAnswer = (answer: InterviewAnswerResponse) => {
    setEditing(answer);
    setAnswerText(answer.customAnswerText ?? "");
    setSelectedOptionKeys(answer.selectedOptionKeys ?? []);
    setError(null);
    setSavedMessage(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const cancelEdit = () => {
    setEditing(null);
    setAnswerText("");
    setSelectedOptionKeys([]);
    setError(null);
  };

  const updateSelectedOptions = (optionKey: string) => {
    if (!activeQuestion) return;
    setSelectedOptionKeys((current) => {
      if (!activeQuestion.allowsMultiple) return current.includes(optionKey) ? [] : [optionKey];
      if (optionKey === "not-decided") return current.includes(optionKey) ? [] : [optionKey];
      const decided = current.filter((key) => key !== "not-decided");
      return decided.includes(optionKey) ? decided.filter((key) => key !== optionKey) : [...decided, optionKey];
    });
    setError(null);
  };

  const reopen = async () => {
    setSubmitting(true);
    const response = await interviewApi.reopen(projectId);
    if (response.success && response.data) {
      applySession(response.data);
      cancelEdit();
    } else {
      setError(response.message || "We couldn’t reopen the questions. Try again.");
    }
    setSubmitting(false);
  };

  if (loading) {
    return <div className="question-loading" role="status"><Loader2 aria-hidden="true" /> Preparing your project questions…</div>;
  }
  if (!session) {
    return <div className="question-error"><p role="alert">{error ?? "Questions are unavailable right now."}</p><Button variant="outline" onClick={() => window.location.reload()}>Try again</Button></div>;
  }

  if (!activeQuestion && session.readiness.generationReady) {
    return (
      <section className="answer-review" aria-labelledby="answer-review-title">
        <div className="answer-review__heading"><CheckCircle2 aria-hidden="true" /><div><p>Questions complete</p><h2 id="answer-review-title">Review your answers</h2><span>Make any final changes before Velocira uses these decisions to generate the requirements.</span></div></div>
        <div className="answer-review__list">
          {capturedAnswers.map((answer, index) => (
            <article key={answer.id}>
              <span>{String(index + 1).padStart(2, "0")}</span>
              <div><p>{label(answer.category)}</p><h3>{answer.questionText}</h3><strong>{answerSummary(answer)}</strong></div>
              <Button type="button" variant="ghost" size="sm" icon={<Pencil className="h-3.5 w-3.5" />} onClick={() => editAnswer(answer)}>Edit</Button>
            </article>
          ))}
        </div>
        {error && <p className="question-error" role="alert">{error}</p>}
        <div className="answer-review__actions">
          <Button type="button" variant="outline" icon={<RotateCcw className="h-4 w-4" />} onClick={() => void reopen()} loading={submitting}>Reopen questions</Button>
          {onContinueToSrs && <a href="#srs-heading" className="focus-ring inline-flex min-h-11 items-center justify-center gap-2 rounded-md border border-primary bg-primary px-5 py-2.5 text-sm font-semibold tracking-[0.01em] text-on-primary transition-colors hover:border-primary-hover hover:bg-primary-hover" onClick={() => onContinueToSrs()}><Sparkles className="h-4 w-4" aria-hidden="true" />Continue to generation</a>}
        </div>
      </section>
    );
  }

  if (!activeQuestion) {
    return (
      <section className="question-error" aria-labelledby="question-blocked-title">
        <Lightbulb aria-hidden="true" /><div><h2 id="question-blocked-title">One decision still needs attention</h2><p>Review the remaining gap so the generated requirements do not rely on an assumption.</p>{session.readiness.blockers.length > 0 && <ul>{session.readiness.blockers.map((blocker) => <li key={blocker}>{blocker}</li>)}</ul>}<Button variant="outline" onClick={() => void reopen()} loading={submitting}>Load the follow-up</Button></div>
      </section>
    );
  }

  return (
    <section className="question-flow" aria-labelledby="active-question-title">
      <div className="question-progress">
        <div className="question-progress__meta">
          <span>{editing ? "Editing a previous answer" : `Question ${currentNumber} of ${requiredCount}`}</span>
          <span>{editing ? "Changes save when you continue" : remainingAfterCurrent === 1 ? "1 after this question" : `${remainingAfterCurrent} after this question`}</span>
        </div>
        <div className="question-progress__track" role="progressbar" aria-label="Question progress" aria-valuemin={0} aria-valuemax={requiredCount} aria-valuenow={answeredCount}><span style={{ width: `${progressPercent}%` }} /></div>
        <div className="question-progress__saved" role="status" aria-live="polite"><Cloud aria-hidden="true" /> {savedMessage ?? "Saved automatically"}</div>
      </div>

      <AnimatePresence mode="wait">
        <motion.div className="question-card" key={editing ? `edit-${editing.id}` : activeQuestion.questionKey} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.18 }}>
          <header className="question-card__header">
            <p>{label(activeQuestion.category)} <span>Required</span></p>
            <h2 id="active-question-title">{activeQuestion.questionText}</h2>
            <details>
              <summary>Why we’re asking <ChevronDown aria-hidden="true" /></summary>
              <div><p><strong>Why it matters</strong>{activeQuestion.whyWeAsk}</p><p><strong>What this clarifies</strong>{activeQuestion.missingRequirement}</p></div>
            </details>
          </header>

          <div className="question-card__body">
            {activeQuestion.options.length > 0 && (
              <fieldset className="question-options" disabled={submitting}>
                <legend>{activeQuestion.allowsMultiple ? "Choose all that apply" : "Choose the closest answer"}</legend>
                <p>You can add details or give a different answer below.</p>
                <div>
                  {activeQuestion.options.map((option) => {
                    const selected = selectedOptionKeys.includes(option.key);
                    return (
                      <label key={option.key} className={selected ? "is-selected" : undefined}>
                        <input type={activeQuestion.allowsMultiple ? "checkbox" : "radio"} name={`interview-choice-${activeQuestion.questionKey}`} checked={selected} onChange={() => updateSelectedOptions(option.key)} />
                        <span aria-hidden="true"><Check /></span>
                        <span><strong>{option.label}</strong><small>{option.description}</small></span>
                      </label>
                    );
                  })}
                </div>
              </fieldset>
            )}

            <label className="question-answer">
              <span>{activeQuestion.options.length > 0 ? "Add context or give a different answer" : "Your answer"}</span>
              <small>{activeQuestion.options.length > 0 ? "Optional when a choice above captures the decision." : "A concise answer is enough. You can revise it later."}</small>
              <textarea rows={4} value={answerText} onChange={(event) => { setAnswerText(event.target.value); setError(null); }} placeholder={answerPlaceholder(activeQuestion.category)} disabled={submitting} />
            </label>

            {error && <p className="question-card__error" role="alert">{error}</p>}

            <div className="question-card__actions">
              <div>
                {editing ? <Button variant="ghost" icon={<ArrowLeft className="h-4 w-4" />} onClick={cancelEdit} disabled={submitting}>Cancel editing</Button> : capturedAnswers.length > 0 ? <Button variant="ghost" icon={<ArrowLeft className="h-4 w-4" />} onClick={() => editAnswer(capturedAnswers[capturedAnswers.length - 1]!)} disabled={submitting}>Previous answer</Button> : <span />}
                <details className="question-defer">
                  <summary>I can’t answer this yet</summary>
                  <div><button type="button" onClick={() => void submit("UNKNOWN")} disabled={submitting}>Mark as not decided</button><button type="button" onClick={() => void submit("SKIPPED")} disabled={submitting}>Skip for now</button></div>
                </details>
              </div>
              <Button size="lg" icon={<ArrowRight className="h-4 w-4" />} onClick={() => void submit("ANSWERED")} loading={submitting}>{submitting ? "Saving answer…" : editing ? "Save changes" : "Save and continue"}</Button>
            </div>
          </div>
        </motion.div>
      </AnimatePresence>

      {capturedAnswers.length > 0 && !editing && (
        <details className="question-history">
          <summary>Review {capturedAnswers.length} saved answer{capturedAnswers.length === 1 ? "" : "s"}</summary>
          <div>{capturedAnswers.map((answer) => <button key={answer.id} type="button" onClick={() => editAnswer(answer)}><span>{label(answer.category)}</span><strong>{answerSummary(answer)}</strong><Pencil aria-hidden="true" /></button>)}</div>
        </details>
      )}
    </section>
  );
}
