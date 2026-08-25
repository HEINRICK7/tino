import { FiscalServiceError } from "./types.ts";

export interface RetryPolicy {
  maxAttempts: number;
  timeoutMs: number;
  backoffMs: number;
}

const sleep = (milliseconds: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, milliseconds));

export async function withRetry<T>(
  operation: (signal: AbortSignal, attempt: number) => Promise<T>,
  policy: RetryPolicy,
  onAttempt?: (attempt: number) => void,
): Promise<T> {
  const maxAttempts = Math.max(1, policy.maxAttempts);
  let lastError: unknown;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    onAttempt?.(attempt);
    const controller = new AbortController();
    let timeout: ReturnType<typeof setTimeout> | undefined;
    try {
      const timedOperation = operation(controller.signal, attempt);
      const timeoutOperation = new Promise<never>((_, reject) => {
        timeout = setTimeout(() => {
          controller.abort();
          reject(new FiscalServiceError("TIMEOUT", "Fiscal gateway timeout", { retryable: true }));
        }, policy.timeoutMs);
      });
      return await Promise.race([timedOperation, timeoutOperation]);
    } catch (error) {
      lastError = error;
      const retryable = error instanceof FiscalServiceError && error.retryable;
      if (!retryable || attempt === maxAttempts) break;
      await sleep(policy.backoffMs * 2 ** (attempt - 1));
    } finally {
      if (timeout !== undefined) clearTimeout(timeout);
    }
  }

  if (lastError instanceof FiscalServiceError) {
    throw lastError;
  }
  throw new FiscalServiceError("RETRY_EXHAUSTED", "Fiscal gateway failed after retries", {
    retryable: false,
  });
}
