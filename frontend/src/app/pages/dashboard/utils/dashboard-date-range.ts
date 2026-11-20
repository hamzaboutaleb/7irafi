import { Temporal } from '@js-temporal/polyfill';
import type { TranslationKey } from '../../../i18n/language.js';
import type { AnalyticsQuery, Granularity } from '../../../types/analytics.js';

const TIME_ZONE = 'Africa/Casablanca';
const MAX_RANGE_BUCKETS = 1200;

export interface DashboardFilters {
  start: string;
  end: string;
  granularity: Granularity;
}

export function createDefaultFilters(): DashboardFilters {
  const today = Temporal.Now.plainDateISO(TIME_ZONE);

  return {
    start: today.subtract({ days: 29 }).toString(),
    end: today.toString(),
    granularity: 'day',
  };
}

export function buildAnalyticsQuery({ start, end, granularity }: DashboardFilters): AnalyticsQuery {
  const firstDay = Temporal.PlainDate.from(start);
  const exclusiveEndDate = Temporal.PlainDate.from(end).add({ days: 1 });
  return {
    from: firstDay.toZonedDateTime(TIME_ZONE).toInstant().toString(),
    to: exclusiveEndDate.toZonedDateTime(TIME_ZONE).toInstant().toString(),
    granularity,
  };
}

export function buildBucketDates({ start, end, granularity }: DashboardFilters): string[] {
  let currentDate = Temporal.PlainDate.from(start);
  const endDate = Temporal.PlainDate.from(end);
  let step: Temporal.DurationLike = { days: 1 };

  if (granularity === 'week') {
    currentDate = currentDate.subtract({ days: currentDate.dayOfWeek - 1 });
    step = { weeks: 1 };
  } else if (granularity === 'month') {
    currentDate = currentDate.with({ day: 1 });
    step = { months: 1 };
  }

  const dates: string[] = [];

  while (Temporal.PlainDate.compare(currentDate, endDate) <= 0) {
    dates.push(currentDate.toString());
    currentDate = currentDate.add(step);
  }

  return dates;
}

export function validateDashboardFilters(filters: DashboardFilters): TranslationKey | null {
  const { start, end, granularity } = filters;

  try {
    const firstDay = Temporal.PlainDate.from(start);
    const lastDay = Temporal.PlainDate.from(end);

    if (Temporal.PlainDate.compare(firstDay, lastDay) > 0) return 'analytics.invalidRange';

    const days = firstDay.until(lastDay).days;
    let rangeBuckets = days;
    if (granularity === 'week') {
      rangeBuckets = days / 7;
    } else if (granularity === 'month') {
      rangeBuckets = firstDay.until(lastDay, { largestUnit: 'months' }).months;
    }

    return rangeBuckets > MAX_RANGE_BUCKETS ? 'analytics.rangeTooLarge' : null;
  } catch {
    return 'analytics.invalidRange';
  }
}
