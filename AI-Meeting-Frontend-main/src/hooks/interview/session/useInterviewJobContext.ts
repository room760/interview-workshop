import { useCallback, useMemo, useState } from "react";
import type { SetStateAction } from "react";
import type { InterviewJobContext } from "@/services/interviewService";
import type { InterviewFlowUser } from "@/hooks/interview/session/interviewSessionFlow.shared";

const INTERVIEW_JOB_CONTEXT_STORAGE_KEY = "interview.jobContext";

export const EMPTY_INTERVIEW_JOB_CONTEXT: InterviewJobContext = {
  jobTitle: "",
  jobRequirement: "",
  companyName: "",
  officialDomain: "",
};

const getStorageIdentity = (user: InterviewFlowUser) => {
  if (!user) return null;
  if (typeof user.id === "number" && Number.isFinite(user.id) && user.id > 0) {
    return `id:${user.id}`;
  }
  const username = user.username?.trim();
  return username ? `username:${username}` : null;
};

const sanitizeJobContext = (value: unknown): InterviewJobContext => {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return EMPTY_INTERVIEW_JOB_CONTEXT;
  }
  const source = value as Record<string, unknown>;
  return {
    jobTitle: typeof source.jobTitle === "string" ? source.jobTitle : "",
    jobRequirement:
      typeof source.jobRequirement === "string" ? source.jobRequirement : "",
    companyName:
      typeof source.companyName === "string" ? source.companyName : "",
    officialDomain:
      typeof source.officialDomain === "string" ? source.officialDomain : "",
  };
};

const readStoredJobContext = (storageKey: string | null) => {
  if (!storageKey) {
    return EMPTY_INTERVIEW_JOB_CONTEXT;
  }
  const raw = window.sessionStorage.getItem(storageKey);
  if (!raw) {
    return EMPTY_INTERVIEW_JOB_CONTEXT;
  }
  try {
    return sanitizeJobContext(JSON.parse(raw));
  } catch {
    window.sessionStorage.removeItem(storageKey);
    return EMPTY_INTERVIEW_JOB_CONTEXT;
  }
};

export const hasInterviewJobContext = (context: InterviewJobContext) =>
  Object.values(context).some((value) => value.trim().length > 0);

export function useInterviewJobContext(user: InterviewFlowUser) {
  const storageKey = useMemo(() => {
    const identity = getStorageIdentity(user);
    return identity ? `${INTERVIEW_JOB_CONTEXT_STORAGE_KEY}.${identity}` : null;
  }, [user]);
  const [stored, setStored] = useState(() => ({
    storageKey,
    value: readStoredJobContext(storageKey),
  }));
  const jobContext =
    stored.storageKey === storageKey
      ? stored.value
      : readStoredJobContext(storageKey);

  const setJobContext = useCallback(
    (nextValue: SetStateAction<InterviewJobContext>) => {
      setStored((previous) => {
        const current =
          previous.storageKey === storageKey
            ? previous.value
            : readStoredJobContext(storageKey);
        const resolved =
          typeof nextValue === "function" ? nextValue(current) : nextValue;
        const sanitized = sanitizeJobContext(resolved);
        if (storageKey) {
          window.sessionStorage.setItem(storageKey, JSON.stringify(sanitized));
        }
        return { storageKey, value: sanitized };
      });
    },
    [storageKey],
  );

  return {
    jobContext,
    setJobContext,
  };
}
