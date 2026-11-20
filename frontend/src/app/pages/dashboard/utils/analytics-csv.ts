interface AnalyticsCsvRow {
  date: string;
  jobs: number;
  applications: number;
}

export function buildAnalyticsCsv(rows: readonly AnalyticsCsvRow[]): string {
  const header = 'Date,Jobs,Applications';
  const dataLines = rows.map(({ date, jobs, applications }) =>
    [date, jobs, applications].join(','),
  );

  return [header, ...dataLines].join('\r\n');
}
